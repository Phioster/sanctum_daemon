package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalClockTest {
    @Test fun `an iso instant becomes a local HH mm`() {
        assertTrue(Regex("\\d{2}:\\d{2}").matches(localClock("2026-11-07T08:12:00Z")))
    }

    @Test fun `garbage gives blank`() {
        assertEquals("", localClock("soon"))
    }
}
