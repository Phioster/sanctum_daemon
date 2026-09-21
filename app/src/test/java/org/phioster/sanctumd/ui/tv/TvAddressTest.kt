package org.phioster.sanctumd.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which address the TV tries first, and whether it admits to an unencrypted one.
 *
 * This is a security rule, not a convenience: the sign-in that follows carries a password or a
 * Quick Connect secret. A server that speaks both must not be reached over plain http merely
 * because that spelling was earlier in the list.
 */
class TvAddressTest {

    @Test
    fun `https is tried before http`() {
        val c = candidateUrls("192.168.1.20")
        assertEquals(
            listOf(
                "https://192.168.1.20",
                "https://192.168.1.20:8920",
                "http://192.168.1.20",
                "http://192.168.1.20:8096",
            ),
            c,
        )
        assertTrue("https must come first", c.first().startsWith("https://"))
    }

    @Test
    fun `a typed port is taken as given, both schemes, https first`() {
        assertEquals(
            listOf("https://jellyfin.local:8096", "http://jellyfin.local:8096"),
            candidateUrls("jellyfin.local:8096"),
        )
    }

    @Test
    fun `a full url is used as typed, and nothing is guessed around it`() {
        assertEquals(listOf("http://box.lan:8096"), candidateUrls("http://box.lan:8096"))
        assertEquals(listOf("https://jf.example.net"), candidateUrls("https://jf.example.net/"))
    }

    @Test
    fun `nothing typed, nothing tried`() {
        assertEquals(emptyList<String>(), candidateUrls("   "))
    }

    @Test
    fun `an unencrypted address is recognised as one`() {
        assertTrue(isPlainHttp("http://192.168.1.20:8096"))
        assertTrue(isPlainHttp("HTTP://box.lan"))
        assertFalse(isPlainHttp("https://jf.example.net"))
        assertFalse(isPlainHttp(""))
    }
}
