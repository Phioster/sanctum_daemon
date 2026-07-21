package org.phioster.sanctumd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.DashTab

/** The rearrange/edit rules behind the dashboard's ✎ mode — all the off-by-one candidates. */
class DashLogicTest {

    private fun card(id: String) = DashCard(id, CardType.RADARR_QUEUE, "svc")
    private fun tabs() = listOf(
        DashTab("t1", "Home", cards = listOf(card("c1"), card("c2"), card("c3"))),
        DashTab("t2", "Movies"),
        DashTab("t3", "Series"),
    )

    @Test
    fun `moving a tab right swaps it with its neighbour`() {
        val (moved, index) = tabs().movedTab("t1", +1)!!
        assertEquals(listOf("t2", "t1", "t3"), moved.map { it.id })
        assertEquals(1, index)
    }

    @Test
    fun `the first tab cannot move left and the last cannot move right`() {
        assertNull(tabs().movedTab("t1", -1))
        assertNull(tabs().movedTab("t3", +1))
    }

    @Test
    fun `moving an unknown tab does nothing`() {
        assertNull(tabs().movedTab("nope", +1))
    }

    @Test
    fun `moving a card only touches its own tab`() {
        val moved = tabs().movedCard("t1", "c1", +1)
        assertEquals(listOf("c2", "c1", "c3"), moved[0].cards.map { it.id })
        assertEquals(tabs()[1], moved[1])
    }

    @Test
    fun `a card at the edge stays put`() {
        assertEquals(
            listOf("c1", "c2", "c3"),
            tabs().movedCard("t1", "c1", -1)[0].cards.map { it.id },
        )
        assertEquals(
            listOf("c1", "c2", "c3"),
            tabs().movedCard("t1", "c3", +1)[0].cards.map { it.id },
        )
    }

    @Test
    fun `the entry count is clamped to what a card can render`() {
        fun countAfter(requested: Int) = tabs()
            .withCardUpdated("t1", "c1", "Queue", requested, 0L, "", "", false, "", "")
            .first().cards.first().count
        assertEquals(MIN_CARD_ENTRIES, countAfter(0))
        assertEquals(MIN_CARD_ENTRIES, countAfter(-5))
        assertEquals(MAX_CARD_ENTRIES, countAfter(999))
        assertEquals(6, countAfter(7)) // TEMP: proves the CI gate
    }

    @Test
    fun `the card title is trimmed`() {
        val updated = tabs()
            .withCardUpdated("t1", "c1", "  Download Queue  ", 5, 0L, "", "", false, "", "")
        assertEquals("Download Queue", updated.first().cards.first().title)
    }

    @Test
    fun `adding and removing a card only affects the target tab`() {
        val added = tabs().withCardAdded("t2", card("new"))
        assertEquals(listOf("new"), added[1].cards.map { it.id })
        assertEquals(3, added[0].cards.size)

        val removed = added.withCardRemoved("t1", "c2")
        assertEquals(listOf("c1", "c3"), removed[0].cards.map { it.id })
        assertEquals(listOf("new"), removed[1].cards.map { it.id })
    }

    @Test
    fun `removing a card that is not there leaves the list alone`() {
        assertEquals(tabs(), tabs().withCardRemoved("t1", "does-not-exist"))
    }
}
