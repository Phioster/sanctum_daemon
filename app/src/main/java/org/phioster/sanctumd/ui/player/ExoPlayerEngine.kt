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

    override fun autoSelectSubtitle(languages: List<String>): Boolean {
        if (released) return false
        val langs = languages.map { it.lowercase() }.toSet()
        // Collect German subtitle candidates, tracking whether each is forced.
        val matches = mutableListOf<Triple<Tracks.Group, Int, Boolean>>()
        exo.currentTracks.groups.forEach { g ->
            if (g.type != C.TRACK_TYPE_TEXT) return@forEach
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.lowercase()
                val label = f.label?.lowercase().orEmpty()
                val isGerman = lang in langs || label.contains("deutsch") || label.contains("german")
                if (!isGerman) continue
                val forced = (f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0 ||
                    label.contains("forced") || label.contains("erzwungen")
                matches += Triple(g.mediaTrackGroup, i, forced)
            }
        }
        // Prefer a forced German track; otherwise the first normal German track.
        val pick = matches.firstOrNull { it.third } ?: matches.firstOrNull() ?: return false
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(pick.first, pick.second))
            .build()
        return true
    }

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
