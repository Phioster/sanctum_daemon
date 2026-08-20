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

/**
 * Reads one entry of Radarr's `/api/v3/collection`.
 *
 * Radarr keeps the whole TMDB collection, not just the films you own, and marks each one with
 * `isExisting`. That is what makes "which films of this run am I missing" answerable — and it is
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
