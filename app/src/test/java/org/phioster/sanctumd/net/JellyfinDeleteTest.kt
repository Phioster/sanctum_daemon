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
 * Deleting in Jellyfin alone is a trap in an *arr setup: Radarr still knows the movie, sees the
 * file missing on its next scan and (while monitored) grabs it again. Pairing the two needs a
 * link that is exact rather than guessed, which is what the provider ids give us.
 */
class JellyfinDeleteTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-del")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun jellyfin() = ServiceConfig(
        id = "jf-del", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false,
    )
    private fun radarr() = ServiceConfig(
        id = "rad", type = ServiceType.RADARR, label = "Radarr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `an item carries the provider ids that link it to the arr side`() = runBlocking {
        respond("""[{"Id":"u1","Name":"admin","Policy":{"IsAdministrator":true}}]""")
        respond("""{"Id":"i1","Name":"Coraline","Type":"Movie","ProviderIds":{"Tmdb":"14836","Imdb":"tt0327597"}}""")

        val detail = jellyfinItemDetail(jellyfin(), "i1")

        assertEquals("14836", detail.providerIds["Tmdb"])
        assertEquals("tt0327597", detail.providerIds["Imdb"])
    }

    @Test
    fun `deleting an item removes it on the server`() = runBlocking {
        respond("""[{"Id":"u1","Name":"admin","Policy":{"IsAdministrator":true}}]""")
        server.enqueue(MockResponse().setResponseCode(204))

        val result = jellyfinDeleteItem(jellyfin(), "i1")

        server.takeRequest() // the user lookup
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/Items/i1"))
        assertEquals("deleted", result)
    }

    @Test
    fun `the arr counterpart is found by its tmdb id`() = runBlocking {
        respond(
            """[{"id":5,"title":"Coraline","year":2009,"tmdbId":14836},
                {"id":6,"title":"Something Else","year":2011,"tmdbId":999}]""",
        )

        val match = arrFindByProviderId(radarr(), tmdbId = "14836", tvdbId = null)

        assertEquals(5, match?.id)
        assertEquals("Coraline", match?.title)
    }

    /** No match must mean "offer nothing", never "offer the first thing that looked similar". */
    @Test
    fun `an unknown provider id matches nothing`() = runBlocking {
        respond("""[{"id":5,"title":"Coraline","year":2009,"tmdbId":14836}]""")

        assertNull(arrFindByProviderId(radarr(), tmdbId = "12345", tvdbId = null))
    }

    @Test
    fun `deleting on the arr side can add the import exclusion`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        arrDelete(radarr(), id = 5, deleteFiles = true, addImportExclusion = true)

        val path = server.takeRequest().path!!
        assertTrue("got $path", path.contains("deleteFiles=true"))
        assertTrue("got $path", path.contains("addImportExclusion=true"))
    }
}
