package org.phioster.nexarr.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.phioster.nexarr.data.ServiceStore
import org.phioster.nexarr.data.StatusSnap
import org.phioster.nexarr.data.StatusSnapshotStore
import org.phioster.nexarr.model.ServiceType
import org.phioster.nexarr.net.fetchStatus

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
                svc.id to StatusSnap(ok = st?.ok == true, label = svc.label, note = note)
            }
        }.awaitAll().toMap()
        StatusSnapshotStore(applicationContext).write(snaps)
        StatusWidget().updateAll(applicationContext)
        Result.success()
    }
}
