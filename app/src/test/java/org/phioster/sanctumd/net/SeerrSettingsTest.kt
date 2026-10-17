package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** General settings, job schedules and caches; field names read off a live Seerr 3.3. */
class SeerrSettingsTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "seerr", type = ServiceType.SEERR, label = "Seerr",
        baseUrl = server.url("/").toString(), apiKey = "key",
    )

    // Trimmed copy of the live answer.
    private val live = """{"apiKey":"secret","applicationTitle":"Seerr","applicationUrl":"","cacheImages":false,
        "defaultPermissions":1080033312,"defaultQuotas":{"movie":{"quotaLimit":0,"quotaDays":7}},"hideAvailable":false,
        "hideBlocklisted":false,"localLogin":true,"discoverRegion":"","streamingRegion":"","originalLanguage":"",
        "blocklistRegion":"","blocklistLanguage":"","blocklistedTags":"","blocklistedTagsLimit":50,"mediaServerType":2,
        "partialRequestsEnabled":true,"enableSpecialEpisodes":false,"locale":"en","youtubeUrl":""}"""

    @Test fun `saving overlays the edits on the server's own object`() = runBlocking {
        server.enqueue(MockResponse().setBody(live))
        server.enqueue(MockResponse().setBody(live))
        server.enqueue(MockResponse().setBody("{}"))
        val s = seerrMainSettings(config())
        assertEquals(true, s.partialRequestsEnabled)
        assertEquals("settings saved", seerrSaveMainSettings(config(), s.copy(discoverRegion = "DE", originalLanguage = "de|en", locale = "de")))
        server.takeRequest(); server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST /api/v1/settings/main", "${post.method} ${post.path}")
        val body = Json.parseToJsonElement(post.body.readUtf8()) as JsonObject
        assertEquals("DE", body["discoverRegion"]!!.jsonPrimitive.content)
        assertEquals("de|en", body["originalLanguage"]!!.jsonPrimitive.content)
        assertEquals("de", body["locale"]!!.jsonPrimitive.content)
        // Untouched settings go back as they were.
        assertEquals("1080033312", body["defaultPermissions"]!!.jsonPrimitive.content)
        assertTrue(body["defaultQuotas"] is JsonObject)
    }

    @Test fun `regions and languages come from Seerr's own lists`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"iso_3166_1":"US","english_name":"United States"},{"iso_3166_1":"DE","english_name":"Germany"}]"""))
        server.enqueue(MockResponse().setBody("""[{"iso_639_1":"de","english_name":"German","name":"Deutsch"}]"""))
        assertEquals(listOf("Germany", "United States"), seerrRegions(config()).map { it.name })
        assertEquals("de", seerrLanguages(config()).single().code)
        assertEquals("/api/v1/regions", server.takeRequest().path)
        assertEquals("/api/v1/languages", server.takeRequest().path)
    }

    @Test fun `job schedules are six-field cron lines both ways`() {
        assertEquals("0 */5 * * * *", jobCron("minutes", 5))
        assertEquals("0 0 */6 * * *", jobCron("hours", 6))
        assertEquals(null, jobCron("fixed", 1))
        assertEquals(5, jobEvery("0 */5 * * * *"))
        assertEquals(null, jobEvery("0 0 3 * * *"))
    }

    @Test fun `a schedule change and a cancel go to the job's own routes`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
        seerrScheduleJob(config(), "jellyfin-recently-added-scan", "0 */10 * * * *")
        seerrCancelJob(config(), "jellyfin-full-scan")
        val s = server.takeRequest()
        assertEquals("POST /api/v1/settings/jobs/jellyfin-recently-added-scan/schedule", "${s.method} ${s.path}")
        assertEquals("""{"schedule":"0 */10 * * * *"}""", s.body.readUtf8())
        assertEquals("/api/v1/settings/jobs/jellyfin-full-scan/cancel", server.takeRequest().path)
    }

    @Test fun `caches show their stats and flush by id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"apiCaches":[{"id":"tmdb","name":"The Movie Database API","stats":{"hits":3719,"misses":643,"keys":81,"ksize":13689,"vsize":117600}}],"dnsCache":{}}"""))
        server.enqueue(MockResponse().setBody("{}"))
        val c = seerrCaches(config()).single()
        assertEquals(SeerrCache("tmdb", "The Movie Database API", 3719, 643, 81, 131289), c)
        assertEquals("flushed", seerrFlushCache(config(), "tmdb"))
        server.takeRequest()
        assertEquals("/api/v1/settings/cache/tmdb/flush", server.takeRequest().path)
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        seerrSaveMainSettings(config(), parseMainSettings(Json.parseToJsonElement(live) as JsonObject))
        seerrScheduleJob(config(), "x", "0 */5 * * * *")
        seerrCancelJob(config(), "x")
        seerrFlushCache(config(), "tmdb")
        assertEquals(0, server.requestCount)
    }
}
