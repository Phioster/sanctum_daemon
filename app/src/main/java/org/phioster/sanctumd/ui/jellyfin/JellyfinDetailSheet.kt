package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.common.*
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
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel

/**
 * The full-screen sheet for one media item.
 *
 * Lifted out of JellyfinScreen, which had grown past 1700 lines with 63 loose state variables in
 * a single composable — the reason an earlier attempt at this produced a function with fifteen
 * parameters and was abandoned. [JellyfinDetailState] collapses the sheet's own state into one
 * object; what remains as parameters is the coupling that is genuinely there, stated openly
 * rather than reached for through shared locals.
 */
@Composable
internal fun JellyfinDetailSheet(
    state: JellyfinDetailState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    downloads: Map<String, org.phioster.sanctumd.model.DownloadEntry>,
    favorites: List<org.phioster.sanctumd.model.JellyMediaItem>?,
    sessions: List<org.phioster.sanctumd.model.JellySession>?,
    onFavorites: (List<org.phioster.sanctumd.model.JellyMediaItem>?) -> Unit,
    onSessions: (List<org.phioster.sanctumd.model.JellySession>?) -> Unit,
    onMessage: (String) -> Unit,
    onPlay: (org.phioster.sanctumd.ui.player.PlayRequest) -> Unit,
    onWatched: (String, String, Boolean) -> Unit,
) {
    val d = state.detail ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dl = downloads[d.id]
    Box(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Banner: a blurred poster backdrop with the sharp poster + title on top ──
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (d.posterUrl.isNotBlank()) {
                    JellyPoster(d.posterUrl, config, Modifier.matchParentSize().blur(28.dp), RectangleShape, ContentScale.Crop)
                } else {
                    Box(Modifier.matchParentSize().background(Surface))
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Black.copy(alpha = 0.35f), Black.copy(alpha = 0.65f), Black))))
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    if (d.posterUrl.isNotBlank()) {
                        Box {
                            JellyPoster(d.posterUrl, config, Modifier.width(120.dp).height(180.dp), RoundedCornerShape(8.dp), ContentScale.Crop)
                            if (d.played) WatchedBadge(accent, Modifier.align(Alignment.TopEnd), size = 22.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.played && d.posterUrl.isBlank()) {
                                WatchedBadge(accent, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(d.name, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (d.subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(d.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val quick = d.facts.take(3).joinToString("  ·  ") { it.second }
                        if (quick.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(quick, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // ── Body ──
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                if (d.kind in org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS) {
                    PrimaryButton("play", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) {
                        state.closeAll(); onPlay(org.phioster.sanctumd.ui.player.PlayRequest(d.id, d.name))
                    }
                    Spacer(Modifier.height(8.dp))
                    val startDownload = { state.downloadQuality = d }
                    when (dl?.state) {
                        org.phioster.sanctumd.model.DownloadEntry.STATE_DONE ->
                            Hint("✓  downloaded — play it from the DOWNLOADS row", Modifier.padding(vertical = 8.dp))
                        org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING, org.phioster.sanctumd.model.DownloadEntry.STATE_QUEUED ->
                            SecondaryButton("⬇  ${(dl.progress * 100).toInt()}%  ·  cancel", Modifier.fillMaxWidth()) { org.phioster.sanctumd.service.DownloadService.cancel(context, d.id) }
                        org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED ->
                            SecondaryButton("⚠  download failed — retry", Modifier.fillMaxWidth(), accent = ErrRed) { startDownload() }
                        else ->
                            SecondaryButton("⬇  download", Modifier.fillMaxWidth()) { startDownload() }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (d.kind == "Audio") {
                    PrimaryButton("play", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) {
                        state.closeAll()
                        scope.launch {
                            val track = runCatching { vm.jellyfinTrack(config, d.id) }.getOrNull()
                            if (track != null) org.phioster.sanctumd.ui.player.MusicController.play(context, listOf(track), 0, config.customHeaders)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (dl?.state) {
                        org.phioster.sanctumd.model.DownloadEntry.STATE_DONE ->
                            Hint("✓  downloaded", Modifier.padding(vertical = 8.dp))
                        org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING, org.phioster.sanctumd.model.DownloadEntry.STATE_QUEUED ->
                            SecondaryButton("⬇  ${(dl.progress * 100).toInt()}%  ·  cancel", Modifier.fillMaxWidth()) { org.phioster.sanctumd.service.DownloadService.cancel(context, d.id) }
                        else ->
                            SecondaryButton("⬇  download", Modifier.fillMaxWidth()) { org.phioster.sanctumd.service.DownloadService.enqueue(context, config.id, d.id, d.name, d.subtitle, d.posterUrl, 0L, "Audio") }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // Favourite + cast, side by side above the watched toggle.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        if (d.favorite) "♥  favorite" else "♡  favorite",
                        Modifier.weight(1f),
                    ) {
                        scope.launch {
                            runCatching { vm.jellyfinSetFavorite(config, d.id, !d.favorite) }
                                .onSuccess {
                                    onMessage(if (d.favorite) "removed from favorites" else "added to favorites")
                                    runCatching { vm.jellyfinMediaDetail(config, d.id) }.getOrNull()?.let { state.replaceTop(it) }
                                    onFavorites(runCatching { vm.jellyfinFavoriteList(config) }.getOrNull() ?: favorites)
                                }
                                .onFailure { onMessage("could not update: ${it.message ?: "failed"}") }
                        }
                    }
                    if (d.kind in org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS) {
                        SecondaryButton("▶  play on…", Modifier.weight(1f)) {
                            state.cast = d
                            scope.launch { onSessions(runCatching { vm.jellyfinSessionList(config) }.getOrNull() ?: sessions) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Watched toggle — the explicit counterpart to the poster badge (and the only way
                // to mark something watched that has no badge yet).
                if (d.kind in org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS || d.kind in setOf("Series", "Season")) {
                    val folder = d.kind in setOf("Series", "Season")
                    SecondaryButton(
                        if (d.played) "✓  watched  ·  mark unwatched" else "mark as watched",
                        Modifier.fillMaxWidth(),
                    ) {
                        if (folder) state.confirmWatched = Triple(d.id, d.name, !d.played)
                        else onWatched(d.id, d.name, !d.played)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (d.facts.isNotEmpty()) {
                    d.facts.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            pair.forEach { (k, v) ->
                                Column(Modifier.weight(1f)) {
                                    Text(v, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(k.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (d.genres.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(d.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                if (d.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (opensAsDetail(d.kind)) {
                    DetailChildren(d, vm, config, accent, downloads, onMessage) { child ->
                        scope.launch {
                            runCatching { vm.jellyfinMediaDetail(config, child.id) }.getOrNull()?.let { state.open(it) }
                        }
                    }
                }
                d.fileInfo?.let { fi ->
                    // Only worth a lookup when a track actually lacks a language of its own.
                    val needsLanguage = fi.streams.any { it.type == "Audio" && it.language.isBlank() }
                    var assumedLanguage by remember(d.id) { mutableStateOf("") }
                    LaunchedEffect(d.id, needsLanguage) {
                        assumedLanguage = if (!needsLanguage) "" else {
                            val tmdb = d.providerIds["Tmdb"]?.toIntOrNull() ?: 0
                            vm.originalLanguage(tmdb, isTv = d.kind == "Series" || d.kind == "Episode")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FileInfoSection(fi, accent, assumedLanguage)
                }
                if (d.cast.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    MediaSectionHeader("CAST", accent)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        d.cast.forEach { member ->
                            Column(Modifier.width(84.dp).padding(end = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (member.profileUrl.isNotBlank()) {
                                    JellyPoster(member.profileUrl, config, Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)), RoundedCornerShape(36.dp), ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Surface))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(member.name, fontFamily = Mono, color = MatrixGreen, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                if (member.character.isNotBlank()) {
                                    Text(member.character, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        // ── Top bar overlay: back + open-in-Jellyfin ──
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { openExternal(context, jellyfinAppPackages, "${config.normalizedBaseUrl}web/#/details?id=${d.id}") }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in Jellyfin", tint = accent)
            }
            // The rarely-used and the destructive live here rather than as eight stacked
            // buttons: the list had grown into a wall you had to read to find "play".
            Box {
                IconButton(onClick = { state.menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen)
                }
                DropdownMenu(expanded = state.menuOpen, onDismissRequest = { state.menuOpen = false }) {
                    if (arrServiceTypeFor(d.kind) != null) {
                        DropdownMenuItem(
                            text = { Text("Manage in Radarr / Sonarr", fontFamily = Mono) },
                            onClick = { state.menuOpen = false; state.manage = d },
                        )
                    }
                    // A collection joins to Radarr by its TMDB *collection* id, not a film id —
                    // a different lookup than the one above, so it gets its own entry.
                    if (d.kind == "BoxSet" && d.providerIds["Tmdb"] != null) {
                        DropdownMenuItem(
                            text = { Text("Missing films in Radarr", fontFamily = Mono) },
                            onClick = { state.menuOpen = false; state.collection = d },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Subtitles", fontFamily = Mono) },
                        onClick = { state.menuOpen = false; state.subtitles = d },
                    )
                    DropdownMenuItem(
                        text = { Text("Identify", fontFamily = Mono) },
                        onClick = { state.menuOpen = false; state.identify = d },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", fontFamily = Mono, color = ErrRed) },
                        onClick = { state.menuOpen = false; state.delete = d },
                    )
                }
            }
        }
    }
}
