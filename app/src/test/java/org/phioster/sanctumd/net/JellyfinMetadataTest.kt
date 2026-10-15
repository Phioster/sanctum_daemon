package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import org.phioster.sanctumd.ui.jellyfin.splitList

/**
 * POST /Items/{id} replaces the whole item. Whatever the editor doesn't show (people, provider
 * ids, studios) has to go back exactly as it came, or saving a title wipes the cast.
 */
class JellyfinMetadataTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-meta")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-meta", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )

    private val item = """{"Id":"i1","Name":"Lion Fist","OriginalTitle":"","ForcedSortName":"",
        "ProductionYear":2019,"Overview":"old","Genres":["Action"],"Tags":[],"OfficialRating":"FSK-16",
        "LockData":false,"People":[{"Name":"Someone","Type":"Actor"}],"ProviderIds":{"Tmdb":"123"},
        "Studios":[{"Name":"X"}]}"""

    @Test fun `the editor reads what the server has`() = runBlocking {
        server.enqueue(MockResponse().setBody(item))
        val m = jellyfinMetadata(config(), "i1")
        assertEquals("/Items/i1?userId=u1", server.takeRequest().path)
        assertEquals("Lion Fist", m.name)
        assertEquals(2019, m.year)
        assertEquals(listOf("Action"), m.genres)
        assertEquals("FSK-16", m.officialRating)
        assertEquals(false, m.locked)
    }

    @Test fun `saving writes the edit over the full item`() = runBlocking {
        server.enqueue(MockResponse().setBody(item))
        server.enqueue(MockResponse().setResponseCode(204))
        val edit = JellyMetadata(
            name = "Lion Fist - Kampf der Champions", originalTitle = "Lion Fist", sortTitle = "",
            year = 2020, overview = "new", genres = listOf("Action", "Martial Arts"), tags = listOf("archiv"),
            officialRating = "FSK-18", locked = true,
        )
        assertEquals("saved", jellyfinSaveMetadata(config(), "i1", edit))
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST /Items/i1", "${post.method} ${post.path}")
        val body = Json.parseToJsonElement(post.body.readUtf8()) as JsonObject
        assertEquals("\"Lion Fist - Kampf der Champions\"", body["Name"].toString())
        assertEquals("2020", body["ProductionYear"].toString())
        assertEquals("[\"Action\",\"Martial Arts\"]", body["Genres"].toString())
        assertEquals("true", body["LockData"].toString())
        // Not part of the editor, so untouched.
        assertEquals("""[{"Name":"Someone","Type":"Actor"}]""", body["People"].toString())
        assertEquals("""{"Tmdb":"123"}""", body["ProviderIds"].toString())
        assertTrue(body.containsKey("Studios"))
    }

    @Test fun `a cleared year is sent as null, not dropped`() {
        val raw = Json.parseToJsonElement(item) as JsonObject
        val out = withMetadata(raw, metadataOf(raw).copy(year = null))
        assertEquals("null", out["ProductionYear"].toString())
    }

    @Test fun `comma lists are trimmed and deduplicated`() {
        assertEquals(listOf("Action", "Drama"), splitList(" Action, Drama ,, Action "))
    }

    @Test fun `remote images come with the thumbnail and fall back to the full url`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"Images":[{"ProviderName":"TheMovieDb","Url":"http://a/full.jpg","ThumbnailUrl":"http://a/thumb.jpg",
                    "Width":1000,"Height":1500,"Language":"de"},{"ProviderName":"Fanart","Url":"http://b/x.jpg"}],
                    "TotalRecordCount":2}""",
            ),
        )
        val imgs = jellyfinRemoteImages(config(), "i1", "Primary")
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/Items/i1/RemoteImages?type=Primary"))
        assertEquals("http://a/thumb.jpg", imgs[0].thumbnailUrl)
        assertEquals("http://b/x.jpg", imgs[1].thumbnailUrl)
    }

    @Test fun `picking an image hands the url to the server`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals("image set", jellyfinPickRemoteImage(config(), "i1", "Backdrop", "http://a/full.jpg"))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/Items/i1/RemoteImages/Download?type=Backdrop&imageUrl=http%3A%2F%2Fa%2Ffull.jpg", req.path)
    }

    @Test fun `an upload is base64 text with the image's type`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        jellyfinUploadImage(config(), "i1", "Primary", byteArrayOf(1, 2, 3), "image/png")
        val req = server.takeRequest()
        assertEquals("/Items/i1/Images/Primary", req.path)
        assertEquals("AQID", req.body.readUtf8())
        assertTrue(req.getHeader("Content-Type")!!.startsWith("image/png"))
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinSaveMetadata(config(), "i1", metadataOf(Json.parseToJsonElement(item) as JsonObject))
        jellyfinPickRemoteImage(config(), "i1", "Primary", "http://a")
        jellyfinUploadImage(config(), "i1", "Primary", byteArrayOf(1), "image/png")
        assertEquals(0, server.requestCount)
    }
}
