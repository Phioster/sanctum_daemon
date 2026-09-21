package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.DownloadEntry
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.MediaRowStyle
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.service.DownloadService
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.player.PLAYABLE_VIDEO_KINDS
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/**
 * Everything the Media tab is looking at: the home rows, the folder the viewer has descended
 * into, and how that folder is sorted and filtered.
 *
 * The loads live here rather than in the screen so that "what we show" and "how we fetch it" sit
 * together, and the screen is left holding only the pieces that cross tabs.
 */
internal class JellyfinBrowseState {
    var views by mutableStateOf<List<JellyMediaItem>?>(null)
    var contents by mutableStateOf<List<JellyMediaItem>?>(null)
    var resume by mutableStateOf<List<JellyMediaItem>?>(null)
    var latest by mutableStateOf<List<JellyMediaItem>?>(null)
    var favorites by mutableStateOf<List<JellyMediaItem>?>(null)

    /** The folders descended into. Empty = the Media home. */
    var stack by mutableStateOf<List<JellyMediaItem>>(emptyList())

    // Sorting and "unwatched only" are server-side (a folder may hold more than one page);
    // [filter] just narrows what is already loaded.
    var sort by mutableStateOf("IsFolder,SortName")
    var desc by mutableStateOf(false)
    var unwatched by mutableStateOf(false)
    var filter by mutableStateOf("")

    /**
     * The album at the current level, when it is one. Music gets its own screen rather than the
     * generic folder list: an album is a record sleeve and a running order, not a directory.
     */
    var album by mutableStateOf<org.phioster.sanctumd.net.MusicAlbum?>(null)

    // Which folders are open at the current level, and their lazily loaded children.
    var expanded by mutableStateOf<Set<String>>(emptySet())
    var children by mutableStateOf<Map<String, List<JellyMediaItem>>>(emptyMap())

    /** Loads the Media home. Returns an error message, or null when it worked. */
    suspend fun loadHome(vm: DashboardViewModel, config: ServiceConfig): String? = guard {
        views = vm.jellyfinViews(config)
        resume = vm.jellyfinContinue(config)
        latest = vm.jellyfinRecent(config, null)
        favorites = runCatching { vm.jellyfinFavoriteList(config) }.getOrDefault(emptyList())
    }

    /** Loads one folder's contents. Returns an error message, or null when it worked. */
    suspend fun loadFolder(vm: DashboardViewModel, config: ServiceConfig, parent: JellyMediaItem): String? {
        contents = null
        album = null
        return guard {
            // An album is fetched twice on purpose: once as playable tracks (what the screen shows
            // and what the queue plays. They must not disagree) and once as generic items, because
            // the download chips need the fields only those carry.
            if (parent.kind == "MusicAlbum") album = vm.jellyfinAlbum(config, parent.id)
            contents = vm.jellyfinItemList(
                config, parent.id,
                seasonNumber = if (parent.kind == "Season") parent.number else null,
                sortBy = sort, descending = desc, unwatchedOnly = unwatched,
            )
        }
    }

    /** Reloads the current level, plus any open accordion sections, their episodes carry
     *  watched state too. */
    suspend fun reload(vm: DashboardViewModel, config: ServiceConfig): String? {
        val err = if (stack.isEmpty()) loadHome(vm, config) else loadFolder(vm, config, stack.last())
        contents.orEmpty().filter { it.id in expanded }.forEach { loadChildren(vm, config, it) }
        return err
    }

    suspend fun loadChildren(vm: DashboardViewModel, config: ServiceConfig, f: JellyMediaItem) {
        val kids = runCatching {
            vm.jellyfinItemList(config, f.id, if (f.kind == "Season") f.number else null)
        }.getOrDefault(emptyList())
        children = children + (f.id to kids)
    }

    private inline fun guard(body: () -> Unit): String? = try {
        body()
        null
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        t.message ?: "failed"
    }
}

@Composable
internal fun rememberJellyfinBrowseState() = remember { JellyfinBrowseState() }

/**
 * What a tap on the Media tab can do. Bundled because both halves of the tab need the same
 * handful, and threading seven lambdas through two signatures reads worse than one object.
 */
internal class JellyfinMediaActions(
    val open: (JellyMediaItem) -> Unit,
    /** Returns whether the change was applied now (false = a confirmation is pending). */
    val setWatched: (JellyMediaItem, Boolean) -> Boolean,
    val play: (JellyMediaItem) -> Unit,
    val playDownload: (DownloadEntry) -> Unit,
    val toggleFolder: (JellyMediaItem) -> Unit,
    val manageDownloads: () -> Unit,
    val message: (String) -> Unit,
)

