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
 * The global search could not be filtered because its result type carried no adult flag, even
 * though both sources know it. Marking it is the whole change — and the policy stays as decided:
 * pornography only. FSK 18, R and NC-17 are mainstream ratings and must keep showing up, or
 * an 18-rated horror film would silently vanish from search.
 */
class SearchAdultTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-search")
    }
    @After fun stop() = server.shutdown()

    private fun jellyfin() = ServiceConfig(
        id = "jf-search", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false,
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    private fun searchWithRating(rating: String) = runBlocking {
        respond("""[{"Id":"u1","Name":"admin","Policy":{"IsAdministrator":true}}]""")
        respond("""{"Items":[{"Id":"i1","Name":"Something","Type":"Movie","OfficialRating":"$rating"}]}""")
        jellyfinSearchResults(jellyfin(), "something").first()
    }

    @Test fun `a pornographic rating marks the hit as adult`() {
        assertTrue(searchWithRating("XXX").adult)
    }

    @Test fun `an 18 rating stays visible because it is mainstream, not porn`() {
        assertFalse(searchWithRating("FSK 18").adult)
        clearJellyfinSession("jf-search")
        assertFalse(searchWithRating("R").adult)
    }

    @Test fun `an item without a rating is not adult`() {
        assertFalse(searchWithRating("").adult)
    }
}
