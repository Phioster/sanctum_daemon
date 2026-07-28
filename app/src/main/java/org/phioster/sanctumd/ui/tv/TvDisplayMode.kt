package org.phioster.sanctumd.ui.tv

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Matching the panel's refresh rate to the film's frame rate.
 *
 * A TV stick outputs a fixed 60 Hz for its user interface. Film is 23.976 fps and European
 * television is 25 fps, and neither divides into 60 — so frames get held for uneven numbers of
 * refreshes (3, then 2, then 3…). The result is a steady, regular hitch in motion while the audio
 * stays perfectly smooth, which is exactly the shape of the problem reported here. No decoder
 * setting fixes it; the output has to change.
 *
 * Android exposes the panel's modes and lets a window request one. Picking a mode whose refresh
 * rate is a whole multiple of the video's rate makes every frame last the same time again.
 */

/** What the player is currently doing about refresh rate, for the diagnostics panel. */
data class DisplayModeInfo(
    val currentHz: Float = 0f,
    val requestedHz: Float = 0f,
    val available: List<Float> = emptyList(),
    val switched: Boolean = false,
)

@Suppress("DEPRECATION")
private fun Activity.activeDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay

/**
 * How well [refresh] suits [fps]: 0 is perfect. A mode qualifies when the video rate divides into it
 * a whole number of times, so 50 Hz is ideal for 25 fps and 48 Hz for 24.
 */
private fun cadenceError(refresh: Float, fps: Float): Float {
    if (fps <= 0f) return Float.MAX_VALUE
    val multiple = (refresh / fps).roundToInt()
    if (multiple < 1) return Float.MAX_VALUE
    return abs(refresh - fps * multiple) / fps
}

/**
 * Asks the window for the refresh rate that suits [fps], keeping the current resolution.
 *
 * Returns what happened, for display in the diagnostics panel. Does nothing when the panel offers
 * no better cadence than it is already running — switching modes blanks the screen for a moment, so
 * it is only worth it for a real improvement.
 */
fun applyRefreshRateFor(activity: Activity, fps: Float): DisplayModeInfo {
    val display = activity.activeDisplay() ?: return DisplayModeInfo()
    val current = display.mode ?: return DisplayModeInfo()
    val modes = display.supportedModes.orEmpty()
    val info = DisplayModeInfo(
        currentHz = current.refreshRate,
        available = modes.map { it.refreshRate }.distinct().sorted(),
    )
    if (fps <= 0f || modes.size < 2) return info

    // Only same-resolution modes: a resolution change is the user's business, not ours.
    val candidates = modes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }.ifEmpty { modes.toList() }

    val best = candidates.minByOrNull { cadenceError(it.refreshRate, fps) } ?: return info
    val bestError = cadenceError(best.refreshRate, fps)
    val currentError = cadenceError(current.refreshRate, fps)
    // Tolerance covers 23.976-into-59.94 style rounding; anything worse is real judder.
    if (bestError >= currentError || bestError > 0.01f) return info

    activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = best.modeId }
    return info.copy(requestedHz = best.refreshRate, switched = true)
}

/** Hands the panel back to the system's own choice. */
fun clearPreferredRefreshRate(activity: Activity) {
    activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 }
}
