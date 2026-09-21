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
 * A request row offered three actions and nothing else — no way to see what a request was
 * actually made with. The fields below are the ones a live Jellyseerr 3.3 returns for
 * `/api/v1/request/{id}`; they were read off the running instance rather than assumed, after
 * an earlier feature was built against an endpoint that turned out not to exist.
 *
 * `rootFolder` and `profileId` are null when the request took the server defaults, which has to
 * read as "default" rather than as blank.
 */
class SeerrRequestDetailTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() = server.shutdown()

    private fun config() = ServiceConfig(
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    private val steered = """{"id":20,"status":2,"type":"movie","is4k":false,"serverId":0,
        "profileId":10,"rootFolder":"/storage/emulated/0/Archiv/Filme",
        "createdAt":"2026-08-18T14:08:11.000Z","seasonCount":0,
        "requestedBy":{"id":1,"username":"SekVII","jellyfinUsername":"SekVII"},
        "media":{"id":88,"mediaType":"movie","tmdbId":36239,"status":3}}"""

    @Test
    fun `a steered request shows the folder and the profile it was made with`() = runBlocking {
        respond(steered)
        respond("""{"title":"Slaughtered Vomit Dolls"}""")           // title lookup
        respond("""[{"id":0,"isDefault":true,"activeDirectory":"/media/Movies","activeProfileId":9}]""")
        respond("""{"rootFolders":[],"profiles":[{"id":9,"name":"[German] HD Bluray + WEB"},{"id":10,"name":"Any"}]}""")

        val d = seerrRequestDetail(config(), 20)

        assertEquals(20, d.id)
        assertEquals("Slaughtered Vomit Dolls", d.title)
        assertEquals("approved", d.status)
        assertEquals("SekVII", d.requestedBy)
        assertEquals("/storage/emulated/0/Archiv/Filme", d.rootFolder)
        assertEquals("Any", d.profile)
        assertTrue("got ${d.created}", d.created.startsWith("2026-08-18"))
    }

    /** The common case: nothing was steered, and the view must say so instead of showing blanks. */
    @Test
    fun `an unsteered request reports the defaults as defaults`() = runBlocking {
        respond(
            """{"id":19,"status":5,"type":"movie","is4k":false,"serverId":0,"profileId":null,
                "rootFolder":null,"createdAt":"2026-08-15T14:00:00.000Z","seasonCount":0,
                "requestedBy":{"id":1,"username":"SekVII"},
                "media":{"id":80,"mediaType":"movie","tmdbId":15157,"status":5}}""",
        )
        respond("""{"title":"Coraline"}""")

        val d = seerrRequestDetail(config(), 19)

        assertEquals("completed", d.status)
        assertEquals("default", d.rootFolder)
        assertEquals("default", d.profile)
        // No profile to resolve means no service lookup at all — two requests, not four.
        assertEquals(2, server.requestCount)
    }
}
