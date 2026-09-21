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
 * Safe mode has to be a mechanism, not a promise: a guarded call must not reach the
 * network at all while it is on. That is checked against a real server here, if the
 * request arrived, the guard failed.
 */
class DestructiveTest {

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
        id = "t", type = ServiceType.RADARR, label = "Radarr",
        baseUrl = server.url("/").toString(), apiKey = "k",
    )

    @Test
    fun `a guarded call runs normally while safe mode is off`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        arrQueueRemove(config(), 5)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `safe mode stops the call before it hits the network`() = runBlocking {
        SafeMode.enabled = true
        val result = arrQueueRemove(config(), 5)
        assertEquals(0, server.requestCount)
        assertEquals("blocked by safe mode", result)
    }

    @Test
    fun `a blocked delete never reaches the server`() = runBlocking {
        SafeMode.enabled = true
        val result = arrDelete(config(), id = 12, deleteFiles = true)
        assertEquals(0, server.requestCount)
        assertEquals("blocked by safe mode", result)
    }

    @Test
    fun `blocked calls are counted`() = runBlocking {
        SafeMode.enabled = true
        val before = SafeMode.blockedCount
        arrRssSync(config())
        arrSearchAll(config(), cutoff = false)
        assertEquals(before + 2, SafeMode.blockedCount)
    }

    @Test
    fun `reads are not guarded - the dashboard still works in safe mode`() = runBlocking {
        SafeMode.enabled = true
        server.enqueue(MockResponse().setBody("""{"records":[]}""").setHeader("Content-Type", "application/json"))
        arrQueue(config())
        assertTrue("a read must still reach the server", server.requestCount == 1)
    }
}
