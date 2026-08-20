package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * Each service names the same idea differently, and the cost of getting it wrong is not an error
 * but a wrong answer: an unknown query is ignored and the *whole* queue comes back, so a
 * stranger's download would appear on this item's page.
 */
class QueueFilterParamTest {

    @Test fun `radarr filters its queue by movie`() {
        assertEquals("movieId", queueFilterParam(ServiceType.RADARR))
    }

    @Test fun `sonarr filters its queue by series`() {
        assertEquals("seriesId", queueFilterParam(ServiceType.SONARR))
    }

    /** No param means don't ask at all, rather than ask wrongly. */
    @Test fun `a service with no counterpart is not queried`() {
        assertNull(queueFilterParam(ServiceType.LIDARR))
        assertNull(queueFilterParam(ServiceType.JELLYFIN))
    }
}
