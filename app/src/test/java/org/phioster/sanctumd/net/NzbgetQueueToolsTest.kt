package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.nzbget.formatEta
import org.phioster.sanctumd.ui.nzbget.queueDetail

/** Package F: richer queue rows, timed pause, post-processing, multi-select (names from XmlRpc.cpp). */
class NzbgetQueueToolsTest {

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
        id = "nzb", type = ServiceType.NZBGET, label = "NZBGet",
        baseUrl = server.url("/").toString(), username = "u", password = "p",
    )

    /** The queue downloads in order: the second item waits for the first. */
    @Test fun `etas add up along the queue and skip paused items`() {
        val mb = 1024L * 1024
        val etas = queueEtas(listOf(10, 20, 30), listOf(false, true, false), rateBytesPerSec = mb)
        assertEquals(listOf(10L, null, 40L), etas)
    }

    @Test fun `nothing moving means no eta at all`() {
        assertEquals(listOf<Long?>(null, null), queueEtas(listOf(1, 2), listOf(false, false), 0))
    }

    @Test fun `the queue reads category, priority, health, age and post-processing`() = runBlocking {
        val posted = System.currentTimeMillis() / 1000 - 3 * 86_400
        server.enqueue(
            MockResponse().setBody(
                """{"result":[{"NZBID":7,"NZBName":"Show.S01E01","Status":"PP_QUEUED","FileSizeMB":900,"RemainingSizeMB":0,
                    "Category":"tv","MaxPriority":50,"Health":975,"MinPostTime":$posted,
                    "PostInfoText":"Unpacking Show.S01E01","PostStageProgress":450}]}""",
            ),
        )
        val q = nzbgetQueue(config()).single()
        assertEquals("tv", q.category)
        assertEquals(50, q.priority)
        assertEquals(975, q.health)
        assertEquals(3, q.ageDays)
        assertEquals("Unpacking Show.S01E01", q.postInfo)
        assertEquals(450, q.postProgress)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a row's detail line leaves out what is unknown`() {
        val full = NzbQueueItem(1, "x", "DOWNLOADING", 100, 50, category = "tv", priority = 50, health = 975, ageDays = 3, etaSec = 3900)
        assertEquals("ETA 1h 05m · tv · high · 3d old · health 97.5%", queueDetail(full))
        assertEquals("", queueDetail(NzbQueueItem(1, "x", "QUEUED", 100, 100)))
        assertEquals("40s", formatEta(40))
    }

    @Test fun `a timed pause pauses, then has NZBGet schedule the resume`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        assertEquals("paused for 30 min", runNzbPauseFor(config(), 30))
        assertTrue(server.takeRequest().body.readUtf8().contains(""""method":"pausedownload""""))
        val resume = server.takeRequest().body.readUtf8()
        assertTrue(resume, resume.contains(""""method":"scheduleresume""""))
        assertTrue(resume, resume.contains(""""params":[1800]"""))
    }

    @Test fun `post-processing is paused and resumed by its own methods`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        runNzbPostPause(config(), pause = true)
        runNzbPostPause(config(), pause = false)
        assertTrue(server.takeRequest().body.readUtf8().contains(""""method":"pausepost""""))
        assertTrue(server.takeRequest().body.readUtf8().contains(""""method":"resumepost""""))
    }

    @Test fun `the header reads a timed pause and the current rate`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":{"DownloadPaused":true,"ResumeTime":1760700000,"PostPaused":true,"DownloadRate":5000}}"""))
        val s = nzbgetPauseState(config())
        assertEquals(1760700000L, s.resumeAt)
        assertEquals(true, s.postPaused)
        assertEquals(0L, s.rateBytesPerSec)
    }

    @Test fun `several items go out in one editqueue call`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        assertEquals("done (3)", runNzbEditMany(config(), "GroupPause", listOf(1, 2, 3)))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains(""""params":["GroupPause",0,"",[1,2,3]]"""))
    }

    @Test fun `safe mode keeps the new queue actions off the network`() = runBlocking {
        SafeMode.enabled = true
        runNzbPauseFor(config(), 30)
        runNzbPostPause(config(), true)
        runNzbEditMany(config(), "GroupDelete", listOf(1))
        assertEquals(0, server.requestCount)
    }
}
