package org.phioster.sanctumd.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the conversion that broke 1.50.1 in a way nothing could catch at compile time.
 */
class ColorArgbTest {

    @Test fun `a colour survives the round trip`() {
        assertEquals(0xFF00FF66L, Color(0xFF00FF66).argbLong())
        assertEquals(0xFFFFAA00L, Color(0xFFFFAA00).argbLong())
    }

    @Test fun `full alpha does not sign-extend into nonsense`() {
        val v = Color(0xFFFFFFFF).argbLong()
        assertEquals(0xFFFFFFFFL, v)
        assertEquals(true, v > 0)
    }

    /**
     * The trap itself, written down: `Color.value` is Compose's internal 64-bit form and encodes
     * the colour space too. Reading it as ARGB is what produced black text on a black tile.
     */
    @Test fun `the internal value is not an argb value`() {
        val green = Color(0xFF00FF66)
        assertNotEquals(green.argbLong(), green.value.toLong())
    }

    @Test fun `what argbLong produces can be turned back into the same colour`() {
        val original = Color(0xFF35C5F4)
        assertEquals(original, Color(original.argbLong()))
    }
}
