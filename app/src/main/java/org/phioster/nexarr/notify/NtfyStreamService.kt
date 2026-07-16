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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.phioster.nexarr.data.NotifyStore
import org.phioster.nexarr.data.ServiceStore
import org.phioster.nexarr.model.NotifySettings
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceType
import java.util.concurrent.TimeUnit

/**
 * Keeps streaming connections open to the user's ntfy topics and turns each incoming
 * message into a local notification — so Nexarr shows the same live pushes the ntfy
 * app would. Subscribes to the notification-settings topic plus every topic of
 * configured NTFY services; topics on the same server share one connection.
 */
class NtfyStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    // readTimeout is the watchdog: ntfy sends a keepalive every ~45 s, so 77 s of
    // silence means the connection is dead and we should reconnect.
    private val client = OkHttpClient.Builder()
        .readTimeout(77, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** One streaming connection: a server, its topics, and an optional token. */
    private data class Sub(val server: String, val topics: List<String>, val token: String)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)
        ServiceCompat.startForeground(this, FGS_ID, ongoingNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // Every (re)start re-snapshots the subscriptions — settings or services may have changed.
        job?.cancel()
        job = scope.launch { superviseStreams() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel(); job = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun superviseStreams() {
        val store = NotifyStore(applicationContext)
        val settings = store.currentSettings()
        val services = runCatching { ServiceStore(applicationContext).services.first() }.getOrDefault(emptyList())
        val subs = buildSubs(settings, services)
        if (subs.isEmpty()) { stopSelf(); return }
        val mainTopic = settings.ntfyTopic
        coroutineScope { subs.forEach { sub -> launch { runLoop(store, sub, mainTopic) } } }
    }

    private fun buildSubs(s: NotifySettings, services: List<ServiceConfig>): List<Sub> {
        // server -> (topics, token); the settings topic and NTFY-service topics merge per server.
        val byServer = LinkedHashMap<String, Pair<LinkedHashSet<String>, String>>()
        fun add(server: String, topics: List<String>, token: String) {
            val key = server.trimEnd('/')
            if (key.isBlank() || topics.isEmpty()) return
            val cur = byServer[key]
            if (cur == null) byServer[key] = LinkedHashSet(topics) to token
            else { cur.first.addAll(topics); if (cur.second.isBlank() && token.isNotBlank()) byServer[key] = cur.first to token }
        }
        if (s.live && s.ntfyServer.isNotBlank() && s.ntfyTopic.isNotBlank()) add(s.ntfyServer, listOf(s.ntfyTopic), s.ntfyToken)
        services.filter { it.type == ServiceType.NTFY }.forEach { svc ->
            add(svc.baseUrl, svc.topics.map { it.trim() }.filter { it.isNotBlank() }, svc.apiKey)
        }
        return byServer.map { (server, v) -> Sub(server, v.first.toList(), v.second) }
    }

    private suspend fun runLoop(store: NotifyStore, sub: Sub, mainTopic: String) {
        val cursorScope = cursorScopeFor(sub.server)
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            // On reconnect, ask for everything since the last seen message so nothing
            // is missed while offline; a fresh subscription (time 0) starts live-only.
            val (lastTime, _) = store.ntfyCursor(cursorScope)
            val url = "${sub.server}/${sub.topics.joinToString(",")}/json" +
                if (lastTime > 0) "?since=$lastTime" else ""
            try {
                val req = Request.Builder().url(url).apply {
                    if (sub.token.isNotBlank()) header("Authorization", "Bearer ${sub.token}")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                    backoff = 2_000L // connected — reset backoff
                    val source = resp.body?.source() ?: return@use
                    while (!source.exhausted() && currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isNotBlank()) handleLine(line, store, cursorScope, mainTopic)
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

    private suspend fun handleLine(line: String, store: NotifyStore, cursorScope: String, mainTopic: String) {
        val msg = parseNtfyLine(line) ?: return
        val (_, recentIds) = store.ntfyCursor(cursorScope)
        if (msg.id.isNotEmpty() && msg.id in recentIds) return // duplicate at the ?since= boundary
        val base = msg.title.ifBlank { "Nexarr" }
        // Messages from a secondary topic carry the topic as prefix so they're tellable apart.
        val title = if (msg.topic.isNotBlank() && msg.topic != mainTopic) "[${msg.topic}] $base" else base
        postNotification(msg.id.ifEmpty { msg.text }.hashCode(), title, msg.text)
        if (msg.time > 0) store.saveNtfyCursor(msg.time, recentIds + msg.id, cursorScope)
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

        /** Cursor scope per server; the empty scope keeps the pre-multi-topic cursor keys. */
        fun cursorScopeFor(server: String): String =
            Integer.toHexString(server.trimEnd('/').hashCode())

        fun start(ctx: Context) {
            val i = Intent(ctx, NtfyStreamService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, NtfyStreamService::class.java))
        }

        /** Re-reads settings + services (call after either changed); the service
         *  stops itself when no subscriptions remain. */
        fun restart(ctx: Context) = start(ctx)
    }
}
