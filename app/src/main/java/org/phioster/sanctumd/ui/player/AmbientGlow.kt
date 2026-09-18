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
import org.phioster.sanctumd.net.jellyfinAmbientArtwork
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
 * The thumbnail is decoded at a fraction of its size, averaged down to a handful of pixels and then
 * stretched over the whole player. That downscale *is* the blur — there is no blur pass to pay for
 * on old devices. Stopping part of the way down is not enough: at a few dozen pixels the scene
 * stays readable in the bars, shapes and all, which is the opposite of ambient.
 */
/** One frame of glow: the picture plus how strongly it may be painted (see [ambientAlpha]). */
private data class AmbientFrame(val image: ImageBitmap, val alpha: Float)

@Composable
fun AmbientGlow(
    config: ServiceConfig,
    itemId: String,
    positionMs: Long,
    videoRect: VideoRect?,
    modifier: Modifier = Modifier,
    onStatus: (String) -> Unit = {},
) {
    var info by remember(itemId) { mutableStateOf<TrickplayInfo?>(null) }
    var sheetIndex by remember(itemId) { mutableStateOf(-1) }
    var sheet by remember(itemId) { mutableStateOf<Bitmap?>(null) }
    var current by remember(itemId) { mutableStateOf<AmbientFrame?>(null) }
    var previous by remember(itemId) { mutableStateOf<AmbientFrame?>(null) }
    val fade = remember(itemId) { Animatable(1f) }

    LaunchedEffect(config.id, itemId) {
        if (itemId.isBlank()) return@LaunchedEffect
        onStatus("asking the server…")
        val loaded = jellyfinTrickplay(config, itemId)
        info = loaded
        // Reported in the player's info panel, so "why are my bars black" has an honest answer.
        if (loaded != null) {
            onStatus("${loaded.width}px previews")
            return@LaunchedEffect
        }
        // No trickplay: fall back to the item's own artwork. One colour for the whole film rather
        // than one per scene, and painted fainter — a white poster would otherwise light up the room.
        val art = withContext(Dispatchers.IO) {
            jellyfinAmbientArtwork(config, itemId)?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
        }
        if (art == null) {
            onStatus("no trickplay, no artwork")
        } else {
            current = AmbientFrame(soften(art).asImageBitmap(), ambientAlpha(POSTER_ALPHA, averageLuma(art)))
            onStatus("artwork colours · no trickplay")
        }
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
        }
        if (tile == null) {
            onStatus("${trick.width}px previews · tile $wanted unavailable")
            return@LaunchedEffect
        }
        onStatus("${trick.width}px previews · live")
        previous = current
        current = AmbientFrame(soften(tile).asImageBitmap(), ambientAlpha(SCENE_ALPHA, averageLuma(tile)))
        fade.snapTo(0f)
        fade.animateTo(1f, tween(durationMillis = 900))
    }

    val frame = current ?: return
    if (videoRect == null) return
    Canvas(modifier.fillMaxSize()) {
        // Everything is painted across the whole player and then clipped to the bars, so the wash
        // lines up with the frame it spills out of.
        clipRect(videoRect.left, videoRect.top, videoRect.right, videoRect.bottom, ClipOp.Difference) {
            previous?.let { drawStretched(it, 1f) }
            drawStretched(frame, fade.value)
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

private fun DrawScope.drawStretched(frame: AmbientFrame, fade: Float) {
    if (fade <= 0f) return
    drawImage(
        image = frame.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(frame.image.width, frame.image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
        alpha = fade * frame.alpha,
        filterQuality = FilterQuality.High,
    )
}

/**
 * Averages a frame down to [GLOW_LONG_EDGE_PX] so that stretching it back out leaves fields of
 * colour instead of a recognisable picture. Brightness is measured on the original, before this.
 *
 * Halving repeatedly instead of dropping straight to the target: one big bilinear step only looks
 * at 2x2 neighbourhoods, so most pixels never reach the result and the wash flickers as the scene
 * moves. Halving averages all of them. The bitmaps are a few dozen pixels, so this is free.
 */
private fun soften(bmp: Bitmap): Bitmap {
    val (w, h) = glowSampleSize(bmp.width, bmp.height, GLOW_LONG_EDGE_PX)
    if (w >= bmp.width || h >= bmp.height) return bmp
    var cur = bmp
    while (cur.width / 2 > w && cur.height / 2 > h) {
        val next = runCatching { Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true) }.getOrNull()
        if (next == null || next === cur) break
        cur = next
    }
    return runCatching { Bitmap.createScaledBitmap(cur, w, h, true) }.getOrDefault(cur)
}

/** Average brightness of a frame, 0 (black) to 1 (white). The bitmaps here are a few hundred pixels. */
private fun averageLuma(bmp: Bitmap): Float {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return 0.5f
    val px = IntArray(w * h)
    runCatching { bmp.getPixels(px, 0, w, 0, 0, w, h) }.getOrElse { return 0.5f }
    var sum = 0.0
    for (p in px) {
        sum += 0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)
    }
    return (sum / px.size / 255.0).toFloat()
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

/** Long edge a glow frame is averaged down to. Lower is more diffuse; this is the dial to turn. */
private const val GLOW_LONG_EDGE_PX = 10
private const val SCENE_ALPHA = 0.85f
private const val POSTER_ALPHA = 0.5f
