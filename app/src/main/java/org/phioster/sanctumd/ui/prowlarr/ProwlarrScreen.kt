package org.phioster.sanctumd.ui.prowlarr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ProwlarrIndexerItem
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
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
import org.phioster.sanctumd.ServiceLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProwlarrScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) } // 0=Indexers, 1=Search, 2=History
    var indexers by remember { mutableStateOf<List<ProwlarrIndexerItem>?>(null) }
    var releases by remember { mutableStateOf<List<ProwlarrRelease>?>(null) }
    var history by remember { mutableStateOf<List<org.phioster.sanctumd.model.ProwlarrHistoryItem>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(actionMsg) { if (actionMsg != null) { kotlinx.coroutines.delay(4000); actionMsg = null } }
    var barMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val categories = remember { vm.prowlarrCategoryOptions() }
    var category by remember { mutableStateOf(categories.first()) }
    var catMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var confirmGrab by remember { mutableStateOf<ProwlarrRelease?>(null) }
    var systemInfo by remember { mutableStateOf<org.phioster.sanctumd.model.ProwlarrSystemInfo?>(null) }
    var tasks by remember { mutableStateOf<List<org.phioster.sanctumd.model.ProwlarrTaskItem>?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    var confirmDelIndexer by remember { mutableStateOf<ProwlarrIndexerItem?>(null) }
    var editIndexer by remember { mutableStateOf<ProwlarrIndexerItem?>(null) }
    var editForm by remember { mutableStateOf<org.phioster.sanctumd.model.ProwlarrIndexerEdit?>(null) }
    var editValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editSaving by remember { mutableStateOf(false) }
    var showAddSchema by remember { mutableStateOf(false) }
    var schemas by remember { mutableStateOf<List<org.phioster.sanctumd.net.ProwlarrSchemaEntry>?>(null) }
    var schemaQuery by remember { mutableStateOf("") }
    var addEntry by remember { mutableStateOf<org.phioster.sanctumd.net.ProwlarrSchemaEntry?>(null) }
    var addName by remember { mutableStateOf("") }
    var addValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var addSaving by remember { mutableStateOf(false) }
    var addTesting by remember { mutableStateOf(false) }
    var addMsg by remember { mutableStateOf<String?>(null) }
    val arrTargets = remember { vm.arrTargets() }

    suspend fun loadIndexers() {
        listError = null
        try {
            indexers = vm.prowlarrIndexerList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadHistory() {
        listError = null
        try {
            history = vm.prowlarrHistoryList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    LaunchedEffect(mode) {
        if (mode == 0 && indexers == null) loadIndexers()
        if (mode == 2 && history == null) loadHistory()
    }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        releases = null
        listError = null
        scope.launch {
            try {
                releases = vm.prowlarrSearchList(config, query, category.id)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                listError = t.message
            } finally {
                searching = false
            }
        }
    }
    fun act(action: suspend () -> String, reloadIndexers: Boolean) {
        scope.launch {
            actionMsg = action()
            if (reloadIndexers) loadIndexers()
            vm.refreshAll()
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(config.type, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(config.label, fontFamily = Mono, color = MatrixGreen)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Add indexer", fontFamily = Mono) }, onClick = {
                                barMenu = false; showAddSchema = true; schemas = null; schemaQuery = ""
                                scope.launch { schemas = runCatching { vm.prowlarrIndexerSchemasOf(config) }.getOrDefault(emptyList()) }
                            })
                            DropdownMenuItem(text = { Text("Test all indexers", fontFamily = Mono) }, onClick = { barMenu = false; act({ vm.prowlarrTestAll(config) }, false) })
                            DropdownMenuItem(text = { Text("System & tasks", fontFamily = Mono) }, onClick = {
                                barMenu = false; showSystem = true; systemInfo = null; tasks = null
                                scope.launch {
                                    systemInfo = runCatching { vm.prowlarrSystemInfo(config) }.getOrNull()
                                    tasks = runCatching { vm.prowlarrTaskList(config) }.getOrDefault(emptyList())
                                }
                            })
                            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                if (status?.ok == true) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        status.stats.forEach { (k, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 20.sp)
                                Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Indexers", fontFamily = Mono) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Search", fontFamily = Mono) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("History", fontFamily = Mono) })
                    Spacer(Modifier.weight(1f))
                    if (mode == 0 || mode == 2) {
                        IconButton(enabled = !refreshing, onClick = { actionMsg = null; scope.launch { refreshing = true; if (mode == 0) loadIndexers() else loadHistory(); refreshing = false } }) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = MatrixGreen, strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                        }
                    }
                }
                if (mode == 1) {
                    Spacer(Modifier.height(8.dp))
                    Field("Search all indexers", query) { query = it }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(onClick = { catMenu = true }) {
                                Text("cat: ${category.name}", fontFamily = Mono, color = MatrixGreen)
                            }
                            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                categories.forEach { c ->
                                    DropdownMenuItem(text = { Text(c.name, fontFamily = Mono) }, onClick = { catMenu = false; category = c })
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { runSearch() }, enabled = query.isNotBlank() && !searching) {
                            Text(if (searching) "…" else "Search", fontFamily = Mono)
                        }
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            SwipeTabs(mode, 3, { mode = it }, Modifier.weight(1f).fillMaxWidth()) { page ->
                if (listError != null) {
                    Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (page) {
                            0 -> {
                                val ix = indexers
                                when {
                                    ix == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    ix.isEmpty() -> item { Text("no indexers", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(ix) { row ->
                                        ProwlarrIndexerRow(
                                            item = row,
                                            accent = accent,
                                            onTest = { act({ vm.prowlarrTest(config, row.id) }, false) },
                                            onToggle = { act({ vm.prowlarrToggle(config, row.id, !row.enable) }, true) },
                                            onEdit = {
                                                editIndexer = row; editForm = null; editValues = emptyMap()
                                                scope.launch {
                                                    val f = runCatching { vm.prowlarrIndexerEditOf(config, row.id) }.getOrNull()
                                                    editForm = f
                                                    editValues = f?.fields?.associate { it.name to it.value } ?: emptyMap()
                                                }
                                            },
                                            onDelete = { confirmDelIndexer = row },
                                        )
                                    }
                                }
                            }
                            1 -> {
                                val r = releases
                                when {
                                    searching -> item { Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    r == null -> item { Text("enter a query and search", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    r.isEmpty() -> item { Text("no results", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(r) { rel ->
                                        ProwlarrReleaseRow(
                                            item = rel,
                                            accent = accent,
                                            arrTargets = arrTargets,
                                            onGrab = { confirmGrab = rel },
                                            onSendTo = { target -> scope.launch { actionMsg = vm.sendReleaseToArr(target, rel) } },
                                        )
                                    }
                                }
                            }
                            else -> {
                                val h = history
                                when {
                                    h == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    h.isEmpty() -> item { Text("no history", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(h) { ev -> ProwlarrHistoryRow(ev, accent) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmGrab?.let { rel ->
        AlertDialog(
            onDismissRequest = { confirmGrab = null },
            containerColor = Surface,
            title = { Text("Grab release", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text(rel.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("${rel.indexer} · ${rel.protocol} · ${rel.sizeMb} MB", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Sends to Prowlarr's download client.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = rel
                    confirmGrab = null
                    scope.launch { actionMsg = vm.prowlarrGrabRelease(config, r) }
                }) { Text("Grab", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmGrab = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    confirmDelIndexer?.let { ix ->
        AlertDialog(
            onDismissRequest = { confirmDelIndexer = null },
            containerColor = Surface,
            title = { Text("Delete indexer?", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("Remove \"${ix.name}\" from Prowlarr. This does not touch the connected apps.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { confirmDelIndexer = null; act({ vm.prowlarrDelete(config, ix.id) }, true) }) {
                    Text("Delete", fontFamily = Mono, color = ErrRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelIndexer = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    editIndexer?.let { ix ->
        AlertDialog(
            onDismissRequest = { if (!editSaving) editIndexer = null },
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
                            actionMsg = vm.prowlarrSaveIndexerOf(config, id, vals)
                            editSaving = false
                            editIndexer = null
                            loadIndexers()
                        }
                    },
                ) { Text(if (editSaving) "saving…" else "Save", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { if (!editSaving) editIndexer = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showAddSchema) {
        AlertDialog(
            onDismissRequest = { showAddSchema = false },
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
                                            showAddSchema = false
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
            dismissButton = { TextButton(onClick = { showAddSchema = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    addEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!addSaving) addEntry = null },
            containerColor = Surface,
            title = { Text("Add ${entry.name}", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    addMsg?.let { m ->
                        val ok = !m.startsWith("error")
                        Text(
                            if (ok) "✓ $m" else m,
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
                                    actionMsg = res
                                    addEntry = null
                                    loadIndexers()
                                }
                            }
                        },
                    ) { Text(if (addSaving) "adding…" else "Add", fontFamily = Mono, color = MatrixGreen) }
                }
            },
            dismissButton = { TextButton(onClick = { if (!addSaving && !addTesting) addEntry = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showSystem) {
        AlertDialog(
            onDismissRequest = { showSystem = false },
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
                            val c = if (type.equals("error", true)) ErrRed else Color(0xFFFFAA00)
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
            confirmButton = { TextButton(onClick = { showSystem = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
internal fun ProwlarrHistoryRow(item: org.phioster.sanctumd.model.ProwlarrHistoryItem, accent: Color) {
    val evColor = when (item.eventType) {
        "releaseGrabbed" -> MatrixGreen
        "indexerQuery" -> accent.copy(alpha = 0.8f)
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.indexer} · ${item.date}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(item.eventType, fontFamily = Mono, color = evColor, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@Composable
private fun ProwlarrFieldInput(f: org.phioster.sanctumd.model.ProwlarrField, value: String, onChange: (String) -> Unit) {
    when {
        f.type == "checkbox" -> JellyToggle(f.label, value.toBoolean()) { onChange(it.toString()) }
        f.type == "select" && f.options.isNotEmpty() -> {
            val curLabel = f.options.firstOrNull { it.first == value }?.second ?: value
            DropdownField(f.label, curLabel, f.options.map { it.second }) { idx -> onChange(f.options[idx].first) }
        }
        else -> Field(f.label, value, isPassword = f.type == "password") { onChange(it) }
    }
    if (f.helpText.isNotBlank()) {
        Text(f.helpText, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 10.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun ProwlarrIndexerRow(item: ProwlarrIndexerItem, accent: Color, onTest: () -> Unit, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val stateColor = when {
        item.failing -> ErrRed
        !item.enable -> MatrixGreen.copy(alpha = 0.4f)
        else -> MatrixGreen
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { menu = true }
                .padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, fontFamily = Mono, color = stateColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    when {
                        item.failing -> "failing"
                        !item.enable -> "disabled"
                        else -> item.protocol
                    },
                    fontFamily = Mono, color = stateColor, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "prio ${item.priority} · ${item.grabs} grabs · ${item.queries} q · ${item.failRate}% fail",
                fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Test", fontFamily = Mono) }, onClick = { menu = false; onTest() })
            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(if (item.enable) "Disable" else "Enable", fontFamily = Mono) }, onClick = { menu = false; onToggle() })
            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono, color = ErrRed) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
internal fun ProwlarrReleaseRow(
    item: ProwlarrRelease,
    accent: Color,
    arrTargets: List<ServiceConfig>,
    onGrab: () -> Unit,
    onSendTo: (ServiceConfig) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val meta = buildString {
        append(item.indexer)
        append(" · ")
        append(if (item.sizeMb >= 1024) "%.1f GB".format(item.sizeMb / 1024.0) else "${item.sizeMb} MB")
        if (item.protocol == "torrent") append(" · ${item.seeders ?: 0}S")
        else append(" · ${item.ageDays}d")
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { menu = true }
                .padding(vertical = 10.dp),
        ) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(meta, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(item.categories, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Grab (Prowlarr)", fontFamily = Mono) }, onClick = { menu = false; onGrab() })
            arrTargets.forEach { t ->
                DropdownMenuItem(text = { Text("Send to ${t.label}", fontFamily = Mono) }, onClick = { menu = false; onSendTo(t) })
            }
        }
    }
}
