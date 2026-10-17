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

/** Playlists and merging versions, as Jellyfin's OpenAPI spec describes them. */
class JellyfinPlaylistsTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-pl")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-pl", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )
    private fun line() = server.takeRequest().let { "${it.method} ${it.path}" }

    @Test fun `playlists are listed for the user with their counts`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"p1","Name":"Gym","Type":"Playlist","IsFolder":true,"ChildCount":12}]}"""))
        val p = jellyfinPlaylists(config()).single()
        assertEquals("p1" to 12, p.id to p.count)
        val path = server.takeRequest().path!!
        listOf("userId=u1", "IncludeItemTypes=Playlist", "Recursive=true", "Fields=ChildCount").forEach { assertTrue("$it in $path", path.contains(it)) }
    }

    @Test fun `a music playlist keeps its order, entry ids and becomes one queue`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Id":"p1","Name":"Gym"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"Items":[{"Id":"t2","Name":"Second","Type":"Audio","PlaylistItemId":"e1","RunTimeTicks":1800000000},
                    {"Id":"t1","Name":"First","Type":"Audio","PlaylistItemId":"e2","RunTimeTicks":1200000000}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"OpenAccess":true,"Shares":[{"UserId":"u2","CanEdit":true}],"ItemIds":[]}"""))
        val d = jellyfinPlaylistDetail(config(), "p1")
        assertEquals(listOf("e1", "e2"), d.entries.map { it.entryId })
        assertEquals(listOf("t2", "t1"), d.tracks.map { it.id })
        assertEquals(180_000L, d.entries[0].durationMs)
        assertEquals(true, d.public)
        assertEquals(mapOf("u2" to true), d.shares)
        server.takeRequest()
        assertTrue(server.takeRequest().path!!.startsWith("/Playlists/p1/Items?userId=u1"))
        assertEquals("GET /Playlists/p1", line())
    }

    @Test fun `a mixed playlist has no music queue`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Id":"p2","Name":"Mix"}"""))
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"t1","Name":"Song","Type":"Audio","PlaylistItemId":"e1"},{"Id":"m1","Name":"Film","Type":"Movie","PlaylistItemId":"e2"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        val d = jellyfinPlaylistDetail(config(), "p2")
        assertEquals(2, d.entries.size)
        assertEquals(emptyList<MusicTrack>(), d.tracks)
        assertEquals(false, d.public)
    }

    @Test fun `create sends name, user and first items, and returns the id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Id":"new1"}"""))
        assertEquals("new1", jellyfinCreatePlaylist(config(), " Roadtrip ", listOf("a", "b")))
        val req = server.takeRequest()
        assertEquals("POST /Playlists", "${req.method} ${req.path}")
        assertEquals("""{"Name":"Roadtrip","UserId":"u1","Ids":["a","b"]}""", req.body.readUtf8())
    }

    @Test fun `entries are added by item and removed and moved by entry`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        assertEquals("added 2", jellyfinAddToPlaylist(config(), "p1", listOf("a", "b")))
        jellyfinRemoveFromPlaylist(config(), "p1", listOf("e1", "e2"))
        jellyfinMovePlaylistEntry(config(), "p1", "e3", 0)
        assertEquals("POST /Playlists/p1/Items?ids=a%2Cb&userId=u1", line())
        assertEquals("DELETE /Playlists/p1/Items?entryIds=e1%2Ce2", line())
        assertEquals("POST /Playlists/p1/Items/e3/Move/0", line())
    }

    @Test fun `update leaves out what is not changed`() {
        assertEquals("""{"Name":"New"}""", playlistUpdateBody(" New ", null).toString())
        assertEquals("""{"IsPublic":true}""", playlistUpdateBody(null, true).toString())
    }

    @Test fun `sharing sets edit rights per user, null unshares`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
        assertEquals("shared", jellyfinSharePlaylist(config(), "p1", "u2", canEdit = false))
        assertEquals("unshared", jellyfinSharePlaylist(config(), "p1", "u2", canEdit = null))
        val share = server.takeRequest()
        assertEquals("POST /Playlists/p1/Users/u2", "${share.method} ${share.path}")
        assertEquals("""{"CanEdit":false}""", share.body.readUtf8())
        assertEquals("DELETE /Playlists/p1/Users/u2", line())
    }

    @Test fun `deleting a playlist deletes only the playlist item, merging takes the id list`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
        jellyfinDeletePlaylist(config(), "p1")
        assertEquals("merged 2 versions", jellyfinMergeVersions(config(), listOf("m1", "m2")))
        assertEquals("DELETE /Items/p1", line())
        assertEquals("POST /Videos/MergeVersions?ids=m1%2Cm2", line())
    }

    @Test fun `safe mode keeps every write off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinCreatePlaylist(config(), "x", emptyList())
        jellyfinAddToPlaylist(config(), "p", listOf("a"))
        jellyfinRemoveFromPlaylist(config(), "p", listOf("e"))
        jellyfinMovePlaylistEntry(config(), "p", "e", 1)
        jellyfinUpdatePlaylist(config(), "p", name = "y")
        jellyfinSharePlaylist(config(), "p", "u", true)
        jellyfinDeletePlaylist(config(), "p")
        jellyfinMergeVersions(config(), listOf("a", "b"))
        assertEquals(0, server.requestCount)
    }
}
