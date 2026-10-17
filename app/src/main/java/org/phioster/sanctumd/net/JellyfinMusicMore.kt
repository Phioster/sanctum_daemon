package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lyrics, Instant Mix and "more like this". Routes from Jellyfin's LyricsController
 * (Audio/{itemId}/Lyrics), InstantMixController (Items/{itemId}/InstantMix) and
 * LibraryController (Items/{itemId}/Similar); the lyric shape is MediaBrowser.Model.Lyrics.
 */
internal interface JfMusicApi {
    @GET("Audio/{id}/Lyrics") suspend fun lyrics(@Path("id") id: String): Response<ResponseBody>

    @GET("Items/{id}/InstantMix") suspend fun instantMix(
        @Path("id") id: String,
        @Query("userId") uid: String,
        @Query("limit") limit: Int,
    ): JfItemsResp

    @GET("Items/{id}/Similar") suspend fun similar(
        @Path("id") id: String,
        @Query("userId") uid: String,
        @Query("limit") limit: Int,
    ): JfItemsResp
}

/** One line of a song. [startMs] is null for lyrics without timing. */
data class LyricLine(val text: String, val startMs: Long?)

data class MusicLyrics(val lines: List<LyricLine>) {
    /** Timed lyrics follow the song; untimed ones are just read. */
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.startMs != null }

    /** The line being sung at [positionMs], or -1 before the first. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var found = -1
        lines.forEachIndexed { i, l -> if ((l.startMs ?: Long.MAX_VALUE) <= positionMs) found = i }
        return found
    }
}

private val lenient = Json { ignoreUnknownKeys = true }

/** A LyricDto: Lyrics[] of {Text, Start in ticks}, Metadata.Offset in ticks shifts them all. */
internal fun lyricsOf(body: String): MusicLyrics? {
    val root = runCatching { lenient.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val offset = (root["Metadata"] as? JsonObject)?.let { jsLong(it, "Offset") } ?: 0L
    val lines = (root["Lyrics"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        LyricLine(jsStr(o, "Text").orEmpty(), jsLong(o, "Start")?.let { ticksToMs(it + offset).coerceAtLeast(0) })
    }
    return MusicLyrics(lines).takeIf { l -> l.lines.any { it.text.isNotBlank() } }
}

/** The track's lyrics, or null when the server has none (it answers 404 then). */
suspend fun jellyfinLyrics(config: ServiceConfig, trackId: String): MusicLyrics? = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val r = apiFor<JfMusicApi>(config, jellyfinAuth(token)).lyrics(trackId)
    when {
        r.code() == 404 -> null
        !r.isSuccessful -> throw IllegalStateException("HTTP ${r.code()}")
        else -> r.body()?.string()?.let { lyricsOf(it) }
    }
}

/** A track met outside its album (in a mix), with the album's cover when it has none itself. */
internal fun JfItem.toMusicTrack(base: String, headers: Map<String, String>): MusicTrack {
    val ownArt = !ImageTags?.get("Primary").isNullOrBlank()
    val albumArt = !AlbumId.isNullOrBlank() && !AlbumPrimaryImageTag.isNullOrBlank()
    return MusicTrack(
        id = Id,
        title = Name,
        artist = AlbumArtist.orEmpty(),
        album = Album.orEmpty(),
        streamUrl = audioStreamUrl(base, Id),
        artUrl = trackArtUrl(base, Id, ownArt, AlbumId.orEmpty(), albumArt),
        headers = headers,
        number = IndexNumber,
        durationMs = ticksToMs(RunTimeTicks),
    )
}

/** Jellyfin's Instant Mix from a track, album, artist or playlist: songs that go with it. */
suspend fun jellyfinInstantMix(config: ServiceConfig, itemId: String, limit: Int = 50): List<MusicTrack> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val uid = jellyfinResolveUserId(config, jfApi(config, token))
    val headers = config.customHeaders + jellyfinAuth(token)
    apiFor<JfMusicApi>(config, jellyfinAuth(token)).instantMix(itemId, uid, limit).Items
        .filter { it.Type == "Audio" }
        .map { it.toMusicTrack(config.normalizedBaseUrl, headers) }
}

/** Albums like this one, for the row under the running order. */
suspend fun jellyfinSimilar(config: ServiceConfig, itemId: String, limit: Int = 12): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val uid = jellyfinResolveUserId(config, jfApi(config, token))
    apiFor<JfMusicApi>(config, jellyfinAuth(token)).similar(itemId, uid, limit).Items
        .filter { it.Id != itemId }
        .map { it.toMediaItem(config, token) }
}
