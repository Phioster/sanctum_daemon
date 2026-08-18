package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*

// Downloads and the music bar: self-contained pieces of the Media screen, kept out of
// JellyfinScreen.kt so that file is about browsing rather than everything at once.

@Composable
internal fun DownloadCard(
    entry: org.phioster.sanctumd.model.DownloadEntry,
    accent: Color,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.width(120.dp).padding(end = 10.dp)) {
        Box(
            Modifier.width(120.dp).height(170.dp).clip(RoundedCornerShape(8.dp)).background(Surface)
                .clickable { onPlay() },
        ) {
            if (entry.posterFile.startsWith("/")) {
                coil.compose.AsyncImage(
                    model = java.io.File(entry.posterFile),
                    contentDescription = entry.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Dim + state glyph overlay.
            val glyph = when (entry.state) {
                org.phioster.sanctumd.model.DownloadEntry.STATE_DONE -> "▶"
                org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED -> "⚠"
                else -> "${(entry.progress * 100).toInt()}%"
            }
            Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                Text(glyph, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "✕", fontFamily = Mono, color = ErrRed, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).background(Color(0xAA000000), RoundedCornerShape(6.dp))
                    .clickable { onDelete() }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        // Thin progress bar while downloading.
        if (!entry.done && entry.state != org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(MatrixGreen.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(entry.progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val status = when (entry.state) {
            org.phioster.sanctumd.model.DownloadEntry.STATE_DONE -> "downloaded"
            org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED -> "failed"
            org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING -> "${(entry.progress * 100).toInt()}% · downloading"
            else -> if (entry.error.contains("Wi-Fi")) "waiting for Wi-Fi" else "queued"
        }
        Text(status, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** Full-screen manager for offline downloads: storage summary, Wi-Fi-only toggle, bulk clear, and a
 *  list with per-item play/delete. */
@Composable
internal fun DownloadsManager(
    entries: List<org.phioster.sanctumd.model.DownloadEntry>,
    accent: Color,
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    deleteWatched: Boolean,
    onDeleteWatched: (Boolean) -> Unit,
    onPlay: (org.phioster.sanctumd.model.DownloadEntry) -> Unit,
    onDelete: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val used = entries.sumOf { if (it.done) it.sizeBytes else it.downloadedBytes }
    val free = remember { runCatching { android.os.StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val doneCount = entries.count { it.done }

    Box(
        Modifier.fillMaxSize().background(Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // The app is edge-to-edge (enableEdgeToEdge); this full-screen overlay must pad for the
        // status/nav bars itself, or the header sits under the status bar.
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen) }
                Text("downloads", fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("${entries.size} items · ${fmtSize(used)} used · ${fmtSize(free)} free", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (wifiOnly) "[x] Wi-Fi only" else "[ ] Wi-Fi only", fontFamily = Mono, color = if (wifiOnly) MatrixGreen else MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.clickable { onWifiOnly(!wifiOnly) })
                Spacer(Modifier.weight(1f))
                if (doneCount > 0) Text("clear done", fontFamily = Mono, color = accent, fontSize = 12.sp, modifier = Modifier.clickable { onClearCompleted() }.padding(end = 14.dp))
                if (entries.isNotEmpty()) Text("clear all", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable { onClearAll() })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (deleteWatched) "[x] delete when watched" else "[ ] delete when watched",
                fontFamily = Mono, color = if (deleteWatched) MatrixGreen else MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.clickable { onDeleteWatched(!deleteWatched) },
            )
            Text(
                "Checked on every visit to the media tab: a finished download whose item counts as watched on the server is removed.",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
            if (entries.isEmpty()) {
                Text("no downloads", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (e.done) onPlay(e) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(46.dp, 64.dp).clip(RoundedCornerShape(4.dp)).background(Surface)) {
                                if (e.posterFile.startsWith("/")) {
                                    coil.compose.AsyncImage(model = java.io.File(e.posterFile), contentDescription = e.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = when (e.state) {
                                    org.phioster.sanctumd.model.DownloadEntry.STATE_DONE -> fmtSize(e.sizeBytes)
                                    org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING -> "${(e.progress * 100).toInt()}% · ${fmtSize(e.downloadedBytes)}"
                                    org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED -> "failed"
                                    else -> if (e.error.contains("Wi-Fi")) "waiting for Wi-Fi" else "queued"
                                }
                                Text(sub, fontFamily = Mono, color = accent.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.state == org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING) {
                                    Spacer(Modifier.height(3.dp))
                                    Box(Modifier.fillMaxWidth().height(3.dp).background(MatrixGreen.copy(alpha = 0.2f))) {
                                        Box(Modifier.fillMaxWidth(e.progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (e.done) Text("▶", fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, modifier = Modifier.clickable { onPlay(e) }.padding(8.dp))
                            Text("✕", fontFamily = Mono, color = ErrRed, fontSize = 15.sp, modifier = Modifier.clickable { onDelete(e.itemId) }.padding(8.dp))
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000; val m = total / 60; val s = total % 60
    return "%d:%02d".format(m, s)
}

/** Compact now-playing bar (Scaffold bottom): art, title/artist, play-pause, next; tap to expand. */
@Composable
internal fun MusicBar(
    state: org.phioster.sanctumd.ui.player.MusicState,
    accent: Color,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Surface).clickable { onOpen() }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(Black)) {
            if (state.artworkUri.isNotBlank()) {
                coil.compose.AsyncImage(model = state.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(state.title.ifBlank { "…" }, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.artist.isNotBlank()) Text(state.artist, fontFamily = Mono, color = accent.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (state.isPlaying) "❚❚" else "▶", fontFamily = Mono, color = MatrixGreen, fontSize = 17.sp, modifier = Modifier.clickable { onToggle() }.padding(8.dp))
        if (state.hasNext) Text("⏭", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, modifier = Modifier.clickable { onNext() }.padding(8.dp))
    }
}

/** Full-screen now-playing: big art, seek bar, prev/play-pause/next. */
@Composable
internal fun NowPlayingScreen(
    state: org.phioster.sanctumd.ui.player.MusicState,
    accent: Color,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) { pos = org.phioster.sanctumd.ui.player.MusicController.positionMs(); dur = org.phioster.sanctumd.ui.player.MusicController.durationMs() }
            delay(500)
        }
    }
    Box(
        Modifier.fillMaxSize().background(Black)
            // Swallow taps so nothing reaches the media list behind this overlay.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen) }
                Text("now playing", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
                if (state.artworkUri.isNotBlank()) {
                    coil.compose.AsyncImage(model = state.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(state.title, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (state.artist.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.artist, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            val d = dur.coerceAtLeast(1)
            val p = if (scrubbing) (scrubPos * d).toLong() else pos
            Slider(
                value = p.toFloat().coerceIn(0f, d.toFloat()),
                onValueChange = { scrubbing = true; scrubPos = it / d.toFloat() },
                onValueChangeFinished = { org.phioster.sanctumd.ui.player.MusicController.seekTo((scrubPos * d).toLong()); scrubbing = false },
                valueRange = 0f..d.toFloat(),
                colors = SliderDefaults.colors(thumbColor = MatrixGreen, activeTrackColor = MatrixGreen, inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(p), fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp)
                Text(fmtTime(dur), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.prev() }, enabled = state.hasPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = if (state.hasPrev) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.playPause() }) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = MatrixGreen, modifier = Modifier.size(56.dp))
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.next() }, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = if (state.hasNext) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), modifier = Modifier.size(36.dp))
                }
            }
            // Queue / album track list fills the space below the controls.
            if (state.queue.size > 1) {
                Spacer(Modifier.height(18.dp))
                Text("UP NEXT", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(state.queue) { i, t ->
                        val current = i == state.currentIndex
                        Row(
                            Modifier.fillMaxWidth().clickable { org.phioster.sanctumd.ui.player.MusicController.seekToIndex(i) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", fontFamily = Mono, color = if (current) accent else MatrixGreen.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.width(26.dp))
                            Text(t.title, fontFamily = Mono, color = if (current) accent else MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (current) Text(if (state.isPlaying) "❚❚" else "▶", fontFamily = Mono, color = accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
