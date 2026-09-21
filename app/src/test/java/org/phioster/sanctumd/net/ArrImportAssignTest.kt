package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Sonarr cannot import a file from a series alone — it needs the concrete episode ids. The
 * Radarr-shaped assignment writes a `movie` object, which Sonarr's branch of the import command
 * never reads, so assigning on Sonarr used to produce a command with neither seriesId nor
 * episodeIds: a silent no-op the user could still trigger.
 */
class ArrImportAssignTest {

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
        id = "test",
        type = type,
        label = type.label,
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
    )

    @Test fun `assigning episodes injects the series and its episodes and clears rejections`() {
        val patched = arrImportAssignEpisodes(
            """{"path":"/dl/black.torch.s01e07.mkv","rejections":[{"reason":"Unknown Series"}]}""",
            seriesId = 12, seriesTitle = "BLACK TORCH", episodeIds = listOf(340, 341),
        )
        assertTrue(patched.contains("\"series\":{\"id\":12"))
        assertTrue(patched.contains("\"title\":\"BLACK TORCH\""))
        assertTrue(patched.contains("\"episodes\":[{\"id\":340},{\"id\":341}]"))
        assertTrue(patched.contains("\"rejections\":[]"))
        assertTrue(patched.contains("\"path\":\"/dl/black.torch.s01e07.mkv\"")) // untouched field survives
    }

    @Test
    fun `an episode assignment reaches Sonarr as seriesId and episodeIds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        val patched = arrImportAssignEpisodes(
            """{"path":"/dl/x.mkv","rejections":[{"reason":"Unknown Series"}]}""",
            seriesId = 12, seriesTitle = "BLACK TORCH", episodeIds = listOf(340),
        )

        arrManualImportExecute(config(ServiceType.SONARR), listOf(patched))

        val body = server.takeRequest().body.readUtf8()
        assertTrue("got $body", body.contains("\"seriesId\":12"))
        assertTrue("got $body", body.contains("\"episodeIds\":[340]"))
    }

    /** The Radarr path must keep behaving exactly as before — it already works in the field. */
    @Test
    fun `a movie assignment still reaches Radarr as movieId`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        val patched = arrImportAssignMovie("""{"path":"/dl/y.mkv"}""", movieId = 77, title = "The Odyssey")

        arrManualImportExecute(config(ServiceType.RADARR), listOf(patched))

        val body = server.takeRequest().body.readUtf8()
        assertTrue("got $body", body.contains("\"movieId\":77"))
        assertFalse("got $body", body.contains("seriesId"))
    }
}
