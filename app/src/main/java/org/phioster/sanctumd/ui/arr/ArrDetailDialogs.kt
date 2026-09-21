package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The five things the detail screen can ask on top of itself: an album's track list, moving the
 * item to another folder, deleting it, the release picker, and the confirmation before grabbing
 * one. Which of them is open is [state]'s business; this only draws them.
 */
@Composable
internal fun ArrDetailDialogs(
    state: ArrDetailState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    itemId: Int,
    accent: Color,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val st = state
        st.trackAlbum?.let { al ->
            AlertDialog(
                onDismissRequest = { st.trackAlbum = null },
                containerColor = Surface,
                title = { Text("${al.title}${if (al.year.isNotBlank()) " (${al.year})" else ""}", fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        Text("${al.trackFileCount}/${al.trackCount} tracks", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        val tr = st.tracks
                        when {
                            tr == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            tr.isEmpty() -> Text("no tracks", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> tr.forEach { t ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.trackNumber.padStart(2, ' '), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t.title, fontFamily = Mono, color = if (t.hasFile) MatrixGreen else MatrixGreen.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (t.duration.isNotBlank()) Text(t.duration, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val a = al; st.trackAlbum = null
                        openReleases(movieId = null, episodeId = null, albumId = a.id, title = a.title)
                    }) { Text("Search releases", fontFamily = Mono, color = MatrixGreen) }
                },
                dismissButton = { TextButton(onClick = { st.trackAlbum = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
            )
        }

        if (st.showMove) {
            AlertDialog(
                onDismissRequest = { if (!st.moving) st.showMove = false },
                containerColor = Surface,
                title = { Text("Move to folder", fontFamily = Mono, color = MatrixGreen) },
                text = {
                    Column {
                        Text(
                            "The files move with the entry. Nothing is re-downloaded.",
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        when (val folders = st.moveTargets) {
                            null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> if (folders.isEmpty()) {
                                Text("no root folders", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            } else folders.forEach { path ->
                                Text(
                                    path,
                                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !st.moving) {
                                            st.moving = true
                                            scope.launch {
                                                st.actionMsg = vm.arrMoveItem(config, itemId, path)
                                                st.detail = runCatching { vm.arrDetailOf(config, itemId) }.getOrNull() ?: st.detail
                                                st.moving = false
                                                st.showMove = false
                                            }
                                        }
                                        .padding(vertical = 9.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { if (!st.moving) st.showMove = false }) {
                        Text(if (st.moving) "moving…" else "Cancel", fontFamily = Mono, color = MatrixGreen)
                    }
                },
            )
        }

        if (st.confirmDelete) {
            AlertDialog(
                onDismissRequest = { st.confirmDelete = false },
                containerColor = Surface,
                title = { Text("Delete ${st.detail?.title ?: ""}?", fontFamily = Mono, color = MatrixGreen) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also delete files", fontFamily = Mono, color = MatrixGreen, modifier = Modifier.weight(1f))
                        Switch(checked = st.deleteFiles, onCheckedChange = { st.deleteFiles = it })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val df = st.deleteFiles
                        st.confirmDelete = false
                        scope.launch {
                            val r = vm.arrDeleteItem(config, itemId, df)
                            if (!r.startsWith("error")) onBack() else st.actionMsg = r
                        }
                    }) { Text("Delete", fontFamily = Mono, color = ErrRed) }
                },
                dismissButton = { TextButton(onClick = { st.confirmDelete = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
            )
        }

        if (st.pickerOpen) {
            AlertDialog(
                onDismissRequest = { st.pickerOpen = false },
                containerColor = Surface,
                title = { Text("Releases", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    val rs = st.releases
                    Column(Modifier.heightIn(max = 460.dp)) {
                        Text(st.pickerTitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        when {
                            rs == null -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            rs.isEmpty() -> Text("no releases", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                rs.forEach { rel -> ArrReleaseRow(rel, accent) { st.confirmGrab = rel } }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { st.pickerOpen = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
            )
        }

        st.confirmGrab?.let { rel ->
            AlertDialog(
                onDismissRequest = { st.confirmGrab = null },
                containerColor = Surface,
                title = { Text("Grab release", fontFamily = Mono, color = MatrixGreen) },
                text = { Text(rel.title, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        val r = rel
                        st.confirmGrab = null; st.pickerOpen = false
                        scope.launch { st.actionMsg = vm.arrGrabRelease(config, r.guid, r.indexerId) }
                    }) { Text("Grab", fontFamily = Mono, color = MatrixGreen) }
                },
                dismissButton = { TextButton(onClick = { st.confirmGrab = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
            )
        }
}
