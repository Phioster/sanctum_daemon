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
import org.phioster.sanctumd.data.DownloadStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.DownloadEntry
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
            else -> scope.launch { pump() }
        }
        return START_NOT_STICKY
    }

    /** Processes queued entries one by one, then stops the service when the queue is empty. */
    private suspend fun pump() = mutex.withLock {
        while (true) {
            val next = store.read().values
                .filter { it.state == DownloadEntry.STATE_QUEUED }
                .minByOrNull { it.addedAt } ?: break
            runCatching { downloadOne(next) }
        }
        stopNow()
    }

    private suspend fun downloadOne(entry: DownloadEntry) {
        val id = entry.itemId
        if (id in cancelled) { cancelled -= id; return }
        val config = runCatching { ServiceStore(this).services.first().firstOrNull { it.id == entry.serverId } }.getOrNull()
        if (config == null) {
            store.update(id) { it.copy(state = DownloadEntry.STATE_FAILED, error = "server not found") }
            return
        }
        store.update(id) { it.copy(state = DownloadEntry.STATE_RUNNING, error = "") }

        val dir = File(filesDir, "downloads").apply { mkdirs() }
        try {
            val plan = jellyfinDownloadPlan(config, id)
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
                        while (true) {
                            if (id in cancelled) throw DownloadCancelled
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            got += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 700) {
                                lastUi = now
                                store.update(id) { it.copy(downloadedBytes = got) }
                                val pct = if (total > 0) (got * 100 / total).toInt() else 0
                                notify(buildNotification("↓ ${entry.name}", pct, 100, total <= 0))
                            }
                        }
                    }
                }
            }
            val poster = cachePoster(config, entry, dir)
            store.update(id) {
                it.copy(state = DownloadEntry.STATE_DONE, filePath = file.absolutePath, downloadedBytes = it.sizeBytes.coerceAtLeast(file.length()), posterFile = poster)
            }
        } catch (c: CancellationException) {
            throw c // real coroutine cancellation (service destroyed) — don't swallow
        } catch (d: DownloadCancelled) {
            removeEntryAndFiles(id) // user cancelled: clear the entry + any partial files for this id
            cancelled -= id
        } catch (t: Throwable) {
            store.update(id) { it.copy(state = DownloadEntry.STATE_FAILED, error = t.message ?: t.javaClass.simpleName) }
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
            .setColor(android.graphics.Color.parseColor("#14532D"))
            .setContentTitle("Sanctumd downloads")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (max > 0) setProgress(max, progress, indeterminate) }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FGS_ID = 4712
        private const val ACTION_ENQUEUE = "org.phioster.sanctumd.download.ENQUEUE"
        private const val ACTION_CANCEL = "org.phioster.sanctumd.download.CANCEL"
        private const val ACTION_DELETE = "org.phioster.sanctumd.download.DELETE"
        private const val EXTRA_ENTRY = "entry"
        private const val EXTRA_ITEM_ID = "itemId"
        private val downloadJson = Json { ignoreUnknownKeys = true }

        /** Queue a download. [posterUrl] is remote here; the service caches it locally. */
        fun enqueue(context: Context, serverId: String, itemId: String, name: String, subtitle: String, posterUrl: String, runTimeTicks: Long) {
            val entry = DownloadEntry(
                itemId = itemId, serverId = serverId, name = name, subtitle = subtitle,
                posterFile = posterUrl, runTimeTicks = runTimeTicks,
            )
            val i = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_ENTRY, downloadJson.encodeToString(entry))
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun cancel(context: Context, itemId: String) = send(context, ACTION_CANCEL, itemId)
        fun delete(context: Context, itemId: String) = send(context, ACTION_DELETE, itemId)

        private fun send(context: Context, action: String, itemId: String) {
            val i = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            ContextCompat.startForegroundService(context, i)
        }
    }
}
