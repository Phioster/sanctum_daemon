package org.phioster.sanctumd.ui.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * Which service a Seerr request lands in.
 *
 * Seerr names its two kinds "movie" and "tv" (not the "Movie"/"Series" Jellyfin uses) so this
 * is a separate mapping rather than a reuse of the Jellyfin one. Getting it wrong would search
 * the wrong service and report "not in the library" for something that is.
 */
class SeerrCounterpartTest {

    @Test fun `a movie request lands in radarr`() {
        assertEquals(ServiceType.RADARR, seerrServiceTypeFor("movie"))
    }

    @Test fun `a tv request lands in sonarr`() {
        assertEquals(ServiceType.SONARR, seerrServiceTypeFor("tv"))
    }

    @Test fun `the media type is matched regardless of case`() {
        assertEquals(ServiceType.RADARR, seerrServiceTypeFor("Movie"))
        assertEquals(ServiceType.SONARR, seerrServiceTypeFor("TV"))
    }

    @Test fun `anything unrecognised is not paired`() {
        assertNull(seerrServiceTypeFor("music"))
        assertNull(seerrServiceTypeFor(""))
    }
}
