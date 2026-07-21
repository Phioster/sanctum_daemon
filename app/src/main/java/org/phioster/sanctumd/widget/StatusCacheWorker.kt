package org.phioster.sanctumd.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.data.StatusSnap
import org.phioster.sanctumd.data.StatusSnapshotStore
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.fetchStatus

/** Refreshes the cached service-status snapshot that the status widget renders from. */
class StatusCacheWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val services = runCatching { ServiceStore(applicationContext).services.first() }
            .getOrDefault(emptyList())
            .filter { it.type != ServiceType.SHORTCUTS } // shortcuts have no status
        val snaps = services.map { svc ->
            async {
                val st = runCatching { fetchStatus(svc) }.getOrNull()
                val note = st?.note
                    ?: st?.stats?.firstOrNull()?.let { "${it.second} ${it.first}" }
                    ?: if (st?.ok == true) "ok" else (st?.error ?: "down")
                svc.id to StatusSnap(
                    ok = st?.ok == true,
                    label = svc.label,
                    note = note,
                    stats = st?.stats?.toMap().orEmpty(),
                )
            }
        }.awaitAll().toMap()
        StatusSnapshotStore(applicationContext).write(snaps)
        // Every widget that renders from this snapshot: the status list + the three 1×1 stat tiles.
        StatusWidget().updateAll(applicationContext)
        StackHealthWidget().updateAll(applicationContext)
        QueueTileWidget().updateAll(applicationContext)
        SeerrTileWidget().updateAll(applicationContext)
        LibraryTileWidget().updateAll(applicationContext)
        Result.success()
    }
}
