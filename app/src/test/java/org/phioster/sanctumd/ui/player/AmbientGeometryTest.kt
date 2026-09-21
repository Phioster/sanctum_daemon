package org.phioster.sanctumd.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The bar geometry the ambient glow paints into, and, just as important, when it paints nothing. */
class AmbientGeometryTest {

    @Test fun `16 by 9 video on a tall phone gets side bars`() {
        val r = ambientVideoRect(boxW = 2000, boxH = 900, aspect = 16f / 9f, zoom = 1f)
        assertNotNull(r)
        assertEquals(900f, r!!.bottom - r.top, 0.5f)
        assertEquals(1600f, r.right - r.left, 0.5f)
        assertEquals(200f, r.left, 0.5f)
    }

    @Test fun `scope video gets top and bottom bars`() {
        val r = ambientVideoRect(boxW = 2000, boxH = 900, aspect = 2.39f, zoom = 1f)
        assertNotNull(r)
        assertEquals(2000f, r!!.right - r.left, 0.5f)
        assertEquals(836.8f, r.bottom - r.top, 0.5f)
    }

    @Test fun `zooming to fill leaves no bars`() {
        assertNull(ambientVideoRect(2000, 900, aspect = 16f / 9f, zoom = 1.25f))
        assertNull(ambientVideoRect(2000, 900, aspect = 16f / 9f, zoom = 4f))
    }

    @Test fun `an exactly matching video has no bars`() {
        assertNull(ambientVideoRect(2000, 900, aspect = 2000f / 900f, zoom = 1f))
    }

    @Test fun `a bright frame is painted fainter than a dark one`() {
        val dark = ambientAlpha(0.85f, luma = 0.05f)
        val bright = ambientAlpha(0.85f, luma = 0.95f)
        assertEquals(0.827f, dark, 0.01f)
        assertEquals(0.406f, bright, 0.01f)
        // A white poster must not out-shine a dark scene, whatever base it started from.
        assertEquals(true, ambientAlpha(0.5f, luma = 1f) < dark)
    }

    @Test fun `alpha stays within range for absurd input`() {
        assertEquals(0f, ambientAlpha(0f, luma = 0.5f), 0.001f)
        assertEquals(1f, ambientAlpha(2f, luma = 0f), 0.001f)
        assertEquals(0.85f, ambientAlpha(0.85f, luma = -3f), 0.001f)
    }

    @Test fun `unknown aspect or size paints nothing`() {
        assertNull(ambientVideoRect(2000, 900, aspect = 0f, zoom = 1f))
        assertNull(ambientVideoRect(0, 0, aspect = 1.78f, zoom = 1f))
    }

    @Test fun `a frame shrinks to the target on its longer side, keeping its shape`() {
        assertEquals(10 to 6, glowSampleSize(40, 23, longEdgePx = 10))
        assertEquals(6 to 12, glowSampleSize(24, 48, longEdgePx = 12))
    }

    @Test fun `a frame already that small is handed back untouched`() {
        assertEquals(8 to 5, glowSampleSize(8, 5, longEdgePx = 10))
        assertEquals(10 to 4, glowSampleSize(10, 4, longEdgePx = 10))
    }

    @Test fun `the short side never rounds away to nothing`() {
        assertEquals(10 to 1, glowSampleSize(400, 3, longEdgePx = 10))
    }

    @Test fun `absurd input is handed back unchanged`() {
        assertEquals(0 to 0, glowSampleSize(0, 0, longEdgePx = 10))
        assertEquals(40 to 23, glowSampleSize(40, 23, longEdgePx = 0))
    }
}
