package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.ServiceConfig

/** A playable audio track resolved for the background music player. Token rides in [streamUrl]/[artUrl]
 *  (api_key); any per-service custom headers (CF Access) are added by [MusicService]. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val artUrl: String,
)

private fun audioStreamUrl(base: String, id: String, token: String) =
    "${base}Audio/$id/stream?static=true&api_key=$token"

private fun primaryArtUrl(base: String, id: String, token: String) =
    "${base}Items/$id/Images/Primary?maxHeight=400&api_key=$token"

/** All audio tracks of an album, in disc/track order. Tracks without their own art fall back to the album art. */
suspend fun jellyfinAlbumTracks(config: ServiceConfig, albumId: String, albumName: String = ""): List<MusicTrack> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val base = config.normalizedBaseUrl
    val albumArt = primaryArtUrl(base, albumId, token)
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
                streamUrl = audioStreamUrl(base, item.Id, token),
                artUrl = if (hasOwnArt) primaryArtUrl(base, item.Id, token) else albumArt,
            )
        }
}

/** A single audio track (played as a one-item queue). */
suspend fun jellyfinTrack(config: ServiceConfig, itemId: String): MusicTrack = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val base = config.normalizedBaseUrl
    val d = api.itemDetail(uid, itemId)
    val hasArt = !d.ImageTags?.get("Primary").isNullOrBlank()
    MusicTrack(
        id = d.Id,
        title = d.Name,
        artist = d.AlbumArtist.orEmpty(),
        album = "",
        streamUrl = audioStreamUrl(base, d.Id, token),
        artUrl = if (hasArt) primaryArtUrl(base, d.Id, token) else "",
    )
}
