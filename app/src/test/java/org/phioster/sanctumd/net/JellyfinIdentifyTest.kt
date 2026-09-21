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
 * German release names regularly leave Jellyfin matched to the wrong title, or to nothing.
 * Identify asks the metadata providers for candidates and pins the item to the chosen one.
 * The same two-step the web UI does, because a single "refresh" would only re-derive the same
 * wrong guess from the same filename.
 */
class JellyfinIdentifyTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-id")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-id", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false,
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `candidates come back with the year and provider ids needed to tell them apart`() = runBlocking {
        respond(
            """[{"Name":"Jackass: Best and Last","ProductionYear":2022,
                 "ProviderIds":{"Tmdb":"1016170"},"ImageUrl":"http://img/1.jpg",
                 "SearchProviderName":"TheMovieDb"}]""",
        )

        val results = jellyfinIdentifyCandidates(config(), "i1", "Movie", "Jackass", null)

        assertEquals(1, results.size)
        assertEquals("Jackass: Best and Last", results[0].name)
        assertEquals(2022, results[0].year)
        assertEquals("TheMovieDb", results[0].provider)
    }

    @Test
    fun `a movie search asks the movie endpoint and carries the item id`() = runBlocking {
        respond("[]")

        jellyfinIdentifyCandidates(config(), "i1", "Movie", "Jackass", 2022)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/Items/RemoteSearch/Movie"))
        val body = req.body.readUtf8()
        assertTrue("got $body", body.contains("\"ItemId\":\"i1\""))
        assertTrue("got $body", body.contains("\"Name\":\"Jackass\""))
        assertTrue("got $body", body.contains("\"Year\":2022"))
    }

    /** A series must not be looked up against the movie database. */
    @Test
    fun `a series search asks the series endpoint`() = runBlocking {
        respond("[]")

        jellyfinIdentifyCandidates(config(), "s1", "Series", "Black Torch", null)

        assertTrue(server.takeRequest().path!!.endsWith("/Items/RemoteSearch/Series"))
    }

    @Test
    fun `applying a candidate posts it to the item and replaces its images`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = jellyfinApplyIdentify(
            config(), "i1",
            JellyIdentifyCandidateRaw("""{"Name":"Jackass: Best and Last","ProviderIds":{"Tmdb":"1016170"}}"""),
        )

        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertEquals("POST", req.method)
        assertTrue("got ${req.path}", req.path!!.startsWith("/Items/RemoteSearch/Apply/i1"))
        assertTrue("got ${req.path}", req.path!!.contains("replaceAllImages=true"))
        // The chosen candidate has to travel back verbatim. That is what pins the item.
        assertTrue("got $body", body.contains("\"Tmdb\":\"1016170\""))
        assertEquals("identified", result)
    }
}
