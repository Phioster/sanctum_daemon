package org.phioster.sanctumd.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.TrickplayInfo
import org.phioster.sanctumd.net.jellyfinTrickplay
import org.phioster.sanctumd.net.jellyfinTrickplayTile

/**
 * Ambient glow in the letterbox/pillarbox bars, YouTube-style: the picture bleeds out of the frame
 * into the black bars as a soft wash of colour that follows the scene.
 *
 * libmpv renders straight into a SurfaceView, so the app never sees the video frames and can't
 * sample them. Instead the colours come from Jellyfin's **trickplay** tiles — the scrubbing preview
 * thumbnails. One sheet holds a few minutes of them, so a single small download covers a long
 * stretch and the glow still changes with the scene (one thumbnail per [TrickplayInfo.intervalMs],
 * typically 10s). Items the server has no trickplay for simply keep their black bars.
 *
 * The thumbnail is decoded at a fraction of its size and stretched over the whole player, which is
 * what makes the wash soft — no blur pass needed, so it costs nothing on old devices either.
 */
@Composable
fun AmbientGlow(
    config: ServiceConfig,
    itemId: String,
    positionMs: Long,
    videoRect: VideoRect?,
    modifier: Modifier = Modifier,
) {
    var info by remember(itemId) { mutableStateOf<TrickplayInfo?>(null) }
    var sheetIndex by remember(itemId) { mutableStateOf(-1) }
    var sheet by remember(itemId) { mutableStateOf<Bitmap?>(null) }
    var current by remember(itemId) { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember(itemId) { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember(itemId) { Animatable(1f) }

    LaunchedEffect(config.id, itemId) {
        if (itemId.isNotBlank()) info = jellyfinTrickplay(config, itemId)
    }

    val trick = info
    val thumbIndex = trick?.thumbIndexAt(positionMs) ?: -1
    LaunchedEffect(trick, thumbIndex) {
        if (trick == null || thumbIndex < 0) return@LaunchedEffect
        val wanted = trick.sheetIndexOf(thumbIndex)
        val tile = withContext<Bitmap?>(Dispatchers.IO) {
            if (wanted != sheetIndex || sheet == null) {
                val bytes = jellyfinTrickplayTile(config, itemId, trick, wanted) ?: return@withContext null
                sheet = decodeSheet(bytes, trick) ?: return@withContext null
                sheetIndex = wanted
            }
            sheet?.let { cropCell(it, trick, thumbIndex) }
        } ?: return@LaunchedEffect
        previous = current
        current = tile.asImageBitmap()
        fade.snapTo(0f)
        fade.animateTo(1f, tween(durationMillis = 900))
    }

    val image = current ?: return
    if (videoRect == null) return
    Canvas(modifier.fillMaxSize()) {
        // Everything is painted across the whole player and then clipped to the bars, so the wash
        // lines up with the frame it spills out of.
        clipRect(videoRect.left, videoRect.top, videoRect.right, videoRect.bottom, ClipOp.Difference) {
            previous?.let { drawStretched(it, 1f) }
            drawStretched(image, fade.value)
            // Fade towards the screen edges so the bars don't glow brighter than the film itself.
            drawRect(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    1f to Color(0xCC000000),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxOf(size.width, size.height) * 0.75f,
                ),
            )
        }
    }
}

private fun DrawScope.drawStretched(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0f) return
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
        alpha = alpha * 0.85f,
        filterQuality = FilterQuality.High,
    )
}

/**
 * Decodes a tile sheet down to roughly [TARGET_CELL_PX] per thumbnail. A full sheet is 100-odd
 * thumbnails; at full size that would be tens of megabytes of bitmap for something that ends up
 * as a colour wash.
 */
private fun decodeSheet(bytes: ByteArray, info: TrickplayInfo): Bitmap? {
    var sample = 1
    while (info.width / (sample * 2) >= TARGET_CELL_PX) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
}

/** The single thumbnail for [thumbIndex] out of an already downscaled [sheet]. */
private fun cropCell(sheet: Bitmap, info: TrickplayInfo, thumbIndex: Int): Bitmap? {
    val cellW = sheet.width / info.tileWidth
    val cellH = sheet.height / info.tileHeight
    if (cellW < 1 || cellH < 1) return null
    val (col, row) = info.cellOf(thumbIndex)
    val x = (col * cellW).coerceIn(0, sheet.width - cellW)
    val y = (row * cellH).coerceIn(0, sheet.height - cellH)
    return runCatching { Bitmap.createBitmap(sheet, x, y, cellW, cellH) }.getOrNull()
}

private const val TARGET_CELL_PX = 24
