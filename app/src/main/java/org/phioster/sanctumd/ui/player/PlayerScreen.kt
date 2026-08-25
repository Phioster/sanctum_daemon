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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/** Whether the app is currently in picture-in-picture; MainActivity pushes the changes in. */
object PipState {
    val inPip = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** Shrink the player into a floating window. No-op below Android 8 or if the system refuses. */
private fun enterPip(activity: Activity?, aspect: Float) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val a = if (aspect > 0.4f && aspect < 2.4f) aspect else 16f / 9f
    runCatching {
        activity.enterPictureInPictureMode(
            android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational((a * 1000).toInt(), 1000))
                .build(),
        )
    }
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
    var videoAspect by remember { mutableStateOf(0f) } // video width/height, for the zoom-to-fill step
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var qualityLabel by remember { mutableStateOf("Auto") }
    // Last position the engine actually reported (> 0 = something is really decoding). Every report to
    // Jellyfin is made from this: a PositionTicks of 0 makes the server store PlaybackPositionTicks=0,
    // which silently drops the item out of "Continue Watching" — so a player that never started (or a
    // stream still loading) must never report.
    var lastGoodPos by remember { mutableStateOf(0L) }
    // Resume position we asked the engine to start at, and whether we've verified it took effect.
    var resumeTarget by remember { mutableStateOf(0L) }
    var resumeApplied by remember { mutableStateOf(true) }

    // The item actually on screen. Autoplay swaps this instead of tearing the overlay down, so the
    // engine, the immersive window flags and the surface all survive a jump to the next episode.
    var curItem by remember(itemId) { mutableStateOf(itemId) }
    var curTitle by remember(itemId) { mutableStateOf(title) }

    // Playback preferences.
    val audioLang by vm.audioLanguage.collectAsState()
    val subLang by vm.subtitleLanguage.collectAsState()
    val subMode by vm.subtitleMode.collectAsState()
    val subScalePref by vm.subtitleScale.collectAsState()
    val autoplayNext by vm.autoplayNext.collectAsState()
    val autoSkipSegments by vm.autoSkipSegments.collectAsState()
    val askResume by vm.askResume.collectAsState()
    val nextLeadSec by vm.nextEpisodeLead.collectAsState()

    // Live (per-playback) subtitle tuning, seeded from the saved preference.
    var subScale by remember(subScalePref) { mutableStateOf(subScalePref) }
    var subDelayMs by remember { mutableStateOf(0L) }

    // Resume prompt: set when the server has a position and the user wants to be asked.
    var resumeAsk by remember { mutableStateOf<Long?>(null) }
    // Intro/outro ranges and what the next episode is (both null until loaded, both optional).
    var segments by remember { mutableStateOf<List<org.phioster.sanctumd.net.MediaSegment>>(emptyList()) }
    var skipped by remember { mutableStateOf<Set<Int>>(emptySet()) } // segment indices already skipped/dismissed
    var nextUp by remember { mutableStateOf<org.phioster.sanctumd.net.NextEpisode?>(null) }
    var nextCardVisible by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableStateOf(-1) } // seconds left, <0 = no countdown (manual)
    // Sleep timer: minutes chosen, and the deadline it maps to (null = off). "episode" ends at EOF.
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepAtEpisodeEnd by remember { mutableStateOf(false) }
    var sleepDeadline by remember { mutableStateOf(0L) }
    var sleepFired by remember { mutableStateOf(false) }
    val inPip by PipState.inPip.collectAsState()

    // Brightness (window level, 0..1) + volume (0..1) driven by swipe gestures.
    var brightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f)
    }
    var volume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    // Transient HUD while adjusting: Pair(isBrightness, value 0..1); null = hidden.
    var adjustHud by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }
    // Transient "±10s" flash after a double-tap seek (0 = hidden).
    var seekHud by remember { mutableStateOf(0) }

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

    // Resolve the stream (or load the local file) and start playback. Re-runs when autoplay moves on
    // to the next episode, which is why it keys on [curItem] rather than the parameter.
    LaunchedEffect(curItem, localFileUri) {
        try {
            loadError = null
            segments = emptyList(); skipped = emptySet(); nextUp = null
            nextCardVisible = false; nextCountdown = -1
            if (localFileUri != null) {
                engine.prepare(localFileUri, isHls = false, startPositionMs = 0, headers = emptyMap())
            } else {
                val src = vm.jellyfinPlaybackSource(config, curItem)
                source = src
                // Asking about the resume point means starting at 0 and offering the jump — starting
                // mid-file and then rewinding would waste a seek and look broken.
                val ask = askResume && src.startPositionMs > 5_000
                val start = if (ask) 0L else src.startPositionMs
                resumeTarget = start
                resumeApplied = start <= 0
                engine.prepare(src.url, src.isHls, start, src.authHeaders)
                vm.jellyfinReportStart(config, src, start)
                if (ask) resumeAsk = src.startPositionMs
                // Both are optional extras: no plugin, no segments; not an episode, no next up.
                segments = runCatching { vm.jellyfinSegments(config, curItem) }.getOrDefault(emptyList())
                nextUp = runCatching { vm.jellyfinNextEpisode(config, curItem) }.getOrNull()
            }
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    /** Report the current item as stopped and move the overlay to [next]. */
    fun playNext(next: org.phioster.sanctumd.net.NextEpisode) {
        source?.let { src ->
            val pos = engine.snapshot().positionMs.takeIf { it > 0 } ?: lastGoodPos
            vm.jellyfinReportStoppedAsync(config, src, pos)
        }
        source = null
        lastGoodPos = 0
        nextCardVisible = false
        nextCountdown = -1
        curTitle = next.name
        curItem = next.id
    }

    // Poll player state for the UI (position, buffering, ended).
    LaunchedEffect(Unit) {
        while (true) {
            val snap = engine.snapshot()
            if (!scrubbing) state = snap
            if (snap.positionMs > 0) lastGoodPos = snap.positionMs
            // Belt-and-braces resume: once the file is loaded, if the engine is still sitting at the
            // start although we asked to resume, seek there once ourselves.
            if (!resumeApplied && snap.durationMs > 0) {
                if (snap.positionMs < 2_000) engine.seekTo(resumeTarget)
                resumeApplied = true
            }
            val st = engine.stats()
            if (st.width > 0 && st.height > 0) videoAspect = st.width.toFloat() / st.height
            if (state.ended) controlsVisible = true

            // Auto-skip intro/outro when the user asked for it (the button shows either way).
            if (autoSkipSegments && snap.positionMs > 0) {
                val i = segments.indexOfFirst { snap.positionMs in it.startMs until it.endMs }
                if (i >= 0 && i !in skipped) {
                    skipped = skipped + i
                    engine.seekTo(segments[i].endMs)
                }
            }

            // Sleep timer.
            if (!sleepFired && sleepDeadline > 0 && System.currentTimeMillis() >= sleepDeadline) {
                sleepFired = true; sleepDeadline = 0
                engine.pause(); controlsVisible = true
            }
            if (!sleepFired && sleepAtEpisodeEnd && snap.ended) {
                sleepFired = true; engine.pause()
            }

            // "Next episode" countdown once the outro starts (or the file ends).
            val outro = segments.firstOrNull { it.kind.equals("Outro", true) }
            // With a real outro segment the card follows the server's timing; without one it falls
            // back to a fixed lead before the end (the server may report no outro at all — the older
            // Intro Skipper route only ever returns intros).
            val nearEnd = snap.durationMs > 0 && snap.positionMs > 0 &&
                snap.positionMs >= (outro?.startMs ?: (snap.durationMs - nextLeadSec * 1000L))
            val next = nextUp
            if (next != null && !sleepAtEpisodeEnd && !sleepFired && (snap.ended || nearEnd)) {
                if (!nextCardVisible) {
                    nextCardVisible = true
                    nextCountdown = if (autoplayNext) (if (snap.ended) 5 else 15) else -1
                }
            } else if (!snap.ended && !nearEnd) {
                nextCardVisible = false
                nextCountdown = -1
            }
            delay(500)
        }
    }

    // Periodic progress report to Jellyfin (every 10s) so the resume position stays fresh.
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        while (true) {
            delay(10_000)
            val snap = engine.snapshot()
            // Still loading / nothing decoding: skip rather than report a 0 and erase the resume point.
            if (snap.positionMs <= 0) continue
            vm.jellyfinReportProgress(config, src, snap.positionMs, !snap.isPlaying)
        }
    }

    // Swiping the app away from recents kills the process with no dispose and no back press, so push
    // the current position when the app goes to the background — otherwise everything since the last
    // 10s ping is lost and the server is left with a session that never stopped.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, source) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val src = source
                val snap = engine.snapshot()
                val pos = if (snap.positionMs > 0) snap.positionMs else lastGoodPos
                if (src != null && pos > 0) vm.jellyfinReportProgressAsync(config, src, pos, !snap.isPlaying)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Once tracks load, apply the saved language preferences. In the default "forced" mode a file
    // without a forced track in that language ends up with subtitles OFF, deliberately.
    LaunchedEffect(curItem, localFileUri, audioLang, subLang, subMode) {
        repeat(40) { // ~20s window for tracks to appear
            if (engine.tracks(TrackKind.SUBTITLE).isNotEmpty() || engine.tracks(TrackKind.AUDIO).isNotEmpty()) {
                engine.applyTrackPreferences(audioLang, subLang, subMode)
                engine.setSubtitleScale(subScale)
                engine.setSubtitleDelay(subDelayMs)
                return@LaunchedEffect
            }
            delay(500)
        }
    }
    // Live subtitle tuning from the settings panel.
    LaunchedEffect(subScale) { engine.setSubtitleScale(subScale) }
    LaunchedEffect(subDelayMs) { engine.setSubtitleDelay(subDelayMs) }
    // Count the "next episode" card down once it appears.
    LaunchedEffect(nextCountdown) {
        if (nextCountdown > 0) {
            delay(1000)
            nextCountdown -= 1
        } else if (nextCountdown == 0) {
            nextUp?.let { playNext(it) }
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
    LaunchedEffect(seekHud) {
        if (seekHud != 0) { delay(600); seekHud = 0 }
    }

    // Re-resolve the stream at a new quality cap, resuming from the current position.
    fun changeQuality(label: String, cap: Int?) {
        if (localFileUri != null) return
        qualityLabel = label
        settingsOpen = false
        val resumeMs = engine.snapshot().positionMs.takeIf { it > 0 } ?: lastGoodPos
        scope.launch {
            try {
                val src = vm.jellyfinPlaybackSource(config, curItem, cap)
                source = src
                resumeTarget = resumeMs
                resumeApplied = resumeMs <= 0
                engine.prepare(src.url, src.isHls, resumeMs, src.authHeaders)
                engine.setSpeed(speed)
            } catch (t: Throwable) {
                loadError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    val leave = {
        val src = source
        if (src != null) {
            // Fall back to the last seen position, then to where we started — never report a 0 we
            // don't mean, or Jellyfin drops the item from Continue Watching.
            val snapPos = engine.snapshot().positionMs
            val pos = when {
                snapPos > 0 -> snapPos
                lastGoodPos > 0 -> lastGoodPos
                else -> src.startPositionMs
            }
            vm.jellyfinReportStoppedAsync(config, src, pos)
        }
        onClose() // removal triggers the DisposableEffect below, which releases the engine once
    }
    BackHandler {
        if (settingsOpen) settingsOpen = false else leave()
    }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    // Pinch-to-zoom: snaps to steps on release, stays centered (no free panning). Double-tap resets.
    var zoomScale by remember { mutableStateOf(1f) }
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
                    // YouTube-style: double-tap the sides to jump, the middle to reset the zoom.
                    onDoubleTap = { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> { engine.seekBy(-10_000); adjustHud = null; seekHud = -10 }
                            offset.x > size.width - third -> { engine.seekBy(10_000); adjustHud = null; seekHud = 10 }
                            else -> zoomScale = 1f
                        }
                    },
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

        // Ambient glow: the picture bleeds into the black bars, fed by Jellyfin's trickplay tiles.
        // Skipped for local files (no server to ask) and in PiP (no bars worth lighting up).
        if (!inPip && localFileUri == null) {
            AmbientGlow(
                config = config,
                itemId = curItem,
                positionMs = state.positionMs,
                videoRect = ambientVideoRect(boxSize.width, boxSize.height, videoAspect, zoomScale),
            )
        }

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

        if (seekHud != 0) {
            Text(
                if (seekHud > 0) "»  +10s" else "«  −10s",
                fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (seekHud > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 40.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // ── Skip intro / outro ──
        val activeSegment = segments.withIndex().firstOrNull { (i, seg) ->
            i !in skipped && state.positionMs in seg.startMs until seg.endMs
        }
        if (activeSegment != null && !inPip && nextCountdown < 0 && !nextCardVisible) {
            val (idx, seg) = activeSegment
            Text(
                if (seg.kind.equals("Outro", true)) "skip outro  »" else "skip intro  »",
                fontFamily = Mono, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (controlsVisible) 76.dp else 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MatrixGreen)
                    .clickable { skipped = skipped + idx; engine.seekTo(seg.endMs) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }

        // ── Next episode ──
        val next = nextUp
        if (nextCardVisible && next != null && !inPip) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (controlsVisible) 76.dp else 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE6000000))
                    .clickable { playNext(next) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    if (nextCountdown > 0) "NEXT IN ${nextCountdown}s" else "NEXT EPISODE",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(next.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (next.subtitle.isNotBlank()) {
                    Text(next.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("▶  play now", fontFamily = Mono, color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MatrixGreen).clickable { playNext(next) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("dismiss", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp,
                        modifier = Modifier.clickable { nextCardVisible = false; nextCountdown = -1; nextUp = null }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }

        // ── Resume prompt ──
        resumeAsk?.let { pos ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF0000000))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("continue watching?", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row {
                    Text("resume ${fmt(pos)}", fontFamily = Mono, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(MatrixGreen)
                            .clickable { engine.seekTo(pos); resumeTarget = pos; resumeApplied = true; resumeAsk = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("start over", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x33FFFFFF))
                            .clickable { resumeAsk = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }

        if (controlsVisible && !inPip) {
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
                Text(curTitle, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = { enterPip(activity, videoAspect) }) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = MatrixGreen)
                    }
                }
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
                PlayerSeekBar(
                    fraction = pos.toFloat() / dur,
                    scrubbing = scrubbing,
                    onScrub = { scrubbing = true; scrubPos = it },
                    onScrubEnd = { engine.seekTo((it * dur).toLong()); scrubbing = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(pos), fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp)
                    Text(fmt(state.durationMs), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        if (settingsOpen && !inPip) {
            SettingsPanel(
                engine = engine,
                currentSpeed = speed,
                currentQuality = qualityLabel,
                subtitleScale = subScale,
                subtitleDelayMs = subDelayMs,
                sleepMinutes = sleepMinutes,
                sleepAtEpisodeEnd = sleepAtEpisodeEnd,
                hasNextEpisode = nextUp != null,
                onSpeed = { sp -> speed = sp; engine.setSpeed(sp) },
                onQuality = { label, cap -> changeQuality(label, cap) },
                onSubtitleScale = { subScale = it },
                onSubtitleDelay = { subDelayMs = it },
                onSleep = { minutes, tillEnd ->
                    sleepMinutes = minutes
                    sleepAtEpisodeEnd = tillEnd
                    sleepFired = false
                    sleepDeadline = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                },
                onClose = { settingsOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (infoOpen && !inPip) {
            val playMethod = when {
                localFileUri != null -> "local file"
                source?.isHls == true -> "transcode (HLS)"
                source != null -> "direct play"
                else -> "…"
            }
            InfoPanel(
                vm = vm,
                config = config,
                itemId = curItem,
                title = curTitle,
                engine = engine,
                isLocal = localFileUri != null,
                playMethod = playMethod,
                segments = segments,
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
    segments: List<org.phioster.sanctumd.net.MediaSegment>,
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

        // What the server reported for intro/outro — the honest answer to "why did the skip button
        // (or the next-episode card) show up when it did".
        if (!isLocal) {
            Spacer(Modifier.height(16.dp))
            Text("SEGMENTS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (segments.isEmpty()) {
                Text(
                    "none reported — no media segments and no Intro Skipper data for this episode",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 15.sp,
                )
            } else {
                segments.forEach { seg ->
                    InfoStat(seg.kind.lowercase(), "${fmt(seg.startMs)} – ${fmt(seg.endMs)}")
                }
            }
        }
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
    subtitleScale: Float,
    subtitleDelayMs: Long,
    sleepMinutes: Int,
    sleepAtEpisodeEnd: Boolean,
    hasNextEpisode: Boolean,
    onSpeed: (Float) -> Unit,
    onQuality: (String, Int?) -> Unit,
    onSubtitleScale: (Float) -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onSleep: (minutes: Int, tillEpisodeEnd: Boolean) -> Unit,
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

        // Subtitle look & sync — live for this playback (the default size lives in settings).
        SettingsSection(Icons.Filled.Translate, "subtitle size") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sc ->
                    val on = kotlin.math.abs(sc - subtitleScale) < 0.01f
                    Text(
                        "${(sc * 100).roundToInt()}%",
                        fontFamily = Mono, fontSize = 12.sp, color = if (on) Black else MatrixGreen,
                        modifier = Modifier
                            .background(if (on) MatrixGreen else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onSubtitleScale(sc) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        SettingsSection(Icons.Filled.Subtitles, "subtitle delay") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("−0.5s", fontFamily = Mono, fontSize = 12.sp, color = MatrixGreen,
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable { onSubtitleDelay(subtitleDelayMs - 500) }.padding(horizontal = 10.dp, vertical = 5.dp))
                Text("%+.1fs".format(subtitleDelayMs / 1000f), fontFamily = Mono, fontSize = 12.sp, color = MatrixGreen,
                    modifier = Modifier.widthIn(min = 52.dp).clickable { onSubtitleDelay(0) })
                Text("+0.5s", fontFamily = Mono, fontSize = 12.sp, color = MatrixGreen,
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable { onSubtitleDelay(subtitleDelayMs + 500) }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }

        SettingsSection(Icons.Filled.Bedtime, "sleep timer") {
            SettingsRow("off", selected = sleepMinutes == 0 && !sleepAtEpisodeEnd) { onSleep(0, false) }
            listOf(15, 30, 45, 60).forEach { min ->
                SettingsRow("$min min", selected = sleepMinutes == min && !sleepAtEpisodeEnd) { onSleep(min, false) }
            }
            if (hasNextEpisode) {
                SettingsRow("end of episode", selected = sleepAtEpisodeEnd) { onSleep(0, true) }
            }
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
