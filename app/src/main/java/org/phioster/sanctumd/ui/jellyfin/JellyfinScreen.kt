package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var sessions by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellySession>?>(null) }
    var users by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyUser>?>(null) }
    var dashInfo by remember { mutableStateOf<org.phioster.sanctumd.model.JellySystemInfo?>(null) }
    var tasks by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyTask>?>(null) }
    var activity by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyActivity>?>(null) }
    var devices by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyDevice>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf(false) }
    var messageFor by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }
    var confirmRestart by remember { mutableStateOf(false) }
    // True while a restart is being confirmed (server polled until back) — keeps its status message from auto-clearing.
    var restartInProgress by remember { mutableStateOf(false) }
    // Dashboard sub-section opened from the tile overview (null = show the tiles).
    var dashSection by remember { mutableStateOf<String?>(null) }
    var libraries by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyLibrary>?>(null) }
    var editUser by remember { mutableStateOf<org.phioster.sanctumd.model.JellyUser?>(null) }
    var showCreateUser by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    val ds = rememberJellyfinDetailState()
    val bs = rememberJellyfinBrowseState()
    var playRequest by remember { mutableStateOf<org.phioster.sanctumd.ui.player.PlayRequest?>(null) }
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
    val musicState by org.phioster.sanctumd.ui.player.MusicController.state.collectAsState()
    var nowPlayingOpen by remember { mutableStateOf(false) }
    // Attach to any running music session so the now-playing bar appears immediately.
    LaunchedEffect(Unit) { org.phioster.sanctumd.ui.player.MusicController.bind(context) }
    // Foregrounding the app resumes any Wi-Fi-parked downloads (a background FGS start is blocked).
    val hasQueued = downloads.values.any { it.state == org.phioster.sanctumd.model.DownloadEntry.STATE_QUEUED }
    LaunchedEffect(hasQueued) { if (hasQueued) org.phioster.sanctumd.service.DownloadService.resume(context) }
    // Deep link from search: open the item-detail dialog on top of the media tab.
    LaunchedEffect(Unit) {
        if (initialItemId != null) {
            runCatching { vm.jellyfinMediaDetail(config, initialItemId) }.getOrNull()?.let { ds.open(it) }
        }
    }
    var logFiles by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyLogFile>?>(null) }
    var logView by remember { mutableStateOf<String?>(null) } // log file name being viewed
    var logText by remember { mutableStateOf<String?>(null) } // its content (null = loading)
    var plugins by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyPlugin>?>(null) }
    var editLibrary by remember { mutableStateOf<org.phioster.sanctumd.model.JellyLibrary?>(null) }
    var showAddLibrary by remember { mutableStateOf(false) }
    var pluginDetail by remember { mutableStateOf<org.phioster.sanctumd.model.JellyPlugin?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyPackage>?>(null) }
    val tvState = rememberJellyfinLiveTvState()


    /** Drop finished downloads whose item is watched on the server, when the user asked for that. */
    suspend fun sweepWatchedDownloads() {
        if (!vm.downloadsDeleteWatched.value) return
        val done = downloads.values.filter { it.serverId == config.id && it.state == org.phioster.sanctumd.model.DownloadEntry.STATE_DONE }
        if (done.isEmpty()) return
        val watched = runCatching { vm.jellyfinPlayedIds(config, done.map { it.itemId }) }.getOrDefault(emptySet())
        watched.forEach { org.phioster.sanctumd.service.DownloadService.delete(context, it) }
    }

    suspend fun loadSessions() {
        listError = null
        try { sessions = vm.jellyfinSessionList(config) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    suspend fun loadUsers() {
        listError = null
        try {
            users = vm.jellyfinUserList(config)
            if (libraries == null) libraries = vm.jellyfinLibraryList(config)
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    suspend fun loadDashboard() {
        listError = null
        try {
            dashInfo = vm.jellyfinInfo(config)
            tasks = vm.jellyfinTaskList(config)
            activity = vm.jellyfinActivityLog(config)
            devices = runCatching { vm.jellyfinDeviceList(config) }.getOrDefault(emptyList())
            libraries = runCatching { vm.jellyfinLibraryList(config) }.getOrDefault(emptyList())
            plugins = runCatching { vm.jellyfinPluginList(config) }.getOrDefault(emptyList())
            logFiles = runCatching { vm.jellyfinLogList(config) }.getOrDefault(emptyList())
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    suspend fun loadLiveTv() { listError = tvState.reload(vm, config) }
    suspend fun loadMedia() {
        listError = if (bs.stack.isEmpty()) bs.loadHome(vm, config).also { sweepWatchedDownloads() }
        else bs.loadFolder(vm, config, bs.stack.last())
    }
    LaunchedEffect(mode) { dashSection = null; when (mode) { 0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard(); 4 -> loadLiveTv(); else -> {} } }
    // System back from an open dashboard category returns to the tile overview.
    BackHandler(enabled = mode == 2 && dashSection != null) { dashSection = null }
    // Action results (e.g. "restarting") shouldn't linger — clear them after a few seconds,
    // except while a restart is polling for the server to come back.
    LaunchedEffect(actionMsg, restartInProgress) {
        if (actionMsg != null && !restartInProgress) { kotlinx.coroutines.delay(4000); actionMsg = null }
    }
    LaunchedEffect(mode, bs.stack, bs.sort, bs.desc, bs.unwatched) { if (mode == 3) loadMedia() }
    ds.manage?.let { target ->
        JellyfinArrBridgeDialog(vm, target, accent, onDismiss = { ds.manage = null }) { msg ->
            ds.manage = null
            actionMsg = msg
            scope.launch { vm.refreshAll() }
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
    fun openMedia(it: org.phioster.sanctumd.model.JellyMediaItem) {
        if (it.isFolder && !opensAsDetail(it.kind)) bs.stack = bs.stack + it
        else scope.launch { runCatching { vm.jellyfinMediaDetail(config, it.id) }.getOrNull()?.let { d -> ds.open(d) } }
    }
    fun toggleFolder(f: org.phioster.sanctumd.model.JellyMediaItem) {
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
    val setWatched: (org.phioster.sanctumd.model.JellyMediaItem, Boolean) -> Boolean = { m, want ->
        if (m.isFolder) {
            ds.confirmWatched = Triple(m.id, m.name, want)
            false
        } else {
            applyWatched(m.id, m.name, want)
            true
        }
    }
    // Play a completed download offline: audio via the background music player, video via the overlay.
    fun playDownload(e: org.phioster.sanctumd.model.DownloadEntry) {
        if (e.filePath.isBlank()) return
        val fileUri = android.net.Uri.fromFile(java.io.File(e.filePath)).toString()
        if (e.mediaType == "Audio") {
            val art = if (e.posterFile.startsWith("/")) android.net.Uri.fromFile(java.io.File(e.posterFile)).toString() else ""
            val track = org.phioster.sanctumd.net.MusicTrack(e.itemId, e.name, e.subtitle, "", fileUri, art)
            org.phioster.sanctumd.ui.player.MusicController.play(context, listOf(track), 0, emptyMap())
        } else {
            playRequest = org.phioster.sanctumd.ui.player.PlayRequest(e.itemId, e.name, localFileUri = fileUri)
        }
    }
    val mediaActions = JellyfinMediaActions(
        open = ::openMedia,
        setWatched = setWatched,
        play = { m -> playRequest = org.phioster.sanctumd.ui.player.PlayRequest(m.id, m.name) },
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
                            (it.state == org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING ||
                                it.state == org.phioster.sanctumd.model.DownloadEntry.STATE_QUEUED)
                    }
                    if (activeDl.isNotEmpty()) {
                        val label = if (activeDl.size == 1) "⬇ ${(activeDl.first().progress * 100).toInt()}%" else "⬇ ${activeDl.size}"
                        Text(
                            label, fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { mode = 3; bs.stack = emptyList() }
                                .padding(horizontal = 8.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            if (mode == 3) {
                                DropdownMenuItem(text = { Text("Customize rows", fontFamily = Mono) }, onClick = { barMenu = false; rowPickerOpen = true })
                            }
                            DropdownMenuItem(text = { Text("Open in Jellyfin", fontFamily = Mono) }, onClick = { barMenu = false; openExternal(context, jellyfinAppPackages, "${config.normalizedBaseUrl}web/") })
                            DropdownMenuItem(text = { Text("Scan library", fontFamily = Mono) }, onClick = { barMenu = false; scope.launch { actionMsg = vm.jellyfinScan(config) } })
                            DropdownMenuItem(text = { Text("Restart server", fontFamily = Mono) }, onClick = { barMenu = false; confirmRestart = true })
                            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
        bottomBar = {
            if (musicState.hasMedia) MusicBar(musicState, accent, onToggle = { org.phioster.sanctumd.ui.player.MusicController.playPause() }, onNext = { org.phioster.sanctumd.ui.player.MusicController.next() }, onOpen = { nowPlayingOpen = true })
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
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
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
                enabled = bs.stack.isEmpty() && dashSection == null,
            ) { jfPage ->
                val pageMode = jfOrder[jfPage]
                // The media tab still renders when offline — downloads are local and must stay reachable.
                if (listError != null && pageMode != 3) {
                    Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (pageMode) {
                            0 -> {
                                val s = sessions
                                when {
                                    s == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    s.isEmpty() -> item { Text("no active sessions", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(s) { sess ->
                                        JellySessionRow(
                                            item = sess,
                                            accent = accent,
                                            onPlayPause = { act { vm.jellyfinControl(config, sess.id, if (sess.paused) "Unpause" else "Pause") } },
                                            onStop = { act { vm.jellyfinControl(config, sess.id, "Stop") } },
                                            onMessage = { messageFor = sess.id; messageText = "" },
                                        )
                                    }
                                }
                            }
                            1 -> {
                                val u = users
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "+ new user",
                                        fontFamily = Mono, color = accent, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { newUserName = ""; newUserPass = ""; showCreateUser = true }.padding(vertical = 6.dp),
                                    )
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                }
                                when {
                                    u == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    u.isEmpty() -> item { Text("no users", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(u) { usr -> JellyUserRow(usr, accent) { editUser = usr } }
                                }
                            }
                            3 -> if (bs.stack.isEmpty()) {
                                jellyfinMediaHome(bs, config, accent, context, downloads, hiddenSet, mediaStyles, listError, mediaActions)
                            } else {
                                jellyfinFolderLevel(bs, vm, config, accent, context, scope, downloads, mediaActions)
                            }
                            2 -> {
                                if (dashSection == null) {
                                    // ── Overview: server card + clickable category tiles ──
                                    item {
                                        val si = dashInfo
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MatrixGreen.copy(alpha = 0.06f))
                                                .border(1.dp, MatrixGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.Dns, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(si?.serverName ?: "…", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text("v${si?.version ?: "…"}${if (!si?.os.isNullOrBlank()) " · ${si!!.os}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        val cats = listOf(
                                            DashCat("tasks", "Tasks", tasks?.size, Icons.Filled.Schedule),
                                            DashCat("activity", "Activity", activity?.size, Icons.Filled.History),
                                            DashCat("libraries", "Libraries", libraries?.size, Icons.Filled.VideoLibrary),
                                            DashCat("plugins", "Plugins", plugins?.size, Icons.Filled.Extension),
                                            DashCat("logs", "Logs", logFiles?.size, Icons.Filled.Description),
                                            DashCat("devices", "Devices", devices?.size, Icons.Filled.Devices),
                                        )
                                        cats.chunked(2).forEach { rowCats ->
                                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                rowCats.forEach { c ->
                                                    DashTile(c.label, c.count, c.icon, accent, Modifier.weight(1f)) { dashSection = c.key }
                                                }
                                                if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    // ── One category, opened from a tile ──
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { dashSection = null }.padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accent, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(dashSection!!.uppercase(), fontFamily = Mono, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                    }
                                    when (dashSection) {
                                        "tasks" -> {
                                            val tk = tasks
                                            when {
                                                tk == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(tk) { t -> JellyTaskRow(t, accent) { scope.launch { actionMsg = vm.jellyfinRunTaskById(config, t.id); loadDashboard() } } }
                                            }
                                        }
                                        "activity" -> {
                                            val ac = activity
                                            when {
                                                ac == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                ac.isEmpty() -> item { Text("no activity", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(ac) { e -> JellyActivityRow(e, accent) }
                                            }
                                        }
                                        "libraries" -> {
                                            item {
                                                Text(
                                                    "+ add library",
                                                    fontFamily = Mono, color = accent, fontSize = 13.sp,
                                                    modifier = Modifier.fillMaxWidth().clickable { showAddLibrary = true }.padding(vertical = 8.dp),
                                                )
                                                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                            }
                                            val lb = libraries
                                            when {
                                                lb == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                lb.isEmpty() -> item { Text("no libraries", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(lb) { l -> JellyLibraryRow(l, accent) { editLibrary = l } }
                                            }
                                        }
                                        "plugins" -> {
                                            item {
                                                Text(
                                                    "+ plugin catalog",
                                                    fontFamily = Mono, color = accent, fontSize = 13.sp,
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        catalog = null; showCatalog = true
                                                        scope.launch { catalog = runCatching { vm.jellyfinCatalog(config) }.getOrDefault(emptyList()) }
                                                    }.padding(vertical = 8.dp),
                                                )
                                                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                            }
                                            val pl = plugins
                                            when {
                                                pl == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                pl.isEmpty() -> item { Text("no plugins", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(pl) { p -> JellyPluginRow(p, accent) { pluginDetail = p } }
                                            }
                                        }
                                        "logs" -> {
                                            val lg = logFiles
                                            when {
                                                lg == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                lg.isEmpty() -> item { Text("no logs", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(lg) { f ->
                                                    JellyLogRow(f, accent) {
                                                        logView = f.name; logText = null
                                                        scope.launch { logText = vm.jellyfinLogText(config, f.name) }
                                                    }
                                                }
                                            }
                                        }
                                        "devices" -> {
                                            val dv = devices
                                            when {
                                                dv == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                dv.isEmpty() -> item { Text("no devices", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                                else -> items(dv) { d -> JellyDeviceRow(d, accent) }
                                            }
                                        }
                                    }
                                }
                            }
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

    messageFor?.let { sid ->
        AlertDialog(
            onDismissRequest = { messageFor = null },
            containerColor = Surface,
            title = { Text("Send message", fontFamily = Mono, color = MatrixGreen) },
            text = { Field("Message", messageText) { messageText = it } },
            confirmButton = {
                TextButton(enabled = messageText.isNotBlank(), onClick = {
                    val txt = messageText; messageFor = null
                    scope.launch { actionMsg = vm.jellyfinMessage(config, sid, txt) }
                }) { Text("Send", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { messageFor = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    ds.downloadQuality?.let { d ->
        // Original file vs. a transcoded, smaller copy. The server re-encodes on the fly for the
        // capped options, so the size shown on the card is an estimate until it finishes.
        AlertDialog(
            onDismissRequest = { ds.downloadQuality = null },
            containerColor = Surface,
            title = { Text("download quality", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    listOf(
                        0 to "original file",
                        8_000_000 to "1080p  ·  smaller",
                        4_000_000 to "720p  ·  much smaller",
                        1_500_000 to "480p  ·  smallest",
                    ).forEach { (bitrate, label) ->
                        Text(
                            label,
                            fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                ds.downloadQuality = null
                                org.phioster.sanctumd.service.DownloadService.enqueue(
                                    context, config.id, d.id, d.name, d.subtitle, d.posterUrl, 0L, "Video", bitrate,
                                )
                                actionMsg = "download queued"
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ds.downloadQuality = null }) { Text("cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    ds.cast?.let { d ->
        // Hand the item to another Jellyfin client. Only sessions that accept remote control and
        // aren't this phone are useful here.
        val targets = sessions.orEmpty().filter { it.canControl }
        AlertDialog(
            onDismissRequest = { ds.cast = null },
            containerColor = Surface,
            title = { Text("play on…", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    if (sessions == null) {
                        Text("loading devices…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 13.sp)
                    } else if (targets.isEmpty()) {
                        Text(
                            "No other device is available. A client has to be open and allow remote control.",
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 13.sp,
                        )
                    } else {
                        targets.forEach { t ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    ds.cast = null
                                    scope.launch { actionMsg = vm.jellyfinPlayOn(config, t.id, d.id) }
                                }.padding(vertical = 8.dp),
                            ) {
                                Text(t.device.ifBlank { t.client }, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                                Text(
                                    listOfNotNull(t.user.takeIf { it.isNotBlank() }, t.client.takeIf { it.isNotBlank() }).joinToString(" · "),
                                    fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ds.cast = null }) { Text("close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    ds.confirmWatched?.let { (wid, wname, want) ->
        AlertDialog(
            onDismissRequest = { ds.confirmWatched = null },
            containerColor = Surface,
            title = { Text(if (want) "Mark everything watched?" else "Mark everything unwatched?", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Text(
                    "This applies to every episode in \"$wname\".",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { ds.confirmWatched = null; applyWatched(wid, wname, want) }) {
                    Text(if (want) "mark watched" else "mark unwatched", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { ds.confirmWatched = null }) { Text("cancel", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f)) } },
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            containerColor = Surface,
            title = { Text("Restart server?", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("This restarts the Jellyfin server for everyone.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    scope.launch {
                        restartInProgress = true
                        actionMsg = "restarting…"
                        val r = vm.jellyfinRestartServer(config)
                        if (r.startsWith("error")) {
                            restartInProgress = false
                            actionMsg = r
                            return@launch
                        }
                        // Give the server a moment to actually go down, then poll until it answers again.
                        kotlinx.coroutines.delay(3000)
                        actionMsg = "restarting… waiting for server to come back"
                        var back = false
                        val deadline = System.currentTimeMillis() + 120_000
                        while (System.currentTimeMillis() < deadline) {
                            if (runCatching { vm.jellyfinInfo(config) }.getOrNull() != null) { back = true; break }
                            kotlinx.coroutines.delay(3000)
                        }
                        restartInProgress = false
                        if (back) {
                            actionMsg = "✓ server back online"
                            loadDashboard()
                        } else {
                            actionMsg = "restart sent — server hasn't responded yet"
                        }
                    }
                }) {
                    Text("Restart", fontFamily = Mono, color = ErrRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showCreateUser) {
        AlertDialog(
            onDismissRequest = { showCreateUser = false },
            containerColor = Surface,
            title = { Text("New user", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Username", newUserName) { newUserName = it }
                    Field("Password (optional)", newUserPass, isPassword = true) { newUserPass = it }
                }
            },
            confirmButton = {
                TextButton(enabled = newUserName.isNotBlank(), onClick = {
                    val n = newUserName; val p = newUserPass; showCreateUser = false
                    scope.launch { actionMsg = vm.jellyfinAddUser(config, n, p); loadUsers() }
                }) { Text("Create", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showCreateUser = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    editUser?.let { usr ->
        JellyUserDialog(
            user = usr,
            libraries = libraries,
            onDismiss = { editUser = null },
            onSave = { admin, disabled, allowDownloads, enableAll, folders ->
                editUser = null
                scope.launch {
                    actionMsg = vm.jellyfinUpdatePolicy(config, usr.id, admin, disabled, allowDownloads, enableAll, folders)
                    loadUsers()
                }
            },
            onResetPassword = { newPw ->
                scope.launch { actionMsg = vm.jellyfinResetPassword(config, usr.id, newPw) }
            },
            onDelete = {
                editUser = null
                scope.launch { actionMsg = vm.jellyfinRemoveUser(config, usr.id); loadUsers() }
            },
        )
    }

    if (showAddLibrary) {
        JellyAddLibraryDialog(
            accent = accent,
            onDismiss = { showAddLibrary = false },
            onCreate = { name, type, path ->
                showAddLibrary = false
                scope.launch { actionMsg = vm.jellyfinCreateLibrary(config, name, type, path); loadDashboard() }
            },
        )
    }

    editLibrary?.let { lib ->
        JellyLibraryDialog(
            library = lib,
            accent = accent,
            onDismiss = { editLibrary = null },
            onRename = { newName ->
                editLibrary = null
                scope.launch { actionMsg = vm.jellyfinRenameLibraryTo(config, lib.name, newName); loadDashboard() }
            },
            onAddPath = { path ->
                editLibrary = null
                scope.launch { actionMsg = vm.jellyfinLibraryAddPath(config, lib.name, path); loadDashboard() }
            },
            onRemovePath = { path ->
                editLibrary = null
                scope.launch { actionMsg = vm.jellyfinLibraryRemovePath(config, lib.name, path); loadDashboard() }
            },
            onDelete = {
                editLibrary = null
                scope.launch { actionMsg = vm.jellyfinRemoveLibrary(config, lib.name); loadDashboard() }
            },
        )
    }

    pluginDetail?.let { p ->
        JellyPluginDialog(
            plugin = p,
            accent = accent,
            onDismiss = { pluginDetail = null },
            onToggle = {
                pluginDetail = null
                scope.launch { actionMsg = vm.jellyfinPluginEnable(config, p.id, p.version, p.status.equals("Disabled", true)); loadDashboard() }
            },
            onUninstall = {
                pluginDetail = null
                scope.launch { actionMsg = vm.jellyfinPluginUninstall(config, p.id, p.version); loadDashboard() }
            },
        )
    }

    if (showCatalog) {
        JellyCatalogDialog(
            catalog = catalog,
            accent = accent,
            onDismiss = { showCatalog = false },
            onInstall = { pkg ->
                showCatalog = false
                scope.launch { actionMsg = vm.jellyfinCatalogInstall(config, pkg.name, pkg.guid); loadDashboard() }
            },
        )
    }

    JellyfinLiveTvDialogs(tvState, vm, config, scope, { actionMsg = it }) { scope.launch { listError = tvState.reload(vm, config) } }

    logView?.let { name ->
        AlertDialog(
            onDismissRequest = { logView = null },
            containerColor = Surface,
            title = { Text(name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        logText ?: "loading…",
                        fontFamily = Mono,
                        color = if (logText?.startsWith("error") == true) ErrRed else MatrixGreen.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { logView = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    JellyfinDetailSheet(
        state = ds,
        vm = vm,
        config = config,
        accent = accent,
        downloads = downloads,
        favorites = favorites,
        sessions = sessions,
        onFavorites = { favorites = it },
        onSessions = { sessions = it },
        onMessage = { actionMsg = it },
        onPlay = { playRequest = it },
        onWatched = { id, name, want -> applyWatched(id, name, want) },
    )

    // Full-screen playback overlay, on top of everything in this screen.
    if (libraryFilterOpen) {
        AlertDialog(
            onDismissRequest = { libraryFilterOpen = false },
            containerColor = Surface,
            title = { Text("show libraries", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    bs.views.orEmpty().forEach { view ->
                        val shown = view.id !in hiddenSet
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val next = if (shown) hiddenSet + view.id else hiddenSet - view.id
                                vm.setHiddenLibraries(config.id, next.toList())
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (shown) "[x]" else "[ ]", fontFamily = Mono, color = if (shown) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 14.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(view.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { libraryFilterOpen = false }) { Text("done", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (rowPickerOpen) {
        AlertDialog(
            onDismissRequest = { rowPickerOpen = false },
            containerColor = Surface,
            title = { Text("customize rows", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    listOf("resume" to "Continue Watching", "recent" to "Recently Added", "libraries" to "Libraries").forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { rowPickerOpen = false; configRow = key }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            if ((mediaStyles[key] ?: org.phioster.sanctumd.model.MediaRowStyle()).hidden) {
                                Text("hidden", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                    Row(
                        Modifier.fillMaxWidth().clickable { rowPickerOpen = false; libraryFilterOpen = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Show / hide libraries", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rowPickerOpen = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    configRow?.let { rowKey ->
        val title = when (rowKey) { "resume" -> "Continue Watching"; "recent" -> "Recently Added"; else -> "Libraries" }
        MediaRowConfigDialog(
            title = title,
            style = mediaStyles[rowKey] ?: org.phioster.sanctumd.model.MediaRowStyle(),
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
            onDelete = { id -> org.phioster.sanctumd.service.DownloadService.delete(context, id) },
            onClearCompleted = { org.phioster.sanctumd.service.DownloadService.clearCompleted(context) },
            onClearAll = { org.phioster.sanctumd.service.DownloadService.clearAll(context); downloadsManagerOpen = false },
            onClose = { downloadsManagerOpen = false },
        )
    }

    if (nowPlayingOpen) {
        NowPlayingScreen(state = musicState, accent = accent, onClose = { nowPlayingOpen = false })
    }

    playRequest?.let { pr ->
        org.phioster.sanctumd.ui.player.PlayerScreen(
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
