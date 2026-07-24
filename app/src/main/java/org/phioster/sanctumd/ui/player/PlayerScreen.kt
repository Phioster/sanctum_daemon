package org.phioster.sanctumd.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.PlaybackSource
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Full-screen video player overlay. Resolves the stream via [PlaybackSource], drives an
 * [ExoPlayerEngine], reports playback to Jellyfin (start/progress/stopped, so Continue Watching
 * updates), and shows terminal-green controls. Presented on top of [org.phioster.sanctumd.ui.jellyfin.JellyfinScreen].
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
    val engine = remember { ExoPlayerEngine(context) }

    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(PlaybackState()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0f) }

    // Immersive full-screen + landscape while the player is up; restored on leave.
    DisposableEffect(Unit) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is Activity } as? Activity
        val window = activity?.window
        val prevOrientation = activity?.requestedOrientation
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
            if (state.ended) { controlsVisible = true }
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

    // Auto-hide the controls a few seconds after they appear (while playing).
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) { delay(3500); controlsVisible = false }
    }

    val leave = {
        val src = source
        if (src != null) vm.jellyfinReportStoppedAsync(config, src, engine.snapshot().positionMs)
        onClose() // removal triggers the DisposableEffect below, which releases the engine once
    }
    BackHandler { leave() }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    Box(
        Modifier.fillMaxSize().background(Black).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { controlsVisible = !controlsVisible },
    ) {
        engine.VideoSurface(Modifier.fillMaxSize())

        if (source == null && loadError == null && localFileUri == null) {
            CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.align(Alignment.Center))
        }
        if (state.isBuffering && loadError == null) {
            CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.align(Alignment.Center))
        }
        loadError?.let { err ->
            Text(
                "playback error: $err",
                fontFamily = Mono, color = Color(0xFFFF5555), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        if (controlsVisible) {
            // Top bar: back + title.
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { leave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen)
                }
                Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            // Bottom seek bar + times.
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp),
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
    }
}
