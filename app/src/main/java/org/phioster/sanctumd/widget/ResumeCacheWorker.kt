package org.phioster.sanctumd.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.ResumeSnap
import org.phioster.sanctumd.data.ResumeSnapshotStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.jellyfinResume

/** Refreshes the cached "continue watching" list for [ResumeWidget]. */
class ResumeCacheWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val services = runCatching { ServiceStore(applicationContext).services.first() }
            .getOrDefault(emptyList())
            .filter { it.type == ServiceType.JELLYFIN }

        val snaps = services.map { svc ->
            async {
                runCatching { jellyfinResume(svc) }.getOrDefault(emptyList()).map { item ->
                    ResumeSnap(
                        itemId = item.id,
                        title = item.name,
                        subtitle = item.subtitle,
                        percent = (item.progressPct * 100).toInt().coerceIn(0, 100),
                        serviceId = svc.id,
                    )
                }
            }
        }.awaitAll().flatten().take(12)

        ResumeSnapshotStore(applicationContext).write(snaps)
        ResumeWidget().updateAll(applicationContext)
        Result.success()
    }
}
