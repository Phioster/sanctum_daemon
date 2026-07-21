package org.phioster.sanctumd.notify

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.NotifyStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.arrHistory
import org.phioster.sanctumd.net.arrSystem
import org.phioster.sanctumd.net.jellyfinLatest
import org.phioster.sanctumd.net.seerrRequests

/**
 * Periodically polls the configured services and raises a local notification for anything
 * new since the last run (baseline established on first sighting, so no backlog spam).
 */
class SanctumdNotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = NotifyStore(ctx)
        val settings = store.currentSettings()
        if (!settings.enabled) return Result.success()

        val services = runCatching { ServiceStore(ctx).services.first() }.getOrDefault(emptyList())
        val seen = store.seen().toMutableMap()
        var nid = ((System.currentTimeMillis() / 1000) % 1_000_000).toInt()

        // current = list of (dedupKey, displayText); notifies only for keys not seen before.
        fun handle(key: String, channel: String, title: String, current: List<Pair<String, String>>) {
            val prev = seen[key]
            seen[key] = current.map { it.first }
            if (prev == null) return // first sighting → baseline only
            current.filter { it.first !in prev }.take(6).forEach { (_, text) ->
                notify(ctx, channel, nid++, title, text)
            }
        }

        for (svc in services) {
            runCatching {
                when (svc.type) {
                    ServiceType.JELLYFIN -> if (settings.newMedia) {
                        val items = jellyfinLatest(svc, null)
                        handle(
                            "${svc.id}:media", Notifications.CH_MEDIA, "New in ${svc.label}",
                            items.map { it.id to (it.name + if (it.subtitle.isNotBlank()) " · ${it.subtitle}" else "") },
                        )
                    }
                    ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> {
                        if (settings.imports) {
                            val imports = arrHistory(svc).filter { it.eventType.equals("downloadFolderImported", true) }
                            handle(
                                "${svc.id}:imports", Notifications.CH_IMPORTS, "Imported · ${svc.label}",
                                imports.map { ("${it.title}|${it.date}") to it.title },
                            )
                        }
                        if (settings.health) {
                            val health = arrSystem(svc).health
                            handle(
                                "${svc.id}:health", Notifications.CH_HEALTH, "Health · ${svc.label}",
                                health.map { it.second to "${it.first}: ${it.second}" },
                            )
                        }
                    }
                    ServiceType.SEERR -> if (settings.requests) {
                        val requests = seerrRequests(svc, "pending")
                        handle(
                            "${svc.id}:requests", Notifications.CH_REQUESTS, "New request · ${svc.label}",
                            requests.map { it.id.toString() to it.title },
                        )
                    }
                    else -> {}
                }
            }
        }

        store.saveSeen(seen)
        return Result.success()
    }

    private fun notify(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(ctx, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(org.phioster.sanctumd.R.drawable.ic_notify)
            .setColor(android.graphics.Color.parseColor("#14532D"))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
