package org.phioster.sanctumd.ui.player

/** Where the video actually sits on screen, in pixels. */
data class VideoRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The on-screen rect of an aspect-fit video inside a [boxW] × [boxH] player, scaled by [zoom]
 * around the centre — or null when the video covers the whole player, i.e. there are no bars for
 * the ambient glow to fill.
 *
 * Pure maths, kept out of [AmbientGlow] so it can be unit tested without Compose.
 */
fun ambientVideoRect(boxW: Int, boxH: Int, aspect: Float, zoom: Float): VideoRect? {
    if (boxW <= 0 || boxH <= 0 || aspect <= 0f) return null
    val boxAspect = boxW.toFloat() / boxH
    val fitW: Float
    val fitH: Float
    if (aspect >= boxAspect) {
        fitW = boxW.toFloat()
        fitH = boxW / aspect
    } else {
        fitH = boxH.toFloat()
        fitW = boxH * aspect
    }
    val w = fitW * zoom.coerceAtLeast(1f)
    val h = fitH * zoom.coerceAtLeast(1f)
    // A hair of tolerance: a rect that misses by a fraction of a pixel is "no bars", not a 0.4px glow.
    if (w >= boxW - 1f && h >= boxH - 1f) return null
    return VideoRect(
        left = (boxW - w) / 2f,
        top = (boxH - h) / 2f,
        right = (boxW + w) / 2f,
        bottom = (boxH + h) / 2f,
    )
}
