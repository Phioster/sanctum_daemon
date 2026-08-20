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
import org.phioster.sanctumd.model.JellyFileInfo
import org.phioster.sanctumd.model.JellyIdentifyCandidate
import org.phioster.sanctumd.model.JellyStream
import org.phioster.sanctumd.model.JellyWatchStat
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellySubtitle
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
    played = UserData?.Played == true,
    unplayedCount = UserData?.UnplayedItemCount ?: 0,
    favorite = UserData?.IsFavorite == true,
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
suspend fun jellyfinItems(
    config: ServiceConfig,
    parentId: String,
    seasonNumber: Int? = null,
    sortBy: String = "IsFolder,SortName",
    descending: Boolean = false,
    unwatchedOnly: Boolean = false,
): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.items(
        uid, parentId,
        sortBy = sortBy,
        sortOrder = if (descending) "Descending" else "Ascending",
        filters = if (unwatchedOnly) "IsUnplayed" else null,
    ).Items
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
        subtitle = when (d.Type) {
            "Episode" -> buildString {
                d.SeriesName?.let { append(it) }
                val s = d.ParentIndexNumber; val e = d.IndexNumber
                if (s != null && e != null) { if (isNotEmpty()) append(" · "); append("S%02dE%02d".format(s, e)) }
            }
            "Audio" -> d.AlbumArtist.orEmpty()
            else -> ""
        },
        played = d.UserData?.Played == true,
        unplayedCount = d.UserData?.UnplayedItemCount ?: 0,
        favorite = d.UserData?.IsFavorite == true,
        number = d.IndexNumber,
        fileInfo = d.MediaSources.firstOrNull()?.toFileInfo(),
        providerIds = d.ProviderIds.orEmpty(),
    )
}

/**
 * Deletes an item and its file from Jellyfin.
 *
 * On its own this is only half a deletion in an *arr setup: Radarr/Sonarr still hold the entry,
 * notice the missing file on their next scan and re-download it while it stays monitored. The
 * caller is expected to offer the paired removal — see [arrFindByProviderId].
 */
