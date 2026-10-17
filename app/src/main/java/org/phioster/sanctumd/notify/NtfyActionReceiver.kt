package org.phioster.sanctumd.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.phioster.sanctumd.model.NtfyAction
import java.util.concurrent.TimeUnit

/**
 * Runs the "http" buttons of an ntfy notification, the way the ntfy app does: the request the
 * publisher described, sent when the user taps, without opening Sanctumd. Not exported, so only
 * the PendingIntents this app built can trigger it.
 */
class NtfyActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val method = intent.getStringExtra(EXTRA_METHOD) ?: "POST"
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val headers = intent.getStringArrayListExtra(EXTRA_HEADERS).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val clear = intent.getBooleanExtra(EXTRA_CLEAR, false)
        val pending = goAsync()
        Thread {
            val ok = runCatching {
                val request = Request.Builder().url(url).apply {
                    // Stored as "Name: value" lines, the shape a header has anyway.
                    headers.forEach { h -> h.split(':', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> header(k.trim(), v.trim()) } }
                    val hasBody = method !in setOf("GET", "HEAD")
                    method(method, if (hasBody) body.toRequestBody() else null)
                }.build()
                client.newCall(request).execute().use { it.isSuccessful }
            }.onFailure { Log.w(TAG, "action '$label' failed", it) }.getOrDefault(false)
            if (ok && clear) NotificationManagerCompat.from(context).cancel(notificationId)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, if (ok) "$label: done" else "$label: failed", Toast.LENGTH_SHORT).show()
            }
            pending.finish()
        }.start()
    }

    companion object {
        private const val TAG = "NtfyAction"
        private const val EXTRA_URL = "url"
        private const val EXTRA_METHOD = "method"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_HEADERS = "headers"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_CLEAR = "clear"

        private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

        /** The notification button for [action]; "view" opens the address, "http" comes here. */
        fun button(context: Context, notificationId: Int, index: Int, action: NtfyAction): NotificationCompat.Action? {
            // Each button needs its own request code, or Android hands every button the same intent.
            val requestCode = notificationId * 4 + index
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = when (action.kind) {
                "view" -> PendingIntent.getActivity(
                    context, requestCode,
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    flags,
                )
                "http" -> PendingIntent.getBroadcast(
                    context, requestCode,
                    Intent(context, NtfyActionReceiver::class.java)
                        .putExtra(EXTRA_URL, action.url)
                        .putExtra(EXTRA_METHOD, action.method)
                        .putExtra(EXTRA_BODY, action.body)
                        .putStringArrayListExtra(EXTRA_HEADERS, ArrayList(action.headers.map { (k, v) -> "$k: $v" }))
                        .putExtra(EXTRA_LABEL, action.label)
                        .putExtra(EXTRA_NOTIFICATION, notificationId)
                        .putExtra(EXTRA_CLEAR, action.clear),
                    flags,
                )
                else -> return null
            }
            return NotificationCompat.Action.Builder(0, action.label, pi).build()
        }
    }
}
