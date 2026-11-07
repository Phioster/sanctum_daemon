package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

// The offline downloads, in one place: the header button opens this screen, and the media home
// only lists downloads itself while the server is out of reach (2026-11-07, replacing the strip on
// the media home plus a separate "manage" screen, which showed the same thing twice).

/** Whether this entry still needs the network: running, waiting, paused or failed. */
internal val DownloadEntry.inProgress: Boolean get() = !done

/** Finished downloads bundled the way the list shows them: a series' episodes together, films alone. */
internal data class DownloadGroup(val title: String, val entries: List<DownloadEntry>) {
    val isSeries: Boolean get() = entries.size > 1 || entries.first().subtitle.contains(" · S")
}

/**
 * An episode's subtitle is "Series · S01E02" ([org.phioster.sanctumd.net.jfSubtitle]), so the part
 * before the dot names its show. Groups keep the order of their newest download; episodes inside
 * a group run in episode order.
 */
internal fun groupDownloads(done: List<DownloadEntry>): List<DownloadGroup> {
    val bySeries = linkedMapOf<String, MutableList<DownloadEntry>>()
    done.sortedByDescending { it.addedAt }.forEach { e ->
        val key = if (e.subtitle.contains(" · S")) "s:" + e.subtitle.substringBefore(" · ") else "i:" + e.itemId
        bySeries.getOrPut(key) { mutableListOf() } += e
    }
    return bySeries.map { (key, list) ->
        val title = if (key.startsWith("s:")) key.removePrefix("s:") else list.first().name
        DownloadGroup(title, list.sortedBy { it.subtitle.substringAfter(" · ", "") })
    }
}

internal fun fmtBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** "3 min" / "1 h 05 min" for a remaining time in seconds; blank when it can't be told. */
internal fun fmtEta(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "< 1 min"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
}

