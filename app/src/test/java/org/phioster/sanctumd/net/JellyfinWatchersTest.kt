package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list behind the podium: everybody's watch time, and one person's titles. */
class JellyfinWatchersTest {

    @Test fun `a row becomes a person with the raw id kept apart from the name`() {
        val w = watcherRows(listOf(listOf("SekVII", "0a1b2c3d4e5f60718293a4b5c6d7e8f9", "69", "54273", "2026-11-11 18:53:02.1")))
        assertEquals("SekVII", w.single().name)
        assertEquals("0a1b2c3d4e5f60718293a4b5c6d7e8f9", w.single().userId)
        assertEquals(69, w.single().plays)
        assertEquals(54273L, w.single().seconds)
        assertEquals("2026-11-11", w.single().lastPlayed)
    }

    @Test fun `a row without a name is dropped, a short row does not crash`() {
        assertTrue(watcherRows(listOf(listOf("", "abc", "1", "10", ""))).isEmpty())
        assertEquals(0L, watcherRows(listOf(listOf("Cora"))).single().seconds)
    }

    @Test fun `titles carry type, plays, seconds and the day`() {
        val t = watchedTitleRows(listOf(listOf("Mutiny", "itm1", "Movie", "2", "5760.0", "2026-11-11 20:00:00"))).single()
        assertEquals("Mutiny", t.name)
        assertEquals("Movie", t.type)
        assertEquals(2, t.plays)
        assertEquals(5760L, t.seconds)
        assertEquals("2026-11-11", t.lastPlayed)
    }

    /** The id is pasted into SQL text, so only a plain id may get that far. */
    @Test fun `only a plain id makes a query`() {
        val sql = watcherTitlesSql("0a1b2c3d4e5f60718293a4b5c6d7e8f9")!!
        assertTrue(sql, sql.contains("WHERE UserId = '0a1b2c3d4e5f60718293a4b5c6d7e8f9'"))
        assertTrue(sql, sql.contains("BETWEEN 0 AND 21600"))
        assertNull(watcherTitlesSql("x' OR '1'='1"))
        assertNull(watcherTitlesSql(""))
    }

    @Test fun `the list keeps the garbage rows out of the sum too`() {
        assertTrue(WATCHERS_SQL, WATCHERS_SQL.contains("BETWEEN 0 AND 21600"))
        assertTrue(WATCHERS_SQL, WATCHERS_SQL.startsWith("SELECT UserId, UserId AS RawId"))
    }
}
