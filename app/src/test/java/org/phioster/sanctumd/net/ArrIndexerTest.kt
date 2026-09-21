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
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Sonarr and Radarr keep their own indexer failure counters: when Prowlarr answers 429 they lock
 * the indexer out on their side too, and it stays locked until a successful test resets it.
 *
 * **They expose that through the health check, not through an indexerstatus endpoint** — that
 * endpoint returns 404 on Radarr 6.3 and Sonarr 4.0 (measured on a live instance). The earlier
 * implementation called it anyway and swallowed the failure, so every indexer always looked
 * healthy. The tests passed because MockWebServer will happily serve an endpoint that does not
 * exist; that is the trap these tests now guard against.
 */
class ArrIndexerTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config(type: ServiceType = ServiceType.SONARR, label: String = type.label) = ServiceConfig(
        id = "svc-${label.lowercase()}",
        type = type,
        label = label,
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
    )

    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    private val oneIndexer =
        """[{"id":3,"name":"treasure-maps (Prowlarr)","protocol":"usenet","priority":25,
             "enableRss":true,"enableAutomaticSearch":true,"enableInteractiveSearch":true}]"""

    @Test
    fun `a health warning naming the indexer marks it failing`() = runBlocking {
        respond(oneIndexer)
        respond(
            """[{"source":"IndexerStatusCheck","type":"warning",
                 "message":"Indexers unavailable due to failures: treasure-maps (Prowlarr)"}]""",
        )

        val indexers = arrIndexers(config())

        assertTrue(indexers[0].failing)
        assertFalse(indexers[0].statusUnknown)
    }

    @Test
    fun `an indexer the warning does not name stays healthy`() = runBlocking {
        respond(oneIndexer)
        respond(
            """[{"source":"IndexerStatusCheck","type":"warning",
                 "message":"Indexers unavailable due to failures: some-other-indexer"}]""",
        )

        assertFalse(arrIndexers(config())[0].failing)
    }

    @Test
    fun `an unrelated health warning does not mark anything failing`() = runBlocking {
        respond(oneIndexer)
        respond("""[{"source":"UpdateCheck","type":"warning","message":"New update is available"}]""")

        assertFalse(arrIndexers(config())[0].failing)
    }

    @Test
    fun `a healthy service reports nothing failing`() = runBlocking {
        respond(oneIndexer)
        respond("[]")

        val ix = arrIndexers(config())[0]
        assertFalse(ix.failing)
        assertFalse(ix.statusUnknown)
    }

    /**
     * The defect this replaces: an unreachable status source was reported as "all healthy".
     * Unknown has to stay distinguishable from fine, or the view lies with a straight face.
     */
    @Test
    fun `an unreadable health check yields unknown, never healthy`() = runBlocking {
        respond(oneIndexer)
        server.enqueue(MockResponse().setResponseCode(404))

        val ix = arrIndexers(config())[0]
        assertTrue("status must be flagged as unknown", ix.statusUnknown)
        assertFalse("unknown is not the same as failing", ix.failing)
    }

    @Test
    fun `the indexer list still loads when the health check fails`() = runBlocking {
        respond(oneIndexer)
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(1, arrIndexers(config()).size)
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
