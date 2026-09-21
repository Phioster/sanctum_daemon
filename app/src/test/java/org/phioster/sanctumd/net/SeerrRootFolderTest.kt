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
 * Requests can be steered into a specific root folder — that is how a movie ends up in a
 * private library instead of the shared one. The folder must come from Seerr's own service
 * config (the value has to match what Seerr knows), and it must only appear in the request
 * body when the user actually picked one.
 */
class SeerrRootFolderTest {

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
        id = "seerr-1",
        type = ServiceType.SEERR,
        label = "Seerr",
        baseUrl = server.url("/").toString(),
        apiKey = "key",
        useLogin = false,
    )

    private fun enqueueService() {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":0,"name":"Radarr","isDefault":true,"activeDirectory":"/media/Movies","activeProfileId":9}]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"rootFolders":[{"id":4,"path":"/media/Movies"},{"id":5,"path":"/media/Archive/Movies"}],
                    "profiles":[{"id":9,"name":"[German] HD Bluray + WEB"},{"id":10,"name":"Any"}]}""",
            ),
        )
    }

    @Test
    fun `root folders come from the default server for that media type`() = runBlocking {
        enqueueService()

        val folders = seerrServiceOptions(config(), "movie").rootFolders

        assertEquals(listOf("/media/Movies", "/media/Archive/Movies"), folders.map { it.path })
        assertTrue("the server's activeDirectory is the default", folders[0].isDefault)
        assertFalse(folders[1].isDefault)
        assertEquals(0, folders[1].serverId)
    }

    /**
     * A request that cannot choose its quality profile always lands on the default one — which
     * here demands a German custom-format score, so an English-only film is rejected outright.
     */
    @Test
    fun `the same call also yields the quality profiles and which is default`() = runBlocking {
        enqueueService()

        val opts = seerrServiceOptions(config(), "movie")

        assertEquals(listOf("[German] HD Bluray + WEB", "Any"), opts.profiles.map { it.name })
        assertEquals(9, opts.defaultProfileId)
    }

    @Test
    fun `tv requests read the sonarr service, not radarr`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":2,"name":"Sonarr","isDefault":true,"activeDirectory":"/media/Series"}]"""))
        server.enqueue(MockResponse().setBody("""{"rootFolders":[{"id":2,"path":"/media/Series"}],"profiles":[]}"""))

        seerrServiceOptions(config(), "tv")

        assertEquals("/api/v1/service/sonarr", server.takeRequest().path)
        assertEquals("/api/v1/service/sonarr/2", server.takeRequest().path)
    }

    @Test
    fun `a chosen root folder travels in the request body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        seerrRequest(config(), tmdbId = 42, mediaType = "movie", seasons = null, rootFolder = "/media/Archive/Movies", serverId = 0)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("got $body", body.contains(""""rootFolder":"/media/Archive/Movies""""))
        assertTrue("got $body", body.contains(""""serverId":0"""))
    }

    @Test
    fun `a chosen quality profile travels in the request body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        seerrRequest(config(), tmdbId = 42, mediaType = "movie", seasons = null, profileId = 10)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("got $body", body.contains(""""profileId":10"""))
    }

    @Test
    fun `without a chosen profile the body carries no profile`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        seerrRequest(config(), tmdbId = 42, mediaType = "movie", seasons = null)

        assertFalse(server.takeRequest().body.readUtf8().contains("profileId"))
    }

    @Test
    fun `a completed request is named, not shown as a question mark`() {
        assertEquals("pending", seerrStatusText(1))
        assertEquals("approved", seerrStatusText(2))
        assertEquals("declined", seerrStatusText(3))
        assertEquals("failed", seerrStatusText(4))
        // Status 5 is what every finished request carries; it used to render as "?".
        assertEquals("completed", seerrStatusText(5))
        assertEquals("?", seerrStatusText(99))
    }

    @Test
    fun `a request can be deleted`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = seerrDeleteRequest(config(), id = 20)

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/api/v1/request/20"))
        assertEquals("deleted", result)
    }

    @Test
    fun `without a chosen folder the body carries no root folder`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        seerrRequest(config(), tmdbId = 42, mediaType = "movie", seasons = null)

        val body = server.takeRequest().body.readUtf8()
        assertFalse("got $body", body.contains("rootFolder"))
        assertFalse("got $body", body.contains("serverId"))
    }
}
