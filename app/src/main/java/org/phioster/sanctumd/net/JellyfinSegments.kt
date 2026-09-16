package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---- Intro/outro segments + "what plays next" ----

/** A skippable stretch of an episode. [kind] is "Intro" or "Outro"; times are player milliseconds. */
data class MediaSegment(val kind: String, val startMs: Long, val endMs: Long)

/** The episode following the one being watched. */
data class NextEpisode(val id: String, val name: String, val subtitle: String)

private const val TICKS_PER_MS = 10_000L

@Serializable internal data class JfSegment(
    val Type: String = "",
    val StartTicks: Long = 0,
    val EndTicks: Long = 0,
)
@Serializable internal data class JfSegmentsResp(val Items: List<JfSegment> = emptyList())

/** The Intro Skipper plugin's own (older) route, kept as a fallback for pre-10.10 servers. */
@Serializable internal data class JfIntroTimestamps(
    val Valid: Boolean = false,
    val IntroStart: Double = 0.0,
    val IntroEnd: Double = 0.0,
)

@Serializable internal data class JfEpisodesResp(val Items: List<JfItem> = emptyList())

internal interface JellyfinSegmentsApi {
    /** Jellyfin 10.10+: media segments from any provider (the Intro Skipper plugin feeds these). */
    @GET("MediaSegments/{id}")
    suspend fun segments(
        @Path("id") id: String,
        // Repeated parameters, not a comma list: a comma list parses to no valid enum value
        // and the server filters every segment away, answering with an empty list.
        @Query("includeSegmentTypes") types: List<String> = listOf("Intro", "Outro"),
    ): JfSegmentsResp

    /** Older Intro Skipper plugin route (seconds, intro only). */
    @GET("Episode/{id}/IntroTimestamps")
    suspend fun introTimestamps(@Path("id") id: String): JfIntroTimestamps

    @GET("Shows/{seriesId}/Episodes")
    suspend fun seriesEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String = "Overview",
    ): JfEpisodesResp
}

internal fun jfSegmentsApi(config: ServiceConfig, token: String) =
    apiFor<JellyfinSegmentsApi>(config, jellyfinAuth(token))

/**
 * Intro and outro ranges for [itemId], empty when the server can't tell us (no plugin, older
 * version, movie rather than episode). Tries the native segments API first and falls back to the
 * Intro Skipper plugin's own route, so both server generations get a skip button.
 */
suspend fun jellyfinMediaSegments(config: ServiceConfig, itemId: String): List<MediaSegment> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfSegmentsApi(config, token)
    val native = runCatching {
        api.segments(itemId).Items
            .filter { it.EndTicks > it.StartTicks }
            .map { MediaSegment(it.Type, it.StartTicks / TICKS_PER_MS, it.EndTicks / TICKS_PER_MS) }
    }.getOrDefault(emptyList())
    if (native.isNotEmpty()) return@withContext native
    runCatching {
        val t = api.introTimestamps(itemId)
        if (t.Valid && t.IntroEnd > t.IntroStart) {
            listOf(MediaSegment("Intro", (t.IntroStart * 1000).toLong(), (t.IntroEnd * 1000).toLong()))
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())
}

/**
 * The next episode after [itemId], or null when it's the finale (or not an episode at all). Walks
 * the whole series rather than the season, so the last episode of a season rolls into the next one.
 */
suspend fun jellyfinNextEpisode(config: ServiceConfig, itemId: String): NextEpisode? = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        val uid = jellyfinResolveUserId(config, jfApi(config, token))
        val detail = jfApi(config, token).itemDetail(id = itemId, uid = uid)
        val seriesId = detail.SeriesId ?: return@runCatching null
        val episodes = jfSegmentsApi(config, token).seriesEpisodes(seriesId, uid).Items
            .filter { it.LocationType != "Virtual" }
        val idx = episodes.indexOfFirst { it.Id == itemId }
        val next = episodes.getOrNull(idx + 1)?.takeIf { idx >= 0 } ?: return@runCatching null
        NextEpisode(next.Id, next.Name, jfSubtitle(next))
    }.getOrNull()
}
