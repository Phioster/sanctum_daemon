package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.model.SeerrIssueItem
import org.phioster.sanctumd.model.SeerrRequestItem
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
    var confirmItem by remember { mutableStateOf<SeerrSearchItem?>(null) }
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
    var issueDetailId by remember { mutableStateOf<Int?>(null) }
    var reportFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var requestDetail by remember { mutableStateOf<org.phioster.sanctumd.model.SeerrRequestDetail?>(null) }

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
            // This used to swallow every failure into an empty list, on the belief that the
            // endpoint needs a Plex-linked account. It does not — Jellyseerr keeps its own
            // watchlist — so a real failure was being shown as "nothing on your watchlist".
            watchlist = emptyList()
            listError = t.message
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
                            DropdownMenuItem(text = { Text("Search & request", fontFamily = Mono) }, onClick = { barMenu = false; showAdd = true })
                            DropdownMenuItem(text = { Text("Users & stats", fontFamily = Mono) }, onClick = { barMenu = false; showStats = true })
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
                        else Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
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
                                        SeerrRequestRow(req, accent) {
                                            requestDetail = null
                                            scope.launch {
                                                requestDetail = runCatching { vm.seerrRequestDetailOf(config, req.id) }.getOrNull()
                                                if (requestDetail == null) actionMsg = "could not load request ${req.id}"
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                val i = issues
                                when {
                                    i == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    i.isEmpty() -> item { Text("no issues", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(i) { iss ->
                                        SeerrIssueRow(iss, accent) { issueDetailId = iss.id }
                                    }
                                }
                            }
                            3 -> {
                                val w = watchlist
                                when {
                                    w == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    w.isEmpty() -> item { Text("watchlist is empty — add titles from a title's page, or in Seerr itself", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
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
        SeerrSearchDialog(vm, config, accent, onDismiss = { showAdd = false }) { r ->
            showAdd = false
            openDiscoverDetail(r)
        }
    }
    confirmItem?.let { item ->
        SeerrRequestDialog(vm, config, item, onDismiss = { confirmItem = null }) { msg ->
            actionMsg = msg
            scope.launch { loadRequests(); vm.refreshAll() }
        }
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

    requestDetail?.let { d ->
        SeerrRequestDetailDialog(vm, config, d, accent, onDismiss = { requestDetail = null }) { msg ->
            requestDetail = null
            actionMsg = msg
            scope.launch { loadRequests(); vm.refreshAll() }
        }
    }

    reportFor?.let { (mid, title) ->
        SeerrReportIssueDialog(vm, config, mid, title, accent, onDismiss = { reportFor = null }) { msg ->
            reportFor = null
            actionMsg = msg
            scope.launch { loadIssues() }
        }
    }

    mediaDetail?.let { d ->
        SeerrMediaDetailView(
            vm, config, d, accent,
            onBack = { mediaDetail = null },
            onRequest = { item -> mediaDetail = null; confirmItem = item },
            onReportIssue = { id, title -> reportFor = id to title },
        )
    }

    category?.let { (genreId, catName, catKind) ->
        SeerrCategoryView(
            vm, config, genreId, catName, catKind, accent,
            visible = mediaDetail == null,
            onBack = { category = null },
            onOpen = { di -> openDiscoverDetail(di) },
        )
    }

    if (showStats) SeerrStatsDialog(vm, config, accent) { showStats = false }

    issueDetailId?.let { iid ->
        SeerrIssueDialog(
            vm, config, iid, accent,
            onMessage = { actionMsg = it },
            onListChanged = { scope.launch { loadIssues() } },
            onClose = { issueDetailId = null },
        )
    }
}

@Composable
internal fun SeerrRequestRow(item: SeerrRequestItem, accent: Color, onOpen: () -> Unit) {
    val statusColor = when (item.status) {
        "approved" -> MatrixGreen
        "declined" -> ErrRed
        "pending" -> WarnAmber
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                // Always: a settled request still has settings worth seeing and a delete to run.
                .clickable { onOpen() }
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
    }
}

@Composable
internal fun SeerrIssueRow(item: SeerrIssueItem, accent: Color, onClick: () -> Unit) {
    val statusColor = if (item.status == "open") WarnAmber else MatrixGreen
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
        "processing", "pending", "partial" -> WarnAmber
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
