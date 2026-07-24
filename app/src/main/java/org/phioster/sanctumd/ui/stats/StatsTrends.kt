package org.phioster.sanctumd.ui.stats

import org.phioster.sanctumd.data.StatsSnapshot
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.model.StatBar
import org.phioster.sanctumd.model.StatChart
import org.phioster.sanctumd.model.StatTile
import java.time.LocalDate

/** Time-series derivations for the stats screen's TRENDS section. All pure functions over the
 *  recorded [StatsSnapshot] history — no network, no Android — so they're straightforward to unit-test. */

/** The (day, value) points of one metric key across the recorded history, oldest first. */
internal fun seriesFor(history: List<StatsSnapshot>, key: String): List<Pair<Long, Long>> =
    history.sortedBy { it.epochDay }.mapNotNull { snap -> snap.metrics[key]?.let { snap.epochDay to it } }

private fun dayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString().takeLast(5) // MM-dd

private fun freeGb(bytes: Long) = "%.0f GB".format(bytes / 1_000_000_000.0)

/** Consecutive positive increases ("added per period"), labeled by the later day. */
internal fun deltaBars(points: List<Pair<Long, Long>>, maxBars: Int = 8): List<StatBar> {
    if (points.size < 2) return emptyList()
    val sorted = points.sortedBy { it.first }
    return sorted.zipWithNext { a, b -> b.first to (b.second - a.second) }
        .takeLast(maxBars)
        .map { (day, d) -> val v = d.coerceAtLeast(0); StatBar(dayLabel(day), v.toFloat(), "+$v") }
}

/** Linear-fit estimate of days until a shrinking free-space series reaches zero; null if there
 *  aren't enough points or the trend isn't downward. */
internal fun forecastDaysToFull(points: List<Pair<Long, Long>>): Int? {
    if (points.size < 3) return null
    val xs = points.map { it.first.toDouble() }
    val ys = points.map { it.second.toDouble() }
    val mx = xs.average()
    val my = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in xs.indices) {
        num += (xs[i] - mx) * (ys[i] - my)
        den += (xs[i] - mx) * (xs[i] - mx)
    }
    if (den == 0.0) return null
    val slope = num / den // bytes/day; negative = shrinking
    if (slope >= 0) return null
    return (ys.last() / -slope).toInt().coerceAtLeast(0)
}

/** Builds the TRENDS tiles + charts from the recorded history for the given services. */
internal fun buildTrends(services: List<ServiceConfig>, history: List<StatsSnapshot>): Pair<List<StatTile>, List<StatChart>> {
    val tiles = mutableListOf<StatTile>()
    val charts = mutableListOf<StatChart>()
    services.forEach { svc ->
        val id = svc.id
        val accent = svc.type.accent
        when (svc.type) {
            ServiceType.JELLYFIN -> {
                val pts = seriesFor(history, "$id|episodes")
                val bars = deltaBars(pts)
                if (bars.isNotEmpty()) {
                    charts += StatChart("${svc.label} · episodes added", bars, accent)
                    val added = pts.last().second - pts.first().second
                    if (added > 0) tiles += StatTile("${svc.label} · added", "+$added", accent)
                }
            }
            ServiceType.PROWLARR -> {
                val bars = deltaBars(seriesFor(history, "$id|grabs"))
                if (bars.isNotEmpty()) charts += StatChart("${svc.label} · grabs per day", bars, accent)
            }
            ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> {
                val diskKeys = history.flatMap { it.metrics.keys }.filter { it.startsWith("$id|diskfree:") }.distinct()
                diskKeys.forEach { key ->
                    val pts = seriesFor(history, key)
                    val days = forecastDaysToFull(pts) ?: return@forEach
                    val path = key.substringAfter("diskfree:")
                    tiles += StatTile("${svc.label} · ${path.takeLast(12)} full in", "~${days}d", accent)
                    charts += StatChart(
                        "${svc.label} · free ${path.takeLast(14)}",
                        pts.takeLast(10).map { (d, v) -> StatBar(dayLabel(d), (v / 1_000_000_000.0).toFloat(), freeGb(v)) },
                        accent,
                    )
                }
            }
            else -> {}
        }
    }
    return tiles to charts
}
