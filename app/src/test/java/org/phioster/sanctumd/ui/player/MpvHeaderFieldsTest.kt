package org.phioster.sanctumd.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The header lines that broke playback.
 *
 * mpv's `http-header-fields` is a comma-separated list, and Jellyfin's `Authorization` header is
 * made of commas. Set in one go, mpv split one header into four broken lines, the server answered
 * 400 Bad Request, and the player sat on "buffering…" forever. The engine appends them one at a
 * time now; this keeps the shape of a single line honest.
 */
class MpvHeaderFieldsTest {

    private val jellyfin = "MediaBrowser Token=\"abc123\", Client=\"Sanctumd\", Device=\"Android\""

    @Test
    fun `a header full of commas stays one line`() {
        assertEquals(
            listOf("Authorization: $jellyfin"),
            headerLines(mapOf("Authorization" to jellyfin)),
        )
    }

    @Test
    fun `every header gets its own line, in order`() {
        assertEquals(
            listOf("Authorization: $jellyfin", "CF-Access-Client-Id: xyz"),
            headerLines(linkedMapOf("Authorization" to jellyfin, "CF-Access-Client-Id" to "xyz")),
        )
    }

    @Test
    fun `no headers, no lines`() {
        assertEquals(emptyList<String>(), headerLines(emptyMap()))
    }
}
