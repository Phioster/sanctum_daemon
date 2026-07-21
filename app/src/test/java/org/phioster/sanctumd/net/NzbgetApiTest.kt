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

/** NZBGet is the one service on HTTP Basic and JSON-RPC, so both are pinned here. */
class NzbgetApiTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config() = ServiceConfig(
        id = "nzb",
        type = ServiceType.NZBGET,
        label = "NZBGet",
        baseUrl = server.url("/").toString(),
        username = "nzbadmin",
        password = "hunter2",
    )

    @Test
    fun `the queue maps NZBGet's capitalised fields`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"result":[{"NZBID":42,"NZBName":"Some.Release","Status":"DOWNLOADING","FileSizeMB":2048,"RemainingSizeMB":512}]}"""
        ))
        val queue = nzbgetQueue(config())
        assertEquals(1, queue.size)
        assertEquals(42, queue[0].id)
        assertEquals("Some.Release", queue[0].name)
        assertEquals("DOWNLOADING", queue[0].status)
    }

    @Test
    fun `requests authenticate with HTTP Basic`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":[]}"""))
        nzbgetQueue(config())
        val auth = server.takeRequest().getHeader("Authorization")
        assertTrue("expected Basic auth, got $auth", auth!!.startsWith("Basic "))
    }

    @Test
    fun `pause posts a JSON-RPC command`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        val result = runNzbgetPause(config())
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertTrue(sent.body.readUtf8().contains("pausedownload"))
        assertEquals("paused", result)
    }
}
