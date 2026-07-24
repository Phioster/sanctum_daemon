package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.ResponseBody
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---- Playback: streaming source resolution + progress reporting ----

/** One media source option the server offers for an item. */
@Serializable internal data class JfMediaSource(
    val Id: String = "",
    val Container: String? = null,
    val SupportsDirectPlay: Boolean = false,
    val SupportsDirectStream: Boolean = false,
    val SupportsTranscoding: Boolean = false,
    val TranscodingUrl: String? = null,
    val RunTimeTicks: Long? = null,
)
@Serializable internal data class JfPlaybackInfoResp(
    val MediaSources: List<JfMediaSource> = emptyList(),
    val PlaySessionId: String? = null,
)
/** Minimal item read used only to recover the resume position + duration. */
@Serializable internal data class JfPlayItem(
    val RunTimeTicks: Long? = null,
    val UserData: JfUserData? = null,
)

internal interface JellyfinPlaybackApi {
    @POST("Items/{id}/PlaybackInfo")
    suspend fun playbackInfo(
        @Path("id") id: String,
        @Query("UserId") userId: String,
        @Body body: JsonObject,
    ): JfPlaybackInfoResp

    @GET("Users/{uid}/Items/{id}")
    suspend fun playItem(@Path("uid") uid: String, @Path("id") id: String): JfPlayItem

    @POST("Sessions/Playing") suspend fun reportStart(@Body body: JsonObject): Response<ResponseBody>
    @POST("Sessions/Playing/Progress") suspend fun reportProgress(@Body body: JsonObject): Response<ResponseBody>
    @POST("Sessions/Playing/Stopped") suspend fun reportStopped(@Body body: JsonObject): Response<ResponseBody>
}

internal fun jfPlaybackApi(config: ServiceConfig, token: String) =
    apiFor<JellyfinPlaybackApi>(config, mapOf("X-Emby-Token" to token))

/** A resolved, playable stream for one Jellyfin item. */
data class PlaybackSource(
    val itemId: String,
    val url: String,
    val isHls: Boolean, // true = transcoded HLS (HlsMediaSource); false = progressive/direct file
    val mediaSourceId: String,
    val playSessionId: String,
    val startPositionMs: Long, // resume position, 0 = start
    val runTimeMs: Long, // total duration, 0 = unknown
    val authHeaders: Map<String, String>, // X-Emby-Token for the player's HTTP data source
)

private const val TICKS_PER_MS = 10_000L

/**
 * A compact device profile: ExoPlayer direct-plays common containers/codecs; anything else the
 * server transcodes to an HLS/ts h264+aac stream (handled by media3-exoplayer-hls). Kept lean —
 * the server only needs to know what we can decode vs. what to transcode. [maxBitrate] (bps), when
 * set, caps streaming and forces a transcode for anything above it (the quality selector).
 */
private fun deviceProfile(maxBitrate: Int?): JsonObject = buildJsonObject {
    val cap = maxBitrate ?: 120_000_000
    put("MaxStreamingBitrate", cap)
    put("MaxStaticBitrate", cap)
    putJsonArray("DirectPlayProfiles") {
        addJsonObject {
            put("Container", "mp4,m4v,mkv,webm,mov")
            put("Type", "Video")
            put("VideoCodec", "h264,hevc,vp8,vp9,av1,mpeg4")
            put("AudioCodec", "aac,mp3,ac3,eac3,opus,flac,vorbis,alac")
        }
    }
    putJsonArray("TranscodingProfiles") {
        addJsonObject {
            put("Container", "ts")
            put("Type", "Video")
            put("VideoCodec", "h264")
            put("AudioCodec", "aac,mp3,ac3")
            put("Protocol", "hls")
            put("Context", "Streaming")
            put("MaxAudioChannels", "2")
            put("MinSegments", 1)
            put("BreakOnNonKeyFrames", true)
        }
    }
    putJsonArray("SubtitleProfiles") {
        addJsonObject { put("Format", "vtt"); put("Method", "Hls") }
        addJsonObject { put("Format", "srt"); put("Method", "External") }
    }
    putJsonArray("ContainerProfiles") {}
    putJsonArray("CodecProfiles") {}
}

