package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.ServiceConfig

/** A playable audio track resolved for the background music player. No secret rides in [streamUrl] or
 *  [artUrl] any more — [headers] carries the Jellyfin token and any per-service custom headers. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val artUrl: String,
    /**
     * What every request for this track needs: the service's own custom headers plus Jellyfin
     * auth. The token used to ride in the URL instead, which put it into the MediaSession's
     * metadata — and that is readable by any app on the device. See [MusicService].
     */
    val headers: Map<String, String> = emptyMap(),
    /** Track number within its disc, as the server has it; null when untagged. */
    val number: Int? = null,
    val durationMs: Long = 0,
)

/** An album with everything the album screen shows, and the queue it plays. */
data class MusicAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int?,
    val artUrl: String,
    val tracks: List<MusicTrack>,
) {
    val totalMs: Long get() = tracks.sumOf { it.durationMs }
}

/** Jellyfin counts in 100-nanosecond ticks; everything else here counts in milliseconds. */
internal fun ticksToMs(ticks: Long?): Long = (ticks ?: 0L) / 10_000L

/**
 * `m:ss`, or `h:mm:ss` once past an hour. A missing runtime reads as an em dash rather than
 * "0:00" — the server simply did not say, and a zero would claim it did.
 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun audioStreamUrl(base: String, id: String) =
    "${base}Audio/$id/stream?static=true"

internal fun primaryArtUrl(base: String, id: String) =
    "${base}Items/$id/Images/Primary?maxHeight=400"

/**
 * Which cover a track should carry: its own, else the album's, else none at all. The album's used
 * to be handed out even when the album had no cover either, so every track pointed at a URL that
 * 404s — and the media session's bitmap loader ran into it once per track, for nothing.
 */
internal fun trackArtUrl(
    base: String,
    trackId: String,
    trackHasArt: Boolean,
    albumId: String,
    albumHasArt: Boolean,
): String = when {
    trackHasArt -> primaryArtUrl(base, trackId)
    albumHasArt -> primaryArtUrl(base, albumId)
    else -> ""
}

/**
 * An album and its tracks in disc/track order — one fetch for everything the album screen needs,
 * because the screen and the playback queue are the same list and must not disagree.
 */
suspend fun jellyfinAlbum(config: ServiceConfig, albumId: String): MusicAlbum = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val base = config.normalizedBaseUrl
    val detail = api.itemDetail(id = albumId, uid = uid)
    val albumHasArt = !detail.ImageTags?.get("Primary").isNullOrBlank()
    val headers = config.customHeaders + jellyfinAuth(token)
    val tracks = api.items(uid, albumId).Items
        .filter { it.Type == "Audio" }
        .sortedWith(compareBy({ it.ParentIndexNumber ?: 0 }, { it.IndexNumber ?: 0 }))
        .map { item ->
            val hasOwnArt = !item.ImageTags?.get("Primary").isNullOrBlank()
            MusicTrack(
                id = item.Id,
                title = item.Name,
                artist = item.AlbumArtist.orEmpty(),
                album = detail.Name,
                streamUrl = audioStreamUrl(base, item.Id),
                artUrl = trackArtUrl(base, item.Id, hasOwnArt, albumId, albumHasArt),
                headers = headers,
                number = item.IndexNumber,
                durationMs = ticksToMs(item.RunTimeTicks),
            )
        }
    MusicAlbum(
        id = albumId,
        name = detail.Name,
        artist = detail.AlbumArtist.orEmpty(),
        year = detail.ProductionYear,
        artUrl = if (albumHasArt) primaryArtUrl(base, albumId) else "",
        tracks = tracks,
    )
}

/** Just the queue, for callers that only want to press play. */
suspend fun jellyfinAlbumTracks(config: ServiceConfig, albumId: String): List<MusicTrack> =
    jellyfinAlbum(config, albumId).tracks

/** A single audio track (played as a one-item queue). */
suspend fun jellyfinTrack(config: ServiceConfig, itemId: String): MusicTrack = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val base = config.normalizedBaseUrl
    val d = api.itemDetail(id = itemId, uid = uid)
    val hasArt = !d.ImageTags?.get("Primary").isNullOrBlank()
    MusicTrack(
        id = d.Id,
        title = d.Name,
        artist = d.AlbumArtist.orEmpty(),
        album = "",
        streamUrl = audioStreamUrl(base, d.Id),
        artUrl = if (hasArt) primaryArtUrl(base, d.Id) else "",
        headers = config.customHeaders + jellyfinAuth(token),
    )
}
