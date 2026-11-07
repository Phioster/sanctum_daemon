package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    @Test
    fun `the prowlarr test-all message loses its key but keeps the host`() {
        val raw = "Unable to connect to indexer, check the log above the ValidationFailure for more details. " +
            "HTTP request failed: [500:InternalServerError] [GET] at [https://treasure-maps.com/api?t=caps&apikey=0123abcdEF4567]"
        val out = redactSecrets(raw)
        assertFalse(out, out.contains("0123abcdEF4567"))
        assertEquals(true, out.contains("https://treasure-maps.com/api?t=caps&apikey=•••]"))
    }

    @Test
    fun `json fields, headers and url credentials are blanked`() {
        assertEquals("""{"apiKey":"•••","x":1}""", redactSecrets("""{"apiKey":"s3cr3t","x":1}"""))
        assertEquals("X-Api-Key: •••", redactSecrets("X-Api-Key: abc123"))
        assertEquals("Authorization: Bearer •••", redactSecrets("Authorization: Bearer tk_abc.def"))
        assertEquals("MediaBrowser Token=\"•••\"", redactSecrets("MediaBrowser Token=\"abc\""))
        assertEquals("http://•••@10.0.0.7:6789/jsonrpc", redactSecrets("http://nzbget:hunter2@10.0.0.7:6789/jsonrpc"))
        assertEquals("a?passkey=•••&b=2", redactSecrets("a?passkey=XYZ&b=2"))
    }

    @Test
    fun `ordinary error texts pass through untouched`() {
        listOf("HTTP 500", "timeout", "Movie with ID 0 does not exist", "bypass the cache", "no tokens left")
            .forEach { assertEquals(it, redactSecrets(it)) }
    }

    @Test
    fun `errorText falls back to the class name`() {
        assertEquals("IllegalStateException", errorText(IllegalStateException()))
        assertEquals("bad apikey=•••", errorText(RuntimeException("bad apikey=k1")))
    }
}
