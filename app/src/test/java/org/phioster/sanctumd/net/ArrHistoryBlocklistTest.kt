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
 * Blocklisting used to require the release to be sitting in the queue, so it only worked while a
 * download was running. When an indexer serves several wrongly-tagged releases for the same title,
 * that means waiting for each to be grabbed before it can be blocked — one download at a time.
 *
 * Marking a history entry as failed blocks it after the fact. Verified on the live instance
 * first: the route answers with a domain error for an unknown id, where an invented route
 * answers 405.
 */
class ArrHistoryBlocklistTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config(type: ServiceType = ServiceType.RADARR) = ServiceConfig(
        id = "test", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    @Test
    fun `history entries carry the id needed to block them`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"records":[{"id":4711,"eventType":"grabbed","date":"2026-08-18T16:08:16Z",
                        "sourceTitle":"Dolls.1987.GERMAN.DL.1080p.BluRay.x264-OldsMan",
                        "quality":{"quality":{"name":"Bluray-1080p"}}}]}""",
                )
                .setHeader("Content-Type", "application/json"),
        )

        val history = arrHistory(config())

        assertEquals(4711, history[0].id)
        assertEquals("Dolls.1987.GERMAN.DL.1080p.BluRay.x264-OldsMan", history[0].title)
    }

    @Test
    fun `blocking a past release marks its history entry as failed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = arrBlocklistFromHistory(config(), id = 4711)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v3/history/failed/4711", req.path)
        assertEquals("blocklisted", result)
    }

    @Test
    fun `lidarr uses its v1 api`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        arrBlocklistFromHistory(config(ServiceType.LIDARR), id = 5)

        assertEquals("/api/v1/history/failed/5", server.takeRequest().path)
    }

    @Test
    fun `a failure is reported rather than swallowed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(arrBlocklistFromHistory(config(), id = 1).startsWith("error"))
    }
}
