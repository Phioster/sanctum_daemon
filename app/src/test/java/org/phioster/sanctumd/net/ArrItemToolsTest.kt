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
 * Refresh & scan, automatic season search and the per-item history. Command fields were
 * taken from the services' own command classes: the refresh commands take a list, and an
 * empty list refreshes the whole library.
 */
class ArrItemToolsTest {

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
        id = "test", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private fun refresh(type: ServiceType): Pair<String, String> = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertEquals("refresh started", arrRefreshItem(config(type), 42))
        val req = server.takeRequest()
        req.path!! to req.body.readUtf8()
    }

    @Test fun `sonarr refreshes exactly the one series`() {
        val (path, body) = refresh(ServiceType.SONARR)
        assertEquals("/api/v3/command", path)
        assertTrue(body, body.contains(""""name":"RefreshSeries""""))
        assertTrue(body, body.contains(""""seriesIds":[42]"""))
    }

    @Test fun `radarr refreshes exactly the one movie`() {
        val (_, body) = refresh(ServiceType.RADARR)
        assertTrue(body, body.contains(""""name":"RefreshMovie""""))
        assertTrue(body, body.contains(""""movieIds":[42]"""))
    }

    @Test fun `lidarr refreshes exactly the one artist on v1`() {
        val (path, body) = refresh(ServiceType.LIDARR)
        assertEquals("/api/v1/command", path)
        assertTrue(body, body.contains(""""name":"RefreshArtist""""))
        assertTrue(body, body.contains(""""artistIds":[42]"""))
    }

    @Test fun `a season search names series and season`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        assertEquals("search started", arrSeasonSearch(config(ServiceType.SONARR), seriesId = 7, seasonNumber = 2))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains(""""name":"SeasonSearch""""))
        assertTrue(body, body.contains(""""seriesId":7"""))
        assertTrue(body, body.contains(""""seasonNumber":2"""))
    }

    @Test fun `safe mode keeps both commands off the network`() = runBlocking {
        SafeMode.enabled = true
        arrRefreshItem(config(ServiceType.SONARR), 42)
        arrSeasonSearch(config(ServiceType.SONARR), 7, 2)
        assertEquals(0, server.requestCount)
    }

    @Test fun `sonarr history asks for the series and labels the episode`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"eventType":"grabbed","date":"2026-10-01T10:00:00Z","sourceTitle":"Old.S01E01",
                     "quality":{"quality":{"name":"WEBDL-1080p"}},"episode":{"seasonNumber":1,"episodeNumber":1}},
                    {"id":2,"eventType":"downloadFolderImported","date":"2026-10-02T10:00:00Z","sourceTitle":"New.S01E02",
                     "quality":{"quality":{"name":"WEBDL-1080p"}},"episode":{"seasonNumber":1,"episodeNumber":2}}]""",
            ),
        )
        val items = arrItemHistory(config(ServiceType.SONARR), 7)
        assertEquals("/api/v3/history/series?seriesId=7&includeEpisode=true", server.takeRequest().path)
        // Newest first, whatever order the server sent.
        assertEquals(listOf(2, 1), items.map { it.id })
        assertEquals("S01E02 · New.S01E02", items[0].title)
        assertEquals("2026-10-02 10:00", items[0].date)
        assertEquals("WEBDL-1080p", items[0].quality)
    }

    @Test fun `radarr and lidarr use their own history routes`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        arrItemHistory(config(ServiceType.RADARR), 5)
        arrItemHistory(config(ServiceType.LIDARR), 6)
        assertEquals("/api/v3/history/movie?movieId=5", server.takeRequest().path)
        assertEquals("/api/v1/history/artist?artistId=6", server.takeRequest().path)
    }
}
