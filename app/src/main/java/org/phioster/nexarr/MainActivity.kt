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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
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
import org.phioster.nexarr.model.CardType
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
    var searchOpen by remember { mutableStateOf(false) }

    val editorOpen = addOpen || editing != null
    BackHandler(enabled = editorOpen || detail != null || searchOpen) {
        when {
            editorOpen -> { addOpen = false; editing = null }
            detail != null -> detail = null
            else -> searchOpen = false
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
                ServiceType.JELLYFIN -> JellyfinScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                else -> ServiceDetailScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
            }
        }
        searchOpen -> GlobalSearchScreen(
            vm = vm,
            onBack = { searchOpen = false },
            onOpenService = { cfg -> searchOpen = false; detail = cfg },
        )
        else -> HomeShell(
            vm = vm,
            onAdd = { addOpen = true },
            onOpen = { detail = it },
            onEdit = { editing = it },
            onSearch = { searchOpen = true },
        )
    }
}

/** Selectable tab icons; the stored key maps back to a Material icon. */
private val tabIcons: List<Pair<String, ImageVector>> = listOf(
    "home" to Icons.Filled.Home,
    "movie" to Icons.Filled.Movie,
    "tv" to Icons.Filled.Tv,
    "livetv" to Icons.Filled.LiveTv,
    "music" to Icons.Filled.MusicNote,
    "download" to Icons.Filled.Download,
    "book" to Icons.Filled.MenuBook,
    "star" to Icons.Filled.Star,
    "favorite" to Icons.Filled.Favorite,
    "folder" to Icons.Filled.Folder,
)

private fun tabIcon(key: String): ImageVector =
    tabIcons.firstOrNull { it.first == key }?.second ?: Icons.Filled.Home

/** Accent choices shared by card and tab pickers; 0 = "use the default" (service/tab colour). */
private val accentPalette = listOf(0L, 0xFF35D07AL, 0xFF00A4DCL, 0xFFFFC230L, 0xFFE66000L, 0xFF818CF8L, 0xFFEC4899L, 0xFF8B5CF6L, 0xFFE5534BL)

