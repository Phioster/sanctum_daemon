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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
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
import org.phioster.sanctumd.model.ArrMissingItem
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
    val supportsImport = config.type == ServiceType.RADARR || config.type == ServiceType.SONARR ||
        config.type == ServiceType.LIDARR
    var showSystem by remember { mutableStateOf(false) }
    var showIndexers by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showBlocklist by remember { mutableStateOf(false) }
    val prowlarr = remember { vm.prowlarrService() }
    var prowlarrSearchFor by remember { mutableStateOf<String?>(null) }
    var importAt by remember { mutableStateOf<ArrImportStart?>(null) }
    val rememberedPath by vm.lastImportPath.collectAsState()

    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(0) } // 0=Title, 1=Year, 2=Size
    var sortMenu by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(actionMsg) { if (actionMsg != null) { kotlinx.coroutines.delay(4000); actionMsg = null } }
    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(initialAddTerm != null) }
    var addTerm by remember { mutableStateOf(initialAddTerm ?: "") }
    // Interactive "custom search" release picker for a missing/cutoff row.
    var pickFor by remember { mutableStateOf<ArrMissingItem?>(null) }

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

    // The lookup takes the whole screen: a title is looked at first and only then added, so the
    // list of hits is not a menu that fires on the first tap.
    if (showAdd) {
        ArrLookupScreen(
            vm = vm,
            config = config,
            initialTerm = addTerm,
            onBack = { showAdd = false },
            onOpenLibraryItem = { id -> showAdd = false; detailId = id },
            onAdded = { msg ->
                showAdd = false
                actionMsg = msg
                scope.launch { reload(); vm.refreshAll() }
            },
        )
        return
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
                            DropdownMenuItem(text = { Text("Search & add", fontFamily = Mono) }, onClick = { barMenu = false; addTerm = ""; showAdd = true })
                            if (supportsImport) {
                                DropdownMenuItem(text = { Text("Manual import", fontFamily = Mono) }, onClick = {
                                    barMenu = false
                                    importAt = ArrImportStart(rememberedPath, scan = false)
                                })
                            }
                            if (supportsDetail) {
                                DropdownMenuItem(text = { Text("System & health", fontFamily = Mono) }, onClick = {
                                    barMenu = false; showSystem = true
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
                                            onCustomSearch = { pickFor = mi },
                                            onProwlarrSearch = prowlarr?.let { { prowlarrSearchFor = mi.title } },
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
                                                onImport = { importAt = ArrImportStart(qi.outputPath, scan = true) },
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
                                    else -> items(h) { ev ->
                                        ArrHistoryRow(ev, accent) { act { vm.arrBlockFromHistory(config, ev.id) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    prowlarrSearchFor?.let { term ->
        prowlarr?.let { pw ->
            ArrProwlarrSearchDialog(vm, pw, config, term, accent, onDismiss = { prowlarrSearchFor = null }) { msg ->
                prowlarrSearchFor = null
                actionMsg = msg
                scope.launch { vm.refreshAll() }
            }
        }
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

    if (showSystem) ArrSystemDialog(vm, config) { showSystem = false }

    importAt?.let { st ->
        ArrImportDialog(vm, config, accent, st, onMessage = { actionMsg = it }) { importAt = null }
    }

    pickFor?.let { wanted ->
        ArrReleasePicker(
            vm, config, wanted, accent,
            onMessage = { actionMsg = it },
            onGrabbed = { scope.launch { reload() } },
            onDismiss = { pickFor = null },
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
internal fun ArrMissingRow(
    item: ArrMissingItem,
    accent: Color,
    onSearch: () -> Unit,
    onCustomSearch: () -> Unit,
    onProwlarrSearch: (() -> Unit)? = null,
) {
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
            // The service searches by id; when the indexer has the wrong one, only text finds it.
            onProwlarrSearch?.let { go ->
                DropdownMenuItem(text = { Text("Search on Prowlarr", fontFamily = Mono) }, onClick = { menu = false; go() })
            }
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
internal fun ArrHistoryRow(item: ArrHistoryItem, accent: Color, onBlocklist: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    // Only a grab can be blocked. An import or a deletion is not a release.
    val blockable = item.eventType.equals("grabbed", true) || item.eventType.equals("downloadFailed", true)
    val evColor = when (item.eventType) {
        "grabbed" -> MatrixGreen
        "downloadFolderImported" -> accent.copy(alpha = 0.9f)
        "downloadFailed", "episodeFileDeleted", "movieFileDeleted" -> ErrRed
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .let { m -> if (blockable) m.clickable { menu = true } else m }
                .padding(vertical = 10.dp),
        ) {
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.eventType} · ${item.date}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (item.quality.isNotBlank()) Text(item.quality, fontFamily = Mono, color = evColor, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Blocklist this release", fontFamily = Mono, color = ErrRed) },
                onClick = { menu = false; onBlocklist() },
            )
        }
    }
}
