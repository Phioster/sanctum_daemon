package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** Jellyfin library item counts, for the stats screen. */
data class JellyCounts(val movies: Int, val series: Int, val episodes: Int, val songs: Int)

suspend fun jellyfinCounts(config: ServiceConfig): JellyCounts = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val c = jfApi(config, token).counts()
    JellyCounts(c.MovieCount, c.SeriesCount, c.EpisodeCount, c.SongCount)
}

/** One root folder's disk usage (raw bytes), for the stats bars. */
data class DiskInfo(val path: String, val freeBytes: Long, val totalBytes: Long)

suspend fun arrDiskSpace(config: ServiceConfig): List<DiskInfo> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).diskspace("$base/diskspace")
        .filter { it.totalSpace > 0 }
        .map { DiskInfo(it.path, it.freeSpace, it.totalSpace) }
}

/** Grabs per indexer for a Prowlarr instance. */
suspend fun prowlarrIndexerGrabs(config: ServiceConfig): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
    apiFor<ProwlarrApi>(config, apiKeyHeader(config)).statsFull().indexers
        .filter { it.indexerName.isNotBlank() }
        .map { it.indexerName to it.numberOfGrabs }
}

/** Total item count for an *arr library ("*arr" = Radarr/Sonarr/Lidarr). */
suspend fun arrLibraryCount(config: ServiceConfig): Int = withContext(Dispatchers.IO) {
    if (config.type !in setOf(ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR)) 0
    else arrLibrary(config).size
}
