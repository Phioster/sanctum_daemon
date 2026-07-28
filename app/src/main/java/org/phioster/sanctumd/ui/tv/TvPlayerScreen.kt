package org.phioster.sanctumd.ui.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import kotlinx.coroutines.launch
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
import org.phioster.sanctumd.ui.player.TrackKind
import org.phioster.sanctumd.ui.player.TrackOption
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_JUMP_MS = 30_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L

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

/** One line in the options menu. Headers are shown but skipped when moving the selection. */
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
 * HEVC, PGS/ASS subtitles), so the server never has to transcode and nothing silently fails to play.
 * [ExoPlayerEngine] remains the fallback if the native library can't load on this ABI.
 *
 * All input is handled as raw key events rather than Compose focus traversal: a player has no
 * "next control to the right", it has verbs. Left/right seek, centre plays and pauses, up opens the
 * track menu, back leaves.
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

    // libmpv first — that is the whole reason this client exists. ExoPlayer only steps in when the
    // native library refuses to load on this device's ABI.
    val engine: MediaPlayerEngine = remember {
        runCatching { MpvPlayerEngine(context) }.getOrElse { ExoPlayerEngine(context) }
    }

    var curItem by remember(itemId) { mutableStateOf(itemId) }
    var curTitle by remember(itemId) { mutableStateOf(title) }
    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var state by remember { mutableStateOf(PlaybackState()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var lastGoodPos by remember { mutableStateOf(0L) }

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsNonce by remember { mutableStateOf(0) } // bumped on input to restart the hide timer
    var menuOpen by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableStateOf(0) }
    var menuNonce by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }

    var segments by remember { mutableStateOf<List<MediaSegment>>(emptyList()) }
    var skipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var nextEpisode by remember { mutableStateOf<NextEpisode?>(null) }

    val audioLang by store.audioLanguage.collectAsState("de")
    val subLang by store.subtitleLanguage.collectAsState("de")
    val subMode by store.subtitleMode.collectAsState("forced")
    val autoplayNext by store.autoplayNext.collectAsState(true)

    val keyFocus = remember { FocusRequester() }

    // Keep the panel awake and the system bars out of the way for as long as we are playing.
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
            engine.release()
        }
    }

    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    // Resolve the stream and start. Re-runs when autoplay advances to the next episode.
    LaunchedEffect(curItem) {
        loadError = null
        segments = emptyList()
        skipped = emptySet()
        nextEpisode = null
        runCatching {
            val src = jellyfinPlaybackSource(config, curItem)
            source = src
            engine.prepare(src.url, src.isHls, src.startPositionMs, src.authHeaders)
            jellyfinReportStart(config, src, src.startPositionMs)
            src
        }.onFailure { loadError = it.message ?: it.javaClass.simpleName }
        segments = runCatching { jellyfinMediaSegments(config, curItem) }.getOrDefault(emptyList())
        nextEpisode = runCatching { jellyfinNextEpisode(config, curItem) }.getOrNull()
    }

    // Poll the engine for the UI.
    LaunchedEffect(Unit) {
        while (true) {
            val snap = engine.snapshot()
            state = snap
            if (snap.positionMs > 0) lastGoodPos = snap.positionMs
            if (snap.ended) controlsVisible = true
            delay(400)
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

    // Apply the saved language preferences once tracks show up.
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

    // Auto-hide the overlay again after a few idle seconds — but never while paused or menu-open.
    LaunchedEffect(controlsNonce, controlsVisible, menuOpen, state.isPlaying) {
        if (!controlsVisible || menuOpen || !state.isPlaying) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    LaunchedEffect(toast) {
        if (toast != null) { delay(2000); toast = null }
    }

    // Auto-play the next episode when the file ends.
    LaunchedEffect(state.ended) {
        val next = nextEpisode
        if (state.ended && autoplayNext && next != null) {
            toast = "nächste Folge: ${next.name}"
            delay(3000)
            if (engine.snapshot().ended) playNext(next)
        }
    }

    val activeSegment = segments.indexOfFirst {
        state.positionMs in it.startMs until it.endMs && it.kind.equals("Intro", true)
    }

    // Track lists are read from the engine when the menu is opened, not on every recomposition —
    // hence the nonce: it is bumped just before opening so the list is current, and stays put while
    // the menu is on screen (a list that reshuffles under the selection would be unusable).
    val menuEntries: List<TvMenuEntry> = remember(menuNonce) {
        buildList {
            add(TvMenuEntry("audio", header = true))
            val audio = engine.tracks(TrackKind.AUDIO)
            if (audio.isEmpty()) add(TvMenuEntry("  keine Tonspur", header = true))
            audio.forEach { t: TrackOption ->
                add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.AUDIO, t.id) })
            }
            add(TvMenuEntry("untertitel", header = true))
            val subs = engine.tracks(TrackKind.SUBTITLE)
            add(TvMenuEntry("  aus", selected = subs.none { it.selected }) { engine.selectTrack(TrackKind.SUBTITLE, null) })
            subs.forEach { t: TrackOption ->
                add(TvMenuEntry("  ${t.label}", selected = t.selected) { engine.selectTrack(TrackKind.SUBTITLE, t.id) })
            }
        }
    }

    // Start on the currently active track, or the first real entry — never on a header.
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
        when {
            menuOpen -> menuOpen = false
            controlsVisible && state.isPlaying -> controlsVisible = false
            else -> leave()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        if (menuOpen) moveMenu(-1) else { menuNonce++; menuOpen = true; poke() }
                        true
                    }
                    Key.DirectionDown -> {
                        if (menuOpen) moveMenu(1) else poke()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (menuOpen) { menuOpen = false } else { engine.seekBy(-SEEK_STEP_MS); poke() }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!menuOpen) { engine.seekBy(SEEK_STEP_MS); poke() }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (menuOpen) {
                            menuEntries.getOrNull(menuIndex)?.onSelect?.invoke()
                            menuOpen = false
                        } else {
                            engine.togglePlay(); poke()
                        }
                        true
                    }
                    Key.MediaPlayPause -> { engine.togglePlay(); poke(); true }
                    Key.MediaPlay -> { engine.play(); poke(); true }
                    Key.MediaPause -> { engine.pause(); poke(); true }
                    Key.MediaFastForward -> { engine.seekBy(SEEK_JUMP_MS); poke(); true }
                    Key.MediaRewind -> { engine.seekBy(-SEEK_JUMP_MS); poke(); true }
                    Key.MediaStop -> { leave(); true }
                    Key.MediaNext -> { nextEpisode?.let { playNext(it) }; true }
                    Key.Menu -> {
                        if (!menuOpen) menuNonce++
                        menuOpen = !menuOpen
                        poke()
                        true
                    }
                    else -> false
                }
            },
    ) {
        engine.VideoSurface(Modifier.fillMaxSize())

        if (loadError != null) {
            Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.85f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wiedergabe fehlgeschlagen", color = ErrRed, fontFamily = Mono, fontSize = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(loadError ?: "", color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Text("zurück mit der Zurück-Taste", color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 12.sp)
                }
            }
        } else if (state.durationMs <= 0 && state.positionMs <= 0) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("puffere…", color = MatrixGreen, fontFamily = Mono, fontSize = 18.sp)
            }
        }

        // Skip-intro, when the server actually reports segments (needs the Intro Skipper plugin).
        if (activeSegment >= 0 && activeSegment !in skipped) {
            Box(Modifier.fillMaxSize().padding(bottom = 140.dp, end = TvSidePad), Alignment.BottomEnd) {
                TvButton("Intro überspringen  ›") {
                    skipped = skipped + activeSegment
                    engine.seekTo(segments[activeSegment].endMs)
                }
            }
        }

        if (controlsVisible && loadError == null) {
            TvPlayerControls(
                title = curTitle,
                state = state,
                isBuffering = state.isBuffering,
                hasNext = nextEpisode != null,
            )
        }

        if (menuOpen) {
            TvPlayerMenu(entries = menuEntries, selectedIndex = menuIndex)
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

/** Bottom overlay: title, scrub bar, times and the key hints a remote needs spelled out once. */
@Composable
private fun TvPlayerControls(
    title: String,
    state: PlaybackState,
    isBuffering: Boolean,
    hasNext: Boolean,
) {
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvSidePad, vertical = 34.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, color = MatrixGreen, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MatrixGreen.copy(alpha = 0.22f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MatrixGreen))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(state.positionMs), color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    isBuffering -> "puffert…"
                    state.ended -> "ende"
                    state.isPlaying -> "▶ läuft"
                    else -> "❚❚ pausiert"
                },
                color = MatrixGreen.copy(alpha = 0.75f), fontFamily = Mono, fontSize = 14.sp,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = MatrixGreen, fontFamily = Mono, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            buildString {
                append("OK = Play/Pause   ◀ ▶ = 10 s   ▲ = Ton & Untertitel   Zurück = beenden")
                if (hasNext) append("   ⏭ = nächste Folge")
            },
            color = MatrixGreen.copy(alpha = 0.45f), fontFamily = Mono, fontSize = 12.sp,
        )
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
                when {
                    entry.header -> Text(
                        entry.label.uppercase(),
                        color = MatrixGreen.copy(alpha = 0.5f),
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp),
                    )
                    else -> {
                        val focused = index == selectedIndex
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (focused) MatrixGreen else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                (if (entry.selected) "● " else "○ ") + entry.label.trim(),
                                color = if (focused) Black else MatrixGreen,
                                fontFamily = Mono,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