/** The header's download button: always there, a progress ring with a count while anything loads. */
@Composable
internal fun DownloadsHeaderButton(entries: List<DownloadEntry>, accent: Color, onClick: () -> Unit) {
    val active = entries.filter { it.state == DownloadEntry.STATE_RUNNING || it.state == DownloadEntry.STATE_QUEUED }
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (active.isEmpty()) {
            Icon(AppIcons.Download, contentDescription = "Downloads", tint = MatrixGreen, modifier = Modifier.size(20.dp))
        } else {
            val total = active.sumOf { it.sizeBytes }.coerceAtLeast(1)
            val got = active.sumOf { it.downloadedBytes }
            CircularProgressIndicator(
                progress = { (got.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.size(24.dp),
                color = accent, trackColor = accent.copy(alpha = 0.2f), strokeWidth = 2.5.dp,
            )
            Icon(AppIcons.Download, contentDescription = "Downloads", tint = accent, modifier = Modifier.size(12.dp))
            if (active.size > 1) {
                Text(
                    "${active.size}", fontFamily = Mono, color = Black, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 4.dp)
                        .background(accent, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** A download's picture: the cached file once it's there, the server's image before that. */
@Composable
internal fun DownloadThumb(entry: DownloadEntry, config: ServiceConfig?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Surface)) {
        when {
            entry.posterFile.startsWith("/") -> coil.compose.AsyncImage(
                model = java.io.File(entry.posterFile), contentDescription = entry.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            entry.posterFile.startsWith("http") && config != null ->
                JellyPoster(entry.posterFile, config, Modifier.fillMaxSize(), RoundedCornerShape(6.dp), ContentScale.Crop)
        }
    }
}

/** Bytes per second for a running download, smoothed over the store's ~4 updates a second. */
@Composable
private fun rememberRate(entry: DownloadEntry): Long {
    var lastAt by remember(entry.itemId) { mutableLongStateOf(0L) }
    var lastBytes by remember(entry.itemId) { mutableLongStateOf(0L) }
    var rate by remember(entry.itemId) { mutableLongStateOf(0L) }
    LaunchedEffect(entry.downloadedBytes, entry.state) {
        if (entry.state != DownloadEntry.STATE_RUNNING) { rate = 0; lastAt = 0; return@LaunchedEffect }
        val now = System.currentTimeMillis()
        if (lastAt > 0 && now - lastAt > 150 && entry.downloadedBytes >= lastBytes) {
            val r = (entry.downloadedBytes - lastBytes) * 1000 / (now - lastAt)
            rate = if (rate == 0L) r else (rate * 7 + r * 3) / 10
        }
        lastAt = now; lastBytes = entry.downloadedBytes
    }
    return rate
}

@Composable
private fun SectionLabel(text: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Text(text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) Text(trailing, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, tint.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp)) }
}

/** One download that isn't finished: progress with size, speed and time left, pause/continue and cancel. */
@Composable
internal fun ActiveDownloadRow(
    entry: DownloadEntry,
    config: ServiceConfig?,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val rate = rememberRate(entry)
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        DownloadThumb(entry, config, Modifier.width(96.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sizes = if (entry.sizeBytes > 0) "${fmtBytes(entry.downloadedBytes)} of ${fmtBytes(entry.sizeBytes)}" else fmtBytes(entry.downloadedBytes)
            val line = when (entry.state) {
                DownloadEntry.STATE_RUNNING -> sizes
                DownloadEntry.STATE_PAUSED -> "paused · $sizes"
                DownloadEntry.STATE_FAILED -> "failed · ${entry.error.ifBlank { "tap continue to retry" }}"
                else -> if (entry.error.contains("Wi-Fi")) "waiting for Wi-Fi" else "queued"
            }
            Text(
                listOf(entry.subtitle.substringAfter(" · ", ""), line).filter { it.isNotBlank() }.joinToString(" · "),
                fontFamily = Mono, color = if (entry.state == DownloadEntry.STATE_FAILED) ErrRed else accent.copy(alpha = 0.75f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { entry.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (entry.state == DownloadEntry.STATE_RUNNING) accent else accent.copy(alpha = 0.45f),
                trackColor = accent.copy(alpha = 0.15f),
            )
            if (entry.state == DownloadEntry.STATE_RUNNING && rate > 0) {
                val eta = if (entry.sizeBytes > entry.downloadedBytes) fmtEta((entry.sizeBytes - entry.downloadedBytes) / rate) else ""
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf("${fmtBytes(rate)}/s", eta.takeIf { it.isNotBlank() }?.let { "$it left" }).filterNotNull().joinToString(" · "),
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (entry.state) {
            DownloadEntry.STATE_RUNNING, DownloadEntry.STATE_QUEUED -> RoundAction(AppIcons.Pause, "Pause", MatrixGreen, onPause)
            else -> RoundAction(AppIcons.Play, "Continue", MatrixGreen, onResume)
        }
        Spacer(Modifier.width(6.dp))
        RoundAction(AppIcons.Cancel, "Cancel", ErrRed, onCancel)
    }
}

/** A finished download: picture with play button, title, size, and a menu to delete it. */
@Composable
internal fun ReadyDownloadRow(
    entry: DownloadEntry,
    config: ServiceConfig?,
    accent: Color,
    title: String,
    detail: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            DownloadThumb(entry, config, Modifier.width(96.dp).aspectRatio(16f / 9f))
            PosterPlayButton(accent, Modifier.align(Alignment.Center), size = 30.dp, onClick = onPlay)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(AppIcons.More, contentDescription = "More", tint = MatrixGreen.copy(alpha = 0.8f)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play", fontFamily = Mono) }, onClick = { menu = false; onPlay() })
                DropdownMenuItem(text = { Text("Delete download", fontFamily = Mono, color = ErrRed) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** A finished series: its cover, how many episodes and how big, and the episodes below it. */
@Composable
private fun SeriesGroup(
    group: DownloadGroup,
    config: ServiceConfig?,
    accent: Color,
    onPlay: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit,
) {
    var open by remember(group.title) { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp)).background(Surface).padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            DownloadThumb(group.entries.first(), config, Modifier.width(40.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val n = group.entries.size
                Text(
                    "$n episode${if (n == 1) "" else "s"} · ${fmtBytes(group.entries.sumOf { it.sizeBytes })}",
                    fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp,
                )
            }
            Icon(if (open) AppIcons.Expanded else AppIcons.Collapsed, contentDescription = null, tint = MatrixGreen.copy(alpha = 0.7f))
        }
        if (open) {
            group.entries.forEach { e ->
                ReadyDownloadRow(
                    e, config, accent,
                    title = e.name,
                    detail = listOf(e.subtitle.substringAfter(" · ", ""), fmtBytes(e.sizeBytes)).filter { it.isNotBlank() }.joinToString(" · "),
                    onPlay = { onPlay(e) }, onDelete = { onDelete(e) },
                )
            }
        }
    }
}

/**
 * The downloads screen: storage at the top, then what is still loading (pause, continue,
 * cancel), then what is ready to watch, grouped by series. Settings live behind the gear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    entries: List<DownloadEntry>,
    config: ServiceConfig?,
    accent: Color,
    wifiOnly: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    deleteWatched: Boolean,
    onDeleteWatched: (Boolean) -> Unit,
    onPlay: (DownloadEntry) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    /** Deletes finished downloads the server counts as watched; returns how many went. */
    onDeleteWatchedNow: suspend () -> Int,
    smartOn: Boolean,
    onSmart: (Boolean) -> Unit,
    smartCount: Int,
    onSmartCount: (Int) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingsOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val used = entries.sumOf { if (it.done) it.sizeBytes else it.downloadedBytes }
    val free = remember { runCatching { android.os.StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val active = entries.filter { it.inProgress }.sortedBy { it.addedAt }
    val groups = groupDownloads(entries.filter { it.done })

    Box(
        Modifier.fillMaxSize().background(Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen) }
                Text("Downloads", fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = "Download settings", tint = MatrixGreen) }
            }
            // Storage: what the downloads take against what the phone still has free.
            val share = if (used + free > 0) used.toFloat() / (used + free) else 0f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface)) {
                Box(Modifier.fillMaxWidth(share.coerceAtLeast(if (used > 0) 0.01f else 0f)).height(8.dp).background(accent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Downloads ${fmtBytes(used)}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.65f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("${fmtBytes(free)} free", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.65f), fontSize = 10.sp)
            }
            note?.let { Text(it, fontFamily = Mono, color = accent, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }

            if (entries.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Download, contentDescription = null, tint = MatrixGreen.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing downloaded yet", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text("Use \"download\" on a film or an episode to watch it offline.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (active.isNotEmpty()) {
                        item { SectionLabel("DOWNLOADING · ${active.size}") }
                        items(active, key = { "a-" + it.itemId }) { e ->
                            ActiveDownloadRow(
                                e, config, accent,
                                onPause = { onPause(e.itemId) }, onResume = { onResume(e.itemId) }, onCancel = { onDelete(e.itemId) },
                            )
                        }
                    }
                    if (groups.isNotEmpty()) {
                        item { SectionLabel("READY · ${groups.sumOf { it.entries.size }}", fmtBytes(groups.sumOf { g -> g.entries.sumOf { it.sizeBytes } })) }
                        items(groups, key = { "g-" + it.title + it.entries.first().itemId }) { g ->
                            if (g.isSeries) {
                                SeriesGroup(g, config, accent, onPlay, onDelete = { onDelete(it.itemId) })
                            } else {
                                val e = g.entries.first()
                                ReadyDownloadRow(
                                    e, config, accent, title = e.name,
                                    detail = listOf(e.subtitle, fmtBytes(e.sizeBytes)).filter { it.isNotBlank() }.joinToString(" · "),
                                    onPlay = { onPlay(e) }, onDelete = { onDelete(e.itemId) },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (settingsOpen) {
        var confirmAll by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, containerColor = Surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Download settings", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                SettingSwitch("Only download on Wi-Fi", "Waits for Wi-Fi instead of using mobile data.", wifiOnly, onWifiOnly)
                SettingSwitch("Delete after watching", "Removes a finished download once the server counts it as watched.", deleteWatched, onDeleteWatched)
                SettingSwitch(
                    "Smart downloads",
                    "Keeps the next $smartCount unwatched episodes of every downloaded series on the phone. Watched ones are deleted, the next ones load, also in the background.",
                    smartOn, onSmart,
                )
                if (smartOn) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes to keep", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        RoundAction(AppIcons.Remove, "Fewer", MatrixGreen) { if (smartCount > 1) onSmartCount(smartCount - 1) }
                        Text("$smartCount", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                        RoundAction(AppIcons.Add, "More", MatrixGreen) { if (smartCount < 10) onSmartCount(smartCount + 1) }
                    }
                }
                Text(
                    "Delete watched downloads now",
                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            val n = onDeleteWatchedNow()
                            note = if (n == 0) "No watched downloads to delete" else "Deleted $n watched download${if (n == 1) "" else "s"}"
                            settingsOpen = false
                        }
                    }.padding(vertical = 10.dp),
                )
                if (entries.isNotEmpty()) {
                    Text(
                        if (confirmAll) "Tap again to delete all ${entries.size} downloads" else "Delete all downloads",
                        fontFamily = Mono, color = ErrRed, fontSize = 13.sp, fontWeight = if (confirmAll) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (confirmAll) { settingsOpen = false; onClearAll() } else confirmAll = true
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
            Text(hint, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
