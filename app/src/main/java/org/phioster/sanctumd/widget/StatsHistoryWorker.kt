package org.phioster.sanctumd.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.data.StatsHistoryStore
import org.phioster.sanctumd.net.collectStatsSnapshot
import java.util.concurrent.TimeUnit

/** Appends one daily stat snapshot to [StatsHistoryStore] so the stats screen can draw trends
 *  (library growth, grabs/day, storage forecast). Read-only against the servers; writes only local
 *  DataStore. Scheduled once/day; a same-day run just replaces that day's entry. */
class StatsHistoryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val services = runCatching { ServiceStore(applicationContext).services.first() }.getOrDefault(emptyList())
        if (services.isEmpty()) return Result.success()
        val snap = runCatching { collectStatsSnapshot(services) }.getOrNull() ?: return Result.retry()
        if (snap.metrics.isNotEmpty()) StatsHistoryStore(applicationContext).append(snap)
        return Result.success()
    }

    companion object {
        private const val WORK = "sanctumd_stats_history"

        /** Schedules the once-a-day snapshot; keeps any already-scheduled instance. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<StatsHistoryWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
