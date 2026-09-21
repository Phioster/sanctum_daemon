package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.PlaybackKind
import org.phioster.sanctumd.model.TranscodeCost

/** The exact strings a live Jellyfin 10.x wrote into PlaybackActivity, read on 2026-08-21. */
class PlaybackKindTest {

    @Test fun `a direct play is untouched`() {
        assertEquals(PlaybackKind.DIRECT, playbackKind("DirectPlay"))
    }

    @Test fun `a direct stream was remuxed, not re-encoded`() {
        assertEquals(PlaybackKind.STREAM, playbackKind("DirectStream"))
    }

    /** The costly one, and the reason this distinction exists at all on a phone server. */
    @Test fun `a transcode is recognised despite its trailing detail`() {
        assertEquals(PlaybackKind.TRANSCODE, playbackKind("Transcode (v:h264 a:direct)"))
        assertEquals(PlaybackKind.TRANSCODE, playbackKind("Transcode (v:direct a:aac)"))
    }

    @Test fun `an unknown or missing method counts as direct rather than alarming`() {
        assertEquals(PlaybackKind.DIRECT, playbackKind(null))
        assertEquals(PlaybackKind.DIRECT, playbackKind(""))
    }

    /** What was re-encoded is the actionable half. It says whether the video or only the audio. */
    @Test fun `the detail of a transcode is kept`() {
        assertEquals("v:h264 a:direct", transcodeDetail("Transcode (v:h264 a:direct)"))
        assertEquals("", transcodeDetail("DirectPlay"))
        assertEquals("", transcodeDetail("Transcode"))
    }

    /**
     * Live TV has no original file to hand through, so it is transcoded every single time.
     * Counting it made the tile report work nobody can avoid. 3 of 12 on the live server.
     */
    @Test fun `live tv does not count against the server`() {
        assertFalse(countsAsTranscode("Transcode (v:h264 a:direct)", "TvChannel"))
    }

    @Test fun `a real file being transcoded does count`() {
        assertTrue(countsAsTranscode("Transcode (v:h264 a:direct)", "Movie"))
        assertTrue(countsAsTranscode("Transcode (v:h264 a:direct)", "Episode"))
    }

    @Test fun `a direct play never counts, live tv or not`() {
        assertFalse(countsAsTranscode("DirectPlay", "Movie"))
        assertFalse(countsAsTranscode("DirectStream", "TvChannel"))
    }
}

/**
 * What a transcode actually cost the server, split out of the same label.
 *
 * Measured on the live server 2026-08-21: `Transcode (v:direct a:direct)` means **nothing** was
 * re-encoded. Only the container changed, which a phone does for free. Counting that next to a
 * full h264+aac re-encode made the tile alarm about work that never happened.
 */
class TranscodeCostTest {

    @Test fun `nothing re-encoded is a remux`() {
        assertEquals(TranscodeCost.REMUX, transcodeCost("Transcode (v:direct a:direct)"))
    }

    @Test fun `only the video re-encoded`() {
        assertEquals(TranscodeCost.VIDEO, transcodeCost("Transcode (v:h264 a:direct)"))
    }

    @Test fun `only the audio re-encoded`() {
        assertEquals(TranscodeCost.AUDIO, transcodeCost("Transcode (v:direct a:aac)"))
    }

    @Test fun `both re-encoded is the expensive case`() {
        assertEquals(TranscodeCost.FULL, transcodeCost("Transcode (v:h264 a:aac)"))
    }

    /** A label without a readable detail must not be talked down into looking cheap. */
    @Test fun `an unreadable detail counts as the worst case`() {
        assertEquals(TranscodeCost.FULL, transcodeCost("Transcode"))
        assertEquals(TranscodeCost.FULL, transcodeCost("Transcode (something else)"))
    }

    @Test fun `what was never a transcode has no cost`() {
        assertEquals(TranscodeCost.NONE, transcodeCost("DirectPlay"))
        assertEquals(TranscodeCost.NONE, transcodeCost("DirectStream"))
        assertEquals(TranscodeCost.NONE, transcodeCost(null))
    }
}

/** The tile's two numbers, counted apart. Live TV stays out of both. */
class TranscodeTallyTest {

    private fun rows() = listOf(
        Triple("Transcode (v:h264 a:aac)", 4, "Movie"),
        Triple("Transcode (v:h264 a:direct)", 2, "Episode"),
        Triple("Transcode (v:direct a:direct)", 3, "Movie"),
        Triple("Transcode (v:direct a:direct)", 5, "TvChannel"),
        Triple("DirectPlay", 40, "Movie"),
    )

    @Test fun `re-encodes and remuxes are counted apart`() {
        val t = tallyTranscodes(rows())
        assertEquals(6, t.reencoded)
        assertEquals(3, t.remuxed)
    }

    @Test fun `live tv counts in neither`() {
        val t = tallyTranscodes(listOf(Triple("Transcode (v:h264 a:aac)", 9, "TvChannel")))
        assertEquals(0, t.reencoded)
        assertEquals(0, t.remuxed)
    }
}
