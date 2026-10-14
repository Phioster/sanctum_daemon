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
 * Monitoring a single Sonarr season. The series is written back whole, so a wrong match would
 * silently flip another season or drop fields Sonarr needs.
 */
class ArrSeasonMonitorTest {

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
        id = "test", type = ServiceType.SONARR, label = "Sonarr",
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private val series = """{"id":7,"title":"Grimm","monitored":true,"qualityProfileId":4,""" +
        """"seasons":[{"seasonNumber":0,"monitored":false},{"seasonNumber":1,"monitored":true},{"seasonNumber":2,"monitored":false}]}"""

    @Test fun `only the chosen season flips and the series goes back whole`() = runBlocking {
        server.enqueue(MockResponse().setBody(series))
        server.enqueue(MockResponse().setResponseCode(202).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(202).setBody("[]"))
        val result = arrSetSeasonMonitored(config(), seriesId = 7, seasonNumber = 2, monitored = true, episodeIds = listOf(21, 22))

        assertEquals("GET /api/v3/series/7", server.takeRequest().let { "${it.method} ${it.path}" })
        val put = server.takeRequest()
        assertEquals("PUT /api/v3/series/7", "${put.method} ${put.path}")
        val body = put.body.readUtf8()
        assertTrue("got $body", body.contains("""{"seasonNumber":2,"monitored":true}"""))
        assertTrue("got $body", body.contains("""{"seasonNumber":1,"monitored":true}"""))
        assertTrue("got $body", body.contains("""{"seasonNumber":0,"monitored":false}"""))
        assertTrue("got $body", body.contains(""""qualityProfileId":4"""))

        val eps = server.takeRequest()
        assertEquals("PUT /api/v3/episode/monitor", "${eps.method} ${eps.path}")
        val epBody = eps.body.readUtf8()
        assertTrue("got $epBody", epBody.contains(""""episodeIds":[21,22]"""))
        assertTrue("got $epBody", epBody.contains(""""monitored":true"""))
        assertEquals("monitoring", result)
    }

    @Test fun `a failed series update does not touch the episodes`() = runBlocking {
        server.enqueue(MockResponse().setBody(series))
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        val result = arrSetSeasonMonitored(config(), 7, 1, false, listOf(11))
        assertEquals("error: HTTP 400", result)
        assertEquals(2, server.requestCount)
    }

    @Test fun `safe mode keeps it off the network`() = runBlocking {
        SafeMode.enabled = true
        arrSetSeasonMonitored(config(), 7, 1, false, listOf(11))
        assertEquals(0, server.requestCount)
    }

    @Test fun `the detail reads each season's flag`() = runBlocking {
        server.enqueue(MockResponse().setBody(series))
        val d = arrDetail(config(), 7)
        assertEquals(mapOf(0 to false, 1 to true, 2 to false), d.seasonMonitored)
    }
}
