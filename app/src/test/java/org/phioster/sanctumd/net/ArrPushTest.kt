package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import retrofit2.Response

/**
 * Guards the "send a Prowlarr release to an arr" path, the class of bug where a bare HTTP
 * 200 from release/push was reported as "sent" even though the arr grabbed nothing. Also
 * covers the Sonarr season query and the manual-import movie assignment added alongside it.
 */
class ArrPushTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        SafeMode.enabled = false // guarded calls must reach the mock server
        server = MockWebServer().also { it.start() }
    }
    @After fun stop() { server.shutdown() }

    private fun config(type: ServiceType = ServiceType.RADARR) = ServiceConfig(
        id = "test", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private fun release(downloadUrl: String = "http://dl/nzb", magnetUrl: String = "") = ProwlarrRelease(
        guid = "guid-1", indexerId = 3, indexer = "NZBgeek",
        title = "The.Odyssey.2026.2160p", sizeMb = 2048, sizeBytes = 2048L * 1024 * 1024,
        protocol = "usenet", seeders = null, ageDays = 1, categories = "Movies",
        downloadUrl = downloadUrl, magnetUrl = magnetUrl, publishDate = "2026-07-01T00:00:00Z",
    )

    private fun ok(body: String): Response<ResponseBody> =
        Response.success(body.toResponseBody("application/json".toMediaType()))

    // ---- reportArrPush (pure) ----

    @Test fun `approved true reports grabbed`() {
        assertEquals("grabbed by Radarr", reportArrPush(ok("""{"approved":true}"""), "Radarr"))
    }

    @Test fun `rejections report not added with the reason`() {
        assertEquals(
            "not added: Unknown Movie",
            reportArrPush(ok("""{"approved":false,"rejections":["Unknown Movie"]}"""), "Radarr"),
        )
    }

    @Test fun `rejected flag without a reason falls back to the add-first hint`() {
        assertEquals(
            "not added: Radarr doesn't track this. Add it there first",
            reportArrPush(ok("""{"rejected":true}"""), "Radarr"),
        )
    }

    @Test fun `array response with reason objects is unwrapped`() {
        assertEquals(
            "not added: Not a wanted movie",
            reportArrPush(ok("""[{"rejections":[{"reason":"Not a wanted movie"}]}]"""), "Radarr"),
        )
    }

    @Test fun `empty or unparseable or neutral body is treated as sent`() {
        assertEquals("sent to Radarr", reportArrPush(ok(""), "Radarr"))
        assertEquals("sent to Radarr", reportArrPush(ok("not json"), "Radarr"))
        assertEquals("sent to Radarr", reportArrPush(ok("""{"id":5}"""), "Radarr"))
    }

    @Test fun `http error is surfaced`() {
        val err = Response.error<ResponseBody>(500, "boom".toResponseBody("text/plain".toMediaType()))
        assertEquals("error: HTTP 500", reportArrPush(err, "Radarr"))
    }

    // ---- arrPushRelease (integration over the mock server) ----

    @Test fun `push sends title and download url to release push`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"approved":true}""").setHeader("Content-Type", "application/json"))
        assertEquals("grabbed by Radarr", arrPushRelease(config(), release()))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/api/v3/release/push"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"title\":\"The.Odyssey.2026.2160p\""))
        assertTrue(body.contains("\"downloadUrl\":\"http://dl/nzb\""))
        assertTrue(body.contains("\"protocol\":\"usenet\""))
    }

    @Test fun `push without any download url never hits the network`() = runBlocking {
        assertEquals("error: release has no download URL", arrPushRelease(config(), release(downloadUrl = "", magnetUrl = "")))
        assertEquals(0, server.requestCount)
    }

    @Test fun `torrent magnet is used when download url is blank`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"approved":true}"""))
        arrPushRelease(config(), release(downloadUrl = "", magnetUrl = "magnet:?xt=abc").copy(protocol = "torrent"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"downloadUrl\":\"magnet:?xt=abc\""))
        assertTrue(body.contains("\"magnetUrl\":\"magnet:?xt=abc\""))
        assertTrue(body.contains("\"protocol\":\"torrent\""))
    }

    // ---- arrReleases season query (new seriesId + seasonNumber param) ----

    @Test fun `sonarr season search builds seriesId and seasonNumber query`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        arrReleases(config(ServiceType.SONARR), movieId = null, episodeId = null, seriesId = 42, seasonNumber = 3)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v3/release?seriesId=42&seasonNumber=3"))
    }

    // ---- arrImportAssignMovie (pure) ----

    @Test fun `assigning a movie injects the id and clears rejections`() {
        val patched = arrImportAssignMovie(
            """{"path":"/dl/x.mkv","rejections":[{"reason":"unknown"}]}""",
            movieId = 77, title = "The Odyssey",
        )
        assertTrue(patched.contains("\"movie\":{\"id\":77"))
        assertTrue(patched.contains("\"title\":\"The Odyssey\""))
        assertTrue(patched.contains("\"rejections\":[]"))
        assertTrue(patched.contains("\"path\":\"/dl/x.mkv\"")) // untouched field survives
    }
}