/**
 * The PlaybackInfoDto POST body. For **Auto** quality ([maxBitrate] null) we deliberately send NO
 * device profile: the server then defaults to direct-play whenever it can (matches what actually
 * plays here — a restrictive profile made it needlessly transcode). Only when a quality cap is
 * chosen do we send a profile whose bitrate + HLS transcode target force the server down.
 */
private fun playbackInfoBody(userId: String, maxBitrate: Int?): JsonObject = buildJsonObject {
    put("UserId", userId)
    if (maxBitrate != null) {
        put("DeviceProfile", deviceProfile(maxBitrate))
        put("MaxStreamingBitrate", maxBitrate)
    }
    put("EnableDirectPlay", true)
    put("EnableDirectStream", true)
    put("EnableTranscoding", true)
    put("AllowVideoStreamCopy", true)
    put("AllowAudioStreamCopy", true)
}

/**
 * Resolves a playable stream for [itemId]. Asks the server (PlaybackInfo + our device profile)
 * whether the file can be sent as-is (static stream) or must be transcoded (HLS), and recovers the
 * saved resume position. The access token travels in a header, never in the URL — consistent with
 * [jellyfinImageHeaders] so it stays out of any cache.
 */
suspend fun jellyfinPlaybackSource(config: ServiceConfig, itemId: String, maxBitrate: Int? = null): PlaybackSource = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfPlaybackApi(config, token)
    val jfApiClient = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, jfApiClient)

    val resumeTicks = runCatching { api.playItem(uid, itemId).UserData?.PlaybackPositionTicks }.getOrNull() ?: 0L
    val info = api.playbackInfo(itemId, uid, playbackInfoBody(uid, maxBitrate))
    val ms = info.MediaSources.firstOrNull()
        ?: error("no media sources for item $itemId")
    val psid = info.PlaySessionId ?: ""
    val base = config.normalizedBaseUrl // ends with '/'

    // Prefer sending the original file (direct play/stream) — only fall back to a transcode when the
    // server can't do either, so a playable file never needlessly goes through ffmpeg.
    val transcode = ms.TranscodingUrl
    val (url, isHls) = when {
        ms.SupportsDirectPlay || ms.SupportsDirectStream ->
            "${base}Videos/$itemId/stream?static=true&mediaSourceId=${ms.Id}&playSessionId=$psid" to false
        !transcode.isNullOrBlank() ->
            base.trimEnd('/') + transcode to true
        else -> error("item $itemId is not playable")
    }

    PlaybackSource(
        itemId = itemId,
        url = url,
        isHls = isHls,
        mediaSourceId = ms.Id,
        playSessionId = psid,
        startPositionMs = resumeTicks / TICKS_PER_MS,
        runTimeMs = (ms.RunTimeTicks ?: 0L) / TICKS_PER_MS,
        authHeaders = mapOf("X-Emby-Token" to token) + config.customHeaders,
    )
}

private fun playStateBody(src: PlaybackSource, positionMs: Long, isPaused: Boolean? = null): JsonObject =
    buildJsonObject {
        put("ItemId", src.itemId)
        put("MediaSourceId", src.mediaSourceId)
        put("PlaySessionId", src.playSessionId)
        put("PositionTicks", positionMs * TICKS_PER_MS)
        if (isPaused != null) put("IsPaused", isPaused)
    }

/** Tells the server playback began — so it appears in "now playing" and marks the item watching. */
suspend fun jellyfinReportStart(config: ServiceConfig, src: PlaybackSource, positionMs: Long): Unit = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        jfPlaybackApi(config, token).reportStart(playStateBody(src, positionMs))
    }
    Unit
}

/** Periodic progress ping — updates the resume position + Continue Watching. */
suspend fun jellyfinReportProgress(config: ServiceConfig, src: PlaybackSource, positionMs: Long, isPaused: Boolean): Unit = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        jfPlaybackApi(config, token).reportProgress(playStateBody(src, positionMs, isPaused))
    }
    Unit
}

/** Playback ended — persists the final resume position (or marks played when near the end). */
suspend fun jellyfinReportStopped(config: ServiceConfig, src: PlaybackSource, positionMs: Long): Unit = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        jfPlaybackApi(config, token).reportStopped(playStateBody(src, positionMs))
    }
    Unit
}
