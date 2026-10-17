package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** Season episodes and person pages, as seerr-api.yml describes them. */
class SeerrPeopleTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config() = ServiceConfig(
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )

    @Test fun `a season lists its episodes in order with dates`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":1,"seasonNumber":2,"episodes":[
                    {"episodeNumber":2,"name":"Two","airDate":"2026-11-01","overview":""},
                    {"episodeNumber":1,"name":"One","airDate":"2026-10-25T00:00:00.000Z","overview":"x"}]}""",
            ),
        )
        val eps = seerrSeasonEpisodes(config(), 1399, 2)
        assertEquals("/api/v1/tv/1399/season/2", server.takeRequest().path)
        assertEquals(listOf(1, 2), eps.map { it.number })
        assertEquals("2026-10-25", eps[0].airDate)
    }

    @Test fun `a person's credits are read like discover, once each, newest first`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":17419,"name":"Bryan Cranston","knownForDepartment":"Acting","birthday":"1956-03-07","profilePath":"/p.jpg","biography":"Actor."}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"id":17419,"cast":[
                    {"id":1396,"mediaType":"tv","name":"Breaking Bad","firstAirDate":"2008-01-20","character":"Walter"},
                    {"id":1396,"mediaType":"tv","name":"Breaking Bad","firstAirDate":"2008-01-20","character":"Heisenberg"},
                    {"id":1,"mediaType":"movie","title":"Newer","releaseDate":"2020-05-05"}],"crew":[]}""",
            ),
        )
        val p = seerrPerson(config(), 17419)
        assertEquals("/api/v1/person/17419", server.takeRequest().path)
        assertEquals("/api/v1/person/17419/combined_credits", server.takeRequest().path)
        assertEquals("Bryan Cranston", p.name)
        assertEquals("https://image.tmdb.org/t/p/w300/p.jpg", p.photoUrl)
        assertEquals(listOf("Newer", "Breaking Bad"), p.credits.map { it.title })
    }

    @Test fun `cast members carry their tmdb person id`() {
        val detail = Json.parseToJsonElement("""{"credits":{"cast":[{"id":17419,"name":"Bryan Cranston","character":"Walter"}]}}""") as JsonObject
        assertEquals(17419, parseCast(detail).single().personId)
    }
}
