package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.snapshotFlow
import org.phioster.sanctumd.ui.theme.SurfaceHi
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*

/**
 * One genre as a scrolling grid, paged as it goes. [visible] is false while a media detail
 * sits on top: the detail is composed earlier in the tree and would otherwise draw behind
 * this, but the grid keeps its scroll position and its loaded pages.
 */
@Composable
internal fun SeerrCategoryView(
    vm: DashboardViewModel,
    config: ServiceConfig,
    genreId: Int,
    catName: String,
    catKind: String,
    accent: Color,
    visible: Boolean,
    onBack: () -> Unit,
    onOpen: (org.phioster.sanctumd.model.SeerrDiscoverItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    var sort by remember(genreId, catKind) { mutableStateOf("popularity.desc") }
    var catItems by remember(genreId, catKind) { mutableStateOf<List<org.phioster.sanctumd.model.SeerrDiscoverItem>>(emptyList()) }
    var catPage by remember(genreId, catKind) { mutableStateOf(1) }
    var catLoading by remember(genreId, catKind) { mutableStateOf(false) }
    var catEnd by remember(genreId, catKind) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val newestSort = if (catKind == "tv") "first_air_date.desc" else "release_date.desc"
    LaunchedEffect(sort) {
        catItems = emptyList(); catPage = 1; catEnd = false; catLoading = true
        val first = runCatching { vm.seerrDiscoverGenreOf(config, catKind, genreId, sort, 1) }.getOrDefault(emptyList())
        catItems = first.distinctBy { it.tmdbId }; catLoading = false; if (first.isEmpty()) catEnd = true
    }
    LaunchedEffect(gridState, sort) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (!catLoading && !catEnd && catItems.isNotEmpty() && last >= catItems.size - 6) {
                catLoading = true
                val next = catPage + 1
                val more = runCatching { vm.seerrDiscoverGenreOf(config, catKind, genreId, sort, next) }.getOrDefault(emptyList())
                if (more.isEmpty()) catEnd = true else { catItems = (catItems + more).distinctBy { it.tmdbId }; catPage = next }
                catLoading = false
            }
        }
    }
    // Hidden (but state kept) while a media detail is open on top — the detail is composed
    // earlier in the tree, so it would otherwise draw behind this full-screen grid.
    if (visible) Box(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                Text(catName.uppercase(), fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (catKind == "tv") "series" else "movies", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("popularity.desc" to "Popular", newestSort to "Newest", "vote_average.desc" to "Top rated").forEach { (v, label) ->
                    val sel = sort == v
                    Text(
                        label, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) accent else SurfaceHi).clickable { sort = v }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(catItems, key = { it.tmdbId }) { di -> SeerrCategoryPoster(di) { onOpen(di) } }
                }
                if (catLoading && catItems.isEmpty()) {
                    CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.align(Alignment.Center))
                }
                if (!catLoading && catItems.isEmpty()) {
                    Text("nothing here", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
