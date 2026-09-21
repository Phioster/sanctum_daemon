package org.phioster.sanctumd.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The header line that broke playback.
 *
 * mpv's `http-header-fields` is a comma-separated list, and Jellyfin's `Authorization` header is
 * made of commas. Joined plainly, mpv split one header into four broken ones, the server answered
 * 400, and the player sat on "buffering…" forever — with nothing on screen to say why.
 */
class MpvHeaderFieldsTest {

    private val jellyfin = "MediaBrowser Token=\"abc123\", Client=\"Sanctumd\", Device=\"Android\""

    @Test
    fun `a header full of commas survives as one header`() {
        val out = mpvHeaderFields(mapOf("Authorization" to jellyfin))
        val line = "Authorization: $jellyfin"
        assertEquals("%${line.toByteArray().size}%$line", out)
        // The length prefix is what keeps mpv from splitting on those commas.
        assertTrue("the value must stay whole", out.endsWith(jellyfin))
    }

    @Test
    fun `several headers stay separable, each one escaped`() {
        val out = mpvHeaderFields(linkedMapOf("Authorization" to jellyfin, "CF-Access-Client-Id" to "xyz"))
        val first = "Authorization: $jellyfin"
        val second = "CF-Access-Client-Id: xyz"
        assertEquals("%${first.toByteArray().size}%$first,%${second.toByteArray().size}%$second", out)
    }

    @Test
    fun `the length is counted in bytes, not characters`() {
        // "ä" is one character but two bytes in UTF-8; mpv counts bytes.
        val out = mpvHeaderFields(mapOf("X-Test" to "ä"))
        assertEquals("%10%X-Test: ä", out)
    }

    @Test
    fun `no headers, nothing to escape`() {
        assertEquals("", mpvHeaderFields(emptyMap()))
    }
}
