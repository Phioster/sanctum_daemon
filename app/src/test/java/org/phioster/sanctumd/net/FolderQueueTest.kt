package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.ui.jellyfin.canPlayFromGrid

class FolderQueueTest {
    @Test fun `a run starts at the first unwatched episode`() {
        assertEquals(2, queueStart(listOf(true, true, false, false)))
        assertEquals(0, queueStart(listOf(false, true)))
    }

    @Test fun `all watched or empty starts from the top`() {
        assertEquals(0, queueStart(listOf(true, true)))
        assertEquals(0, queueStart(emptyList()))
    }

    @Test fun `play buttons show on films, episodes, series and seasons only`() {
        listOf("Movie", "Episode", "Series", "Season").forEach { assertTrue(it, canPlayFromGrid(it)) }
        listOf("BoxSet", "MusicAlbum", "Audio", "Playlist", "movies").forEach { assertFalse(it, canPlayFromGrid(it)) }
    }
}
