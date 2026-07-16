package org.phioster.nexarr.notify

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.phioster.nexarr.data.NotifyStore
import java.util.concurrent.TimeUnit

/**
 * Keeps a streaming connection open to the user's ntfy topic and turns each incoming
 * message into a local notification — so Nexarr shows the same live pushes the ntfy app would.
 */
class NtfyStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    // readTimeout is the watchdog: ntfy sends a keepalive every ~45 s, so 77 s of
    // silence means the connection is dead and we should reconnect.
    private val client = OkHttpClient.Builder()
        .readTimeout(77, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(this, FGS_ID, ongoingNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (job == null) job = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); job = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val store = NotifyStore(applicationContext)
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            val s = store.currentSettings()
            if (!s.live || s.ntfyServer.isBlank() || s.ntfyTopic.isBlank()) {
                stopSelf(); return
            }
            // On reconnect, ask for everything since the last seen message so nothing
            // is missed while offline; a fresh install (time 0) subscribes live-only.
            val (lastTime, _) = store.ntfyCursor()
            val url = "${s.ntfyServer.trimEnd('/')}/${s.ntfyTopic}/json" +
                if (lastTime > 0) "?since=$lastTime" else ""
            try {
                val req = Request.Builder().url(url).apply {
                    if (s.ntfyToken.isNotBlank()) header("Authorization", "Bearer ${s.ntfyToken}")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                    backoff = 2_000L // connected — reset backoff
                    val source = resp.body?.source() ?: return@use
                    while (!source.exhausted() && currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isNotBlank()) handleLine(line, store)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // network hiccup / server restart / watchdog timeout → backoff below
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun handleLine(line: String, store: NotifyStore) {
        val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
        if (str("event") != "message") return // ignore open/keepalive/poll_request
        val id = str("id").orEmpty()
        val (_, lastId) = store.ntfyCursor()
        if (id.isNotEmpty() && id == lastId) return // duplicate at the ?since= boundary
        val body = str("message") ?: return
        // The body may itself be JSON that a service (Radarr/Overseerr/…) posted.
        val inner = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val title: String
        val text: String
        if (inner != null) {
            fun s(key: String) = (inner[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val nested = (inner["movie"] as? JsonObject) ?: (inner["series"] as? JsonObject) ?: (inner["media"] as? JsonObject)
            val nestedTitle = nested?.let { (it["title"] as? JsonPrimitive)?.contentOrNull }
            title = str("title") ?: s("title") ?: s("subject") ?: s("eventType") ?: "Nexarr"
            text = s("message") ?: s("body") ?: nestedTitle ?: body.take(180)
        } else {
            title = str("title") ?: "Nexarr"
            text = body.take(180)
        }
        postNotification(id.ifEmpty { text }.hashCode(), title, text)
        (obj["time"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { store.saveNtfyCursor(it, id) }
    }

    private fun postNotification(id: Int, title: String, text: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = launch?.let { PendingIntent.getActivity(this, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(this, Notifications.CH_LIVE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    private fun ongoingNotification() =
        NotificationCompat.Builder(this, Notifications.CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Nexarr live push")
            .setContentText("Listening for notifications")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        private const val FGS_ID = 4711

        fun start(ctx: Context) {
            val i = Intent(ctx, NtfyStreamService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, NtfyStreamService::class.java))
        }
    }
}
