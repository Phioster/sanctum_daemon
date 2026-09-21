package org.phioster.sanctumd.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.jellyfinItems
import org.phioster.sanctumd.ui.theme.Black

/**
 * A library, series or season as a poster grid.
 *
 * One screen per level rather than the phone app's inline accordion: on a TV, drilling in and
 * pressing Back is the gesture people already know from every other streaming app.
 */
@Composable
internal fun TvBrowseScreen(
    config: ServiceConfig,
    parentId: String,
    title: String,
    refreshTick: Int,
    onOpen: (JellyMediaItem) -> Unit,
) {
    var items by remember { mutableStateOf<List<JellyMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(parentId, refreshTick) {
        loading = true
        error = null
        runCatching { jellyfinItems(config, parentId) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    LaunchedEffect(loading, items.isEmpty()) {
        if (!loading && items.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        TvTopBar(title)
        when {
            loading -> TvMessage("lade…", Modifier.padding(top = 60.dp))
            error != null -> TvMessage("could not load — $error", Modifier.padding(top = 60.dp), error = true)
            items.isEmpty() -> TvMessage("dieser Ordner ist leer", Modifier.padding(top = 60.dp))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = TvSidePad, end = TvSidePad, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    TvPosterCard(
                        item = item,
                        config = config,
                        width = 150.dp,
                        focusRequester = firstFocus.takeIf { index == 0 },
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}
