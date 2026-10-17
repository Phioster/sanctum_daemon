package org.phioster.sanctumd.ui.jellyfin

import org.phioster.sanctumd.ui.common.SectionHeader
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.MusicAlbum
import org.phioster.sanctumd.net.MusicTrack
import org.phioster.sanctumd.net.formatDuration
import org.phioster.sanctumd.service.DownloadService
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon

/**
 * An album, shown as a record sleeve rather than a folder: square cover, running order, and a tap
 * that plays instead of asking first.
 *
 * The generic browse level treats an album like a season, expand it, drill in, open a detail sheet,
 * press play. That is four steps to hear a song, and it reads as a file manager. Here the list is
 * the running order, tapping a track starts it with the rest of the album queued behind it, and the
 * details are still one long press away for the things nobody needs mid-listen (download, identify).
 */
internal fun LazyListScope.jellyfinAlbumLevel(
    album: MusicAlbum?,
    contents: List<JellyMediaItem>?,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    context: android.content.Context,
    downloads: Map<String, DownloadEntry>,
    act: JellyfinMediaActions,
    here: JellyMediaItem,
    playingId: String,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    item {
        Spacer(Modifier.height(8.dp))
        BrowseChip("‹ back", accent, onClick = onBack)
        Spacer(Modifier.height(14.dp))
    }

    if (album == null) {
        item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) }
        return
    }

    item {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Square, because a record sleeve is. The generic row used a 46x68 poster box and
            // letterboxed every cover into a shape no album has ever had.
            Box(
                Modifier.fillMaxWidth(0.62f).aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp)).background(Surface),
            ) {
                if (album.artUrl.isNotBlank()) {
                    JellyPoster(album.artUrl, config, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(10.dp), ContentScale.Crop)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                album.name, fontFamily = Mono, color = MatrixGreen, fontSize = 19.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(album.artist.ifBlank { null }, album.year?.toString()).joinToString(" · ")
            if (line.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(line, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${album.tracks.size} tracks · ${formatDuration(album.totalMs)}",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (album.tracks.isNotEmpty()) {
                BrowseChip("play", MatrixGreen, AppIcons.Play) { vm.playJellyfinAlbum(config, album.id) }
                BrowseChip("shuffle", MatrixGreen, AppIcons.Shuffle) { vm.playJellyfinAlbum(config, album.id, shuffle = true) }
                BrowseChip("mix", MatrixGreen, AppIcons.Audio) { vm.playJellyfinInstantMix(config, album.id) }
            }
            val toGet = contents.orEmpty().filter { !it.isFolder && it.kind == "Audio" && downloads[it.id]?.done != true }
            if (toGet.isNotEmpty()) {
                BrowseChip("album (${toGet.size})", MatrixGreen, AppIcons.Download) {
                    toGet.forEach { t -> DownloadService.enqueue(context, config.id, t.id, t.name, t.subtitle, t.posterUrl, 0L, "Audio") }
                    act.message("queued ${toGet.size} downloads")
                }
            }
            BrowseChip("scan", MatrixGreen.copy(alpha = 0.85f), AppIcons.Refresh, onScan)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
    }

    if (album.tracks.isEmpty()) {
        item { Text("no tracks", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
        return
    }

    itemsIndexed(album.tracks, key = { _, t -> "trk-${t.id}" }) { index, track ->
        AlbumTrackRow(
            track = track,
            accent = accent,
            playing = track.id == playingId,
            downloaded = downloads[track.id]?.done == true,
            onPlay = { vm.playJellyfinAlbum(config, album.id, startIndex = index) },
            onDetails = { contents.orEmpty().firstOrNull { it.id == track.id }?.let(act.open) },
        )
    }
    item(key = "similar-${album.id}") { SimilarAlbums(vm, config, album.id, accent, act.open) }
    item { Spacer(Modifier.height(16.dp)) }
}

/** "More like this": albums Jellyfin finds close to this one, a tap opens the sleeve. */
@Composable
private fun SimilarAlbums(vm: DashboardViewModel, config: ServiceConfig, albumId: String, accent: Color, onOpen: (JellyMediaItem) -> Unit) {
    var list by remember(albumId) { mutableStateOf<List<JellyMediaItem>>(emptyList()) }
    LaunchedEffect(albumId) { list = runCatching { vm.jellyfinSimilarOf(config, albumId) }.getOrDefault(emptyList()) }
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        SectionHeader("more like this", accent)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { a ->
                Column(Modifier.width(112.dp).clickable { onOpen(a) }) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(8.dp)).background(Surface)) {
                        if (a.posterUrl.isNotBlank()) JellyPoster(a.posterUrl, config, Modifier.size(112.dp), RoundedCornerShape(8.dp), ContentScale.Crop)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (a.subtitle.isNotBlank()) {
                        Text(a.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** One line of the running order: number, title, length. And a caret on the one that is playing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTrackRow(
    track: MusicTrack,
    accent: Color,
    playing: Boolean,
    downloaded: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onDetails)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                Icon(AppIcons.Play, contentDescription = "playing", tint = accent, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    track.number?.toString() ?: "·",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            track.title,
            fontFamily = Mono,
            color = if (playing) accent else MatrixGreen,
            fontSize = 14.sp,
            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (downloaded) {
            Icon(AppIcons.Download, contentDescription = "Downloaded", tint = MatrixGreen.copy(alpha = 0.45f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            formatDuration(track.durationMs),
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 12.sp,
        )
    }
}

/**
 * An album in a list of albums. Square cover, because that is the shape a sleeve has. The generic
 * row's 46x68 poster box belongs to films and letterboxes every cover it is given.
 */
@Composable
internal fun AlbumListRow(
    item: JellyMediaItem,
    config: ServiceConfig,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Surface)) {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(6.dp), ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}
