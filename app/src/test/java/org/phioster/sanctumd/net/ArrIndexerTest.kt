package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import java.time.Instant

/**
 * Sonarr and Radarr keep their own indexer failure counters, separate from Prowlarr's: when
 * Prowlarr answers 429 they lock the indexer out on their side too, and it stays locked until
 * a successful test resets it. That repair is what this covers — including telling a temporary
 * lockout (recovers by itself) apart from a permanently broken indexer.
 */
class ArrIndexerTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config(type: ServiceType = ServiceType.SONARR, label: String = type.label) = ServiceConfig(
        id = "svc-${label.lowercase()}",
        type = type,
        label = label,
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
    )

    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    private val now: Instant = Instant.parse("2026-08-17T01:30:00Z")

    @Test
    fun `a locked out indexer reports until when it is disabled`() = runBlocking {
        respond("""[{"id":3,"name":"treasure-maps (Prowlarr)","protocol":"usenet","priority":25,
                    "enableRss":true,"enableAutomaticSearch":true,"enableInteractiveSearch":true}]""")
        respond("""[{"indexerId":3,"disabledTill":"2026-08-17T02:03:39Z","mostRecentFailure":"2026-08-17T01:03:39Z"}]""")

        val indexers = arrIndexers(config(), now)

        assertEquals(1, indexers.size)
        assertTrue(indexers[0].failing)
        assertEquals("2026-08-17T02:03:39Z", indexers[0].disabledTill)
    }

    /**
     * The *arr apps keep the status row after a lockout expires, because the escalation level
     * has to survive so repeated failures back off faster. Reading "row exists" as "locked out"
     * would therefore paint an indexer red forever after its first bad day.
     */
    @Test
    fun `a lockout that has already expired is not a lockout`() = runBlocking {
        respond("""[{"id":3,"name":"treasure-maps (Prowlarr)","protocol":"usenet","enableRss":true}]""")
        respond("""[{"indexerId":3,"disabledTill":"2026-08-17T01:00:00Z","mostRecentFailure":"2026-08-17T00:00:00Z"}]""")

        val indexers = arrIndexers(config(), now)

        assertFalse(indexers[0].failing)
        assertNull(indexers[0].disabledTill)
    }

    @Test
    fun `an indexer without a status entry counts as healthy`() = runBlocking {
        respond("""[{"id":3,"name":"treasure-maps (Prowlarr)","protocol":"usenet","enableRss":true}]""")
        respond("""[]""")

        val indexers = arrIndexers(config(), now)

        assertFalse(indexers[0].failing)
        assertNull(indexers[0].disabledTill)
    }

    @Test
    fun `the indexer list still loads when the status endpoint fails`() = runBlocking {
        respond("""[{"id":3,"name":"treasure-maps (Prowlarr)","protocol":"usenet","enableRss":true}]""")
        server.enqueue(MockResponse().setResponseCode(500))

        val indexers = arrIndexers(config(), now)

        assertEquals(1, indexers.size)
        assertFalse(indexers[0].failing)
    }

    @Test
    fun `testing all indexers posts to the service's own testall endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("[]"))

        val result = arrTestAllIndexers(config(ServiceType.SONARR))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v3/indexer/testall", req.path)
        assertEquals("tested", result)
    }

    @Test
    fun `lidarr uses its v1 api path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("[]"))

        arrTestAllIndexers(config(ServiceType.LIDARR))

        assertEquals("/api/v1/indexer/testall", server.takeRequest().path)
    }

    @Test
    fun `repairing across services reports one result per service`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(500))

        val results = arrRepairIndexers(
            listOf(config(ServiceType.SONARR, "Sonarr"), config(ServiceType.RADARR, "Radarr")),
        )

        assertEquals(listOf("Sonarr", "Radarr"), results.map { it.first })
        assertEquals("tested", results[0].second)
        assertTrue("got ${results[1].second}", results[1].second.startsWith("error"))
    }
}
