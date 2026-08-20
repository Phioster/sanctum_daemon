package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.phioster.sanctumd.model.JellyMediaDetail

/**
 * The detail sheet's own state, held together instead of scattered.
 *
 * `JellyfinScreen` had grown to 63 loose `var … by remember` declarations in one composable,
 * which is why every attempt to lift a piece of it out produced a function with fifteen
 * parameters: there was nothing to pass but the individual variables. Grouping the ones that
 * belong to the sheet is what makes lifting it out possible at all.
 *
 * Deliberately a plain holder with no logic — it says what is open, nothing about what that
 * means. Anything that acts stays in the screen or in the sheet.
 */
internal class JellyfinDetailState {
    /** The item whose sheet is open, or null when none is. */
    var detail by mutableStateOf<JellyMediaDetail?>(null)

    /** The overflow menu in the sheet's header. */
    var menuOpen by mutableStateOf(false)

    // Each of these holds the item a dialog was opened for — null means that dialog is closed.
    var manage by mutableStateOf<JellyMediaDetail?>(null)
    var subtitles by mutableStateOf<JellyMediaDetail?>(null)
    var identify by mutableStateOf<JellyMediaDetail?>(null)
    var delete by mutableStateOf<JellyMediaDetail?>(null)
    var cast by mutableStateOf<JellyMediaDetail?>(null)

    /** Closes the sheet and every dialog belonging to it. */
    fun closeAll() {
        detail = null
        menuOpen = false
        manage = null
        subtitles = null
        identify = null
        delete = null
        cast = null
    }
}

@Composable
internal fun rememberJellyfinDetailState(): JellyfinDetailState = remember { JellyfinDetailState() }
