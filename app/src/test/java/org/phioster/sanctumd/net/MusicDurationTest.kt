package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Jellyfin counts in 100-nanosecond ticks and the album screen prints minutes, so the conversion
 * is worth nailing down: a factor of a thousand either way turns a three-minute song into three
 * seconds or fifty minutes and nobody would notice in a screenshot.
 */
class MusicDurationTest {

    @Test
    fun `ticks become milliseconds`() {
        // The real value of "Extase des Teufels" as the server reports it.
        assertEquals(172_639L, ticksToMs(1_726_399_999L))
        assertEquals(0L, ticksToMs(null))
        assertEquals(0L, ticksToMs(0L))
    }

    @Test
    fun `a track reads as minutes and seconds`() {
        assertEquals("2:52", formatDuration(172_639L))
        assertEquals("0:07", formatDuration(7_000L))
        assertEquals("4:00", formatDuration(240_000L))
    }

    @Test
    fun `an album past an hour grows an hours field`() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:14:32", formatDuration(4_472_000L))
    }

    /** A server that did not report a runtime has not said "zero", so we do not print one. */
    @Test
    fun `a missing runtime is an em dash, not zero`() {
        assertEquals("—", formatDuration(0L))
        assertEquals("—", formatDuration(-5L))
    }
}
