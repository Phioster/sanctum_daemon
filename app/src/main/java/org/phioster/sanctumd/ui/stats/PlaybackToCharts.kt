package org.phioster.sanctumd.ui.stats

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
internal fun playbackTiles(stats: JellyPlaybackStats, accentArgb: Long, warnArgb: Long): List<StatTile> = emptyList()

internal fun playbackCharts(stats: JellyPlaybackStats, accentArgb: Long, warnArgb: Long): List<StatChart> = emptyList()
