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
 * "FAILURE/UNPACK" says where a download broke, not why. The sub-statuses carry the why;
 * their names and values are the ones in NZBGet's XmlRpc.cpp.
 */
class NzbgetFailureTest {

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
        baseUrl = server.url("/").toString(), username = "nzbadmin", password = "hunter2",
    )

    @Test fun `a success has no reason, even with imperfect health`() {
        assertEquals("", nzbFailureReason(NzbHistoryItem(Status = "SUCCESS/ALL", Health = 990)))
    }

    @Test fun `a password protected archive says so`() {
        assertEquals("unpack: password protected", nzbFailureReason(NzbHistoryItem(Status = "FAILURE/UNPACK", UnpackStatus = "PASSWORD")))
    }

    @Test fun `a full disk says so`() {
        assertEquals("unpack: disk full", nzbFailureReason(NzbHistoryItem(Status = "FAILURE/UNPACK", UnpackStatus = "SPACE")))
    }

    @Test fun `unrepairable health names both numbers`() {
        val r = nzbFailureReason(
            NzbHistoryItem(Status = "FAILURE/HEALTH", Health = 873, CriticalHealth = 920, DeleteStatus = "HEALTH"),
        )
        assertEquals("health 87.3 % (needs 92.0 %) · deleted: too many missing articles", r.replace(',', '.'))
    }

    @Test fun `history rows carry the reason`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"result":[{"NZBID":7,"Name":"Show.S01E01","Status":"FAILURE/PAR","FileSizeMB":900,
                    "ParStatus":"FAILURE","UnpackStatus":"NONE","Health":950,"CriticalHealth":900}]}""",
            ),
        )
        val h = nzbgetHistory(config(), hidden = false).single()
        assertTrue(h.reason, h.reason.endsWith("repair failed"))
    }

    @Test fun `mark bad is an editqueue command on the one id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        runNzbEditQueue(config(), "HistoryMarkBad", 7)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains(""""method":"editqueue""""))
        assertTrue(body, body.contains("HistoryMarkBad"))
        assertTrue(body, body.contains("[7]"))
    }

    @Test fun `safe mode keeps the history actions off the network`() = runBlocking {
        SafeMode.enabled = true
        runNzbEditQueue(config(), "HistoryRetryFailed", 7)
        assertEquals(0, server.requestCount)
    }
}
