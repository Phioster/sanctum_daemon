package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.SeerrIssueItem
import org.phioster.sanctumd.model.SeerrRequestItem
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.aspectRatio
import org.phioster.sanctumd.ui.theme.SurfaceHi
import org.phioster.sanctumd.model.SeerrSearchItem
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
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ServiceLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeerrScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    initialDetail: Pair<Int, String>? = null, // deep link from global search: (tmdbId, mediaType)
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0=Requests, 1=Issues, 2=Discover
    var reqFilter by remember { mutableStateOf("all") }
    var issueFilter by remember { mutableStateOf("open") }
    var discoverKind by remember { mutableStateOf("trending") }
    var filterMenu by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<SeerrRequestItem>?>(null) }
    var issues by remember { mutableStateOf<List<SeerrIssueItem>?>(null) }
    var discover by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrDiscoverItem>?>(null) }
    var watchlist by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrDiscoverItem>?>(null) }
    var genres by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var genreItems by remember { mutableStateOf<Map<Int, List<org.phioster.sanctumd.model.SeerrDiscoverItem>>>(emptyMap()) }
    var genreType by remember { mutableStateOf("movies") } // "movies" | "tv" — which genre catalog the rows use
    var category by remember { mutableStateOf<Triple<Int, String, String>?>(null) } // (genreId, name, kind) for the full-screen category view
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(actionMsg) { if (actionMsg != null) { kotlinx.coroutines.delay(4000); actionMsg = null } }
    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SeerrSearchItem>?>(null) }
    var confirmItem by remember { mutableStateOf<SeerrSearchItem?>(null) }
    var rootFolders by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrRootFolder>>(emptyList()) }
    var chosenFolder by remember { mutableStateOf<org.phioster.sanctumd.model.SeerrRootFolder?>(null) }
    var mediaDetail by remember { mutableStateOf<org.phioster.sanctumd.model.SeerrMediaDetail?>(null) }
    var mediaDetailLoading by remember { mutableStateOf(initialDetail != null) }
    // Deep link from search: open the media-detail dialog right away.
    LaunchedEffect(Unit) {
        if (initialDetail != null) {
            mediaDetail = runCatching { vm.seerrMediaDetailById(config, initialDetail.first, initialDetail.second) }.getOrNull()
            mediaDetailLoading = false
        }
    }
    var showStats by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var users by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrUserInfo>?>(null) }
    var seasons by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrSeason>?>(null) }
    var selectedSeasons by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var issueDetailId by remember { mutableStateOf<Int?>(null) }
    var issueDetail by remember { mutableStateOf<org.phioster.sanctumd.model.SeerrIssueDetail?>(null) }
    var commentText by remember { mutableStateOf("") }

    val reqFilters = listOf("all", "pending", "approved", "processing", "failed", "available", "unavailable")
    val issueFilters = listOf("open", "resolved", "all")
    val discoverKinds = listOf("trending", "movies", "tv", "genres")

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
    suspend fun loadWatchlist() {
        listError = null
        try {
            watchlist = vm.seerrWatchlistOf(config)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            // No Plex link → the endpoint may 404; degrade to the friendly empty note instead of an error.
            watchlist = emptyList()
        }
    }
    // Discover-by-genre: load the genre catalogue, then each genre's first page in parallel.
    suspend fun loadGenreRows() {
        listError = null
        try {
            genres = null
            genreItems = emptyMap()
            val gs = vm.seerrGenresOf(config, genreType).take(14)
            genres = gs
            coroutineScope {
                genreItems = gs.map { (id, _) ->
                    async { id to runCatching { vm.seerrDiscoverGenreOf(config, genreType, id) }.getOrDefault(emptyList()) }
                }.awaitAll().toMap()
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    fun openDiscoverDetail(di: org.phioster.sanctumd.model.SeerrDiscoverItem) {
        mediaDetailLoading = true
        scope.launch {
            mediaDetail = runCatching { vm.seerrMediaDetailById(config, di.tmdbId, di.mediaType) }.getOrNull()
                ?: org.phioster.sanctumd.model.SeerrMediaDetail(di.tmdbId, di.title, di.year, di.mediaType, "", di.posterUrl, emptyList(), "", di.status, emptyList())
            mediaDetailLoading = false
        }
    }
    LaunchedEffect(mode, reqFilter, issueFilter, discoverKind, genreType) {
        when (mode) {
            0 -> loadRequests()
            1 -> loadIssues()
            3 -> loadWatchlist()
            else -> if (discoverKind == "genres") loadGenreRows() else loadDiscover()
        }
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
                            DropdownMenuItem(text = { Text("New request", fontFamily = Mono) }, onClick = { barMenu = false; searchTerm = ""; searchResults = null; showAdd = true })
                            DropdownMenuItem(text = { Text("Users & stats", fontFamily = Mono) }, onClick = {
                                barMenu = false; showStats = true; stats = null; users = null
                                scope.launch {
                                    stats = runCatching { vm.seerrStats(config) }.getOrDefault(emptyList())
                                    users = runCatching { vm.seerrUserList(config) }.getOrDefault(emptyList())
                                }
                            })
                            DropdownMenuItem(text = { Text("Open in Seerr", fontFamily = Mono) }, onClick = { barMenu = false; openExternal(context, seerrAppPackages, config.baseUrl) })
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
                    val seerrChips = listOf("Requests", "Issues", "Discover", "Watchlist")
                    val seerrChipState = rememberLazyListState()
                    LaunchedEffect(mode) { seerrChipState.animateScrollToItem(mode) }
                    LazyRow(
                        state = seerrChipState,
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(seerrChips) { i, label ->
                            FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label, fontFamily = Mono) })
                        }
                    }
                    if (mode != 3) {
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
                    }
                    IconButton(enabled = !refreshing, onClick = { actionMsg = null; scope.launch { refreshing = true; when (mode) { 0 -> loadRequests(); 1 -> loadIssues(); 3 -> loadWatchlist(); else -> if (discoverKind == "genres") loadGenreRows() else loadDiscover() }; refreshing = false } }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = MatrixGreen, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            SwipeTabs(mode, 4, { mode = it }, Modifier.weight(1f).fillMaxWidth()) { page ->
                if (listError != null) {
                    Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (page) {
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
                            3 -> {
                                val w = watchlist
                                when {
                                    w == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    w.isEmpty() -> item { Text("watchlist is empty (needs a Plex-linked account)", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(w) { di -> SeerrDiscoverRow(di, accent) { openDiscoverDetail(di) } }
                                }
                            }
                            else -> if (discoverKind == "genres") {
                                // media-type toggle for which genre catalogue to browse
                                item {
                                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("movies" to "movies", "tv" to "series").forEach { (key, label) ->
                                            val sel = genreType == key
                                            Text(
                                                label, fontFamily = Mono, fontSize = 12.sp,
                                                color = if (sel) Black else MatrixGreen,
                                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                                    .background(if (sel) MatrixGreen else Surface)
                                                    .clickable { genreType = key }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                                val gs = genres
                                when {
                                    gs == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    gs.isEmpty() -> item { Text("no genres", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> gs.forEach { (id, name) ->
                                        val its = genreItems[id]
                                        if (!its.isNullOrEmpty()) {
                                            item(key = "genre-$id") {
                                                SeerrGenreSection(
                                                    name = name, items = its, accent = accent,
                                                    onSeeAll = { category = Triple(id, name, genreType) },
                                                    onOpen = { di -> openDiscoverDetail(di) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val d = discover
                                when {
                                    d == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    d.isEmpty() -> item { Text("nothing to show", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(d) { di -> SeerrDiscoverRow(di, accent) { openDiscoverDetail(di) } }
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
        rootFolders = emptyList(); chosenFolder = null
        if (ci != null) {
            // Only worth offering when there is something to choose between; a single-folder
            // setup keeps the dialog exactly as it was.
            val f = runCatching { vm.seerrRootFoldersOf(config, ci.mediaType) }.getOrDefault(emptyList())
            if (f.size > 1) {
                rootFolders = f
                chosenFolder = f.firstOrNull { it.isDefault } ?: f.first()
            }
        }
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
                    if (rootFolders.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        DropdownField(
                            "Folder",
                            chosenFolder?.path?.substringAfterLast('/').orEmpty(),
                            rootFolders.map { it.path },
                        ) { i -> chosenFolder = rootFolders[i] }
                    }
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
                        // Only send a folder when the user steered away from Seerr's default.
                        val folder = chosenFolder?.takeIf { !it.isDefault }
                        confirmItem = null
                        scope.launch {
                            actionMsg = vm.seerrRequestMedia(config, tmdb, type, chosen, folder?.path, folder?.serverId)
                            loadRequests()
                            vm.refreshAll()
                        }
                    },
                ) { Text("Request", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmItem = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (mediaDetailLoading && mediaDetail == null) {
        AlertDialog(
            onDismissRequest = { mediaDetailLoading = false },
            containerColor = Surface,
            title = { Text("loading…", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("", fontFamily = Mono) },
            confirmButton = { TextButton(onClick = { mediaDetailLoading = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    mediaDetail?.let { d ->
        BackHandler { mediaDetail = null }
        var onWatchlist by remember(d.tmdbId) { mutableStateOf(d.onWatchlist) }
        var watchlistBusy by remember(d.tmdbId) { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Black)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // ── Banner: blurred poster backdrop + sharp poster + title ──
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    if (d.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = d.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().blur(28.dp).background(Surface),
                        )
                    } else {
                        Box(Modifier.matchParentSize().background(Surface))
                    }
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Black.copy(alpha = 0.35f), Black.copy(alpha = 0.65f), Black))))
                    Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        if (d.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = d.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).background(Surface),
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(d.title, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val quick = d.facts.take(3).joinToString("  ·  ") { it.second }
                            if (quick.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(quick, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (d.status.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(d.status, fontFamily = Mono, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // ── Body ──
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("request", Modifier.fillMaxWidth(), accent = accent) {
                        val item = SeerrSearchItem(d.tmdbId, d.title, d.year, d.mediaType)
                        mediaDetail = null
                        confirmItem = item
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        if (onWatchlist) "on watchlist  ✓" else "add to watchlist",
                        Modifier.fillMaxWidth(),
                        icon = if (onWatchlist) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        accent = if (onWatchlist) MatrixGreen else accent,
                        enabled = !watchlistBusy,
                    ) {
                        watchlistBusy = true
                        scope.launch {
                            val res = if (onWatchlist) {
                                vm.seerrRemoveFromWatchlistOf(config, d.tmdbId, d.mediaType)
                            } else {
                                vm.seerrAddToWatchlistOf(config, d.tmdbId, d.mediaType, d.title)
                            }
                            if (!res.startsWith("error")) onWatchlist = !onWatchlist
                            watchlistBusy = false
                            android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("open in Seerr", Modifier.fillMaxWidth(), accent = accent) {
                        openExternal(context, seerrAppPackages, "${config.normalizedBaseUrl}${d.mediaType}/${d.tmdbId}")
                    }
                    if (d.facts.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        d.facts.chunked(2).forEach { pair ->
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
                    if (d.genres.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(d.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                    if (d.overview.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    if (d.cast.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader("CAST")
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            d.cast.forEach { member ->
                                Column(Modifier.width(84.dp).padding(end = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (member.profileUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = member.profileUrl, contentDescription = null, contentScale = ContentScale.Crop,
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
                    Spacer(Modifier.height(24.dp))
                }
                Spacer(Modifier.navigationBarsPadding())
            }
            // ── Top bar overlay: back + open-in-Seerr ──
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { mediaDetail = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { openExternal(context, seerrAppPackages, "${config.normalizedBaseUrl}${d.mediaType}/${d.tmdbId}") }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open in Seerr", tint = accent)
                }
            }
        }
    }

    category?.let { (genreId, catName, catKind) ->
        BackHandler { category = null }
        var sort by remember(genreId, catKind) { mutableStateOf("popularity.desc") }
        var catItems by remember(genreId, catKind) { mutableStateOf<List<org.phioster.sanctumd.model.SeerrDiscoverItem>>(emptyList()) }
        var catPage by remember(genreId, catKind) { mutableStateOf(1) }
        var catLoading by remember(genreId, catKind) { mutableStateOf(false) }
        var catEnd by remember(genreId, catKind) { mutableStateOf(false) }
        val gridState = rememberLazyGridState()
        val newestSort = if (catKind == "tv") "first_air_date.desc" else "release_date.desc"
        LaunchedEffect(sort) {
            catItems = emptyList(); catPage = 1; catEnd = false; catLoading = true
            val first = runCatching { vm.seerrDiscoverGenreOf(config, catKind, genreId, sort, 1) }.getOrDefault(emptyList())
            catItems = first.distinctBy { it.tmdbId }; catLoading = false; if (first.isEmpty()) catEnd = true
        }
        LaunchedEffect(gridState, sort) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                if (!catLoading && !catEnd && catItems.isNotEmpty() && last >= catItems.size - 6) {
                    catLoading = true
                    val next = catPage + 1
                    val more = runCatching { vm.seerrDiscoverGenreOf(config, catKind, genreId, sort, next) }.getOrDefault(emptyList())
                    if (more.isEmpty()) catEnd = true else { catItems = (catItems + more).distinctBy { it.tmdbId }; catPage = next }
                    catLoading = false
                }
            }
        }
        // Hidden (but state kept) while a media detail is open on top — the detail is composed
        // earlier in the tree, so it would otherwise draw behind this full-screen grid.
        if (mediaDetail == null) Box(Modifier.fillMaxSize().background(Black)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { category = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                    Text(catName.uppercase(), fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (catKind == "tv") "series" else "movies", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("popularity.desc" to "Popular", newestSort to "Newest", "vote_average.desc" to "Top rated").forEach { (v, label) ->
                        val sel = sort == v
                        Text(
                            label, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) accent else SurfaceHi).clickable { sort = v }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(catItems, key = { it.tmdbId }) { di -> SeerrCategoryPoster(di) { openDiscoverDetail(di) } }
                    }
                    if (catLoading && catItems.isEmpty()) {
                        CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.align(Alignment.Center))
                    }
                    if (!catLoading && catItems.isEmpty()) {
                        Text("nothing here", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            containerColor = Surface,
            title = { Text("Users & stats", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    SectionHeader("REQUESTS")
                    Spacer(Modifier.height(6.dp))
                    val st = stats
                    when {
                        st == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> st.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                pair.forEach { (k, v) ->
                                    Column(Modifier.weight(1f)) {
                                        Text(v, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(k.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("USERS")
                    Spacer(Modifier.height(6.dp))
                    val us = users
                    when {
                        us == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        us.isEmpty() -> Text("no users", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> us.forEach { u ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (u.email.isNotBlank()) Text(u.email, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${u.requestCount} req", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
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
internal fun SeerrRequestRow(item: SeerrRequestItem, accent: Color, onApprove: () -> Unit, onDecline: () -> Unit) {
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
                .padding(vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(46.dp).height(69.dp).clip(RoundedCornerShape(4.dp)).background(Surface),
                    )
                } else {
                    Box(Modifier.width(46.dp).height(69.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
internal fun SeerrIssueRow(item: SeerrIssueItem, accent: Color, onClick: () -> Unit) {
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
internal fun SeerrDiscoverRow(item: org.phioster.sanctumd.model.SeerrDiscoverItem, accent: Color, onRequest: () -> Unit) {
    val statusColor = when (item.status) {
        "available" -> MatrixGreen
        "processing", "pending", "partial" -> Color(0xFFFFAA00)
        else -> MatrixGreen.copy(alpha = 0.5f)
    }
    Row(
        Modifier.fillMaxWidth().clickable { onRequest() }.padding(vertical = 8.dp),
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

/** One genre's horizontal poster row in the Discover "genres" view, with a "see all ›" that opens
 *  the full-screen category view. */
@Composable
private fun SeerrGenreSection(
    name: String,
    items: List<org.phioster.sanctumd.model.SeerrDiscoverItem>,
    accent: Color,
    onSeeAll: () -> Unit,
    onOpen: (org.phioster.sanctumd.model.SeerrDiscoverItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onSeeAll() }.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name.uppercase(), fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("see all ›", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.tmdbId }) { di -> SeerrGenrePoster(di, onOpen) }
        }
    }
}

@Composable
private fun SeerrGenrePoster(item: org.phioster.sanctumd.model.SeerrDiscoverItem, onOpen: (org.phioster.sanctumd.model.SeerrDiscoverItem) -> Unit) {
    Column(Modifier.width(128.dp).clickable { onOpen(item) }) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(128.dp).height(192.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(128.dp).height(192.dp).clip(RoundedCornerShape(6.dp)).background(Surface))
        }
        Spacer(Modifier.height(4.dp))
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A poster tile that fills its grid cell (2:3 poster + title) for the full-screen category grid. */
@Composable
private fun SeerrCategoryPoster(item: org.phioster.sanctumd.model.SeerrDiscoverItem, onOpen: () -> Unit) {
    Column(Modifier.clickable { onOpen() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(Surface)) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
