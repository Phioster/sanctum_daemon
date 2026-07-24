package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.phioster.sanctumd.model.ArrCastMember
import org.phioster.sanctumd.model.JellyWatchStat
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig

internal fun jellyImageUrl(config: ServiceConfig, id: String, tag: String?, token: String): String {
    if (id.isBlank()) return ""
    var url = "${config.normalizedBaseUrl}Items/$id/Images/Primary?maxHeight=450&quality=90"
    if (!tag.isNullOrBlank()) url += "&tag=$tag"
    // No api_key in the URL — it would end up in Coil's disk cache. Loaders send
    // the token via jellyfinImageHeaders() instead.
    return url
}

/** Headers for loading Jellyfin images (Coil), keeping the token out of the URL. */
fun jellyfinImageHeaders(config: ServiceConfig): Map<String, String> {
    val token = if (!config.useLogin) config.apiKey else jellyfinSession[config.id]?.first.orEmpty()
    return if (token.isNotBlank()) mapOf("X-Emby-Token" to token) else emptyMap()
}

internal fun jfSubtitle(item: JfItem): String = when (item.Type) {
    "Episode" -> buildString {
        item.SeriesName?.let { append(it) }
        val s = item.ParentIndexNumber; val e = item.IndexNumber
        if (s != null && e != null) { if (isNotEmpty()) append(" · "); append("S%02dE%02d".format(s, e)) }
    }
    "Audio", "MusicAlbum" -> item.AlbumArtist ?: (item.ProductionYear?.toString() ?: "")
    else -> item.ProductionYear?.toString() ?: ""
}

internal fun JfItem.toMediaItem(config: ServiceConfig, token: String) = JellyMediaItem(
    id = Id,
    name = Name,
    kind = CollectionType ?: Type,
    subtitle = jfSubtitle(this),
    posterUrl = jellyImageUrl(config, Id, ImageTags?.get("Primary"), token),
    isFolder = IsFolder,
    progressPct = ((UserData?.PlayedPercentage ?: 0.0) / 100.0).toFloat(),
    number = IndexNumber,
    adult = isAdultRating(OfficialRating),
)

/** The user's libraries (Movies, Shows, Music, …). */
suspend fun jellyfinLibraryViews(config: ServiceConfig): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.views(uid).Items.map { it.toMediaItem(config, token) }
}

/** "Continue watching" — partially played items. */
suspend fun jellyfinResume(config: ServiceConfig): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.resume(uid).Items.map { it.toMediaItem(config, token) }
}

/** "Recently added" — newest items, optionally within one library. */
suspend fun jellyfinLatest(config: ServiceConfig, parentId: String? = null): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.latest(uid, 20, parentId).map { it.toMediaItem(config, token) }
}

/**
 * Contents of a library or folder.
 *
 * Virtual items (metadata placeholders with no actual media file) are dropped, matching what
 * Jellyfin itself shows — this hides "missing" episodes and specials that aren't really present.
 * When [seasonNumber] is given (parent is a season), episodes are also filtered to that exact
 * season, since Jellyfin otherwise merges Specials (season 0) into the season they aired within.
 */
suspend fun jellyfinItems(config: ServiceConfig, parentId: String, seasonNumber: Int? = null): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.items(uid, parentId).Items
        .filter { it.LocationType != "Virtual" }
        .filter { seasonNumber == null || it.Type != "Episode" || it.ParentIndexNumber == seasonNumber }
        .map { it.toMediaItem(config, token) }
}

/** Full detail for one media item, including cast. */
suspend fun jellyfinItemDetail(config: ServiceConfig, itemId: String): JellyMediaDetail = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val d = api.itemDetail(uid, itemId)
    val facts = buildList {
        d.ProductionYear?.takeIf { it > 0 }?.let { add("year" to it.toString()) }
        d.RunTimeTicks?.takeIf { it > 0 }?.let { add("runtime" to "${it / 600_000_000} min") }
        d.OfficialRating?.takeIf { it.isNotBlank() }?.let { add("rating" to it) }
        d.CommunityRating?.let { add("score" to "%.1f".format(it)) }
        d.Studios.firstOrNull()?.Name?.takeIf { it.isNotBlank() }?.let { add("studio" to it) }
    }
    val cast = d.People.filter { it.Type == "Actor" }.take(20).map { p ->
        ArrCastMember(
            name = p.Name,
            character = p.Role ?: "",
            profileUrl = if (!p.PrimaryImageTag.isNullOrBlank()) jellyImageUrl(config, p.Id, p.PrimaryImageTag, token) else "",
        )
    }
    JellyMediaDetail(
        id = d.Id,
        name = d.Name,
        overview = d.Overview ?: "",
        posterUrl = jellyImageUrl(config, d.Id, d.ImageTags?.get("Primary"), token),
        facts = facts,
        genres = d.Genres.joinToString(" · "),
        cast = cast,
        kind = d.Type,
        subtitle = if (d.Type == "Episode") buildString {
            d.SeriesName?.let { append(it) }
            val s = d.ParentIndexNumber; val e = d.IndexNumber
            if (s != null && e != null) { if (isNotEmpty()) append(" · "); append("S%02dE%02d".format(s, e)) }
        } else "",
    )
}

/** Trigger a metadata/library refresh for a single library or item. */
suspend fun jellyfinScanItem(config: ServiceConfig, itemId: String): String = destructive("rescan Jellyfin item $itemId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).refreshItem(itemId), "scan started")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Playback control: cmd = "Pause" | "Unpause" | "Stop" | "PlayPause". */
suspend fun jellyfinPlayCommand(config: ServiceConfig, sessionId: String, cmd: String): String = destructive("send playback command $cmd to session $sessionId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).playCommand(sessionId, cmd), cmd.lowercase())
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinSendMessage(config: ServiceConfig, sessionId: String, text: String): String = destructive("send a message to Jellyfin session $sessionId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).message(sessionId, JfMessageReq(text)), "message sent")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Top watchers by total playback time (seconds). Needs the Playback Reporting plugin;
 *  throws if it isn't installed (endpoint 404) — the caller shows a hint. */
suspend fun jellyfinTopWatchers(config: ServiceConfig, limit: Int = 3): List<JellyWatchStat> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val q = "SELECT UserId, SUM(PlayDuration) FROM PlaybackActivity GROUP BY UserId ORDER BY SUM(PlayDuration) DESC LIMIT $limit"
    val resp = jfApi(config, token).playbackQuery(JfPlaybackQueryReq(q, ReplaceUserId = true))
    resp.results.mapNotNull { row ->
        val name = row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val secs = row.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0L
        JellyWatchStat(name, secs)
    }
}
