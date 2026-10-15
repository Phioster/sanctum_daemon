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

/** Upcoming and missing episodes, and server backups, as Jellyfin's OpenAPI spec describes them. */
class JellyfinServerExtrasTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        clearJellyfinSession("jf-x")
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "jf-x", type = ServiceType.JELLYFIN, label = "Jellyfin",
        baseUrl = server.url("/").toString(), apiKey = "tk", useLogin = false, userId = "u1",
    )

    private val episode = """{"Items":[{"Id":"e1","Name":"Rescue","Type":"Episode","SeriesName":"Bookworm",
        "ParentIndexNumber":1,"IndexNumber":60,"PremiereDate":"2026-10-03T00:00:00.0000000Z"}]}"""

    @Test fun `upcoming episodes name show, episode and date`() = runBlocking {
        server.enqueue(MockResponse().setBody(episode))
        val e = jellyfinUpcoming(config()).single()
        assertTrue(server.takeRequest().path!!.startsWith("/Shows/Upcoming?userId=u1"))
        assertEquals("Bookworm · S01E60", e.where)
        assertEquals("Rescue", e.name)
        assertEquals("2026-10-03", e.date)
    }

    /** Unaired episodes are "missing" too as far as files go; the list is about aired ones. */
    @Test fun `missing asks for aired episodes without a file, newest first`() = runBlocking {
        server.enqueue(MockResponse().setBody(episode))
        jellyfinMissing(config())
        val path = server.takeRequest().path!!
        listOf("IsMissing=true", "IsUnaired=false", "Recursive=true", "IncludeItemTypes=Episode", "SortBy=PremiereDate", "SortOrder=Descending")
            .forEach { assertTrue("$it in $path", path.contains(it)) }
    }

    @Test fun `backups are listed newest first`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"ServerVersion":"10.11.0","DateCreated":"2026-09-01T03:00:00Z","Path":"/config/backups/a.zip"},
                    {"ServerVersion":"10.11.1","DateCreated":"2026-10-01T03:00:00Z","Path":"/config/backups/b.zip"}]""",
            ),
        )
        val list = jellyfinBackups(config())
        assertEquals("/Backup", server.takeRequest().path)
        assertEquals(listOf("/config/backups/b.zip", "/config/backups/a.zip"), list.map { it.path })
        assertEquals("2026-10-01 03:00", list[0].created)
    }

    @Test fun `a new backup always includes the database`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals("backup created", jellyfinCreateBackup(config(), metadata = true, trickplay = false, subtitles = false))
        val req = server.takeRequest()
        assertEquals("POST /Backup/Create", "${req.method} ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body, body.contains(""""Database":true"""))
        assertTrue(body, body.contains(""""Metadata":true"""))
        assertTrue(body, body.contains(""""Trickplay":false"""))
    }

    @Test fun `restore names the archive`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        jellyfinRestoreBackup(config(), "/config/backups/b.zip")
        val req = server.takeRequest()
        assertEquals("POST /Backup/Restore", "${req.method} ${req.path}")
        assertEquals("""{"ArchiveFileName":"/config/backups/b.zip"}""", req.body.readUtf8())
    }

    @Test fun `safe mode keeps backup and restore off the network`() = runBlocking {
        SafeMode.enabled = true
        jellyfinCreateBackup(config(), false, false, false)
        jellyfinRestoreBackup(config(), "/x.zip")
        assertEquals(0, server.requestCount)
    }
}
