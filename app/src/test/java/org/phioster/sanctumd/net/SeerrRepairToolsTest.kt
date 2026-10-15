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
 * Retry a failed request, fix a stuck media status, run a job now. Routes and bodies are the
 * ones in Seerr's own OpenAPI spec (seerr-api.yml).
 */
class SeerrRepairToolsTest {

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
    private fun respond(code: Int = 200, body: String = "{}") =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json"))

    @Test fun `a failed request is flagged and carries its media`() = runBlocking {
        respond(
            body = """{"id":31,"status":4,"type":"movie","is4k":false,"profileId":null,"rootFolder":null,
                "createdAt":"2026-10-01T10:00:00.000Z","requestedBy":{"username":"SekVII"},
                "media":{"id":120,"mediaType":"movie","tmdbId":1,"status":3,"status4k":1}}""",
        )
        respond(body = """{"title":"X"}""")
        val d = seerrRequestDetail(config(), 31)
        assertEquals(true, d.failed)
        assertEquals(120, d.mediaId)
        assertEquals("processing", d.mediaStatus)
    }

    @Test fun `retry posts to the request's retry route`() = runBlocking {
        respond()
        assertEquals("sent again", seerrRetryRequest(config(), 31))
        val req = server.takeRequest()
        assertEquals("POST /api/v1/request/31/retry", "${req.method} ${req.path}")
    }

    @Test fun `an older Seerr without the route says so`() = runBlocking {
        respond(404)
        assertTrue(seerrRetryRequest(config(), 31).contains("can't retry"))
    }

    @Test fun `mark available names the status in the path and the 4K flag in the body`() = runBlocking {
        respond()
        assertEquals("marked available", seerrMarkAvailable(config(), 120, is4k = false))
        val req = server.takeRequest()
        assertEquals("POST /api/v1/media/120/available", "${req.method} ${req.path}")
        assertEquals("""{"is4k":false}""", req.body.readUtf8())
    }

    @Test fun `reset deletes the media entry`() = runBlocking {
        respond(204, "")
        assertEquals("reset", seerrResetMedia(config(), 120))
        val req = server.takeRequest()
        assertEquals("DELETE /api/v1/media/120", "${req.method} ${req.path}")
    }

    @Test fun `jobs are listed with their next run, and run by id`() = runBlocking {
        respond(
            body = """[{"id":"jellyfin-recently-added-scan","name":"Jellyfin Recently Added Scan","type":"process",
                "interval":"minutes","nextExecutionTime":"2026-10-15T14:05:00.000Z","running":false},
                {"id":"radarr-scan","name":"Radarr Scan","nextExecutionTime":"2026-10-16T03:00:00.000Z","running":true}]""",
        )
        val jobs = seerrJobs(config())
        assertEquals(listOf("jellyfin-recently-added-scan", "radarr-scan"), jobs.map { it.id })
        assertEquals("2026-10-15 14:05", jobs[0].nextRun)
        assertEquals(true, jobs[1].running)

        respond()
        assertEquals("started", seerrRunJob(config(), "radarr-scan"))
        server.takeRequest()
        val run = server.takeRequest()
        assertEquals("POST /api/v1/settings/jobs/radarr-scan/run", "${run.method} ${run.path}")
    }

    @Test fun `safe mode keeps every repair off the network`() = runBlocking {
        SafeMode.enabled = true
        seerrRetryRequest(config(), 31)
        seerrMarkAvailable(config(), 120, false)
        seerrResetMedia(config(), 120)
        seerrRunJob(config(), "radarr-scan")
        assertEquals(0, server.requestCount)
    }
}
