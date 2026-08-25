package org.phioster.sanctumd.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The bar geometry the ambient glow paints into — and, just as important, when it paints nothing. */
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

    @Test fun `unknown aspect or size paints nothing`() {
        assertNull(ambientVideoRect(2000, 900, aspect = 0f, zoom = 1f))
        assertNull(ambientVideoRect(0, 0, aspect = 1.78f, zoom = 1f))
    }
}
