package org.phioster.sanctumd.ui.arr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The label is the whole point of showing the lockout: "recovers at 04:03 by itself" and
 * "still broken" call for different reactions from the user.
 */
class IndexerLockoutLabelTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-08-17T01:30:00Z")

    @Test fun `a lockout later today shows just the time in local zone`() {
        assertEquals("locked until 04:03", lockoutLabel("2026-08-17T02:03:39Z", now, berlin))
    }

    @Test fun `a lockout on another day carries the date`() {
        assertEquals("locked until 19.08. 04:03", lockoutLabel("2026-08-19T02:03:39Z", now, berlin))
    }

    @Test fun `a healthy indexer has no label`() {
        assertEquals("", lockoutLabel(null, now, berlin))
    }

    @Test fun `an unparseable timestamp degrades to no label rather than crashing`() {
        assertEquals("", lockoutLabel("who knows", now, berlin))
    }
}
