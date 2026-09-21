package org.phioster.sanctumd.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.phioster.sanctumd.data.DashboardStore
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.MediaSegment
import org.phioster.sanctumd.net.NextEpisode
import org.phioster.sanctumd.net.PlaybackSource
import org.phioster.sanctumd.net.jellyfinMediaSegments
import org.phioster.sanctumd.net.jellyfinNextEpisode
import org.phioster.sanctumd.net.jellyfinPlaybackSource
import org.phioster.sanctumd.net.jellyfinReportProgress
import org.phioster.sanctumd.net.jellyfinReportStart
import org.phioster.sanctumd.net.jellyfinReportStopped
import org.phioster.sanctumd.ui.player.ExoPlayerEngine
import org.phioster.sanctumd.ui.player.MediaPlayerEngine
import org.phioster.sanctumd.ui.player.MpvPlayerEngine
import org.phioster.sanctumd.ui.player.PlaybackState
import org.phioster.sanctumd.ui.player.PlaybackStats
import org.phioster.sanctumd.ui.player.TrackKind
import org.phioster.sanctumd.ui.player.TrackOption
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_JUMP_MS = 30_000L
private const val CONTROLS_TIMEOUT_MS = 5_000L

/** The two focusable strips of the control bar. */
private const val STRIP_SCRUB = 0
private const val STRIP_BUTTONS = 1

