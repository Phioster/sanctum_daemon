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
import org.phioster.sanctumd.model.JellyPackage
import org.phioster.sanctumd.model.JellyPlugin
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.jellyfin.subtitleFormatOf

/** Package G: cancel a task, API keys, remote, split versions, subtitle upload, plugin updates (Jellyfin OpenAPI). */
class JellyfinAdminToolsTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-g")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-g", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )
    private fun line() = server.takeRequest().let { "${it.method} ${it.path}" }

    @Test fun `a running task is cancelled by its id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("cancelled", jellyfinCancelTask(config(), "t9"))
        assertEquals("DELETE /ScheduledTasks/Running/t9", line())
    }

    @Test fun `api keys are listed, created by app name and revoked by key`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"Items":[{"AccessToken":"abc123def","AppName":"Sonarr","DateCreated":"2026-01-02T03:04:05Z","DateLastActivity":"2026-10-17T08:00:00Z"}],"TotalRecordCount":1}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val k = jellyfinApiKeys(config()).single()
        assertEquals(JellyApiKey("abc123def", "Sonarr", "2026-01-02", "2026-10-17"), k)
        assertEquals("key created", jellyfinCreateApiKey(config(), " Radarr "))
        assertEquals("key revoked", jellyfinRevokeApiKey(config(), "abc123def"))
        assertEquals("GET /Auth/Keys", line())
        assertEquals("POST /Auth/Keys?app=Radarr", line())
        assertEquals("DELETE /Auth/Keys/abc123def", line())
    }

    @Test fun `remote commands use the general command route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        jellyfinRemote(config(), "s1", "VolumeUp")
        assertEquals("POST /Sessions/s1/Command/VolumeUp", line())
    }

    @Test fun `splitting removes the alternate sources`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("versions split", jellyfinSplitVersions(config(), "m1"))
        assertEquals("DELETE /Videos/m1/AlternateSources", line())
    }

    @Test fun `a subtitle goes up as base64 with its format`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("subtitle uploaded", jellyfinUploadSubtitle(config(), "m1", "ger", "srt", forced = false, hearingImpaired = true, bytes = "1\nHallo".toByteArray()))
        val req = server.takeRequest()
        assertEquals("POST /Videos/m1/Subtitles", "${req.method} ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body, body.contains(""""Language":"ger""""))
        assertTrue(body, body.contains(""""Format":"srt""""))
        assertTrue(body, body.contains(""""IsHearingImpaired":true"""))
        assertTrue(body, body.contains(""""Data":"MQpIYWxsbw==""""))
    }

    @Test fun `only real subtitle files are accepted`() {
        assertEquals("srt", subtitleFormatOf("Film.de.SRT"))
        assertEquals("ass", subtitleFormatOf("a.ass"))
        assertEquals(null, subtitleFormatOf("notes.txt"))
        assertEquals(null, subtitleFormatOf("noext"))
    }

    @Test fun `versions compare number by number`() {
        assertTrue(isNewerVersion("10.0.0.0", "11.0.0.0"))
        assertTrue(isNewerVersion("9.9", "9.10"))
        assertFalse(isNewerVersion("12.0.0.0", "12.0.0.0"))
        assertFalse(isNewerVersion("13.1", "13.0.9"))
    }

    @Test fun `plugin updates match by guid either way it is written, else by name`() {
        val installed = listOf(
            JellyPlugin("a1b2c3d4e5f60718293a4b5c6d7e8f90", "10.0.0.0", "TMDb", "", "Active", false),
            JellyPlugin("ffff", "3.0.0.0", "Intro Skipper", "", "Active", true),
            JellyPlugin("eeee", "5.0.0.0", "Up To Date", "", "Active", true),
        )
        val catalog = listOf(
            JellyPackage("TMDb", "A1B2C3D4-E5F6-0718-293A-4B5C6D7E8F90", "", "11.0.0.0"),
            JellyPackage("intro skipper", "", "", "3.1.0.0"),
            JellyPackage("Up To Date", "eeee", "", "5.0.0.0"),
        )
        val up = pluginUpdates(installed, catalog)
        assertEquals(setOf("a1b2c3d4e5f60718293a4b5c6d7e8f90", "ffff"), up.keys)
        assertEquals("11.0.0.0", up.getValue("a1b2c3d4e5f60718293a4b5c6d7e8f90").version)
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinCancelTask(config(), "t")
        jellyfinCreateApiKey(config(), "x")
        jellyfinRevokeApiKey(config(), "k")
        jellyfinRemote(config(), "s", "Mute")
        jellyfinSplitVersions(config(), "m")
        jellyfinUploadSubtitle(config(), "m", "ger", "srt", false, false, ByteArray(1))
        assertEquals(0, server.requestCount)
    }
}
