package org.phioster.sanctumd.ui.jellyfin

import org.phioster.sanctumd.net.errorText

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
import androidx.media3.common.Player
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Downloads and the music bar: self-contained pieces of the Media screen, kept out of
// JellyfinScreen.kt so that file is about browsing rather than everything at once.

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
        Icon(
            if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = "Play/Pause", tint = MatrixGreen,
            modifier = Modifier.clickable { onToggle() }.padding(8.dp).size(22.dp),
        )
        if (state.hasNext) {
            Icon(AppIcons.Next, contentDescription = "Next", tint = MatrixGreen, modifier = Modifier.clickable { onNext() }.padding(8.dp).size(20.dp))
        }
    }
}

/** Full-screen now-playing: big art, seek bar, prev/play-pause/next, then queue or lyrics. */
@Composable
internal fun NowPlayingScreen(
    state: org.phioster.sanctumd.ui.player.MusicState,
    accent: Color,
    onClose: () -> Unit,
    vm: org.phioster.sanctumd.ui.DashboardViewModel? = null,
    config: org.phioster.sanctumd.model.ServiceConfig? = null,
) {
    BackHandler { onClose() }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
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
            // The cover steps back while lyrics are open, so more lines fit under the controls.
            Box(Modifier.fillMaxWidth(if (showLyrics) 0.42f else 0.82f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
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
            // Shuffle and repeat flank the transport, where every music player puts them. Both are
            // the player's own modes, so the lock screen and a car head unit see the same thing.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.toggleShuffle() }) {
                    Icon(
                        AppIcons.Shuffle, contentDescription = "Shuffle",
                        tint = if (state.shuffle) accent else MatrixGreen.copy(alpha = 0.35f),
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.prev() }, enabled = state.hasPrev) {
                    Icon(AppIcons.Previous, contentDescription = "Prev", tint = if (state.hasPrev) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.playPause() }) {
                    Icon(if (state.isPlaying) AppIcons.Pause else AppIcons.Play, contentDescription = "Play/Pause", tint = MatrixGreen, modifier = Modifier.size(56.dp))
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.next() }, enabled = state.hasNext) {
                    Icon(AppIcons.Next, contentDescription = "Next", tint = if (state.hasNext) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { org.phioster.sanctumd.ui.player.MusicController.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) MatrixGreen.copy(alpha = 0.35f) else accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            if (vm != null && config != null && state.currentMediaId.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.queue.size > 1) {
                        BrowseChip("up next", if (!showLyrics) accent else MatrixGreen.copy(alpha = 0.6f)) { showLyrics = false }
                    }
                    BrowseChip("lyrics", if (showLyrics) accent else MatrixGreen.copy(alpha = 0.6f)) { showLyrics = true }
                    BrowseChip("instant mix", MatrixGreen.copy(alpha = 0.85f), AppIcons.Shuffle) {
                        vm.playJellyfinInstantMix(config, state.currentMediaId)
                        showLyrics = false
                    }
                }
            }
            if (showLyrics && vm != null && config != null) {
                Spacer(Modifier.height(10.dp))
                LyricsPane(vm, config, state.currentMediaId, pos, accent, Modifier.fillMaxWidth().weight(1f))
            } else if (state.queue.size > 1) {
                // Queue / album track list fills the space below the controls.
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
                            if (current) {
                                Icon(
                                    if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = null, tint = accent, modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The song's lyrics. Timed ones follow the song: the line being sung is lit and kept in view, and
 * tapping a line jumps there. Untimed ones are just a page to read.
 */
@Composable
private fun LyricsPane(
    vm: org.phioster.sanctumd.ui.DashboardViewModel,
    config: org.phioster.sanctumd.model.ServiceConfig,
    trackId: String,
    positionMs: Long,
    accent: Color,
    modifier: Modifier,
) {
    var lyrics by remember(trackId) { mutableStateOf<org.phioster.sanctumd.net.MusicLyrics?>(null) }
    var state by remember(trackId) { mutableStateOf("loading…") }
    LaunchedEffect(trackId) {
        runCatching { vm.jellyfinLyricsOf(config, trackId) }
            .onSuccess { lyrics = it; state = if (it == null) "no lyrics on the server for this song" else "" }
            .onFailure { state = "error: ${errorText(it)}" }
    }
    val l = lyrics
    if (l == null) {
        Text(state, fontFamily = Mono, color = if (state.startsWith("error")) ErrRed else MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp,
            modifier = modifier.padding(top = 12.dp))
        return
    }
    val current = l.lineAt(positionMs)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { if (current > 1) listState.animateScrollToItem(current - 2) }
    LazyColumn(modifier, state = listState) {
        itemsIndexed(l.lines) { i, line ->
            val lit = l.synced && i == current
            Text(
                line.text.ifBlank { " " },
                fontFamily = Mono,
                fontSize = if (lit) 17.sp else 15.sp,
                fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !l.synced -> MatrixGreen
                    lit -> accent
                    i < current -> MatrixGreen.copy(alpha = 0.45f)
                    else -> MatrixGreen.copy(alpha = 0.8f)
                },
                modifier = Modifier.fillMaxWidth()
                    .then(if (l.synced) Modifier.clickable { line.startMs?.let { org.phioster.sanctumd.ui.player.MusicController.seekTo(it) } } else Modifier)
                    .padding(vertical = 5.dp),
            )
        }
        if (!l.synced) item { Spacer(Modifier.height(24.dp)) }
    }
}
