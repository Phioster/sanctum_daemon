package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.sanctumd.model.PlaybackKind

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

    /** What was re-encoded is the actionable half — it says whether the video or only the audio. */
    @Test fun `the detail of a transcode is kept`() {
        assertEquals("v:h264 a:direct", transcodeDetail("Transcode (v:h264 a:direct)"))
        assertEquals("", transcodeDetail("DirectPlay"))
        assertEquals("", transcodeDetail("Transcode"))
    }
}
