package org.phioster.sanctumd.ui.player

/**
 * What shape the video surface has to be.
 *
 * With the zero-copy output (`vo=mediacodec_embed`) the decoder renders straight into the Android
 * surface. mpv never composites, so its own `keepaspect` handling does not run: the picture is
 * stretched to whatever shape the surface has. A surface filling the screen therefore pulls a
 * 2.39:1 film to the full height of the panel and a 4:3 one to its full width. The surface has to
 * carry the aspect ratio itself; the bars are then simply the black box behind it.
 */

/** Outside this a number is a broken header, not a film, better to fill than to collapse. */
private val SANE_ASPECT = 0.2f..6.0f

/**
 * Width divided by height, or 0 when nothing usable is known yet (the caller fills the screen).
 *
 * [reported] is the player's own figure (mpv's `video-params/aspect`) which already accounts for
 * non-square pixels and is therefore preferred. [displayWidth] and [displayHeight] are the
 * aspect-corrected dimensions used as a fallback; the *stored* size must not be used here, because
 * an anamorphic file stores 720×576 and means it to be shown as 16:9.
 */
fun displayAspect(reported: Float, displayWidth: Int, displayHeight: Int): Float {
    if (reported in SANE_ASPECT) return reported
    if (displayWidth <= 0 || displayHeight <= 0) return 0f
    val fromSize = displayWidth.toFloat() / displayHeight.toFloat()
    return if (fromSize in SANE_ASPECT) fromSize else 0f
}
