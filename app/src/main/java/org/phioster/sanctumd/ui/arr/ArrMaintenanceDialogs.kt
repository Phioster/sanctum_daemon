package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.ArrBackup
import org.phioster.sanctumd.net.ArrExclusion
import org.phioster.sanctumd.net.ArrLogLine
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

@Composable
private fun Status(text: String) = Text(text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)

@Composable
private fun Message(text: String?) {
    text?.let { Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 11.sp) }
}

/** The app's own log, newest first; a tap on a line shows its exception. Sonarr, Radarr, Lidarr and Prowlarr alike. */
@Composable
internal fun ArrLogsDialog(vm: DashboardViewModel, config: ServiceConfig, onDismiss: () -> Unit) {
    var level by remember { mutableStateOf("warn") }
    var lines by remember { mutableStateOf<List<ArrLogLine>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(level) {
        lines = null; error = null; open = null
        runCatching { vm.arrLogLines(config, level) }.onSuccess { lines = it }.onFailure { error = it.message ?: "failed" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("${config.label} log", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Row {
                    listOf("error", "warn", "info").forEach { l ->
                        FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l, fontFamily = Mono, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Column(Modifier.heightIn(min = 120.dp, max = 460.dp).verticalScroll(rememberScrollState())) {
                    val ls = lines
                    when {
                        error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
                        ls == null -> Status("loading…")
                        ls.isEmpty() -> Status("nothing at this level")
                        else -> ls.forEachIndexed { i, e ->
                            val c = when (e.level) {
                                "error", "fatal" -> ErrRed
                                "warn" -> WarnAmber
                                else -> MatrixGreen.copy(alpha = 0.75f)
                            }
                            Column(Modifier.fillMaxWidth().clickable(enabled = e.exception.isNotBlank()) { open = if (open == i) null else i }.padding(vertical = 3.dp)) {
                                Text("${e.time}  ${e.level.uppercase()}  ${e.logger}", fontFamily = Mono, color = c.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.message, fontFamily = Mono, color = c, fontSize = 10.sp, lineHeight = 13.sp)
                                if (open == i) Text(e.exception, fontFamily = Mono, color = c.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 12.sp)
                                else if (e.exception.isNotBlank()) Text("tap for details", fontFamily = Mono, color = c.copy(alpha = 0.4f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Backups the app made itself; restoring stays in its web UI, since it restarts the app. */
@Composable
internal fun ArrBackupsDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ArrBackup>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    suspend fun reload() { list = runCatching { vm.arrBackupList(config) }.getOrNull() ?: list ?: emptyList() }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Backups", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Message(message)
                Text(
                    "+ back up now", fontFamily = Mono, color = accent, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            message = vm.arrBackupNow(config)
                            // The command runs in the background; give it a moment before reading the list again.
                            kotlinx.coroutines.delay(4000)
                            reload()
                        }
                    }.padding(vertical = 6.dp),
                )
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                val l = list
                when {
                    l == null -> Status("loading…")
                    l.isEmpty() -> Status("no backups")
                    else -> l.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.time, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                                Text("${b.type} · ${sizeLabel(b.sizeBytes)}", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                if (confirmDelete == b.id) {
                                    confirmDelete = null
                                    scope.launch { message = vm.arrBackupDelete(config, b.id); reload() }
                                } else confirmDelete = b.id
                            }) { Text(if (confirmDelete == b.id) "sure?" else "delete", fontFamily = Mono, color = ErrRed, fontSize = 11.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Restoring is done in the web UI: it restarts ${config.label}.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Titles an import list must not add again; removing one lets the list add it next sync. */
@Composable
internal fun ArrExclusionsDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ArrExclusion>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { list = runCatching { vm.arrExclusionList(config) }.getOrNull() ?: list ?: emptyList() }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Import exclusions", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Message(message)
                val l = list
                when {
                    l == null -> Status("loading…")
                    l.isEmpty() -> Status("nothing excluded")
                    else -> l.forEach { x ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(x.title.ifBlank { "#${x.externalId}" } + (x.year?.let { " ($it)" } ?: ""), fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { scope.launch { message = vm.arrExclusionRemove(config, x.id); reload() } }) {
                                Text("remove", fontFamily = Mono, color = accent, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
