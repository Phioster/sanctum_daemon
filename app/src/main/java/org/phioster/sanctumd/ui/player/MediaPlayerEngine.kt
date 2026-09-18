package org.phioster.sanctumd.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the player overlay should play: a Jellyfin item (streamed) or a downloaded local file. */
internal data class PlayRequest(val itemId: String, val title: String, val localFileUri: String? = null)

/** A selectable audio or subtitle track. [id] is opaque (engine-specific); [selected] is the current pick. */
data class TrackOption(val id: String, val label: String, val selected: Boolean)

enum class TrackKind { AUDIO, SUBTITLE }

/**
 * ISO-639 aliases, so a user's "de" also matches a track tagged `deu`/`ger` or labelled "Deutsch".
 * Track metadata in the wild is inconsistent; matching on one code alone misses most files.
 */
internal fun languageAliases(code: String): Set<String> = when (code.lowercase()) {
    "de" -> setOf("de", "deu", "ger", "de-de", "german", "deutsch")
    "en" -> setOf("en", "eng", "en-us", "en-gb", "english", "englisch")
    "fr" -> setOf("fr", "fra", "fre", "french", "französisch")
    "es" -> setOf("es", "spa", "esp", "spanish", "spanisch")
    "it" -> setOf("it", "ita", "italian", "italienisch")
    "nl" -> setOf("nl", "nld", "dut", "dutch")
    "pl" -> setOf("pl", "pol", "polish")
    "pt" -> setOf("pt", "por", "portuguese")
    "ru" -> setOf("ru", "rus", "russian")
    "ja" -> setOf("ja", "jpn", "japanese")
    "ko" -> setOf("ko", "kor", "korean")
    "tr" -> setOf("tr", "tur", "turkish")
    else -> setOf(code.lowercase())
}

/** Languages offered in the playback settings; "" = keep whatever the file defaults to. */
val LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "" to "file default",
    "de" to "Deutsch",
    "en" to "English",
    "fr" to "Français",
    "es" to "Español",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "pt" to "Português",
    "ru" to "Русский",
    "ja" to "日本語",
    "ko" to "한국어",
    "tr" to "Türkçe",
)

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

/** Live technical playback metrics for the player's info panel ("stats for nerds"). Fields are 0/blank
 *  when the engine can't report them. [bufferedPercent] is the cache/buffer fill 0..100. */
data class PlaybackStats(
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val bitrateKbps: Int = 0,
    val fps: Float = 0f,
    val bufferedPercent: Int = 0,
    val hwDecode: String = "",
    /** Frames the decoder threw away because it could not keep up. */
    val droppedFrames: Int = 0,
    /** Frames shown later than they should have been. Kept apart from [droppedFrames] on purpose:
     *  a device that is too slow *drops*, whereas a frame rate that does not divide into the
     *  panel's refresh rate only ever arrives *late*. Conflating them hides which one you have. */
    val delayedFrames: Int = 0,
    /** The file's own frame rate, as opposed to [fps] which is what is actually being rendered. */
    val containerFps: Float = 0f,
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

    /** Live technical metrics for the info panel; may be all-default while nothing is decoding yet. */
    fun stats(): PlaybackStats

    /**
     * The shape of the picture, width divided by height, or 0 while nothing is known yet.
     *
     * The caller sizes the surface by this instead of filling the screen: see [displayAspect] for
     * why the zero-copy output cannot letterbox by itself.
     */
    fun videoAspect(): Float

    /** Available audio/subtitle tracks (subtitles include an implicit "off" handled by the UI). */
    fun tracks(kind: TrackKind): List<TrackOption>
    /** Select a track by its [TrackOption.id]; null disables the kind (used to turn subtitles off). */
    fun selectTrack(kind: TrackKind, id: String?)
    /**
     * Apply the user's track preferences to the loaded file.
     *
     * [audioLang] picks the first audio track in that language (blank = leave the file's default).
     * [subMode] decides subtitles: "forced" takes only a *forced* track in [subLang] and otherwise
     * switches them **off** (rather than letting the file's default sneak in), "any" falls back to a
     * normal track in that language, "off" disables them outright.
     */
    fun applyTrackPreferences(audioLang: String, subLang: String, subMode: String)

    /** Subtitle size multiplier (1.0 = the engine's default). No-op on engines that can't style. */
    fun setSubtitleScale(scale: Float)
    /** Shift subtitles against the audio, in milliseconds (positive = later). */
    fun setSubtitleDelay(delayMs: Long)
    fun setSpeed(speed: Float)
    fun currentSpeed(): Float

    fun release()

    /** Renders the video output. Engine-specific (ExoPlayer PlayerView here; an mpv SurfaceView later). */
    @Composable fun VideoSurface(modifier: Modifier)
}
