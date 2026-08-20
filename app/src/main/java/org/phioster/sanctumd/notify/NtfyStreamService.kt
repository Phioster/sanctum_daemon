package org.phioster.sanctumd.notify

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
import org.phioster.sanctumd.data.NotifyStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.NotifySettings
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import java.util.concurrent.TimeUnit

/**
 * Keeps streaming connections open to the user's ntfy topics and turns each incoming
 * message into a local notification — so Sanctumd shows the same live pushes the ntfy
 * app would. Subscribes to the notification-settings topic plus every topic of
 * configured NTFY services; topics on the same server share one connection.
 */
class NtfyStreamService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /** Health problems seen but not yet resolved, keyed by "service|issue". */
    private val openHealth = mutableMapOf<String, OpenHealth>()
    /** Id of the configured Jellyfin service, so a notification can point into it. */
    private var jellyfinServiceId: String? = null

    private data class OpenHealth(val id: Int, val timeSeconds: Long)

    private var job: Job? = null
    // readTimeout is the watchdog. The user's ntfy keepalive is 90 s (kept just under
    // Cloudflare's fixed 100 s idle cutoff), so the watchdog must exceed 90 s — otherwise
    // it fires between keepalives and reconnects endlessly. 110 s leaves jitter margin;
    // a genuinely dead link is caught ~100 s anyway when Cloudflare closes the socket.
    private val client = OkHttpClient.Builder()
        .readTimeout(110, TimeUnit.SECONDS)
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
        jellyfinServiceId = services.firstOrNull { it.type == ServiceType.JELLYFIN }?.id
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
        val base = msg.title.ifBlank { "Sanctumd" }
        // Process-wide dedup across ALL streams (the persisted cursor is per-server only): the same
        // message can arrive twice when the live-push topic and an NTFY service point at the same
        // server, or when a reconnect re-delivers past the trimmed recent-id list. Also collapses a
        // repeated payload with a fresh id (e.g. Jellyfin's double Playback-Stop) within a short window.
        if (isDuplicate(msg.id, base, msg.text)) {
            if (msg.time > 0) store.saveNtfyCursor(msg.time, recentIds + msg.id, cursorScope)
            return
        }
        // Messages from a secondary topic carry a MASKED topic as prefix so they're tellable apart
        // without leaking the (often unprotected) topic name in the notification.
        val title = if (msg.topic.isNotBlank() && msg.topic != mainTopic) "[${maskTopic(msg.topic)}] $base" else base
        val id = msg.id.ifEmpty { msg.text }.hashCode()
        if (!collapseHealthPair(parseHealthEvent(base, msg.text), id, title, msg.time)) {
            postNotification(id, title, msg.text, msg.time, jellyfinItemIdFromClick(msg.click))
        }
        if (msg.time > 0) store.saveNtfyCursor(msg.time, recentIds + msg.id, cursorScope)
    }

    /**
     * Shows a health problem and its end as one line instead of two.
     *
     * A failure is posted normally but remembered. When its restore arrives, the failure's
     * notification is rewritten in place to say it is over and how long it lasted — so a blip that
     * healed in seconds occupies one quiet slot rather than two alarms, and a problem that is still
     * open keeps looking like one.
     *
     * Returns true when the caller should not post anything further.
     */
    private fun collapseHealthPair(event: HealthEvent?, id: Int, title: String, timeSeconds: Long): Boolean {
        if (event == null) return false
        val key = "${event.service}|${event.issue}"
        if (!event.resolved) {
            openHealth[key] = OpenHealth(id, timeSeconds)
            return false
        }
        val open = openHealth.remove(key) ?: return false
        val lasted = if (timeSeconds > 0 && open.timeSeconds > 0) timeSeconds - open.timeSeconds else -1
        val howLong = when {
            lasted < 0 -> ""
            lasted < 60 -> " after ${lasted}s"
            lasted < 3600 -> " after ${lasted / 60}m"
            else -> " after ${lasted / 3600}h"
        }
        // Reusing the failure's id replaces that notification rather than adding one.
        postNotification(open.id, "$title — resolved$howLong", event.issue, timeSeconds)
        return true
    }

    /** Reveal only the first 4 chars of a topic (hide the rest — for an unprotected topic the random suffix is effectively the access secret). */
    private fun maskTopic(t: String): String = if (t.length <= 4) "•".repeat(t.length) else "${t.take(4)}••••••"

    private fun postNotification(
        id: Int,
        title: String,
        text: String,
        whenSeconds: Long = 0L,
        jellyfinItemId: String? = null,
    ) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            // The same carrier the launcher shortcuts and the global search already use, so the
            // tap lands on the item's own page rather than wherever the app was last left.
            if (jellyfinItemId != null && jellyfinServiceId != null) {
                putExtra("route", "service")
                putExtra("serviceId", jellyfinServiceId)
                putExtra("itemId", jellyfinItemId)
            }
        }
        val pi = launch?.let { PendingIntent.getActivity(this, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(this, Notifications.CH_LIVE)
            .setSmallIcon(org.phioster.sanctumd.R.drawable.ic_notify)
            .setColor(android.graphics.Color.parseColor("#14532D"))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            // The message's own time, not this moment. A phone that was asleep delivers a whole
            // backlog at once, and stamping those with the delivery time made a 12-second outage
            // at 02:03 read as six separate alarms at 06:26.
            .apply { if (whenSeconds > 0) { setWhen(whenSeconds * 1000L); setShowWhen(true) } }
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    private fun ongoingNotification() =
        NotificationCompat.Builder(this, Notifications.CH_SERVICE)
            .setSmallIcon(org.phioster.sanctumd.R.drawable.ic_notify)
            .setColor(android.graphics.Color.parseColor("#14532D"))
            .setContentTitle("Sanctumd live push")
            .setContentText("Listening for notifications")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        private const val FGS_ID = 4711

        // Process-wide dedup so the same push never notifies twice across streams/reconnects.
        private val seenIds = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val seenContent = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private const val ID_WINDOW_MS = 300_000L      // 5 min: same id re-delivered on a reconnect
        private const val CONTENT_WINDOW_MS = 20_000L  // 20 s: same payload with a fresh id (double webhook)

        /** True if this push was already shown recently (by id, or by identical title+text). */
        private fun isDuplicate(id: String, title: String, text: String): Boolean {
            val now = System.currentTimeMillis()
            seenIds.entries.removeAll { now - it.value > ID_WINDOW_MS }
            seenContent.entries.removeAll { now - it.value > CONTENT_WINDOW_MS }
            var dup = false
            if (id.isNotEmpty() && seenIds.putIfAbsent(id, now) != null) dup = true
            if (seenContent.putIfAbsent("$title $text", now) != null) dup = true
            return dup
        }

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
