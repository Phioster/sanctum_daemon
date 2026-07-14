package org.phioster.nexarr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ServiceType
import org.phioster.nexarr.ui.DashboardViewModel

private val MatrixGreen = Color(0xFF00FF41)
private val Black = Color(0xFF000000)
private val Surface = Color(0xFF0A0A0A)
private val ErrRed = Color(0xFFFF5555)
private val Mono = FontFamily.Monospace

private val NexarrColors = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = Black,
    background = Black,
    onBackground = MatrixGreen,
    surface = Surface,
    onSurface = MatrixGreen,
    surfaceVariant = Surface,
    onSurfaceVariant = MatrixGreen,
    outline = MatrixGreen.copy(alpha = 0.4f),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NexarrColors) {
                NexarrApp()
            }
        }
    }
}

@Composable
private fun NexarrApp(vm: DashboardViewModel = viewModel()) {
    var addOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }
    var detail by remember { mutableStateOf<ServiceConfig?>(null) }

    val editorOpen = addOpen || editing != null
    BackHandler(enabled = editorOpen || detail != null) {
        when {
            editorOpen -> { addOpen = false; editing = null }
            else -> detail = null
        }
    }

    when {
        editorOpen -> AddServiceScreen(
            existing = editing,
            onCancel = { addOpen = false; editing = null },
            onSave = { vm.upsertService(it); addOpen = false; editing = null },
            onTest = { vm.test(it) },
        )
        detail != null -> {
            val cfg = detail!!
            val back = { detail = null }
            val edit = { editing = detail; detail = null }
            if (cfg.type == ServiceType.NZBGET) {
                NzbgetScreen(vm = vm, config = cfg, onBack = back, onEdit = edit)
            } else {
                ServiceDetailScreen(vm = vm, config = cfg, onBack = back, onEdit = edit)
            }
        }
        else -> DashboardScreen(
            vm = vm,
            onAdd = { addOpen = true },
            onOpen = { detail = it },
            onEdit = { editing = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    vm: DashboardViewModel,
    onAdd: () -> Unit,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
) {
    val services by vm.services.collectAsState()
    val statuses by vm.statuses.collectAsState()

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("> nexarr_", fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MatrixGreen, contentColor = Black) {
                Icon(Icons.Filled.Add, contentDescription = "Add service")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (services.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "no services yet\n\ntap + to add a service",
                    fontFamily = Mono,
                    color = MatrixGreen.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            services.forEach { svc ->
                ServiceCard(
                    config = svc,
                    status = statuses[svc.id],
                    onOpen = { onOpen(svc) },
                    onEdit = { onEdit(svc) },
                    onRemove = { vm.removeService(svc.id) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ServiceCard(
    config: ServiceConfig,
    status: ServiceStatus?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = Color(config.type.accent)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp)) { Text("●", color = accent, fontSize = 12.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.label, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 18.sp)
                    Text(config.type.label, fontFamily = Mono, color = accent, fontSize = 12.sp)
                }
                val tag = when {
                    status == null || status.isLoading -> "[...]"
                    status.ok -> "[ok]"
                    else -> "[err]"
                }
                Text(tag, fontFamily = Mono, color = if (status == null || status.isLoading || status.ok) MatrixGreen else ErrRed)
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen.copy(alpha = 0.6f))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                status == null || status.isLoading -> Text("connecting…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                status.ok -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    status.stats.forEach { (k, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 20.sp)
                            Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(status.error ?: "error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
            }
            status?.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NzbgetScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var showHidden by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<NzbQueueItem>?>(null) }
    var history by remember { mutableStateOf<List<NzbHistoryEntry>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }

    suspend fun loadQueue() {
        listError = null
        runCatching { vm.queue(config) }.onSuccess { queue = it }.onFailure { listError = it.message }
    }
    suspend fun loadHistory() {
        listError = null
        runCatching { vm.history(config, showHidden) }.onSuccess { history = it }.onFailure { listError = it.message }
    }
    LaunchedEffect(tab, showHidden) { if (tab == 0) loadQueue() else loadHistory() }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Edit", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(status?.note ?: "—", fontFamily = Mono, color = accent, fontSize = 13.sp)
                    Text(
                        status?.stats?.firstOrNull()?.let { "${it.second} KB/s" } ?: "",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ActionBtn("Pause all", true) { scope.launch { actionMsg = vm.nzbgetPause(config); loadQueue(); vm.refreshAll() } }
                    Spacer(Modifier.width(12.dp))
                    ActionBtn("Resume all", true) { scope.launch { actionMsg = vm.nzbgetResume(config); loadQueue(); vm.refreshAll() } }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { scope.launch { if (tab == 0) loadQueue() else loadHistory() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Queue", fontFamily = Mono) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("History", fontFamily = Mono) })
                if (tab == 1) {
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("hidden", fontFamily = Mono) })
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (listError != null) {
                    Text("error: $listError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        if (tab == 0) {
                            val q = queue
                            when {
                                q == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                q.isEmpty() -> item { Text("queue is empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                else -> items(q) { QueueRow(it) }
                            }
                        } else {
                            val h = history
                            when {
                                h == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                h.isEmpty() -> item { Text("no history", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                else -> items(h) { HistoryRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: NzbQueueItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth(),
            color = MatrixGreen,
            trackColor = Surface,
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.status, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            Text("${item.remainingMb} / ${item.sizeMb} MB", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@Composable
private fun HistoryRow(item: NzbHistoryEntry) {
    val statusColor = when {
        item.status.contains("SUCCESS", true) -> MatrixGreen
        item.status.contains("FAILURE", true) || item.status.contains("DELETED", true) -> ErrRed
        else -> MatrixGreen.copy(alpha = 0.7f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.status, fontFamily = Mono, color = statusColor, fontSize = 11.sp)
            Text("${item.sizeMb} MB", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDetailScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var actionResult by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Edit", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(config.type.label, fontFamily = Mono, color = accent, fontSize = 14.sp)
            Text(config.baseUrl, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            when {
                status?.ok == true -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    status.stats.forEach { (k, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 22.sp)
                            Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
                status == null || status.isLoading -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                else -> Text(status.error ?: "error", fontFamily = Mono, color = ErrRed, fontSize = 13.sp)
            }
            status?.note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Text("> actions", fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val perform: (suspend () -> String) -> Unit = { action ->
                busy = true
                actionResult = null
                scope.launch {
                    actionResult = action()
                    busy = false
                    vm.refreshAll()
                }
            }
            when (config.type) {
                ServiceType.JELLYFIN ->
                    ActionBtn("Scan library", !busy) { perform { vm.jellyfinScan(config) } }
                ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR ->
                    ActionBtn("Search missing", !busy) { perform { vm.searchMissing(config) } }
                ServiceType.PROWLARR ->
                    ActionBtn("Test all indexers", !busy) { perform { vm.prowlarrTestAll(config) } }
                ServiceType.NZBGET -> Row {
                    ActionBtn("Pause", !busy) { perform { vm.nzbgetPause(config) } }
                    Spacer(Modifier.width(12.dp))
                    ActionBtn("Resume", !busy) { perform { vm.nzbgetResume(config) } }
                }
                ServiceType.SEERR -> Text(
                    "request approve/decline comes with the list view",
                    fontFamily = Mono,
                    color = MatrixGreen.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                )
            }
            actionResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServiceScreen(
    existing: ServiceConfig? = null,
    onCancel: () -> Unit,
    onSave: (ServiceConfig) -> Unit,
    onTest: suspend (ServiceConfig) -> ServiceStatus,
) {
    var type by remember { mutableStateOf(existing?.type ?: ServiceType.JELLYFIN) }
    var label by remember { mutableStateOf(existing?.label ?: ServiceType.JELLYFIN.label) }
    var labelEdited by remember { mutableStateOf(existing != null) }
    var url by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var jellyLogin by remember { mutableStateOf(existing?.useLogin ?: false) }
    var cfId by remember { mutableStateOf(existing?.customHeaders?.get("CF-Access-Client-Id") ?: "") }
    var cfSecret by remember { mutableStateOf(existing?.customHeaders?.get("CF-Access-Client-Secret") ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Keep label in sync with the chosen type until the user edits it manually.
    LaunchedEffect(type) { if (!labelEdited) label = type.label }

    val usesLogin = (type == ServiceType.JELLYFIN && jellyLogin) || type == ServiceType.NZBGET

    fun build(): ServiceConfig {
        val headers = buildMap {
            if (cfId.isNotBlank()) put("CF-Access-Client-Id", cfId.trim())
            if (cfSecret.isNotBlank()) put("CF-Access-Client-Secret", cfSecret.trim())
        }
        val base = ServiceConfig(
            type = type,
            label = label.ifBlank { type.label },
            baseUrl = url.trim(),
            apiKey = apiKey.trim(),
            username = username.trim(),
            password = password,
            useLogin = type == ServiceType.JELLYFIN && jellyLogin,
            customHeaders = headers,
        )
        return if (existing != null) base.copy(id = existing.id) else base
    }

    val canSave = url.isNotBlank() && when {
        type == ServiceType.NZBGET -> username.isNotBlank() && password.isNotBlank()
        type == ServiceType.JELLYFIN && jellyLogin -> username.isNotBlank() && password.isNotBlank()
        else -> apiKey.isNotBlank()
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "edit service" else "add service", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ServiceType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label, fontFamily = Mono) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Field("Label", label) { label = it; labelEdited = true }
            Field("Base URL (https://…)", url) { url = it }

            // Jellyfin can auth by API key or by login.
            if (type == ServiceType.JELLYFIN) {
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = !jellyLogin, onClick = { jellyLogin = false }, label = { Text("API key", fontFamily = Mono) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = jellyLogin, onClick = { jellyLogin = true }, label = { Text("Login", fontFamily = Mono) })
                }
            }

            if (type.usesApiKeyHeader || (type == ServiceType.JELLYFIN && !jellyLogin)) {
                Field("API key", apiKey) { apiKey = it }
            }
            if (usesLogin) {
                Field("Username", username) { username = it }
                Field("Password", password, isPassword = true) { password = it }
            }

            Spacer(Modifier.height(8.dp))
            Text("Cloudflare Access (optional)", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            Field("CF-Access-Client-Id", cfId) { cfId = it }
            Field("CF-Access-Client-Secret", cfSecret, isPassword = true) { cfSecret = it }

            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = {
                    testResult = "testing…"
                    scope.launch {
                        val r = onTest(build())
                        testResult = if (r.ok) "ok: " + r.stats.joinToString { "${it.first}=${it.second}" } else "error: ${r.error}"
                    }
                }) { Text("Test", fontFamily = Mono) }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = { onSave(build()) }, enabled = canSave) {
                    Text("Save", fontFamily = Mono)
                }
            }
            testResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontFamily = Mono, color = if (it.startsWith("ok")) MatrixGreen else Color(0xFFFFAA00), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Mono) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun ActionBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) { Text(label, fontFamily = Mono) }
}
