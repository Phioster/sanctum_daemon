package org.phioster.sanctumd.ui.stats

import org.phioster.sanctumd.model.JellyPlayEntry
import org.phioster.sanctumd.model.JellyPlaybackStats
import org.phioster.sanctumd.model.StatBar
import org.phioster.sanctumd.model.StatChart
import org.phioster.sanctumd.model.StatTile

/**
 * Turns playback data into the same tiles and charts the rest of the stats screen is made of.
 *
 * Written as a conversion rather than as its own drawing code on purpose: the first version drew
 * its own bars and tiles, which looked almost-but-not-quite like the Radarr and Sonarr charts
 * above it. Going through [StatTile] and [StatChart] makes it consistent by construction — if
 * the house style changes, this changes with it.
 */
internal fun playbackTiles(
    stats: JellyPlaybackStats,
    accentArgb: Long,
    warnArgb: Long,
): List<StatTile> {
    if (stats.plays == 0) return emptyList()
    return listOf(
        StatTile("Stunden", "%.0f".format(stats.hours), accentArgb),
        StatTile("Wiedergaben", stats.plays.toString(), accentArgb),
        // The only number here worth being unhappy about — coloured only when there is something
        // to be unhappy about, so a clean library doesn't wear a warning.
        StatTile(
            "Transkodiert",
            stats.transcodes.toString(),
            if (stats.transcodes > 0) warnArgb else accentArgb,
        ),
    )
}

internal fun playbackCharts(
    stats: JellyPlaybackStats,
    accentArgb: Long,
    warnArgb: Long,
): List<StatChart> {
    if (stats.plays == 0) return emptyList()
    val charts = mutableListOf<StatChart>()

    fun byHours(entries: List<JellyPlayEntry>, title: String) {
        if (entries.isEmpty()) return
        charts += StatChart(
            title,
            entries.map { e ->
                StatBar(e.label, e.hours.toFloat(), "%.1f h".format(e.hours), id = e.itemId)
            },
            accentArgb,
        )
    }

    byHours(stats.topTitles, "MEISTGESEHEN")
    byHours(stats.devices, "GERÄTE")

    if (stats.forced.isNotEmpty()) {
        charts += StatChart(
            "ERZWINGT TRANSKODIERUNG",
            stats.forced.map { e ->
                // Counted in plays, not hours: how often the server was made to re-encode is the
                // interesting number, and the detail says whether it was the video or only sound.
                StatBar(
                    label = if (e.detail.isBlank()) e.label else "${e.label}  ·  ${e.detail}",
                    value = e.plays.toFloat(),
                    display = "${e.plays}×",
                    id = e.itemId,
                )
            },
            warnArgb,
        )
    }
    return charts
}
