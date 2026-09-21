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
 * Moving an item to another root folder — how something ends up in the private archive after the
 * fact, instead of only at the moment it is added.
 *
 * Each service names both the route and the id field differently, and getting either wrong fails
 * quietly: the editor accepts an empty list and answers 202, so a body with the wrong field name
 * looks exactly like a successful move that moved nothing. Verified live before writing this:
 * Radarr 6.3.0, Sonarr 4.0.19 and Lidarr all answer 202 on their editor route.
 */
class ArrMoveTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config(type: ServiceType) = ServiceConfig(
        id = "test", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private fun move(type: ServiceType) = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("[]"))
        val result = arrMoveToRootFolder(config(type), id = 43, rootFolderPath = "/media/Archive/Movies")
        val req = server.takeRequest()
        Triple(result, req.path!!, req.body.readUtf8())
    }

    @Test fun `radarr moves a movie`() {
        val (result, path, body) = move(ServiceType.RADARR)
        assertEquals("PUT /api/v3/movie/editor", "PUT $path")
        assertTrue("got $body", body.contains(""""movieIds":[43]"""))
        assertEquals("moved", result)
    }

    @Test fun `sonarr moves a series`() {
        val (_, path, body) = move(ServiceType.SONARR)
        assertEquals("/api/v3/series/editor", path)
        assertTrue("got $body", body.contains(""""seriesIds":[43]"""))
    }

    @Test fun `lidarr moves an artist on its v1 api`() {
        val (_, path, body) = move(ServiceType.LIDARR)
        assertEquals("/api/v1/artist/editor", path)
        assertTrue("got $body", body.contains(""""artistIds":[43]"""))
    }

    /** Without this the entry moves and the files stay behind, split across two folders. */
    @Test fun `the files travel with the entry`() {
        val (_, _, body) = move(ServiceType.RADARR)
        assertTrue("got $body", body.contains(""""moveFiles":true"""))
        assertTrue("got $body", body.contains(""""rootFolderPath":"/media/Archive/Movies""""))
    }
}
