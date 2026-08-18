package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * With a single flaky indexer the same broken release gets grabbed again and again. Blocklisting
 * on removal is what stops that loop — and it was unreachable, because `blocklist` was pinned to
 * false in the URL, the same shape of defect as the hardcoded `addImportExclusion`.
 */
class ArrBlocklistTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "test", type = ServiceType.RADARR, label = "Radarr",
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    @Test
    fun `removing without blocklisting stays the default`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        arrQueueRemove(config(), id = 3)

        val path = server.takeRequest().path!!
        assertTrue("got $path", path.contains("blocklist=false"))
        assertTrue("got $path", path.contains("removeFromClient=true"))
    }

    @Test
    fun `a release can be removed and blocklisted in one go`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        arrQueueRemove(config(), id = 3, blocklist = true)

        assertTrue(server.takeRequest().path!!.contains("blocklist=true"))
    }

    @Test
    fun `the blocklist can be read`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"records":[{"id":11,"sourceTitle":"Bad.Release.German.1080p","date":"2026-08-17T02:03:39Z"}]}""")
                .setHeader("Content-Type", "application/json"),
        )

        val entries = arrBlocklist(config())

        assertEquals(1, entries.size)
        assertEquals(11, entries[0].id)
        assertEquals("Bad.Release.German.1080p", entries[0].title)
    }

    /** Unblocking has to be possible, or a mistaken blocklist entry is permanent. */
    @Test
    fun `a blocklist entry can be removed again`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = arrBlocklistRemove(config(), id = 11)

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/api/v3/blocklist/11"))
        assertEquals("removed", result)
    }
}
