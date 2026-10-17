package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.JellyPlaylist
import org.phioster.sanctumd.net.JellyPlaylistDetail
import org.phioster.sanctumd.net.formatDuration
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** A playlist as the browser opens it: a folder whose order is the point. */
internal fun JellyPlaylist.asMediaItem() = JellyMediaItem(id, name, "Playlist", "$count items", posterUrl, isFolder = true, progressPct = 0f)

/** The home row: every playlist, plus the way to start one. */
@Composable
internal fun PlaylistRow(playlists: List<JellyPlaylist>, accent: Color, onOpen: (JellyPlaylist) -> Unit, onNew: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        playlists.forEach { p ->
            Column(Modifier.width(110.dp).clickable { onOpen(p) }) {
                Box(Modifier.size(110.dp).clip(RoundedCornerShape(6.dp)).background(Surface)) {
                    if (p.posterUrl.isNotBlank()) {
                        AsyncImage(model = p.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(110.dp))
                    }
                }
                Text(p.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${p.count} items", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable { onNew() },
            contentAlignment = Alignment.Center,
        ) { Text("+ new", fontFamily = Mono, color = accent, fontSize = 13.sp) }
    }
}

/** Name for a new playlist; [onCreate] gets the trimmed name. */
@Composable
internal fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New playlist", fontFamily = Mono, color = MatrixGreen) },
        text = { Field("Name", name) { name = it } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim()) }) { Text("Create", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Puts [itemIds] into an existing playlist or a new one. */
@Composable
internal fun AddToPlaylistDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    itemIds: List<String>,
    accent: Color,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<JellyPlaylist>?>(null) }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { lists = runCatching { vm.jellyfinPlaylistList(config) }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text(if (itemIds.size == 1) "Add to playlist" else "Add ${itemIds.size} to playlist", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                val l = lists
                when {
                    l == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    l.isEmpty() -> Text("no playlists yet", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> l.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                busy = true
                                scope.launch { onDone(vm.jellyfinPlaylistAdd(config, p.id, itemIds).let { if (it.startsWith("error")) it else "$it to ${p.name}" }) }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.count}", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                Field("New playlist", newName) { newName = it }
                TextButton(enabled = newName.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = vm.jellyfinPlaylistCreate(config, newName, itemIds)
                        onDone(if (r.startsWith("error")) r else "created ${newName.trim()}")
                    }
                }) { Text("+ create with ${if (itemIds.size == 1) "it" else "them"}", fontFamily = Mono, color = accent) }
            }
        },
        confirmButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Who else sees the playlist: everyone (public) or chosen users, each with or without edit rights. */
@Composable
private fun SharePlaylistDialog(vm: DashboardViewModel, config: ServiceConfig, pl: JellyPlaylistDetail, onDismiss: () -> Unit, onChanged: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyUser>?>(null) }
    var shares by remember { mutableStateOf(pl.shares) }
    var public by remember { mutableStateOf(pl.public) }
    LaunchedEffect(Unit) { users = runCatching { vm.jellyfinUserList(config) }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Share ${pl.name}", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("public (every user sees it)", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(checked = public, onCheckedChange = { on ->
                        public = on
                        scope.launch { onChanged(vm.jellyfinPlaylistUpdate(config, pl.id, public = on)) }
                    })
                }
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                users?.forEach { u ->
                    val shared = u.id in shares
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        if (shared) {
                            Text("edit", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 10.sp)
                            Checkbox(checked = shares[u.id] == true, onCheckedChange = { can ->
                                shares = shares + (u.id to can)
                                scope.launch { onChanged(vm.jellyfinPlaylistShare(config, pl.id, u.id, can)) }
                            })
                        }
                        Switch(checked = shared, onCheckedChange = { on ->
                            shares = if (on) shares + (u.id to false) else shares - u.id
                            scope.launch { onChanged(vm.jellyfinPlaylistShare(config, pl.id, u.id, if (on) false else null)) }
                        })
                    }
                } ?: Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/**
 * An opened playlist: its running order with move and remove, plus play, rename, share and
 * delete. Music plays as one queue; a list with films opens the tapped title instead.
 */
