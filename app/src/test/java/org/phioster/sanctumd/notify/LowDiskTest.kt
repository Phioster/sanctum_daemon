package org.phioster.sanctumd.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.net.DiskInfo

class LowDiskTest {
    private val gb = 1_000_000_000L

    @Test fun `a disk shared by radarr and sonarr warns once`() {
        val d = DiskInfo("/storage/emulated", 7 * gb, 221 * gb)
        assertEquals(1, lowDisks(listOf(d, d.copy()), 15 * gb).size)
    }

    @Test fun `enough room means no warning`() {
        assertTrue(lowDisks(listOf(DiskInfo("/x", 53 * gb, 221 * gb)), 15 * gb).isEmpty())
    }

    @Test fun `the message says how much is left and how full`() {
        assertEquals(
            "Only 7.4 GB free on /storage/emulated (96% used). Imports need room for the whole file.",
            diskWarning(DiskInfo("/storage/emulated", 7_400_000_000L, 221 * gb)),
        )
    }
}
