package org.phioster.sanctumd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.DownloadEntry
import java.io.IOException
import java.net.SocketTimeoutException

/** A dropped connection must not end a download; a real refusal must. */
class DownloadRetryTest {

    @Test
    fun `network errors and server hiccups are retried`() {
        assertTrue(isRetryable(SocketTimeoutException("read timed out")))
        assertTrue(isRetryable(IOException("unexpected end of stream")))
        assertTrue(isRetryable(HttpStatusException(502)))
        assertTrue(isRetryable(HttpStatusException(429)))
    }

    @Test
    fun `refusals and programming errors are final`() {
        assertFalse(isRetryable(HttpStatusException(404)))
        assertFalse(isRetryable(HttpStatusException(401)))
        assertFalse(isRetryable(IllegalArgumentException("escaped")))
    }

    @Test
    fun `backoff grows and then levels off`() {
        assertEquals(5_000L, retryDelayMs(1))
        assertTrue(retryDelayMs(2) > retryDelayMs(1))
        assertEquals(retryDelayMs(4), retryDelayMs(20))
    }

    @Test
    fun `only entries stuck in running are recovered`() {
        fun e(id: String, s: String) = DownloadEntry(itemId = id, serverId = "s", name = id, state = s)
        val all = listOf(
            e("a", DownloadEntry.STATE_RUNNING), e("b", DownloadEntry.STATE_PAUSED),
            e("c", DownloadEntry.STATE_DONE), e("d", DownloadEntry.STATE_QUEUED),
        )
        assertEquals(listOf("a"), orphanedRunning(all))
    }
}
