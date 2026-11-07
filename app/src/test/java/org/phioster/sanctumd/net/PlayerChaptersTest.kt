package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.ui.player.chapterAt

class PlayerChaptersTest {
    @Test fun `chapters come sorted, deduplicated and named`() {
        val c = playerChapters(listOf(JfChapter(600_000_000, "Act 2"), JfChapter(0, ""), JfChapter(0, "dup")))
        assertEquals(listOf(PlayerChapter(0, "Chapter 1"), PlayerChapter(60_000, "Act 2")), c)
    }

    @Test fun `a single chapter is not worth a list`() {
        assertTrue(playerChapters(listOf(JfChapter(0, "Chapter 1"))).isEmpty())
    }

    @Test fun `the current chapter is the last one started`() {
        val c = listOf(PlayerChapter(0, "a"), PlayerChapter(60_000, "b"))
        assertEquals("a", chapterAt(c, 59_999)?.name)
        assertEquals("b", chapterAt(c, 60_000)?.name)
    }
}
