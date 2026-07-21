package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.phioster.sanctumd.model.ServiceConfig
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
import org.phioster.sanctumd.ui.common.*

@Composable
internal fun JellyPoster(url: String, config: ServiceConfig, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, scale: ContentScale) {
    val ctx = LocalContext.current
    val model = ImageRequest.Builder(ctx).data(url).apply {
        config.customHeaders.forEach { (k, v) -> addHeader(k, v) }
        org.phioster.sanctumd.net.jellyfinImageHeaders(config).forEach { (k, v) -> addHeader(k, v) }
    }.build()
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = scale,
        modifier = modifier.clip(shape).background(Surface),
    )
}

@Composable
internal fun JellyPosterCard(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, width: androidx.compose.ui.unit.Dp = 120.dp, caption: Boolean = true, onClick: () -> Unit) {
    val h = width * 1.5f
    Column(Modifier.width(width).padding(end = 10.dp).clickable { onClick() }) {
        Box {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.width(width).height(h), RoundedCornerShape(6.dp), ContentScale.Crop)
            } else {
                Box(Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface))
            }
            if (item.progressPct > 0.01f) {
                LinearProgressIndicator(
                    progress = { item.progressPct },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = accent, trackColor = Black.copy(alpha = 0.6f),
                )
            }
        }
        if (caption) {
            Spacer(Modifier.height(4.dp))
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun JellyMediaRow(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.posterUrl.isNotBlank()) {
            JellyPoster(item.posterUrl, config, Modifier.width(46.dp).height(68.dp), RoundedCornerShape(4.dp), ContentScale.Crop)
        } else {
            Box(Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.isFolder) Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}

@Composable
internal fun JellyUserDialog(
    user: org.phioster.sanctumd.model.JellyUser,
    libraries: List<org.phioster.sanctumd.model.JellyLibrary>?,
    onDismiss: () -> Unit,
    onSave: (admin: Boolean, disabled: Boolean, allowDownloads: Boolean, enableAll: Boolean, folders: List<String>) -> Unit,
    onResetPassword: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var admin by remember { mutableStateOf(user.admin) }
    var disabled by remember { mutableStateOf(user.disabled) }
    var allowDownloads by remember { mutableStateOf(user.allowDownloads) }
    var enableAll by remember { mutableStateOf(user.enableAllFolders) }
    var folders by remember { mutableStateOf(user.enabledFolders.toSet()) }
    var newPw by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(user.name, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                JellyToggle("Administrator", admin) { admin = it }
                JellyToggle("Enabled", !disabled) { disabled = !it }
                JellyToggle("Allow downloads", allowDownloads) { allowDownloads = it }
                JellyToggle("Access all libraries", enableAll) { enableAll = it }
                if (!enableAll) {
                    Spacer(Modifier.height(4.dp))
                    when {
                        libraries == null -> Text("loading libraries…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        libraries.isEmpty() -> Text("no libraries found", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        else -> libraries.forEach { lib ->
                            val checked = folders.contains(lib.id)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    folders = if (checked) folders - lib.id else folders + lib.id
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (checked) "[x]" else "[ ]", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(lib.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                Spacer(Modifier.height(6.dp))
                Text("RESET PASSWORD", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                Field("New password", newPw, isPassword = true) { newPw = it }
                TextButton(enabled = newPw.isNotBlank(), onClick = { val p = newPw; newPw = ""; onResetPassword(p) }) {
                    Text("Set password", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                if (!confirmDelete) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete user", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete for real?", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("Yes", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                        TextButton(onClick = { confirmDelete = false }) { Text("No", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(admin, disabled, allowDownloads, enableAll, folders.toList()) }) {
                Text("Save", fontFamily = Mono, color = MatrixGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
internal fun JellyPodium(stats: List<org.phioster.sanctumd.model.JellyWatchStat>, accent: Color, solid: Boolean) {
    val top = stats.take(3)
    data class Slot(val rank: Int, val stat: org.phioster.sanctumd.model.JellyWatchStat?)
    val slots = when (top.size) {
        0 -> emptyList()
        1 -> listOf(Slot(1, top[0]))
        2 -> listOf(Slot(1, top[0]), Slot(2, top[1]))
        else -> listOf(Slot(2, top[1]), Slot(1, top[0]), Slot(3, top[2]))
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { slot ->
            val barH = when (slot.rank) { 1 -> 64.dp; 2 -> 46.dp; else -> 34.dp }
            val medal = when (slot.rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(medal, fontSize = 18.sp)
                Text(
                    slot.stat?.name ?: "—",
                    fontFamily = Mono, color = if (solid) Black else MatrixGreen,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    slot.stat?.let { fmtWatch(it.seconds) } ?: "",
                    fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(barH)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(accent.copy(alpha = if (slot.rank == 1) 0.9f else 0.5f)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "#${slot.rank}",
                        fontFamily = Mono, color = if (solid) MatrixGreen else Black,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun JellyTaskRow(item: org.phioster.sanctumd.model.JellyTask, accent: Color, onRun: () -> Unit) {
    val running = item.state.equals("Running", true)
    Column(Modifier.fillMaxWidth().clickable(enabled = !running) { onRun() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                if (running) "${item.progress}%" else if (item.state.isNotBlank()) "▶ run" else "",
                fontFamily = Mono,
                color = if (running) Color(0xFFFFAA00) else MatrixGreen,
                fontSize = 11.sp,
            )
        }
        if (item.lastResult.isNotBlank() && !running) {
            Spacer(Modifier.height(2.dp))
            Text(
                "last: ${item.lastResult}${if (item.lastRun.isNotBlank()) " · ${item.lastRun}" else ""}",
                fontFamily = Mono,
                color = if (item.lastResult.equals("Completed", true)) MatrixGreen.copy(alpha = 0.6f) else ErrRed,
                fontSize = 10.sp,
            )
        } else if (item.lastRun.isNotBlank() && !running) {
            Spacer(Modifier.height(2.dp))
            Text("last run ${item.lastRun}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyActivityRow(item: org.phioster.sanctumd.model.JellyActivity, accent: Color) {
    val sevColor = when (item.severity.lowercase()) {
        "error", "fatal" -> ErrRed
        "warn", "warning" -> Color(0xFFFFAA00)
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text("${item.date}${if (item.overview.isNotBlank()) " · ${item.overview}" else ""}", fontFamily = Mono, color = sevColor, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyDeviceRow(item: org.phioster.sanctumd.model.JellyDevice, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.app.isNotBlank()) Text(item.app, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                if (item.user.isNotBlank()) append(item.user)
                if (item.lastActivity.isNotBlank()) { if (isNotEmpty()) append(" · "); append(item.lastActivity) }
            },
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyLibraryRow(item: org.phioster.sanctumd.model.JellyLibrary, accent: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.collectionType.isNotBlank()) Text(item.collectionType, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (item.locations.isEmpty()) "no folders" else item.locations.joinToString(" · "),
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyPluginRow(item: org.phioster.sanctumd.model.JellyPlugin, accent: Color, onClick: () -> Unit) {
    val statusColor = when (item.status.lowercase()) {
        "active" -> MatrixGreen
        "disabled" -> MatrixGreen.copy(alpha = 0.4f)
        "restart" -> Color(0xFFFFAA00)
        else -> ErrRed
    }
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(item.status.lowercase(), fontFamily = Mono, color = statusColor, fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text("v${item.version}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyLogRow(item: org.phioster.sanctumd.model.JellyLogFile, accent: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(item.size, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(item.date, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyChannelRow(item: org.phioster.sanctumd.model.JellyChannel, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildString {
                    if (item.number.isNotBlank()) append("${item.number} · ")
                    append(item.name)
                },
                fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        if (item.nowPlaying.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("▶ ${item.nowPlaying}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyAddLibraryDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, path: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("movies") }
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New library", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Field("Name", name) { name = it }
                Spacer(Modifier.height(6.dp))
                Text("TYPE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("movies", "tvshows", "music", "books", "mixed").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t, fontFamily = Mono, fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Field("Folder path on server", path) { path = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && path.isNotBlank(), onClick = {
                onCreate(name.trim(), if (type == "mixed") "" else type, path.trim())
            }) { Text("Create", fontFamily = Mono, color = MatrixGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
internal fun JellyLibraryDialog(
    library: org.phioster.sanctumd.model.JellyLibrary,
    accent: Color,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onAddPath: (String) -> Unit,
    onRemovePath: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(library.name) }
    var newPath by remember { mutableStateOf("") }
    var confirmRemovePath by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(library.name, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Field("Name", name) { name = it } }
                    if (name.isNotBlank() && name.trim() != library.name) {
                        TextButton(onClick = { onRename(name.trim()) }) { Text("Rename", fontFamily = Mono, color = accent, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("FOLDERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                library.locations.forEach { loc ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(loc, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            if (confirmRemovePath == loc) "remove?" else "✕",
                            fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                if (confirmRemovePath == loc) onRemovePath(loc) else confirmRemovePath = loc
                            }.padding(start = 8.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Field("Add folder path", newPath) { newPath = it } }
                    if (newPath.isNotBlank()) {
                        TextButton(onClick = { onAddPath(newPath.trim()) }) { Text("Add", fontFamily = Mono, color = accent, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (confirmDelete) "Really delete this library? (media files stay on disk)" else "Delete library",
                    fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                    modifier = Modifier.clickable { if (confirmDelete) onDelete() else confirmDelete = true }.padding(vertical = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
internal fun JellyPluginDialog(
    plugin: org.phioster.sanctumd.model.JellyPlugin,
    accent: Color,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onUninstall: () -> Unit,
) {
    var confirmUninstall by remember { mutableStateOf(false) }
    val disabled = plugin.status.equals("Disabled", true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(plugin.name, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Text("v${plugin.version} · ${plugin.status}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                if (plugin.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(plugin.description, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (disabled) "Enable plugin" else "Disable plugin",
                    fontFamily = Mono, color = accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { onToggle() }.padding(vertical = 4.dp),
                )
                if (plugin.canUninstall) {
                    Text(
                        if (confirmUninstall) "Really uninstall?" else "Uninstall",
                        fontFamily = Mono, color = ErrRed, fontSize = 13.sp,
                        modifier = Modifier.clickable { if (confirmUninstall) onUninstall() else confirmUninstall = true }.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
internal fun JellyCatalogDialog(
    catalog: List<org.phioster.sanctumd.model.JellyPackage>?,
    accent: Color,
    onDismiss: () -> Unit,
    onInstall: (org.phioster.sanctumd.model.JellyPackage) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var confirmInstall by remember { mutableStateOf<String?>(null) } // package guid
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Plugin catalog", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Field("Search", query) { query = it }
                Spacer(Modifier.height(6.dp))
                val list = catalog?.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
                    when {
                        catalog == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f))
                        list.isNullOrEmpty() -> Text("no packages", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f))
                        else -> LazyColumn {
                            items(list) { pkg ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(pkg.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(
                                            when {
                                                pkg.installed -> "installed"
                                                confirmInstall == pkg.guid -> "install?"
                                                else -> "install"
                                            },
                                            fontFamily = Mono,
                                            color = if (pkg.installed) MatrixGreen.copy(alpha = 0.4f) else accent,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable(enabled = !pkg.installed) {
                                                if (confirmInstall == pkg.guid) onInstall(pkg) else confirmInstall = pkg.guid
                                            }.padding(start = 8.dp),
                                        )
                                    }
                                    if (pkg.description.isNotBlank()) {
                                        Text(
                                            "${if (pkg.version.isNotBlank()) "v${pkg.version} · " else ""}${pkg.description}",
                                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
internal fun JellySessionRow(
    item: org.phioster.sanctumd.model.JellySession,
    accent: Color,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMessage: () -> Unit,
) {
    val playing = item.nowPlaying.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (playing) item.nowPlaying else "${item.user} · idle",
                fontFamily = Mono, color = if (playing) MatrixGreen else MatrixGreen.copy(alpha = 0.5f),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (playing) Text(if (item.paused) "paused" else "playing", fontFamily = Mono, color = if (item.paused) Color(0xFFFFAA00) else MatrixGreen, fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(item.user)
                if (item.device.isNotBlank()) append(" · ${item.device}")
                if (playing && item.subtitle.isNotBlank()) append(" · ${item.subtitle}")
                if (!playing && item.lastActivity.isNotBlank()) append(" · ${item.lastActivity}")
            },
            fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (playing) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { item.progressPct }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
            if (item.canControl) {
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = onPlayPause) { Text(if (item.paused) "▶ play" else "❚❚ pause", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                    TextButton(onClick = onStop) { Text("■ stop", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                    TextButton(onClick = onMessage) { Text("✉ msg", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("no remote control", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@Composable
internal fun JellyUserRow(item: org.phioster.sanctumd.model.JellyUser, accent: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, fontFamily = Mono, color = if (item.disabled) MatrixGreen.copy(alpha = 0.4f) else MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.disabled) Text("disabled", fontFamily = Mono, color = ErrRed, fontSize = 10.sp)
            else if (item.admin) Text("admin", fontFamily = Mono, color = accent, fontSize = 10.sp)
        }
        if (item.lastActivity.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("last active ${item.lastActivity}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}
