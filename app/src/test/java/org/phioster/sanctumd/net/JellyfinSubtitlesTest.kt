package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
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
 * Jellyfin can fetch subtitles itself, per title — which covers the need Bazarr would have
 * covered, without another service on the phone-sized server.
 *
 * Ordering matters more than it looks: a hash match is a subtitle made for this exact file, so
 * it is the one that will actually be in sync. Presenting the list in whatever order the
 * provider returned would bury it.
 */
class JellyfinSubtitlesTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-sub")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-sub", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false,
    )
    private fun respond(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `candidates carry what is needed to choose between them`() = runBlocking {
        respond(
            """[{"Id":"abc","ProviderName":"OpenSubtitles","Name":"Coraline.German.srt","Format":"srt",
                 "DownloadCount":4210,"IsHashMatch":true,"IsForced":false,
                 "ThreeLetterISOLanguageName":"ger"}]""",
        )

        val subs = jellyfinSubtitleCandidates(config(), "i1", "ger")

        assertEquals(1, subs.size)
        assertEquals("abc", subs[0].id)
        assertEquals("OpenSubtitles", subs[0].provider)
        assertEquals("srt", subs[0].format)
        assertEquals(4210, subs[0].downloads)
        assertTrue(subs[0].hashMatch)
    }

    @Test
    fun `a hash match is offered first, then the most downloaded`() = runBlocking {
        respond(
            """[{"Id":"a","DownloadCount":10,"IsHashMatch":false},
                {"Id":"b","DownloadCount":5,"IsHashMatch":false},
                {"Id":"c","DownloadCount":1,"IsHashMatch":true}]""",
        )

        val subs = jellyfinSubtitleCandidates(config(), "i1", "ger")

        assertEquals(listOf("c", "a", "b"), subs.map { it.id })
    }

    @Test
    fun `the search asks for the requested language`() = runBlocking {
        respond("[]")

        jellyfinSubtitleCandidates(config(), "i1", "eng")

        assertTrue(server.takeRequest().path!!.endsWith("/Items/i1/RemoteSearch/Subtitles/eng"))
    }

    @Test
    fun `downloading posts the chosen subtitle id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = jellyfinDownloadSubtitle(config(), "i1", "abc")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue("got ${req.path}", req.path!!.endsWith("/Items/i1/RemoteSearch/Subtitles/abc"))
        assertEquals("downloaded", result)
    }
}
