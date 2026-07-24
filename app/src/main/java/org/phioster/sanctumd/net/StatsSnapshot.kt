package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.phioster.sanctumd.data.StatsSnapshot
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** Builds today's compact metric map across all services for the stats-history trend charts.
 *  Deliberately lightweight (counts + one library/diskspace call per service, no full scans);
 *  each service is isolated in runCatching so one failure never drops the others. */
suspend fun collectStatsSnapshot(services: List<ServiceConfig>): StatsSnapshot = coroutineScope {
    val epochDay = java.time.LocalDate.now().toEpochDay()
    val perService = services
        .map { svc -> async(Dispatchers.IO) { runCatching { metricsForService(svc) }.getOrDefault(emptyMap()) } }
        .awaitAll()
    StatsSnapshot(epochDay, perService.fold(emptyMap()) { acc, m -> acc + m })
}

private suspend fun metricsForService(svc: ServiceConfig): Map<String, Long> {
    val id = svc.id
    val m = mutableMapOf<String, Long>()
    when (svc.type) {
        ServiceType.JELLYFIN -> {
            val c = jellyfinCounts(svc)
            m["$id|movies"] = c.movies.toLong()
            m["$id|series"] = c.series.toLong()
            m["$id|episodes"] = c.episodes.toLong()
            m["$id|songs"] = c.songs.toLong()
        }
        ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> {
            runCatching { arrLibrary(svc) }.getOrNull()?.let { lib ->
                m["$id|items"] = lib.size.toLong()
                m["$id|storageMb"] = lib.sumOf { it.sizeMb }
            }
            runCatching { arrMissing(svc).size }.getOrNull()?.let { m["$id|missing"] = it.toLong() }
            runCatching { arrDiskSpace(svc) }.getOrNull()?.forEach { d -> m["$id|diskfree:${d.path}"] = d.freeBytes }
        }
        ServiceType.PROWLARR -> {
            runCatching { prowlarrIndexerStats(svc) }.getOrNull()?.let { s ->
                m["$id|grabs"] = s.sumOf { it.third }.toLong()
                m["$id|queries"] = s.sumOf { it.second }.toLong()
            }
        }
        ServiceType.SEERR -> {
            runCatching { seerrRequestStats(svc) }.getOrNull()?.forEach { (k, v) ->
                v.toLongOrNull()?.let { m["$id|req_${k.lowercase()}"] = it }
            }
        }
        else -> {}
    }
    return m
}
