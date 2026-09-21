package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phioster.sanctumd.ServiceLogo
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.MediaRowStyle
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.MusicTrack
import org.phioster.sanctumd.service.DownloadService
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.player.MusicController
import org.phioster.sanctumd.ui.player.PlayRequest
import org.phioster.sanctumd.ui.player.PlayerScreen
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

@OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun JellyfinScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    initialItemId: String? = null, // deep link from global search: open this item's detail
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(3) } // 0=Now Playing, 1=Users, 2=Dashboard, 3=Media (default), 4=Live TV
    val ps = rememberJellyfinPeopleState()
    val ad = rememberJellyfinAdminState()
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf(false) }
    val ds = rememberJellyfinDetailState()
    val bs = rememberJellyfinBrowseState()
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }
    val downloads by vm.downloads.collectAsState(initial = emptyMap())
    val wifiOnly by vm.downloadsWifiOnly.collectAsState()
    val deleteWatched by vm.downloadsDeleteWatched.collectAsState()
    var downloadsManagerOpen by remember { mutableStateOf(false) }
    val hiddenLibs by vm.hiddenLibraries.collectAsState()
    val hiddenSet = hiddenLibs[config.id].orEmpty().toSet()
    var libraryFilterOpen by remember { mutableStateOf(false) }
    val mediaStyles by vm.mediaRowStyles.collectAsState()
    var configRow by remember { mutableStateOf<String?>(null) } // "resume"/"recent"/"libraries" being styled
    var rowPickerOpen by remember { mutableStateOf(false) } // the shared "customize rows" entry
    val musicState by MusicController.state.collectAsState()
    var nowPlayingOpen by remember { mutableStateOf(false) }
    // Attach to any running music session so the now-playing bar appears immediately.
    LaunchedEffect(Unit) { MusicController.bind(context) }
    // Foregrounding the app resumes any Wi-Fi-parked downloads (a background FGS start is blocked).
    val hasQueued = downloads.values.any { it.state == DownloadEntry.STATE_QUEUED }
    LaunchedEffect(hasQueued) { if (hasQueued) DownloadService.resume(context) }
    // Deep link from search: open the item-detail dialog on top of the media tab.
    LaunchedEffect(Unit) {
        if (initialItemId != null) {
            runCatching { vm.jellyfinMediaDetail(config, initialItemId) }.getOrNull()?.let { ds.open(it) }
        }
    }
    val tvState = rememberJellyfinLiveTvState()

    /** Drop finished downloads whose item is watched on the server, when the user asked for that. */
    suspend fun sweepWatchedDownloads() {
        if (!vm.downloadsDeleteWatched.value) return
        val done = downloads.values.filter { it.serverId == config.id && it.state == DownloadEntry.STATE_DONE }
        if (done.isEmpty()) return
        val watched = runCatching { vm.jellyfinPlayedIds(config, done.map { it.itemId }) }.getOrDefault(emptySet())
        watched.forEach { DownloadService.delete(context, it) }
    }

    suspend fun loadSessions() { listError = ps.loadSessions(vm, config) }
    suspend fun loadUsers() {
        listError = ps.loadUsers(vm, config)
        runCatching { ad.ensureLibraries(vm, config) }
    }
    suspend fun loadDashboard() { listError = ad.reload(vm, config) }
    suspend fun loadLiveTv() { listError = tvState.reload(vm, config) }
    suspend fun loadMedia() {
        listError = if (bs.stack.isEmpty()) bs.loadHome(vm, config).also { sweepWatchedDownloads() }
        else bs.loadFolder(vm, config, bs.stack.last())
    }
    LaunchedEffect(mode) { ad.section = null; when (mode) { 0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard(); 4 -> loadLiveTv(); else -> {} } }
    // System back from an open dashboard category returns to the tile overview.
    BackHandler(enabled = mode == 2 && ad.section != null) { ad.section = null }
    // Action results (e.g. "restarting") shouldn't linger — clear them after a few seconds,
    // except while a restart is polling for the server to come back.
    LaunchedEffect(actionMsg, ad.restartInProgress) {
        if (actionMsg != null && !ad.restartInProgress) { kotlinx.coroutines.delay(4000); actionMsg = null }
    }
    LaunchedEffect(mode, bs.stack, bs.sort, bs.desc, bs.unwatched) { if (mode == 3) loadMedia() }
    ds.manage?.let { target ->
        val counterpart = org.phioster.sanctumd.ui.arr.ArrCounterpartTarget(
            serviceType = arrServiceTypeFor(target.kind) ?: org.phioster.sanctumd.model.ServiceType.RADARR,
            tmdbId = target.providerIds["Tmdb"],
            tvdbId = target.providerIds["Tvdb"],
            scopeNote = counterpartScopeNote(target.kind),
        )
        org.phioster.sanctumd.ui.arr.ArrCounterpartDialog(vm, counterpart, accent, onDismiss = { ds.manage = null }) { msg ->
            ds.manage = null
            actionMsg = msg
            scope.launch { vm.refreshAll() }
        }
    }

    ds.collection?.let { target ->
        val tmdb = target.providerIds["Tmdb"]?.toIntOrNull()
        if (tmdb != null) {
            org.phioster.sanctumd.ui.arr.ArrCollectionDialog(
                vm, tmdb, accent,
                onDismiss = { ds.collection = null },
            ) { msg ->
                actionMsg = msg
                scope.launch { vm.refreshAll() }
            }
        }
    }

    ds.subtitles?.let { target ->
        JellyfinSubtitlesDialog(
            vm, config, target, accent,
            onDismiss = { ds.subtitles = null },
        ) { msg ->
            ds.subtitles = null
            actionMsg = msg
        }
    }

    ds.identify?.let { target ->
        JellyfinIdentifyDialog(
            vm, config, target, accent,
            onDismiss = { ds.identify = null },
        ) { msg ->
            ds.identify = null
            actionMsg = msg
            // Metadata changed underneath us; re-read the sheet rather than show the old title.
            scope.launch {
                runCatching { vm.jellyfinMediaDetail(config, target.id) }.getOrNull()?.let { ds.replaceTop(it) }
                vm.refreshAll()
            }
        }
    }

    ds.delete?.let { target ->
        JellyfinDeleteDialog(
            vm, config, target, accent,
            onDismiss = { ds.delete = null },
        ) { msg ->
            ds.delete = null
            ds.closeAll() // the item is gone; its sheet must not linger
            actionMsg = msg
            scope.launch { vm.refreshAll() }
        }
    }

    BackHandler(enabled = mode == 3 && (ds.detail != null || bs.stack.isNotEmpty())) {
        if (!ds.back()) bs.stack = bs.stack.dropLast(1)
    }
    fun openMedia(it: JellyMediaItem) {
        if (it.isFolder && !opensAsDetail(it.kind)) bs.stack = bs.stack + it
        else scope.launch { runCatching { vm.jellyfinMediaDetail(config, it.id) }.getOrNull()?.let { d -> ds.open(d) } }
    }
    fun toggleFolder(f: JellyMediaItem) {
        if (f.id in bs.expanded) {
            bs.expanded = bs.expanded - f.id
        } else {
            bs.expanded = bs.expanded + f.id
            if (bs.children[f.id] == null) scope.launch { bs.loadChildren(vm, config, f) }
        }
    }
    // Leaving a folder level closes everything — the state belongs to the level you were on.
    LaunchedEffect(bs.stack) { bs.expanded = emptySet(); bs.children = emptyMap() }

    // ── Watched toggle ────────────────────────────────────────────────────────────────────────────
    // Marking a Series/Season cascades to every episode on the server, so folders confirm first.
    // Non-folders flip straight away; the badge keeps its own optimistic state, we reload behind it.
    suspend fun reloadMedia() { listError = bs.reload(vm, config) }
    fun applyWatched(itemId: String, name: String, want: Boolean) {
        scope.launch {
            runCatching { vm.jellyfinSetWatched(config, itemId, want) }
                .onSuccess {
                    actionMsg = if (want) "$name marked watched" else "$name marked unwatched"
                    if (ds.detail?.id == itemId) {
                        runCatching { vm.jellyfinMediaDetail(config, itemId) }.getOrNull()?.let { ds.replaceTop(it) }
                    }
                    reloadMedia()
                }
                .onFailure { actionMsg = "could not update: ${it.message ?: "failed"}" }
        }
    }
    /** Badge tap: returns whether the change was applied now (false = a confirmation is pending). */
    val setWatched: (JellyMediaItem, Boolean) -> Boolean = { m, want ->
        if (m.isFolder) {
            ds.confirmWatched = Triple(m.id, m.name, want)
            false
        } else {
            applyWatched(m.id, m.name, want)
            true
        }
    }
    // Play a completed download offline: audio via the background music player, video via the overlay.
    fun playDownload(e: DownloadEntry) {
        if (e.filePath.isBlank()) return
        val fileUri = android.net.Uri.fromFile(java.io.File(e.filePath)).toString()
        if (e.mediaType == "Audio") {
            val art = if (e.posterFile.startsWith("/")) android.net.Uri.fromFile(java.io.File(e.posterFile)).toString() else ""
            val track = MusicTrack(e.itemId, e.name, e.subtitle, "", fileUri, art)
            MusicController.play(context, listOf(track), 0, emptyMap())
        } else {
            playRequest = PlayRequest(e.itemId, e.name, localFileUri = fileUri)
        }
    }
    val mediaActions = JellyfinMediaActions(
        open = ::openMedia,
        setWatched = setWatched,
        play = { m -> playRequest = PlayRequest(m.id, m.name) },
        playDownload = ::playDownload,
        toggleFolder = ::toggleFolder,
        manageDownloads = { downloadsManagerOpen = true },
        message = { actionMsg = it },
    )
    fun act(action: suspend () -> String) {
        scope.launch { actionMsg = action(); loadSessions() }
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
                    // Live download indicator — visible from any Jellyfin tab while something downloads.
                    val activeDl = downloads.values.filter {
                        it.serverId == config.id &&
                            (it.state == DownloadEntry.STATE_RUNNING ||
                                it.state == DownloadEntry.STATE_QUEUED)
                    }
                    if (activeDl.isNotEmpty()) {
                        val label = if (activeDl.size == 1) "${(activeDl.first().progress * 100).toInt()}%" else "${activeDl.size}"
                        Row(
                            Modifier.clickable { mode = 3; bs.stack = emptyList() }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Download, contentDescription = "Downloads", tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            if (mode == 3) {
                                DropdownMenuItem(text = { Text("Customize rows", fontFamily = Mono) }, onClick = { barMenu = false; rowPickerOpen = true })
                            }
                            DropdownMenuItem(text = { Text("Open in Jellyfin", fontFamily = Mono) }, onClick = { barMenu = false; openExternal(context, jellyfinAppPackages, "${config.normalizedBaseUrl}web/") })
                            DropdownMenuItem(text = { Text("Scan library", fontFamily = Mono) }, onClick = { barMenu = false; scope.launch { actionMsg = vm.jellyfinScan(config) } })
                            DropdownMenuItem(text = { Text("Restart server", fontFamily = Mono) }, onClick = { barMenu = false; ad.confirmRestart = true })
                            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
        bottomBar = {
            if (musicState.hasMedia) MusicBar(musicState, accent, onToggle = { MusicController.playPause() }, onNext = { MusicController.next() }, onOpen = { nowPlayingOpen = true })
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
                    val jfChips = listOf("Media" to 3, "Now Playing" to 0, "Users" to 1, "Dashboard" to 2, "Live TV" to 4)
                    val jfChipState = rememberLazyListState()
                    LaunchedEffect(mode) { jfChipState.animateScrollToItem(jfChips.indexOfFirst { it.second == mode }.coerceAtLeast(0)) }
                    LazyRow(
                        state = jfChipState,
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(jfChips) { (label, m) ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label, fontFamily = Mono) })
                        }
                    }
                    IconButton(
                        enabled = !refreshing,
                        onClick = {
                            actionMsg = null
                            scope.launch {
                                refreshing = true
                                when (mode) {
                                    0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard(); 4 -> loadLiveTv()
                                    else -> loadMedia()
                                }
                                refreshing = false
                            }
                        },
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = MatrixGreen, strokeWidth = 2.dp)
                        } else {
                            Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                        }
                    }
                }
                actionMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            // Chip order isn't the raw mode order, and swipe must not fight nested nav (Media browse /
            // Dashboard section). Swipe walks the visual order; disabled while inside a sub-navigation.
            val jfOrder = listOf(3, 0, 1, 2, 4)
            SwipeTabs(
                tab = jfOrder.indexOf(mode).coerceAtLeast(0),
                count = jfOrder.size,
                onChange = { mode = jfOrder[it] },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enabled = bs.stack.isEmpty() && ad.section == null,
            ) { jfPage ->
                val pageMode = jfOrder[jfPage]
                // The media tab still renders when offline — downloads are local and must stay reachable.
                if (listError != null && pageMode != 3) {
                    Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (pageMode) {
                            0 -> jellyfinSessionsTab(ps, vm, config, accent, ::act)
                            1 -> jellyfinUsersTab(ps, accent)
                            3 -> if (bs.stack.isEmpty()) {
                                jellyfinMediaHome(bs, config, accent, context, downloads, hiddenSet, mediaStyles, listError, mediaActions)
                            } else {
                                jellyfinFolderLevel(bs, vm, config, accent, context, scope, downloads, mediaActions, musicState.currentMediaId)
                            }
                            2 -> jellyfinDashboardTab(ad, vm, config, accent, scope, { actionMsg = it }) { loadDashboard() }
                            4 -> jellyfinLiveTvTab(
                                tvState, vm, config, accent, scope,
                                onMessage = { actionMsg = it },
                                onReload = { scope.launch { listError = tvState.reload(vm, config) } },
                            )
                        }
                    }
                }
            }
        }
    }

    JellyfinItemDialogs(ds, ps, vm, config, accent, scope, { actionMsg = it }) { id, name, want ->
        applyWatched(id, name, want)
    }

    JellyfinLiveTvDialogs(tvState, vm, config, scope, { actionMsg = it }) { scope.launch { listError = tvState.reload(vm, config) } }

    JellyfinPeopleDialogs(ps, vm, config, ad.libraries, scope, { actionMsg = it }) { loadUsers() }

    JellyfinAdminDialogs(ad, vm, config, accent, scope, { actionMsg = it }) { loadDashboard() }

    JellyfinDetailSheet(
        state = ds,
        vm = vm,
        config = config,
        accent = accent,
        downloads = downloads,
        favorites = bs.favorites,
        sessions = ps.sessions,
        onFavorites = { bs.favorites = it },
        onSessions = { ps.sessions = it },
        onMessage = { actionMsg = it },
        onPlay = { playRequest = it },
        onWatched = { id, name, want -> applyWatched(id, name, want) },
    )

    // Full-screen playback overlay, on top of everything in this screen.
    JellyfinRowDialogs(
        bs, vm, config, accent, hiddenSet, mediaStyles,
        libraryFilterOpen = libraryFilterOpen,
        rowPickerOpen = rowPickerOpen,
        onLibraryFilter = { libraryFilterOpen = it },
        onRowPicker = { rowPickerOpen = it },
        onConfigRow = { configRow = it },
    )

    configRow?.let { rowKey ->
        val title = when (rowKey) { "resume" -> "Continue Watching"; "recent" -> "Recently Added"; else -> "Libraries" }
        MediaRowConfigDialog(
            title = title,
            style = mediaStyles[rowKey] ?: MediaRowStyle(),
            serviceColor = accent,
            posterOptions = rowKey == "resume" || rowKey == "recent",
            onSave = { vm.setMediaRowStyle(rowKey, it) },
            onDismiss = { configRow = null },
        )
    }

    if (downloadsManagerOpen) {
        DownloadsManager(
            entries = downloads.values.filter { it.serverId == config.id }.sortedByDescending { it.addedAt },
            accent = accent,
            wifiOnly = wifiOnly,
            onWifiOnly = { vm.setDownloadsWifiOnly(it) },
            deleteWatched = deleteWatched,
            onDeleteWatched = { vm.setDownloadsDeleteWatched(it); if (it) scope.launch { sweepWatchedDownloads() } },
            onPlay = { e -> downloadsManagerOpen = false; playDownload(e) },
            onDelete = { id -> DownloadService.delete(context, id) },
            onClearCompleted = { DownloadService.clearCompleted(context) },
            onClearAll = { DownloadService.clearAll(context); downloadsManagerOpen = false },
            onClose = { downloadsManagerOpen = false },
        )
    }

    if (nowPlayingOpen) {
        NowPlayingScreen(state = musicState, accent = accent, onClose = { nowPlayingOpen = false })
    }

    playRequest?.let { pr ->
        PlayerScreen(
            vm = vm,
            config = config,
            itemId = pr.itemId,
            title = pr.title,
            onClose = { playRequest = null },
            localFileUri = pr.localFileUri,
        )
    }
}

/** A card in the offline DOWNLOADS row: cached poster, progress/state, tap to play (when done). */
