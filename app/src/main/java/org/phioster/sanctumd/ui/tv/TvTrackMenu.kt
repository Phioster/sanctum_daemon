package org.phioster.sanctumd.ui.tv

import org.phioster.sanctumd.ui.player.MediaPlayerEngine
import org.phioster.sanctumd.ui.player.TrackKind
import org.phioster.sanctumd.ui.player.TrackOption

/** One line in the track menu. Headers are shown but skipped when moving the selection. */
internal data class TvMenuEntry(
    val label: String,
    val header: Boolean = false,
    val selected: Boolean = false,
    val onSelect: () -> Unit = {},
)

/**
 * What the track menu offers: the audio and subtitle tracks the engine reports, plus the two
 * picture settings. Read when the menu opens, not on every recomposition, because a list that
 * reshuffled under the selection would be unusable.
 */
internal fun tvTrackMenu(
    engine: MediaPlayerEngine,
    directOutput: Boolean,
    matchRefresh: Boolean,
    autoSkip: Boolean,
    onToggleAutoSkip: () -> Unit,
    onToggleRefresh: () -> Unit,
    /** A track was picked by hand; null id = subtitles off. Remembered per series. */
    onTrackPicked: (TrackKind, String?) -> Unit,
    onToggleDirect: () -> Unit,
): List<TvMenuEntry> = buildList {
    add(TvMenuEntry("audio", header = true))
    val audio = engine.tracks(TrackKind.AUDIO)
    if (audio.isEmpty()) add(TvMenuEntry("  no audio track", header = true))
    audio.forEach { t: TrackOption ->
        add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.AUDIO, t.id); onTrackPicked(TrackKind.AUDIO, t.id) })
    }
    add(TvMenuEntry("subtitles", header = true))
    if (directOutput) add(TvMenuEntry("  (text subtitles only; image subtitles like PGS stay off)", header = true))
    val subs = engine.tracks(TrackKind.SUBTITLE)
    add(TvMenuEntry("  off", selected = subs.none { it.selected }) { engine.selectTrack(TrackKind.SUBTITLE, null); onTrackPicked(TrackKind.SUBTITLE, null) })
    subs.forEach { t: TrackOption ->
        add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.SUBTITLE, t.id); onTrackPicked(TrackKind.SUBTITLE, t.id) })
    }
    add(TvMenuEntry("playback", header = true))
    add(TvMenuEntry("  skip intro and credits automatically", selected = autoSkip, onSelect = onToggleAutoSkip))
    add(TvMenuEntry("picture", header = true))
    add(TvMenuEntry("  match the refresh rate to the film", selected = matchRefresh, onSelect = onToggleRefresh))
    add(TvMenuEntry("  direct output (recommended)", selected = directOutput, onSelect = onToggleDirect))
}
