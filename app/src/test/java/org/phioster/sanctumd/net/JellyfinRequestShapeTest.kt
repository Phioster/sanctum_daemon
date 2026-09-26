package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Pins request details that a Jellyfin server accepts silently but answers uselessly.
 *
 * All were live defects: a comma list of segment types matches no enum value, so every segment
 * query came back empty and the skip-intro button never appeared; and X-Emby-Token is a legacy
 * auth method that server 12 disables by default.
 */
class JellyfinRequestShapeTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-shape")
    }
    @After fun stop() { server.shutdown() }

    private fun config() = ServiceConfig(
        id = "jf-shape", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false,
    )

    /** A hit on the first call, so the plugin fallback never runs and one request is queued. */
    private fun respondWithSegment() = server.enqueue(
        MockResponse()
            .setBody("""{"Items":[{"Type":"Intro","StartTicks":10000000,"EndTicks":20000000}]}""")
            .setHeader("Content-Type", "application/json"),
    )

    @Test
    fun `segment types are repeated parameters, never a comma list`() = runBlocking {
        respondWithSegment()
        val segments = jellyfinMediaSegments(config(), "item42")
        val path = server.takeRequest().path!!
        assertTrue("got $path", path.contains("includeSegmentTypes=Intro"))
        assertTrue("got $path", path.contains("includeSegmentTypes=Outro"))
        assertFalse("got $path", path.contains("Intro,Outro") || path.contains("Intro%2COutro"))
        assertEquals(1, segments.size)
    }

    @Test
    fun `the token travels in the MediaBrowser authorization header`() = runBlocking {
        respondWithSegment()
        jellyfinMediaSegments(config(), "item42")
        val req = server.takeRequest()
        val auth = req.getHeader("Authorization")
        assertTrue("got $auth", auth != null && auth.startsWith("MediaBrowser ") && auth.contains("Token=\"tk\""))
        assertEquals(null, req.getHeader("X-Emby-Token"))
    }

    @Test
    fun `continue watching asks for videos only`() = runBlocking {
        // Without the filter the server lists the season and the series of a half-watched episode
        // as resumable too, and each showed up as its own tile beside the episode.
        server.enqueue(MockResponse().setBody("""{"Items":[]}""").setHeader("Content-Type", "application/json"))
        jellyfinResume(config().copy(userId = "u1"))
        val path = server.takeRequest().path!!
        assertTrue("got $path", path.startsWith("/UserItems/Resume"))
        assertTrue("got $path", path.contains("MediaTypes=Video"))
    }
}
