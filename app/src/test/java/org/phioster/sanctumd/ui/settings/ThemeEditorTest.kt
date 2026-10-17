package org.phioster.sanctumd.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.sanctumd.ui.theme.CustomTheme
import org.phioster.sanctumd.ui.theme.CustomThemes
import org.phioster.sanctumd.ui.theme.PALETTES
import org.phioster.sanctumd.ui.theme.Readability
import org.phioster.sanctumd.ui.theme.ThemeStore
import org.phioster.sanctumd.ui.theme.liftedSurface
import org.phioster.sanctumd.ui.theme.paletteById
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeEditorTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `every built-in preset passes the editor's own readability bar`() {
        PALETTES.forEach { p -> assertTrue("${p.id}: ${Readability.worst(p)}", Readability.worst(p) >= Readability.MIN) }
    }

    @Test fun `AE86 has white ink on red cards`() {
        val p = paletteById("ae86")
        assertTrue(p.accent.red > 0.9f && p.accent.green > 0.9f)
        assertTrue("red surface", p.surface.red > p.surface.green * 3 && p.surface.red > p.surface.blue * 3)
        assertEquals(2, Readability.level(Readability.worst(p)))
    }

    @Test fun `contrast follows the WCAG formula`() {
        assertEquals(21.0, Readability.contrast(Color.White, Color.Black), 0.01)
        assertEquals(1.0, Readability.contrast(Color.Gray, Color.Gray), 0.01)
    }

    @Test fun `make readable fixes a dark accent on a dark background`() {
        val (a, b, s) = makeReadable(Color(0xFF203020), Color(0xFF101010), Color(0xFF1A1A1A))
        val worst = Readability.worst(org.phioster.sanctumd.ui.theme.Palette("x", "x", a, b, s, liftedSurface(s)))
        assertTrue("worst $worst", worst >= Readability.GOOD)
        // The hue survives; only brightness moved.
        assertTrue(a.green > a.red && a.green > a.blue)
    }

    @Test fun `make readable rescues a light background too`() {
        val (a, b, s) = makeReadable(Color(0xFFFFFF00), Color(0xFFEEEEEE), Color(0xFFDDDDDD))
        val worst = Readability.worst(org.phioster.sanctumd.ui.theme.Palette("x", "x", a, b, s, liftedSurface(s)))
        assertTrue("worst $worst", worst >= Readability.MIN)
    }

    @Test fun `hex in and out`() {
        assertEquals(Color(0xFFD7141A).toArgb(), parseHex("#d7141a")?.toArgb())
        assertEquals("D7141A", Color(0xFFD7141A).hex())
        assertNull(parseHex("12345"))
        assertNull(parseHex("zzzzzz"))
    }

    @Test fun `an own theme is saved, chosen, read back and deleted`() {
        val t = CustomTheme.from(CustomThemes.newId(), "Initial D", Color(0xFFFFFFFF), Color(0xFF120304), Color(0xFF3A0A0E))
        ThemeStore.saveCustom(ctx, t)
        assertEquals(t.id, ThemeStore.paletteId(ctx))
        val read = ThemeStore.read(ctx)
        assertEquals(Color(0xFF3A0A0E).toArgb(), CustomThemes.byId(ctx, t.id)!!.palette().surface.toArgb())
        assertEquals("Initial D", read.label)
        ThemeStore.deleteCustom(ctx, t.id)
        assertTrue(CustomThemes.all(ctx).none { it.id == t.id })
        assertEquals("matrix", ThemeStore.paletteId(ctx))
    }
}
