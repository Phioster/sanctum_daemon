package org.phioster.sanctumd.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Media3 ExoPlayer implementation of [MediaPlayerEngine]. Handles progressive/direct files and
 *  transcoded HLS; the device's hardware decoders cover most codecs, the rest the server transcodes. */
@UnstableApi
class ExoPlayerEngine(private val context: Context) : MediaPlayerEngine {
    private val exo: ExoPlayer = ExoPlayer.Builder(context).build()
    private var lastError: String? = null
    private var released = false
    private var lastState = PlaybackState()

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { lastError = error.errorCodeName }
        })
    }

    override fun prepare(url: String, isHls: Boolean, startPositionMs: Long, headers: Map<String, String>) {
        lastError = null
        val http = url.startsWith("http")
        val dataSourceFactory = if (http) {
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
        } else {
            DefaultDataSource.Factory(context)
        }
        val item = MediaItem.fromUri(url)
        val source = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
        }
        exo.setMediaSource(source)
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun play() { exo.play() }
    override fun pause() { exo.pause() }
    override fun togglePlay() { if (exo.isPlaying) exo.pause() else exo.play() }
    override fun seekTo(positionMs: Long) { exo.seekTo(positionMs.coerceAtLeast(0)) }
    override fun seekBy(deltaMs: Long) {
        val target = (exo.currentPosition + deltaMs).coerceIn(0, if (exo.duration > 0) exo.duration else Long.MAX_VALUE)
        exo.seekTo(target)
    }

    override fun snapshot(): PlaybackState {
        if (released) return lastState
        lastState = PlaybackState(
            positionMs = exo.currentPosition.coerceAtLeast(0),
            durationMs = if (exo.duration > 0) exo.duration else 0,
            bufferedMs = exo.bufferedPosition.coerceAtLeast(0),
            isPlaying = exo.isPlaying,
            isBuffering = exo.playbackState == Player.STATE_BUFFERING,
            ended = exo.playbackState == Player.STATE_ENDED,
            error = lastError,
        )
        return lastState
    }

    override fun videoAspect(): Float {
        if (released) return 0f
        val v = exo.videoFormat ?: return 0f
        val par = v.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
        val reported = if (v.height > 0) v.width * par / v.height else 0f
        return displayAspect(reported, v.width, v.height)
    }

    override fun stats(): PlaybackStats {
        if (released) return PlaybackStats()
        val v = exo.videoFormat
        val a = exo.audioFormat
        val bitrate = listOf(v?.bitrate ?: androidx.media3.common.Format.NO_VALUE, v?.averageBitrate ?: androidx.media3.common.Format.NO_VALUE)
            .firstOrNull { it > 0 } ?: 0
        return PlaybackStats(
            width = v?.width?.takeIf { it > 0 } ?: 0,
            height = v?.height?.takeIf { it > 0 } ?: 0,
            videoCodec = codecName(v?.sampleMimeType),
            audioCodec = codecName(a?.sampleMimeType),
            bitrateKbps = if (bitrate > 0) bitrate / 1000 else 0,
            fps = v?.frameRate?.takeIf { it > 0 } ?: 0f,
            bufferedPercent = exo.bufferedPercentage.coerceIn(0, 100),
            hwDecode = "", // ExoPlayer doesn't expose the decoder name simply
        )
    }

    // "video/avc" -> "avc"/"h264", strips the type prefix and normalises the common names.
    private fun codecName(mime: String?): String = when (val c = mime?.substringAfter('/')?.lowercase()) {
        null -> ""
        "avc" -> "h264"
        "hevc" -> "h265"
        else -> c
    }

    // Remembers, per kind, which media track group + index each exposed TrackOption id maps to,
    // so selectTrack() can build the right override.
    private val lookup = mutableMapOf<String, Pair<Tracks.Group, Int>>()

    private fun c(kind: TrackKind) = if (kind == TrackKind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

    override fun tracks(kind: TrackKind): List<TrackOption> {
        if (released) return emptyList()
        val type = c(kind)
        val out = mutableListOf<TrackOption>()
        var n = 0
        exo.currentTracks.groups.forEach { group ->
            if (group.type == type) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val f = group.getTrackFormat(i)
                    val id = "$type:$n"; n++
                    lookup[id] = group to i
                    val label = f.label
                        ?: f.language?.let { java.util.Locale(it).displayLanguage.ifBlank { it } }
                        ?: "track ${out.size + 1}"
                    out += TrackOption(id, label, group.isTrackSelected(i))
                }
            }
        }
        return out
    }

    override fun selectTrack(kind: TrackKind, id: String?) {
        if (released) return
        val type = c(kind)
        val builder = exo.trackSelectionParameters.buildUpon()
        if (id == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val (group, index) = lookup[id] ?: return
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        exo.trackSelectionParameters = builder.build()
    }

    /** Text/audio tracks matching [langs] (by language tag or label), with their forced flag. */
    private fun matches(type: Int, langs: Set<String>): List<Triple<Tracks.Group, Int, Boolean>> {
        val out = mutableListOf<Triple<Tracks.Group, Int, Boolean>>()
        exo.currentTracks.groups.forEach { g ->
            if (g.type != type) return@forEach
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.lowercase()
                val label = f.label?.lowercase().orEmpty()
                if (lang !in langs && langs.none { label.contains(it) }) continue
                val forced = (f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0 ||
                    label.contains("forced") || label.contains("erzwungen")
                out += Triple(g, i, forced)
            }
        }
        return out
    }

    override fun applyTrackPreferences(audioLang: String, subLang: String, subMode: String) {
        if (released) return
        var params = exo.trackSelectionParameters.buildUpon()
        if (audioLang.isNotBlank()) {
            matches(C.TRACK_TYPE_AUDIO, languageAliases(audioLang)).firstOrNull()?.let { (g, i, _) ->
                params = params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            }
        }
        val pick = if (subMode == "off" || subLang.isBlank()) {
            null
        } else {
            val subs = matches(C.TRACK_TYPE_TEXT, languageAliases(subLang))
            subs.firstOrNull { it.third } ?: subs.firstOrNull().takeIf { subMode == "any" }
        }
        params = if (pick != null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(pick.first.mediaTrackGroup, pick.second))
        } else {
            // Deliberately off: in "forced" mode a file whose forced track is missing shows nothing,
            // instead of falling back to whatever track the file marks as default.
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        }
        exo.trackSelectionParameters = params.build()
    }

    /** ExoPlayer here renders into a bare SurfaceView (no PlayerView/SubtitleView), so there is no
     *  subtitle styling surface to talk to. mpv, the primary engine, handles both. */
    override fun setSubtitleScale(scale: Float) = Unit
    override fun setSubtitleDelay(delayMs: Long) = Unit

    override fun setSpeed(speed: Float) { exo.setPlaybackSpeed(speed) }
    override fun currentSpeed(): Float = exo.playbackParameters.speed

    override fun release() {
        if (released) return
        released = true
        exo.release()
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
        )
    }
}
