package org.phioster.sanctumd.ui.arr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLookupItem
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.DropdownField
import org.phioster.sanctumd.ui.common.FactGrid
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.common.PrimaryButton
import org.phioster.sanctumd.ui.common.SecondaryButton
import org.phioster.sanctumd.ServiceLogo
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Searching a Servarr service for a title that is not in the library yet.
 *
 * The service's own web UI shows a poster, a plot and a runtime before anything is added, and the
 * lookup answer carries all of it — this screen is where that arrives. Adding is one button on the
 * info screen rather than the only thing the list can do, so a title can simply be looked up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrLookupScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    initialTerm: String,
    onBack: () -> Unit,
    onOpenLibraryItem: (Int) -> Unit,
    onAdded: (String) -> Unit,
) {
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf(initialTerm) }
    var results by remember { mutableStateOf<List<ArrLookupItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<ArrLookupItem?>(null) }

    // The add dialog: only reached from the info screen, so it never fires on a mis-tap in the list.
    var adding by remember { mutableStateOf<ArrLookupItem?>(null) }
    var profiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var chosenProfile by remember { mutableStateOf<ArrProfile?>(null) }
    var chosenFolder by remember { mutableStateOf<String?>(null) }
    var metaProfiles by remember { mutableStateOf<List<ArrProfile>>(emptyList()) }
    var chosenMeta by remember { mutableStateOf<ArrProfile?>(null) }
    var monitored by remember { mutableStateOf(true) }

    fun runSearch() {
        val t = term.trim()
        if (t.isBlank()) return
        searching = true
        error = null
        scope.launch {
            runCatching { vm.arrLookupList(config, t) }
                .onSuccess { results = it }
                .onFailure { error = it.message; results = emptyList() }
            searching = false
        }
    }
    LaunchedEffect(Unit) { if (initialTerm.isNotBlank()) runSearch() }

    fun openAdd(item: ArrLookupItem) {
        adding = item
        monitored = true
        profiles = emptyList(); folders = emptyList(); metaProfiles = emptyList()
        chosenProfile = null; chosenFolder = null; chosenMeta = null
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

    val info = picked
    if (info != null) {
        BackHandler { picked = null }
        ArrLookupInfo(
            item = info,
            config = config,
            accent = accent,
            onBack = { picked = null },
            onAdd = { openAdd(info) },
            onOpenLibrary = { onOpenLibraryItem(info.libraryId) },
        )
    } else {
        BackHandler(onBack = onBack)
        Scaffold(
            containerColor = Black,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceLogo(config.type, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Search ${config.label}", fontFamily = Mono, color = MatrixGreen)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.Back, contentDescription = "Back", tint = MatrixGreen)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                Field("Search title", term) { term = it }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    if (searching) "searching…" else "search",
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Search,
                    accent = accent,
                    enabled = term.isNotBlank() && !searching,
                ) { runSearch() }
                Spacer(Modifier.height(12.dp))
                val res = results
                when {
                    error != null -> Text(
                        org.phioster.sanctumd.ui.services.friendlyStatusError(error),
                        fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                    )
                    res == null -> Text(
                        "look a title up — the plot, the runtime and the rating come with it",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp,
                    )
                    res.isEmpty() -> Text("no results", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(res) { hit -> ArrLookupRow(hit, accent) { picked = hit } }
                    }
                }
            }
        }
    }

    adding?.let { item ->
        AlertDialog(
            onDismissRequest = { adding = null },
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
                        val raw = item.raw
                        val p = chosenProfile!!
                        val f = chosenFolder!!
                        val m = monitored
                        val meta = chosenMeta?.id ?: 0
                        adding = null
                        scope.launch { onAdded(vm.arrAddItem(config, raw, p.id, f, m, meta)) }
                    },
                ) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

/** One hit in the result list: the same poster-and-two-lines row the Seerr search uses. */
@Composable
private fun ArrLookupRow(item: ArrLookupItem, accent: Color, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 8.dp),
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
            val line = buildList {
                item.year.takeIf { it > 0 }?.let { add(it.toString()) }
                item.facts.take(2).forEach { add(it.second) }
            }.joinToString(" · ")
            if (line.isNotBlank()) {
                Text(line, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (item.libraryId > 0) "in library" else "add",
            fontFamily = Mono,
            color = if (item.libraryId > 0) MatrixGreen else accent.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

/**
 * The info screen behind a hit: what the title is, before anything is downloaded for it.
 *
 * Laid out like the Seerr detail sheet — blurred backdrop, poster, facts, plot — because it
 * answers the same question and should not look like a different app.
 */
@Composable
private fun ArrLookupInfo(
    item: ArrLookupItem,
    config: ServiceConfig,
    accent: Color,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (item.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().blur(28.dp).background(Surface),
                    )
                } else {
                    Box(Modifier.matchParentSize().background(Surface))
                }
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Black.copy(alpha = 0.35f), Black.copy(alpha = 0.65f), Black)),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = MatrixGreen)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    if (item.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).background(Surface),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        val quick = buildList {
                            item.year.takeIf { it > 0 }?.let { add(it.toString()) }
                            item.facts.take(2).forEach { add(it.second) }
                        }.joinToString("  ·  ")
                        if (quick.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(quick, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (item.libraryId > 0) "in library" else "not added",
                            fontFamily = Mono, color = if (item.libraryId > 0) MatrixGreen else accent,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                if (item.libraryId > 0) {
                    SecondaryButton(
                        "open in ${config.label}",
                        Modifier.fillMaxWidth(),
                        icon = AppIcons.Open,
                        accent = accent,
                        onClick = onOpenLibrary,
                    )
                } else {
                    PrimaryButton(
                        "add to ${config.label}",
                        Modifier.fillMaxWidth(),
                        icon = AppIcons.Add,
                        accent = accent,
                        onClick = onAdd,
                    )
                }
                if (item.facts.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FactGrid(item.facts, accent)
                }
                if (item.genres.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(item.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                if (item.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(item.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