/** Fire-and-forget scope for the final "stopped" report, which must outlive this composable. */
private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun findActivity(context: Context): Activity? {
    var c = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** A button on the control bar's lower strip. */
private data class BarButton(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/** One line in the track menu. Headers are shown but skipped when moving the selection. */
private data class TvMenuEntry(
    val label: String,
    val header: Boolean = false,
    val selected: Boolean = false,
    val onSelect: () -> Unit = {},
)

/**
 * Full-screen playback for the TV.
 *
 * Uses the same [MpvPlayerEngine] as the phone — which is the entire point of this client: libmpv
 * decodes in software whatever the TV stick's own codecs refuse (DTS/TrueHD audio, VC-1, 10-bit
 * HEVC, PGS/ASS subtitles), so the server never has to transcode. It is created with the low-power
 * tuning profile, because a stick's GPU cannot afford mpv's default rendering quality.
 * [ExoPlayerEngine] remains the fallback if the native library can't load on this ABI.
 *
 * **Input model.** With the overlay hidden, a key press does one thing: it brings the overlay up.
 * Nothing seeks, nothing toggles — a stray press on a remote should never move the film. With the
 * overlay up there are two strips: the progress bar, where left/right seek, and a row of buttons,
 * where left/right move between them. Up and down switch strips. That is the whole grammar, and it
 * keeps seeking somewhere you have deliberately navigated to.
 */
@UnstableApi
@Composable
internal fun TvPlayerScreen(
    config: ServiceConfig,
    itemId: String,
    title: String,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember { DashboardStore(context) }

    // The output mode is fixed when the engine is constructed, so it has to be known before the
    // first frame — hence the blocking read, which also serves as the collect's initial value. Given
    // a plain `false` initial, the engine would be built once wrongly and then immediately rebuilt,
    // restarting playback in front of the user. Toggling it later *does* rebuild, deliberately.
    val storedDirectOutput = remember { runBlocking { store.tvDirectOutput.first() } }
    val directOutput by store.tvDirectOutput.collectAsState(storedDirectOutput)

    // libmpv first — that is the whole reason this client exists. ExoPlayer only steps in when the
    // native library refuses to load on this device's ABI.
    val engine: MediaPlayerEngine = remember(directOutput) {
        runCatching { MpvPlayerEngine(context, tvTuning = true, directOutput = directOutput) }
            .getOrElse { ExoPlayerEngine(context) }
    }

    var curItem by remember(itemId) { mutableStateOf(itemId) }
    var curTitle by remember(itemId) { mutableStateOf(title) }
    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var state by remember { mutableStateOf(PlaybackState()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var lastGoodPos by remember { mutableStateOf(0L) }

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsNonce by remember { mutableStateOf(0) }
    var strip by remember { mutableStateOf(STRIP_BUTTONS) }
    var buttonIndex by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableStateOf(0) }
    var menuNonce by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    var segments by remember { mutableStateOf<List<MediaSegment>>(emptyList()) }
    var skipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var nextEpisode by remember { mutableStateOf<NextEpisode?>(null) }

    var infoOpen by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(PlaybackStats()) }
    var displayInfo by remember { mutableStateOf(DisplayModeInfo()) }
    var videoAspect by remember { mutableStateOf(0f) }

    val audioLang by store.audioLanguage.collectAsState("de")
    val subLang by store.subtitleLanguage.collectAsState("de")
    val subMode by store.subtitleMode.collectAsState("forced")
    val autoplayNext by store.autoplayNext.collectAsState(true)
    val matchRefresh by store.tvMatchRefresh.collectAsState(true)
    val scope = rememberCoroutineScope()

    val keyFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val activity = findActivity(context)
        val controller = activity?.window?.let {
            androidx.core.view.WindowInsetsControllerCompat(it, view).apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            view.keepScreenOn = false
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity?.let { clearPreferredRefreshRate(it) }
        }
    }

    DisposableEffect(engine) { onDispose { engine.release() } }

    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    // Resolve the stream and start. Re-runs when autoplay advances to the next episode.
    LaunchedEffect(curItem, engine) {
        loadError = null
        segments = emptyList()
        skipped = emptySet()
        nextEpisode = null
        runCatching {
            val src = jellyfinPlaybackSource(config, curItem)
            source = src

            // Switch the panel *before* the decoder gets a surface. Doing this mid-stream tears the
            // surface out from under MediaCodec — which corrupts the picture outright — and the
            // resulting configuration change restarts the activity, so playback begins all over
            // again. The frame rate comes from the server precisely so it is known this early.
            val activity = findActivity(context)
            if (matchRefresh && activity != null && src.videoFps > 0f) {
                displayInfo = applyRefreshRateFor(activity, src.videoFps)
                if (displayInfo.switched) delay(1500) // let the panel finish re-syncing
                displayInfo = displayInfo.copy(currentHz = currentRefreshRate(activity))
            }

            engine.prepare(src.url, src.isHls, src.startPositionMs, src.authHeaders)
            jellyfinReportStart(config, src, src.startPositionMs)
        }.onFailure { loadError = it.message ?: it.javaClass.simpleName }
        segments = runCatching { jellyfinMediaSegments(config, curItem) }.getOrDefault(emptyList())
        nextEpisode = runCatching { jellyfinNextEpisode(config, curItem) }.getOrNull()
    }

    LaunchedEffect(Unit) {
        while (true) {
            val snap = engine.snapshot()
            state = snap
            if (snap.positionMs > 0) lastGoodPos = snap.positionMs
            if (snap.ended) controlsVisible = true
            if (infoOpen) stats = engine.stats()
            videoAspect = engine.videoAspect()
            delay(500)
        }
    }

    // Progress ping so the resume point and Continue Watching stay current. A reported 0 would erase
    // the server's resume position, so a not-yet-decoding player stays silent instead.
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        while (true) {
            delay(10_000)
            val snap = engine.snapshot()
            if (snap.positionMs <= 0) continue
            jellyfinReportProgress(config, src, snap.positionMs, !snap.isPlaying)
        }
    }

    LaunchedEffect(curItem, audioLang, subLang, subMode) {
        repeat(40) {
            if (engine.tracks(TrackKind.AUDIO).isNotEmpty() || engine.tracks(TrackKind.SUBTITLE).isNotEmpty()) {
                engine.applyTrackPreferences(audioLang, subLang, subMode)
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    // The box being switched off or the app backgrounded gives no dispose — push the position now.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, source) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val src = source
                val snap = engine.snapshot()
                val pos = if (snap.positionMs > 0) snap.positionMs else lastGoodPos
                if (src != null && pos > 0) {
                    reportScope.launch { runCatching { jellyfinReportProgress(config, src, pos, true) } }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun reportStoppedNow() {
        val src = source ?: return
        val pos = engine.snapshot().positionMs.takeIf { it > 0 } ?: lastGoodPos
        if (pos > 0) reportScope.launch { runCatching { jellyfinReportStopped(config, src, pos) } }
    }

    fun leave() {
        reportStoppedNow()
        onLeave()
    }

    fun playNext(next: NextEpisode) {
        reportStoppedNow()
        source = null
        lastGoodPos = 0
        curTitle = next.name
        curItem = next.id
    }

    fun poke() { controlsVisible = true; controlsNonce++ }

    LaunchedEffect(controlsNonce, controlsVisible, menuOpen, state.isPlaying) {
        if (!controlsVisible || menuOpen || !state.isPlaying) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    LaunchedEffect(toast) {
        if (toast != null) { delay(2500); toast = null }
    }

    LaunchedEffect(state.ended) {
        val next = nextEpisode
        if (state.ended && autoplayNext && next != null) {
            toast = "next episode: ${next.name}"
            delay(3000)
            if (engine.snapshot().ended) playNext(next)
        }
    }

    val activeSegment = segments.indexOfFirst {
        state.positionMs in it.startMs until it.endMs && it.kind.equals("Intro", true)
    }

    val buttons: List<BarButton> = buildList {
        add(
            BarButton(
                if (state.isPlaying) "pause" else "play",
                if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
            ) { engine.togglePlay() },
        )
        add(BarButton("audio & subtitles", AppIcons.Subtitles) { menuNonce++; menuOpen = true })
        if (activeSegment >= 0 && activeSegment !in skipped) {
            add(BarButton("skip intro", AppIcons.Next) {
                skipped = skipped + activeSegment
                engine.seekTo(segments[activeSegment].endMs)
            })
        }
        nextEpisode?.let { next -> add(BarButton("next episode", AppIcons.Next) { playNext(next) }) }
        add(BarButton(if (infoOpen) "hide stats" else "stats", AppIcons.Info) {
            infoOpen = !infoOpen
            if (infoOpen) stats = engine.stats()
        })
        add(BarButton("leave", AppIcons.Cancel) { leave() })
    }
    val safeButtonIndex = buttonIndex.coerceIn(0, buttons.lastIndex)

    // Track lists are read when the menu is opened, not on every recomposition — hence the nonce: a
    // list that reshuffled under the selection would be unusable.
    val menuEntries: List<TvMenuEntry> = remember(menuNonce, matchRefresh, directOutput) {
        buildList {
            add(TvMenuEntry("audio", header = true))
            val audio = engine.tracks(TrackKind.AUDIO)
            if (audio.isEmpty()) add(TvMenuEntry("  no audio track", header = true))
            audio.forEach { t: TrackOption ->
                add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.AUDIO, t.id) })
            }
            add(TvMenuEntry("untertitel", header = true))
            if (directOutput) add(TvMenuEntry("  (direct output draws no subtitles)", header = true))
            val subs = engine.tracks(TrackKind.SUBTITLE)
            add(TvMenuEntry("  aus", selected = subs.none { it.selected }) { engine.selectTrack(TrackKind.SUBTITLE, null) })
            subs.forEach { t: TrackOption ->
                add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.SUBTITLE, t.id) })
            }
            add(TvMenuEntry("bild", header = true))
            add(TvMenuEntry("  Bildrate an Film anpassen", selected = matchRefresh) {
                scope.launch { store.setTvMatchRefresh(!matchRefresh) }
            })
            add(TvMenuEntry("  direct output (recommended) — no subtitles", selected = directOutput) {
                scope.launch { store.setTvDirectOutput(!directOutput) }
            })
        }
    }

    LaunchedEffect(menuOpen, menuEntries) {
        if (!menuOpen) return@LaunchedEffect
        val active = menuEntries.indexOfFirst { !it.header && it.selected }
        menuIndex = if (active >= 0) active else menuEntries.indexOfFirst { !it.header }.coerceAtLeast(0)
    }

    fun moveMenu(delta: Int) {
        if (menuEntries.isEmpty()) return
        var i = menuIndex
        repeat(menuEntries.size) {
            i = (i + delta + menuEntries.size) % menuEntries.size
            if (!menuEntries[i].header) { menuIndex = i; return }
        }
    }

    BackHandler {
        when (backAction(menuOpen, infoOpen, controlsVisible, state.isPlaying)) {
            BackAction.CLOSE_MENU -> menuOpen = false
            BackAction.CLOSE_INFO -> infoOpen = false
            BackAction.HIDE_CONTROLS -> controlsVisible = false
            BackAction.LEAVE -> leave()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A television has no finger, but the phone this gets tested on does -- and once the
            // overlay has hidden itself there is otherwise no way back to it without a D-pad.
            .pointerInput(Unit) { detectTapGestures { poke() } }
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Dedicated media keys act immediately, overlay or not — that is what they are for.
                when (ev.key) {
                    Key.MediaPlayPause -> { engine.togglePlay(); poke(); return@onPreviewKeyEvent true }
                    Key.MediaPlay -> { engine.play(); poke(); return@onPreviewKeyEvent true }
                    Key.MediaPause -> { engine.pause(); poke(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { engine.seekBy(SEEK_JUMP_MS); poke(); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { engine.seekBy(-SEEK_JUMP_MS); poke(); return@onPreviewKeyEvent true }
                    Key.MediaStop -> { leave(); return@onPreviewKeyEvent true }
                    Key.MediaNext -> { nextEpisode?.let { playNext(it) }; return@onPreviewKeyEvent true }
                    else -> Unit
                }

                if (menuOpen) {
                    when (ev.key) {
                        Key.DirectionUp -> moveMenu(-1)
                        Key.DirectionDown -> moveMenu(1)
                        Key.DirectionLeft -> menuOpen = false
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            menuEntries.getOrNull(menuIndex)?.onSelect?.invoke()
                            menuOpen = false
                            poke()
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }

                // Overlay hidden: the first press only wakes it. Nothing may move the film by accident.
                // Back is the exception. Swallowed here it woke the overlay, the BackHandler then
                // hid it again, and the two took turns: a running film could not be left at all.
                if (!controlsVisible && ev.key != Key.Back) {
                    poke()
                    return@onPreviewKeyEvent true
                }

                when (ev.key) {
                    Key.DirectionUp -> { strip = STRIP_SCRUB; poke() }
                    Key.DirectionDown -> { strip = STRIP_BUTTONS; poke() }
                    Key.DirectionLeft -> {
                        if (strip == STRIP_SCRUB) engine.seekBy(-SEEK_STEP_MS)
                        else buttonIndex = (safeButtonIndex - 1).coerceAtLeast(0)
                        poke()
                    }
                    Key.DirectionRight -> {
                        if (strip == STRIP_SCRUB) engine.seekBy(SEEK_STEP_MS)
                        else buttonIndex = (safeButtonIndex + 1).coerceAtMost(buttons.lastIndex)
                        poke()
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (strip == STRIP_SCRUB) engine.togglePlay()
                        else buttons.getOrNull(safeButtonIndex)?.onClick?.invoke()
                        poke()
                    }
                    Key.Menu -> { menuNonce++; menuOpen = true; poke() }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        // The zero-copy output stretches the picture to whatever shape the surface has, so the
        // surface carries the aspect ratio and the black box behind it supplies the bars.
        engine.VideoSurface(
            if (videoAspect > 0f) Modifier.aspectRatio(videoAspect).align(Alignment.Center)
            else Modifier.fillMaxSize(),
        )

        if (loadError != null) {
            Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.85f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("playback failed", color = ErrRed, fontFamily = Mono, fontSize = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(loadError ?: "", color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Text("back button to leave", color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 12.sp)
                }
            }
        } else if (state.durationMs <= 0 && state.positionMs <= 0) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("buffering…", color = MatrixGreen, fontFamily = Mono, fontSize = 18.sp)
            }
        }

        if (controlsVisible && loadError == null) {
            TvPlayerControls(
                title = curTitle,
                state = state,
                buttons = buttons,
                strip = strip,
                buttonIndex = safeButtonIndex,
            )
        }

        if (menuOpen) TvPlayerMenu(entries = menuEntries, selectedIndex = menuIndex)

        if (infoOpen) {
            TvPlayerInfo(
                stats = stats,
                display = displayInfo,
                transcoding = source?.isHls == true,
                directOutput = directOutput,
            )
        }

        toast?.let {
            Box(Modifier.fillMaxSize().padding(top = 40.dp), Alignment.TopCenter) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Surface)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text(it, color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp) }
            }
        }
    }
}

