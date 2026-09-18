package org.phioster.sanctumd.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one key that has to always get you out again. */
class TvBackActionTest {

    @Test fun `with nothing open Zurueck leaves the film`() {
        // The state a film spends nearly all of its time in: overlay hidden, picture running.
        assertEquals(
            BackAction.LEAVE,
            backAction(menuOpen = false, infoOpen = false, controlsVisible = false, isPlaying = true),
        )
    }

    @Test fun `the menu closes before anything else`() {
        assertEquals(
            BackAction.CLOSE_MENU,
            backAction(menuOpen = true, infoOpen = true, controlsVisible = true, isPlaying = true),
        )
    }

    @Test fun `the technical panel closes instead of leaving the film`() {
        assertEquals(
            BackAction.CLOSE_INFO,
            backAction(menuOpen = false, infoOpen = true, controlsVisible = false, isPlaying = true),
        )
        assertEquals(
            BackAction.CLOSE_INFO,
            backAction(menuOpen = false, infoOpen = true, controlsVisible = true, isPlaying = true),
        )
    }

    @Test fun `a visible bar over a running film is put away first`() {
        assertEquals(
            BackAction.HIDE_CONTROLS,
            backAction(menuOpen = false, infoOpen = false, controlsVisible = true, isPlaying = true),
        )
    }

    @Test fun `paused, Zurueck leaves rather than hiding the bar`() {
        assertEquals(
            BackAction.LEAVE,
            backAction(menuOpen = false, infoOpen = false, controlsVisible = true, isPlaying = false),
        )
    }

    @Test fun `every layer is reachable in at most four presses`() {
        // Walking the worst case: menu over panel over bar. Each press must strictly peel one layer,
        // never put one back, so the sequence has to end in LEAVE.
        var menu = true
        var info = true
        var controls = true
        val seen = mutableListOf<BackAction>()
        repeat(4) {
            val action = backAction(menu, info, controls, isPlaying = true)
            seen += action
            when (action) {
                BackAction.CLOSE_MENU -> menu = false
                BackAction.CLOSE_INFO -> info = false
                BackAction.HIDE_CONTROLS -> controls = false
                BackAction.LEAVE -> Unit
            }
        }
        assertEquals(
            listOf(BackAction.CLOSE_MENU, BackAction.CLOSE_INFO, BackAction.HIDE_CONTROLS, BackAction.LEAVE),
            seen,
        )
    }
}
