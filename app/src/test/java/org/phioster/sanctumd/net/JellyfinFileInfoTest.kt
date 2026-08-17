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
 * The detail view shows what the file actually is — container, size and every track. Jellyfin
 * ships that with the item itself, so it has to be read off the existing detail response rather
 * than costing a second round trip.
 */
class JellyfinFileInfoTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-file")
    }
    @After fun stop() = server.shutdown()

    private fun config() = ServiceConfig(
        id = "jf-file",
        type = ServiceType.JELLYFIN,
        label = "Jellyfin",
        baseUrl = server.url("/").toString(),
        apiKey = "tk",
        useLogin = false,
    )

    private fun enqueueUser() {
        server.enqueue(MockResponse().setBody("""[{"Id":"u1","Name":"admin","Policy":{"IsAdministrator":true}}]"""))
    }

    @Test
    fun `container size and tracks come off the item detail response`() = runBlocking {
        enqueueUser()
        server.enqueue(
            MockResponse().setBody(
                """{"Id":"i1","Name":"Steamboy","Type":"Movie","MediaSources":[{
                   "Container":"mkv","Size":9052398293,"Path":"/media/Movies/Steamboy.mkv","Bitrate":9204000,
                   "MediaStreams":[
                     {"Type":"Video","Codec":"h264","Profile":"High","Width":1920,"Height":1080,
                      "AverageFrameRate":23.976,"BitDepth":8,"BitRate":8500000,"VideoRange":"SDR","IsDefault":true},
                     {"Type":"Audio","Codec":"eac3","DisplayLanguage":"Deutsch","Channels":6,
                      "ChannelLayout":"5.1","SampleRate":48000,"BitRate":640000,"IsDefault":true},
                     {"Type":"Subtitle","Codec":"subrip","DisplayLanguage":"Deutsch","IsForced":true,"IsExternal":false}
                   ]}]}""",
            ),
        )

        val info = jellyfinItemDetail(config(), "i1").fileInfo!!

        assertEquals("mkv", info.container)
        assertEquals(9_052_398_293L, info.sizeBytes)
        assertEquals("/media/Movies/Steamboy.mkv", info.path)
        assertEquals(9_204_000, info.bitrate)
        assertEquals(listOf("Video", "Audio", "Subtitle"), info.streams.map { it.type })

        val video = info.streams[0]
        assertEquals("h264", video.codec)
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals(8, video.bitDepth)

        val audio = info.streams[1]
        assertEquals("Deutsch", audio.language)
        assertEquals(6, audio.channels)
        assertEquals("5.1", audio.channelLayout)
        assertEquals(48_000, audio.sampleRate)

        assertTrue(info.streams[2].isForced)
    }

    @Test
    fun `an item without media sources has no file info at all`() = runBlocking {
        enqueueUser()
        server.enqueue(MockResponse().setBody("""{"Id":"s1","Name":"Some Series","Type":"Series"}"""))

        assertNull(jellyfinItemDetail(config(), "s1").fileInfo)
    }
}
