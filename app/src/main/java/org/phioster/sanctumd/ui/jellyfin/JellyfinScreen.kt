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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import org.phioster.sanctumd.ui.common.*
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
    var mode by remember { mutableStateOf(if (initialItemId != null) 3 else 0) } // 0=Now Playing, 1=Users, 2=Dashboard, 3=Media, 4=Live TV
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
    var mediaViews by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyMediaItem>?>(null) }
    var mediaContents by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyMediaItem>?>(null) }
    var resumeItems by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyMediaItem>?>(null) }
    var latestItems by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyMediaItem>?>(null) }
    var browseStack by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyMediaItem>>(emptyList()) }
    var mediaDetail by remember { mutableStateOf<org.phioster.sanctumd.model.JellyMediaDetail?>(null) }
    var playRequest by remember { mutableStateOf<org.phioster.sanctumd.ui.player.PlayRequest?>(null) }
    val downloads by vm.downloads.collectAsState(initial = emptyMap())
    // Deep link from search: open the item-detail dialog on top of the media tab.
    LaunchedEffect(Unit) {
        if (initialItemId != null) {
            mediaDetail = runCatching { vm.jellyfinMediaDetail(config, initialItemId) }.getOrNull()
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
    var liveTv by remember { mutableStateOf<org.phioster.sanctumd.model.JellyLiveTv?>(null) }
    var channels by remember { mutableStateOf<List<org.phioster.sanctumd.model.JellyChannel>?>(null) }
    var showAddTuner by remember { mutableStateOf(false) }
    var showAddProvider by remember { mutableStateOf(false) }
    var confirmDeleteTuner by remember { mutableStateOf<String?>(null) }
    var confirmDeleteProvider by remember { mutableStateOf<String?>(null) }

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
    suspend fun loadLiveTv() {
        listError = null
        confirmDeleteTuner = null; confirmDeleteProvider = null
        try {
            liveTv = vm.jellyfinLiveTvStatus(config)
            channels = runCatching { vm.jellyfinChannelList(config) }.getOrDefault(emptyList())
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    suspend fun loadMediaHome() {
        listError = null
        try {
            mediaViews = vm.jellyfinViews(config)
            resumeItems = vm.jellyfinContinue(config)
            latestItems = vm.jellyfinRecent(config, null)
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    suspend fun loadMediaFolder(parent: org.phioster.sanctumd.model.JellyMediaItem) {
        listError = null
        mediaContents = null
        try {
            mediaContents = vm.jellyfinItemList(config, parent.id, if (parent.kind == "Season") parent.number else null)
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    LaunchedEffect(mode) { dashSection = null; when (mode) { 0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard(); 4 -> loadLiveTv(); else -> {} } }
    // System back from an open dashboard category returns to the tile overview.
    BackHandler(enabled = mode == 2 && dashSection != null) { dashSection = null }
    // Action results (e.g. "restarting") shouldn't linger — clear them after a few seconds,
    // except while a restart is polling for the server to come back.
    LaunchedEffect(actionMsg, restartInProgress) {
        if (actionMsg != null && !restartInProgress) { kotlinx.coroutines.delay(4000); actionMsg = null }
    }
    LaunchedEffect(mode, browseStack) {
        if (mode == 3) { if (browseStack.isEmpty()) loadMediaHome() else loadMediaFolder(browseStack.last()) }
    }
    BackHandler(enabled = mode == 3 && (mediaDetail != null || browseStack.isNotEmpty())) {
        if (mediaDetail != null) mediaDetail = null else browseStack = browseStack.dropLast(1)
    }
    fun openMedia(it: org.phioster.sanctumd.model.JellyMediaItem) {
        if (it.isFolder) browseStack = browseStack + it
        else scope.launch { mediaDetail = runCatching { vm.jellyfinMediaDetail(config, it.id) }.getOrElse { null } }
    }
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
                    Box {
                        IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
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
                    val jfChips = listOf("Now Playing" to 0, "Media" to 3, "Users" to 1, "Dashboard" to 2, "Live TV" to 4)
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
                                    else -> if (browseStack.isEmpty()) loadMediaHome() else loadMediaFolder(browseStack.last())
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
            val jfOrder = listOf(0, 3, 1, 2, 4)
            SwipeTabs(
                tab = jfOrder.indexOf(mode).coerceAtLeast(0),
                count = jfOrder.size,
                onChange = { mode = jfOrder[it] },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enabled = browseStack.isEmpty() && dashSection == null,
            ) { jfPage ->
                val pageMode = jfOrder[jfPage]
                if (listError != null) {
                    Text("error: $listError", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
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
                            3 -> {
                                if (browseStack.isEmpty()) {
                                    val myDownloads = downloads.values
                                        .filter { it.serverId == config.id }
                                        .sortedByDescending { it.addedAt }
                                    if (myDownloads.isNotEmpty()) {
                                        item {
                                            Spacer(Modifier.height(8.dp))
                                            Text("DOWNLOADS  ·  offline", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                            Spacer(Modifier.height(6.dp))
                                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                myDownloads.forEach { e ->
                                                    DownloadCard(
                                                        entry = e, accent = accent,
                                                        onPlay = {
                                                            if (e.done && e.filePath.isNotBlank()) {
                                                                playRequest = org.phioster.sanctumd.ui.player.PlayRequest(
                                                                    e.itemId, e.name,
                                                                    localFileUri = android.net.Uri.fromFile(java.io.File(e.filePath)).toString(),
                                                                )
                                                            }
                                                        },
                                                        onDelete = { org.phioster.sanctumd.service.DownloadService.delete(context, e.itemId) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    val res = resumeItems
                                    if (!res.isNullOrEmpty()) {
                                        item {
                                            Spacer(Modifier.height(8.dp))
                                            Text("CONTINUE WATCHING", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                            Spacer(Modifier.height(6.dp))
                                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                res.forEach { m -> JellyPosterCard(m, config, accent) { openMedia(m) } }
                                            }
                                        }
                                    }
                                    val lat = latestItems
                                    if (!lat.isNullOrEmpty()) {
                                        item {
                                            Spacer(Modifier.height(12.dp))
                                            Text("RECENTLY ADDED", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                            Spacer(Modifier.height(6.dp))
                                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                                lat.forEach { m -> JellyPosterCard(m, config, accent) { openMedia(m) } }
                                            }
                                        }
                                    }
                                    item {
                                        Spacer(Modifier.height(12.dp))
                                        Text("LIBRARIES", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                    }
                                    val v = mediaViews
                                    when {
                                        v == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                        v.isEmpty() -> item { Text("no libraries", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                        else -> items(v) { m -> JellyMediaRow(m, config, accent) { openMedia(m) } }
                                    }
                                } else {
                                    val here = browseStack.last()
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text("‹ back", fontFamily = Mono, color = accent, fontSize = 13.sp, modifier = Modifier.clickable { browseStack = browseStack.dropLast(1) })
                                            Spacer(Modifier.weight(1f))
                                            Text("⟳ scan", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.clickable { scope.launch { actionMsg = vm.jellyfinScanLibrary(config, here.id) } })
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(here.name, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                                    }
                                    val m = mediaContents
                                    when {
                                        m == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                                        m.isEmpty() -> item { Text("empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                                        else -> items(m) { it2 -> JellyMediaRow(it2, config, accent) { openMedia(it2) } }
                                    }
                                }
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
                            4 -> {
                                val tv = liveTv
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    when {
                                        tv == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f))
                                        !tv.enabled -> Text("Live TV is not enabled on this server", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                                        else -> tv.services.forEach { s ->
                                            Text(s, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("TUNERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                    Text(
                                        "+ add tuner",
                                        fontFamily = Mono, color = accent, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { showAddTuner = true }.padding(vertical = 6.dp),
                                    )
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                }
                                val tuners = tv?.tuners
                                when {
                                    tuners == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    tuners.isEmpty() -> item { Text("no tuners", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    else -> items(tuners) { t ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(t.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        Text(t.type, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                                                    }
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(t.url, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Text(
                                                    if (confirmDeleteTuner == t.id) "remove?" else "✕",
                                                    fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                                                    modifier = Modifier.clickable {
                                                        if (confirmDeleteTuner == t.id) scope.launch { actionMsg = vm.jellyfinTunerDelete(config, t.id); loadLiveTv() }
                                                        else confirmDeleteTuner = t.id
                                                    }.padding(start = 12.dp),
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                                        }
                                    }
                                }
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    Text("GUIDE PROVIDERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                    Text(
                                        "+ add xmltv guide",
                                        fontFamily = Mono, color = accent, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { showAddProvider = true }.padding(vertical = 6.dp),
                                    )
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                                }
                                val providers = tv?.providers
                                when {
                                    providers == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    providers.isEmpty() -> item { Text("no guide providers", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    else -> items(providers) { p ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(p.type, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                                                    if (p.path.isNotBlank()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(p.path, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Text(
                                                    if (confirmDeleteProvider == p.id) "remove?" else "✕",
                                                    fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                                                    modifier = Modifier.clickable {
                                                        if (confirmDeleteProvider == p.id) scope.launch { actionMsg = vm.jellyfinProviderDelete(config, p.id); loadLiveTv() }
                                                        else confirmDeleteProvider = p.id
                                                    }.padding(start = 12.dp),
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                                        }
                                    }
                                }
                                val ch = channels
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    Text("CHANNELS${if (!ch.isNullOrEmpty()) " (${ch.size})" else ""}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                                // Guide data expires daily — if no channel knows its current
                                // program, offer to run the server's "Refresh Guide" task.
                                if (!ch.isNullOrEmpty() && ch.none { it.nowPlaying.isNotBlank() }) {
                                    item {
                                        Text(
                                            "no program data — guide may be stale · ⟳ refresh guide",
                                            fontFamily = Mono, color = accent, fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                scope.launch {
                                                    val task = runCatching { vm.jellyfinTaskList(config) }.getOrNull()
                                                        ?.firstOrNull { it.name.contains("Guide", ignoreCase = true) }
                                                    actionMsg = if (task == null) "guide task not found" else vm.jellyfinRunTaskById(config, task.id)
                                                }
                                            }.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                                when {
                                    ch == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    ch.isEmpty() -> item { Text("no channels", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    else -> items(ch) { c -> JellyChannelRow(c, accent) }
                                }
                            }
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

    if (showAddTuner) {
        var tunerType by remember { mutableStateOf("m3u") }
        var tunerUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTuner = false },
            containerColor = Surface,
            title = { Text("New tuner", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text("TYPE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row {
                        listOf("m3u" to "M3U playlist", "hdhomerun" to "HDHomeRun").forEach { (key, label) ->
                            FilterChip(
                                selected = tunerType == key,
                                onClick = { tunerType = key },
                                label = { Text(label, fontFamily = Mono, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    Field(if (tunerType == "m3u") "Playlist URL or file path" else "Device address", tunerUrl) { tunerUrl = it }
                }
            },
            confirmButton = {
                TextButton(enabled = tunerUrl.isNotBlank(), onClick = {
                    val ty = tunerType; val u = tunerUrl.trim(); showAddTuner = false
                    scope.launch { actionMsg = vm.jellyfinTunerAdd(config, ty, u); loadLiveTv() }
                }) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showAddTuner = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (showAddProvider) {
        var providerPath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddProvider = false },
            containerColor = Surface,
            title = { Text("New XMLTV guide", fontFamily = Mono, color = MatrixGreen) },
            text = { Field("XMLTV URL or file path", providerPath) { providerPath = it } },
            confirmButton = {
                TextButton(enabled = providerPath.isNotBlank(), onClick = {
                    val p = providerPath.trim(); showAddProvider = false
                    scope.launch { actionMsg = vm.jellyfinProviderAdd(config, p); loadLiveTv() }
                }) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { showAddProvider = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

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

    mediaDetail?.let { d ->
        AlertDialog(
            onDismissRequest = { mediaDetail = null },
            containerColor = Surface,
            title = { Text(d.name, fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (d.posterUrl.isNotBlank()) {
                        JellyPoster(d.posterUrl, config, Modifier.fillMaxWidth().heightIn(max = 260.dp), RoundedCornerShape(8.dp), ContentScale.Fit)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (d.kind in org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS) {
                        TextButton(
                            onClick = { mediaDetail = null; playRequest = org.phioster.sanctumd.ui.player.PlayRequest(d.id, d.name) },
                            modifier = Modifier.fillMaxWidth().border(1.dp, MatrixGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                        ) { Text("▶  play", fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        val dl = downloads[d.id]
                        val startDownload = {
                            org.phioster.sanctumd.service.DownloadService.enqueue(
                                context, config.id, d.id, d.name, d.genres, d.posterUrl, 0L,
                            )
                        }
                        when (dl?.state) {
                            org.phioster.sanctumd.model.DownloadEntry.STATE_DONE ->
                                Text("✓  downloaded — play it from the DOWNLOADS row", fontFamily = Mono, color = accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                            org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING, org.phioster.sanctumd.model.DownloadEntry.STATE_QUEUED ->
                                TextButton(
                                    onClick = { org.phioster.sanctumd.service.DownloadService.cancel(context, d.id) },
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                                ) { Text("⬇  ${(dl.progress * 100).toInt()}%  ·  cancel", fontFamily = Mono, color = MatrixGreen) }
                            org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED ->
                                TextButton(
                                    onClick = { startDownload() },
                                    modifier = Modifier.fillMaxWidth().border(1.dp, ErrRed.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                ) { Text("⚠  download failed — retry", fontFamily = Mono, color = ErrRed) }
                            else ->
                                TextButton(
                                    onClick = { startDownload() },
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MatrixGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                ) { Text("⬇  download", fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (d.facts.isNotEmpty()) {
                        d.facts.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
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
                        Text(d.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                    if (d.overview.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    if (d.cast.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("CAST", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            d.cast.forEach { member ->
                                Column(Modifier.width(84.dp).padding(end = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (member.profileUrl.isNotBlank()) {
                                        JellyPoster(member.profileUrl, config, Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)), RoundedCornerShape(36.dp), ContentScale.Crop)
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
                }
            },
            confirmButton = { TextButton(onClick = { mediaDetail = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
            dismissButton = {
                TextButton(onClick = { openExternal(context, jellyfinAppPackages, "${config.normalizedBaseUrl}web/#/details?id=${d.id}") }) {
                    Text("Open in Jellyfin", fontFamily = Mono, color = accent)
                }
            },
        )
    }

    // Full-screen playback overlay, on top of everything in this screen.
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
@Composable
private fun DownloadCard(
    entry: org.phioster.sanctumd.model.DownloadEntry,
    accent: Color,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.width(120.dp).padding(end = 10.dp)) {
        Box(
            Modifier.width(120.dp).height(170.dp).clip(RoundedCornerShape(8.dp)).background(Surface)
                .clickable { onPlay() },
        ) {
            if (entry.posterFile.startsWith("/")) {
                coil.compose.AsyncImage(
                    model = java.io.File(entry.posterFile),
                    contentDescription = entry.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Dim + state glyph overlay.
            val glyph = when (entry.state) {
                org.phioster.sanctumd.model.DownloadEntry.STATE_DONE -> "▶"
                org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED -> "⚠"
                else -> "${(entry.progress * 100).toInt()}%"
            }
            Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                Text(glyph, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "✕", fontFamily = Mono, color = ErrRed, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).background(Color(0xAA000000), RoundedCornerShape(6.dp))
                    .clickable { onDelete() }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        // Thin progress bar while downloading.
        if (!entry.done && entry.state != org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(MatrixGreen.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(entry.progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val status = when (entry.state) {
            org.phioster.sanctumd.model.DownloadEntry.STATE_DONE -> "downloaded"
            org.phioster.sanctumd.model.DownloadEntry.STATE_FAILED -> "failed"
            org.phioster.sanctumd.model.DownloadEntry.STATE_RUNNING -> "downloading…"
            else -> "queued"
        }
        Text(status, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
