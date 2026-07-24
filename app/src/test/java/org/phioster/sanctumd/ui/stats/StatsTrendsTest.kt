package org.phioster.sanctumd.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.data.StatsSnapshot
import org.phioster.sanctumd.data.mergeSnapshots
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** The pure trend math behind the stats screen's TRENDS section + the history store's merge rule. */
class StatsTrendsTest {

    private fun snap(day: Long, vararg m: Pair<String, Long>) = StatsSnapshot(day, m.toMap())

    @Test
    fun `mergeSnapshots replaces same day and caps window`() {
        val existing = (0L until 5L).map { snap(it, "k" to it) }
        val merged = mergeSnapshots(existing, snap(2, "k" to 99), maxDays = 3)
        // capped to 3 most-recent days (2,3,4), and day 2 carries the replacement value
        assertEquals(listOf(2L, 3L, 4L), merged.map { it.epochDay })
        assertEquals(99L, merged.first { it.epochDay == 2L }.metrics["k"])
    }

    @Test
    fun `deltaBars are consecutive positive increases`() {
        val bars = deltaBars(listOf(0L to 100L, 1L to 100L, 2L to 130L, 3L to 125L))
        assertEquals(listOf("+0", "+30", "+0"), bars.map { it.display }) // 100->100, 100->130, 130->125(clamped)
    }

    @Test
    fun `deltaBars need at least two points`() {
        assertTrue(deltaBars(listOf(5L to 10L)).isEmpty())
    }

    @Test
    fun `forecastDaysToFull projects a shrinking series to zero`() {
        // 100 GB free, losing 10 GB/day -> ~10 days at the last point (70 GB / 10 GB/day = 7)
        val gb = 1_000_000_000L
        val pts = listOf(0L to 100 * gb, 1L to 90 * gb, 2L to 80 * gb, 3L to 70 * gb)
        val days = forecastDaysToFull(pts)
        assertEquals(7, days)
    }

    @Test
    fun `forecastDaysToFull is null when not shrinking or too few points`() {
        val gb = 1_000_000_000L
        assertNull(forecastDaysToFull(listOf(0L to 10 * gb, 1L to 20 * gb, 2L to 30 * gb))) // growing
        assertNull(forecastDaysToFull(listOf(0L to 30 * gb, 1L to 20 * gb))) // <3 points
    }

    @Test
    fun `buildTrends emits jellyfin growth chart and added tile`() {
        val jf = ServiceConfig(id = "jf1", type = ServiceType.JELLYFIN, label = "Jelly", baseUrl = "http://x")
        val history = listOf(
            snap(0, "jf1|episodes" to 1000),
            snap(1, "jf1|episodes" to 1010),
            snap(2, "jf1|episodes" to 1025),
        )
        val (tiles, charts) = buildTrends(listOf(jf), history)
        assertTrue(charts.any { it.title.contains("episodes added") })
        assertEquals("+25", tiles.first { it.label.contains("added") }.value)
    }
}
