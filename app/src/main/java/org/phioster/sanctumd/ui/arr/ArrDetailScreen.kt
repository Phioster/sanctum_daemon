package org.phioster.sanctumd.ui.arr

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrDetail
import org.phioster.sanctumd.model.ArrEpisode
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrDetailScreen(vm: DashboardViewModel, config: ServiceConfig, itemId: Int, onBack: () -> Unit) {
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    val isSonarr = config.type == ServiceType.SONARR
    val isLidarr = config.type == ServiceType.LIDARR
    var detail by remember { mutableStateOf<ArrDetail?>(null) }
    var episodes by remember { mutableStateOf<List<ArrEpisode>?>(null) }
    var albums by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrAlbum>?>(null) }
    var trackAlbum by remember { mutableStateOf<org.phioster.sanctumd.model.ArrAlbum?>(null) }
    var tracks by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrTrack>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var moveTargets by remember { mutableStateOf<List<String>?>(null) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    // release picker: null=closed; loading when releases==null
    var pickerOpen by remember { mutableStateOf(false) }
    var releases by remember { mutableStateOf<List<ArrRelease>?>(null) }
    var pickerTitle by remember { mutableStateOf("") }
    var confirmGrab by remember { mutableStateOf<ArrRelease?>(null) }
    var cast by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrCastMember>?>(null) }

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
    fun openReleases(movieId: Int?, episodeId: Int?, title: String, albumId: Int? = null, seriesId: Int? = null, seasonNumber: Int? = null) {
        pickerTitle = title; releases = null; pickerOpen = true
        scope.launch {
            releases = runCatching { vm.arrReleasesFor(config, movieId, episodeId, albumId, seriesId, seasonNumber) }.getOrElse {
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
                            DropdownMenuItem(text = { Text("Move to folder", fontFamily = Mono) }, onClick = {
                                barMenu = false
                                moveTargets = null
                                showMove = true
                                scope.launch { moveTargets = runCatching { vm.arrRootFoldersList(config) }.getOrDefault(emptyList()) }
                            })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; deleteFiles = false; confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        if (loadError != null) {
            Text(org.phioster.sanctumd.ui.services.friendlyStatusError(loadError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(padding).padding(16.dp))
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
                if (d != null) {
                    Spacer(Modifier.height(10.dp))
                    // Whole-item monitor toggle. For Lidarr this is essential: albums are only searched
                    // when the artist itself is monitored.
                    SecondaryButton(
                        if (d.monitored) "monitored" else "not monitored — tap to monitor",
                        Modifier.fillMaxWidth(),
                        icon = if (d.monitored) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        accent = if (d.monitored) Color(0xFFFFAA00) else accent,
                    ) {
                        scope.launch {
                            actionMsg = vm.arrSetLibraryMonitored(config, itemId, !d.monitored)
                            detail = runCatching { vm.arrDetailOf(config, itemId) }.getOrNull() ?: detail
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
                    else -> eps.groupBy { it.seasonNumber }.forEach { (season, seasonEps) ->
                        item(key = "season_$season") {
                            SonarrSeasonHeader(
                                season = season,
                                episodeCount = seasonEps.size,
                                haveCount = seasonEps.count { it.hasFile },
                                accent = accent,
                                onSearch = {
                                    openReleases(
                                        movieId = null, episodeId = null,
                                        seriesId = itemId, seasonNumber = season,
                                        title = if (season == 0) "Specials" else "Season $season",
                                    )
                                },
                            )
                        }
                        items(seasonEps, key = { it.id }) { ep ->
                            ArrEpisodeRow(
                                ep, accent,
                                onToggleMonitor = {
                                    scope.launch {
                                        vm.arrSetEpisodeMonitored(config, ep.id, !ep.monitored)
                                        episodes = vm.arrEpisodesOf(config, itemId)
                                    }
                                },
                            ) {
                                openReleases(movieId = null, episodeId = ep.id, title = "S%02dE%02d %s".format(ep.seasonNumber, ep.episodeNumber, ep.title))
                            }
                        }
                    }
                }
            }
            if (isLidarr) {
                val als = albums
                when {
                    als == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    als.isEmpty() -> item { Text("no albums", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    else -> items(als, key = { it.id }) { al ->
                        ArrAlbumRow(
                            al, accent,
                            onToggleMonitor = {
                                scope.launch {
                                    actionMsg = vm.arrSetAlbumMonitored(config, al.id, !al.monitored)
                                    albums = runCatching { vm.arrAlbumsOf(config, itemId) }.getOrNull() ?: albums
                                }
                            },
                            onQuickSearch = { scope.launch { actionMsg = vm.arrSearch(config, al.id) } },
                            onOpen = {
                                trackAlbum = al; tracks = null
                                scope.launch { tracks = runCatching { vm.arrTracksOf(config, al.id) }.getOrDefault(emptyList()) }
                            },
                        )
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

    if (showMove) {
        AlertDialog(
            onDismissRequest = { if (!moving) showMove = false },
            containerColor = Surface,
            title = { Text("Move to folder", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text(
                        "The files move with the entry. Nothing is re-downloaded.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    when (val folders = moveTargets) {
                        null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (folders.isEmpty()) {
                            Text("no root folders", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        } else folders.forEach { path ->
                            Text(
                                path,
                                fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !moving) {
                                        moving = true
                                        scope.launch {
                                            actionMsg = vm.arrMoveItem(config, itemId, path)
                                            detail = runCatching { vm.arrDetailOf(config, itemId) }.getOrNull() ?: detail
                                            moving = false
                                            showMove = false
                                        }
                                    }
                                    .padding(vertical = 9.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!moving) showMove = false }) {
                    Text(if (moving) "moving…" else "Cancel", fontFamily = Mono, color = MatrixGreen)
                }
            },
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
internal fun SonarrSeasonHeader(season: Int, episodeCount: Int, haveCount: Int, accent: Color, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (season == 0) "SPECIALS" else "SEASON $season",
            fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text("$haveCount/$episodeCount", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.weight(1f))
        // Interactive search for the whole season (season packs + episodes).
        IconButton(onClick = onSearch, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Search, contentDescription = "Search season", tint = MatrixGreen)
        }
    }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
}

@Composable
internal fun ArrEpisodeRow(item: ArrEpisode, accent: Color, onToggleMonitor: () -> Unit, onSearch: () -> Unit) {
    val c = if (item.hasFile) MatrixGreen else if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.4f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onSearch() }.padding(vertical = 6.dp)) {
                Text(
                    "S%02dE%02d  %s".format(item.seasonNumber, item.episodeNumber, item.title),
                    fontFamily = Mono, color = c, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(if (item.hasFile) "✓ downloaded" else item.airDate, fontFamily = Mono, color = c.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            IconButton(onClick = onToggleMonitor) {
                Icon(
                    if (item.monitored) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (item.monitored) "Unmonitor" else "Monitor",
                    tint = if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search episode", tint = accent)
            }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun ArrAlbumRow(
    item: org.phioster.sanctumd.model.ArrAlbum,
    accent: Color,
    onToggleMonitor: () -> Unit,
    onQuickSearch: () -> Unit,
    onOpen: () -> Unit,
) {
    val complete = item.trackCount > 0 && item.trackFileCount >= item.trackCount
    val c = if (complete) MatrixGreen else if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.4f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onOpen() }.padding(vertical = 6.dp)) {
                Text(
                    "${item.title}${if (item.year.isNotBlank()) " (${item.year})" else ""}",
                    fontFamily = Mono, color = c, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("${item.trackFileCount}/${item.trackCount} tracks", fontFamily = Mono, color = c.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            // Proper 48dp touch targets for monitor + search.
            IconButton(onClick = onToggleMonitor) {
                Icon(
                    if (item.monitored) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (item.monitored) "Unmonitor" else "Monitor",
                    tint = if (item.monitored) Color(0xFFFFAA00) else MatrixGreen.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onQuickSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search album", tint = accent)
            }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun ArrReleaseRow(item: ArrRelease, accent: Color, onGrab: () -> Unit) {
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