/** Bottom overlay: title, the seekable progress strip, and the button strip below it. */
@Composable
private fun TvPlayerControls(
    title: String,
    state: PlaybackState,
    buttons: List<BarButton>,
    strip: Int,
    buttonIndex: Int,
) {
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val scrubActive = strip == STRIP_SCRUB
    Column(
        Modifier.fillMaxSize().padding(horizontal = TvSidePad, vertical = 34.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, color = MatrixGreen, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        // The progress strip is a focus target in its own right: when it is selected, and only then,
        // left and right seek.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (scrubActive) MatrixGreen else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(if (scrubActive) 8.dp else 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixGreen.copy(alpha = 0.22f)),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MatrixGreen))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(state.positionMs), color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    state.isBuffering -> "puffert…"
                    state.ended -> "ende"
                    state.isPlaying -> "playing"
                    else -> "pausiert"
                },
                color = MatrixGreen.copy(alpha = 0.75f), fontFamily = Mono, fontSize = 14.sp,
            )
            Box(Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            buttons.forEachIndexed { index, button ->
                val active = strip == STRIP_BUTTONS && index == buttonIndex
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) MatrixGreen else Color.Transparent)
                        .border(
                            1.dp,
                            MatrixGreen.copy(alpha = if (active) 1f else 0.4f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ink = if (active) Black else MatrixGreen
                        button.icon?.let {
                            Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(button.label, color = ink, fontFamily = Mono, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (scrubActive) {
                "◀ ▶ = seek 10 s   ▼ = to the buttons   OK = play/pause   Back = leave"
            } else {
                "◀ ▶ = pick a button   ▲ = to the progress bar   OK = select   Back = leave"
            },
            color = MatrixGreen.copy(alpha = 0.45f), fontFamily = Mono, fontSize = 12.sp,
        )
    }
}

/**
 * Technical readout, so a report of "it stutters" can be answered with numbers rather than another
 * guess. The two lines that matter: **hwdec** (blank means it is decoding in software and the device
 * probably cannot keep up) and **Bild/Panel** — a frame rate that is not a whole fraction of the
 * panel's refresh rate judders however fast the decoder is, and dropped frames stay at zero while it
 * does, which is exactly how you tell the two apart.
 */
@Composable
private fun TvPlayerInfo(
    stats: PlaybackStats,
    display: DisplayModeInfo,
    transcoding: Boolean,
    directOutput: Boolean,
) {
    val rows = listOf(
        "Bild" to buildString {
            append(if (stats.width > 0) "${stats.width}×${stats.height}" else "—")
            if (stats.videoCodec.isNotBlank()) append("  ${stats.videoCodec}")
            if (stats.bitrateKbps > 0) append("  ${stats.bitrateKbps} kbit/s")
        },
        "audio" to stats.audioCodec.ifBlank { "—" },
        "hwdec" to stats.hwDecode.ifBlank { "SOFTWARE (no hardware decoder!)" },
        "Bildrate" to buildString {
            append(if (stats.containerFps > 0f) "%.3f fps".format(stats.containerFps) else "—")
            if (stats.fps > 0f) append("  (gerendert %.1f)".format(stats.fps))
        },
        "Panel" to buildString {
            append(if (display.currentHz > 0f) "%.2f Hz".format(display.currentHz) else "—")
            if (display.switched) append("  → angefordert %.2f Hz".format(display.requestedHz))
            if (display.available.size > 1) {
                append("  [")
                append(display.available.joinToString(", ") { "%.0f".format(it) })
                append("]")
            }
        },
        "dropped (too slow)" to stats.droppedFrames.toString(),
        "late (cadence)" to stats.delayedFrames.toString(),
        "Ausgabe" to if (directOutput) "direkt (zero-copy)" else "Standard (GPU-Kopie)",
        "Quelle" to if (transcoding) "Transkodierung (HLS)" else "Direktwiedergabe",
    )
    Box(Modifier.fillMaxSize().padding(TvSidePad), Alignment.TopStart) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Black.copy(alpha = 0.85f))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "TECHNIK",
                color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono,
                fontSize = 12.sp, letterSpacing = 2.sp,
            )
            rows.forEach { (label, value) ->
                Row {
                    Text(
                        label.padEnd(18),
                        color = MatrixGreen.copy(alpha = 0.55f),
                        fontFamily = Mono, fontSize = 14.sp,
                    )
                    Text(value, color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Track chooser, driven entirely by up/down/OK. */
@Composable
private fun TvPlayerMenu(entries: List<TvMenuEntry>, selectedIndex: Int) {
    Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.75f)), Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(end = TvSidePad)
                .width(420.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .padding(vertical = 18.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                if (entry.header) {
                    Text(
                        entry.label.uppercase(),
                        color = MatrixGreen.copy(alpha = 0.5f),
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp),
                    )
                } else {
                    val active = index == selectedIndex
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (active) MatrixGreen else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            (if (entry.selected) "● " else "○ ") + entry.label.trim(),
                            color = if (active) Black else MatrixGreen,
                            fontFamily = Mono,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
