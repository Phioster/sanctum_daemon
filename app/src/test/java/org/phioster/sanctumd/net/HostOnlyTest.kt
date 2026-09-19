package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The safe-mode log line must not carry an indexer's API key into logcat. */
class HostOnlyTest {

    @Test
    fun `only the host survives`() {
        assertEquals("indexer.example.net", hostOnly("https://indexer.example.net/api?t=get&apikey=SECRET"))
        assertEquals("10.0.0.7:6789", hostOnly("http://10.0.0.7:6789/nzb/123"))
    }

    @Test
    fun `no query string ever comes through`() {
        for (u in listOf(
            "https://a.example/api?apikey=SECRET",
            "https://a.example?apikey=SECRET",
            "a.example/x?apikey=SECRET",
        )) {
            val h = hostOnly(u)
            assertFalse(h, h.contains("SECRET"))
            assertFalse(h, h.contains("?"))
        }
    }

    @Test
    fun `nonsense still yields something printable`() {
        assertEquals("?", hostOnly(""))
        assertEquals("?", hostOnly("https://"))
    }
}
