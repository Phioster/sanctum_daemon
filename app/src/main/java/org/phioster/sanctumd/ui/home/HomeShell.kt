package org.phioster.sanctumd.ui.home

import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.tween
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberUpdatedState
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
import org.phioster.sanctumd.ui.common.*

/** Wraps a set of tab pages in a finger-following HorizontalPager (drags with the finger,
 *  snaps on release), kept in sync with the caller's [tab]. Nested horizontally-scrolling
 *  children (poster rows) still scroll via nested scroll. */
@Composable
internal fun SwipeTabs(
    tab: Int,
    count: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (page: Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = tab.coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    LaunchedEffect(tab) { if (pager.currentPage != tab) pager.animateScrollToPage(tab) }
    // Read the latest tab inside the long-running collector — capturing the param directly would
    // freeze it at its initial value, so returning to page 0 (the initial value) never notified and
    // the tab bar stuck on the previous tab.
    val latestTab = rememberUpdatedState(tab)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != latestTab.value) onChange(it) } }
    HorizontalPager(
        state = pager,
        modifier = modifier,
        userScrollEnabled = enabled,
        pageContent = { page -> content(page) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeShell(
    vm: DashboardViewModel,
    onAdd: () -> Unit,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
    onSearch: (String) -> Unit,
    onNotifications: () -> Unit,
    onStats: () -> Unit = {},
) {
    val tabs by vm.tabs.collectAsState()
    val services by vm.services.collectAsState()
    var selected by vm.homeTab // survives leaving composition — back returns to the same tab
    var editMode by remember { mutableStateOf(false) }
    var showAddCard by remember { mutableStateOf(false) }
    var showAddTab by remember { mutableStateOf(false) }
    var newTabName by remember { mutableStateOf("") }
    var newTabIcon by remember { mutableStateOf("home") }
    var showEditTab by remember { mutableStateOf(false) }
    var editTabName by remember { mutableStateOf("") }
    var editTabIcon by remember { mutableStateOf("home") }
    var newTabAccent by remember { mutableStateOf(0L) }
    var editTabAccent by remember { mutableStateOf(0L) }

    val current = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val currentTab = tabs.getOrNull(current)
    val swipeTabs by vm.swipeTabs.collectAsState()
    val swipeDrawer by vm.swipeDrawer.collectAsState()
    val drawerBand by vm.drawerBand.collectAsState()
    val currentAccent = currentTab?.takeIf { it.accent != 0L }?.let { Color(it.accent) } ?: MatrixGreen
    val scope = rememberCoroutineScope()
    // Custom full-width Services drawer, driven directly by the finger. progress: 0 = closed, 1 = open.
    // Either we're coming back from a screen opened out of the drawer, or this is a cold start and
    // the user wants the app to open on the services list.
    val startOnServices = vm.startScreenPending && vm.startScreen.value == "services"
    val startOpen = vm.reopenDrawer || startOnServices
    LaunchedEffect(Unit) { vm.reopenDrawer = false; vm.startScreenPending = false }
    var drawerWidthPx by remember { mutableFloatStateOf(1f) }
    val drawerProgress = remember { androidx.compose.animation.core.Animatable(if (startOpen) 1f else 0f) }
    val drawerOpen = drawerProgress.value > 0.001f
    val fullyOpen = drawerProgress.value > 0.99f
    fun openDrawer() = scope.launch { drawerProgress.animateTo(1f, androidx.compose.animation.core.tween(260)) }
    fun closeDrawer() = scope.launch { drawerProgress.animateTo(0f, androidx.compose.animation.core.tween(240)) }

    // Refresh service statuses while the drawer is fully open.
    LaunchedEffect(fullyOpen) {
        if (fullyOpen) {
            vm.refreshAll()
            while (true) { kotlinx.coroutines.delay(30_000); vm.refreshAll() }
        }
    }
    BackHandler(enabled = editMode) { editMode = false }
    BackHandler(enabled = drawerOpen) { closeDrawer() }

    Box(Modifier.fillMaxSize().onSizeChanged { drawerWidthPx = it.width.toFloat().coerceAtLeast(1f) }) {
    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(currentTab?.name ?: "home", fontFamily = Mono, fontWeight = FontWeight.Bold, color = currentAccent) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                navigationIcon = {
                    IconButton(onClick = { openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Services", tint = MatrixGreen) }
                },
                actions = {
                    // No search icon here — the tabs already carry the inline search bar.
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(if (editMode) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = "Edit", tint = if (editMode) MatrixGreen else MatrixGreen.copy(alpha = 0.8f))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                tabs.forEachIndexed { i, t ->
                    val ta = if (t.accent != 0L) Color(t.accent) else MatrixGreen
                    NavigationBarItem(
                        selected = current == i,
                        onClick = { selected = i; editMode = false },
                        icon = { Icon(tabIcon(t.icon), contentDescription = null) },
                        label = { Text(t.name, fontFamily = Mono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Black, selectedTextColor = ta, indicatorColor = ta,
                            unselectedIconColor = ta.copy(alpha = 0.5f), unselectedTextColor = ta.copy(alpha = 0.5f),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (editMode) FloatingActionButton(onClick = { showAddCard = true }, containerColor = MatrixGreen, contentColor = Black) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Finger-following tab paging (drags with the finger, snaps on release). Nested poster
            // rows still scroll via nested scroll.
            val dashPager = rememberPagerState(initialPage = current) { tabs.size.coerceAtLeast(1) }
            LaunchedEffect(current) { if (dashPager.currentPage != current) dashPager.animateScrollToPage(current) }
            LaunchedEffect(dashPager) { snapshotFlow { dashPager.settledPage }.collect { if (it != selected) selected = it } }
            HorizontalPager(
                state = dashPager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = swipeTabs && !editMode && tabs.size > 1,
            ) { page ->
                val t = tabs.getOrNull(page)
                if (t != null) {
                    WidgetTabContent(
                        vm = vm,
                        tab = t,
                        editMode = editMode,
                        onOpenService = onOpen,
                        onAddTab = { newTabName = ""; newTabIcon = "home"; newTabAccent = 0L; showAddTab = true },
                        onEditTab = { editTabName = t.name; editTabIcon = t.icon.ifBlank { "home" }; editTabAccent = t.accent; showEditTab = true },
                        onDeleteTab = { vm.removeTab(t.id); selected = 0; editMode = false },
                        onMoveTab = { dir -> selected = vm.moveTab(t.id, dir) },
                        onSearch = onSearch,
                    )
                }
            }
            // Bottom band: drag right to pull the Services drawer out (follows the finger, snaps on release).
            // Not composed at all in edit mode — otherwise this overlay covers the bottom of the screen
            // and swallows taps/scrolls on the lowest card's reorder controls (disabling the drag alone
            // isn't enough; the Box still wins the hit-test in the overlap zone).
            if (swipeDrawer && drawerBand > 0f && !editMode) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(drawerBand)
                        .draggable(
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                scope.launch { drawerProgress.snapTo((drawerProgress.value + delta / drawerWidthPx).coerceIn(0f, 1f)) }
                            },
                            onDragStopped = { v ->
                                drawerProgress.animateTo(if (drawerProgress.value > 0.35f || v > 900f) 1f else 0f, androidx.compose.animation.core.tween(220))
                            },
                        ),
                )
            }
        }
    }
        // Scrim: dims the peeking content while the drawer is partially open.
        if (drawerProgress.value > 0.001f) {
            Box(Modifier.fillMaxSize().background(Black.copy(alpha = drawerProgress.value.coerceIn(0f, 1f) * 0.5f)))
        }
        // The Services drawer: full width, slid in from the left, drag left to close.
        if (drawerOpen) {
            Box(
                Modifier.fillMaxSize()
                    .offset { IntOffset((-(1f - drawerProgress.value) * drawerWidthPx).roundToInt(), 0) }
                    .background(Black)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch { drawerProgress.snapTo((drawerProgress.value + delta / drawerWidthPx).coerceIn(0f, 1f)) }
                        },
                        onDragStopped = { v ->
                            drawerProgress.animateTo(if (drawerProgress.value > 0.6f && v > -900f) 1f else 0f, androidx.compose.animation.core.tween(220))
                        },
                    ),
            ) {
                ServicesDrawer(
                    vm = vm,
                    onOpen = { cfg -> vm.reopenDrawer = true; closeDrawer(); onOpen(cfg) },
                    onEdit = { cfg -> vm.reopenDrawer = true; onEdit(cfg) },
                    onAdd = { vm.reopenDrawer = true; onAdd() },
                    onNotifications = { vm.reopenDrawer = true; onNotifications() },
                    onSearch = { term -> vm.reopenDrawer = true; onSearch(term) },
                    onStats = { vm.reopenDrawer = true; onStats() },
                    onClose = { closeDrawer() },
                )
            }
        }
    }

    if (showAddCard && currentTab != null) {
        AddCardDialog(
            services = services,
            onDismiss = { showAddCard = false },
            onAdd = { type, sid -> vm.addCard(currentTab.id, type, sid); showAddCard = false },
        )
    }
    if (showAddTab) {
        AlertDialog(
            onDismissRequest = { showAddTab = false },
            containerColor = Surface,
            title = { Text("New tab", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Tab name", newTabName) { newTabName = it }
                    Spacer(Modifier.height(12.dp))
                    Text("ICON", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    IconPickerGrid(newTabIcon) { newTabIcon = it }
                    Spacer(Modifier.height(12.dp))
                    Text("ACCENT", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    AccentPickerRow(newTabAccent, MatrixGreen) { newTabAccent = it }
                }
            },
            confirmButton = {
                TextButton(enabled = newTabName.isNotBlank(), onClick = { val n = newTabName; val ic = newTabIcon; val ac = newTabAccent; showAddTab = false; vm.addTab(n, ic, ac); selected = tabs.size }) {
                    Text("Add", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showAddTab = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
    if (showEditTab && currentTab != null) {
        AlertDialog(
            onDismissRequest = { showEditTab = false },
            containerColor = Surface,
            title = { Text("Edit tab", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Field("Tab name", editTabName) { editTabName = it }
                    Spacer(Modifier.height(12.dp))
                    Text("ICON", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    IconPickerGrid(editTabIcon) { editTabIcon = it }
                    Spacer(Modifier.height(12.dp))
                    Text("ACCENT", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    AccentPickerRow(editTabAccent, MatrixGreen) { editTabAccent = it }
                }
            },
            confirmButton = {
                TextButton(enabled = editTabName.isNotBlank(), onClick = { val id = currentTab.id; val n = editTabName; val ic = editTabIcon; val ac = editTabAccent; showEditTab = false; vm.renameTab(id, n); vm.setTabIcon(id, ic); vm.setTabAccent(id, ac) }) {
                    Text("Save", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showEditTab = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServicesDrawer(
    vm: DashboardViewModel,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
    onAdd: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: (String) -> Unit,
    onStats: () -> Unit = {},
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("> sanctumd_", fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MatrixGreen) } },
                actions = {
                    IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MatrixGreen) }
                    IconButton(onClick = onStats) { Icon(Icons.Filled.BarChart, contentDescription = "Stats", tint = MatrixGreen) }
                    IconButton(onClick = onNotifications) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MatrixGreen) }
                    // Refresh removed — pull-to-refresh on the list covers it now.
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MatrixGreen, contentColor = Black) { Icon(Icons.Filled.Add, contentDescription = "Add service") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ServicesContent(vm, onOpen, onEdit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServicesContent(
    vm: DashboardViewModel,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
) {
    val services by vm.services.collectAsState()
    val statuses by vm.statuses.collectAsState()
    val viewMode by vm.serviceViewMode.collectAsState()
    val collapsed by vm.collapsedGroups.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // Long-press target: its quick actions open in a sheet. Group editor target: a rename dialog.
    var actionsFor by remember { mutableStateOf<ServiceConfig?>(null) }
    var groupFor by remember { mutableStateOf<ServiceConfig?>(null) }
    // Drag state: which service is being dragged and how far it has travelled since the last swap.
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0) }

    // Pinned first, then groups alphabetically with the ungrouped ones last; the order inside each
    // section stays the user's own (that's what dragging edits).
    val pinned = services.filter { it.pinned }
    val rest = services.filterNot { it.pinned }
    val groups: List<Pair<String, List<ServiceConfig>>> = buildList {
        if (pinned.isNotEmpty()) add(PINNED_SECTION to pinned)
        rest.filter { it.group.isNotBlank() }
            .groupBy { it.group }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (name, list) -> add(name to list) }
        rest.filter { it.group.isBlank() }.takeIf { it.isNotEmpty() }?.let {
            add((if (isEmpty()) "" else UNGROUPED_SECTION) to it)
        }
    }

    /** Swap the dragged service with its neighbour inside the section it is being dragged in. */
    fun dragBy(section: List<ServiceConfig>, id: String, steps: Int) {
        val idx = section.indexOfFirst { it.id == id }
        val target = section.getOrNull(idx + steps) ?: return
        vm.moveServiceTo(id, target.id)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; vm.refreshAllSuspend(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            if (services.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text("no services yet\n\ntap + to add a service", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 14.sp)
            } else {
                // View switcher.
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("cards" to "cards", "compact" to "compact", "grid" to "grid").forEach { (key, label) ->
                        val on = viewMode == key
                        Text(
                            label,
                            fontFamily = Mono, fontSize = 11.sp,
                            color = if (on) Black else MatrixGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (on) MatrixGreen else Color.Transparent)
                                .border(1.dp, MatrixGreen.copy(alpha = if (on) 0f else 0.3f), RoundedCornerShape(6.dp))
                                .clickable { vm.setServiceViewMode(key) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            groups.forEach { (section, items) ->
                val isCollapsed = section in collapsed
                if (section.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleGroupCollapsed(section) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (isCollapsed) "▸" else "▾", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            section.uppercase(),
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("(${items.size})", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                }
                if (isCollapsed) return@forEach

                when (viewMode) {
                    "grid" -> items.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { svc ->
                                ServiceTile(
                                    config = svc,
                                    status = statuses[svc.id],
                                    modifier = Modifier.weight(1f),
                                    onOpen = { onOpen(svc) },
                                    onLongPress = { actionsFor = svc },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    "compact" -> items.forEach { svc ->
                        Box(Modifier.dragSlot(svc.id, dragId, dragOffset)) {
                            ServiceRowCompact(
                                config = svc,
                                status = statuses[svc.id],
                                onOpen = { onOpen(svc) },
                                onLongPress = { actionsFor = svc },
                                dragHandle = {
                                    DragHandle(
                                        onStart = { dragId = svc.id; dragOffset = 0f },
                                        onDrag = { dy ->
                                            dragOffset += dy
                                            val h = (rowHeight.takeIf { it > 0 } ?: 120)
                                            while (kotlin.math.abs(dragOffset) >= h) {
                                                val step = if (dragOffset > 0) 1 else -1
                                                dragBy(items, svc.id, step)
                                                dragOffset -= step * h
                                            }
                                        },
                                        onEnd = { dragId = null; dragOffset = 0f },
                                    )
                                },
                            )
                        }
                    }
                    else -> items.forEachIndexed { index, svc ->
                        Box(Modifier.dragSlot(svc.id, dragId, dragOffset).onSizeChanged { if (it.height > 0) rowHeight = it.height }) {
                            ServiceCard(
                                config = svc,
                                status = statuses[svc.id],
                                isFirst = index == 0,
                                isLast = index == items.lastIndex,
                                onOpen = { onOpen(svc) },
                                onEdit = { onEdit(svc) },
                                onRemove = { vm.removeService(svc.id) },
                                onMoveUp = { dragBy(items, svc.id, -1) },
                                onMoveDown = { dragBy(items, svc.id, +1) },
                                onLongPress = { actionsFor = svc },
                                onPin = { vm.setServicePinned(svc.id, !svc.pinned) },
                                onGroup = { groupFor = svc },
                                dragHandle = {
                                    DragHandle(
                                        onStart = { dragId = svc.id; dragOffset = 0f },
                                        onDrag = { dy ->
                                            dragOffset += dy
                                            val h = (rowHeight.takeIf { it > 0 } ?: 200)
                                            while (kotlin.math.abs(dragOffset) >= h) {
                                                val step = if (dragOffset > 0) 1 else -1
                                                dragBy(items, svc.id, step)
                                                dragOffset -= step * h
                                            }
                                        },
                                        onEnd = { dragId = null; dragOffset = 0f },
                                    )
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    actionsFor?.let { svc ->
        ServiceQuickActionsSheet(vm, svc, onEdit = { actionsFor = null; onEdit(svc) }, onClose = { actionsFor = null })
    }
    groupFor?.let { svc ->
        ServiceGroupDialog(
            config = svc,
            existing = services.mapNotNull { it.group.takeIf { g -> g.isNotBlank() } }.distinct().sorted(),
            onPick = { vm.setServiceGroup(svc.id, it); groupFor = null },
            onClose = { groupFor = null },
        )
    }
}

private const val PINNED_SECTION = "pinned"
private const val UNGROUPED_SECTION = "other"

/** Lifts the dragged item out of the flow visually without disturbing its neighbours. */
private fun Modifier.dragSlot(id: String, dragId: String?, offset: Float): Modifier =
    if (id == dragId) this.zIndex(1f).graphicsLayer { translationY = offset; alpha = 0.85f } else this

/** The ≡ grip: dragging it reorders, so a long-press anywhere else stays free for quick actions. */
@Composable
private fun DragHandle(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Reorder",
        tint = MatrixGreen.copy(alpha = 0.45f),
        modifier = Modifier
            .padding(start = 2.dp)
            .size(22.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                    onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                )
            },
    )
}

/** Long-press sheet: the service's quick actions, run in place. */
@Composable
private fun ServiceQuickActionsSheet(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actions = remember(config.id) { org.phioster.sanctumd.ui.dashboard.quickActionsFor(config) }
    var running by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface,
        title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                if (actions.isEmpty()) {
                    Text("no quick actions for this service", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                actions.forEach { action ->
                    Text(
                        if (running == action.label) "${action.label}  ·  running…" else action.label,
                        fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = running == null) {
                            running = action.label
                            scope.launch {
                                result = runCatching { action.run(vm, config) }.getOrElse { "error: ${it.message}" }
                                running = null
                            }
                        }.padding(vertical = 10.dp),
                    )
                }
                result?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = Color(config.type.accent), fontSize = 11.sp)
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onEdit) { Text("edit", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("close", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f)) } },
    )
}

/** Picks or types the group a service belongs to. */
@Composable
private fun ServiceGroupDialog(
    config: ServiceConfig,
    existing: List<String>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(config.group) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface,
        title = { Text("group", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("group name", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, color = MatrixGreen),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreen.copy(alpha = 0.6f),
                        unfocusedBorderColor = MatrixGreen.copy(alpha = 0.25f),
                        cursorColor = MatrixGreen,
                    ),
                )
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existing.forEach { g ->
                            Text(
                                g, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp,
                                modifier = Modifier
                                    .border(1.dp, MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable { name = g }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Leave empty to take it out of every group.",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(name) }) { Text("save", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("cancel", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetTabContent(
    vm: DashboardViewModel,
    tab: org.phioster.sanctumd.model.DashTab,
    editMode: Boolean,
    onOpenService: (ServiceConfig) -> Unit,
    onAddTab: () -> Unit,
    onEditTab: () -> Unit,
    onDeleteTab: () -> Unit,
    onMoveTab: (Int) -> Unit,
    onSearch: (String) -> Unit,
) {
    val services by vm.services.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // Entering edit mode prepends the tab-edit bar at the top; scroll up so it's visible.
    LaunchedEffect(editMode) { if (editMode) listState.animateScrollToItem(0) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                vm.dashRefreshTick.intValue++ // force every card to reload its data
                vm.refreshAllSuspend()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState) {
        if (!editMode) {
            item {
                Spacer(Modifier.height(10.dp))
                InlineSearchBar(onSearch)
            }
        }
        if (editMode) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("◀", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, modifier = Modifier.clickable { onMoveTab(-1) }.padding(end = 12.dp))
                        Text("▶", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, modifier = Modifier.clickable { onMoveTab(1) })
                    }
                    Text("+ new tab", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.clickable { onAddTab() })
                    Text("edit tab", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.clickable { onEditTab() })
                    Text("delete", fontFamily = Mono, color = ErrRed, fontSize = 13.sp, modifier = Modifier.clickable { onDeleteTab() })
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
            }
        }
        if (tab.cards.isEmpty()) {
            item {
                Spacer(Modifier.height(60.dp))
                Text(
                    if (editMode) "tap + to add a card" else "empty tab\n\ntap the pencil to edit",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            itemsIndexed(tab.cards, key = { _, c -> c.id }) { index, card ->
                val config = services.firstOrNull { it.id == card.serviceId }
                DashCardView(
                    vm = vm,
                    card = card,
                    config = config,
                    editMode = editMode,
                    isFirst = index == 0,
                    isLast = index == tab.cards.lastIndex,
                    onOpenService = { config?.let(onOpenService) },
                    onOpenAny = onOpenService,
                    onRemove = { vm.removeCard(tab.id, card.id) },
                    onMoveUp = { vm.moveCard(tab.id, card.id, -1) },
                    onMoveDown = { vm.moveCard(tab.id, card.id, +1) },
                    onSaveConfig = { title, count, accent, icon, posterSize, background, theme, density -> vm.updateCard(tab.id, card.id, title, count, accent, icon, posterSize, background, theme, density) },
                    allServices = services,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
}
