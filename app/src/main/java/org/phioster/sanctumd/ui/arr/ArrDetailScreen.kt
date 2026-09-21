package org.phioster.sanctumd.ui.arr

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val st = rememberArrDetailState(itemId)
    LaunchedEffect(itemId) { st.load(vm, config, itemId, isSonarr, isLidarr) }
    fun openReleases(movieId: Int?, episodeId: Int?, title: String, albumId: Int? = null, seriesId: Int? = null, seasonNumber: Int? = null) {
        st.pickerTitle = title; st.releases = null; st.pickerOpen = true
        scope.launch {
            st.releases = runCatching { vm.arrReleasesFor(config, movieId, episodeId, albumId, seriesId, seasonNumber) }.getOrElse {
                st.actionMsg = "error: ${it.message}"; st.pickerOpen = false; emptyList()
            }
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(st.detail?.title ?: "…", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { st.barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen) }
                        DropdownMenu(expanded = st.barMenu, onDismissRequest = { st.barMenu = false }) {
                            DropdownMenuItem(text = { Text("Automatic search", fontFamily = Mono) }, onClick = {
                                st.barMenu = false; scope.launch { st.actionMsg = vm.arrLibSearch(config, itemId) }
                            })
                            if (config.type == ServiceType.RADARR) {
                                DropdownMenuItem(text = { Text("Interactive search", fontFamily = Mono) }, onClick = {
                                    st.barMenu = false; openReleases(movieId = itemId, episodeId = null, title = st.detail?.title ?: "")
                                })
                            }
                            DropdownMenuItem(text = { Text("Move to folder", fontFamily = Mono) }, onClick = {
                                st.barMenu = false
                                st.moveTargets = null
                                st.showMove = true
                                scope.launch { st.moveTargets = runCatching { vm.arrRootFoldersList(config) }.getOrDefault(emptyList()) }
                            })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { st.barMenu = false; st.deleteFiles = false; st.confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        if (st.loadError != null) {
            Text(org.phioster.sanctumd.ui.services.friendlyStatusError(st.loadError), fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                val d = st.detail
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
                        FactGrid(chips, accent)
                    }
                }
                if (d != null) {
                    Spacer(Modifier.height(10.dp))
                    // Whole-item monitor toggle. For Lidarr this is essential: albums are only searched
                    // when the artist itself is monitored.
                    SecondaryButton(
                        if (d.monitored) "monitored" else "not monitored — tap to monitor",
                        Modifier.fillMaxWidth(),
                        icon = if (d.monitored) AppIcons.Monitored else AppIcons.NotMonitored,
                        accent = if (d.monitored) WarnAmber else accent,
                    ) {
                        scope.launch {
                            st.actionMsg = vm.arrSetLibraryMonitored(config, itemId, !d.monitored)
                            st.detail = runCatching { vm.arrDetailOf(config, itemId) }.getOrNull() ?: st.detail
                        }
                    }
                }
                if (!d?.genres.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(d!!.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                st.actionMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 12.sp)
                }
                if (!d?.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(d!!.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                if (!st.availability.isEmpty) {
                    Spacer(Modifier.height(14.dp))
                    WatchProviderSection(st.availability, accent)
                }
                val cst = st.cast
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
                val eps = st.episodes
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
                                        st.episodes = vm.arrEpisodesOf(config, itemId)
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
                val als = st.albums
                when {
                    als == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    als.isEmpty() -> item { Text("no albums", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
                    else -> items(als, key = { it.id }) { al ->
                        ArrAlbumRow(
                            al, accent,
                            onToggleMonitor = {
                                scope.launch {
                                    st.actionMsg = vm.arrSetAlbumMonitored(config, al.id, !al.monitored)
                                    st.albums = runCatching { vm.arrAlbumsOf(config, itemId) }.getOrNull() ?: st.albums
                                }
                            },
                            onQuickSearch = { scope.launch { st.actionMsg = vm.arrSearch(config, al.id) } },
                            onOpen = {
                                st.trackAlbum = al; st.tracks = null
                                scope.launch { st.tracks = runCatching { vm.arrTracksOf(config, al.id) }.getOrDefault(emptyList()) }
                            },
                        )
                    }
                }
            }
        }
    }

    ArrDetailDialogs(st, vm, config, itemId, accent, onBack) { albumId, title ->
        openReleases(movieId = null, episodeId = null, albumId = albumId, title = title)
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
            Icon(AppIcons.Search, contentDescription = "Search season", tint = MatrixGreen)
        }
    }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
}

@Composable
internal fun ArrEpisodeRow(item: ArrEpisode, accent: Color, onToggleMonitor: () -> Unit, onSearch: () -> Unit) {
    val c = if (item.hasFile) MatrixGreen else if (item.monitored) WarnAmber else MatrixGreen.copy(alpha = 0.4f)
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
                    if (item.monitored) AppIcons.Monitored else AppIcons.NotMonitored,
                    contentDescription = if (item.monitored) "Unmonitor" else "Monitor",
                    tint = if (item.monitored) WarnAmber else MatrixGreen.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onSearch) {
                Icon(AppIcons.Search, contentDescription = "Search episode", tint = accent)
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
    val c = if (complete) MatrixGreen else if (item.monitored) WarnAmber else MatrixGreen.copy(alpha = 0.4f)
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
                    if (item.monitored) AppIcons.Monitored else AppIcons.NotMonitored,
                    contentDescription = if (item.monitored) "Unmonitor" else "Monitor",
                    tint = if (item.monitored) WarnAmber else MatrixGreen.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onQuickSearch) {
                Icon(AppIcons.Search, contentDescription = "Search album", tint = accent)
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
                color = if (item.approved) MatrixGreen.copy(alpha = 0.7f) else WarnAmber,
                fontSize = 10.sp,
            )
        }
        if (!item.approved && item.rejection.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(item.rejection, fontFamily = Mono, color = WarnAmber.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}
