package org.phioster.sanctumd.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phioster.sanctumd.data.DashboardStore
import org.phioster.sanctumd.data.DownloadStore
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.jellyfinSeriesIdOf
import org.phioster.sanctumd.net.jellyfinSmartEpisodes
import org.phioster.sanctumd.net.smartTargets
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Smart downloads (2026-11-07): for every series with downloaded episodes, keep the next N
 * unwatched ones on the phone. Watched episode downloads are deleted, the following ones queued.
 * Runs when the media tab opens and hourly in the background, so an episode watched on the TV
 * is replaced before the phone goes out the door.
 */
object SmartDownloads {
    private val lock = Mutex()

    private fun key(serverId: String, seriesId: String) = "$serverId|$seriesId"

    /** Forgets a series once its last download was deleted by hand: the user is done with it. */
    suspend fun untrack(context: Context, serverId: String, seriesId: String) {
        if (seriesId.isBlank()) return
        val store = DashboardStore(context)
        store.setSmartSeries(store.smartSeries.first() - key(serverId, seriesId))
    }

    suspend fun untrackServer(context: Context, serverId: String) {
        val store = DashboardStore(context)
        store.setSmartSeries(store.smartSeries.first().filterNot { it.startsWith("$serverId|") }.toSet())
    }

    suspend fun run(context: Context) = lock.withLock {
        val settings = DashboardStore(context)
        if (!settings.smartDownloads.first()) return@withLock
        val count = settings.smartDownloadCount.first()
        val downloads = DownloadStore(context)
        val servers = ServiceStore(context).realServices.first().filter { it.type == ServiceType.JELLYFIN }
        var tracked = settings.smartSeries.first()
        var queued = false

        servers.forEach { config ->
            // Episodes downloaded before this feature carry no series id yet; look it up once.
            downloads.read().values
                .filter { it.serverId == config.id && it.seriesId.isBlank() && it.subtitle.contains(" · S") }
                .forEach { e ->
                    runCatching { jellyfinSeriesIdOf(config, e.itemId) }.getOrNull()?.let { sid ->
                        downloads.update(e.itemId) { it.copy(seriesId = sid) }
                    }
                }
            val mine = downloads.read().values.filter { it.serverId == config.id }
            tracked = tracked + mine.filter { it.seriesId.isNotBlank() }.map { key(config.id, it.seriesId) }

            tracked.filter { it.startsWith("${config.id}|") }.forEach { k ->
                val seriesId = k.substringAfter('|')
                val episodes = runCatching { jellyfinSmartEpisodes(config, seriesId) }.getOrNull() ?: return@forEach
                val played = episodes.filter { it.played }.map { it.id }.toSet()
                // Finished downloads of watched episodes make room. Running ones are left alone.
                mine.filter { it.seriesId == seriesId && it.done && it.itemId in played }.forEach { e ->
                    e.filePath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                    e.posterFile.takeIf { it.startsWith("/") }?.let { File(it).delete() }
                    downloads.remove(e.itemId)
                }
                val targets = smartTargets(episodes, count)
                if (targets.isEmpty()) { tracked = tracked - k; return@forEach } // series watched to the end
                val have = downloads.read().keys
                targets.filter { it.id !in have }.forEachIndexed { i, ep ->
                    downloads.put(
                        DownloadEntry(
                            itemId = ep.id, serverId = config.id, name = ep.name, subtitle = ep.subtitle,
                            posterFile = ep.posterUrl, runTimeTicks = ep.runTimeTicks, seriesId = seriesId,
                            state = DownloadEntry.STATE_QUEUED, addedAt = System.currentTimeMillis() + i,
                        ),
                    )
                    queued = true
                }
            }
        }
        settings.setSmartSeries(tracked)
        // From the background Android may refuse the start; the queue then runs when the app opens.
        if (queued) DownloadService.resume(context)
    }
}

/** The hourly background pass of [SmartDownloads]. */
class SmartDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { SmartDownloads.run(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK = "sanctumd_smart_downloads"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<SmartDownloadWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
