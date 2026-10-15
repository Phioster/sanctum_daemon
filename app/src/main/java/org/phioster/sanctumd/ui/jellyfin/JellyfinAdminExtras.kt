package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.net.JellyBackup
import org.phioster.sanctumd.net.JellyEpisodeLine
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

@Composable
internal fun JellyEpisodeLineRow(item: JellyEpisodeLine, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.where, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.date.isNotBlank()) Text(item.date, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Text(item.name, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyBackupRow(item: JellyBackup, accent: Color, onRestore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.created, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                Text(
                    listOf(item.serverVersion.takeIf { it.isNotBlank() }?.let { "v$it" }, item.path.substringAfterLast('/'))
                        .filterNotNull().joinToString(" · "),
                    fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRestore) { Text("restore", fontFamily = Mono, color = ErrRed.copy(alpha = 0.85f)) }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

/** What goes into a new backup. The database always does; the rest is what makes it large. */
@Composable
internal fun JellyCreateBackupDialog(onDismiss: () -> Unit, onCreate: (metadata: Boolean, trickplay: Boolean, subtitles: Boolean) -> Unit) {
    var metadata by remember { mutableStateOf(false) }
    var trickplay by remember { mutableStateOf(false) }
    var subtitles by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New backup", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Text("Database, users and settings are always included.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("metadata (images, nfo)", metadata) { v: Boolean -> metadata = v },
                    Triple("trickplay previews", trickplay) { v: Boolean -> trickplay = v },
                    Triple("extracted subtitles", subtitles) { v: Boolean -> subtitles = v },
                ).forEach { (label, on, set) ->
                    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = set)
                    }
                }
                Text("Large backups can take minutes.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(metadata, trickplay, subtitles) }) { Text("Create", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Restoring rolls the whole server back; it asks with the date spelled out. */
@Composable
internal fun JellyRestoreBackupDialog(backup: JellyBackup, onDismiss: () -> Unit, onRestore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Restore backup?", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Text(
                "The server restarts and goes back to ${backup.created}. Everything since then is lost: " +
                    "watch history, new users, settings, library changes.",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 13.sp,
            )
        },
        confirmButton = { TextButton(onClick = onRestore) { Text("Restore", fontFamily = Mono, color = ErrRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
