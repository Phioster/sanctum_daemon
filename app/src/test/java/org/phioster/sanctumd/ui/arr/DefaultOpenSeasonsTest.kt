package org.phioster.sanctumd.ui.arr

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which Sonarr season starts open, as on Sonarr's own series page. */
class DefaultOpenSeasonsTest {

    @Test fun `the newest season opens`() {
        assertEquals(setOf(3), defaultOpenSeasons(listOf(3, 3, 2, 1, 0)))
    }

    @Test fun `specials don't win just by being listed`() {
        assertEquals(setOf(1), defaultOpenSeasons(listOf(0, 1)))
    }

    @Test fun `specials open when they are all there is`() {
        assertEquals(setOf(0), defaultOpenSeasons(listOf(0, 0)))
    }

    @Test fun `no episodes, nothing open`() {
        assertEquals(emptySet<Int>(), defaultOpenSeasons(emptyList()))
    }
}
