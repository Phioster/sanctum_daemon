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
import org.phioster.sanctumd.notify.Notifications
import org.phioster.sanctumd.notify.parseNtfyLine

/**
 * ntfy priorities reaching the right Android channel, and Prowlarr's connected apps with the
 * same sync its own "Sync App Indexers" button sends.
 */
class PaketDTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    // ── ntfy ──

    @Test fun `the priority is read, and missing means ntfy's default 3`() {
        val urgent = parseNtfyLine("""{"id":"a","event":"message","topic":"t","message":"disk full","priority":5}""")
        val plain = parseNtfyLine("""{"id":"b","event":"message","topic":"t","message":"hello"}""")
        assertEquals(5, urgent?.priority)
        assertEquals(3, plain?.priority)
    }

    @Test fun `an out of range priority is clamped`() {
        assertEquals(5, parseNtfyLine("""{"id":"c","event":"message","message":"x","priority":9}""")?.priority)
    }

    /** 3 and 4 must stay on the original channel, or the user's settings for it would stop applying. */
    @Test fun `each priority lands on its channel`() {
        assertEquals(Notifications.CH_LIVE_QUIET, Notifications.liveChannelFor(1))
        assertEquals(Notifications.CH_LIVE_QUIET, Notifications.liveChannelFor(2))
        assertEquals(Notifications.CH_LIVE, Notifications.liveChannelFor(3))
        assertEquals(Notifications.CH_LIVE, Notifications.liveChannelFor(4))
        assertEquals(Notifications.CH_LIVE_URGENT, Notifications.liveChannelFor(5))
        assertEquals("live", Notifications.CH_LIVE)
    }

    // ── Prowlarr ──

    private fun config() = ServiceConfig(
        id = "pr", type = ServiceType.PROWLARR, label = "Prowlarr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )

    @Test fun `connected apps are listed with kind and sync level`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":2,"name":"Sonarr","implementationName":"Sonarr","syncLevel":"fullSync"},
                    {"id":1,"name":"radarr","implementation":"Radarr","syncLevel":"disabled"}]""",
            ),
        )
        val apps = prowlarrApps(config())
        assertEquals("/api/v1/applications", server.takeRequest().path)
        assertEquals(listOf("radarr", "Sonarr"), apps.map { it.name })
        assertEquals("Radarr", apps[0].kind)
        assertEquals("disabled", apps[0].syncLevel)
    }

    @Test fun `sync sends what Prowlarr's own button sends`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertEquals("sync started", runProwlarrAppSync(config()))
        val req = server.takeRequest()
        assertEquals("POST /api/v1/command", "${req.method} ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body, body.contains(""""name":"ApplicationIndexerSync""""))
        assertTrue(body, body.contains(""""forceSync":true"""))
    }

    @Test fun `safe mode keeps the sync off the network`() = runBlocking {
        SafeMode.enabled = true
        runProwlarrAppSync(config())
        assertEquals(0, server.requestCount)
    }
}
