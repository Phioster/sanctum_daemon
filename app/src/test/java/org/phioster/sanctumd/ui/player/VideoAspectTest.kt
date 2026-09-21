package org.phioster.sanctumd.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shape the surface takes. The one number that decides whether a film is stretched. */
class VideoAspectTest {

    @Test
    fun `the player's own figure wins`() {
        assertEquals(2.39f, displayAspect(2.39f, 1920, 1080), 0.001f)
    }

    @Test
    fun `without a figure the corrected dimensions decide`() {
        assertEquals(16f / 9f, displayAspect(0f, 1920, 1080), 0.001f)
    }

    @Test
    fun `an anamorphic file is 16 by 9, not the 5 by 4 it stores`() {
        // 720x576 on disk, flagged for widescreen playback: going by the stored size would squeeze
        // every PAL DVD rip. The reported figure has to beat the dimensions.
        assertEquals(16f / 9f, displayAspect(16f / 9f, 720, 576), 0.001f)
    }

    @Test
    fun `nothing known yet means fill the screen`() {
        assertEquals(0f, displayAspect(0f, 0, 0), 0.001f)
    }

    @Test
    fun `a broken header is refused rather than collapsing the picture`() {
        assertEquals(0f, displayAspect(Float.NaN, 0, 0), 0.001f)
        assertEquals(0f, displayAspect(99f, 0, 0), 0.001f)
        assertEquals(0f, displayAspect(0f, 4000, 1), 0.001f)
    }

    @Test
    fun `a broken figure still falls back to usable dimensions`() {
        assertEquals(16f / 9f, displayAspect(99f, 1920, 1080), 0.001f)
    }
}
