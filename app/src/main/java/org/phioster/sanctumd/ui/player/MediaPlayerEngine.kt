package org.phioster.sanctumd.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the player overlay should play: a Jellyfin item (streamed) or a downloaded local file. */
internal data class PlayRequest(val itemId: String, val title: String, val localFileUri: String? = null)

/** Video item kinds that offer in-app playback (audio is deliberately excluded for now). */
internal val PLAYABLE_VIDEO_KINDS = setOf("Movie", "Episode", "Video", "MusicVideo")

/** Live playback state, polled by the player UI (no listener plumbing needed). */
data class PlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
)

/**
 * The playback engine, kept behind this interface so the streaming/download/reporting/UI layers stay
 * engine-agnostic. Phase 1 ships [ExoPlayerEngine]; a libmpv engine can drop in later (max codec
 * coverage) without touching anything else.
 */
interface MediaPlayerEngine {
    /** Load and start a stream. [isHls] selects the HLS source (transcoded) vs. a progressive/local
     *  file. [headers] carries auth for HTTP sources (empty for local files). */
    fun prepare(url: String, isHls: Boolean, startPositionMs: Long, headers: Map<String, String>)
    fun play()
    fun pause()
    fun togglePlay()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun snapshot(): PlaybackState
    fun release()

    /** Renders the video output. Engine-specific (ExoPlayer PlayerView here; an mpv SurfaceView later). */
    @Composable fun VideoSurface(modifier: Modifier)
}