@Composable
private fun AccentPickerRow(selected: Long, defaultColor: Color, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        accentPalette.forEach { c ->
            val col = if (c == 0L) defaultColor else Color(c)
            val sel = selected == c
            Box(
                Modifier.padding(end = 10.dp).size(34.dp).clip(RoundedCornerShape(50))
                    .background(col).border(if (sel) 3.dp else 0.dp, MatrixGreen, RoundedCornerShape(50))
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun IconPickerGrid(selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        tabIcons.forEach { (key, icon) ->
            val sel = key == selected
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) MatrixGreen else Surface)
                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = key, tint = if (sel) Black else MatrixGreen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell(
    vm: DashboardViewModel,
    onAdd: () -> Unit,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
    onSearch: () -> Unit,
) {
    val tabs by vm.tabs.collectAsState()
    val services by vm.services.collectAsState()
    var selected by remember { mutableStateOf(0) }
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

    val servicesIndex = tabs.size
    val current = selected.coerceIn(0, servicesIndex)
    val onServices = current == servicesIndex
    val currentTab = tabs.getOrNull(current)
    val currentAccent = currentTab?.takeIf { it.accent != 0L }?.let { Color(it.accent) } ?: MatrixGreen

    LaunchedEffect(onServices) {
        if (onServices) while (true) { kotlinx.coroutines.delay(30_000); vm.refreshAll() }
    }
    BackHandler(enabled = editMode) { editMode = false }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Black, selectedTextColor = MatrixGreen, indicatorColor = MatrixGreen,
        unselectedIconColor = MatrixGreen.copy(alpha = 0.5f), unselectedTextColor = MatrixGreen.copy(alpha = 0.5f),
    )

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(if (onServices) "> nexarr_" else (currentTab?.name ?: "home"), fontFamily = Mono, fontWeight = FontWeight.Bold, color = if (onServices) MatrixGreen else currentAccent) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MatrixGreen) }
                    if (onServices) {
                        IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen) }
                    } else {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(if (editMode) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = "Edit", tint = if (editMode) MatrixGreen else MatrixGreen.copy(alpha = 0.8f))
                        }
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
                NavigationBarItem(
                    selected = onServices,
                    onClick = { selected = servicesIndex; editMode = false },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                    label = { Text("Services", fontFamily = Mono, fontSize = 10.sp) },
                    colors = navColors,
                )
            }
        },
        floatingActionButton = {
            when {
                onServices -> FloatingActionButton(onClick = onAdd, containerColor = MatrixGreen, contentColor = Black) { Icon(Icons.Filled.Add, contentDescription = "Add service") }
                editMode -> FloatingActionButton(onClick = { showAddCard = true }, containerColor = MatrixGreen, contentColor = Black) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (onServices) {
                ServicesContent(vm, onOpen, onEdit)
            } else if (currentTab != null) {
                WidgetTabContent(
                    vm = vm,
                    tab = currentTab,
                    editMode = editMode,
                    onOpenService = onOpen,
                    onAddTab = { newTabName = ""; newTabIcon = "home"; newTabAccent = 0L; showAddTab = true },
                    onEditTab = { editTabName = currentTab.name; editTabIcon = currentTab.icon.ifBlank { "home" }; editTabAccent = currentTab.accent; showEditTab = true },
                    onDeleteTab = { vm.removeTab(currentTab.id); selected = 0; editMode = false },
                    onMoveTab = { dir -> selected = vm.moveTab(currentTab.id, dir) },
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

@Composable
private fun ServicesContent(
    vm: DashboardViewModel,
    onOpen: (ServiceConfig) -> Unit,
    onEdit: (ServiceConfig) -> Unit,
) {
    val services by vm.services.collectAsState()
    val statuses by vm.statuses.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (services.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text("no services yet\n\ntap + to add a service", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        services.forEachIndexed { index, svc ->
            ServiceCard(
                config = svc,
                status = statuses[svc.id],
                isFirst = index == 0,
                isLast = index == services.lastIndex,
                onOpen = { onOpen(svc) },
                onEdit = { onEdit(svc) },
                onRemove = { vm.removeService(svc.id) },
                onMoveUp = { vm.moveService(svc.id, -1) },
                onMoveDown = { vm.moveService(svc.id, +1) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WidgetTabContent(
    vm: DashboardViewModel,
    tab: org.phioster.nexarr.model.DashTab,
    editMode: Boolean,
    onOpenService: (ServiceConfig) -> Unit,
    onAddTab: () -> Unit,
    onEditTab: () -> Unit,
    onDeleteTab: () -> Unit,
    onMoveTab: (Int) -> Unit,
) {
    val services by vm.services.collectAsState()
    val listState = rememberLazyListState()
    // Entering edit mode prepends the tab-edit bar at the top; scroll up so it's visible.
    LaunchedEffect(editMode) { if (editMode) listState.animateScrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState) {
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
                    onRemove = { vm.removeCard(tab.id, card.id) },
                    onMoveUp = { vm.moveCard(tab.id, card.id, -1) },
                    onMoveDown = { vm.moveCard(tab.id, card.id, +1) },
                    onSaveConfig = { title, count, accent, icon, posterSize, background, theme -> vm.updateCard(tab.id, card.id, title, count, accent, icon, posterSize, background, theme) },
                    allServices = services,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DashCardView(
    vm: DashboardViewModel,
    card: org.phioster.nexarr.model.DashCard,
    config: ServiceConfig?,
    editMode: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenService: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSaveConfig: (String, Int, Long, String, String, Boolean, String) -> Unit,
    allServices: List<ServiceConfig> = emptyList(),
) {
    val serviceless = card.type.service == null
    val accentColor = if (card.accent != 0L) Color(card.accent) else if (config != null) Color(config.type.accent) else MatrixGreen
    // On a solid (accent-tinted) panel, accent-coloured text/icons flip to black for contrast.
    val accent = if (card.theme == "solid") Black else accentColor
    val posterWidth = when (card.posterSize) { "small" -> 84.dp; "large" -> 150.dp; else -> 120.dp }
    var showConfig by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>?>(null) }
    var sessions by remember { mutableStateOf<List<org.phioster.nexarr.model.JellySession>?>(null) }
    var requests by remember { mutableStateOf<List<org.phioster.nexarr.model.SeerrRequestItem>?>(null) }
    var queue by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrQueueItem>?>(null) }
    var missing by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrMissingItem>?>(null) }
    var calendar by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrCalendarItem>?>(null) }
    var history by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrHistoryItem>?>(null) }
    var nzbQueue by remember { mutableStateOf<List<org.phioster.nexarr.model.NzbQueueItem>?>(null) }
    var nzbHistory by remember { mutableStateOf<List<org.phioster.nexarr.model.NzbHistoryEntry>?>(null) }
    var discover by remember { mutableStateOf<List<org.phioster.nexarr.model.SeerrDiscoverItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(card.id, config?.id) {
        if (serviceless) return@LaunchedEffect // Section / Quick Buttons need no data
        if (config == null) { error = "service not found"; return@LaunchedEffect }
        // Retry a couple of times: on a cold start the Jellyfin token may not be ready yet.
        var attempt = 0
        while (attempt < 3) {
            error = null
            try {
                when (card.type) {
                    CardType.SECTION, CardType.QUICKBUTTONS -> {}
                    CardType.JELLYFIN_SESSIONS -> sessions = vm.jellyfinSessionList(config)
                    CardType.JELLYFIN_RECENT -> items = vm.jellyfinRecent(config, null)
                    CardType.JELLYFIN_RESUME -> items = vm.jellyfinContinue(config)
                    CardType.SEERR_REQUESTS -> requests = vm.seerrList(config, "all")
                    CardType.RADARR_QUEUE, CardType.SONARR_QUEUE, CardType.LIDARR_QUEUE -> queue = vm.arrQueueList(config)
                    CardType.RADARR_MISSING, CardType.SONARR_MISSING, CardType.LIDARR_MISSING -> missing = vm.arrMissingList(config)
                    CardType.RADARR_CALENDAR, CardType.SONARR_CALENDAR, CardType.LIDARR_CALENDAR -> calendar = vm.arrCalendarList(config)
                    CardType.RADARR_HISTORY, CardType.SONARR_HISTORY, CardType.LIDARR_HISTORY -> history = vm.arrHistoryList(config)
                    CardType.NZBGET_QUEUE -> nzbQueue = vm.queue(config)
                    CardType.NZBGET_HISTORY -> nzbHistory = vm.history(config, false)
                    CardType.SEERR_TRENDING -> discover = vm.seerrDiscoverList(config, "trending")
                    CardType.SEERR_POPULAR_MOVIES -> discover = vm.seerrDiscoverList(config, "movies")
                    CardType.SEERR_POPULAR_TV -> discover = vm.seerrDiscoverList(config, "tv")
                }
                break
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = t.message
                attempt++
                if (attempt < 3) kotlinx.coroutines.delay(1200)
            }
        }
    }

    val bgPool = when (card.type) {
        CardType.JELLYFIN_RECENT, CardType.JELLYFIN_RESUME -> items?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        CardType.SEERR_TRENDING, CardType.SEERR_POPULAR_MOVIES, CardType.SEERR_POPULAR_TV -> discover?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        else -> null
    }
    // Pick one poster at random per open; re-picks only when the data reloads (or the tab is reopened / app restarts).
    val bgUrl = remember(items, discover) { bgPool?.randomOrNull() }
    val hasBg = card.background && bgUrl != null && config != null
    val boxed = hasBg || card.theme == "solid" || card.theme == "glass"

    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = if (boxed) 8.dp else 0.dp)
            .then(if (boxed) Modifier.clip(RoundedCornerShape(14.dp)) else Modifier)
            .then(
                when {
                    hasBg -> Modifier
                    card.theme == "solid" -> Modifier.background(androidx.compose.ui.graphics.lerp(accentColor, Black, 0.4f))
                    card.theme == "glass" -> Modifier.background(Surface.copy(alpha = 0.5f)).border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    else -> Modifier
                },
            ),
    ) {
        if (hasBg) KenBurnsBackground(bgUrl!!, config!!)
        if (card.theme == "glass") {
            // Glass sheen: a soft diagonal highlight tinted with the accent.
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, accentColor.copy(alpha = 0.10f))),
                ),
            )
        }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = if (boxed) 12.dp else 0.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { onOpenService() }, verticalAlignment = Alignment.CenterVertically) {
                if (card.icon.isNotBlank()) {
                    Icon(tabIcon(card.icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(card.title.ifBlank { card.type.label }.uppercase(), fontFamily = Mono, color = if (card.theme == "solid") Black else if (card.type == CardType.SECTION) accentColor else MatrixGreen, fontSize = if (card.type == CardType.SECTION) 15.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!serviceless) Text(config?.label ?: "?", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
            if (editMode) {
                TextButton(onClick = { showConfig = true }, contentPadding = PaddingValues(4.dp)) { Text("⚙", fontFamily = Mono, color = MatrixGreen) }
                if (!isFirst) TextButton(onClick = onMoveUp, contentPadding = PaddingValues(4.dp)) { Text("↑", fontFamily = Mono, color = MatrixGreen) }
                if (!isLast) TextButton(onClick = onMoveDown, contentPadding = PaddingValues(4.dp)) { Text("↓", fontFamily = Mono, color = MatrixGreen) }
                TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) { Text("✕", fontFamily = Mono, color = ErrRed) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val loading = @Composable { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
        val empty = @Composable { msg: String -> Text(msg, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
        when {
            error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
            card.type == CardType.SECTION -> HorizontalDivider(color = accentColor.copy(alpha = 0.6f), thickness = 2.dp)
            card.type == CardType.QUICKBUTTONS -> {
                val actions = allServices.flatMap { svc -> quickActionsFor(svc).map { svc to it } }
                if (actions.isEmpty()) empty("no actions available")
                else Column {
                    actions.forEach { (svc, qa) ->
                        Text(
                            "▸ ${qa.label}",
                            fontFamily = Mono, color = accentColor, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val res = runCatching { qa.run(vm, svc) }.getOrElse { it.message ?: "failed" }
                                        android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
            card.type == CardType.JELLYFIN_SESSIONS -> {
                val s = sessions
                when {
                    s == null -> loading()
                    s.isEmpty() -> empty("no active sessions")
                    else -> Column { s.forEach { DashSessionRow(it, accent) } }
                }
            }
            card.type == CardType.SEERR_REQUESTS -> {
                val r = requests
                when {
                    r == null -> loading()
                    r.isEmpty() -> empty("no requests")
                    else -> Column { r.take(card.count).forEach { DashLineRow(it.title, it.subtitle.ifBlank { it.status }, accent) } }
                }
            }
            card.type == CardType.RADARR_QUEUE || card.type == CardType.SONARR_QUEUE || card.type == CardType.LIDARR_QUEUE -> {
                val q = queue
                when {
                    q == null -> loading()
                    q.isEmpty() -> empty("queue empty")
                    else -> Column { q.take(card.count).forEach { DashQueueRow(it, accent) } }
                }
            }
            card.type == CardType.RADARR_MISSING || card.type == CardType.SONARR_MISSING || card.type == CardType.LIDARR_MISSING -> {
                val m = missing
                when {
                    m == null -> loading()
                    m.isEmpty() -> empty("nothing missing")
                    else -> Column { m.take(card.count).forEach { DashLineRow(it.title, it.subtitle, accent) } }
                }
            }
            card.type == CardType.RADARR_CALENDAR || card.type == CardType.SONARR_CALENDAR || card.type == CardType.LIDARR_CALENDAR -> {
                val c = calendar
                when {
                    c == null -> loading()
                    c.isEmpty() -> empty("nothing upcoming")
                    else -> Column { c.take(card.count).forEach { DashLineRow("${if (it.hasFile) "✓ " else ""}${it.title}", "${it.date}${if (it.subtitle.isNotBlank()) " · ${it.subtitle}" else ""}", accent) } }
                }
            }
            card.type == CardType.RADARR_HISTORY || card.type == CardType.SONARR_HISTORY || card.type == CardType.LIDARR_HISTORY -> {
                val h = history
                when {
                    h == null -> loading()
                    h.isEmpty() -> empty("no history")
                    else -> Column { h.take(card.count).forEach { DashLineRow(it.title, "${it.eventType} · ${it.date}", accent) } }
                }
            }
            card.type == CardType.NZBGET_QUEUE -> {
                val q = nzbQueue
                when {
                    q == null -> loading()
                    q.isEmpty() -> empty("queue empty")
                    else -> Column { q.take(card.count).forEach { DashNzbRow(it.name, it.status, it.progress, accent) } }
                }
            }
            card.type == CardType.NZBGET_HISTORY -> {
                val h = nzbHistory
                when {
                    h == null -> loading()
                    h.isEmpty() -> empty("no history")
                    else -> Column { h.take(card.count).forEach { DashLineRow(it.name, it.status, accent) } }
                }
            }
            card.type == CardType.SEERR_TRENDING || card.type == CardType.SEERR_POPULAR_MOVIES || card.type == CardType.SEERR_POPULAR_TV -> {
                val d = discover
                when {
                    d == null -> loading()
                    d.isEmpty() -> empty("nothing here")
                    else -> Row(Modifier.horizontalScroll(rememberScrollState())) { d.take(card.count).forEach { DashDiscoverPoster(it, posterWidth) { onOpenService() } } }
                }
            }
            else -> {
                val it2 = items
                when {
                    it2 == null -> loading()
                    it2.isEmpty() -> empty("nothing here")
                    else -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                        it2.take(card.count).forEach { m -> if (config != null) JellyPosterCard(m, config, accent, posterWidth) { onOpenService() } }
                    }
                }
            }
        }
        if (!boxed) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
    }
    }

    if (showConfig) {
        var cfgTitle by remember { mutableStateOf(card.title) }
        var cfgCount by remember { mutableStateOf(card.count) }
        var cfgAccent by remember { mutableStateOf(card.accent) }
        var cfgIcon by remember { mutableStateOf(card.icon) }
        var cfgPoster by remember { mutableStateOf(card.posterSize) }
        var cfgBg by remember { mutableStateOf(card.background) }
        var cfgTheme by remember { mutableStateOf(card.theme) }
        val isPoster = card.type in setOf(CardType.JELLYFIN_RECENT, CardType.JELLYFIN_RESUME, CardType.SEERR_TRENDING, CardType.SEERR_POPULAR_MOVIES, CardType.SEERR_POPULAR_TV)
        val serviceColor = if (config != null) Color(config.type.accent) else MatrixGreen
        val label = @Composable { t: String -> Text(t, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp) }
        AlertDialog(
            onDismissRequest = { showConfig = false },
            containerColor = Surface,
            title = { Text("Card settings", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Field("Title (blank = ${card.type.label})", cfgTitle) { cfgTitle = it }
                    Spacer(Modifier.height(16.dp))
                    label("ENTRIES SHOWN")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (cfgCount > 3) cfgCount-- }, contentPadding = PaddingValues(8.dp)) { Text("−", fontFamily = Mono, color = MatrixGreen, fontSize = 22.sp) }
                        Text("$cfgCount", fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center)
                        TextButton(onClick = { if (cfgCount < 20) cfgCount++ }, contentPadding = PaddingValues(8.dp)) { Text("+", fontFamily = Mono, color = MatrixGreen, fontSize = 22.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    label("CARD STYLE")
                    Spacer(Modifier.height(6.dp))
                    Row {
                        listOf("" to "Flat", "solid" to "Solid", "glass" to "Glass").forEach { (value, lbl) ->
                            val sel = cfgTheme == value
                            Box(
                                Modifier.padding(end = 8.dp).size(width = 74.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { cfgTheme = value },
                                contentAlignment = Alignment.Center,
                            ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    label("ACCENT (1st = service default)")
                    Spacer(Modifier.height(6.dp))
                    AccentPickerRow(cfgAccent, serviceColor) { cfgAccent = it }
                    Spacer(Modifier.height(16.dp))
                    label("HEADER ICON")
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        val noneSel = cfgIcon.isBlank()
                        Box(
                            Modifier.padding(end = 8.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (noneSel) MatrixGreen else Surface)
                                .border(1.dp, if (noneSel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { cfgIcon = "" },
                            contentAlignment = Alignment.Center,
                        ) { Text("∅", fontFamily = Mono, color = if (noneSel) Black else MatrixGreen, fontSize = 18.sp) }
                        tabIcons.forEach { (key, icon) ->
                            val sel = cfgIcon == key
                            Box(
                                Modifier.padding(end = 8.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { cfgIcon = key },
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, contentDescription = key, tint = if (sel) Black else MatrixGreen) }
                        }
                    }
                    if (isPoster) {
                        Spacer(Modifier.height(16.dp))
                        label("POSTER SIZE")
                        Spacer(Modifier.height(6.dp))
                        Row {
                            listOf("small" to "S", "" to "M", "large" to "L").forEach { (value, lbl) ->
                                val sel = cfgPoster == value
                                Box(
                                    Modifier.padding(end = 8.dp).size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) MatrixGreen else Surface)
                                        .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { cfgPoster = value },
                                    contentAlignment = Alignment.Center,
                                ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 15.sp) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { cfgBg = !cfgBg },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                    .background(if (cfgBg) MatrixGreen else Surface)
                                    .border(1.dp, if (cfgBg) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (cfgBg) Text("✓", fontFamily = Mono, color = Black, fontSize = 13.sp) }
                            Spacer(Modifier.width(10.dp))
                            Text("Fanart background (Ken Burns)", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSaveConfig(cfgTitle, cfgCount, cfgAccent, cfgIcon, cfgPoster, cfgBg, cfgTheme); showConfig = false }) {
                    Text("Save", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showConfig = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun DashSessionRow(item: org.phioster.nexarr.model.JellySession, accent: Color) {
    val playing = item.nowPlaying.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(if (playing) item.nowPlaying else "${item.user} · idle", fontFamily = Mono, color = if (playing) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.user}${if (item.device.isNotBlank()) " · ${item.device}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (playing) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { item.progressPct }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
        }
    }
}

@Composable
private fun DashLineRow(title: String, subtitle: String, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashQueueRow(item: org.phioster.nexarr.model.ArrQueueItem, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.status} · ${(item.progress * 100).toInt()}%", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
    }
}

@Composable
private fun DashNzbRow(title: String, status: String, progress: Float, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$status · ${(progress * 100).toInt()}%", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
    }
}

@Composable
private fun DashDiscoverPoster(item: org.phioster.nexarr.model.SeerrDiscoverItem, width: androidx.compose.ui.unit.Dp = 96.dp, onClick: () -> Unit) {
    val h = width * 1.5f
    Column(Modifier.width(width).padding(end = 10.dp).clickable { onClick() }) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface))
        }
        Spacer(Modifier.height(4.dp))
        Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A one-tap action a Quick Buttons card can run against a service. */
private class QuickAction(val label: String, val run: suspend (DashboardViewModel, ServiceConfig) -> String)

private fun quickActionsFor(svc: ServiceConfig): List<QuickAction> = when (svc.type) {
    ServiceType.JELLYFIN -> listOf(QuickAction("Scan ${svc.label}") { vm, s -> vm.jellyfinScan(s) })
    ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> listOf(QuickAction("Search ${svc.label}") { vm, s -> vm.arrSearchAllItems(s, false) })
    ServiceType.PROWLARR -> listOf(QuickAction("Test ${svc.label}") { vm, s -> vm.prowlarrTestAll(s) })
    else -> emptyList()
}

@Composable
private fun BoxScope.KenBurnsBackground(url: String, config: ServiceConfig) {
    val t = rememberInfiniteTransition(label = "kb")
    val scale by t.animateFloat(1.08f, 1.28f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val dx by t.animateFloat(-18f, 18f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "x")
    val dy by t.animateFloat(12f, -12f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    JellyPoster(
        url, config,
        Modifier.matchParentSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = dx; translationY = dy },
        RoundedCornerShape(0.dp), ContentScale.Crop,
    )
    // Dark scrim so the monospace foreground stays readable over any art.
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Black.copy(alpha = 0.55f), Black.copy(alpha = 0.82f)))))
}

@Composable
private fun AddCardDialog(
    services: List<ServiceConfig>,
    onDismiss: () -> Unit,
    onAdd: (org.phioster.nexarr.model.CardType, String) -> Unit,
) {
    // Card types grouped by the service they pull from, only for service types the
    // user actually has configured — like nzb360's per-service "Add new card" sheet.
    // Service-less types (Section, Quick Buttons) live in a "Layout" group shown first.
    val groups = remember(services) {
        val byService = CardType.entries.groupBy { it.service }
        val layout = byService[null]?.let { listOf<Pair<ServiceType?, List<CardType>>>(null to it) } ?: emptyList()
        layout + byService.filterKeys { st -> st != null && services.any { it.type == st } }.toList()
    }
    fun keyOf(st: ServiceType?) = st?.name ?: "layout"
    var expandedKey by remember { mutableStateOf(groups.firstOrNull()?.let { keyOf(it.first) } ?: "") }
    var pendingType by remember { mutableStateOf<CardType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add card", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) {
                    Text("no services configured yet", fontFamily = Mono, color = ErrRed, fontSize = 13.sp)
                }
                groups.forEach { (svcType, types) ->
                    val accent = if (svcType != null) Color(svcType.accent) else MatrixGreen
                    val open = expandedKey == keyOf(svcType)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { expandedKey = if (open) "" else keyOf(svcType); pendingType = null }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("●", color = accent, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(svcType?.label ?: "Layout", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${types.size} cards", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (open) "▾" else "▸", fontFamily = Mono, color = MatrixGreen)
                    }
                    if (open) {
                        types.forEach { t ->
                            val cfgs = services.filter { it.type == svcType }
                            Text(
                                "› ${t.label}",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        when {
                                            svcType == null -> onAdd(t, "") // service-less card
                                            cfgs.size == 1 -> onAdd(t, cfgs.first().id)
                                            else -> pendingType = if (pendingType == t) null else t
                                        }
                                    }
                                    .padding(start = 20.dp, top = 7.dp, bottom = 7.dp),
                            )
                            // If several services of this type exist, pick which one.
                            if (pendingType == t && svcType != null) {
                                cfgs.forEach { c ->
                                    Text(
                                        "  → ${c.label}",
                                        fontFamily = Mono, color = accent, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { onAdd(t, c.id) }.padding(start = 40.dp, top = 6.dp, bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalSearchScreen(
    vm: DashboardViewModel,
    onBack: () -> Unit,
    onOpenService: (ServiceConfig) -> Unit,
) {
    val services by vm.services.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var term by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<org.phioster.nexarr.model.SearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun run() {
        val q = term.trim()
        if (q.isBlank()) return
        scope.launch {
            searching = true
            results = runCatching { vm.globalSearch(q) }.getOrDefault(emptyList())
            searching = false
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("search", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("title across all services", fontFamily = Mono) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { run() }) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MatrixGreen) }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val r = results
                when {
                    searching -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    r == null -> Text("type a title, then search", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    r.isEmpty() -> Text("no matches", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    else -> {
                        val grouped = r.groupBy { it.serviceId }
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            grouped.forEach { (sid, hits) ->
                                val head = hits.first()
                                item(key = "h_$sid") {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "${head.serviceLabel} · ${head.serviceType.label}".uppercase(),
                                        fontFamily = Mono, color = Color(head.serviceType.accent), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                                }
                                items(hits, key = { "${sid}_${it.title}_${it.subtitle}" }) { hit ->
                                    val cfg = services.firstOrNull { it.id == hit.serviceId }
                                    SearchResultRow(hit) { cfg?.let(onOpenService) }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: org.phioster.nexarr.model.SearchResult, onClick: () -> Unit) {
    val accent = Color(hit.serviceType.accent)
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hit.posterUrl.isNotBlank()) {
            AsyncImage(
                model = hit.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}

@Composable
private fun ServiceCard(
    config: ServiceConfig,
    status: ServiceStatus?,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up", fontFamily = Mono) }, onClick = { menuOpen = false; onMoveUp() })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down", fontFamily = Mono) }, onClick = { menuOpen = false; onMoveDown() })
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
    val context = LocalContext.current
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
    var mediaDetail by remember { mutableStateOf<org.phioster.nexarr.model.SeerrMediaDetail?>(null) }
    var mediaDetailLoading by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var users by remember { mutableStateOf<List<org.phioster.nexarr.model.SeerrUserInfo>?>(null) }
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
                                            mediaDetailLoading = true
                                            scope.launch {
                                                mediaDetail = runCatching { vm.seerrMediaDetailById(config, di.tmdbId, di.mediaType) }.getOrNull()
                                                    ?: org.phioster.nexarr.model.SeerrMediaDetail(di.tmdbId, di.title, di.year, di.mediaType, "", di.posterUrl, emptyList(), "", di.status, emptyList())
                                                mediaDetailLoading = false
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
        AlertDialog(
            onDismissRequest = { mediaDetail = null },
            containerColor = Surface,
            title = { Text(d.title, fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (d.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = d.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp)).background(Surface),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val chips = buildList {
                        addAll(d.facts)
                        if (d.status.isNotBlank()) add("status" to d.status)
                    }
                    chips.chunked(2).forEach { pair ->
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = SeerrSearchItem(d.tmdbId, d.title, d.year, d.mediaType)
                    mediaDetail = null
                    confirmItem = item
                }) { Text("Request", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { openExternal(context, seerrAppPackages, "${config.normalizedBaseUrl}${d.mediaType}/${d.tmdbId}") }) {
                        Text("Open in Seerr", fontFamily = Mono, color = accent)
                    }
                    TextButton(onClick = { mediaDetail = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
                }
            },
        )
    }

    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            containerColor = Surface,
            title = { Text("Users & stats", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text("REQUESTS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
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
                    Text("USERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JellyfinScreen(
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
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0=Now Playing, 1=Users, 2=Dashboard
    var sessions by remember { mutableStateOf<List<org.phioster.nexarr.model.JellySession>?>(null) }
    var users by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyUser>?>(null) }
    var dashInfo by remember { mutableStateOf<org.phioster.nexarr.model.JellySystemInfo?>(null) }
    var tasks by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyTask>?>(null) }
    var activity by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyActivity>?>(null) }
    var devices by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyDevice>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var messageFor by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }
    var confirmRestart by remember { mutableStateOf(false) }
    var libraries by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyLibrary>?>(null) }
    var editUser by remember { mutableStateOf<org.phioster.nexarr.model.JellyUser?>(null) }
    var showCreateUser by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var mediaViews by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>?>(null) }
    var mediaContents by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>?>(null) }
    var resumeItems by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>?>(null) }
    var latestItems by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>?>(null) }
    var browseStack by remember { mutableStateOf<List<org.phioster.nexarr.model.JellyMediaItem>>(emptyList()) }
    var mediaDetail by remember { mutableStateOf<org.phioster.nexarr.model.JellyMediaDetail?>(null) }

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
    suspend fun loadMediaFolder(parent: org.phioster.nexarr.model.JellyMediaItem) {
        listError = null
        mediaContents = null
        try {
            mediaContents = vm.jellyfinItemList(config, parent.id, if (parent.kind == "Season") parent.number else null)
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { listError = t.message }
    }
    LaunchedEffect(mode) { when (mode) { 0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard(); else -> {} } }
    LaunchedEffect(mode, browseStack) {
        if (mode == 3) { if (browseStack.isEmpty()) loadMediaHome() else loadMediaFolder(browseStack.last()) }
    }
    BackHandler(enabled = mode == 3 && (mediaDetail != null || browseStack.isNotEmpty())) {
        if (mediaDetail != null) mediaDetail = null else browseStack = browseStack.dropLast(1)
    }
    fun openMedia(it: org.phioster.nexarr.model.JellyMediaItem) {
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
                title = { Text(config.label, fontFamily = Mono, color = MatrixGreen) },
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
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Now Playing", fontFamily = Mono) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = mode == 3, onClick = { mode = 3 }, label = { Text("Media", fontFamily = Mono) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Users", fontFamily = Mono) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Dashboard", fontFamily = Mono) })
                    }
                    IconButton(onClick = {
                        scope.launch {
                            when (mode) {
                                0 -> loadSessions(); 1 -> loadUsers(); 2 -> loadDashboard()
                                else -> if (browseStack.isEmpty()) loadMediaHome() else loadMediaFolder(browseStack.last())
                            }
                        }
                    }) {
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
                                item {
                                    val si = dashInfo
                                    Spacer(Modifier.height(8.dp))
                                    Text(si?.serverName ?: "…", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("v${si?.version ?: "…"}${if (!si?.os.isNullOrBlank()) " · ${si!!.os}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text("SCHEDULED TASKS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                                val tk = tasks
                                when {
                                    tk == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    else -> items(tk) { t -> JellyTaskRow(t, accent) { scope.launch { actionMsg = vm.jellyfinRunTaskById(config, t.id); loadDashboard() } } }
                                }
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    Text("ACTIVITY LOG", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                                val ac = activity
                                when {
                                    ac == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    ac.isEmpty() -> item { Text("no activity", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                                    else -> items(ac) { e -> JellyActivityRow(e, accent) }
                                }
                                val dv = devices
                                if (!dv.isNullOrEmpty()) {
                                    item {
                                        Spacer(Modifier.height(12.dp))
                                        Text("DEVICES", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                    }
                                    items(dv) { d -> JellyDeviceRow(d, accent) }
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
                TextButton(onClick = { confirmRestart = false; scope.launch { actionMsg = vm.jellyfinRestartServer(config) } }) {
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
}

@Composable
private fun JellyPoster(url: String, config: ServiceConfig, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, scale: ContentScale) {
    val ctx = LocalContext.current
    val model = ImageRequest.Builder(ctx).data(url).apply {
        config.customHeaders.forEach { (k, v) -> addHeader(k, v) }
    }.build()
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = scale,
        modifier = modifier.clip(shape).background(Surface),
    )
}

@Composable
private fun JellyPosterCard(item: org.phioster.nexarr.model.JellyMediaItem, config: ServiceConfig, accent: Color, width: androidx.compose.ui.unit.Dp = 120.dp, onClick: () -> Unit) {
    val h = width * 1.5f
    Column(Modifier.width(width).padding(end = 10.dp).clickable { onClick() }) {
        Box {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.width(width).height(h), RoundedCornerShape(6.dp), ContentScale.Crop)
            } else {
                Box(Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface))
            }
            if (item.progressPct > 0.01f) {
                LinearProgressIndicator(
                    progress = { item.progressPct },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = accent, trackColor = Black.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JellyMediaRow(item: org.phioster.nexarr.model.JellyMediaItem, config: ServiceConfig, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.posterUrl.isNotBlank()) {
            JellyPoster(item.posterUrl, config, Modifier.width(46.dp).height(68.dp), RoundedCornerShape(4.dp), ContentScale.Crop)
        } else {
            Box(Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.isFolder) Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}

@Composable
private fun JellyUserDialog(
    user: org.phioster.nexarr.model.JellyUser,
    libraries: List<org.phioster.nexarr.model.JellyLibrary>?,
    onDismiss: () -> Unit,
    onSave: (admin: Boolean, disabled: Boolean, allowDownloads: Boolean, enableAll: Boolean, folders: List<String>) -> Unit,
    onResetPassword: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var admin by remember { mutableStateOf(user.admin) }
    var disabled by remember { mutableStateOf(user.disabled) }
    var allowDownloads by remember { mutableStateOf(user.allowDownloads) }
    var enableAll by remember { mutableStateOf(user.enableAllFolders) }
    var folders by remember { mutableStateOf(user.enabledFolders.toSet()) }
    var newPw by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(user.name, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                JellyToggle("Administrator", admin) { admin = it }
                JellyToggle("Enabled", !disabled) { disabled = !it }
                JellyToggle("Allow downloads", allowDownloads) { allowDownloads = it }
                JellyToggle("Access all libraries", enableAll) { enableAll = it }
                if (!enableAll) {
                    Spacer(Modifier.height(4.dp))
                    when {
                        libraries == null -> Text("loading libraries…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        libraries.isEmpty() -> Text("no libraries found", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        else -> libraries.forEach { lib ->
                            val checked = folders.contains(lib.id)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    folders = if (checked) folders - lib.id else folders + lib.id
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (checked) "[x]" else "[ ]", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(lib.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                Spacer(Modifier.height(6.dp))
                Text("RESET PASSWORD", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                Field("New password", newPw, isPassword = true) { newPw = it }
                TextButton(enabled = newPw.isNotBlank(), onClick = { val p = newPw; newPw = ""; onResetPassword(p) }) {
                    Text("Set password", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                if (!confirmDelete) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete user", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete for real?", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("Yes", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                        TextButton(onClick = { confirmDelete = false }) { Text("No", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(admin, disabled, allowDownloads, enableAll, folders.toList()) }) {
                Text("Save", fontFamily = Mono, color = MatrixGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
private fun JellyToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun JellyTaskRow(item: org.phioster.nexarr.model.JellyTask, accent: Color, onRun: () -> Unit) {
    val running = item.state.equals("Running", true)
    Column(Modifier.fillMaxWidth().clickable(enabled = !running) { onRun() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                if (running) "${item.progress}%" else if (item.state.isNotBlank()) "▶ run" else "",
                fontFamily = Mono,
                color = if (running) Color(0xFFFFAA00) else MatrixGreen,
                fontSize = 11.sp,
            )
        }
        if (item.lastResult.isNotBlank() && !running) {
            Spacer(Modifier.height(2.dp))
            Text(
                "last: ${item.lastResult}${if (item.lastRun.isNotBlank()) " · ${item.lastRun}" else ""}",
                fontFamily = Mono,
                color = if (item.lastResult.equals("Completed", true)) MatrixGreen.copy(alpha = 0.6f) else ErrRed,
                fontSize = 10.sp,
            )
        } else if (item.lastRun.isNotBlank() && !running) {
            Spacer(Modifier.height(2.dp))
            Text("last run ${item.lastRun}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
private fun JellyActivityRow(item: org.phioster.nexarr.model.JellyActivity, accent: Color) {
    val sevColor = when (item.severity.lowercase()) {
        "error", "fatal" -> ErrRed
        "warn", "warning" -> Color(0xFFFFAA00)
        else -> MatrixGreen.copy(alpha = 0.6f)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text("${item.date}${if (item.overview.isNotBlank()) " · ${item.overview}" else ""}", fontFamily = Mono, color = sevColor, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
private fun JellyDeviceRow(item: org.phioster.nexarr.model.JellyDevice, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.app.isNotBlank()) Text(item.app, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                if (item.user.isNotBlank()) append(item.user)
                if (item.lastActivity.isNotBlank()) { if (isNotEmpty()) append(" · "); append(item.lastActivity) }
            },
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
private fun JellySessionRow(
    item: org.phioster.nexarr.model.JellySession,
    accent: Color,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMessage: () -> Unit,
) {
    val playing = item.nowPlaying.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (playing) item.nowPlaying else "${item.user} · idle",
                fontFamily = Mono, color = if (playing) MatrixGreen else MatrixGreen.copy(alpha = 0.5f),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (playing) Text(if (item.paused) "paused" else "playing", fontFamily = Mono, color = if (item.paused) Color(0xFFFFAA00) else MatrixGreen, fontSize = 11.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(item.user)
                if (item.device.isNotBlank()) append(" · ${item.device}")
                if (playing && item.subtitle.isNotBlank()) append(" · ${item.subtitle}")
                if (!playing && item.lastActivity.isNotBlank()) append(" · ${item.lastActivity}")
            },
            fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (playing) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { item.progressPct }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
            if (item.canControl) {
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = onPlayPause) { Text(if (item.paused) "▶ play" else "❚❚ pause", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                    TextButton(onClick = onStop) { Text("■ stop", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                    TextButton(onClick = onMessage) { Text("✉ msg", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("no remote control", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}

@Composable
private fun JellyUserRow(item: org.phioster.nexarr.model.JellyUser, accent: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, fontFamily = Mono, color = if (item.disabled) MatrixGreen.copy(alpha = 0.4f) else MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (item.disabled) Text("disabled", fontFamily = Mono, color = ErrRed, fontSize = 10.sp)
            else if (item.admin) Text("admin", fontFamily = Mono, color = accent, fontSize = 10.sp)
        }
        if (item.lastActivity.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("last active ${item.lastActivity}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
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
    var confirmDelIndexer by remember { mutableStateOf<ProwlarrIndexerItem?>(null) }
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
                                            onDelete = { confirmDelIndexer = row },
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

    confirmDelIndexer?.let { ix ->
        AlertDialog(
            onDismissRequest = { confirmDelIndexer = null },
            containerColor = Surface,
            title = { Text("Delete indexer?", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("Remove \"${ix.name}\" from Prowlarr. This does not touch the connected apps.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { confirmDelIndexer = null; act({ vm.prowlarrDelete(config, ix.id) }, true) }) {
                    Text("Delete", fontFamily = Mono, color = ErrRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelIndexer = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
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
private fun ProwlarrIndexerRow(item: ProwlarrIndexerItem, accent: Color, onTest: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
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
            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono, color = ErrRed) }, onClick = { menu = false; onDelete() })
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
    var trackAlbum by remember { mutableStateOf<org.phioster.nexarr.model.ArrAlbum?>(null) }
    var tracks by remember { mutableStateOf<List<org.phioster.nexarr.model.ArrTrack>?>(null) }
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
                            trackAlbum = al; tracks = null
                            scope.launch { tracks = runCatching { vm.arrTracksOf(config, al.id) }.getOrDefault(emptyList()) }
                        }
                    }
                }
            }
        }
    }

    trackAlbum?.let { al ->
        AlertDialog(
            onDismissRequest = { trackAlbum = null },
            containerColor = Surface,
            title = { Text("${al.title}${if (al.year.isNotBlank()) " (${al.year})" else ""}", fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("${al.trackFileCount}/${al.trackCount} tracks", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    val tr = tracks
                    when {
                        tr == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        tr.isEmpty() -> Text("no tracks", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> tr.forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(t.trackNumber.padStart(2, ' '), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(t.title, fontFamily = Mono, color = if (t.hasFile) MatrixGreen else MatrixGreen.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (t.duration.isNotBlank()) Text(t.duration, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = al; trackAlbum = null
                    openReleases(movieId = null, episodeId = null, albumId = a.id, title = a.title)
                }) { Text("Search releases", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { trackAlbum = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
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

/** Known Android app packages that can display a Jellyfin server. */
private val jellyfinAppPackages = listOf("org.jellyfin.mobile", "dev.jdtech.jellyfin")

/** Known Android app packages for Overseerr/Jellyseerr. */
private val seerrAppPackages = listOf("dev.seerr.mobileapp")

/**
 * Open [webUrl] in the first installed app from [packages]; otherwise hand the URL to the
 * system, which routes it to an installed PWA (e.g. Seerr added to the home screen) or the
 * browser.
 */
private fun openExternal(context: android.content.Context, packages: List<String>, webUrl: String) {
    for (pkg in packages) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) { runCatching { context.startActivity(launch) }; return }
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
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
