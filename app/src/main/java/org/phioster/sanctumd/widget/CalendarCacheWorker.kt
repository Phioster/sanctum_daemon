package org.phioster.sanctumd.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.CalendarSnap
import org.phioster.sanctumd.data.CalendarSnapshotStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.arrCalendarRange

/** Refreshes the cached upcoming-releases snapshot that the calendar (agenda) widget renders from.
 *  Pulls a 35-day window across every Radarr/Sonarr/Lidarr so future releases are visible too. */
class CalendarCacheWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val services = runCatching { ServiceStore(applicationContext).services.first() }
            .getOrDefault(emptyList())
            .filter { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR || it.type == ServiceType.LIDARR }

        val now = java.time.Instant.now()
        val end = now.plus(java.time.Duration.ofDays(35))
        val snaps = services.map { svc ->
            async {
                runCatching { arrCalendarRange(svc, now, end) }.getOrDefault(emptyList()).map { ci ->
                    CalendarSnap(
                        date = ci.date,
                        title = ci.title,
                        subtitle = ci.subtitle,
                        serviceType = svc.type.name,
                        serviceId = svc.id,
                        hasFile = ci.hasFile,
                    )
                }
            }
        }.awaitAll().flatten().sortedBy { it.date }

        CalendarSnapshotStore(applicationContext).write(snaps)
        CalendarWidget().updateAll(applicationContext)
        Result.success()
    }
}
