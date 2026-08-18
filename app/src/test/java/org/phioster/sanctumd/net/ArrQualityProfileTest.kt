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

/**
 * A German custom-format profile blocks anything that only exists in English: the release never
 * reaches the required format score, whatever its quality. The fix is a second, unrestricted
 * profile — never loosening the existing one, which may be managed by Recyclarr and would be
 * silently reverted on its next sync.
 *
 * The new profile is a **clone** of a working one rather than hand-assembled, so the schema
 * comes from the server and cannot be malformed.
 */
class ArrQualityProfileTest {

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
        id = "test", type = ServiceType.RADARR, label = "Radarr",
        baseUrl = server.url("/").toString(), apiKey = "secret-key",
    )

    private val source = """{"id":7,"name":"[German] HD Bluray + WEB","upgradeAllowed":true,"cutoff":9,
        "minFormatScore":100,"cutoffFormatScore":200,
        "items":[{"quality":{"id":9,"name":"HDTV-1080p"},"allowed":true},
                 {"quality":{"id":3,"name":"WEBDL-480p"},"allowed":false}],
        "formatItems":[{"format":1,"name":"German","score":100}]}"""

    private fun cloneWith(name: String) = runBlocking {
        server.enqueue(MockResponse().setBody(source).setHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        val result = arrCloneProfileUnrestricted(config(), sourceId = 7, newName = name)
        val get = server.takeRequest()
        val post = server.takeRequest()
        Triple(result, get, post)
    }

    @Test fun `every quality is allowed in the clone`() {
        val (_, _, post) = cloneWith("Any")
        val body = post.body.readUtf8()
        assertTrue("got $body", body.contains("\"name\":\"HDTV-1080p\""))
        assertFalse("no quality may stay disallowed: $body", body.contains("\"allowed\":false"))
    }

    @Test fun `the custom format score floor is dropped`() {
        val (_, _, post) = cloneWith("Any")
        val body = post.body.readUtf8()
        assertTrue("got $body", body.contains("\"minFormatScore\":0"))
        assertTrue("got $body", body.contains("\"cutoffFormatScore\":0"))
    }

    @Test fun `the clone is created under the new name and without the source id`() {
        val (result, _, post) = cloneWith("Any")
        val body = post.body.readUtf8()
        assertTrue("got $body", body.contains("\"name\":\"Any\""))
        assertFalse("the id must not be carried over: $body", body.contains("\"id\":7"))
        assertEquals("created", result)
    }

    /** The existing profile must come back untouched — no PUT, only a read and a create. */
    @Test fun `the source profile is only read, never written`() {
        val (_, get, post) = cloneWith("Any")
        assertEquals("GET", get.method)
        assertTrue(get.path!!.endsWith("/api/v3/qualityprofile/7"))
        assertEquals("POST", post.method)
        assertTrue(post.path!!.endsWith("/api/v3/qualityprofile"))
    }
}
