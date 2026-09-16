package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** Pins how [jellyfinPlaybackSource] turns a PlaybackInfo response into a playable URL, incl. the
 *  direct-play vs. transcode decision and the resume position. */
class PlaybackApiTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-play") // resolveUserId caches per config id
    }
    @After fun stop() { server.shutdown() }

    private fun config() = ServiceConfig(
        id = "jf-play",
        type = ServiceType.JELLYFIN,
        label = "Jellyfin",
        baseUrl = server.url("/").toString(),
        apiKey = "tk",
        useLogin = false,
    )

    private fun enqueueUserAndItem() {
        // 1) resolveUserId -> GET Users
        server.enqueue(MockResponse().setBody("""[{"Id":"u1","Name":"admin","Policy":{"IsAdministrator":true}}]"""))
        // 2) playItem -> resume position 600000ms (6e9 ticks) + 2h runtime
        server.enqueue(MockResponse().setBody("""{"RunTimeTicks":72000000000,"UserData":{"PlaybackPositionTicks":6000000000}}"""))
    }

    @Test
    fun `direct play builds a static stream url with the resume position`() = runBlocking {
        enqueueUserAndItem()
        server.enqueue(MockResponse().setBody(
            """{"PlaySessionId":"ps1","MediaSources":[{"Id":"ms1","Container":"mkv","SupportsDirectPlay":true,"RunTimeTicks":72000000000}]}"""
        ))
        val src = jellyfinPlaybackSource(config(), "item42")
        assertFalse(src.isHls)
        assertTrue("got ${src.url}", src.url.endsWith("Videos/item42/stream?static=true&mediaSourceId=ms1&playSessionId=ps1"))
        assertEquals("ms1", src.mediaSourceId)
        assertEquals(600_000L, src.startPositionMs)
        assertEquals(7_200_000L, src.runTimeMs)
        assertTrue("got ${src.authHeaders}", src.authHeaders["Authorization"]!!.contains("Token=\"tk\""))
        // Pins route and argument order: both parameters are Strings, so a swapped call would
        // compile silently and ask the server for the item id as a user.
        server.takeRequest() // resolveUserId
        val itemReq = server.takeRequest().path!!
        assertTrue("got $itemReq", itemReq.startsWith("/Items/item42?"))
        assertTrue("got $itemReq", itemReq.contains("userId=u1"))
    }

    @Test
    fun `no direct play falls back to the transcoding HLS url`() = runBlocking {
        enqueueUserAndItem()
        server.enqueue(MockResponse().setBody(
            """{"PlaySessionId":"ps2","MediaSources":[{"Id":"ms2","SupportsDirectPlay":false,"SupportsTranscoding":true,"TranscodingUrl":"/videos/item42/master.m3u8?api_key=tk&PlaySessionId=ps2"}]}"""
        ))
        val src = jellyfinPlaybackSource(config(), "item42")
        assertTrue(src.isHls)
        assertTrue("got ${src.url}", src.url.contains("/videos/item42/master.m3u8"))
    }
}
