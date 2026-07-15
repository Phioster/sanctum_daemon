package org.phioster.nexarr.notify

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.phioster.nexarr.data.NotifyStore
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * Receives live pushes forwarded by the ntfy distributor over UnifiedPush.
 * The message body is whatever a service webhook POSTed to the endpoint, so we
 * parse the common shapes (ntfy / Overseerr / Servarr) and fall back to raw text.
 */
class UnifiedPushReceiver : MessagingReceiver() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        runBlocking { NotifyStore(context).saveEndpoint(endpoint) }
    }

    override fun onUnregistered(context: Context, instance: String) {
        runBlocking { NotifyStore(context).saveEndpoint("") }
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        runBlocking { NotifyStore(context).saveEndpoint("") }
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        val (title, text) = parse(String(message))
        notify(context, title, text)
    }

    private fun parse(raw: String): Pair<String, String> {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        if (obj != null) {
            fun s(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val title = s("title") ?: s("subject") ?: s("eventType") ?: "Nexarr"
            val nested = (obj["movie"] as? JsonObject) ?: (obj["series"] as? JsonObject) ?: (obj["media"] as? JsonObject)
            val nestedTitle = nested?.let { (it["title"] as? JsonPrimitive)?.contentOrNull }
            val text = s("message") ?: s("body") ?: nestedTitle ?: raw.take(180)
            return title to text
        }
        return "Nexarr" to raw.take(180)
    }

    private fun notify(ctx: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(ctx, title.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, Notifications.CH_LIVE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n) }
    }
}
