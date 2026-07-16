package org.phioster.nexarr.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Notification channels + WorkManager scheduling for the background poller. */
object Notifications {
    const val CH_MEDIA = "media"
    const val CH_IMPORTS = "imports"
    const val CH_REQUESTS = "requests"
    const val CH_HEALTH = "health"
    const val CH_LIVE = "live"
    const val CH_SERVICE = "live_service"
    private const val WORK = "nexarr_notify_poll"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CH_MEDIA, "New media", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_IMPORTS, "Downloads imported", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_REQUESTS, "Seerr requests", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_HEALTH, "Health issues", NotificationManager.IMPORTANCE_LOW),
            Triple(CH_LIVE, "Live push", NotificationManager.IMPORTANCE_HIGH),
            Triple(CH_SERVICE, "Live push service", NotificationManager.IMPORTANCE_MIN),
        ).forEach { (id, name, importance) ->
            nm.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    fun schedule(ctx: Context, intervalMin: Int) {
        val request = PeriodicWorkRequestBuilder<NexarrNotificationWorker>(
            intervalMin.toLong().coerceAtLeast(15), TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }
}
