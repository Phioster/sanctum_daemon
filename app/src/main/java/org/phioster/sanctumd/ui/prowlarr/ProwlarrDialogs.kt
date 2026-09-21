package org.phioster.sanctumd.ui.prowlarr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ProwlarrIndexerItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Remove an indexer from Prowlarr. The connected apps keep theirs. */
@Composable
internal fun ProwlarrDeleteIndexerDialog(
    indexer: ProwlarrIndexerItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    indexer.let { ix ->
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("Delete indexer?", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("Remove \"${ix.name}\" from Prowlarr. This does not touch the connected apps.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onDismiss(); onConfirm() }) {
                    Text("Delete", fontFamily = Mono, color = ErrRed)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

/**
 * Edit one indexer's settings. The field list comes from Prowlarr itself, so it is fetched
 * when the dialog opens rather than handed in.
 */
@Composable
internal fun ProwlarrEditIndexerDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    indexer: ProwlarrIndexerItem,
    onMessage: (String) -> Unit,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editForm by remember(indexer) { mutableStateOf<org.phioster.sanctumd.model.ProwlarrIndexerEdit?>(null) }
    var editValues by remember(indexer) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editSaving by remember(indexer) { mutableStateOf(false) }
    LaunchedEffect(indexer) {
        val f = runCatching { vm.prowlarrIndexerEditOf(config, indexer.id) }.getOrNull()
        editForm = f
        editValues = f?.fields?.associate { it.name to it.value } ?: emptyMap()
    }
    indexer.let { ix ->
        AlertDialog(
            onDismissRequest = { if (!editSaving) onDismiss() },
            containerColor = Surface,
            title = { Text("Edit ${editForm?.name ?: ix.name}", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                val form = editForm
                if (form == null) {
                    Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                } else if (form.fields.isEmpty()) {
                    Text("no editable settings", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                } else {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                        form.fields.forEach { f ->
                            ProwlarrFieldInput(f, editValues[f.name] ?: f.value) { editValues = editValues + (f.name to it) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editForm != null && !editSaving,
                    onClick = {
                        val id = ix.id
                        val vals = editValues
                        editSaving = true
                        scope.launch {
                            onMessage(vm.prowlarrSaveIndexerOf(config, id, vals))
                            editSaving = false
                            onDismiss()
                            onSaved()
                        }
                    },
                ) { Text(if (editSaving) "saving…" else "Save", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { if (!editSaving) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

/**
 * Adding an indexer in two steps: pick one from Prowlarr's catalogue, then fill its fields.
 * Both steps live here because cancelling the second one ends the whole thing -- going back
 * to the list would mean re-picking anyway.
 */
@Composable
internal fun ProwlarrAddIndexerFlow(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onMessage: (String) -> Unit,
    onAdded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var schemas by remember { mutableStateOf<List<org.phioster.sanctumd.net.ProwlarrSchemaEntry>?>(null) }
    var schemaQuery by remember { mutableStateOf("") }
    var addEntry by remember { mutableStateOf<org.phioster.sanctumd.net.ProwlarrSchemaEntry?>(null) }
    var addName by remember { mutableStateOf("") }
    var addValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var addSaving by remember { mutableStateOf(false) }
    var addTesting by remember { mutableStateOf(false) }
    var addMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        schemas = runCatching { vm.prowlarrIndexerSchemasOf(config) }.getOrDefault(emptyList())
    }
    if (addEntry == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("Add indexer", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 480.dp)) {
                    Field("search", schemaQuery) { schemaQuery = it }
                    Spacer(Modifier.height(8.dp))
                    val list = schemas
                    when {
                        list == null -> Text("loading catalogue…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> {
                            val filtered = list.filter { schemaQuery.isBlank() || it.name.contains(schemaQuery, ignoreCase = true) }
                            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                                if (filtered.isEmpty()) {
                                    item { Text("no matches", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp) }
                                }
                                items(filtered.take(120)) { s ->
                                    Column(
                                        Modifier.fillMaxWidth().clickable {
                                            addEntry = s; addName = s.name; addMsg = null
                                            addValues = s.fields.associate { it.name to it.value }
                                        }.padding(vertical = 10.dp),
                                    ) {
                                        Text(s.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(s.protocol.ifBlank { null }, s.privacy.ifBlank { null }).joinToString(" · "),
                                            fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp,
                                        )
                                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    addEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!addSaving) onDismiss() },
            containerColor = Surface,
            title = { Text("Add ${entry.name}", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    addMsg?.let { m ->
                        val ok = !m.startsWith("error")
                        Text(
                            m,
                            fontFamily = Mono, color = if (ok) MatrixGreen else ErrRed, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Field("name", addName) { addName = it }
                    Spacer(Modifier.height(6.dp))
                    entry.fields.forEach { f ->
                        ProwlarrFieldInput(f, addValues[f.name] ?: f.value) { addValues = addValues + (f.name to it) }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = addName.isNotBlank() && !addSaving && !addTesting,
                        onClick = {
                            val e = entry
                            val nm = addName
                            val vals = addValues
                            addTesting = true; addMsg = null
                            scope.launch {
                                addMsg = vm.prowlarrTestNewIndexerOf(config, e, nm, vals)
                                addTesting = false
                            }
                        },
                    ) { Text(if (addTesting) "testing…" else "Test", fontFamily = Mono, color = accent) }
                    TextButton(
                        enabled = addName.isNotBlank() && !addSaving && !addTesting,
                        onClick = {
                            val e = entry
                            val nm = addName
                            val vals = addValues
                            addSaving = true; addMsg = null
                            scope.launch {
                                val res = vm.prowlarrAddIndexerOf(config, e, nm, vals)
                                addSaving = false
                                if (res.startsWith("error")) {
                                    addMsg = res // keep the dialog open so the user can fix the field
                                } else {
                                    onMessage(res)
                                    onAdded()
                                    onDismiss()
                                }
                            }
                        },
                    ) { Text(if (addSaving) "adding…" else "Add", fontFamily = Mono, color = MatrixGreen) }
                }
            },
            dismissButton = { TextButton(onClick = { if (!addSaving && !addTesting) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

/** Prowlarr's own version, health checks and scheduled tasks. */
@Composable
internal fun ProwlarrSystemDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onDismiss: () -> Unit,
) {
    var systemInfo by remember { mutableStateOf<org.phioster.sanctumd.model.ProwlarrSystemInfo?>(null) }
    var tasks by remember { mutableStateOf<List<org.phioster.sanctumd.model.ProwlarrTaskItem>?>(null) }
    LaunchedEffect(Unit) {
        systemInfo = runCatching { vm.prowlarrSystemInfo(config) }.getOrNull()
        tasks = runCatching { vm.prowlarrTaskList(config) }.getOrDefault(emptyList())
    }
    if (showSystem) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("System & tasks", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val si = systemInfo
                    Text("version ${si?.version ?: "…"}", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("HEALTH")
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.health.isEmpty() -> Text("all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                        else -> si.health.forEach { (type, msg) ->
                            val c = if (type.equals("error", true)) ErrRed else WarnAmber
                            Text("• $msg", fontFamily = Mono, color = c, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("TASKS")
                    val tk = tasks
                    when {
                        tk == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        tk.isEmpty() -> Text("no tasks", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> tk.forEach { t ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(t.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                                Text("last ${t.lastExecution.ifBlank { "—" }} · next ${t.nextExecution.ifBlank { "—" }}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
