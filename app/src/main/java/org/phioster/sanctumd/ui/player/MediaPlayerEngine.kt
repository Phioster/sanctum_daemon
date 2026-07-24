package org.phioster.sanctumd.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the player overlay should play: a Jellyfin item (streamed) or a downloaded local file. */
internal data class PlayRequest(val itemId: String, val title: String, val localFileUri: String? = null)

/** A selectable audio or subtitle track. [id] is opaque (engine-specific); [selected] is the current pick. */
data class TrackOption(val id: String, val label: String, val selected: Boolean)

enum class TrackKind { AUDIO, SUBTITLE }

/** Playback speeds offered in the settings menu. */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

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

    /** Available audio/subtitle tracks (subtitles include an implicit "off" handled by the UI). */
    fun tracks(kind: TrackKind): List<TrackOption>
    /** Select a track by its [TrackOption.id]; null disables the kind (used to turn subtitles off). */
    fun selectTrack(kind: TrackKind, id: String?)
    fun setSpeed(speed: Float)
    fun currentSpeed(): Float

    fun release()

    /** Renders the video output. Engine-specific (ExoPlayer PlayerView here; an mpv SurfaceView later). */
    @Composable fun VideoSurface(modifier: Modifier)
}
