package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.JellyRefreshMode
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Four small server tools: refresh modes, removing a device, disk space and approving a
 * Quick Connect code. Each one's request shape was taken from the official OpenAPI spec.
 */
class JellyfinServerToolsTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-tools")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-tools", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )
    private fun respond(code: Int = 200, body: String = "") =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json"))

    // ── Refresh ──

    /** The server defaults to None without explicit modes; the scan chips must send Default. */
    @Test fun `a plain scan sends Default like the web client`() = runBlocking {
        respond(204)
        assertEquals("scan started", jellyfinScanItem(config(), "i1"))
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/Items/i1/Refresh?"))
        assertTrue(path, path.contains("metadataRefreshMode=Default"))
        assertTrue(path, path.contains("imageRefreshMode=Default"))
        assertTrue(path, path.contains("replaceAllMetadata=false"))
    }

    @Test fun `replace all metadata with images is a full refresh replacing both`() = runBlocking {
        respond(204)
        jellyfinRefreshItem(config(), "i1", JellyRefreshMode.REPLACE, replaceImages = true)
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("metadataRefreshMode=FullRefresh"))
        assertTrue(path, path.contains("imageRefreshMode=FullRefresh"))
        assertTrue(path, path.contains("replaceAllMetadata=true"))
        assertTrue(path, path.contains("replaceAllImages=true"))
    }

    @Test fun `missing metadata keeps what is there`() {
        val q = refreshQuery(JellyRefreshMode.MISSING, replaceImages = false)
        assertEquals(JfRefreshQuery("FullRefresh", "FullRefresh", replaceMetadata = false, replaceImages = false), q)
    }

    @Test fun `a scan never replaces images, even if asked`() {
        assertEquals(false, refreshQuery(JellyRefreshMode.SCAN, replaceImages = true).replaceImages)
    }

    // ── Devices ──

    @Test fun `removing a device deletes it by id`() = runBlocking {
        respond(204)
        assertEquals("device removed", jellyfinDeleteDevice(config(), "abc123"))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/Devices?id=abc123", req.path)
    }

    @Test fun `the app's own device is recognised in the list`() = runBlocking {
        respond(body = """{"Items":[{"Id":"other","Name":"TV","AppName":"Sanctumd TV"},{"Id":"","Name":"Phone"}]}""")
        val list = jellyfinDevices(config())
        assertEquals(listOf("other", ""), list.map { it.id })
        // An empty id must never count as "this app", or any id-less entry would be protected.
        assertTrue(list.none { it.own })
    }

    @Test fun `a custom device name wins over the reported one`() = runBlocking {
        respond(body = """{"Items":[{"Id":"d","Name":"SM-G991B","CustomName":"Wohnzimmer"}]}""")
        assertEquals("Wohnzimmer", jellyfinDevices(config()).single().name)
    }

    @Test fun `safe mode keeps device removal off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinDeleteDevice(config(), "abc123")
        assertEquals(0, server.requestCount)
    }

    // ── Storage ──

    @Test fun `libraries come first and a shared server path is listed once`() = runBlocking {
        respond(
            body = """{"ProgramDataFolder":{"Path":"/config","FreeSpace":10,"UsedSpace":90},
                "CacheFolder":{"Path":"/config","FreeSpace":10,"UsedSpace":90},
                "TranscodingTempFolder":{"Path":"/transcode","FreeSpace":5,"UsedSpace":5},
                "Libraries":[{"Name":"Filme","Folders":[{"Path":"/media/movies","FreeSpace":100,"UsedSpace":900}]}]}""",
        )
        val rows = jellyfinStorage(config())
        assertEquals(listOf("Filme", "data", "transcodes"), rows.map { it.label })
        assertEquals(100L, rows[0].freeBytes)
        assertEquals(900L, rows[0].usedBytes)
    }

    // ── Quick Connect ──

    @Test fun `approving a code names the user to sign in as`() = runBlocking {
        respond(body = "true")
        assertEquals("approved", jellyfinApproveQuickConnect(config(), " 123456 "))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/QuickConnect/Authorize?code=123456&userId=u1", req.path)
    }

    @Test fun `an unknown code says so`() = runBlocking {
        respond(404)
        assertTrue(jellyfinApproveQuickConnect(config(), "000000").contains("no pending request"))
    }
}
