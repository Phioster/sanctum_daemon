package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.demo.DemoWorld
import org.phioster.sanctumd.model.ServiceType
import java.io.File

class JellyfinMusicMoreTest {
    @Test fun `timed lyrics are read in milliseconds, offset applied`() {
        val l = lyricsOf("""{"Metadata":{"IsSynced":true,"Offset":-10000000},"Lyrics":[{"Text":"one","Start":50000000},{"Text":"two","Start":100000000}]}""")!!
        assertTrue(l.synced)
        assertEquals(listOf(4_000L, 9_000L), l.lines.map { it.startMs })
        assertEquals(-1, l.lineAt(3_999))
        assertEquals(0, l.lineAt(4_000))
        assertEquals(1, l.lineAt(60_000))
    }

    @Test fun `untimed lyrics are a page and never light a line`() {
        val l = lyricsOf("""{"Lyrics":[{"Text":"a"},{"Text":""},{"Text":"b"}]}""")!!
        assertFalse(l.synced)
        assertEquals(-1, l.lineAt(10_000))
        assertEquals(3, l.lines.size)
    }

    @Test fun `empty or broken answers mean no lyrics`() {
        assertNull(lyricsOf("""{"Lyrics":[]}"""))
        assertNull(lyricsOf("""{"Lyrics":[{"Text":"  "}]}"""))
        assertNull(lyricsOf("not json"))
    }

    @Test fun `a mixed track borrows its album's cover`() {
        val base = "https://jf/"
        val withAlbum = JfItem(Id = "t1", Name = "Song", Type = "Audio", AlbumId = "a1", AlbumPrimaryImageTag = "x", Album = "Record")
            .toMusicTrack(base, emptyMap())
        assertEquals(primaryArtUrl(base, "a1"), withAlbum.artUrl)
        assertEquals("Record", withAlbum.album)
        val own = JfItem(Id = "t2", Type = "Audio", ImageTags = mapOf("Primary" to "p"), AlbumId = "a1", AlbumPrimaryImageTag = "x").toMusicTrack(base, emptyMap())
        assertEquals(primaryArtUrl(base, "t2"), own.artUrl)
        assertEquals("", JfItem(Id = "t3", Type = "Audio").toMusicTrack(base, emptyMap()).artUrl)
    }

    @Test fun `the demo answers lyrics, mix and similar through the real code`() = runBlocking {
        DemoWorld.assetReader = { name -> File("src/main/assets/demo/$name").readText() }
        val jf = DemoMode.services.first { it.type == ServiceType.JELLYFIN }
        val album = DemoWorld.libraryAlbums.first()
        val first = album.tracks.first { it.n == 1 }
        val second = album.tracks.firstOrNull { it.n == 2 && it.disc == first.disc }
        val synced = jellyfinLyrics(jf, DemoWorld.trackId(album.id, first.n, first.disc))
        assertNotNull(synced)
        assertTrue(synced!!.synced)
        if (second != null) assertFalse(jellyfinLyrics(jf, DemoWorld.trackId(album.id, second.n, second.disc))!!.synced)
        val mix = jellyfinInstantMix(jf, DemoWorld.albumId(album.id))
        assertTrue(mix.isNotEmpty())
        assertEquals(album.title, mix.first().album)
        assertTrue(mix.all { it.artUrl.isNotBlank() })
        val similar = jellyfinSimilar(jf, DemoWorld.albumId(album.id))
        assertTrue(similar.none { it.id == DemoWorld.albumId(album.id) })
        assertTrue(similar.all { it.kind == "MusicAlbum" })
    }
}
