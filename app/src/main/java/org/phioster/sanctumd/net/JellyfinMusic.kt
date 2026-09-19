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
)

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

/** All audio tracks of an album, in disc/track order. Tracks without their own art fall back to the album art. */
suspend fun jellyfinAlbumTracks(config: ServiceConfig, albumId: String, albumName: String = ""): List<MusicTrack> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val base = config.normalizedBaseUrl
    val albumHasArt = !api.itemDetail(id = albumId, uid = uid).ImageTags?.get("Primary").isNullOrBlank()
    val headers = config.customHeaders + jellyfinAuth(token)
    api.items(uid, albumId).Items
        .filter { it.Type == "Audio" }
        .sortedWith(compareBy({ it.ParentIndexNumber ?: 0 }, { it.IndexNumber ?: 0 }))
        .map { item ->
            val hasOwnArt = !item.ImageTags?.get("Primary").isNullOrBlank()
            MusicTrack(
                id = item.Id,
                title = item.Name,
                artist = item.AlbumArtist.orEmpty(),
                album = albumName,
                streamUrl = audioStreamUrl(base, item.Id),
                artUrl = trackArtUrl(base, item.Id, hasOwnArt, albumId, albumHasArt),
                headers = headers,
            )
        }
}

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
