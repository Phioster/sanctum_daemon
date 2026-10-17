package org.phioster.sanctumd.ui.nzbget

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.AutoRefresh
import org.phioster.sanctumd.ui.common.RefreshBox
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
internal fun NzbgetScreen(
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
    var refreshing by remember { mutableStateOf(false) }
    var pauseState by remember { mutableStateOf<org.phioster.sanctumd.model.NzbPauseState?>(null) }
    /** Queue items picked by a long press; non-empty switches rows from menu to selection. */
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pauseMenu by remember { mutableStateOf(false) }
    LaunchedEffect(actionMsg) { if (actionMsg != null) { kotlinx.coroutines.delay(4000); actionMsg = null } }

    suspend fun loadQueue() {
        listError = null
        try {
            val q = vm.queue(config)
            pauseState = runCatching { vm.nzbPauseState(config) }.getOrNull()
            queue = org.phioster.sanctumd.net.withEtas(q, pauseState?.rateBytesPerSec ?: 0L)
            selected = selected.filter { id -> q.any { it.id == id } }.toSet()
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
    fun pullRefresh() { actionMsg = null; scope.launch { refreshing = true; if (tab == 0) loadQueue() else loadHistory(); refreshing = false } }
    // Speed and progress change by the second while downloading; an idle queue only needs a glance.
    AutoRefresh(tab, periodMs = {
        val busy = (pauseState?.rateBytesPerSec ?: 0L) > 0 || queue.orEmpty().any { it.postInfo.isNotBlank() }
        if (tab != 0) null else if (busy) 3_000L else 15_000L
    }) { loadQueue() }

    var barMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    /** The log dialog: null closed, (null, _) the whole server log, (id, name) one download's. */
    var logTarget by remember { mutableStateOf<Pair<Int?, String>?>(null) }
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(config.type, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(config.label, fontFamily = Mono, color = MatrixGreen)
                    }
                },
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
                            DropdownMenuItem(text = { Text("Log", fontFamily = Mono) }, onClick = { barMenu = false; logTarget = null to "Log" })
                            val postPaused = pauseState?.postPaused == true
                            DropdownMenuItem(
                                text = { Text(if (postPaused) "Resume post-processing" else "Pause post-processing", fontFamily = Mono) },
                                onClick = { barMenu = false; act { vm.nzbPostPause(config, !postPaused) } },
                            )
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
                    Box {
                        TextButton(onClick = { pauseMenu = true }) { Text("pause for…", fontFamily = Mono, color = accent, fontSize = 12.sp) }
                        DropdownMenu(expanded = pauseMenu, onDismissRequest = { pauseMenu = false }) {
                            listOf(30 to "30 min", 60 to "1 hour", 180 to "3 hours").forEach { (min, label) ->
                                DropdownMenuItem(text = { Text(label, fontFamily = Mono) }, onClick = { pauseMenu = false; act { vm.nzbPauseFor(config, min) } })
                            }
                        }
                    }
                    IconButton(enabled = !refreshing, onClick = { pullRefresh() }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = MatrixGreen, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen)
                    }
                }
                pauseState?.let { ps ->
                    val lines = buildList {
                        if (ps.paused && ps.resumeAt > 0) {
                            add("paused until " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(ps.resumeAt * 1000)))
                        }
                        if (ps.postPaused) add("post-processing paused")
                    }
                    if (lines.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(lines.joinToString(" · "), fontFamily = Mono, color = org.phioster.sanctumd.ui.theme.WarnAmber, fontSize = 12.sp)
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
            if (tab == 0 && selected.isNotEmpty()) {
                // Acts on every picked item in one editqueue call.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected", fontFamily = Mono, color = accent, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    listOf("GroupPause" to "pause", "GroupResume" to "resume", "GroupMoveTop" to "top", "GroupDelete" to "delete").forEach { (cmd, label) ->
                        TextButton(onClick = {
                            val ids = selected.toList()
                            selected = emptySet()
                            act { vm.nzbEditMany(config, cmd, ids) }
                        }) { Text(label, fontFamily = Mono, color = if (cmd == "GroupDelete") ErrRed else MatrixGreen, fontSize = 12.sp) }
                    }
                    TextButton(onClick = { selected = emptySet() }) { Text("x", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            RefreshBox(refreshing, { pullRefresh() }, Modifier.weight(1f).fillMaxWidth()) {
                SwipeTabs(tab, 2, { tab = it }, Modifier.fillMaxSize()) { page ->
                    if (listError != null) {
                        Text(org.phioster.sanctumd.ui.services.friendlyStatusError(listError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            if (page == 0) {
                                val q = queue
                                when {
                                    q == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    q.isEmpty() -> item { Text("queue is empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                                    else -> items(q) { qi ->
                                        QueueRow(
                                            item = qi,
                                            selected = qi.id in selected,
                                            selecting = selected.isNotEmpty(),
                                            onToggleSelect = { selected = if (qi.id in selected) selected - qi.id else selected + qi.id },
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
                                    else -> items(h) { hi -> HistoryRow(hi, onShowLog = { logTarget = hi.id to hi.name }) { cmd -> act { vm.nzbEdit(config, cmd, hi.id) } } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    logTarget?.let { (id, title) ->
        NzbLogDialog(title = title, load = { if (id == null) vm.nzbLog(config) else vm.nzbItemLog(config, id) }) { logTarget = null }
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

/** "1h 05m", "12m", "40s": short enough for a queue row. */
internal fun formatEta(sec: Long): String = when {
    sec >= 3600 -> "%dh %02dm".format(sec / 3600, (sec % 3600) / 60)
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}s"
}

/** NZBGet's priority numbers as the words its own UI uses. */
internal fun priorityLabel(p: Int): String = when {
    p >= 900 -> "force"
    p >= 100 -> "very high"
    p >= 50 -> "high"
    p <= -100 -> "very low"
    p <= -50 -> "low"
    else -> ""
}

/** Everything about a queue item that fits on one line; empty parts are left out. */
internal fun queueDetail(item: NzbQueueItem): String = listOfNotNull(
    item.etaSec?.let { "ETA ${formatEta(it)}" },
    item.category.takeIf { it.isNotBlank() },
    priorityLabel(item.priority).takeIf { it.isNotBlank() },
    item.ageDays.takeIf { it >= 0 }?.let { "${it}d old" },
    item.health.takeIf { it < 1000 }?.let { "health %.1f%%".format(java.util.Locale.ROOT, it / 10.0) },
).joinToString(" · ")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun QueueRow(
    item: NzbQueueItem,
    selected: Boolean = false,
    selecting: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onAction: (String, String) -> Unit,
    onCategory: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .background(if (selected) MatrixGreen.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                // A long press starts picking; while picking, a tap adds or removes instead of opening the menu.
                .combinedClickable(onClick = { if (selecting) onToggleSelect() else menu = true }, onLongClick = onToggleSelect)
                .padding(vertical = 8.dp),
        ) {
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
            val detail = queueDetail(item)
            if (detail.isNotBlank()) {
                Text(detail, fontFamily = Mono, color = if (item.health < 1000) org.phioster.sanctumd.ui.theme.WarnAmber else MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.postInfo.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.postInfo, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { (item.postProgress / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MatrixGreen.copy(alpha = 0.6f),
                    trackColor = Surface,
                )
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
internal fun HistoryRow(item: NzbHistoryEntry, onShowLog: () -> Unit = {}, onAction: (String) -> Unit) {
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
            if (item.reason.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.reason, fontFamily = Mono, color = ErrRed.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!item.status.startsWith("SUCCESS")) {
                // Fetches only the articles that were missing, instead of the whole download again.
                DropdownMenuItem(text = { Text("Retry failed articles", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryRetryFailed") })
                DropdownMenuItem(text = { Text("Post-process again", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryProcess") })
            }
            // Tells Sonarr/Radarr this release is bad, so they blocklist it and grab another.
            DropdownMenuItem(text = { Text("Mark bad, grab another", fontFamily = Mono, color = ErrRed) }, onClick = { menu = false; onAction("HistoryMarkBad") })
            DropdownMenuItem(text = { Text("Show log", fontFamily = Mono) }, onClick = { menu = false; onShowLog() })
            DropdownMenuItem(text = { Text("Return to queue", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryReturn") })
            DropdownMenuItem(text = { Text("Redownload", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryRedownload") })
            DropdownMenuItem(text = { Text("Delete (hide)", fontFamily = Mono) }, onClick = { menu = false; onAction("HistoryDelete") })
            // Fully removes the entry from NZBGet's dupe history so the same release can be grabbed again.
            DropdownMenuItem(text = { Text("Delete + unblock (dupe)", fontFamily = Mono, color = ErrRed) }, onClick = { menu = false; onAction("HistoryFinalDelete") })
        }
    }
}
