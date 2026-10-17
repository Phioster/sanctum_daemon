package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.ArrProvider
import org.phioster.sanctumd.net.ArrRename
import org.phioster.sanctumd.net.ArrTag
import org.phioster.sanctumd.net.cleanTagLabel
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.SurfaceHi

@Composable
private fun Status(text: String) = Text(text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)

@Composable
private fun Message(text: String?) {
    text?.let { Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp)) }
}

/** Only the file name: the folders are the same on both sides and would push the change off screen. */
private fun fileName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

/** What the naming scheme would change, ticked files get renamed. */
@Composable
internal fun ArrRenameDialog(vm: DashboardViewModel, config: ServiceConfig, itemId: Int, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ArrRename>?>(null) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itemId) {
        runCatching { vm.arrRenameList(config, itemId) }
            .onSuccess { l -> list = l; picked = l.map { it.fileId }.toSet() }
            .onFailure { message = "error: ${it.message}"; list = emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Rename files", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Message(message)
                val l = list
                when {
                    l == null -> Status("loading…")
                    l.isEmpty() -> Status("Every file already matches the naming scheme.")
                    else -> l.forEach { r ->
                        val on = r.fileId in picked
                        Row(
                            Modifier.fillMaxWidth().clickable { picked = if (on) picked - r.fileId else picked + r.fileId }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(if (on) AppIcons.Selected else AppIcons.NotSelected, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                if (r.label.isNotBlank()) Text(r.label, fontFamily = Mono, color = accent, fontSize = 11.sp)
                                Text(fileName(r.existing), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text(fileName(r.renamed), fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                    }
                }
            }
        },
        confirmButton = {
            if (!list.isNullOrEmpty()) {
                TextButton(enabled = picked.isNotEmpty(), onClick = {
                    scope.launch { message = vm.arrRenameRun(config, itemId, picked.toList()); if (!message!!.startsWith("error")) onDismiss() }
                }) { Text("Rename ${picked.size}", fontFamily = Mono, color = accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** All tags of the service: where each is used; create, rename, delete. */
@Composable
internal fun ArrTagsDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ArrTag>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    suspend fun reload() { list = runCatching { vm.arrTagList(config) }.getOrElse { message = "error: ${it.message}"; list ?: emptyList() } }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Tags", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (config.type == ServiceType.PROWLARR) "Tags tie indexers to proxies and apps." else "Tags tie titles to indexers, download clients, release and delay profiles.",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp),
                )
                Message(message)
                val l = list
                when {
                    l == null -> Status("loading…")
                    l.isEmpty() -> Status("no tags yet")
                    else -> l.forEach { t ->
                        if (editing == t.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(editText, { editText = it }, singleLine = true, textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = MatrixGreen), modifier = Modifier.weight(1f))
                                TextButton(enabled = cleanTagLabel(editText).isNotEmpty(), onClick = {
                                    scope.launch { message = vm.arrTagRename(config, t.id, editText); editing = null; reload() }
                                }) { Text("save", fontFamily = Mono, color = accent, fontSize = 12.sp) }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                                    Text(t.uses.ifBlank { "unused" }, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                                IconButton(onClick = { editing = t.id; editText = t.label }) {
                                    Icon(AppIcons.Edit, contentDescription = "Rename", tint = accent, modifier = Modifier.size(16.dp))
                                }
                                if (confirmDelete == t.id) {
                                    TextButton(onClick = { scope.launch { message = vm.arrTagDelete(config, t.id); confirmDelete = null; reload() } }) {
                                        Text(if (t.useCount > 0) "delete, used ${t.useCount}×?" else "delete?", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
                                    }
                                } else {
                                    IconButton(onClick = { confirmDelete = t.id }) {
                                        Icon(AppIcons.Cancel, contentDescription = "Delete", tint = ErrRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newLabel, { newLabel = it }, singleLine = true, placeholder = { Text("new tag", fontFamily = Mono, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = MatrixGreen), modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = cleanTagLabel(newLabel).isNotEmpty(), onClick = {
                        scope.launch { message = vm.arrTagCreate(config, newLabel); newLabel = ""; reload() }
                    }) { Text("add", fontFamily = Mono, color = accent) }
                }
                if (newLabel.isNotBlank() && cleanTagLabel(newLabel) != newLabel.trim()) {
                    Text("saved as \"${cleanTagLabel(newLabel)}\"", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** The tags on one title (or Prowlarr indexer): tap to toggle, save replaces them with the picked set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArrItemTagsDialog(vm: DashboardViewModel, config: ServiceConfig, itemId: Int, title: String, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<ArrTag>?>(null) }
    var picked by remember { mutableStateOf<Set<Int>?>(null) }
    var original by remember { mutableStateOf(setOf<Int>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("") }
    LaunchedEffect(itemId) {
        runCatching {
            all = vm.arrTagList(config)
            original = vm.arrItemTagIds(config, itemId).toSet()
            picked = original
        }.onFailure { message = "error: ${it.message}"; all = all ?: emptyList(); picked = picked ?: emptySet() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Tags · $title", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Message(message)
                val a = all
                val p = picked
                if (a == null || p == null) Status("loading…") else {
                    if (a.isEmpty()) Status("no tags yet, add one below")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.forEach { t ->
                            val on = t.id in p
                            Text(
                                t.label, fontFamily = Mono, fontSize = 12.sp, color = if (on) Black else accent,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) accent else SurfaceHi)
                                    .clickable { picked = if (on) p - t.id else p + t.id }.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            newLabel, { newLabel = it }, singleLine = true, placeholder = { Text("new tag", fontFamily = Mono, fontSize = 12.sp) },
                            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = MatrixGreen), modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = cleanTagLabel(newLabel).isNotEmpty(), onClick = {
                            scope.launch {
                                val label = cleanTagLabel(newLabel)
                                message = vm.arrTagCreate(config, label)
                                newLabel = ""
                                val fresh = runCatching { vm.arrTagList(config) }.getOrDefault(a)
                                all = fresh
                                // A new tag is meant for this title, so it starts picked.
                                fresh.firstOrNull { it.label == label }?.let { picked = (picked ?: emptySet()) + it.id }
                            }
                        }) { Text("add", fontFamily = Mono, color = accent) }
                    }
                }
            }
        },
        confirmButton = {
            val p = picked
            TextButton(enabled = p != null && p != original, onClick = {
                scope.launch { message = vm.arrItemTagsSet(config, itemId, p!!.toList()); if (!message!!.startsWith("error")) onDismiss() }
            }) { Text("Save", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/**
 * Import lists, download clients or Prowlarr proxies: what is set up, plus the one action that
 * matters for each (sync the lists, test the connections).
 */
@Composable
internal fun ArrProvidersDialog(vm: DashboardViewModel, config: ServiceConfig, which: String, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ArrProvider>?>(null) }
    var results by remember { mutableStateOf<Map<Int, String?>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(which) {
        list = runCatching { vm.arrProviders(config, which) }.getOrElse { message = "error: ${it.message}"; emptyList() }
    }
    val title = when (which) { "importlists" -> "Import lists"; "proxies" -> "Indexer proxies"; else -> "Download clients" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Message(message)
                if (testing) Status("testing…")
                val l = list
                when {
                    l == null -> Status("loading…")
                    l.isEmpty() -> Status("none set up")
                    else -> l.forEach { p ->
                        val r = results
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontFamily = Mono, color = if (p.enabled) MatrixGreen else MatrixGreen.copy(alpha = 0.45f), fontSize = 13.sp)
                                Text(
                                    (listOf(p.kind) + p.facts + listOfNotNull("disabled".takeIf { !p.enabled })).filter { it.isNotBlank() }.joinToString(" · "),
                                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
                                )
                                if (r != null && r.containsKey(p.id) && r[p.id] != null) {
                                    Text(r[p.id]!!, fontFamily = Mono, color = ErrRed, fontSize = 10.sp)
                                }
                            }
                            if (r != null && r.containsKey(p.id)) {
                                val ok = r[p.id] == null
                                Icon(if (ok) AppIcons.Done else AppIcons.Cancel, contentDescription = if (ok) "reachable" else "failed", tint = if (ok) accent else ErrRed, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                    }
                }
            }
        },
        confirmButton = {
            if (!list.isNullOrEmpty()) {
                if (which == "importlists") {
                    TextButton(onClick = { scope.launch { message = vm.arrImportListSyncNow(config) } }) { Text("Sync now", fontFamily = Mono, color = accent) }
                } else {
                    TextButton(enabled = !testing, onClick = {
                        scope.launch {
                            testing = true; message = null
                            results = runCatching { vm.arrProvidersTest(config, which) }.getOrElse { message = "error: ${it.message}"; null }
                            testing = false
                        }
                    }) { Text("Test all", fontFamily = Mono, color = accent) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
