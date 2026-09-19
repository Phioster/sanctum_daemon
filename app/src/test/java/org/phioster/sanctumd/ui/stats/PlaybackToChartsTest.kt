package org.phioster.sanctumd.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.JellyPlayEntry
import org.phioster.sanctumd.model.JellyPlaybackStats

private const val GREEN = 0xFF00FF66L
private const val AMBER = 0xFFFFAA00L

class PlaybackToChartsTest {

    private val stats = JellyPlaybackStats(
        plays = 109, hours = 41.5, since = "2026-07-12", transcodes = 12, remuxes = 3,
        topTitles = listOf(
            JellyPlayEntry("Mortal Kombat II", itemId = "aaa", plays = 3, hours = 3.5),
            JellyPlayEntry("Akira", itemId = "bbb", plays = 5, hours = 2.6),
        ),
        devices = listOf(
            JellyPlayEntry("Android TV", plays = 58, hours = 22.0),
            JellyPlayEntry("Nothing Phone (3)", plays = 15, hours = 4.0),
        ),
        forced = listOf(
            JellyPlayEntry("Mandalorian", itemId = "ccc", plays = 4, detail = "v:h264 a:direct"),
        ),
    )

    @Test fun `the headline numbers become tiles`() {
        val t = playbackTiles(stats, GREEN, AMBER)
        assertEquals(listOf("42", "109", "12", "3"), t.map { it.value })
        assertEquals(listOf("Hours", "Plays", "Transcoded", "Remuxed"), t.map { it.label })
    }

    /** A remux costs the server nothing, so it never wears the warning colour — and never a zero. */
    @Test fun `the remux tile stays calm and disappears when there is nothing to say`() {
        assertEquals(GREEN, playbackTiles(stats, GREEN, AMBER)[3].accentArgb)
        val none = stats.copy(remuxes = 0)
        assertEquals(3, playbackTiles(none, GREEN, AMBER).size)
    }

    /** Only the transcode tile turns amber, and only when there is something to warn about. */
    @Test fun `the transcode tile is only coloured when it is not zero`() {
        assertEquals(AMBER, playbackTiles(stats, GREEN, AMBER)[2].accentArgb)
        val clean = stats.copy(transcodes = 0)
        assertEquals(GREEN, playbackTiles(clean, GREEN, AMBER)[2].accentArgb)
    }

    @Test fun `titles and devices become charts in the house style`() {
        val c = playbackCharts(stats, GREEN, AMBER)
        assertEquals(listOf("MOST WATCHED", "DEVICES", "FORCES TRANSCODING"), c.map { it.title })
        assertEquals(listOf("Mortal Kombat II", "Akira"), c[0].bars.map { it.label })
        assertEquals("3.5 h", c[0].bars[0].display)
        assertEquals("22.0 h", c[1].bars[0].display)
    }

    /** The id rides along so a bar can open its item; a device has none and must stay inert. */
    @Test fun `media bars carry their item id and devices do not`() {
        val c = playbackCharts(stats, GREEN, AMBER)
        assertEquals("aaa", c[0].bars[0].id)
        assertEquals("", c[1].bars[0].id)
    }

    /** The transcode chart counts plays, not hours — and says what had to be re-encoded. */
    @Test fun `the transcode chart counts plays and keeps the detail`() {
        val c = playbackCharts(stats, GREEN, AMBER)[2]
        assertEquals(AMBER, c.accentArgb)
        assertEquals("4×", c.bars[0].display)
        assertTrue(c.bars[0].label.contains("v:h264 a:direct"))
    }

    @Test fun `an empty history produces nothing to draw`() {
        val empty = JellyPlaybackStats(0, 0.0, "", 0, 0, emptyList(), emptyList(), emptyList())
        assertTrue(playbackTiles(empty, GREEN, AMBER).isEmpty())
        assertTrue(playbackCharts(empty, GREEN, AMBER).isEmpty())
    }
}
