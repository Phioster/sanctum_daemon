package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** Buddy (2026-10-24): a `.de.srt` next to the MKV showed in Jellyfin as "German – Extern" but never in our player. */
class ExternalSubtitlesTest {
    private val ms = JfMediaSource(
        Id = "ms1",
        MediaStreams = listOf(
            JfMediaStream(Type = "Video"),
            JfMediaStream(Type = "Subtitle", Codec = "ass", Language = "eng", Index = 2, DisplayTitle = "English - ASS"),
            JfMediaStream(Type = "Subtitle", Codec = "subrip", Language = "ger", Index = 3, IsExternal = true, DisplayTitle = "German - Extern"),
            JfMediaStream(Type = "Subtitle", Codec = "srt", Index = 4, IsExternal = true, DeliveryUrl = "/Videos/i1/ms1/Subtitles/4/0/Stream.srt?api_key=x"),
        ),
    )

    @Test fun `only sidecar files are listed, embedded tracks stay with the stream`() {
        val subs = externalSubtitles("https://jf.example/", "i1", ms)
        assertEquals(2, subs.size)
        assertEquals(ExternalSubtitle("https://jf.example/Videos/i1/ms1/Subtitles/3/0/Stream.srt", "German - Extern", "ger", "srt"), subs[0])
    }

    @Test fun `a delivery url from the server is used as given`() {
        val s = externalSubtitles("https://jf.example/", "i1", ms)[1]
        assertEquals("https://jf.example/Videos/i1/ms1/Subtitles/4/0/Stream.srt?api_key=x", s.url)
        assertEquals("external", s.label)
    }
}
