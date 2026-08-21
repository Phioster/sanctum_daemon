package org.phioster.sanctumd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * A [Color] as the packed `0xAARRGGBB` value that [StatChart]-style models carry.
 *
 * The obvious-looking `color.value.toLong()` is **wrong** and fails silently: `Color.value` is
 * Compose's internal 64-bit representation, which also encodes the colour space — feeding it back
 * into `Color(Long)` produces near-black rather than the colour you started with. That cost a
 * release: the WIEDERGABE tiles rendered their labels in black-on-black and the chart bars drew
 * an invisible fill, while the one tile built from a literal `0xFFFFAA00L` looked perfectly fine.
 *
 * `toArgb()` returns a signed Int, so the mask is what stops a colour with alpha `FF` from
 * sign-extending into a nonsense 64-bit value.
 */
internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
