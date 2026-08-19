package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.JellyMediaItem

/**
 * Browsing goes library → series → season, and only the series has a counterpart in Sonarr:
 * a season carries no provider ids of its own. So the entry to act on is the series in the
 * path, whatever level the user has browsed down to.
 */
class SeriesInStackTest {

    private fun item(id: String, kind: String) =
        JellyMediaItem(id = id, name = id, kind = kind, subtitle = "", posterUrl = "", isFolder = true, progressPct = 0f)

    @Test fun `inside a season the series is found further up the path`() {
        val stack = listOf(item("lib", "tvshows"), item("series-1", "Series"), item("season-1", "Season"))
        assertEquals("series-1", seriesInStack(stack)?.id)
    }

    @Test fun `standing on the series itself finds it`() {
        assertEquals("series-1", seriesInStack(listOf(item("lib", "tvshows"), item("series-1", "Series")))?.id)
    }

    @Test fun `a movie library has no series to act on`() {
        assertNull(seriesInStack(listOf(item("lib", "movies"))))
    }

    @Test fun `at the top there is nothing to act on`() {
        assertNull(seriesInStack(emptyList()))
    }
}
