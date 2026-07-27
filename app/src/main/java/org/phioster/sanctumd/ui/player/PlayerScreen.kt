package org.phioster.sanctumd.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.PlaybackSource
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import kotlin.math.roundToInt

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun findActivity(context: Context): Activity? =
    generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
        .firstOrNull { it is Activity } as? Activity

/** Quality caps (streaming bitrate in bps); null = Auto (direct-play whenever possible). */
private val QUALITY_OPTIONS: List<Pair<String, Int?>> = listOf(
    "Auto" to null,
    "1080p" to 8_000_000,
    "720p" to 4_000_000,
    "480p" to 1_500_000,
)

/**
 * Full-screen video player overlay. Resolves the stream via [PlaybackSource], drives an
 * [ExoPlayerEngine], reports playback to Jellyfin (start/progress/stopped), and offers terminal-green
 * controls: transport, a settings panel (speed / audio / subtitles / quality) and vertical-swipe
 * gestures for brightness (left half) and volume (right half). Presented on top of the Jellyfin screen.
 */
@UnstableApi
@Composable
internal fun PlayerScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    itemId: String,
    title: String,
    onClose: () -> Unit,
    /** When set, plays this local file offline (no PlaybackInfo/reporting). */
    localFileUri: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // libmpv plays everything (any codec, PGS/ASS subs, no server transcode); if its native lib can't
    // load, fall back to ExoPlayer so playback still works.
    val engine: MediaPlayerEngine = remember {
        runCatching { MpvPlayerEngine(context) }.getOrElse { ExoPlayerEngine(context) }
    }
    val activity = remember(context) { findActivity(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val swipeMagnitude by vm.playerSwipeMagnitude.collectAsState()
    val swipeMargin by vm.playerSwipeMargin.collectAsState()

    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(PlaybackState()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var qualityLabel by remember { mutableStateOf("Auto") }

    // Brightness (window level, 0..1) + volume (0..1) driven by swipe gestures.
    var brightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f)
    }
    var volume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    // Transient HUD while adjusting: Pair(isBrightness, value 0..1); null = hidden.
    var adjustHud by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }

    fun applyBrightness() {
        val w = activity?.window ?: return
        val lp = w.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
        w.attributes = lp
    }
    fun applyVolume() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).roundToInt().coerceIn(0, maxVol), 0)
    }

    // Immersive full-screen + landscape while the player is up; restored on leave.
    DisposableEffect(Unit) {
        val window = activity?.window
        val prevOrientation = activity?.requestedOrientation
        val prevCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window?.attributes?.layoutInDisplayCutoutMode else null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Let the window use the full display incl. the camera cutout, so a zoomed video can fill
            // right to the edge. The base video is padded away from the cutout below (YouTube-like).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                window.attributes = lp
            }
            // NB: don't touch decorFitsSystemWindows — the app runs edge-to-edge (enableEdgeToEdge),
            // so the Scaffolds pad for the status bar themselves. Flipping it here (and back to true
            // on dispose) made the decor consume the insets → 0 status-bar inset → the top bar slid
            // up under the status bar after leaving the player. Only hide/show the bars.
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                // Release our brightness override back to the system.
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prevCutoutMode != null) {
                    lp.layoutInDisplayCutoutMode = prevCutoutMode
                }
                window.attributes = lp
            }
            prevOrientation?.let { activity.requestedOrientation = it }
        }
    }

    // Resolve the stream (or load the local file) and start playback.
    LaunchedEffect(itemId, localFileUri) {
        try {
            if (localFileUri != null) {
                engine.prepare(localFileUri, isHls = false, startPositionMs = 0, headers = emptyMap())
            } else {
                val src = vm.jellyfinPlaybackSource(config, itemId)
                source = src
                engine.prepare(src.url, src.isHls, src.startPositionMs, src.authHeaders)
                vm.jellyfinReportStart(config, src, src.startPositionMs)
            }
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    // Poll player state for the UI (position, buffering, ended).
    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) state = engine.snapshot()
            val st = engine.stats()
            if (st.width > 0 && st.height > 0) videoAspect = st.width.toFloat() / st.height
            if (state.ended) controlsVisible = true
            delay(500)
        }
    }

    // Periodic progress report to Jellyfin (every 10s) so the resume position stays fresh.
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        while (true) {
            delay(10_000)
            val snap = engine.snapshot()
            vm.jellyfinReportProgress(config, src, snap.positionMs, !snap.isPlaying)
        }
    }

    // Once tracks load, default subtitles to forced German (fallback: normal German), else leave as-is.
    LaunchedEffect(itemId, localFileUri) {
        repeat(40) { // ~20s window for tracks to appear
            if (engine.tracks(TrackKind.SUBTITLE).isNotEmpty()) {
                engine.autoSelectSubtitle(listOf("de", "deu", "ger", "de-de"))
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    // Auto-hide controls a few seconds after they appear (while playing, settings closed).
    LaunchedEffect(controlsVisible, state.isPlaying, settingsOpen) {
        if (controlsVisible && state.isPlaying && !settingsOpen) { delay(3500); controlsVisible = false }
    }
    // Auto-hide the swipe HUD shortly after the last adjustment.
    LaunchedEffect(adjustHud) {
        if (adjustHud != null) { delay(700); adjustHud = null }
    }

    // Re-resolve the stream at a new quality cap, resuming from the current position.
    fun changeQuality(label: String, cap: Int?) {
        if (localFileUri != null) return
        qualityLabel = label
        settingsOpen = false
        val resumeMs = engine.snapshot().positionMs
        scope.launch {
            try {
                val src = vm.jellyfinPlaybackSource(config, itemId, cap)
                source = src
                engine.prepare(src.url, src.isHls, resumeMs, src.authHeaders)
                engine.setSpeed(speed)
            } catch (t: Throwable) {
                loadError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    val leave = {
        val src = source
        if (src != null) vm.jellyfinReportStoppedAsync(config, src, engine.snapshot().positionMs)
        onClose() // removal triggers the DisposableEffect below, which releases the engine once
    }
    BackHandler {
        if (settingsOpen) settingsOpen = false else leave()
    }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    // Pinch-to-zoom: snaps to steps on release, stays centered (no free panning). Double-tap resets.
    var zoomScale by remember { mutableStateOf(1f) }
    var videoAspect by remember { mutableStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Steps: fit (1x) → "fill" (scales the video to cover the whole screen, cropping the overflow —
    // YouTube-style, for content whose aspect doesn't match the phone) → one bigger step. When the
    // video aspect isn't known yet, fall back to plain multiples.
    val zoomStops = remember(videoAspect, boxSize) {
        if (videoAspect > 0f && boxSize.width > 0 && boxSize.height > 0) {
            val screenAspect = boxSize.width.toFloat() / boxSize.height
            val fill = maxOf(videoAspect / screenAspect, screenAspect / videoAspect).coerceIn(1f, 4f)
            buildList {
                add(1f)
                if (fill > 1.02f) add(fill)
                add((fill * 2f).coerceAtMost(4f))
            }.distinct().sorted().toFloatArray()
        } else {
            floatArrayOf(1f, 2f, 3f, 4f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .background(Color.Black) // pure black bars around the video (matches the letterbox)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (settingsOpen) settingsOpen = false else controlsVisible = !controlsVisible },
                    onDoubleTap = { zoomScale = 1f },
                )
            }
            // One unified gesture loop: 2 fingers = pinch zoom + pan; 1 finger (overlay hidden) =
            // brightness (left) / volume (right). Handling both here keeps them from fighting.
            .pointerInput(controlsVisible, settingsOpen, swipeMagnitude, swipeMargin) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val onLeft = down.position.x < size.width / 2f
                    val marginPx = size.height * swipeMargin
                    val dragAllowed = !(controlsVisible || settingsOpen) && down.position.y in marginPx..(size.height - marginPx)
                    var pinching = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            pinching = true
                            zoomScale = (zoomScale * event.calculateZoom()).coerceIn(1f, 4f)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (!pinching && dragAllowed && pressed == 1) {
                            val change = event.changes.firstOrNull { it.pressed }
                            val dy = change?.positionChange()?.y ?: 0f
                            if (dy != 0f) {
                                val frac = dy / size.height.coerceAtLeast(1) * swipeMagnitude
                                if (onLeft) {
                                    brightness = (brightness - frac).coerceIn(0.01f, 1f); applyBrightness(); adjustHud = true to brightness
                                } else {
                                    volume = (volume - frac).coerceIn(0f, 1f); applyVolume(); adjustHud = false to volume
                                }
                                change?.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    // Snap the zoom to the nearest fixed step on release, so it "latches" instead of
                    // sitting at an arbitrary level.
                    if (pinching) {
                        zoomScale = zoomStops.minByOrNull { kotlin.math.abs(it - zoomScale) } ?: 1f
                    }
                }
            },
    ) {
        engine.VideoSurface(
            // Fills the full screen (aspect-fit → full phone width for wide video) at 1x; pinch snaps
            // to fixed zoom steps, video stays centered (no free panning).
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoomScale; scaleY = zoomScale
            },
        )

        if ((source == null && loadError == null && localFileUri == null) || (state.isBuffering && loadError == null)) {
            CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.align(Alignment.Center))
        }
        loadError?.let { err ->
            Text(
                "playback error: $err",
                fontFamily = Mono, color = Color(0xFFFF5555), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        // Swipe HUD (brightness / volume).
        adjustHud?.let { (isBright, value) ->
            Row(
                Modifier.align(Alignment.Center)
                    .background(Color(0xB3000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (isBright) Icons.Filled.BrightnessMedium else Icons.Filled.VolumeUp,
                    contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(22.dp),
                )
                Text("${(value * 100).roundToInt()}%", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (controlsVisible) {
            // Top scrim + bar: back, title, settings gear.
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { leave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen)
                }
                Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { infoOpen = true; controlsVisible = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = MatrixGreen)
                }
                if (localFileUri == null) {
                    IconButton(onClick = { settingsOpen = true; controlsVisible = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MatrixGreen)
                    }
                }
            }

            // Center transport: -10s / play-pause / +10s.
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { engine.seekBy(-10_000) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10s", tint = MatrixGreen, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { engine.togglePlay(); state = engine.snapshot() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause", tint = MatrixGreen, modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = { engine.seekBy(10_000) }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = MatrixGreen, modifier = Modifier.size(40.dp))
                }
            }

            // Bottom scrim + seek bar + times.
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val dur = state.durationMs.coerceAtLeast(1)
                val pos = if (scrubbing) (scrubPos * dur).toLong() else state.positionMs
                Slider(
                    value = pos.toFloat().coerceIn(0f, dur.toFloat()),
                    onValueChange = { scrubbing = true; scrubPos = it / dur.toFloat() },
                    onValueChangeFinished = { engine.seekTo((scrubPos * dur).toLong()); scrubbing = false },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MatrixGreen, activeTrackColor = MatrixGreen,
                        inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp)
                    Text(fmt(state.durationMs), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        if (settingsOpen) {
            SettingsPanel(
                engine = engine,
                currentSpeed = speed,
                currentQuality = qualityLabel,
                onSpeed = { s -> speed = s; engine.setSpeed(s) },
                onQuality = { label, cap -> changeQuality(label, cap) },
                onClose = { settingsOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (infoOpen) {
            val playMethod = when {
                localFileUri != null -> "local file"
                source?.isHls == true -> "transcode (HLS)"
                source != null -> "direct play"
                else -> "…"
            }
            InfoPanel(
                vm = vm,
                config = config,
                itemId = itemId,
                title = title,
                engine = engine,
                isLocal = localFileUri != null,
                playMethod = playMethod,
                onClose = { infoOpen = false },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

/** Left-side info panel: media details (overview / facts / genres) + live playback metrics
 *  (resolution, codec, bitrate, buffer fill, decode). Opened from the ⓘ button in the top bar. */
@Composable
private fun InfoPanel(
    vm: DashboardViewModel,
    config: ServiceConfig,
    itemId: String,
    title: String,
    engine: MediaPlayerEngine,
    isLocal: Boolean,
    playMethod: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<org.phioster.sanctumd.model.JellyMediaDetail?>(null) }
    var stats by remember { mutableStateOf(engine.stats()) }
    LaunchedEffect(itemId, isLocal) {
        if (!isLocal) detail = runCatching { vm.jellyfinMediaDetail(config, itemId) }.getOrNull()
    }
    // Poll live metrics ~1×/s while the panel is open.
    LaunchedEffect(Unit) {
        while (true) { stats = engine.stats(); delay(1000) }
    }

    Column(
        modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xE6000000))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            // Swallow taps so they don't reach the video underneath (toggle controls / seek).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("info", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Check, contentDescription = "Close", tint = MatrixGreen) }
        }

        val d = detail
        Text(d?.name ?: title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (d != null && d.subtitle.isNotBlank()) {
            Text(d.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        if (d != null) {
            val facts = listOfNotNull(
                d.facts.firstOrNull { it.first.equals("year", true) }?.second,
                d.facts.firstOrNull { it.first.equals("runtime", true) }?.second,
                d.facts.firstOrNull { it.first.equals("rating", true) || it.first.equals("community rating", true) }?.second,
            )
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(facts.joinToString(" · "), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            if (d.genres.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(d.genres, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            if (d.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("PLAYBACK", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val res = if (stats.width > 0 && stats.height > 0) "${stats.width}×${stats.height}" else "—"
        val video = listOfNotNull(stats.videoCodec.ifBlank { null }, if (stats.fps > 0) "%.0f fps".format(stats.fps) else null).joinToString(" · ").ifBlank { "—" }
        InfoStat("method", playMethod)
        InfoStat("resolution", res)
        InfoStat("video", video)
        InfoStat("audio", stats.audioCodec.ifBlank { "—" })
        InfoStat("bitrate", if (stats.bitrateKbps > 0) "${stats.bitrateKbps} kbps" else "—")
        InfoStat("buffer", "${stats.bufferedPercent}%")
        if (stats.hwDecode.isNotBlank()) InfoStat("decode", "hw · ${stats.hwDecode}")
    }
}

@Composable
private fun InfoStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(value, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Right-side settings panel: playback speed, audio & subtitle tracks, quality. */
@UnstableApi
@Composable
private fun SettingsPanel(
    engine: MediaPlayerEngine,
    currentSpeed: Float,
    currentQuality: String,
    onSpeed: (Float) -> Unit,
    onQuality: (String, Int?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read tracks once when the panel opens (currentTracks are stable while paused/playing).
    val audioTracks = remember { engine.tracks(TrackKind.AUDIO) }
    val subtitleTracks = remember { engine.tracks(TrackKind.SUBTITLE) }
    // Local selection state so ticks update immediately.
    var audioSel by remember { mutableStateOf(audioTracks.firstOrNull { it.selected }?.id) }
    var subSel by remember { mutableStateOf(subtitleTracks.firstOrNull { it.selected }?.id) } // null = off

    Column(
        modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xF0000000))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Speed — chips scroll horizontally so none get clipped.
        SettingsSection(Icons.Filled.Speed, "speed") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PLAYBACK_SPEEDS.forEach { s ->
                    val on = kotlin.math.abs(s - currentSpeed) < 0.01f
                    Text(
                        "${s}x",
                        fontFamily = Mono, fontSize = 12.sp,
                        color = if (on) Black else MatrixGreen,
                        modifier = Modifier
                            .background(if (on) MatrixGreen else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onSpeed(s) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        SettingsSection(Icons.Filled.HighQuality, "quality") {
            QUALITY_OPTIONS.forEach { (label, cap) ->
                SettingsRow(label, selected = label == currentQuality) { onQuality(label, cap) }
            }
        }

        SettingsSection(Icons.Filled.Audiotrack, "audio") {
            if (audioTracks.isEmpty()) {
                SettingsPlaceholder()
            } else {
                audioTracks.forEach { t ->
                    SettingsRow(t.label, selected = t.id == audioSel) {
                        audioSel = t.id; engine.selectTrack(TrackKind.AUDIO, t.id)
                    }
                }
            }
        }

        SettingsSection(Icons.Filled.Subtitles, "subtitles") {
            SettingsRow("off", selected = subSel == null) { subSel = null; engine.selectTrack(TrackKind.SUBTITLE, null) }
            subtitleTracks.forEach { t ->
                SettingsRow(t.label, selected = t.id == subSel) {
                    subSel = t.id; engine.selectTrack(TrackKind.SUBTITLE, t.id)
                }
            }
            if (subtitleTracks.isEmpty()) SettingsPlaceholder()
        }

        Text("close", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp,
            modifier = Modifier.clickable { onClose() }.padding(vertical = 6.dp))
    }
}

/** A settings category: header + its rows, tightly packed. Spacing between sections comes from the
 *  parent Column's arrangement, so it stays uniform. */
@Composable
private fun SettingsSection(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SettingsHeader(icon, label)
        content()
    }
}

@Composable
private fun SettingsHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MatrixGreen.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(label.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsPlaceholder() {
    Text("none available", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 7.dp))
}

@Composable
private fun SettingsRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = "selected", tint = MatrixGreen, modifier = Modifier.size(18.dp))
    }
}
