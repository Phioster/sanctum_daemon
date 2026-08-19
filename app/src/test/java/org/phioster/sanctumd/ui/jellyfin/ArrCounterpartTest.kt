package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * Which service owns a Jellyfin item, and which id identifies it there.
 *
 * Radarr keys movies by TMDB, Sonarr keys series by TVDB — using the wrong one finds nothing and
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

    @Test fun `an episode has no counterpart of its own`() {
        assertNull(arrServiceTypeFor("Episode"))
    }

    @Test fun `a season has no counterpart of its own`() {
        assertNull(arrServiceTypeFor("Season"))
    }

    @Test fun `music and anything else are not paired`() {
        assertNull(arrServiceTypeFor("MusicAlbum"))
        assertNull(arrServiceTypeFor(""))
    }
}
