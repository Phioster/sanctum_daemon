package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.sanctumd.model.JellyMediaItem

/**
 * The bulk-download chips used to live on the browse screen, where a season was a folder. Now a
 * season is a detail page, so the same choice — which episodes to queue — is made here.
 */
class PendingDownloadsTest {

    private fun ep(id: String, played: Boolean = false, kind: String = "Episode", folder: Boolean = false) =
        JellyMediaItem(id = id, name = id, kind = kind, subtitle = "", posterUrl = "", isFolder = folder, progressPct = 0f, played = played)

    private val children = listOf(
        ep("e1", played = true),
        ep("e2"),
        ep("e3"),
        ep("e4"),
        ep("extras", folder = true, kind = "Folder"),
        ep("theme", kind = "Audio"),
    )

    @Test fun `only undownloaded playable episodes are pending`() {
        assertEquals(
            listOf("e1", "e3", "e4"),
            pendingDownloads(children, downloaded = setOf("e2")).map { it.id },
        )
    }

    @Test fun `next unwatched skips what is watched and what is already here`() {
        assertEquals(
            listOf("e3", "e4"),
            nextUnwatched(children, downloaded = setOf("e2")).map { it.id },
        )
    }

    @Test fun `next unwatched stops at the limit`() {
        assertEquals(2, nextUnwatched(children, downloaded = emptySet(), limit = 2).size)
    }
}
