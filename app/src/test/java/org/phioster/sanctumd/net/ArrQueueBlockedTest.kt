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
 * After a hand-assigned manual import the queue entry does not clear itself: it stays on
 * `importBlocked` and, because the download folder and the library live on different
 * filesystems here (no hardlinks), the source file is left lying around a second time.
 * Finding those entries is what lets the user tidy up without leaving the app.
 */
class ArrQueueBlockedTest {

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun config() = ServiceConfig(
        id = "test",
        type = ServiceType.RADARR,
        label = "Radarr",
        baseUrl = server.url("/").toString(),
        apiKey = "secret-key",
    )

    private fun queue(body: String) =
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))

    @Test
    fun `only blocked entries are offered for cleanup`() = runBlocking {
        queue(
            """{"records":[
                 {"id":1,"title":"Jackass.5.Einer.geht.noch","status":"completed","trackedDownloadState":"importBlocked"},
                 {"id":2,"title":"Something.Downloading","status":"downloading","trackedDownloadState":"downloading"},
                 {"id":3,"title":"Waiting.One","status":"queued","trackedDownloadState":"importPending"}
               ]}""",
        )

        val blocked = arrBlockedQueueItems(config())

        assertEquals(listOf(1), blocked.map { it.id })
        assertEquals("Jackass.5.Einer.geht.noch", blocked[0].title)
    }

    /** A healthy queue must produce an empty list, not a prompt to clean up nothing. */
    @Test
    fun `a queue with nothing blocked yields nothing`() = runBlocking {
        queue("""{"records":[{"id":2,"title":"Fine","status":"downloading","trackedDownloadState":"downloading"}]}""")

        assertTrue(arrBlockedQueueItems(config()).isEmpty())
    }

    @Test
    fun `a missing trackedDownloadState is not treated as blocked`() = runBlocking {
        queue("""{"records":[{"id":9,"title":"Old Radarr","status":"completed"}]}""")

        assertTrue(arrBlockedQueueItems(config()).isEmpty())
    }
}
