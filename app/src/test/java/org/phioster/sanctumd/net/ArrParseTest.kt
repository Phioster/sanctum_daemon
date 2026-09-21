package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Prowlarr returns what the indexer said, title, size, age. Whether a release is *worth taking*
 * is a Radarr judgement: it parses the name against the profile's custom formats and scores it.
 *
 * The difference is not cosmetic. Measured on the live instance, this release scores −23300
 * because "MD" marks it as mic-dubbed. Something the file name does not advertise and the size
 * does not reveal:
 *
 *     Exit.8.2025.German.5.1.MD.DL.1080p.Bluray.x264-LiNEUP → Bluray-1080p, −23300
 */
class ArrParseTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() = server.shutdown()

    private fun config(type: ServiceType = ServiceType.RADARR) = ServiceConfig(
        id = "test", type = type, label = type.label,
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `a release title comes back scored and with its quality`() = runBlocking {
        respond(
            """{"title":"Exit.8.2025.German.5.1.MD.DL.1080p.Bluray.x264-LiNEUP",
                "customFormatScore":-23300,
                "customFormats":[{"name":"1080p"},{"name":"Line/Mic Dubbed"}],
                "parsedMovieInfo":{"quality":{"quality":{"name":"Bluray-1080p"}},
                                   "languages":[{"name":"German"},{"name":"Original"}]},
                "movie":{"title":"Exit 8","year":2025}}""",
        )

        val v = arrParseRelease(config(), "Exit.8.2025.German.5.1.MD.DL.1080p.Bluray.x264-LiNEUP")!!

        assertEquals("Bluray-1080p", v.quality)
        assertEquals(-23300, v.score)
        assertTrue(v.formats.contains("Line/Mic Dubbed"))
        assertEquals("German, Original", v.languages)
    }

    @Test
    fun `the title travels as a query parameter`() = runBlocking {
        respond("""{"title":"x"}""")

        arrParseRelease(config(), "Some.Release.2020.1080p")

        val path = server.takeRequest().path!!
        assertTrue("got $path", path.startsWith("/api/v3/parse?"))
        assertTrue("got $path", path.contains("Some.Release.2020.1080p"))
    }

    @Test
    fun `lidarr uses its v1 api`() = runBlocking {
        respond("""{"title":"x"}""")

        arrParseRelease(config(ServiceType.LIDARR), "Album.2020.FLAC")

        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/parse?"))
    }

    /** A service that cannot judge the name must yield nothing rather than a misleading zero. */
    @Test
    fun `an unparseable answer yields nothing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(arrParseRelease(config(), "whatever"))
    }
}
