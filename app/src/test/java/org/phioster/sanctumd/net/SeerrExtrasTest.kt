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

/** Package E: upcoming, related titles, blocklist, file deletion, quota and logs (seerr-api.yml). */
class SeerrExtrasTest {

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
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )
    private fun page(vararg ids: Int) =
        """{"results":[${ids.joinToString(",") { """{"id":$it,"title":"t$it","name":"t$it"}""" }}]}"""

    @Test fun `upcoming alternates movies and series`() = runBlocking {
        server.enqueue(MockResponse().setBody(page(1, 2, 3)))
        server.enqueue(MockResponse().setBody(page(10)))
        val list = seerrUpcoming(config())
        assertEquals("/api/v1/discover/movies/upcoming?page=1", server.takeRequest().path)
        assertEquals("/api/v1/discover/tv/upcoming?page=1", server.takeRequest().path)
        assertEquals(listOf(1, 10, 2, 3), list.map { it.tmdbId })
        assertEquals(listOf("movie", "tv", "movie", "movie"), list.map { it.mediaType })
    }

    @Test fun `related titles use the type's own route`() = runBlocking {
        server.enqueue(MockResponse().setBody(page(5)))
        server.enqueue(MockResponse().setBody(page(6)))
        seerrRelated(config(), 7, "tv", "similar")
        seerrRelated(config(), 8, "movie", "recommendations")
        assertEquals("/api/v1/tv/7/similar", server.takeRequest().path)
        assertEquals("/api/v1/movie/8/recommendations", server.takeRequest().path)
    }

    @Test fun `the blocklist is read with who blocked what`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"pageInfo":{},"results":[{"tmdbId":9,"title":"Junk","mediaType":"movie","user":{"displayName":"SekVII"}}]}""",
            ),
        )
        val b = seerrBlocklist(config()).single()
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/blocklist?"))
        assertEquals(SeerrBlockedItem(9, "Junk", "movie", "SekVII"), b)
    }

    @Test fun `block and unblock name the title and its type`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("blocked", seerrBlock(config(), 9, "movie", "Junk"))
        assertEquals("unblocked", seerrUnblock(config(), 9, "movie"))
        val post = server.takeRequest()
        assertEquals("POST /api/v1/blocklist", "${post.method} ${post.path}")
        assertEquals("""{"tmdbId":9,"mediaType":"movie","title":"Junk"}""", post.body.readUtf8())
        assertEquals("DELETE /api/v1/blocklist/9?mediaType=movie", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test fun `deleting the file goes to the media entry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("file deleted", seerrDeleteFile(config(), 120, is4k = false))
        assertEquals("DELETE /api/v1/media/120/file?is4k=false", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test fun `a quota of 0 reads as unlimited data, the rest as numbers`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"movie":{"days":7,"limit":10,"used":3,"remaining":7,"restricted":false},
                    "tv":{"days":0,"limit":0,"used":2,"remaining":0,"restricted":false}}""",
            ),
        )
        val q = seerrQuota(config(), 3)
        assertEquals("/api/v1/user/3/quota", server.takeRequest().path)
        assertEquals(SeerrQuotaPart(limit = 10, days = 7, used = 3, remaining = 7, restricted = false), q.movie)
        assertEquals(0, q.tv?.limit)
    }

    @Test fun `logs ask for the chosen level`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"level":"error","message":"Radarr unreachable","timestamp":"2026-10-17T12:00:00.000Z"}]"""))
        val l = seerrLogs(config(), "warn").single()
        assertEquals("/api/v1/settings/logs?take=100&filter=warn", server.takeRequest().path)
        assertEquals("2026-10-17 12:00:00", l.time)
        assertEquals("Radarr unreachable", l.message)
    }

    @Test fun `a user's requests are filtered by requestedBy`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        seerrRequests(config(), "all", requestedBy = 3)
        assertTrue(server.takeRequest().path!!.contains("requestedBy=3"))
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        seerrBlock(config(), 9, "movie", "x")
        seerrUnblock(config(), 9, "movie")
        seerrDeleteFile(config(), 1, false)
        assertEquals(0, server.requestCount)
    }
}
