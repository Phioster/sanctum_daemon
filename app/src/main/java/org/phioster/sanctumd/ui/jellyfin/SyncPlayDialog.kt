package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.SyncGroup
import org.phioster.sanctumd.net.SyncPlay
import org.phioster.sanctumd.net.errorText
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Watch together: join a SyncPlay group from any Jellyfin app (web, iPhone, TV), or start one.
 * In a group, whatever anyone starts opens for everyone, and pause/seek act for all.
 */
@Composable
internal fun SyncPlayDialog(config: ServiceConfig, accent: Color, group: SyncGroup?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<SyncGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("Watch together") }

    suspend fun refresh() {
        groups = runCatching { SyncPlay.groups(config) }.onFailure { error = "error: ${errorText(it)}" }.getOrNull()
    }
    LaunchedEffect(group?.id) { if (group == null) refresh() }

    fun run(action: suspend () -> String) {
        busy = true
        scope.launch {
            val r = runCatching { action() }.getOrElse { "error: ${errorText(it)}" }
            busy = false
            if (r.startsWith("error")) error = r else onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("SyncPlay", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                if (group != null) {
                    Text(group.name, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${group.participants.size} watching · ${group.state.lowercase()}", fontFamily = Mono, color = accent, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    group.participants.forEach { Text("· $it", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Start anything and it plays for everyone. Pause and seeking work for all.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp,
                    )
                } else {
                    Text("Groups on the server", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    when {
                        groups == null && error == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        groups.isNullOrEmpty() -> Text("No group yet. Start one, the others join it from their Jellyfin app.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> groups!!.forEach { g ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !busy) { run { SyncPlay.join(config, g.id) } }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                                    Text(g.participants.joinToString(", "), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp)
                                }
                                Text("join", fontFamily = Mono, color = accent, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        label = { Text("New group", fontFamily = Mono) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, fontFamily = Mono, color = ErrRed, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (group != null) {
                TextButton(enabled = !busy, onClick = { run { SyncPlay.leave() } }) { Text("Leave", fontFamily = Mono, color = ErrRed) }
            } else {
                TextButton(enabled = !busy && newName.isNotBlank(), onClick = { run { SyncPlay.create(config, newName.trim()) } }) {
                    Text("Start group", fontFamily = Mono, color = accent)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
