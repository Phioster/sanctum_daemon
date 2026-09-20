package org.phioster.sanctumd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * A [Color] as the packed `0xAARRGGBB` value the chart models carry.
 *
 * `color.value.toLong()` looks right and is not: that is Compose's 64-bit form including the
 * colour space, and it comes back out of `Color(Long)` as near-black. The mask stops `toArgb()`'s
 * signed Int from sign-extending when alpha is `FF`.
 */
internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
