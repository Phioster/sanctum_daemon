package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.DownloadEntry

class DownloadGroupingTest {
    private fun e(id: String, name: String, sub: String, at: Long) =
        DownloadEntry(itemId = id, serverId = "s", name = name, subtitle = sub, state = DownloadEntry.STATE_DONE, addedAt = at)

    @Test fun `episodes of one series share a group, films stand alone`() {
        val g = groupDownloads(listOf(
            e("1", "Maomao", "Die Tagebücher der Apothekerin · S01E01", 1),
            e("2", "Pippi geht von Bord", "1969", 2),
            e("3", "Die barsche Heilerin", "Die Tagebücher der Apothekerin · S01E02", 3),
        ))
        assertEquals(listOf("Die Tagebücher der Apothekerin", "Pippi geht von Bord"), g.map { it.title })
        assertEquals(listOf("1", "3"), g[0].entries.map { it.itemId }) // episode order inside
        assertTrue(g[0].isSeries)
        assertFalse(g[1].isSeries)
    }

    @Test fun `a single episode still counts as a series`() {
        assertTrue(groupDownloads(listOf(e("1", "Pilot", "Show · S01E01", 1))).single().isSeries)
    }

    @Test fun `time left reads in minutes and hours`() {
        assertEquals("", fmtEta(0))
        assertEquals("< 1 min", fmtEta(30))
        assertEquals("3 min", fmtEta(200))
        assertEquals("1 h 05 min", fmtEta(3900))
    }
}
