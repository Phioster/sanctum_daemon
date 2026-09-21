package org.phioster.sanctumd.ui.tv

/** What the Zurück key does, decided by what is on screen. */
enum class BackAction { CLOSE_MENU, CLOSE_INFO, HIDE_CONTROLS, LEAVE }

/**
 * Back peels one layer off at a time, innermost first, and leaves the film once nothing is left.
 *
 * Kept out of the screen as plain logic because getting it wrong locks the viewer in: the overlay
 * is hidden for most of a film, so whatever Back does in *that* state has to be "leave". It used to
 * be swallowed by the rule that a press on a hidden overlay only wakes it — waking and hiding then
 * took turns and the film could not be left at all while it was playing.
 */
fun backAction(
    menuOpen: Boolean,
    infoOpen: Boolean,
    controlsVisible: Boolean,
    isPlaying: Boolean,
): BackAction = when {
    menuOpen -> BackAction.CLOSE_MENU
    infoOpen -> BackAction.CLOSE_INFO
    // Paused, the controls are the only thing to look at, so Back means the film, not the bar.
    controlsVisible && isPlaying -> BackAction.HIDE_CONTROLS
    else -> BackAction.LEAVE
}
