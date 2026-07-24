package org.phioster.sanctumd.model

/** One bar in a stats chart. [value] drives the bar length; [display] is the shown number;
 *  [colorArgb] overrides the chart accent per-bar (used when each bar is a distinct service). */
data class StatBar(val label: String, val value: Float, val display: String, val colorArgb: Long? = null)

/** A titled horizontal bar chart on the stats screen. */
data class StatChart(val title: String, val bars: List<StatBar>, val accentArgb: Long)

/** A headline tile (big number). */
data class StatTile(val label: String, val value: String, val accentArgb: Long)

/** Everything the stats screen renders, aggregated across all configured services.
 *  [trendTiles]/[trends] make up the TRENDS section, derived from the recorded stat history. */
data class StatsData(
    val tiles: List<StatTile>,
    val charts: List<StatChart>,
    val trendTiles: List<StatTile> = emptyList(),
    val trends: List<StatChart> = emptyList(),
)
