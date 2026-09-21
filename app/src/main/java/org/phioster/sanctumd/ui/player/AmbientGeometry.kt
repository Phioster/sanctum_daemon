package org.phioster.sanctumd.ui.player

import kotlin.math.roundToInt

/** Where the video actually sits on screen, in pixels. */
data class VideoRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The on-screen rect of an aspect-fit video inside a [boxW] × [boxH] player, scaled by [zoom]
 * around the centre, or null when the video covers the whole player, i.e. there are no bars for
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

/**
 * How strongly an ambient frame is painted: [base] opacity, pulled down the brighter the frame is.
 *
 * Without this a white poster or a snow scene turns the bars into a lamp. [luma] is the frame's
 * average brightness, 0 (black) to 1 (white).
 */
fun ambientAlpha(base: Float, luma: Float): Float =
    (base * (1f - 0.55f * luma.coerceIn(0f, 1f))).coerceIn(0f, 1f)

/**
 * The size a glow frame is averaged down to before it is stretched back over the player: its longer
 * side becomes [longEdgePx], the shorter one follows the aspect ratio and never reaches zero.
 *
 * This downscale is the blur. Stretching a handful of pixels across the whole screen turns them
 * into broad fields of colour; leaving the frame larger keeps shapes readable in the bars, which is
 * the opposite of ambient. Frames already at or below the target are handed back untouched.
 */
fun glowSampleSize(width: Int, height: Int, longEdgePx: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || longEdgePx <= 0) return width to height
    val longest = maxOf(width, height)
    if (longest <= longEdgePx) return width to height
    val scale = longEdgePx.toFloat() / longest
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}