/** The Media home: offline downloads, a hero, the poster rows and the library tiles. */
internal fun LazyListScope.jellyfinMediaHome(
    st: JellyfinBrowseState,
    config: ServiceConfig,
    accent: Color,
    context: android.content.Context,
    downloads: Map<String, DownloadEntry>,
    hiddenSet: Set<String>,
    mediaStyles: Map<String, MediaRowStyle>,
    listError: String?,
    act: JellyfinMediaActions,
) {
    val myDownloads = downloads.values
        .filter { it.serverId == config.id }
        .sortedByDescending { it.addedAt }
    if (myDownloads.isNotEmpty()) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DOWNLOADS  ·  offline", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .clickable { act.manageDownloads() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("manage", fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                myDownloads.forEach { e ->
                    DownloadCard(
                        entry = e, accent = accent,
                        onPlay = { if (e.done) act.playDownload(e) },
                        onDelete = { DownloadService.delete(context, e.itemId) },
                    )
                }
            }
        }
    }
    if (listError != null) {
        // Offline / server unreachable: downloads above still play; the rest needs the server.
        if (myDownloads.isEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("nothing downloaded for offline use", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("server unreachable, showing downloads only", fontFamily = Mono, color = ErrRed.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    } else {
        val res = st.resume
        val lat = st.latest
        // Hero: the top continue-watching item, else the newest addition.
        val hero = res?.firstOrNull() ?: lat?.firstOrNull()
        if (hero != null) {
            item {
                Spacer(Modifier.height(10.dp))
                MediaHero(
                    hero, config, accent,
                    onSetWatched = { want -> act.setWatched(hero, want) },
                    onPlay = {
                        if (hero.kind in PLAYABLE_VIDEO_KINDS) {
                            act.play(hero)
                        } else {
                            act.open(hero)
                        }
                    },
                    onOpen = { act.open(hero) },
                )
            }
        }
        val sResume = mediaStyles["resume"] ?: MediaRowStyle()
        val sRecent = mediaStyles["recent"] ?: MediaRowStyle()
        val sLibs = mediaStyles["libraries"] ?: MediaRowStyle()
        fun styleAccent(argb: Long) = if (argb != 0L) Color(argb) else accent
        if (!res.isNullOrEmpty() && !sResume.hidden) {
            item {
                Spacer(Modifier.height(16.dp))
                MediaSectionHeader("CONTINUE WATCHING", styleAccent(sResume.accent))
                Spacer(Modifier.height(8.dp))
                MediaPosterRow(res, config, styleAccent(sResume.accent), sResume, act.setWatched) { act.open(it) }
            }
        }
        val favs = st.favorites
        if (!favs.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                MediaSectionHeader("FAVORITES", accent)
                Spacer(Modifier.height(8.dp))
                MediaPosterRow(favs, config, accent, MediaRowStyle(), act.setWatched) { act.open(it) }
            }
        }
        if (!lat.isNullOrEmpty() && !sRecent.hidden) {
            item {
                Spacer(Modifier.height(16.dp))
                MediaSectionHeader("RECENTLY ADDED", styleAccent(sRecent.accent))
                Spacer(Modifier.height(8.dp))
                MediaPosterRow(lat, config, styleAccent(sRecent.accent), sRecent, act.setWatched) { act.open(it) }
            }
        }
        if (!sLibs.hidden) {
            item {
                Spacer(Modifier.height(16.dp))
                MediaSectionHeader("LIBRARIES", styleAccent(sLibs.accent))
                Spacer(Modifier.height(8.dp))
            }
        }
        if (!sLibs.hidden) {
            val v = st.views?.filterNot { it.id in hiddenSet }
            when {
                st.views == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                v.isNullOrEmpty() -> item { Text(if (hiddenSet.isEmpty()) "no libraries" else "all libraries hidden", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                else -> {
                    // Libraries as a 2-per-row grid of landscape tiles.
                    v.chunked(2).forEachIndexed { idx, pair ->
                        item(key = "librow-$idx") {
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { lib -> MediaLibraryTile(lib, config, Modifier.weight(1f)) { act.open(lib) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** One folder level: its chips, its sort/filter controls and its contents. */
internal fun LazyListScope.jellyfinFolderLevel(
    st: JellyfinBrowseState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    context: android.content.Context,
    scope: CoroutineScope,
    downloads: Map<String, DownloadEntry>,
    act: JellyfinMediaActions,
    playingId: String,
) {
    val here = st.stack.last()
    // Music leaves the generic folder machinery here: an album is a sleeve and a running order,
    // and the sort chips, the unwatched filter and the drill-in rows all mean nothing to it.
    if (here.kind == "MusicAlbum") {
        jellyfinAlbumLevel(
            album = st.album, contents = st.contents, vm = vm, config = config, accent = accent,
            context = context, downloads = downloads, act = act, here = here, playingId = playingId,
            onBack = { st.stack = st.stack.dropLast(1) },
            onScan = { scope.launch { act.message(vm.jellyfinScanLibrary(config, here.id)) } },
        )
        return
    }
    item {
        Spacer(Modifier.height(8.dp))
        BrowseChip("‹ back", accent) { st.stack = st.stack.dropLast(1) }
        Spacer(Modifier.height(10.dp))
        Text(here.name, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (here.kind == "MusicAlbum") {
                BrowseChip("play album", MatrixGreen, AppIcons.Play) {
                    // In the view model's scope, not this screen's: see playJellyfinTrack.
                    vm.playJellyfinAlbum(config, here.id)
                }
                val toGetAudio = st.contents.orEmpty().filter { !it.isFolder && it.kind == "Audio" && downloads[it.id]?.done != true }
                if (toGetAudio.isNotEmpty()) {
                    BrowseChip("album (${toGetAudio.size})", MatrixGreen, AppIcons.Download) {
                        toGetAudio.forEach { t -> DownloadService.enqueue(context, config.id, t.id, t.name, t.subtitle, t.posterUrl, 0L, "Audio") }
                        act.message("queued ${toGetAudio.size} downloads")
                    }
                }
            }
            val nextUnwatched = st.contents.orEmpty().filter {
                !it.isFolder && !it.played && it.kind in PLAYABLE_VIDEO_KINDS && downloads[it.id]?.done != true
            }.take(3)
            if (nextUnwatched.size > 1) {
                BrowseChip("next ${nextUnwatched.size} unwatched", MatrixGreen, AppIcons.Download) {
                    nextUnwatched.forEach { ep -> DownloadService.enqueue(context, config.id, ep.id, ep.name, ep.subtitle, ep.posterUrl, 0L) }
                    act.message("queued ${nextUnwatched.size} downloads")
                }
            }
            val toGet = st.contents.orEmpty().filter { !it.isFolder && it.kind in PLAYABLE_VIDEO_KINDS && downloads[it.id]?.done != true }
            if (toGet.isNotEmpty()) {
                BrowseChip("all (${toGet.size})", MatrixGreen, AppIcons.Download) {
                    toGet.forEach { ep -> DownloadService.enqueue(context, config.id, ep.id, ep.name, ep.subtitle, ep.posterUrl, 0L) }
                    act.message("queued ${toGet.size} downloads")
                }
            }
            BrowseChip("scan", MatrixGreen.copy(alpha = 0.85f), AppIcons.Refresh) { scope.launch { act.message(vm.jellyfinScanLibrary(config, here.id)) } }
        }
        Spacer(Modifier.height(8.dp))
        // Sort + filter. Sorting and "unwatched only" are server-side
        // (the folder may hold more than one page); the text box just
        // narrows what's already loaded.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "IsFolder,SortName" to "name",
                "DateCreated" to "added",
                "PremiereDate" to "released",
                "CommunityRating" to "rating",
            ).forEach { (key, label) ->
                val on = st.sort == key
                Text(
                    if (on) "$label ${if (st.desc) "↓" else "↑"}" else label,
                    fontFamily = Mono, fontSize = 12.sp,
                    color = if (on) Black else MatrixGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MatrixGreen else Color.Transparent)
                        .border(1.dp, MatrixGreen.copy(alpha = if (on) 0f else 0.3f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (on) st.desc = !st.desc
                            else { st.sort = key; st.desc = key != "IsFolder,SortName" }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(
                "unwatched",
                fontFamily = Mono, fontSize = 12.sp,
                color = if (st.unwatched) Black else MatrixGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (st.unwatched) MatrixGreen else Color.Transparent)
                    .border(1.dp, MatrixGreen.copy(alpha = if (st.unwatched) 0f else 0.3f), RoundedCornerShape(6.dp))
                    .clickable { st.unwatched = !st.unwatched }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = st.filter,
            onValueChange = { st.filter = it },
            placeholder = { Text("filter…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 13.sp) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MatrixGreen.copy(alpha = 0.6f),
                unfocusedBorderColor = MatrixGreen.copy(alpha = 0.25f),
                cursorColor = MatrixGreen,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
    }
    val m = st.contents?.let { list ->
        if (st.filter.isBlank()) list
        else list.filter { it.name.contains(st.filter, ignoreCase = true) }
    }
    when {
        m == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
        m.isEmpty() -> item { Text("empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp)) }
        // Audio tracks read better as a list; everything else as a 3-column poster grid.
        m.any { it.kind == "Audio" } -> items(m) { it2 -> JellyMediaRow(it2, config, accent, { want -> act.setWatched(it2, want) }) { act.open(it2) } }
        // Albums open their own screen; a list of them wants square covers, not film posters.
        m.all { it.isFolder && it.kind == "MusicAlbum" } ->
            items(m, key = { "alb-${it.id}" }) { a -> AlbumListRow(a, config, accent) { act.open(a) } }
        // Seasons still expand in place instead of forcing a drill-in.
        m.all { it.isFolder && it.kind == "Season" } ->
            items(m, key = { "acc-${it.id}" }) { f ->
                ExpandableFolderRow(
                    folder = f,
                    children = st.children[f.id],
                    config = config,
                    accent = accent,
                    expanded = f.id in st.expanded,
                    onToggle = { act.toggleFolder(f) },
                    onOpenFolder = { act.open(f) },
                    onOpenChild = { act.open(it) },
                    onSetWatched = act.setWatched,
                )
            }
        else -> {
            m.chunked(3).forEachIndexed { idx, rowItems ->
                item(key = "browserow-$idx") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { it2 -> MediaGridCard(it2, config, accent, Modifier.weight(1f), { want -> act.setWatched(it2, want) }) { act.open(it2) } }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
