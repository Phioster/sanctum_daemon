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

    /** Sonarr sends a season newest first; by default it reads from episode 1. */
    @Test fun `episode 1 comes first by default`() {
        val eps = listOf(ep(1, 3), ep(1, 1), ep(1, 2))
        assertEquals(listOf(1, 2, 3), seasonsInOrder(eps).single().second.map { it.episodeNumber })
    }

    @Test fun `newest first when chosen, seasons still from 1`() {
        val eps = listOf(ep(1, 1), ep(2, 1), ep(1, 2))
        val ordered = seasonsInOrder(eps, oldestFirst = false)
        assertEquals(listOf(1, 2), ordered.map { it.first })
        assertEquals(listOf(2, 1), ordered.first().second.map { it.episodeNumber })
    }
}
