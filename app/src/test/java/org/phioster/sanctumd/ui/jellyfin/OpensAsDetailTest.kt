package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which folders are *things* with a page of their own, and which are just containers.
 *
 * Before this, every folder went onto the browse stack, so a season was a flat list of episode
 * rows with no artwork, synopsis or actions of its own — unlike a film, which had all three.
 */
class OpensAsDetailTest {

    @Test fun `a series and a season each open as a detail page`() {
        assertTrue(opensAsDetail("Series"))
        assertTrue(opensAsDetail("Season"))
    }

    @Test fun `library roots and albums stay plain browse folders`() {
        assertFalse(opensAsDetail("tvshows"))
        assertFalse(opensAsDetail("movies"))
        assertFalse(opensAsDetail("MusicAlbum"))
    }
}
