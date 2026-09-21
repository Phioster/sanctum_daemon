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
    /**
     * The path of open sheets: series → season → episode. A stack rather than a single value,
     * because Jellyfin's own structure nests, and Back has to walk back up one level at a time
     * instead of dropping the user out of the whole thing.
     */
    var stack by mutableStateOf<List<JellyMediaDetail>>(emptyList())

    /** The sheet currently on top, or null when none is open. */
    val detail: JellyMediaDetail? get() = stack.lastOrNull()

    fun open(item: JellyMediaDetail) { stack = stack + item }

    /** Swaps the top sheet for a freshly loaded copy of itself, keeping the path intact. */
    fun replaceTop(item: JellyMediaDetail) {
        stack = if (stack.isEmpty()) listOf(item) else stack.dropLast(1) + item
    }

    /** One level up. Returns false when there was nothing left to close. */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack = stack.dropLast(1)
        return true
    }

    /** The overflow menu in the sheet's header. */
    var menuOpen by mutableStateOf(false)

    // Each of these holds the item a dialog was opened for — null means that dialog is closed.
    var manage by mutableStateOf<JellyMediaDetail?>(null)

    /** A Jellyfin collection whose Radarr counterpart is open. */
    var collection by mutableStateOf<JellyMediaDetail?>(null)
    var subtitles by mutableStateOf<JellyMediaDetail?>(null)
    var identify by mutableStateOf<JellyMediaDetail?>(null)
    var delete by mutableStateOf<JellyMediaDetail?>(null)
    var cast by mutableStateOf<JellyMediaDetail?>(null)

    /** The item a download-quality choice was opened for. Despite the name it is a dialog
     *  target, not a number — it was easy to misread as one while it sat among 63 loose vars. */
    var downloadQuality by mutableStateOf<JellyMediaDetail?>(null)

    /** Marking a folder watched asks first, because it covers every episode inside it. */
    var confirmWatched by mutableStateOf<Triple<String, String, Boolean>?>(null)

    /** Closes the sheet and every dialog belonging to it. */
    fun closeAll() {
        stack = emptyList()
        menuOpen = false; collection = null
        manage = null
        subtitles = null
        identify = null
        delete = null
        cast = null
        downloadQuality = null
        confirmWatched = null
    }
}

@Composable
internal fun rememberJellyfinDetailState(): JellyfinDetailState = remember { JellyfinDetailState() }
