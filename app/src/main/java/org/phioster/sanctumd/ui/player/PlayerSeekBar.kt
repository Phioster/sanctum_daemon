package org.phioster.sanctumd.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.phioster.sanctumd.ui.theme.MatrixGreen

/**
 * The player's seek bar: a thin line and a small dot, nothing else.
 *
 * Material3's Slider is deliberately not used here. Its thumb is a tall bar, its track carries a
 * stop indicator and gaps, and all of that sits on top of the film. A tap anywhere on the bar seeks
 * there, same as dragging.
 */
@Composable
fun PlayerSeekBar(
    fraction: Float,
    scrubbing: Boolean,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbDp by animateFloatAsState(if (scrubbing) 7f else 4.5f, label = "seekThumb")
    Box(
        modifier
            .height(TOUCH_HEIGHT)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var x = down.position.x
                    onScrub((x / width).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        x = change.position.x
                        onScrub((x / width).coerceIn(0f, 1f))
                        change.consume()
                    }
                    onScrubEnd((x / width).coerceIn(0f, 1f))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val line = 3.dp.toPx()
            val thumb = thumbDp.dp.toPx()
            // Keep the dot fully on screen at both ends instead of half-clipped.
            val left = thumb
            val right = size.width - thumb
            val x = left + (right - left).coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
            drawLine(
                MatrixGreen.copy(alpha = 0.22f), Offset(left, cy), Offset(right, cy),
                strokeWidth = line, cap = StrokeCap.Round,
            )
            drawLine(MatrixGreen, Offset(left, cy), Offset(x, cy), strokeWidth = line, cap = StrokeCap.Round)
            drawCircle(MatrixGreen, radius = thumb, center = Offset(x, cy))
        }
    }
}

private val TOUCH_HEIGHT = 28.dp
