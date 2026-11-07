package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartTargetsTest {
    private fun ep(id: String, played: Boolean) = SmartEpisode(id, id, "", "", played, 0)

    @Test fun `keeps the next unwatched episodes, skipping watched ones in between`() {
        val eps = listOf(ep("1", true), ep("2", true), ep("3", false), ep("4", true), ep("5", false), ep("6", false), ep("7", false))
        assertEquals(listOf("3", "5", "6"), smartTargets(eps, 3).map { it.id })
    }

    @Test fun `a series watched to the end has nothing left to keep`() {
        assertEquals(emptyList<SmartEpisode>(), smartTargets(listOf(ep("1", true), ep("2", true)), 3))
    }
}
