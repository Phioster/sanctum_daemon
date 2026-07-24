package org.phioster.sanctumd.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