internal fun LazyListScope.jellyfinPlaylistLevel(
    st: JellyfinBrowseState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    scope: CoroutineScope,
    act: JellyfinMediaActions,
    playingId: String,
) {
    val here = st.stack.last()
    val pl = st.playlist
    fun reload() = scope.launch { st.playlist = runCatching { vm.jellyfinPlaylistDetailOf(config, here.id) }.getOrNull() ?: st.playlist }
    item {
        var rename by remember { mutableStateOf(false) }
        var share by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        BrowseChip("‹ back", accent) { st.stack = st.stack.dropLast(1) }
        Spacer(Modifier.height(10.dp))
        Text(pl?.name ?: here.name, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        pl?.let { p ->
            val total = p.entries.sumOf { it.durationMs }
            Text("${p.entries.size} items · ${formatDuration(total)}${if (p.public) " · public" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pl != null && pl.tracks.isNotEmpty()) {
                BrowseChip("play all", MatrixGreen, AppIcons.Play) { vm.playJellyfinTracks(config, pl.tracks, 0, shuffle = false) }
                BrowseChip("shuffle", MatrixGreen, AppIcons.Shuffle) { vm.playJellyfinTracks(config, pl.tracks, 0, shuffle = true) }
            }
            BrowseChip("rename", MatrixGreen.copy(alpha = 0.85f), AppIcons.Edit) { rename = true }
            if (pl != null) BrowseChip("share", MatrixGreen.copy(alpha = 0.85f), AppIcons.Open) { share = true }
            BrowseChip(if (confirmDelete) "tap again to delete" else "delete", ErrRed) {
                if (confirmDelete) {
                    scope.launch {
                        val r = vm.jellyfinPlaylistDelete(config, here.id)
                        act.message(r)
                        if (!r.startsWith("error")) st.stack = st.stack.dropLast(1)
                    }
                } else confirmDelete = true
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
        if (rename) {
            var name by remember { mutableStateOf(pl?.name ?: here.name) }
            AlertDialog(
                onDismissRequest = { rename = false },
                containerColor = Surface,
                title = { Text("Rename playlist", fontFamily = Mono, color = MatrixGreen) },
                text = { Field("Name", name) { name = it } },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        rename = false
                        scope.launch { act.message(vm.jellyfinPlaylistUpdate(config, here.id, name = name)); reload() }
                    }) { Text("Save", fontFamily = Mono, color = MatrixGreen) }
                },
                dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
            )
        }
        if (share && pl != null) SharePlaylistDialog(vm, config, pl, onDismiss = { share = false; reload() }) { act.message(it) }
    }
    when {
        pl == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
        pl.entries.isEmpty() -> item { Text("empty. Add titles from their menu, or pick several in a library with a long press.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
        else -> itemsIndexed(pl.entries, key = { _, e -> e.entryId }) { i, e ->
            val playing = e.item.id == playingId
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (pl.tracks.isNotEmpty()) vm.playJellyfinTracks(config, pl.tracks, i, shuffle = false) else act.open(e.item)
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.item.name, fontFamily = Mono, color = if (playing) accent else MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOf(e.item.subtitle, if (e.durationMs > 0) formatDuration(e.durationMs) else "").filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Moved locally first so the list doesn't jump back while the server answers.
                fun move(to: Int) {
                    val list = pl.entries.toMutableList()
                    list.add(to, list.removeAt(i))
                    st.playlist = pl.copy(entries = list, tracks = if (pl.tracks.isEmpty()) pl.tracks else pl.tracks.toMutableList().also { it.add(to, it.removeAt(i)) })
                    scope.launch {
                        val r = vm.jellyfinPlaylistMove(config, here.id, e.entryId, to)
                        if (r.startsWith("error")) { act.message(r); reload() }
                    }
                }
                IconButton(enabled = i > 0, onClick = { move(i - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.MoveUp, contentDescription = "Move up", tint = MatrixGreen.copy(alpha = if (i > 0) 0.8f else 0.2f), modifier = Modifier.size(18.dp))
                }
                IconButton(enabled = i < pl.entries.size - 1, onClick = { move(i + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.MoveDown, contentDescription = "Move down", tint = MatrixGreen.copy(alpha = if (i < pl.entries.size - 1) 0.8f else 0.2f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    st.playlist = pl.copy(entries = pl.entries.filterNot { it.entryId == e.entryId }, tracks = if (pl.tracks.isEmpty()) pl.tracks else pl.tracks.filterIndexed { j, _ -> j != i })
                    scope.launch {
                        val r = vm.jellyfinPlaylistRemove(config, here.id, listOf(e.entryId))
                        if (r.startsWith("error")) { act.message(r); reload() }
                    }
                }, modifier = Modifier.size(32.dp)) {
                    Icon(AppIcons.Cancel, contentDescription = "Remove", tint = ErrRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.06f))
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
}
