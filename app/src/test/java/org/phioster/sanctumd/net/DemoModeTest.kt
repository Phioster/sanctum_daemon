package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.demo.DemoWorld
import org.phioster.sanctumd.model.ServiceType
import java.io.File

/**
 * The demo answered through the app's real network code: every status, list and detail the
 * screenshots show must parse, or the demo would show error cards instead of a busy server.
 */
class DemoModeTest {
    companion object {
        @BeforeClass @JvmStatic fun catalog() {
            DemoWorld.assetReader = { name -> File("src/main/assets/demo/$name").readText() }
        }
    }

    private fun svc(t: ServiceType) = DemoMode.services.first { it.type == t }

    @Test fun `the bundled catalogs are full`() {
        assertTrue(DemoWorld.titles.size >= 80)
        assertTrue(DemoWorld.albums.size >= 20)
        assertTrue(DemoWorld.libraryMovies.size >= 30)
        assertTrue(DemoWorld.libraryShows.size >= 20)
        assertTrue(DemoWorld.downloads.size >= 4)
    }

    @Test fun `every service card shows numbers, none an error`() = runBlocking {
        DemoMode.services.forEach { cfg ->
            val st = fetchStatus(cfg)
            assertTrue("${cfg.label}: ${st.error}", st.ok)
            assertTrue("${cfg.label} stats", st.stats.isNotEmpty() || cfg.type == ServiceType.NTFY)
        }
    }

    @Test fun `the calendar of the next weeks is busy`() = runBlocking {
        val now = java.time.Instant.now()
        val end = now.plus(java.time.Duration.ofDays(30))
        val episodes = arrCalendarRange(svc(ServiceType.SONARR), now, end)
        val movies = arrCalendarRange(svc(ServiceType.RADARR), now.minus(java.time.Duration.ofDays(30)), end)
        val albums = arrCalendarRange(svc(ServiceType.LIDARR), now, end)
        assertTrue("episodes ${episodes.size}", episodes.size >= 15)
        assertTrue("movies ${movies.size}", movies.size >= 3)
        assertTrue("albums ${albums.size}", albums.isNotEmpty())
    }

    @Test fun `arr libraries, queues, missing and history parse`() = runBlocking {
        listOf(ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR).forEach { t ->
            val cfg = svc(t)
            assertTrue("$t library", arrLibrary(cfg).isNotEmpty())
            assertTrue("$t queue", arrQueue(cfg).isNotEmpty())
            assertTrue("$t history", arrHistory(cfg).isNotEmpty())
            arrMissing(cfg)
        }
        val firstShow = arrLibrary(svc(ServiceType.SONARR)).first()
        assertTrue(arrEpisodes(svc(ServiceType.SONARR), firstShow.id).isNotEmpty())
        assertTrue(arrDetail(svc(ServiceType.SONARR), firstShow.id).title.isNotBlank())
        val firstMovie = arrLibrary(svc(ServiceType.RADARR)).first()
        assertEquals(firstMovie.title, arrDetail(svc(ServiceType.RADARR), firstMovie.id).title)
    }

    @Test fun `jellyfin browses from library to episode`() = runBlocking {
        val jf = svc(ServiceType.JELLYFIN)
        val views = jellyfinLibraryViews(jf)
        assertTrue(views.any { it.kind == "tvshows" })
        val shows = jellyfinItems(jf, "lib-tv")
        assertTrue(shows.isNotEmpty())
        val seasons = jellyfinItems(jf, shows.first().id)
        assertTrue("seasons", seasons.isNotEmpty())
        val eps = jellyfinItems(jf, seasons.last().id)
        assertTrue("episodes", eps.isNotEmpty())
        val detail = jellyfinItemDetail(jf, jellyfinItems(jf, "lib-movies").first().id)
        assertTrue(detail.cast.isNotEmpty())
        assertTrue(detail.fileInfo != null)
        assertTrue(jellyfinResume(jf).isNotEmpty())
        assertTrue(jellyfinSessions(jf).count { it.nowPlaying.isNotEmpty() } >= 3)
        assertEquals(3, jellyfinTopWatchers(jf).size)
        assertTrue(jellyfinItems(jf, "lib-music").isNotEmpty())
    }

    @Test fun `seerr, nzbget, prowlarr and ntfy have content`() = runBlocking {
        assertTrue(seerrRequests(svc(ServiceType.SEERR), "all").isNotEmpty())
        assertTrue(seerrDiscover(svc(ServiceType.SEERR), "movies").isNotEmpty())
        assertTrue(nzbgetQueue(svc(ServiceType.NZBGET)).size >= 4)
        assertTrue(prowlarrIndexers(svc(ServiceType.PROWLARR)).isNotEmpty())
        val msgs = ntfyHistory(svc(ServiceType.NTFY), "homelab")
        assertTrue(msgs.any { it.imageUrl.isNotBlank() })
    }

    @Test fun `a write in the demo succeeds and changes nothing`() = runBlocking {
        SafeMode.enabled = false
        val before = arrLibrary(svc(ServiceType.RADARR)).size
        assertTrue(arrRssSync(svc(ServiceType.RADARR)).let { !it.startsWith("error") })
        assertEquals(before, arrLibrary(svc(ServiceType.RADARR)).size)
    }
}
