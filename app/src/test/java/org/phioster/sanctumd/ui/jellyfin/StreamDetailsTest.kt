package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.JellyStream

/**
 * The FILE section must only show what the file actually says.
 *
 * Jellyfin reports `VideoRange` on audio streams too, as the string "Unknown", printing it put
 * a meaningless `range Unknown` row under every audio track. And an untagged track (old AVI rips
 * carry no language) must simply show no language rather than a placeholder.
 */
class StreamDetailsTest {

    private val untaggedMp3 = JellyStream(
        type = "Audio",
        codec = "mp3",
        channels = 2,
        channelLayout = "stereo",
        sampleRate = 48000,
        bitrate = 115_000,
        videoRange = "Unknown", // what Jellyfin returns for a non-video stream
        displayTitle = "MP3 - Stereo",
    )

    @Test fun `an audio track shows no video range`() {
        val labels = streamDetails(untaggedMp3).map { it.first }
        assertFalse("range has no meaning on audio: $labels", labels.contains("range"))
    }

    @Test fun `an unknown range is not shown on video either`() {
        val labels = streamDetails(untaggedMp3.copy(type = "Video", videoRange = "Unknown")).map { it.first }
        assertFalse(labels.contains("range"))
    }

    @Test fun `a real video range is still shown`() {
        val rows = streamDetails(JellyStream(type = "Video", codec = "hevc", videoRange = "HDR10"))
        assertEquals("HDR10", rows.first { it.first == "range" }.second)
    }

    @Test fun `an untagged track simply has no language in its headline`() {
        // "MP3 · stereo", not "unknown · MP3 · stereo", nothing invented.
        assertEquals("MP3 · stereo", streamHeadline(untaggedMp3))
    }

    @Test fun `a tagged track leads with its language`() {
        assertEquals("Deutsch · EAC3 · 5.1", streamHeadline(untaggedMp3.copy(codec = "eac3", language = "Deutsch", channelLayout = "5.1")))
    }

    /**
     * An untagged track can borrow the film's original language. But it is an inference about
     * the film, not a fact about the track, so it is labelled. Without the label it would look
     * exactly like the real thing read out of an MKV, and a dual-language rip with no tags would
     * be quietly mislabelled.
     */
    @Test fun `an untagged audio track can borrow the original language, marked as assumed`() {
        val rows = streamDetails(untaggedMp3, assumedLanguage = "English")
        assertEquals("English (assumed)", rows.first { it.first == "language" }.second)
    }

    @Test fun `a track that states its own language does not get the assumption`() {
        val rows = streamDetails(untaggedMp3.copy(language = "Deutsch"), assumedLanguage = "English")
        assertFalse(rows.map { it.first }.contains("language"))
    }

    @Test fun `without an original language nothing is invented`() {
        assertFalse(streamDetails(untaggedMp3).map { it.first }.contains("language"))
    }

    @Test fun `only audio borrows it, a video track has no language to speak of`() {
        val rows = streamDetails(untaggedMp3.copy(type = "Video"), assumedLanguage = "English")
        assertFalse(rows.map { it.first }.contains("language"))
    }

    @Test fun `the remaining audio facts survive`() {
        val labels = streamDetails(untaggedMp3).map { it.first }
        assertTrue(labels.containsAll(listOf("sample rate", "channels", "bitrate", "title")))
    }
}
