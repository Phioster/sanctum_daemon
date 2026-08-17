package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Test

/** File sizes and track lines are what the FILE section actually shows, so they get pinned here. */
class FileInfoFormatTest {

    @Test fun `bytes become the largest unit that stays readable`() {
        assertEquals("980 B", formatFileSize(980))
        assertEquals("1.9 KB", formatFileSize(1_945))
        assertEquals("12.4 MB", formatFileSize(13_000_000))
        assertEquals("8.43 GB", formatFileSize(9_052_398_293))
    }

    @Test fun `a missing size is left blank rather than shown as zero`() {
        assertEquals("", formatFileSize(0))
    }

    @Test fun `bitrates are shown in kbps for audio and mbps for video`() {
        assertEquals("640 kbps", formatBitrate(640_000))
        assertEquals("9.2 Mbps", formatBitrate(9_204_000))
        assertEquals("", formatBitrate(0))
    }
}
