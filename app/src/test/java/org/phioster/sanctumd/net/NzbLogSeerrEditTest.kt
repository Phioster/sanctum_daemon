package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.NzbLogEntry
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.nzbget.problemsOnly

/**
 * NZBGet's message log (method shapes from its XmlRpc.cpp), and editing a pending Seerr
 * request (body from seerr-api.yml, where a TV request must always carry its seasons).
 */
class NzbLogSeerrEditTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    // ── NZBGet ──

    private fun nzb() = ServiceConfig(
        id = "nzb", type = ServiceType.NZBGET, label = "NZBGet",
        baseUrl = server.url("/").toString(), username = "u", password = "p",
    )

    @Test fun `the server log asks for the last entries and lists newest first`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":[{"ID":1,"Kind":"INFO","Time":1760000000,"Text":"old"},
                    {"ID":2,"Kind":"ERROR","Time":1760000060,"Text":"new"}]}""",
            ),
        )
        val log = nzbgetLog(nzb(), count = 50)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains(""""method":"log""""))
        assertTrue(body, body.contains(""""params":[0,50]"""))
        assertEquals(listOf("new", "old"), log.map { it.text })
        assertEquals("ERROR", log[0].kind)
    }

    @Test fun `one download's log is loadlog with its id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":[]}"""))
        nzbgetItemLog(nzb(), 42, count = 10)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains(""""method":"loadlog""""))
        assertTrue(body, body.contains(""""params":[42,0,10]"""))
    }

    @Test fun `the problems filter keeps errors and warnings only`() {
        val all = listOf("INFO", "WARNING", "DETAIL", "ERROR").map { NzbLogEntry(it, "", it) }
        assertEquals(listOf("WARNING", "ERROR"), problemsOnly(all).map { it.kind })
    }

    // ── Seerr ──

    private fun seerr() = ServiceConfig(
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )

    @Test fun `a tv edit always carries its seasons`() {
        val body = requestUpdateBody("tv", is4k = false, serverId = 0, profileId = 9, rootFolder = "/tv/Archiv", seasons = listOf(3, 1))
        assertEquals(
            """{"mediaType":"tv","is4k":false,"serverId":0,"profileId":9,"rootFolder":"/tv/Archiv","seasons":[1,3]}""",
            body.toString(),
        )
    }

    @Test fun `a movie edit leaves seasons and defaults out`() {
        val body = requestUpdateBody("movie", is4k = false, serverId = null, profileId = null, rootFolder = null, seasons = emptyList())
        assertEquals("""{"mediaType":"movie","is4k":false}""", body.toString())
    }

    @Test fun `the detail keeps what an edit must send back`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":40,"status":1,"type":"tv","is4k":false,"serverId":0,"profileId":null,"rootFolder":null,
                    "createdAt":"2026-10-01T10:00:00.000Z","requestedBy":{"username":"SekVII"},
                    "seasons":[{"seasonNumber":2},{"seasonNumber":1}],
                    "media":{"id":9,"mediaType":"tv","tmdbId":5,"status":2}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"name":"Show"}"""))
        val d = seerrRequestDetail(seerr(), 40)
        assertEquals(0, d.serverId)
        assertEquals(null, d.profileId)
        assertEquals(null, d.rootFolderPath)
        assertEquals(listOf(1, 2), d.seasons)
    }

    @Test fun `saving puts the body to the request`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals("updated", seerrUpdateRequest(seerr(), 40, "tv", false, 0, 9, null, listOf(1)))
        val req = server.takeRequest()
        assertEquals("PUT /api/v1/request/40", "${req.method} ${req.path}")
    }

    @Test fun `a request that is no longer pending says so`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409))
        assertTrue(seerrUpdateRequest(seerr(), 40, "movie", false, null, 9, null, emptyList()).contains("only pending"))
    }

    @Test fun `safe mode keeps the edit off the network`() = runBlocking {
        SafeMode.enabled = true
        seerrUpdateRequest(seerr(), 40, "movie", false, null, 9, null, emptyList())
        assertEquals(0, server.requestCount)
    }
}
