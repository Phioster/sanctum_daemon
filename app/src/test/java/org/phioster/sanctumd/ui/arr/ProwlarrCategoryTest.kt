package org.phioster.sanctumd.ui.arr

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * Searching the wrong newznab category returns nothing and looks exactly like "the indexer does
 * not have it" — the failure mode that already cost two days once. Pinned so a Sonarr search can
 * never quietly go looking through the movie categories.
 */
class ProwlarrCategoryTest {

    @Test fun `radarr searches movies`() {
        assertEquals(2000, prowlarrCategoryFor(ServiceType.RADARR))
    }

    @Test fun `sonarr searches tv`() {
        assertEquals(5000, prowlarrCategoryFor(ServiceType.SONARR))
    }

    @Test fun `lidarr searches audio`() {
        assertEquals(3000, prowlarrCategoryFor(ServiceType.LIDARR))
    }

    /** Anything else searches everything rather than guessing a category and finding nothing. */
    @Test fun `an unexpected service searches all categories`() {
        assertEquals(0, prowlarrCategoryFor(ServiceType.JELLYFIN))
    }
}
