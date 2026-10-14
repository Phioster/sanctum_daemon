package org.phioster.sanctumd.ui.arr

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.sanctumd.model.ArrEpisode

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

    private fun ep(season: Int, number: Int) = ArrEpisode(season * 100 + number, season, number, "", false, false, "")

    @Test fun `season 1 on top, specials at the bottom`() {
        val eps = listOf(ep(2, 1), ep(1, 2), ep(0, 1), ep(1, 1), ep(10, 1))
        assertEquals(listOf(1, 2, 10, 0), seasonsInOrder(eps).map { it.first })
    }

    @Test fun `episodes keep their order inside a season`() {
        val eps = listOf(ep(1, 2), ep(1, 1))
        assertEquals(listOf(2, 1), seasonsInOrder(eps).single().second.map { it.episodeNumber })
    }
}
