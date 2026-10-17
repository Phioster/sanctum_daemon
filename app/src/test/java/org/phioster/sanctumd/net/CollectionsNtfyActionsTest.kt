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
import org.phioster.sanctumd.notify.parseNtfyLine

/** Jellyfin collections (routes from its OpenAPI spec) and ntfy's action buttons. */
class CollectionsNtfyActionsTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-c")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-c", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )

    @Test fun `collections are listed with the item's own marked`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"c1","Name":"Marvel"},{"Id":"c2","Name":"Archiv"}]}"""))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"c2","Name":"Archiv"}]}"""))
        val list = jellyfinCollectionsFor(config(), "i1")
        assertTrue(server.takeRequest().path!!.contains("IncludeItemTypes=BoxSet"))
        assertEquals("/Items/i1/Collections?userId=u1", server.takeRequest().path)
        assertEquals(listOf(false, true), list.map { it.contains })
    }

    @Test fun `without the membership route nothing is marked, nothing fails`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"c1","Name":"Marvel"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(listOf(false), jellyfinCollectionsFor(config(), "i1").map { it.contains })
    }

    @Test fun `create, add and remove use the collection routes`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        assertEquals("collection created", jellyfinCreateCollection(config(), " Archiv ", "i1"))
        assertEquals("added", jellyfinSetInCollection(config(), "c1", "i1", add = true))
        assertEquals("removed", jellyfinSetInCollection(config(), "c1", "i1", add = false))
        assertEquals("POST /Collections?name=Archiv&ids=i1", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /Collections/c1/Items?ids=i1", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /Collections/c1/Items?ids=i1", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test fun `safe mode keeps collection changes off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinCreateCollection(config(), "x", "i1")
        jellyfinSetInCollection(config(), "c1", "i1", true)
        assertEquals(0, server.requestCount)
    }

    // ── ntfy actions ──

    @Test fun `view and http actions are read with their details`() {
        val msg = parseNtfyLine(
            """{"id":"a","event":"message","message":"Download failed","actions":[
                {"action":"view","label":"Open Sonarr","url":"https://sonarr.example/activity"},
                {"action":"http","label":"Retry","url":"https://hook.example/retry","method":"put",
                 "headers":{"Authorization":"Bearer x"},"body":"{\"id\":1}","clear":true}]}""",
        )!!
        assertEquals(2, msg.actions.size)
        assertEquals("view", msg.actions[0].kind)
        val http = msg.actions[1]
        assertEquals("PUT", http.method)
        assertEquals(mapOf("Authorization" to "Bearer x"), http.headers)
        assertEquals("{\"id\":1}", http.body)
        assertEquals(true, http.clear)
    }

    @Test fun `an http action without a method posts, like ntfy`() {
        val msg = parseNtfyLine("""{"id":"b","event":"message","message":"x","actions":[{"action":"http","label":"Go","url":"https://h/x"}]}""")!!
        assertEquals("POST", msg.actions.single().method)
    }

    @Test fun `broadcast, odd schemes and extras beyond three are dropped`() {
        val msg = parseNtfyLine(
            """{"id":"c","event":"message","message":"x","actions":[
                {"action":"broadcast","label":"B"},
                {"action":"view","label":"Intent","url":"intent://evil"},
                {"action":"view","label":"1","url":"https://a"},{"action":"view","label":"2","url":"https://b"},
                {"action":"view","label":"3","url":"https://c"},{"action":"view","label":"4","url":"https://d"}]}""",
        )!!
        assertEquals(listOf("1", "2", "3"), msg.actions.map { it.label })
    }
}
