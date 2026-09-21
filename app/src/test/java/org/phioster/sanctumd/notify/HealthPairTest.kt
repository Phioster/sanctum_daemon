package org.phioster.sanctumd.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real messages, copied from what the servers actually sent on 2026-08-20.
 *
 * All four services fired a failure and a restore for the same issue seconds apart; the phone was
 * asleep and delivered all six at once, which is what made a 12-second blip look like a six-alarm
 * morning.
 */
class HealthPairTest {

    @Test fun `a failure names its service and issue`() {
        assertEquals(
            HealthEvent("Sonarr", "All indexers are unavailable due to failures", resolved = false),
            parseHealthEvent("Sonarr - Health Check Failure", "All indexers are unavailable due to failures"),
        )
    }

    @Test fun `a restore reports the same issue, marked resolved`() {
        assertEquals(
            HealthEvent("Sonarr", "All indexers are unavailable due to failures", resolved = true),
            parseHealthEvent(
                "Sonarr - Health Check Restored",
                "The following issue is now resolved: All indexers are unavailable due to failures",
            ),
        )
    }

    /** The pairing is by service *and* issue — Prowlarr's outage must not close Sonarr's. */
    @Test fun `each service keeps its own issue`() {
        val a = parseHealthEvent("Prowlarr - Health Check Failure", "All indexers are unavailable due to failures")
        val b = parseHealthEvent("Lidarr - Health Check Failure", "All indexers are unavailable due to failures")
        assertEquals("Prowlarr", a?.service)
        assertEquals("Lidarr", b?.service)
    }

    /** A restore whose prefix is missing still carries a usable issue rather than being dropped. */
    @Test fun `a restore without the usual prefix keeps its text`() {
        assertEquals(
            HealthEvent("Radarr", "Lists unavailable", resolved = true),
            parseHealthEvent("Radarr - Health Check Restored", "Lists unavailable"),
        )
    }

    @Test fun `anything that is not a health message is not one`() {
        assertNull(parseHealthEvent("Sonarr - Episode Imported", "Outer Banks S05E04"))
        assertNull(parseHealthEvent("", "All indexers are unavailable due to failures"))
        assertNull(parseHealthEvent("▶️ Wiedergabe gestartet", "Der Pate"))
    }
}
