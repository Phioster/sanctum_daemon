package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.ServiceConfig

/** A title kept out of discover and requests. */
data class SeerrBlockedItem(val tmdbId: Int, val title: String, val mediaType: String, val by: String)

/** One side of a user's request quota; [limit] 0 means unlimited. */
data class SeerrQuotaPart(val limit: Int, val days: Int, val used: Int, val remaining: Int, val restricted: Boolean)
data class SeerrQuota(val movie: SeerrQuotaPart?, val tv: SeerrQuotaPart?)

data class SeerrLogLine(val level: String, val time: String, val message: String)

private fun seerr(config: ServiceConfig) = apiFor<SeerrApi>(config, apiKeyHeader(config))

/** Movies and series not out yet, alternating so neither crowds the other out. */
suspend fun seerrUpcoming(config: ServiceConfig): List<SeerrDiscoverItem> = withContext(Dispatchers.IO) {
    val api = seerr(config)
    val movies = runCatching { parseDiscoverItems(api.upcomingMovies(), "movie") }.getOrDefault(emptyList())
    val tv = runCatching { parseDiscoverItems(api.upcomingTv(), "tv") }.getOrDefault(emptyList())
    interleave(movies, tv)
}

internal fun <T> interleave(a: List<T>, b: List<T>): List<T> =
    (0 until maxOf(a.size, b.size)).flatMap { i -> listOfNotNull(a.getOrNull(i), b.getOrNull(i)) }

/** [kind] is "recommendations" or "similar"; both answer like a discover page. */
suspend fun seerrRelated(config: ServiceConfig, tmdbId: Int, mediaType: String, kind: String): List<SeerrDiscoverItem> =
    withContext(Dispatchers.IO) {
        val type = if (mediaType == "tv") "tv" else "movie"
        parseDiscoverItems(seerr(config).related(type, tmdbId, kind), type)
    }

suspend fun seerrBlocklist(config: ServiceConfig): List<SeerrBlockedItem> = withContext(Dispatchers.IO) {
    (seerr(config).blocklist()["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
        val user = o["user"] as? JsonObject
        SeerrBlockedItem(
            tmdbId = jsInt(o, "tmdbId") ?: 0,
            title = jsStr(o, "title").orEmpty(),
            mediaType = jsStr(o, "mediaType").orEmpty(),
            by = user?.let { jsStr(it, "displayName") ?: jsStr(it, "username") }.orEmpty(),
        )
    }
}

suspend fun seerrBlock(config: ServiceConfig, tmdbId: Int, mediaType: String, title: String): String =
    destructive("block '$title' in Seerr") {
        withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    put("tmdbId", tmdbId)
                    put("mediaType", mediaType)
                    put("title", title)
                }
                okOr(seerr(config).addToBlocklist(body), "blocked")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

suspend fun seerrUnblock(config: ServiceConfig, tmdbId: Int, mediaType: String): String =
    destructive("unblock $tmdbId in Seerr") {
        withContext(Dispatchers.IO) {
            try {
                okOr(seerr(config).removeFromBlocklist(tmdbId, mediaType), "unblocked")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Deletes the title's file through Seerr, which forwards it to Radarr/Sonarr. */
suspend fun seerrDeleteFile(config: ServiceConfig, mediaId: Int, is4k: Boolean): String =
    destructive("delete the file of Seerr media $mediaId") {
        withContext(Dispatchers.IO) {
            try {
                okOr(seerr(config).deleteMediaFile(mediaId, is4k), "file deleted")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

private fun quotaPart(o: JsonObject?): SeerrQuotaPart? = o?.let {
    SeerrQuotaPart(
        limit = jsInt(it, "limit") ?: 0,
        days = jsInt(it, "days") ?: 0,
        used = jsInt(it, "used") ?: 0,
        remaining = jsInt(it, "remaining") ?: 0,
        restricted = jsBool(it, "restricted") == true,
    )
}

suspend fun seerrQuota(config: ServiceConfig, userId: Int): SeerrQuota = withContext(Dispatchers.IO) {
    val o = seerr(config).quota(userId)
    SeerrQuota(quotaPart(o["movie"] as? JsonObject), quotaPart(o["tv"] as? JsonObject))
}

/** [filter] is the lowest level shown: debug, info, warn or error. */
suspend fun seerrLogs(config: ServiceConfig, filter: String): List<SeerrLogLine> = withContext(Dispatchers.IO) {
    seerr(config).logs(filter = filter).map { o ->
        SeerrLogLine(
            level = jsStr(o, "level").orEmpty(),
            time = jsStr(o, "timestamp").orEmpty().take(19).replace('T', ' '),
            message = jsStr(o, "message").orEmpty(),
        )
    }
}
