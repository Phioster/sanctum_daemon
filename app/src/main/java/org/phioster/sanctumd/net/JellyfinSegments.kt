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

/**
 * What a play button on a series or season starts: its episodes in order, as a play queue, and
 * where in it to begin, at the first one not yet watched, like Jellyfin's own clients. Specials
 * (season 0) stay out of a series run. Everything watched starts again from the top.
 */
suspend fun jellyfinFolderQueue(
    config: ServiceConfig,
    id: String,
    kind: String,
    seasonNumber: Int?,
): Pair<List<NextEpisode>, Int> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val episodes = when (kind) {
        "Season" -> api.items(uid, id, sortBy = "SortName").Items
            .filter { it.Type == "Episode" && (seasonNumber == null || it.ParentIndexNumber == seasonNumber) }
        else -> jfSegmentsApi(config, token).seriesEpisodes(id, uid).Items
            .filter { (it.ParentIndexNumber ?: 1) > 0 }
    }.filter { it.LocationType != "Virtual" }
    episodes.map { NextEpisode(it.Id, it.Name, jfSubtitle(it)) } to
        queueStart(episodes.map { it.UserData?.Played == true })
}

/** Index of the first unwatched entry, or 0 when there is none (all watched, or empty). */
internal fun queueStart(played: List<Boolean>): Int = played.indexOfFirst { !it }.coerceAtLeast(0)

/** One episode of a series, as smart downloads need it. */
data class SmartEpisode(val id: String, val name: String, val subtitle: String, val posterUrl: String, val played: Boolean, val runTimeTicks: Long)

/** The series [itemId] (an episode) belongs to, or null. */
suspend fun jellyfinSeriesIdOf(config: ServiceConfig, itemId: String): String? = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    api.itemDetail(id = itemId, uid = jellyfinResolveUserId(config, api)).SeriesId
}

/** A series' real episodes in order, specials left out, with whether each is watched. */
suspend fun jellyfinSmartEpisodes(config: ServiceConfig, seriesId: String): List<SmartEpisode> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val uid = jellyfinResolveUserId(config, jfApi(config, token))
    jfSegmentsApi(config, token).seriesEpisodes(seriesId, uid).Items
        .filter { it.LocationType != "Virtual" && (it.ParentIndexNumber ?: 1) > 0 }
        .map { SmartEpisode(it.Id, it.Name, jfSubtitle(it), jellyImageUrl(config, it.Id, it.ImageTags?.get("Primary"), token), it.UserData?.Played == true, it.RunTimeTicks ?: 0L) }
}

/** The next [count] unwatched episodes, in order. What smart downloads keep on the phone. */
internal fun smartTargets(episodes: List<SmartEpisode>, count: Int): List<SmartEpisode> =
    episodes.filter { !it.played }.take(count)
