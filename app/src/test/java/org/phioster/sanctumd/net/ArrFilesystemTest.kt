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
 * The manual import used to require typing a server-side path by hand, which on a phone means
 * typing something like /opt/nzbget/dst/Some.Release.Name.German.1080p by thumb. Browsing needs
 * the folders and the files. A folder listing alone would not tell you whether you are in the
 * right place.
 */
class ArrFilesystemTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config(type: ServiceType = ServiceType.RADARR) = ServiceConfig(
        id = "test",
        type = type,
        label = type.label,
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
    )

    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `folders come before files and both carry their full path`() = runBlocking {
        respond(
            """{"parent":"/opt","directories":[{"name":"dst","path":"/opt/nzbget/dst"}],
                "files":[{"name":"movie.mkv","path":"/opt/nzbget/movie.mkv","size":8402}]}""",
        )

        val listing = arrBrowse(config(), "/opt/nzbget")

        assertEquals(listOf("dst", "movie.mkv"), listing.entries.map { it.name })
        assertTrue(listing.entries[0].isDirectory)
        assertEquals("/opt/nzbget/dst", listing.entries[0].path)
        assertEquals(8402L, listing.entries[1].size)
    }

    @Test
    fun `the parent is exposed so browsing up is possible`() = runBlocking {
        respond("""{"parent":"/opt","directories":[],"files":[]}""")

        assertEquals("/opt", arrBrowse(config(), "/opt/nzbget").parent)
    }

    /** At the top there is no parent, and the UI has to hide its "up" entry rather than crash. */
    @Test
    fun `the root has no parent`() = runBlocking {
        respond("""{"directories":[{"name":"opt","path":"/opt"}],"files":[]}""")

        assertNull(arrBrowse(config(), "").parent)
    }

    @Test
    fun `browsing asks for files as well and uses the service's api version`() = runBlocking {
        respond("""{"directories":[],"files":[]}""")

        arrBrowse(config(ServiceType.SONARR), "/opt")

        val path = server.takeRequest().path!!
        assertTrue("got $path", path.startsWith("/api/v3/filesystem?"))
        assertTrue("got $path", path.contains("includeFiles=true"))
    }
}
