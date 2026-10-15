package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
 * Editing a title after it was added, and its files on disk. Field names are those of the
 * Sonarr and Radarr OpenAPI specs (EpisodeFileResource, MovieFileResource, MediaInfoResource).
 */
class ArrEditFilesTest {

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
        id = "t", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "key",
    )

    private val episodeFile = """{"id":11,"seriesId":7,"seasonNumber":1,"relativePath":"Season 01/Show - S01E01.mkv",
        "size":1288490188,"releaseGroup":"GRP","quality":{"quality":{"name":"WEBDL-1080p"}},
        "qualityCutoffNotMet":true,
        "mediaInfo":{"videoCodec":"x265","resolution":"1920x1080","videoDynamicRangeType":"HDR10",
                     "audioCodec":"EAC3","audioChannels":5.1,"audioLanguages":"German/Japanese","subtitles":"German"}}"""

    @Test fun `a file is summed up the way it fits on a phone`() {
        val f = arrFileInfo(Json.parseToJsonElement(episodeFile).jsonObject)
        assertEquals(11, f.id)
        assertEquals("Season 01/Show - S01E01.mkv", f.path)
        assertEquals(1288490188L, f.sizeBytes)
        assertEquals("WEBDL-1080p", f.quality)
        assertEquals("x265 · 1920x1080 · HDR10", f.video)
        assertEquals("EAC3 · 5.1 · German/Japanese", f.audio)
        assertEquals("German", f.subtitles)
        assertEquals(true, f.cutoffNotMet)
    }

    @Test fun `a file without media info still has its quality`() {
        val f = arrFileInfo(Json.parseToJsonElement("""{"id":3,"size":10,"quality":{"quality":{"name":"Bluray-720p"}}}""").jsonObject)
        assertEquals("Bluray-720p", f.quality)
        assertEquals("", f.video)
        assertEquals("", f.audio)
    }

    @Test fun `sonarr lists the series' files, radarr the movie's`() = runBlocking {
        server.enqueue(MockResponse().setBody("[$episodeFile]"))
        server.enqueue(MockResponse().setBody("[]"))
        val sonarr = arrFiles(config(ServiceType.SONARR), 7)
        arrFiles(config(ServiceType.RADARR), 5)
        assertEquals("/api/v3/episodefile?seriesId=7", server.takeRequest().path)
        assertEquals("/api/v3/moviefile?movieId=5", server.takeRequest().path)
        assertEquals(setOf(11), sonarr.keys)
    }

    @Test fun `deleting a file addresses the file, not the title`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals("file deleted", arrDeleteFile(config(ServiceType.SONARR), 11))
        arrDeleteFile(config(ServiceType.RADARR), 12)
        assertEquals("DELETE /api/v3/episodefile/11", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /api/v3/moviefile/12", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test fun `editing overlays only the chosen fields`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":7,"title":"Show","qualityProfileId":4,"seriesType":"standard","seasonFolder":true,
                    "path":"/media/tv/Show","tags":[2],"seasons":[{"seasonNumber":1,"monitored":true}]}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(202).setBody("{}"))
        val r = arrUpdateItem(config(ServiceType.SONARR), 7, qualityProfileId = 9, seriesType = "anime", seasonFolder = false)
        assertEquals("saved", r)
        server.takeRequest()
        val put = server.takeRequest()
        assertEquals("PUT /api/v3/series/7", "${put.method} ${put.path}")
        val body = Json.parseToJsonElement(put.body.readUtf8()) as JsonObject
        assertEquals("9", body["qualityProfileId"].toString())
        assertEquals("\"anime\"", body["seriesType"].toString())
        assertEquals("false", body["seasonFolder"].toString())
        // Untouched fields go back exactly as they came.
        assertEquals("\"/media/tv/Show\"", body["path"].toString())
        assertEquals("[2]", body["tags"].toString())
        assertTrue(body.containsKey("seasons"))
        assertTrue(!body.containsKey("minimumAvailability"))
    }

    @Test fun `a radarr edit sets the availability`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":5,"title":"Film","qualityProfileId":4,"minimumAvailability":"announced"}"""))
        server.enqueue(MockResponse().setResponseCode(202).setBody("{}"))
        arrUpdateItem(config(ServiceType.RADARR), 5, qualityProfileId = 4, minimumAvailability = "released")
        server.takeRequest()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("\"released\"", body["minimumAvailability"].toString())
        assertTrue(!body.containsKey("seriesType"))
    }

    @Test fun `safe mode keeps edit and delete off the network`() = runBlocking {
        SafeMode.enabled = true
        arrDeleteFile(config(ServiceType.SONARR), 11)
        arrUpdateItem(config(ServiceType.SONARR), 7, qualityProfileId = 9)
        assertEquals(0, server.requestCount)
    }
}
