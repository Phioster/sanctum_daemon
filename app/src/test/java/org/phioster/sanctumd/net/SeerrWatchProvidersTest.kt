package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.model.WatchProviderKind
import java.util.Locale

/**
 * "Where does this stream?", the way werstreamt.es answers it — read out of the `watchProviders`
 * block Jellyseerr/Overseerr already return with a movie/tv detail, so the app needs no TMDB key
 * of its own.
 *
 * The payload below is the shape Seerr maps TMDB into: one entry per country, each with
 * `flatrate` / `rent` / `buy` lists. Availability is per country, so reading the wrong entry
 * would be worse than reading none — the "nowhere here" case is a result, not a failure.
 */
class SeerrWatchProvidersTest {

    private lateinit var server: MockWebServer
    private lateinit var previousLocale: Locale

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        previousLocale = Locale.getDefault()
    }
    @After fun stop() {
        Locale.setDefault(previousLocale)
        server.shutdown()
    }

    private fun config() = ServiceConfig(
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    private val detail = """{"id":603,"title":"The Matrix","overview":"","releaseDate":"1999-03-30",
        "credits":{"cast":[{"name":"Keanu Reeves","character":"Neo","profilePath":"/keanu.jpg"}]},
        "watchProviders":[
          {"iso_3166_1":"DE","link":"https://www.themoviedb.org/movie/603/watch?locale=DE",
           "flatrate":[{"displayPriority":3,"logoPath":"/sky.jpg","id":30,"name":"WOW"},
                       {"displayPriority":1,"logoPath":"/netflix.jpg","id":8,"name":"Netflix"}],
           "rent":[{"displayPriority":2,"logoPath":"/amazon.jpg","id":10,"name":"Amazon Video"},
                   {"displayPriority":5,"logoPath":"/netflix.jpg","id":8,"name":"Netflix"}],
           "buy":[{"displayPriority":4,"logoPath":"/apple.jpg","id":2,"name":"Apple TV"}]},
          {"iso_3166_1":"US","link":"https://www.themoviedb.org/movie/603/watch?locale=US",
           "flatrate":[{"displayPriority":1,"logoPath":"/max.jpg","id":1899,"name":"Max"}]}
        ]}"""

    private fun parse(region: String) =
        parseWatchProviders(json.parseToJsonElement(detail) as JsonObject, region)

    @Test
    fun `the chosen region decides which services are shown`() {
        val de = parse("DE")
        assertEquals("DE", de.region)
        // TMDB's own display priority, per offer list; subscriptions before paid offers.
        assertEquals(listOf("Netflix", "WOW", "Amazon Video", "Apple TV"), de.providers.map { it.name })
        assertEquals("https://image.tmdb.org/t/p/w92/netflix.jpg", de.providers.first().logoUrl)
        assertTrue(de.link.endsWith("locale=DE"))

        assertEquals(listOf("Max"), parse("US").providers.map { it.name })
    }

    @Test
    fun `a service that also rents is listed once, under the subscription`() {
        val netflix = parse("DE").providers.filter { it.id == 8 }
        assertEquals(1, netflix.size)
        assertEquals(WatchProviderKind.STREAM, netflix.single().kind)
        assertEquals(WatchProviderKind.RENT, parse("DE").providers.single { it.id == 10 }.kind)
        assertEquals(WatchProviderKind.BUY, parse("DE").providers.single { it.id == 2 }.kind)
    }

    /** Nothing in this country is an answer of its own — and never an excuse to show another's. */
    @Test
    fun `a region TMDB has no entry for streams nowhere`() {
        val at = parse("AT")
        assertEquals("AT", at.region)
        assertTrue(at.isEmpty)
        assertEquals("", at.link)
    }

    @Test
    fun `no preference follows the device region`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("DE", watchRegionOf(""))
        assertEquals(listOf("Netflix", "WOW", "Amazon Video", "Apple TV"), parse("").providers.map { it.name })
        // A device without a country still has to ask TMDB for something.
        Locale.setDefault(Locale.forLanguageTag("de"))
        assertEquals("US", watchRegionOf(""))
    }

    @Test
    fun `a title with no watchProviders block is simply not available`() {
        val bare = json.parseToJsonElement("""{"id":1,"title":"Home video"}""") as JsonObject
        assertTrue(parseWatchProviders(bare, "DE").isEmpty)
    }

    @Test
    fun `cast and availability come from one request`() = runBlocking {
        respond(detail)

        val extras = seerrTitleExtras(config(), 603, isTv = false, region = "DE")

        assertEquals(listOf("Keanu Reeves"), extras.cast.map { it.name })
        assertEquals(listOf("Netflix", "WOW", "Amazon Video", "Apple TV"), extras.availability.providers.map { it.name })
        assertEquals(1, server.requestCount)
        val req = server.takeRequest()
        assertEquals("/api/v1/movie/603", req.path)
        assertEquals("key", req.getHeader("X-Api-Key"))
    }

    @Test
    fun `a series asks the tv endpoint`() = runBlocking {
        respond("""{"id":1399,"name":"Game of Thrones","watchProviders":[{"iso_3166_1":"DE","link":"x",
            "flatrate":[{"displayPriority":1,"logoPath":"/sky.jpg","id":30,"name":"WOW"}]}]}""")

        val av = seerrWatchProviders(config(), 1399, isTv = true, region = "DE")

        assertEquals(listOf("WOW"), av.providers.map { it.name })
        assertEquals("/api/v1/tv/1399", server.takeRequest().path)
    }

    /** No tmdbId means nothing to ask about — and nothing must go out over the network for it. */
    @Test
    fun `an unknown tmdb id never reaches the server`() = runBlocking {
        assertTrue(seerrWatchProviders(config(), 0, isTv = false, region = "DE").isEmpty)
        assertEquals(0, server.requestCount)
    }
}
