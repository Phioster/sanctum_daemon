package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
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

/** Where the manual import opens: a folder, and whether to scan it straight away. */
internal data class ArrImportStart(val path: String, val scan: Boolean)

/**
 * The manual import, end to end: browse or jump to a folder, scan it, tick what should go in,
 * hand-assign what the service could not match, and afterwards clear up what stayed blocked in
 * the queue. It stays composed until [onDismiss], because the blocked list only appears once
 * the wizard itself is gone.
 */
@Composable
internal fun ArrImportDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    start: ArrImportStart,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var wizardOpen by remember { mutableStateOf(true) }
    var importFolder by remember { mutableStateOf(start.path) }
    // A counter, not a flag: a LaunchedEffect is cancelled the moment its key changes, so an
    // effect that cleared its own boolean key killed the very scan it had just started.
    var scanRequest by remember { mutableStateOf(if (start.scan) 1 else 0) }
    var importItems by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrImportItem>?>(null) }
    var importScanning by remember { mutableStateOf(false) }
    var importSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var blockedQueue by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrQueueItem>>(emptyList()) }
    // Assigning a target to a row the service could not match by itself.
    var assignRow by remember { mutableStateOf<Int?>(null) }
    var assignEpisodeRow by remember { mutableStateOf<Int?>(null) }
    var assignLibrary by remember { mutableStateOf<List<ArrLibraryItem>?>(null) }
    var assignQuery by remember { mutableStateOf("") }

    suspend fun runImportScan() {
        importScanning = true; importItems = null; importSelected = emptySet()
        importItems = runCatching { vm.arrManualScan(config, importFolder.trim()) }.getOrElse {
            onMessage("error: ${it.message}"); emptyList()
        }
        importSelected = importItems!!.mapIndexedNotNull { i, it -> if (it.importable) i else null }.toSet()
        importScanning = false
        // Next time the browser opens here rather than at the root.
        if (importFolder.isNotBlank()) vm.rememberImportPath(importFolder.trim())
    }


    LaunchedEffect(scanRequest) {
        if (scanRequest > 0 && wizardOpen) runImportScan()
    }

    if (wizardOpen) {
        val items = importItems
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("Manual import", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    ArrFolderBrowser(vm, config, accent, importFolder) { importFolder = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scope.launch { runImportScan() } },
                        enabled = importFolder.isNotBlank() && !importScanning,
                    ) { Text(if (importScanning) "scanning…" else "Scan", fontFamily = Mono) }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        when {
                            items == null -> {}
                            items.isEmpty() -> Text("no importable files", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> items.forEachIndexed { i, it ->
                                val checked = i in importSelected
                                val unmatched = !it.importable && config.type == ServiceType.RADARR
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        importSelected = if (checked) importSelected - i else importSelected + i
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (checked) "[x] " else "[ ] ", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(it.relativePath, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(AppIcons.Forward, contentDescription = null, tint = accent.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("${it.matchedTitle}${if (it.quality.isNotBlank()) " · ${it.quality}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (it.rejection.isNotBlank()) Text(it.rejection, fontFamily = Mono, color = WarnAmber, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (unmatched) {
                                        Text(
                                            "assign",
                                            fontFamily = Mono, color = accent, fontSize = 11.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    // Sonarr needs episode ids, not just a series. Its own two-step picker.
                                                    if (config.type == ServiceType.SONARR) {
                                                        assignEpisodeRow = i
                                                    } else {
                                                        assignRow = i; assignQuery = ""
                                                        if (assignLibrary == null) scope.launch {
                                                            assignLibrary = runCatching { vm.arrLibraryList(config) }.getOrDefault(emptyList())
                                                        }
                                                    }
                                                }
                                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = items != null && importSelected.isNotEmpty(),
                    onClick = {
                        val chosen = items!!.filterIndexed { i, _ -> i in importSelected }.map { it.rawJson }
                        wizardOpen = false
                        scope.launch {
                            onMessage(vm.arrManualImport(config, chosen))
                            vm.refreshAll()
                            // A hand-assigned import leaves its queue entry on importBlocked and the
                            // source file on disk twice; surface those rather than leaving them to rot.
                            blockedQueue = runCatching { vm.arrBlockedQueue(config) }.getOrDefault(emptyList())
                            // Nothing left behind means nothing left to show.
                            if (blockedQueue.isEmpty()) onDismiss()
                        }
                    },
                ) { Text("Import (${importSelected.size})", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (blockedQueue.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("Blocked in queue", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "These finished downloads were never imported automatically. Removing them " +
                            "also deletes the leftover copy from the download client.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    blockedQueue.forEach { q ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                q.title, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (q.outputPath.isNotBlank()) {
                                TextButton(onClick = {
                                    blockedQueue = emptyList()
                                    importFolder = q.outputPath; importItems = null; importSelected = emptySet()
                                    wizardOpen = true; scanRequest++
                                }) {
                                    Text("import", fontFamily = Mono, color = accent, fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    onMessage(vm.arrRemove(config, q.id))
                                    blockedQueue = runCatching { vm.arrBlockedQueue(config) }.getOrDefault(emptyList())
                                    vm.refreshAll()
                                }
                            }) { Text("remove", fontFamily = Mono, color = ErrRed, fontSize = 11.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { blockedQueue = emptyList(); onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
            },
        )
    }

    assignEpisodeRow?.let { rowIdx ->
        SonarrAssignDialog(vm, config, accent, onDismiss = { assignEpisodeRow = null }) { a ->
            val current = importItems
            if (current != null) {
                val patched = vm.arrAssignImportEpisodes(current[rowIdx].rawJson, a.seriesId, a.seriesTitle, a.episodeIds)
                importItems = current.toMutableList().also { l ->
                    l[rowIdx] = l[rowIdx].copy(rawJson = patched, importable = true, matchedTitle = a.label, rejection = "")
                }
                importSelected = importSelected + rowIdx
            }
            assignEpisodeRow = null
        }
    }

    assignRow?.let { rowIdx ->
        AlertDialog(
            onDismissRequest = { assignRow = null },
            containerColor = Surface,
            title = { Text("Assign to movie", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 460.dp)) {
                    Field("Filter", assignQuery) { assignQuery = it }
                    Spacer(Modifier.height(8.dp))
                    val lib = assignLibrary
                    when {
                        lib == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> {
                            val filtered = lib.filter { it.title.contains(assignQuery, ignoreCase = true) }.sortedBy { it.title.lowercase() }
                            if (filtered.isEmpty()) Text("no match", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else Column(Modifier.verticalScroll(rememberScrollState())) {
                                filtered.forEach { mv ->
                                    Text(
                                        "${mv.title}${if (mv.year > 0) " (${mv.year})" else ""}",
                                        fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val current = importItems
                                                if (current != null) {
                                                    val patched = vm.arrAssignImportMovie(current[rowIdx].rawJson, mv.id, mv.title)
                                                    importItems = current.toMutableList().also { l ->
                                                        l[rowIdx] = l[rowIdx].copy(rawJson = patched, importable = true, matchedTitle = mv.title, rejection = "")
                                                    }
                                                    importSelected = importSelected + rowIdx
                                                }
                                                assignRow = null
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { assignRow = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
