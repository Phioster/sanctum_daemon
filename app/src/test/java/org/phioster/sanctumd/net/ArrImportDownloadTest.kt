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
 * The blocked-queue "import" went round in circles (2026-11-07, "Pippi geht von Bord"): the folder
 * scan could not match the German release name, the unmatched row went out without a movieId,
 * Radarr failed the command with "Movie with ID 0 does not exist", the app said "importing", and
 * the same entry popped up again. Scanning by downloadId matches it through the grab history.
 */
class ArrImportDownloadTest {

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
        id = "test", type = ServiceType.RADARR, label = "Radarr",
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    @Test
    fun `a queue entry is scanned by its downloadId and imported with it`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """[{"path":"/dl/Pippi.geht.von.Bord.mkv","relativePath":"Pippi.geht.von.Bord.mkv",
                "quality":{"quality":{"name":"WEBDL-1080p"}},"languages":[{"id":4,"name":"German"}],
                "releaseGroup":"SiXTYNiNE","movie":{"id":31,"title":"Pippi Goes on Board"},"rejections":[]}]""",
        ))
        val rows = arrManualImportScan(config(), "/dl", downloadId = "ABC123")
        val scan = server.takeRequest().path!!
        assertTrue(scan, scan.contains("manualimport?downloadId=ABC123"))
        assertTrue(rows.single().importable)

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":9,"status":"queued"}"""))
        server.enqueue(MockResponse().setBody("""{"id":9,"status":"completed","message":"Manually imported 1 files"}"""))
        val result = arrManualImportExecute(config(), listOf(rows.single().rawJson))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"downloadId\":\"ABC123\""))
        assertTrue(body, body.contains("\"movieId\":31"))
        assertTrue(body, body.contains("\"releaseGroup\":\"SiXTYNiNE\""))
        assertTrue(body, body.contains("\"languages\""))
        assertEquals("/api/v3/command/9", server.takeRequest().path)
        assertEquals("Manually imported 1 files", result)
    }

    @Test
    fun `a folder scan stays a folder scan`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        arrManualImportScan(config(), "/dl/x")
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("manualimport?folder=%2Fdl%2Fx"))
    }

    @Test
    fun `a command the server fails afterwards is reported as an error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":4,"status":"queued"}"""))
        server.enqueue(MockResponse().setBody("""{"id":4,"status":"failed","message":"Movie with ID 0 does not exist"}"""))
        val result = arrManualImportExecute(config(), listOf("""{"path":"/dl/y.mkv","movie":{"id":5}}"""))
        assertEquals("error: Movie with ID 0 does not exist", result)
    }

    @Test
    fun `verdicts`() {
        assertEquals("imported 2 file(s)", importVerdict("completed", null, 2))
        assertEquals("error: import aborted", importVerdict("aborted", "", 1))
        assertEquals("importing 1 file(s) in the background", importVerdict("started", null, 1))
    }

    @Test
    fun `awaitCommand gives up after the timeout`() = runBlocking {
        var calls = 0
        val r = awaitCommand(1, timeoutMs = 30, stepMs = 10) { calls++; kotlinx.serialization.json.JsonObject(emptyMap()) }
        assertEquals(null, r)
        assertTrue(calls >= 2)
    }
}
