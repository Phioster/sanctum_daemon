package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The rule the rest of the app already followed and the music path did not: no credential in a
 * URL. A token here ended up in `MediaMetadata.artworkUri`, which media3 bundles to every
 * connected controller — readable by any app on the device.
 */
class JellyfinMusicUrlTest {

    private val base = "https://jellyfin.example.net/"

    @Test
    fun `the stream url carries no credential`() {
        val url = audioStreamUrl(base, "abc123")
        assertEquals("https://jellyfin.example.net/Audio/abc123/stream?static=true", url)
        assertFalse(url, url.contains("ApiKey", ignoreCase = true))
        assertFalse(url, url.contains("api_key", ignoreCase = true))
        assertFalse(url, url.contains("token", ignoreCase = true))
    }

    @Test
    fun `the artwork url carries no credential`() {
        val url = primaryArtUrl(base, "abc123")
        assertEquals("https://jellyfin.example.net/Items/abc123/Images/Primary?maxHeight=400", url)
        assertFalse(url, url.contains("ApiKey", ignoreCase = true))
        assertFalse(url, url.contains("api_key", ignoreCase = true))
    }

    @Test
    fun `a track with its own cover uses it`() {
        assertEquals(
            "https://jellyfin.example.net/Items/track1/Images/Primary?maxHeight=400",
            trackArtUrl(base, "track1", trackHasArt = true, albumId = "alb1", albumHasArt = true),
        )
    }

    @Test
    fun `a track without one falls back to the album cover`() {
        assertEquals(
            "https://jellyfin.example.net/Items/alb1/Images/Primary?maxHeight=400",
            trackArtUrl(base, "track1", trackHasArt = false, albumId = "alb1", albumHasArt = true),
        )
    }

    /**
     * The regression this guards: the album URL used to be handed out even when the album had no
     * cover, so every track in such an album pointed at a 404 and the session's bitmap loader ran
     * into it once per track.
     */
    @Test
    fun `no cover anywhere means no artwork url at all`() {
        assertEquals(
            "",
            trackArtUrl(base, "track1", trackHasArt = false, albumId = "alb1", albumHasArt = false),
        )
    }
}
