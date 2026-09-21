package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * Which service owns a Jellyfin item, and which id identifies it there.
 *
 * Radarr keys movies by TMDB, Sonarr keys series by TVDB, using the wrong one finds nothing and
 * looks exactly like "not in the library", which is the failure the delete pairing already had to
 * avoid. Episodes and seasons deliberately have no counterpart: the entry on the other side is
 * the whole series, and acting on it would reach far past what the user is looking at.
 */
class ArrCounterpartTest {

    @Test fun `a movie belongs to radarr`() {
        assertEquals(ServiceType.RADARR, arrServiceTypeFor("Movie"))
    }

    @Test fun `a series belongs to sonarr`() {
        assertEquals(ServiceType.SONARR, arrServiceTypeFor("Series"))
    }

    /**
     * An episode and a season reach Sonarr through their series. The bridge was first written to
     * exclude them, which left the feature working on films and silently absent on everything
     * else. They are included now, and the scope note below is what keeps that honest.
     */
    @Test fun `an episode reaches sonarr through its series`() {
        assertEquals(ServiceType.SONARR, arrServiceTypeFor("Episode"))
    }

    @Test fun `a season reaches sonarr through its series`() {
        assertEquals(ServiceType.SONARR, arrServiceTypeFor("Season"))
    }

    @Test fun `a film or a whole series acts on itself, so there is nothing to warn about`() {
        assertEquals("", counterpartScopeNote("Movie"))
        assertEquals("", counterpartScopeNote("Series"))
    }

    /** Acting from an episode reaches the entire series; saying so is the point. */
    @Test fun `from an episode or season the scope is spelled out`() {
        assertTrue(counterpartScopeNote("Episode").contains("whole series"))
        assertTrue(counterpartScopeNote("Season").contains("whole series"))
    }

    @Test fun `music and anything else are not paired`() {
        assertNull(arrServiceTypeFor("MusicAlbum"))
        assertNull(arrServiceTypeFor(""))
    }
}
