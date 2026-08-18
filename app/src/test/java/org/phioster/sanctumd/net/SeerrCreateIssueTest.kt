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
 * The app could list issues, comment on them and change their status — but not open one, which
 * is the half that actually starts the conversation. Reporting "the German audio track is
 * missing" is the point; the rest is follow-up.
 */
class SeerrCreateIssueTest {

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

    @Test
    fun `an issue carries its type, message and the media it is about`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = seerrCreateIssue(config(), mediaId = 42, issueType = 2, message = "no German audio")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/api/v1/issue"))
        val body = req.body.readUtf8()
        assertTrue("got $body", body.contains("\"mediaId\":42"))
        assertTrue("got $body", body.contains("\"issueType\":2"))
        assertTrue("got $body", body.contains("\"message\":\"no German audio\""))
        assertEquals("reported", result)
    }

    /** The four types Seerr knows, so the picker cannot offer one the server rejects. */
    @Test fun `the issue types match what Seerr defines`() {
        assertEquals("video", seerrIssueType(1))
        assertEquals("audio", seerrIssueType(2))
        assertEquals("subtitle", seerrIssueType(3))
        assertEquals("other", seerrIssueType(4))
    }

    /**
     * The issue endpoint wants Seerr's **internal** media id, not the TMDB id. They are
     * different numbers, and posting the TMDB one would open issues against unrelated titles
     * or fail outright — so the detail has to carry it.
     */
    @Test
    fun `the media detail carries Seerr's own media id`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":603,"title":"The Matrix","releaseDate":"1999-03-30",
                             "mediaInfo":{"id":88,"status":5}}""")
                .setHeader("Content-Type", "application/json"),
        )

        val detail = seerrMediaDetail(config(), tmdbId = 603, mediaType = "movie")

        assertEquals(88, detail.mediaId)
        assertEquals(603, detail.tmdbId)
    }

    @Test
    fun `a title that is not in the library has no media id`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"id":603,"title":"The Matrix","releaseDate":"1999-03-30"}""")
                .setHeader("Content-Type", "application/json"),
        )

        assertEquals(0, seerrMediaDetail(config(), tmdbId = 603, mediaType = "movie").mediaId)
    }

    @Test
    fun `a failure is reported rather than swallowed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = seerrCreateIssue(config(), mediaId = 42, issueType = 1, message = "broken")

        assertTrue("got $result", result.startsWith("error"))
    }
}