suspend fun jellyfinDeleteItem(config: ServiceConfig, itemId: String): String =
    destructive("delete Jellyfin item $itemId (removes the file)") {
        withContext(Dispatchers.IO) {
            try {
                val token = jellyfinAccessToken(config)
                val api = jfApi(config, token)
                jellyfinResolveUserId(config, api) // fails fast with a clear error if auth is broken
                okOr(api.deleteItem(itemId), "deleted")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** The first media source turned into what the FILE section renders. */
private fun JfDetailMediaSource.toFileInfo() = JellyFileInfo(
    container = Container?.substringBefore(',').orEmpty(),
    sizeBytes = Size ?: 0L,
    path = Path.orEmpty(),
    bitrate = Bitrate ?: 0,
    streams = MediaStreams.map { s ->
        JellyStream(
            type = s.Type,
            codec = s.Codec.orEmpty(),
            profile = s.Profile.orEmpty(),
            language = s.DisplayLanguage ?: s.Language.orEmpty(),
            displayTitle = s.DisplayTitle.orEmpty(),
            width = s.Width ?: 0,
            height = s.Height ?: 0,
            frameRate = s.AverageFrameRate ?: s.RealFrameRate ?: 0.0,
            bitDepth = s.BitDepth ?: 0,
            bitrate = s.BitRate ?: 0,
            channels = s.Channels ?: 0,
            channelLayout = s.ChannelLayout.orEmpty(),
            sampleRate = s.SampleRate ?: 0,
            videoRange = s.VideoRange.orEmpty(),
            isDefault = s.IsDefault,
            isForced = s.IsForced,
            isExternal = s.IsExternal,
        )
    },
)

/** The user's favourites across the whole library, newest names first. */
suspend fun jellyfinFavorites(config: ServiceConfig): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.itemQuery(uid, filters = "IsFavorite", types = "Movie,Series,Episode,MusicAlbum", limit = 40)
        .Items.map { it.toMediaItem(config, token) }
}

/** Ids among [ids] that the user has already watched — used to clean up finished downloads. */
suspend fun jellyfinPlayedIds(config: ServiceConfig, ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
    if (ids.isEmpty()) return@withContext emptySet()
    runCatching {
        val token = jellyfinAccessToken(config)
        val api = jfApi(config, token)
        val uid = jellyfinResolveUserId(config, api)
        api.itemQuery(uid, ids = ids.joinToString(","), limit = ids.size)
            .Items.filter { it.UserData?.Played == true }.map { it.Id }.toSet()
    }.getOrDefault(emptySet())
}

/** Add/remove a favourite. */
suspend fun jellyfinSetFavorite(config: ServiceConfig, itemId: String, favorite: Boolean): Unit = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val resp = if (favorite) api.markFavorite(uid, itemId) else api.unmarkFavorite(uid, itemId)
    if (!resp.isSuccessful) error("HTTP ${resp.code()}")
}

/** Start [itemId] on another Jellyfin client (the sessions list shows which can be controlled). */
suspend fun jellyfinPlayOnSession(config: ServiceConfig, sessionId: String, itemId: String): String = destructive("play an item on session $sessionId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).playOn(sessionId, itemId), "sent to the other device")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/**
 * Mark [itemId] watched or unwatched for the current user. On a Series or Season the server cascades
 * the change to every episode underneath — which is why the UI confirms before doing it to a folder.
 * Not gated by safe mode: it's user data, reversible with one tap, same class as a progress report.
 */
suspend fun jellyfinSetPlayed(config: ServiceConfig, itemId: String, played: Boolean): Unit = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val resp = if (played) api.markPlayed(uid, itemId) else api.markUnplayed(uid, itemId)
    if (!resp.isSuccessful) error("HTTP ${resp.code()}")
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

// ---- Identify: pinning an item to the right metadata entry ----
//
// A plain refresh would only re-derive the same wrong guess from the same filename, which is
// why this is a two-step: ask the providers what they have, then pin the chosen one.

/** Wraps the provider's own result object so it can be handed back to Jellyfin verbatim. */
data class JellyIdentifyCandidateRaw(val json: String)

/**
 * Metadata candidates for [itemId]. [kind] is the Jellyfin item type — a series must not be
 * looked up against the movie database, so it decides the endpoint.
 */
suspend fun jellyfinIdentifyCandidates(
    config: ServiceConfig,
    itemId: String,
    kind: String,
    name: String,
    year: Int?,
): List<JellyIdentifyCandidate> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val endpoint = when (kind) {
        "Series" -> "Series"
        "Episode" -> "Episode"
        "MusicAlbum" -> "MusicAlbum"
        else -> "Movie"
    }
    val body = buildJsonObject {
        putJsonObject("SearchInfo") {
            put("Name", name)
            if (year != null) put("Year", year)
            put("ItemId", itemId)
        }
        put("ItemId", itemId)
        // Ask everything that is configured; a disabled provider is usually why nothing matched.
        put("IncludeDisabledProviders", true)
    }
    api.remoteSearch(endpoint, body).map { o ->
        JellyIdentifyCandidate(
            name = jsStr(o, "Name") ?: "?",
            year = jsInt(o, "ProductionYear") ?: 0,
            provider = jsStr(o, "SearchProviderName") ?: "",
            imageUrl = jsStr(o, "ImageUrl") ?: "",
            raw = json.encodeToString(JsonObject.serializer(), o),
        )
    }
}

/** Pins [itemId] to the chosen candidate and pulls its artwork along with it. */
suspend fun jellyfinApplyIdentify(
    config: ServiceConfig,
    itemId: String,
    candidate: JellyIdentifyCandidateRaw,
): String = destructive("re-identify Jellyfin item $itemId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val body = json.parseToJsonElement(candidate.json).jsonObject
            okOr(jfApi(config, token).applyRemoteSearch(itemId, replaceAllImages = true, body = body), "identified")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

// ---- Subtitles ----
//
// Jellyfin fetches these itself, per title. That covers what a dedicated subtitle service would
// do, without another process on a phone-sized server.

/**
 * Subtitle candidates for [itemId] in [language] (three-letter ISO, e.g. "ger").
 *
 * Ordered by usefulness rather than by whatever the provider returned: a hash match was made
 * for this exact file and will be in sync, so it goes first; after that the most downloaded,
 * which is the best available proxy for "not a broken rip".
 */
suspend fun jellyfinSubtitleCandidates(
    config: ServiceConfig,
    itemId: String,
    language: String,
): List<JellySubtitle> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).subtitleSearch(itemId, language).map { o ->
        JellySubtitle(
            id = jsStr(o, "Id") ?: "",
            provider = jsStr(o, "ProviderName") ?: "",
            name = jsStr(o, "Name") ?: "",
            format = jsStr(o, "Format") ?: "",
            downloads = jsInt(o, "DownloadCount") ?: 0,
            hashMatch = jsBool(o, "IsHashMatch") == true,
            forced = jsBool(o, "IsForced") == true,
        )
    }.sortedWith(compareByDescending<JellySubtitle> { it.hashMatch }.thenByDescending { it.downloads })
}

/** Downloads a chosen subtitle onto the item. */
suspend fun jellyfinDownloadSubtitle(
    config: ServiceConfig,
    itemId: String,
    subtitleId: String,
): String = destructive("download a subtitle for Jellyfin item $itemId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).subtitleDownload(itemId, subtitleId), "downloaded")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}
