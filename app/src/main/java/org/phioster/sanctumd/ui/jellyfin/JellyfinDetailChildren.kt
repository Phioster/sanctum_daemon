package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import kotlinx.coroutines.launch

/** The heading a container's contents sit under — seasons for a series, episodes for a season. */
internal fun childSectionTitle(kind: String): String = when (kind) {
    "Series" -> "SEASONS"
    "Season" -> "EPISODES"
    else -> "CONTENTS"
}

/**
 * What lives inside this item, as the same poster grid the browse screen uses.
 *
 * A series page lists its seasons, a season page its episodes, and tapping one opens *its* page —
 * so the path series → season → episode is three detail pages, the way Jellyfin's own clients
 * nest them, instead of a detail page for episodes only and flat lists above it.
 */
@Composable
internal fun DetailChildren(
    d: JellyMediaDetail,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    downloads: Map<String, org.phioster.sanctumd.model.DownloadEntry>,
    onMessage: (String) -> Unit,
    onOpen: (JellyMediaItem) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var children by remember(d.id) { mutableStateOf<List<JellyMediaItem>?>(null) }
    var error by remember(d.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(d.id) {
        error = null
        children = null
        children = try {
            // A season's own id lists its episodes, but Jellyfin folds the season-0 Specials in
            // with them — the number filters those back out.
            vm.jellyfinItemList(config, d.id, seasonNumber = if (d.kind == "Season") d.number else null)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message; null
        }
    }

    val list = children
    if (list != null && list.isEmpty() && error == null) return

    Spacer(Modifier.height(16.dp))
    MediaSectionHeader(childSectionTitle(d.kind), accent)
    // The chips act on what is listed below them, so they live with the list rather than in the
    // button column above — that column is already long enough.
    if (!list.isNullOrEmpty()) {
        val done = downloads.filterValues { it.done }.keys
        val pending = pendingDownloads(list, done)
        val next = nextUnwatched(list, done)
        fun queue(items: List<JellyMediaItem>) {
            items.forEach { e ->
                org.phioster.sanctumd.service.DownloadService.enqueue(
                    context, config.id, e.id, e.name, e.subtitle, e.posterUrl, 0L,
                )
            }
            onMessage("queued ${items.size} downloads")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (next.size > 1) BrowseChip("⬇ next ${next.size} unwatched", MatrixGreen) { queue(next) }
            if (pending.isNotEmpty()) BrowseChip("⬇ all (${pending.size})", MatrixGreen) { queue(pending) }
            BrowseChip("⟳ scan", MatrixGreen.copy(alpha = 0.85f)) {
                scope.launch { onMessage(vm.jellyfinScanLibrary(config, d.id)) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    when {
        error != null -> Text(error!!, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
        list == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        else -> Column {
            list.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { c -> MediaGridCard(c, config, accent, Modifier.weight(1f)) { onOpen(c) } }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Episodes here that aren't downloaded yet — what "⬇ all" would fetch. */
internal fun pendingDownloads(
    children: List<JellyMediaItem>,
    downloaded: Set<String>,
): List<JellyMediaItem> = children.filter {
    !it.isFolder && it.kind in org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS && it.id !in downloaded
}

/** The first few of those the viewer hasn't watched — what "⬇ next N" would fetch. */
internal fun nextUnwatched(
    children: List<JellyMediaItem>,
    downloaded: Set<String>,
    limit: Int = 3,
): List<JellyMediaItem> = pendingDownloads(children, downloaded).filter { !it.played }.take(limit)
