package org.phioster.sanctumd.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.phioster.sanctumd.MainActivity
import org.phioster.sanctumd.R
import org.phioster.sanctumd.data.DashboardStore
import org.phioster.sanctumd.data.DownloadStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.net.jellyfinAudioDownloadPlan
import org.phioster.sanctumd.net.jellyfinDownloadPlan
import org.phioster.sanctumd.net.jellyfinImageHeaders
import org.phioster.sanctumd.notify.Notifications
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Manual abort signal for a running download (distinct from coroutine [CancellationException]). */
private object DownloadCancelled : Exception() {
    private fun readResolve(): Any = DownloadCancelled
}

/**
 * Downloads Jellyfin media to the app's private storage for offline playback. Runs one download at a
 * time (a serial queue re-read from [DownloadStore] each step so newly enqueued items are picked up)
 * as a foreground service with a progress notification; the original file is streamed to disk and the
 * poster cached alongside it. Cancelling or deleting removes the entry and any partial file.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { DownloadStore(this) }
    private val mutex = Mutex()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private var wifiCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // stream large files without a read timeout
        .build()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(this, FGS_ID, buildNotification("preparing downloads…", 0, 0, true), fgsType())

        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val json = intent.getStringExtra(EXTRA_ENTRY)
                val entry = json?.let { runCatching { downloadJson.decodeFromString<DownloadEntry>(it) }.getOrNull() }
                scope.launch {
                    if (entry != null) {
                        store.put(entry.copy(state = DownloadEntry.STATE_QUEUED, addedAt = System.currentTimeMillis()))
                        pump()
                    } else if (noActiveWork()) {
                        stopNow()
                    }
                }
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ITEM_ID)
                if (id != null) { cancelled += id; scope.launch { removeEntryAndFiles(id) } }
            }
            ACTION_DELETE -> {
                val id = intent.getStringExtra(EXTRA_ITEM_ID)
                if (id != null) { cancelled += id; scope.launch { removeEntryAndFiles(id); if (noActiveWork()) stopNow() } }
            }
            ACTION_CLEAR_DONE -> scope.launch {
                store.read().values.filter { it.done }.forEach { removeEntryAndFiles(it.itemId) }
                if (noActiveWork()) stopNow()
            }
            ACTION_CLEAR_ALL -> scope.launch {
                store.read().values.forEach { cancelled += it.itemId; removeEntryAndFiles(it.itemId) }
                stopNow()
            }
            else -> scope.launch { pump() }
        }
        return START_NOT_STICKY
    }

    /** Processes queued entries one by one, then stops the service when the queue is empty. Honours
     *  the Wi-Fi-only setting: when on a metered network it parks the queue and waits for Wi-Fi. */
    private suspend fun pump() = mutex.withLock {
        while (true) {
            val next = store.read().values
                .filter { it.state == DownloadEntry.STATE_QUEUED }
                .minByOrNull { it.addedAt } ?: break
            if (wifiOnlyBlocked()) {
                // Flag the parked items and resume automatically when an un-metered network appears.
                store.read().values.filter { it.state == DownloadEntry.STATE_QUEUED }
                    .forEach { q -> store.update(q.itemId) { it.copy(error = "waiting for Wi-Fi") } }
                awaitWifi()
                stopNow()
                return@withLock
            }
            runCatching { downloadOne(next) }
        }
        stopNow()
    }

    private suspend fun wifiOnlyBlocked(): Boolean {
        val wifiOnly = runCatching { DashboardStore(this).downloadsWifiOnly.first() }.getOrDefault(false)
        return wifiOnly && isMetered()
    }

    /** True when there's no un-metered (Wi-Fi/ethernet) network — i.e. only mobile data. */
    private fun isMetered(): Boolean = runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching true
        !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /** Registers a one-shot callback that re-triggers the queue once an un-metered network is up. */
    private fun awaitWifi() {
        if (wifiCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                unregisterWifiWait()
                // Starting a FGS from a background callback is blocked on Android 12+; if so the parked
                // items just resume the next time the app is foregrounded (see resume()).
                runCatching { ContextCompat.startForegroundService(this@DownloadService, Intent(this@DownloadService, DownloadService::class.java)) }
            }
        }
        wifiCallback = cb
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    private fun unregisterWifiWait() {
        val cb = wifiCallback ?: return
        wifiCallback = null
        runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)?.unregisterNetworkCallback(cb) }
    }

    private suspend fun downloadOne(entry: DownloadEntry) {
        val id = entry.itemId
        // For episodes the subtitle holds "SeriesName · S01E02" — prefix it so the notification names the show.
        val display = if (entry.subtitle.isNotBlank()) "${entry.subtitle} · ${entry.name}" else entry.name
        if (id in cancelled) { cancelled -= id; return }
        val config = runCatching { ServiceStore(this).services.first().firstOrNull { it.id == entry.serverId } }.getOrNull()
        if (config == null) {
            store.update(id) { it.copy(state = DownloadEntry.STATE_FAILED, error = "server not found") }
            return
        }
        store.update(id) { it.copy(state = DownloadEntry.STATE_RUNNING, error = "") }

        val dir = File(filesDir, "downloads").apply { mkdirs() }
        try {
            val plan = if (entry.mediaType == "Audio") jellyfinAudioDownloadPlan(config, id) else jellyfinDownloadPlan(config, id, entry.maxBitrate)
            val file = File(dir, "$id.${plan.container}")
            val reqB = Request.Builder().url(plan.url)
            plan.headers.forEach { (k, v) -> if (v.isNotBlank()) reqB.header(k, v) }
            client.newCall(reqB.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty response")
                val total = if (plan.sizeBytes > 0) plan.sizeBytes else body.contentLength()
                store.update(id) { it.copy(sizeBytes = if (total > 0) total else it.sizeBytes) }
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var got = 0L
                        var lastUi = 0L
                        var lastNotif = 0L
                        while (true) {
                            if (id in cancelled) throw DownloadCancelled
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            got += n
                            val now = System.currentTimeMillis()
                            // In-app progress: update often for a smooth bar. Notification: throttled
                            // separately (~1s) so we don't hammer the system notifier.
                            if (now - lastUi > 250) {
                                lastUi = now
                                store.update(id) { it.copy(downloadedBytes = got) }
                            }
                            if (now - lastNotif > 1000) {
                                lastNotif = now
                                val pct = if (total > 0) (got * 100 / total).toInt() else 0
                                val label = if (total > 0) "$pct%  ·  $display" else "↓  $display"
                                notify(buildNotification(label, pct, 100, total <= 0))
                            }
                        }
                    }
                }
            }
            val poster = cachePoster(config, entry, dir)
            store.update(id) {
                it.copy(state = DownloadEntry.STATE_DONE, filePath = file.absolutePath, downloadedBytes = it.sizeBytes.coerceAtLeast(file.length()), posterFile = poster)
            }
            notifyDone(id, "✓  $display", "Download complete")
        } catch (c: CancellationException) {
            throw c // real coroutine cancellation (service destroyed) — don't swallow
        } catch (d: DownloadCancelled) {
            removeEntryAndFiles(id) // user cancelled: clear the entry + any partial files for this id
            cancelled -= id
        } catch (t: Throwable) {
            store.update(id) { it.copy(state = DownloadEntry.STATE_FAILED, error = t.message ?: t.javaClass.simpleName) }
            notifyDone(id, "⚠  $display", "Download failed")
        }
    }

    /** Downloads the poster next to the media so the offline list can show art. Returns its path or "". */
    private fun cachePoster(config: org.phioster.sanctumd.model.ServiceConfig, entry: DownloadEntry, dir: File): String {
        val url = entry.posterFile.takeIf { it.startsWith("http") } ?: return ""
        return try {
            val reqB = Request.Builder().url(url)
            jellyfinImageHeaders(config).forEach { (k, v) -> if (v.isNotBlank()) reqB.header(k, v) }
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) reqB.header(k, v) }
            client.newCall(reqB.build()).execute().use { resp ->
                val body = resp.body ?: return ""
                if (!resp.isSuccessful) return ""
                val f = File(dir, "${entry.itemId}.jpg")
                FileOutputStream(f).use { out -> body.byteStream().copyTo(out) }
                f.absolutePath
            }
        } catch (t: Throwable) { "" }
    }

    private suspend fun removeEntryAndFiles(id: String) {
        store.read()[id]?.let { e ->
            e.filePath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
            e.posterFile.takeIf { it.isNotBlank() && it.startsWith("/") }?.let { File(it).delete() }
        }
        // Also clear any stray partial files for this id.
        File(filesDir, "downloads").listFiles { f -> f.name.startsWith("$id.") }?.forEach { it.delete() }
        store.remove(id)
    }

    private suspend fun noActiveWork(): Boolean =
        store.read().values.none { it.state == DownloadEntry.STATE_QUEUED || it.state == DownloadEntry.STATE_RUNNING }

    private fun notify(n: android.app.Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(this).notify(FGS_ID, n) }
        }
    }

    /** A dismissible completion/failure notification, separate from the ongoing FGS one so it
     *  survives after the service stops — the way a downloader flips "downloading" to "done". */
    private fun notifyDone(itemId: String, text: String, title: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            this, "dl:$itemId".hashCode(), Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Notifications.CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(org.phioster.sanctumd.ui.theme.ThemeStore.notificationColor(applicationContext))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // Unique id (never FGS_ID) so it isn't cleared when the foreground notification is removed.
        runCatching { NotificationManagerCompat.from(this).notify("dl:$itemId".hashCode(), n) }
    }

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // DATA_SYNC exists since API 29; on 26–28 the type is ignored by ServiceCompat.
    private fun fgsType(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    private fun buildNotification(text: String, progress: Int, max: Int, indeterminate: Boolean): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(org.phioster.sanctumd.ui.theme.ThemeStore.notificationColor(applicationContext))
            .setContentTitle("Sanctumd downloads")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply {
                if (max > 0) {
                    setProgress(max, progress, indeterminate)
                    if (!indeterminate) setSubText("$progress%") // percent in the notification header
                }
            }
            .build()
    }

    override fun onDestroy() {
        unregisterWifiWait()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FGS_ID = 4712
        private const val ACTION_ENQUEUE = "org.phioster.sanctumd.download.ENQUEUE"
        private const val ACTION_CANCEL = "org.phioster.sanctumd.download.CANCEL"
        private const val ACTION_DELETE = "org.phioster.sanctumd.download.DELETE"
        private const val ACTION_CLEAR_DONE = "org.phioster.sanctumd.download.CLEAR_DONE"
        private const val ACTION_CLEAR_ALL = "org.phioster.sanctumd.download.CLEAR_ALL"
        private const val EXTRA_ENTRY = "entry"
        private const val EXTRA_ITEM_ID = "itemId"
        private val downloadJson = Json { ignoreUnknownKeys = true }

        /** Queue a download. [posterUrl] is remote here; the service caches it locally. */
        fun enqueue(context: Context, serverId: String, itemId: String, name: String, subtitle: String, posterUrl: String, runTimeTicks: Long, mediaType: String = "Video", maxBitrate: Int = 0) {
            val entry = DownloadEntry(
                itemId = itemId, serverId = serverId, name = name, subtitle = subtitle,
                posterFile = posterUrl, runTimeTicks = runTimeTicks, mediaType = mediaType,
                maxBitrate = maxBitrate,
            )
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_ENTRY, downloadJson.encodeToString(entry))
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context, itemId: String) = send(context, ACTION_CANCEL, itemId)
        fun delete(context: Context, itemId: String) = send(context, ACTION_DELETE, itemId)
        fun clearCompleted(context: Context) = sendAction(context, ACTION_CLEAR_DONE)
        fun clearAll(context: Context) = sendAction(context, ACTION_CLEAR_ALL)
        /** Nudge the queue (e.g. from a foreground screen) — resumes Wi-Fi-parked downloads. */
        fun resume(context: Context) = runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }.let {}

        private fun send(context: Context, action: String, itemId: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            ContextCompat.startForegroundService(context, i)
        }
        private fun sendAction(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).apply { this.action = action })
        }
    }
}
