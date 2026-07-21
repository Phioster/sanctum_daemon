package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Pins the *arr response mapping against recorded payloads: the progress maths, the
 * per-service shape of a "missing" row, and the auth headers every request has to carry
 * (the Cloudflare Access pair is easy to break and only shows up as a 403 on-device).
 */
class ArrApiTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config(type: ServiceType, headers: Map<String, String> = emptyMap()) = ServiceConfig(
        id = "test",
        type = type,
        label = type.label,
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
        customHeaders = headers,
    )

    private fun respond(body: String) = server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `queue progress is derived from size and sizeleft`() = runBlocking {
        respond(
            """{"records":[
                 {"id":7,"title":"Dune.2021.2160p","status":"downloading","size":100.0,"sizeleft":25.0},
                 {"id":8,"title":"Arrival.2016","status":"queued","size":0.0,"sizeleft":0.0}
               ]}"""
        )
        val queue = arrQueue(config(ServiceType.RADARR))
        assertEquals(2, queue.size)
        assertEquals(7, queue[0].id)
        assertEquals("Dune.2021.2160p", queue[0].title)
        assertEquals("downloading", queue[0].status)
        assertEquals(0.75f, queue[0].progress, 0.0001f)
        // size 0 must not divide by zero
        assertEquals(0f, queue[1].progress, 0.0001f)
    }

    @Test
    fun `every request carries the api key and the Cloudflare Access pair`() = runBlocking {
        respond("""{"records":[]}""")
        arrQueue(config(ServiceType.RADARR, mapOf(
            "CF-Access-Client-Id" to "cf-id",
            "CF-Access-Client-Secret" to "cf-secret",
        )))
        val sent = server.takeRequest()
        assertEquals("secret-key", sent.getHeader("X-Api-Key"))
        assertEquals("cf-id", sent.getHeader("CF-Access-Client-Id"))
        assertEquals("cf-secret", sent.getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun `blank custom headers are not sent`() = runBlocking {
        respond("""{"records":[]}""")
        arrQueue(config(ServiceType.RADARR, mapOf("X-Empty" to "", "" to "value")))
        assertEquals(null, server.takeRequest().getHeader("X-Empty"))
    }

    @Test
    fun `sonarr missing rows read as season and episode`() = runBlocking {
        respond(
            """{"records":[{"id":3,"title":"Pilot","seasonNumber":1,"episodeNumber":2,"series":{"title":"Severance"}}]}"""
        )
        val missing = arrMissing(config(ServiceType.SONARR))
        assertEquals("Severance", missing[0].title)
        assertEquals("S01E02 · Pilot", missing[0].subtitle)
    }

    @Test
    fun `radarr missing rows read as title and year`() = runBlocking {
        respond("""{"records":[{"id":4,"title":"Blade Runner 2049","year":2017}]}""")
        val missing = arrMissing(config(ServiceType.RADARR))
        assertEquals("Blade Runner 2049", missing[0].title)
        assertEquals("2017", missing[0].subtitle)
    }

    @Test
    fun `lidarr uses api v1 while radarr and sonarr use v3`() = runBlocking {
        respond("""{"records":[]}""")
        arrQueue(config(ServiceType.LIDARR))
        assertEquals(true, server.takeRequest().path!!.startsWith("/api/v1/queue"))

        respond("""{"records":[]}""")
        arrQueue(config(ServiceType.SONARR))
        assertEquals(true, server.takeRequest().path!!.startsWith("/api/v3/queue"))
    }

    @Test
    fun `unknown fields in a newer arr response do not break parsing`() = runBlocking {
        respond(
            """{"records":[{"id":1,"title":"X","status":"ok","size":10.0,"sizeleft":0.0,
                 "somethingAddedInV6":{"nested":true}}],"totalRecords":1,"newTopLevelField":42}"""
        )
        assertEquals(1, arrQueue(config(ServiceType.RADARR)).size)
    }
}
