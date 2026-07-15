package org.phioster.nexarr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.phioster.nexarr.model.ArrLibraryItem
import org.phioster.nexarr.model.ArrLookupItem
import org.phioster.nexarr.model.ArrMissingItem
import org.phioster.nexarr.model.ArrProfile
import org.phioster.nexarr.model.ArrQueueItem
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.ArrDetail
import org.phioster.nexarr.model.ArrEpisode
import org.phioster.nexarr.model.ArrHistoryItem
import org.phioster.nexarr.model.ArrRelease
import org.phioster.nexarr.model.ProwlarrIndexerItem
import org.phioster.nexarr.model.ProwlarrRelease
import org.phioster.nexarr.model.SeerrIssueItem
import org.phioster.nexarr.model.SeerrRequestItem
import org.phioster.nexarr.model.SeerrSearchItem
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
            onSave = {
                vm.upsertService(it)
                if (detail?.id == it.id) detail = it // stay on the (now updated) service
                addOpen = false; editing = null
            },
            onTest = { vm.test(it) },
        )
        detail != null -> {
            val cfg = detail!!
            val back = { detail = null }
            val edit = { editing = cfg } // keep detail so back returns to the service
            val del = { vm.removeService(cfg.id); detail = null }
            when (cfg.type) {
                ServiceType.NZBGET -> NzbgetScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR ->
                    ArrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                ServiceType.SEERR -> SeerrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                ServiceType.PROWLARR -> ProwlarrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                else -> ServiceDetailScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
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
private fun SeerrScreen(
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
    var mode by remember { mutableStateOf(0) } // 0=Requests, 1=Issues, 2=Discover
    var reqFilter by remember { mutableStateOf("all") }
    var issueFilter by remember { mutableStateOf("open") }
    var discoverKind by remember { mutableStateOf("trending") }
    var filterMenu by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<SeerrRequestItem>?>(null) }
    var issues by remember { mutableStateOf<List<SeerrIssueItem>?>(null) }
    var discover by remember { mutableStateOf<List<org.phioster.nexarr.model.SeerrDiscoverItem>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SeerrSearchItem>?>(null) }
    var confirmItem by remember { mutableStateOf<SeerrSearchItem?>(null) }
    var seasons by remember { mutableStateOf<List<org.phioster.nexarr.model.SeerrSeason>?>(null) }
    var selectedSeasons by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var issueDetailId by remember { mutableStateOf<Int?>(null) }
    var issueDetail by remember { mutableStateOf<org.phioster.nexarr.model.SeerrIssueDetail?>(null) }
    var commentText by remember { mutableStateOf("") }

    val reqFilters = listOf("all", "pending", "approved", "processing", "failed", "available", "unavailable")
    val issueFilters = listOf("open", "resolved", "all")
    val discoverKinds = listOf("trending", "movies", "tv")

    suspend fun loadRequests() {
        listError = null
        try {
            requests = vm.seerrList(config, reqFilter)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadIssues() {
        listError = null
        try {
            issues = vm.seerrIssuesList(config, issueFilter)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadDiscover() {
        listError = null
        try {
            discover = vm.seerrDiscoverList(config, discoverKind)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    LaunchedEffect(mode, reqFilter, issueFilter, discoverKind) {
        when (mode) { 0 -> loadRequests(); 1 -> loadIssues(); else -> loadDiscover() }
    }
    fun act(action: suspend () -> String) {
        scope.launch {
            actionMsg = action()
            loadRequests()
            vm.refreshAll()
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("New request", fontFamily = Mono) }, onClick = { barMenu = false; searchTerm = ""; searchResults = null; showAdd = true })
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
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Requests", fontFamily = Mono) })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Issues", fontFamily = Mono) })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Discover", fontFamily = Mono) })
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { filterMenu = true }) {
                            Text(when (mode) { 0 -> reqFilter; 1 -> issueFilter; else -> discoverKind }, fontFamily = Mono, color = MatrixGreen)
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            (when (mode) { 0 -> reqFilters; 1 -> issueFilters; else -> discoverKinds }).forEach { f ->
                                DropdownMenuItem(text = { Text(f, fontFamily = Mono) }, onClick = {
                                    filterMenu = false
                                    when (mode) { 0 -> reqFilter = f; 1 -> issueFilter = f; else -> discoverKind = f }
                                })
                            }
                        }
                    }
                    IconButton(onClick = { scope.launch { when (mode) { 0 -> loadRequests(); 1 -> loadIssues(); else -> loadDiscover() } } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (listError != null) {
                    Text("error: $listError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (mode) {
                            0 -> {
                                val r = requests
                                when {
                                    r == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    r.isEmpty() -> item { Text("no requests", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(r) { req ->
                                        SeerrRequestRow(
                                            item = req,
                                            accent = accent,
                                            onApprove = { act { vm.seerrApproveReq(config, req.id) } },
                                            onDecline = { act { vm.seerrDeclineReq(config, req.id) } },
                                        )
                                    }
                                }
                            }
                            1 -> {
                                val i = issues
                                when {
                                    i == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    i.isEmpty() -> item { Text("no issues", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(i) { iss ->
                                        SeerrIssueRow(iss, accent) { issueDetailId = iss.id; issueDetail = null; commentText = ""; scope.launch { issueDetail = runCatching { vm.seerrIssueDetailOf(config, iss.id) }.getOrNull() } }
                                    }
                                }
                            }
                            else -> {
                                val d = discover
                                when {
                                    d == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    d.isEmpty() -> item { Text("nothing to show", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(d) { di ->
                                        SeerrDiscoverRow(di, accent) {
                                            confirmItem = SeerrSearchItem(di.tmdbId, di.title, di.year, di.mediaType)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Surface,
            title = { Text("New request", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Search title", searchTerm) { searchTerm = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scope.launch { searchResults = runCatching { vm.seerrSearchList(config, searchTerm) }.getOrElse { emptyList() } } },
                        enabled = searchTerm.isNotBlank(),
                    ) { Text("Search", fontFamily = Mono) }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        val res = searchResults
                        when {
                            res == null -> {}
                            res.isEmpty() -> Text("no results", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> res.forEach { r ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { confirmItem = r; showAdd = false }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        "${r.title}${if (r.year.isNotBlank()) " (${r.year})" else ""}",
                                        fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(r.mediaType, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
    LaunchedEffect(confirmItem) {
        val ci = confirmItem
        seasons = null; selectedSeasons = emptySet()
        if (ci != null && ci.mediaType == "tv") {
            val s = runCatching { vm.seerrSeasonsList(config, ci.tmdbId) }.getOrDefault(emptyList())
            seasons = s
            selectedSeasons = s.map { it.seasonNumber }.toSet() // default: all
        }
    }
    confirmItem?.let { item ->
        val isTv = item.mediaType == "tv"
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            containerColor = Surface,
            title = { Text("Request: ${item.title}", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text(
                        "${if (isTv) "Series" else "Movie"}${if (item.year.isNotBlank()) " (${item.year})" else ""}",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp,
                    )
                    if (isTv) {
                        Spacer(Modifier.height(8.dp))
                        val ss = seasons
                        if (ss == null) {
                            Text("loading seasons…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        } else {
                            val allSel = selectedSeasons.size == ss.size && ss.isNotEmpty()
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedSeasons = if (allSel) emptySet() else ss.map { it.seasonNumber }.toSet()
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (allSel) "[x] " else "[ ] ", fontFamily = Mono, color = if (allSel) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 13.sp)
                                Text("All seasons", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                                ss.forEach { s ->
                                    val checked = s.seasonNumber in selectedSeasons
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            selectedSeasons = if (checked) selectedSeasons - s.seasonNumber else selectedSeasons + s.seasonNumber
                                        }.padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (checked) "[x] " else "[ ] ", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp)
                                        Text("${s.name} · ${s.episodeCount} ep", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isTv || selectedSeasons.isNotEmpty(),
                    onClick = {
                        val tmdb = item.tmdbId; val type = item.mediaType
                        val chosen = if (!isTv) null else selectedSeasons.toList().sorted()
                        confirmItem = null
                        scope.launch {
                            actionMsg = vm.seerrRequestMedia(config, tmdb, type, chosen)
                            loadRequests()
                            vm.refreshAll()
                        }
                    },
                ) { Text("Request", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmItem = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    issueDetailId?.let { iid ->
        AlertDialog(
            onDismissRequest = { issueDetailId = null },
            containerColor = Surface,
            title = { Text(issueDetail?.title ?: "Issue #$iid", fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                val d = issueDetail
                Column(Modifier.heightIn(max = 460.dp)) {
                    if (d == null) {
                        Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    } else {
                        Text("${d.type} · ${d.status}", fontFamily = Mono, color = if (d.status == "open") Color(0xFFFFAA00) else MatrixGreen, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            if (d.description.isNotBlank()) {
                                Text(d.description, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                            }
                            d.comments.forEach { c ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text("${c.author} · ${c.date}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                                    Text(c.message, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Field("Add comment", commentText) { commentText = it }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            OutlinedButton(
                                enabled = commentText.isNotBlank(),
                                onClick = {
                                    val msg = commentText; commentText = ""
                                    scope.launch {
                                        actionMsg = vm.seerrComment(config, iid, msg)
                                        issueDetail = runCatching { vm.seerrIssueDetailOf(config, iid) }.getOrNull()
                                    }
                                },
                            ) { Text("Comment", fontFamily = Mono) }
                            Spacer(Modifier.width(8.dp))
                            val resolved = d.status == "resolved"
                            OutlinedButton(onClick = {
                                scope.launch {
                                    actionMsg = vm.seerrIssueStatus(config, iid, !resolved)
                                    issueDetail = runCatching { vm.seerrIssueDetailOf(config, iid) }.getOrNull()
                                    loadIssues()
                                }
                            }) { Text(if (resolved) "Reopen" else "Resolve", fontFamily = Mono) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { issueDetailId = null; scope.launch { loadIssues() } }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
            dismissButton = {
                TextButton(onClick = {
                    val id = iid; issueDetailId = null
                    scope.launch { actionMsg = vm.seerrDeleteIssue(config, id); loadIssues() }
                }) { Text("Delete", fontFamily = Mono, color = ErrRed) }
            },
        )
    }
}

@Composable
private fun SeerrRequestRow(item: SeerrRequestItem, accent: Color, onApprove: () -> Unit, onDecline: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val statusColor = when (item.status) {
        "approved" -> MatrixGreen
        "declined" -> ErrRed
        "pending" -> Color(0xFFFFAA00)
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = item.pending) { menu = true }
                .padding(vertical = 10.dp),
        ) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.status, fontFamily = Mono, color = statusColor, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Approve", fontFamily = Mono) }, onClick = { menu = false; onApprove() })
            DropdownMenuItem(text = { Text("Decline", fontFamily = Mono) }, onClick = { menu = false; onDecline() })
        }
    }
}

@Composable
private fun SeerrIssueRow(item: SeerrIssueItem, accent: Color, onClick: () -> Unit) {
    val statusColor = if (item.status == "open") Color(0xFFFFAA00) else MatrixGreen
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp)) {
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.status, fontFamily = Mono, color = statusColor, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@Composable
private fun SeerrDiscoverRow(item: org.phioster.nexarr.model.SeerrDiscoverItem, accent: Color, onRequest: () -> Unit) {
    val statusColor = when (item.status) {
        "available" -> MatrixGreen
        "processing", "pending", "partial" -> Color(0xFFFFAA00)
        else -> MatrixGreen.copy(alpha = 0.5f)
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.status.isEmpty()) { onRequest() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(46.dp).height(69.dp).clip(RoundedCornerShape(4.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(46.dp).height(69.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "${if (item.mediaType == "tv") "series" else "movie"}${if (item.year.isNotBlank()) " · ${item.year}" else ""}",
                fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
            )
        }
        Text(item.status.ifBlank { "request" }, fontFamily = Mono, color = statusColor, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProwlarrScreen(
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
    var history by remember { mutableStateOf<List<org.phioster.nexarr.model.ProwlarrHistoryItem>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val categories = remember { vm.prowlarrCategoryOptions() }
    var category by remember { mutableStateOf(categories.first()) }
    var catMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var confirmGrab by remember { mutableStateOf<ProwlarrRelease?>(null) }
    var systemInfo by remember { mutableStateOf<org.phioster.nexarr.model.ProwlarrSystemInfo?>(null) }
    var tasks by remember { mutableStateOf<List<org.phioster.nexarr.model.ProwlarrTaskItem>?>(null) }
    var showSystem by remember { mutableStateOf(false) }
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
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
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
                        IconButton(onClick = { scope.launch { if (mode == 0) loadIndexers() else loadHistory() } }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (listError != null) {
                    Text("error: $listError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (mode) {
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
                    Text("HEALTH", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.health.isEmpty() -> Text("all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                        else -> si.health.forEach { (type, msg) ->
                            val c = if (type.equals("error", true)) ErrRed else Color(0xFFFFAA00)
                            Text("• $msg", fontFamily = Mono, color = c, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("TASKS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
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
private fun ProwlarrHistoryRow(item: org.phioster.nexarr.model.ProwlarrHistoryItem, accent: Color) {
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
private fun ProwlarrIndexerRow(item: ProwlarrIndexerItem, accent: Color, onTest: () -> Unit, onToggle: () -> Unit) {
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
            DropdownMenuItem(text = { Text(if (item.enable) "Disable" else "Enable", fontFamily = Mono) }, onClick = { menu = false; onToggle() })
        }
    }
}

@Composable
private fun ProwlarrReleaseRow(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrScreen(
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
    var tab by remember { mutableStateOf(0) } // 0=Library,1=Missing,2=Cutoff,3=Queue,4=History
    var library by remember { mutableStateOf<List<ArrLibraryItem>?>(null) }
    var missing by remember { mutableStateOf<List<ArrMissingItem>?>(null) }
    var cutoff by remember { mutableStateOf<List<ArrMissingItem>?>(null) }
    var queue by remember { mutableStateOf<List<ArrQueueItem>?>(null) }
    var history by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrHistoryItem>?>(null) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    val supportsDetail = config.type == ServiceType.RADARR || config.type == ServiceType.SONARR || config.type == ServiceType.LIDARR
    val supportsImport = config.type == ServiceType.RADARR || config.type == ServiceType.SONARR
    var showSystem by remember { mutableStateOf(false) }
    var arrSys by remember { mutableStateOf<org.phioster.nexarr.model.ArrSystemInfo?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var importFolder by remember { mutableStateOf("") }
    var importItems by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrImportItem>?>(null) }
    var importScanning by remember { mutableStateOf(false) }
    var importSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(0) } // 0=Title, 1=Year, 2=Size
    var sortMenu by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var addTerm by remember { mutableStateOf("") }
    var addResults by remember { mutableStateOf<List<ArrLookupItem>?>(null) }
    var selected by remember { mutableStateOf<ArrLookupItem?>(null) }
    var profiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosenProfile by remember { mutableStateOf<ArrProfile?>(null) }
    var chosenFolder by remember { mutableStateOf<String?>(null) }
    var metaProfiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var chosenMeta by remember { mutableStateOf<ArrProfile?>(null) }
    var monitored by remember { mutableStateOf(true) }

    suspend fun loadLibrary() {
        listError = null
        try {
            library = vm.arrLibraryList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadMissing() {
        listError = null
        try {
            missing = vm.arrMissingList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadCutoff() {
        listError = null
        try {
            cutoff = vm.arrCutoffList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadQueue() {
        listError = null
        try {
            queue = vm.arrQueueList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun loadHistory() {
        listError = null
        try {
            history = vm.arrHistoryList(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    suspend fun reload() = when (tab) {
        0 -> loadLibrary(); 1 -> loadMissing(); 2 -> loadCutoff(); 3 -> loadQueue(); else -> loadHistory()
    }
    LaunchedEffect(tab) { reload() }
    fun act(action: suspend () -> String) {
        scope.launch {
            actionMsg = action()
            reload()
            vm.refreshAll()
        }
    }

    if (detailId != null && supportsDetail) {
        BackHandler { detailId = null; scope.launch { reload() } }
        ArrDetailScreen(vm = vm, config = config, itemId = detailId!!, onBack = { detailId = null; scope.launch { reload() } })
        return
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Add new", fontFamily = Mono) }, onClick = { barMenu = false; addTerm = ""; addResults = null; showAdd = true })
                            if (supportsImport) {
                                DropdownMenuItem(text = { Text("Manual import", fontFamily = Mono) }, onClick = {
                                    barMenu = false; showImport = true; importItems = null; importSelected = emptySet()
                                })
                            }
                            if (supportsDetail) {
                                DropdownMenuItem(text = { Text("System & health", fontFamily = Mono) }, onClick = {
                                    barMenu = false; showSystem = true; arrSys = null
                                    scope.launch { arrSys = runCatching { vm.arrSystemInfo(config) }.getOrNull() }
                                })
                            }
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
                    when (tab) {
                        1 -> ActionBtn("Search all missing", true) { act { vm.arrSearchAllItems(config, false) } }
                        2 -> ActionBtn("Search all cutoff", true) { act { vm.arrSearchAllItems(config, true) } }
                        else -> {}
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { reload() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf("Library", "Missing", "Cutoff", "Queue", "History")
                tabs.forEachIndexed { i, name ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name, fontFamily = Mono) })
                    if (i < tabs.lastIndex) Spacer(Modifier.width(8.dp))
                }
            }
            if (tab == 0) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Filter library", fontFamily = Mono) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text("sort", fontFamily = Mono, color = MatrixGreen) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DropdownMenuItem(text = { Text("Title", fontFamily = Mono) }, onClick = { sortMenu = false; sortBy = 0 })
                            DropdownMenuItem(text = { Text("Year", fontFamily = Mono) }, onClick = { sortMenu = false; sortBy = 1 })
                            DropdownMenuItem(text = { Text("Size", fontFamily = Mono) }, onClick = { sortMenu = false; sortBy = 2 })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (listError != null) {
                    Text("error: $listError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (tab) {
                            0 -> {
                                val filtered = library?.filter { it.title.contains(query, ignoreCase = true) }?.let { l ->
                                    when (sortBy) {
                                        1 -> l.sortedByDescending { it.year }
                                        2 -> l.sortedByDescending { it.sizeMb }
                                        else -> l.sortedBy { it.title.lowercase() }
                                    }
                                }
                                when {
                                    filtered == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    filtered.isEmpty() -> item { Text(if (query.isBlank()) "library is empty" else "no matches", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(filtered) { li ->
                                        ArrLibraryRow(
                                            item = li,
                                            accent = accent,
                                            onOpen = if (supportsDetail) ({ detailId = li.id }) else null,
                                            onSearch = { act { vm.arrLibSearch(config, li.id) } },
                                        )
                                    }
                                }
                            }
                            1, 2 -> {
                                val m = if (tab == 1) missing else cutoff
                                when {
                                    m == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    m.isEmpty() -> item { Text(if (tab == 1) "nothing missing" else "nothing below cutoff", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(m) { mi -> ArrMissingRow(mi, accent) { act { vm.arrSearch(config, mi.id) } } }
                                }
                            }
                            3 -> {
                                val q = queue
                                when {
                                    q == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    q.isEmpty() -> item { Text("queue is empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(q) { qi -> ArrQueueRow(qi) { act { vm.arrRemove(config, qi.id) } } }
                                }
                            }
                            else -> {
                                val h = history
                                when {
                                    h == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    h.isEmpty() -> item { Text("no history", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(h) { ev -> ArrHistoryRow(ev, accent) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Surface,
            title = { Text("Add ${config.type.label}", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Search title", addTerm) { addTerm = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scope.launch { addResults = runCatching { vm.arrLookupList(config, addTerm) }.getOrElse { emptyList() } } },
                        enabled = addTerm.isNotBlank(),
                    ) { Text("Search", fontFamily = Mono) }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        val res = addResults
                        when {
                            res == null -> {}
                            res.isEmpty() -> Text("no results", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> res.forEach { r ->
                                Text(
                                    "${r.title}${if (r.year > 0) " (${r.year})" else ""}",
                                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = r
                                            showAdd = false
                                            monitored = true
                                            scope.launch {
                                                profiles = runCatching { vm.arrProfilesList(config) }.getOrDefault(emptyList())
                                                folders = runCatching { vm.arrRootFoldersList(config) }.getOrDefault(emptyList())
                                                chosenProfile = profiles.firstOrNull()
                                                chosenFolder = folders.firstOrNull()
                                                if (config.type == ServiceType.LIDARR) {
                                                    metaProfiles = runCatching { vm.arrMetaProfilesList(config) }.getOrDefault(emptyList())
                                                    chosenMeta = metaProfiles.firstOrNull()
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Surface,
            title = { Text("Add: ${item.title}", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    DropdownField("Quality", chosenProfile?.name ?: "…", profiles.map { it.name }) { i -> chosenProfile = profiles[i] }
                    Spacer(Modifier.height(8.dp))
                    DropdownField("Folder", chosenFolder ?: "…", folders) { i -> chosenFolder = folders[i] }
                    if (config.type == ServiceType.LIDARR) {
                        Spacer(Modifier.height(8.dp))
                        DropdownField("Metadata", chosenMeta?.name ?: "…", metaProfiles.map { it.name }) { i -> chosenMeta = metaProfiles[i] }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monitored", fontFamily = Mono, color = MatrixGreen, modifier = Modifier.weight(1f))
                        Switch(checked = monitored, onCheckedChange = { monitored = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = chosenProfile != null && chosenFolder != null && (config.type != ServiceType.LIDARR || chosenMeta != null),
                    onClick = {
                        val raw = item.raw; val p = chosenProfile!!; val f = chosenFolder!!; val m = monitored; val meta = chosenMeta?.id ?: 0
                        selected = null
                        scope.launch {
                            actionMsg = vm.arrAddItem(config, raw, p.id, f, m, meta)
                            reload()
                            vm.refreshAll()
                        }
                    },
                ) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showSystem) {
        AlertDialog(
            onDismissRequest = { showSystem = false },
            containerColor = Surface,
            title = { Text("System & health", fontFamily = Mono, color = MatrixGreen) },
            text = {
                val si = arrSys
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("version ${si?.version ?: "…"}", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("DISK", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.disks.isEmpty() -> Text("—", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> si.disks.forEach { (path, info) ->
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text(path, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(info, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("HEALTH", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.health.isEmpty() -> Text("all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                        else -> si.health.forEach { (type, msg) ->
                            val c = if (type.equals("error", true)) ErrRed else Color(0xFFFFAA00)
                            Text("• $msg", fontFamily = Mono, color = c, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSystem = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showImport) {
        val items = importItems
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = Surface,
            title = { Text("Manual import", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Folder path on server", importFolder) { importFolder = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            importScanning = true; importItems = null; importSelected = emptySet()
                            scope.launch {
                                importItems = runCatching { vm.arrManualScan(config, importFolder.trim()) }.getOrElse {
                                    actionMsg = "error: ${it.message}"; emptyList()
                                }
                                importSelected = importItems!!.mapIndexedNotNull { i, it -> if (it.importable) i else null }.toSet()
                                importScanning = false
                            }
                        },
                        enabled = importFolder.isNotBlank() && !importScanning,
                    ) { Text(if (importScanning) "scanning…" else "Scan", fontFamily = Mono) }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        when {
                            items == null -> {}
                            items.isEmpty() -> Text("no importable files", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                            else -> items.forEachIndexed { i, it ->
                                val checked = i in importSelected
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        importSelected = if (checked) importSelected - i else importSelected + i
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (checked) "[x] " else "[ ] ", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(it.relativePath, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("→ ${it.matchedTitle}${if (it.quality.isNotBlank()) " · ${it.quality}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (it.rejection.isNotBlank()) Text(it.rejection, fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        showImport = false
                        scope.launch {
                            actionMsg = vm.arrManualImport(config, chosen)
                            vm.refreshAll()
                        }
                    },
                ) { Text("Import (${importSelected.size})", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", fontFamily = Mono, color = MatrixGreen)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o, fontFamily = Mono) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
private fun ArrLibraryRow(item: ArrLibraryItem, accent: Color, onOpen: (() -> Unit)?, onSearch: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { if (onOpen != null) onOpen() else menu = true }.padding(vertical = 10.dp)) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (item.sizeMb > 0) "${item.sizeMb} MB" else "", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Search", fontFamily = Mono) }, onClick = { menu = false; onSearch() })
        }
    }
}

@Composable
private fun ArrMissingRow(item: ArrMissingItem, accent: Color, onSearch: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 10.dp)) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Search", fontFamily = Mono) }, onClick = { menu = false; onSearch() })
        }
    }
}

@Composable
private fun ArrQueueRow(item: ArrQueueItem, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 8.dp)) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
            Spacer(Modifier.height(4.dp))
            Text(item.status, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove", fontFamily = Mono) }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun ArrHistoryRow(item: ArrHistoryItem, accent: Color) {
    val evColor = when (item.eventType) {
        "grabbed" -> MatrixGreen
        "downloadFolderImported" -> accent.copy(alpha = 0.9f)
        "downloadFailed", "episodeFileDeleted", "movieFileDeleted" -> ErrRed
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.eventType} · ${item.date}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.quality.isNotBlank()) Text(item.quality, fontFamily = Mono, color = evColor, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrDetailScreen(vm: DashboardViewModel, config: ServiceConfig, itemId: Int, onBack: () -> Unit) {
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    val isSonarr = config.type == ServiceType.SONARR
    val isLidarr = config.type == ServiceType.LIDARR
    var detail by remember { mutableStateOf<ArrDetail?>(null) }
    var episodes by remember { mutableStateOf<List<ArrEpisode>?>(null) }
    var albums by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrAlbum>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    // release picker: null=closed; loading when releases==null
    var pickerOpen by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<ArrRelease>?>(null) }
    var pickerTitle by remember { mutableStateOf("") }
    var confirmGrab by remember { mutableStateOf<ArrRelease?>(null) }
    var cast by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrCastMember>?>(null) }

    LaunchedEffect(itemId) {
        loadError = null
        try {
            val d = vm.arrDetailOf(config, itemId)
            detail = d
            if (isSonarr) episodes = vm.arrEpisodesOf(config, itemId)
            if (isLidarr) albums = vm.arrAlbumsOf(config, itemId)
            if (vm.hasSeerr() && d.tmdbId > 0) {
                cast = runCatching { vm.arrCast(d.tmdbId, isSonarr) }.getOrDefault(emptyList())
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            loadError = t.message
        }
    }
    fun openReleases(movieId: Int?, episodeId: Int?, title: String, albumId: Int? = null) {
        pickerTitle = title; releases = null; pickerOpen = true
        scope.launch {
            releases = runCatching { vm.arrReleasesFor(config, movieId, episodeId, albumId) }.getOrElse {
                actionMsg = "error: ${it.message}"; pickerOpen = false; emptyList()
            }
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "…", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Automatic search", fontFamily = Mono) }, onClick = {
                                barMenu = false; scope.launch { actionMsg = vm.arrLibSearch(config, itemId) }
                            })
                            if (config.type == ServiceType.RADARR) {
                                DropdownMenuItem(text = { Text("Interactive search", fontFamily = Mono) }, onClick = {
                                    barMenu = false; openReleases(movieId = itemId, episodeId = null, title = detail?.title ?: "")
                                })
                            }
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; deleteFiles = false; confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        if (loadError != null) {
            Text("error: $loadError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                val d = detail
                Spacer(Modifier.height(8.dp))
                Row {
                    if (!d?.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = d!!.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(110.dp)
                                .height(165.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Surface),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        val chips = buildList {
                            d?.year?.takeIf { it > 0 }?.let { add("year" to it.toString()) }
                            add("monitored" to if (d?.monitored == true) "yes" else "no")
                            d?.sizeMb?.takeIf { it > 0 }?.let { add("size" to if (it >= 1024) "%.1f GB".format(it / 1024.0) else "$it MB") }
                            d?.facts?.let { addAll(it) }
                        }
                        chips.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                pair.forEach { (k, v) ->
                                    Column(Modifier.weight(1f)) {
                                        Text(v, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(k.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (!d?.genres.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(d!!.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                actionMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
                if (!d?.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(d!!.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                val cst = cast
                if (!cst.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("CAST", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        cst.forEach { member ->
                            Column(Modifier.width(84.dp).padding(end = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (member.profileUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = member.profileUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Surface),
                                    )
                                } else {
                                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Surface))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(member.name, fontFamily = Mono, color = MatrixGreen, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                if (member.character.isNotBlank()) {
                                    Text(member.character, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
                if (isSonarr || isLidarr) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (isLidarr) "ALBUMS" else "EPISODES", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            if (isSonarr) {
                val eps = episodes
                when {
                    eps == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    eps.isEmpty() -> item { Text("no episodes", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    else -> items(eps) { ep ->
                        ArrEpisodeRow(ep, accent) {
                            openReleases(movieId = null, episodeId = ep.id, title = "S%02dE%02d %s".format(ep.seasonNumber, ep.episodeNumber, ep.title))
                        }
                    }
                }
            }
            if (isLidarr) {
                val als = albums
                when {
                    als == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    als.isEmpty() -> item { Text("no albums", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    else -> items(als) { al ->
                        ArrAlbumRow(al, accent) {
                            openReleases(movieId = null, episodeId = null, albumId = al.id, title = al.title)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface,
            title = { Text("Delete ${detail?.title ?: ""}?", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Also delete files", fontFamily = Mono, color = MatrixGreen, modifier = Modifier.weight(1f))
                    Switch(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val df = deleteFiles
                    confirmDelete = false
                    scope.launch {
                        val r = vm.arrDeleteItem(config, itemId, df)
                        if (!r.startsWith("error")) onBack() else actionMsg = r
                    }
                }) { Text("Delete", fontFamily = Mono, color = ErrRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            containerColor = Surface,
            title = { Text("Releases", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                val rs = releases
                Column(Modifier.heightIn(max = 460.dp)) {
                    Text(pickerTitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    when {
                        rs == null -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        rs.isEmpty() -> Text("no releases", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            rs.forEach { rel -> ArrReleaseRow(rel, accent) { confirmGrab = rel } }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    confirmGrab?.let { rel ->
        AlertDialog(
            onDismissRequest = { confirmGrab = null },
            containerColor = Surface,
            title = { Text("Grab release", fontFamily = Mono, color = MatrixGreen) },
            text = { Text(rel.title, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val r = rel
                    confirmGrab = null; pickerOpen = false
                    scope.launch { actionMsg = vm.arrGrabRelease(config, r.guid, r.indexerId) }
                }) { Text("Grab", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmGrab = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun ArrEpisodeRow(item: ArrEpisode, accent: Color, onSearch: () -> Unit) {
    val c = if (item.hasFile) MatrixGreen else if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.4f)
    Column(Modifier.fillMaxWidth().clickable { onSearch() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("S%02dE%02d  %s".format(item.seasonNumber, item.episodeNumber, item.title), fontFamily = Mono, color = c, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (item.hasFile) "✓" else item.airDate, fontFamily = Mono, color = c, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
private fun ArrAlbumRow(item: org.phioster.nexarr.model.ArrAlbum, accent: Color, onSearch: () -> Unit) {
    val complete = item.trackCount > 0 && item.trackFileCount >= item.trackCount
    val c = if (complete) MatrixGreen else if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.4f)
    Column(Modifier.fillMaxWidth().clickable { onSearch() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.title}${if (item.year.isNotBlank()) " (${item.year})" else ""}", fontFamily = Mono, color = c, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${item.trackFileCount}/${item.trackCount}", fontFamily = Mono, color = c, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
private fun ArrReleaseRow(item: ArrRelease, accent: Color, onGrab: () -> Unit) {
    val meta = buildString {
        append(item.indexer)
        append(" · ")
        append(if (item.sizeMb >= 1024) "%.1f GB".format(item.sizeMb / 1024.0) else "${item.sizeMb} MB")
        if (item.protocol == "torrent") append(" · ${item.seeders ?: 0}S") else append(" · ${item.ageDays}d")
        if (item.quality.isNotBlank()) append(" · ${item.quality}")
    }
    val scoreColor = when {
        item.score > 0 -> MatrixGreen
        item.score < 0 -> ErrRed
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Column(Modifier.fillMaxWidth().clickable { onGrab() }.padding(vertical = 8.dp)) {
        Text(item.title, fontFamily = Mono, color = if (item.approved) MatrixGreen else MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(meta, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.customFormats.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text("formats: ${item.customFormats}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("score ${item.score}", fontFamily = Mono, color = scoreColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.approved) "· approved" else "· rejected",
                fontFamily = Mono,
                color = if (item.approved) MatrixGreen.copy(alpha = 0.7f) else Color(0xFFFFAA00),
                fontSize = 10.sp,
            )
        }
        if (!item.approved && item.rejection.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(item.rejection, fontFamily = Mono, color = Color(0xFFFFAA00).copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NzbgetScreen(
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
    var tab by remember { mutableStateOf(0) }
    var showHidden by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<NzbQueueItem>?>(null) }
    var history by remember { mutableStateOf<List<NzbHistoryEntry>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }

    suspend fun loadQueue() {
        listError = null
        try {
            queue = vm.queue(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message ?: t.javaClass.simpleName
        }
    }
    suspend fun loadHistory() {
        listError = null
        try {
            history = vm.history(config, showHidden)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message ?: t.javaClass.simpleName
        }
    }
    LaunchedEffect(tab, showHidden) { if (tab == 0) loadQueue() else loadHistory() }

    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var addUrl by remember { mutableStateOf("") }
    var addCat by remember { mutableStateOf("") }
    var serverInfo by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var categoryFor by remember { mutableStateOf<Int?>(null) }
    var categoryText by remember { mutableStateOf("") }
    val context = LocalContext.current
    fun act(action: suspend () -> String) {
        scope.launch {
            actionMsg = action()
            if (tab == 0) loadQueue() else loadHistory()
            vm.refreshAll()
        }
    }

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
                    Box {
                        IconButton(onClick = { barMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen)
                        }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Add NZB (URL)", fontFamily = Mono) }, onClick = { barMenu = false; showAdd = true })
                            DropdownMenuItem(text = { Text("Server details", fontFamily = Mono) }, onClick = { barMenu = false; scope.launch { serverInfo = runCatching { vm.nzbServer(config) }.getOrNull() ?: listOf("error" to "could not load") } })
                            DropdownMenuItem(text = { Text("View on web", fontFamily = Mono) }, onClick = { barMenu = false; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.baseUrl))) } })
                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
                            DropdownMenuItem(text = { Text("Speed: Unlimited", fontFamily = Mono) }, onClick = { barMenu = false; act { vm.nzbRate(config, 0) } })
                            DropdownMenuItem(text = { Text("Speed: 5 MB/s", fontFamily = Mono) }, onClick = { barMenu = false; act { vm.nzbRate(config, 5120) } })
                            DropdownMenuItem(text = { Text("Speed: 10 MB/s", fontFamily = Mono) }, onClick = { barMenu = false; act { vm.nzbRate(config, 10240) } })
                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(status?.note ?: "—", fontFamily = Mono, color = accent, fontSize = 13.sp)
                    Text(
                        status?.stats?.firstOrNull()?.let { "${it.second} KB/s" } ?: "",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ActionBtn("Pause all", true) { act { vm.nzbgetPause(config) } }
                    Spacer(Modifier.width(12.dp))
                    ActionBtn("Resume all", true) { act { vm.nzbgetResume(config) } }
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
                                else -> items(q) { qi ->
                                    QueueRow(
                                        item = qi,
                                        onAction = { cmd, txt -> act { vm.nzbEdit(config, cmd, qi.id, txt) } },
                                        onCategory = { categoryText = ""; categoryFor = qi.id },
                                    )
                                }
                            }
                        } else {
                            val h = history
                            when {
                                h == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                h.isEmpty() -> item { Text("no history", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                else -> items(h) { hi -> HistoryRow(hi) { cmd -> act { vm.nzbEdit(config, cmd, hi.id) } } }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Surface,
            title = { Text("Add NZB by URL", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("URL", addUrl) { addUrl = it }
                    Field("Category (optional)", addCat) { addCat = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = addUrl.isNotBlank(),
                    onClick = {
                        val u = addUrl; val c = addCat
                        showAdd = false; addUrl = ""; addCat = ""
                        act { vm.nzbAddUrl(config, u, c) }
                    },
                ) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
    serverInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { serverInfo = null },
            containerColor = Surface,
            title = { Text("Server details", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    info.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                            Text(v, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { serverInfo = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
    categoryFor?.let { id ->
        AlertDialog(
            onDismissRequest = { categoryFor = null },
            containerColor = Surface,
            title = { Text("Set category", fontFamily = Mono, color = MatrixGreen) },
            text = { Field("Category", categoryText) { categoryText = it } },
            confirmButton = {
                TextButton(onClick = {
                    val c = categoryText
                    categoryFor = null; categoryText = ""
                    act { vm.nzbEdit(config, "GroupSetCategory", id, c) }
                }) { Text("Set", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { categoryFor = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun QueueRow(item: NzbQueueItem, onAction: (String, String) -> Unit, onCategory: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 8.dp)) {
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
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Pause", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupPause", "") })
            DropdownMenuItem(text = { Text("Resume", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupResume", "") })
            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupDelete", "") })
            DropdownMenuItem(text = { Text("Priority: High", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupSetPriority", "50") })
            DropdownMenuItem(text = { Text("Priority: Normal", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupSetPriority", "0") })
            DropdownMenuItem(text = { Text("Priority: Low", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupSetPriority", "-50") })
            DropdownMenuItem(text = { Text("Move to top", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupMoveTop", "") })
            DropdownMenuItem(text = { Text("Move to bottom", fontFamily = Mono) }, onClick = { menu = false; onAction("GroupMoveBottom", "") })
            DropdownMenuItem(text = { Text("Set category…", fontFamily = Mono) }, onClick = { menu = false; onCategory() })
        }
    }
}

@Composable
private fun HistoryRow(item: NzbHistoryEntry, onAction: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val statusColor = when {
        item.status.contains("SUCCESS", true) -> MatrixGreen
        item.status.contains("FAILURE", true) || item.status.contains("DELETED", true) -> ErrRed
        else -> MatrixGreen.copy(alpha = 0.7f)
    }
    Box {
        Column(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 8.dp)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.status, fontFamily = Mono, color = statusColor, fontSize = 11.sp)
                Text("${item.sizeMb} MB", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Redownload", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryRedownload") })
            DropdownMenuItem(text = { Text("Return to queue", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryReturn") })
            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryDelete") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDetailScreen(
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
    var actionResult by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf(false) }

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
                    Box {
                        IconButton(onClick = { barMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen)
                        }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; onDelete() })
                        }
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
