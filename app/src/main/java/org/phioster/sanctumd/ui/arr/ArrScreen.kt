package org.phioster.sanctumd.ui.arr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ArrLookupItem
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ArrHistoryItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
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
import org.phioster.sanctumd.ServiceLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    initialDetailId: Int? = null, // deep link from global search: open this item's detail
    initialAddTerm: String? = null, // deep link from global search: open the add dialog with this term
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
    var queueFilter by remember { mutableStateOf("") } // "" = all; else a status to filter the queue by
    var history by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrHistoryItem>?>(null) }
    var detailId by remember { mutableStateOf(initialDetailId) }
    val supportsDetail = config.type == ServiceType.RADARR || config.type == ServiceType.SONARR || config.type == ServiceType.LIDARR
    val supportsImport = config.type == ServiceType.RADARR || config.type == ServiceType.SONARR
    var showSystem by remember { mutableStateOf(false) }
    var showIndexers by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showBlocklist by remember { mutableStateOf(false) }
    var arrSys by remember { mutableStateOf<org.phioster.sanctumd.model.ArrSystemInfo?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val rememberedPath by vm.lastImportPath.collectAsState()
    var importFolder by remember { mutableStateOf("") }
    // A counter, not a flag: a LaunchedEffect is cancelled the moment its key changes, so an
    // effect that cleared its own boolean key killed the very scan it had just started
    // ("The coroutine scope left the composition"). Bumping a token the effect never writes
    // keeps the key stable for the whole run.
    var scanRequest by remember { mutableStateOf(0) }
    var importItems by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrImportItem>?>(null) }
    var importScanning by remember { mutableStateOf(false) }
    var importSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }


    // Assigning a target movie to an unmatched manual-import row (Radarr).
    var assignRow by remember { mutableStateOf<Int?>(null) }
    var assignEpisodeRow by remember { mutableStateOf<Int?>(null) }
    var blockedQueue by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrQueueItem>>(emptyList()) }
    var assignLibrary by remember { mutableStateOf<List<ArrLibraryItem>?>(null) }
    var assignQuery by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(0) } // 0=Title, 1=Year, 2=Size
    var sortMenu by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }

    suspend fun runImportScan() {
        importScanning = true; importItems = null; importSelected = emptySet()
        importItems = runCatching { vm.arrManualScan(config, importFolder.trim()) }.getOrElse {
            actionMsg = "error: ${it.message}"; emptyList()
        }
        importSelected = importItems!!.mapIndexedNotNull { i, it -> if (it.importable) i else null }.toSet()
        importScanning = false
        // Next time the browser opens here rather than at the root.
        if (importFolder.isNotBlank()) vm.rememberImportPath(importFolder.trim())
    }

    /** Opens the manual import already pointed at [path] and scans it — no browsing needed. */
    fun openImportAt(path: String) {
        importFolder = path
        importItems = null
        importSelected = emptySet()
        showImport = true
        scanRequest++
    }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(actionMsg) { if (actionMsg != null) { kotlinx.coroutines.delay(4000); actionMsg = null } }
    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(initialAddTerm != null) }
    var addTerm by remember { mutableStateOf(initialAddTerm ?: "") }
    var addResults by remember { mutableStateOf<List<ArrLookupItem>?>(null) }
    // Deep link from search: run the pre-filled lookup right away.
    LaunchedEffect(Unit) {
        if (initialAddTerm != null) {
            addResults = runCatching { vm.arrLookupList(config, initialAddTerm) }.getOrElse { emptyList() }
        }
    }
    var selected by remember { mutableStateOf<ArrLookupItem?>(null) }
    var profiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosenProfile by remember { mutableStateOf<ArrProfile?>(null) }
    var chosenFolder by remember { mutableStateOf<String?>(null) }
    var metaProfiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var chosenMeta by remember { mutableStateOf<ArrProfile?>(null) }
    var monitored by remember { mutableStateOf(true) }
    // Interactive "custom search" release picker for a missing/cutoff row.
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerReleases by remember { mutableStateOf<List<ArrRelease>?>(null) }
    var pickerTitle by remember { mutableStateOf("") }
    var confirmGrab by remember { mutableStateOf<ArrRelease?>(null) }

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
    // Open the interactive release list for a wanted item (movie/episode/album by type).
    fun openCustomSearch(item: ArrMissingItem) {
        pickerTitle = item.title; pickerReleases = null; pickerOpen = true
        scope.launch {
            pickerReleases = runCatching {
                when (config.type) {
                    ServiceType.SONARR -> vm.arrReleasesFor(config, movieId = null, episodeId = item.id)
                    ServiceType.LIDARR -> vm.arrReleasesFor(config, movieId = null, episodeId = null, albumId = item.id)
                    else -> vm.arrReleasesFor(config, movieId = item.id, episodeId = null)
                }
            }.getOrElse { actionMsg = "error: ${it.message}"; pickerOpen = false; emptyList() }
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
                            DropdownMenuItem(text = { Text("Add new", fontFamily = Mono) }, onClick = { barMenu = false; addTerm = ""; addResults = null; showAdd = true })
                            if (supportsImport) {
                                DropdownMenuItem(text = { Text("Manual import", fontFamily = Mono) }, onClick = {
                                    barMenu = false; showImport = true; importItems = null; importSelected = emptySet()
                                    importFolder = rememberedPath
                                })
                            }
                            if (supportsDetail) {
                                DropdownMenuItem(text = { Text("System & health", fontFamily = Mono) }, onClick = {
                                    barMenu = false; showSystem = true; arrSys = null
                                    scope.launch { arrSys = runCatching { vm.arrSystemInfo(config) }.getOrNull() }
                                })
                            }
                            DropdownMenuItem(text = { Text("Indexers", fontFamily = Mono) }, onClick = { barMenu = false; showIndexers = true })
                            DropdownMenuItem(text = { Text("Quality profiles", fontFamily = Mono) }, onClick = { barMenu = false; showProfiles = true })
                            DropdownMenuItem(text = { Text("Blocklist", fontFamily = Mono) }, onClick = { barMenu = false; showBlocklist = true })
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
                    IconButton(enabled = !refreshing, onClick = { actionMsg = null; scope.launch { refreshing = true; reload(); refreshing = false } }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = MatrixGreen, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            val arrTabs = listOf("Library", "Missing", "Cutoff", "Queue", "History")
            val arrChipState = rememberLazyListState()
            LaunchedEffect(tab) { arrChipState.animateScrollToItem(tab) }
            LazyRow(
                state = arrChipState,
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(arrTabs) { i, name ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name, fontFamily = Mono) })
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
            SwipeTabs(tab, 5, { tab = it }, Modifier.weight(1f).fillMaxWidth()) { page ->
                if (listError != null) {
                    Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (page) {
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
                                val m = if (page == 1) missing else cutoff
                                when {
                                    m == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    m.isEmpty() -> item { Text(if (page == 1) "nothing missing" else "nothing below cutoff", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(m) { mi ->
                                        ArrMissingRow(
                                            item = mi,
                                            accent = accent,
                                            onSearch = { act { vm.arrSearch(config, mi.id) } },
                                            onCustomSearch = { openCustomSearch(mi) },
                                        )
                                    }
                                }
                            }
                            3 -> {
                                val q = queue
                                when {
                                    q == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    q.isEmpty() -> item { Text("queue is empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> {
                                        val statuses = q.map { it.status }.filter { it.isNotBlank() }.distinct()
                                        // Ignore a filter whose status no longer exists in the queue (download finished etc.).
                                        val activeFilter = if (queueFilter in statuses) queueFilter else ""
                                        if (statuses.size > 1) {
                                            item {
                                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    FilterChip(selected = activeFilter == "", onClick = { queueFilter = "" }, label = { Text("all", fontFamily = Mono) })
                                                    statuses.forEach { s ->
                                                        Spacer(Modifier.width(6.dp))
                                                        FilterChip(selected = activeFilter == s, onClick = { queueFilter = if (activeFilter == s) "" else s }, label = { Text(s, fontFamily = Mono) })
                                                    }
                                                }
                                            }
                                        }
                                        val shown = q.filter { activeFilter.isEmpty() || it.status == activeFilter }
                                        items(shown) { qi ->
                                            ArrQueueRow(
                                                qi,
                                                onRemove = { act { vm.arrRemove(config, qi.id) } },
                                                onBlocklist = { act { vm.arrRemoveAndBlock(config, qi.id) } },
                                                onImport = { openImportAt(qi.outputPath) },
                                            )
                                        }
                                    }
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

    if (showBlocklist) {
        ArrBlocklistDialog(vm, config, accent, onDismiss = { showBlocklist = false }) { actionMsg = it }
    }

    if (showProfiles) {
        ArrProfilesDialog(vm, config, accent, onDismiss = { showProfiles = false }) { actionMsg = it }
    }

    if (showIndexers) {
        ArrIndexersDialog(vm, config, accent) { showIndexers = false }
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

    LaunchedEffect(scanRequest) {
        if (scanRequest > 0 && showImport) runImportScan()
    }

    if (showImport) {
        val items = importItems
        AlertDialog(
            onDismissRequest = { showImport = false },
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
                                        Text("→ ${it.matchedTitle}${if (it.quality.isNotBlank()) " · ${it.quality}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (it.rejection.isNotBlank()) Text(it.rejection, fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (unmatched) {
                                        Text(
                                            "assign",
                                            fontFamily = Mono, color = accent, fontSize = 11.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    // Sonarr needs episode ids, not just a series — its own two-step picker.
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
                        showImport = false
                        scope.launch {
                            actionMsg = vm.arrManualImport(config, chosen)
                            vm.refreshAll()
                            // A hand-assigned import leaves its queue entry on importBlocked and the
                            // source file on disk twice; surface those rather than leaving them to rot.
                            blockedQueue = runCatching { vm.arrBlockedQueue(config) }.getOrDefault(emptyList())
                        }
                    },
                ) { Text("Import (${importSelected.size})", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (blockedQueue.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { blockedQueue = emptyList() },
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
                                TextButton(onClick = { blockedQueue = emptyList(); openImportAt(q.outputPath) }) {
                                    Text("import", fontFamily = Mono, color = accent, fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    actionMsg = vm.arrRemove(config, q.id)
                                    blockedQueue = runCatching { vm.arrBlockedQueue(config) }.getOrDefault(emptyList())
                                    vm.refreshAll()
                                }
                            }) { Text("remove", fontFamily = Mono, color = ErrRed, fontSize = 11.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { blockedQueue = emptyList() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
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

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            containerColor = Surface,
            title = { Text("Releases", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                val rs = pickerReleases
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
                    scope.launch { actionMsg = vm.arrGrabRelease(config, r.guid, r.indexerId); reload() }
                }) { Text("Grab", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmGrab = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
internal fun ArrLibraryRow(item: ArrLibraryItem, accent: Color, onOpen: (() -> Unit)?, onSearch: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { if (onOpen != null) onOpen() else menu = true }.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArrPoster(item.posterUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.sizeMb > 0) {
                    Text("${item.sizeMb} MB", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Search", fontFamily = Mono) }, onClick = { menu = false; onSearch() })
        }
    }
}

/** Small poster thumbnail for the arr library rows; falls back to an empty surface tile. */
@Composable
internal fun ArrPoster(url: String, width: androidx.compose.ui.unit.Dp = 46.dp, height: androidx.compose.ui.unit.Dp = 68.dp) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(4.dp)).background(Surface),
        )
    } else {
        Box(Modifier.width(width).height(height).clip(RoundedCornerShape(4.dp)).background(Surface))
    }
}

@Composable
internal fun ArrMissingRow(item: ArrMissingItem, accent: Color, onSearch: () -> Unit, onCustomSearch: () -> Unit) {
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
            DropdownMenuItem(text = { Text("Automatic search", fontFamily = Mono) }, onClick = { menu = false; onSearch() })
            DropdownMenuItem(text = { Text("Custom search", fontFamily = Mono) }, onClick = { menu = false; onCustomSearch() })
        }
    }
}

@Composable
internal fun ArrQueueRow(
    item: ArrQueueItem,
    onRemove: () -> Unit,
    onBlocklist: () -> Unit = {},
    onImport: () -> Unit = {},
) {
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
            // The queue already knows where the download landed; browsing to it by hand means
            // clicking down a nested tree for a path nobody remembers.
            if (item.outputPath.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Import this", fontFamily = Mono) },
                    onClick = { menu = false; onImport() },
                )
            }
            DropdownMenuItem(text = { Text("Remove", fontFamily = Mono) }, onClick = { menu = false; onRemove() })
            // Removing alone lets the same broken release be grabbed again on the next search.
            DropdownMenuItem(
                text = { Text("Remove + blocklist", fontFamily = Mono, color = ErrRed) },
                onClick = { menu = false; onBlocklist() },
            )
        }
    }
}

@Composable
internal fun ArrHistoryRow(item: ArrHistoryItem, accent: Color) {
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
