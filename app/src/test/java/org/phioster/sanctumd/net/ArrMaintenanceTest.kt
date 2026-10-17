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

/** Package H: logs, backups and import exclusions, as the Sonarr/Radarr/Prowlarr OpenAPI specs describe them. */
class ArrMaintenanceTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config(type: ServiceType) = ServiceConfig(
        id = type.name, type = type, label = type.name.lowercase(),
        baseUrl = server.url("/").toString(), apiKey = "k",
    )
    private fun line() = server.takeRequest().let { "${it.method} ${it.path}" }

    @Test fun `logs are paged newest first on each app's api base`() = runBlocking {
        val page = """{"page":1,"records":[{"time":"2026-10-17T12:00:00Z","level":"Warn","logger":"RssSync","message":"slow indexer","exception":""}]}"""
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody(page))
        val l = arrLogs(config(ServiceType.SONARR), "warn").single()
        arrLogs(config(ServiceType.PROWLARR), "error")
        assertEquals("GET /api/v3/log?page=1&pageSize=100&sortKey=time&sortDirection=descending&level=warn", line())
        assertEquals("GET /api/v1/log?page=1&pageSize=100&sortKey=time&sortDirection=descending&level=error", line())
        assertEquals(ArrLogLine("warn", "2026-10-17 12:00:00", "RssSync", "slow indexer", ""), l)
    }

    @Test fun `backups are listed newest first, made by command and deleted by id`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"name":"a.zip","type":"scheduled","size":2097152,"time":"2026-10-01T03:00:00Z"},
                    {"id":2,"name":"b.zip","type":"manual","size":512,"time":"2026-10-17T03:00:00Z"}]""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200))
        val b = arrBackups(config(ServiceType.RADARR))
        assertEquals(listOf(2, 1), b.map { it.id })
        assertEquals(2097152L, b[1].sizeBytes)
        assertEquals("backup started", arrCreateBackup(config(ServiceType.RADARR)))
        assertEquals("backup deleted", arrDeleteBackup(config(ServiceType.RADARR), 1))
        assertEquals("GET /api/v3/system/backup", line())
        val cmd = server.takeRequest()
        assertEquals("POST /api/v3/command", "${cmd.method} ${cmd.path}")
        assertEquals("""{"name":"Backup"}""", cmd.body.readUtf8())
        assertEquals("DELETE /api/v3/system/backup/1", line())
    }

    @Test fun `sonarr exclusions use importlistexclusion and the tvdb id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":4,"tvdbId":81189,"title":"Breaking Bad"}]"""))
        server.enqueue(MockResponse().setResponseCode(200))
        val x = arrExclusions(config(ServiceType.SONARR)).single()
        assertEquals(ArrExclusion(4, "Breaking Bad", null, 81189), x)
        arrRemoveExclusion(config(ServiceType.SONARR), 4)
        assertEquals("GET /api/v3/importlistexclusion", line())
        assertEquals("DELETE /api/v3/importlistexclusion/4", line())
    }

    @Test fun `radarr exclusions use exclusions with movie title and year`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":7,"tmdbId":603,"movieTitle":"The Matrix","movieYear":1999}]"""))
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(ArrExclusion(7, "The Matrix", 1999, 603), arrExclusions(config(ServiceType.RADARR)).single())
        assertEquals("exclusion removed", arrRemoveExclusion(config(ServiceType.RADARR), 7))
        assertEquals("GET /api/v3/exclusions", line())
        assertEquals("DELETE /api/v3/exclusions/7", line())
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        arrCreateBackup(config(ServiceType.SONARR))
        arrDeleteBackup(config(ServiceType.SONARR), 1)
        arrRemoveExclusion(config(ServiceType.RADARR), 1)
        assertEquals(0, server.requestCount)
    }
}
