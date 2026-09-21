package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ArrCollection
import org.phioster.sanctumd.model.ArrCollectionMovie
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ServiceType

/**
 * Reads one entry of Radarr's `/api/v3/collection`.
 *
 * Radarr keeps the whole TMDB collection, not just the films you own, and marks each one with
 * `isExisting`. That is what makes "which films of this run am I missing" answerable. And it is
 * the same TMDB id Jellyfin stores on its BoxSet, so the two sides join exactly rather than by
 * title.
 */
internal fun parseArrCollection(o: JsonObject): ArrCollection? {
    val tmdb = jsInt(o, "tmdbId") ?: return null
    val movies = (o["movies"] as? JsonArray).orEmpty().mapNotNull { e ->
        val m = e as? JsonObject ?: return@mapNotNull null
        ArrCollectionMovie(
            tmdbId = jsInt(m, "tmdbId") ?: 0,
            title = jsStr(m, "title") ?: "?",
            year = jsInt(m, "year") ?: 0,
            existing = jsBool(m, "isExisting") == true,
            // A film excluded here was rejected on purpose once; offering it again would undo that.
            excluded = jsBool(m, "isExcluded") == true,
        )
    }
    return ArrCollection(
        id = jsInt(o, "id") ?: 0,
        title = jsStr(o, "title") ?: "?",
        tmdbId = tmdb,
        monitored = jsBool(o, "monitored") == true,
        qualityProfileId = jsInt(o, "qualityProfileId") ?: 0,
        rootFolderPath = jsStr(o, "rootFolderPath").orEmpty(),
        movies = movies,
    )
}

/** The collection with this TMDB id, or null when Radarr does not track that run. */
suspend fun arrCollectionByTmdb(config: ServiceConfig, tmdbId: Int): ArrCollection? =
    withContext(Dispatchers.IO) {
        runCatching {
            apiFor<ArrApi>(config, apiKeyHeader(config))
                .jsonList("${arrBase(config.type)}/collection")
                .mapNotNull { parseArrCollection(it) }
                .firstOrNull { it.tmdbId == tmdbId }
        }.getOrNull()
    }

/**
 * Adds one film of a collection to Radarr, using the collection's own profile and folder.
 *
 * [searchNow] mirrors what the user would tick in Radarr: add it monitored and start looking
 * straight away, rather than adding a row that then sits there doing nothing.
 */
suspend fun arrAddCollectionMovie(
    config: ServiceConfig,
    collection: ArrCollection,
    movie: ArrCollectionMovie,
    searchNow: Boolean,
): String = destructive("add ${movie.title} to ${config.type.label}") {
    withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("tmdbId", movie.tmdbId)
                put("title", movie.title)
                put("qualityProfileId", collection.qualityProfileId)
                put("rootFolderPath", collection.rootFolderPath)
                put("monitored", true)
                put("minimumAvailability", "released")
                putJsonObject("addOptions") {
                    put("searchForMovie", searchNow)
                }
            }
            okOr(
                apiFor<ArrApi>(config, apiKeyHeader(config))
                    .add("${arrBase(config.type)}/movie", body),
                if (searchNow) "added and searching" else "added",
            )
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/**
 * Which query names "this library item" in each service's queue.
 *
 * Radarr keys its queue by movie, Sonarr by series. Getting it wrong returns the whole queue
 * unfiltered, which would show a stranger's download on this item's page.
 */
internal fun queueFilterParam(type: ServiceType): String? = when (type) {
    ServiceType.RADARR -> "movieId"
    ServiceType.SONARR -> "seriesId"
    else -> null
}

/**
 * What is downloading right now for one library item.
 *
 * Uses `queue/details`, the endpoint made for this question. Verified present on the live
 * Radarr 6.3 and Sonarr 4.0 (both answer 200 with `[]` on an empty queue). The records are the
 * same shape `/queue` returns, which is why [ArrQueueRecord] is reused rather than a second model.
 */
suspend fun arrQueueForItem(config: ServiceConfig, itemId: Int): List<ArrQueueItem> =
    withContext(Dispatchers.IO) {
        val param = queueFilterParam(config.type) ?: return@withContext emptyList()
        runCatching {
            apiFor<ArrApi>(config, apiKeyHeader(config))
                .queueDetails("${arrBase(config.type)}/queue/details?$param=$itemId")
                .map { r ->
                    ArrQueueItem(
                        id = r.id,
                        title = r.title,
                        status = r.status,
                        progress = if (r.size > 0) ((r.size - r.sizeleft) / r.size).toFloat().coerceIn(0f, 1f) else 0f,
                        blocked = r.trackedDownloadState == "importBlocked",
                        outputPath = r.outputPath.orEmpty(),
                    )
                }
        }.getOrDefault(emptyList())
    }
