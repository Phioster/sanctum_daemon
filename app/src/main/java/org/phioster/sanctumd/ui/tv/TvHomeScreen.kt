package org.phioster.sanctumd.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.jellyfinFavorites
import org.phioster.sanctumd.net.jellyfinLatest
import org.phioster.sanctumd.net.jellyfinLibraryViews
import org.phioster.sanctumd.net.jellyfinNextUp
import org.phioster.sanctumd.net.jellyfinResume
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/** Everything the home screen shows, fetched in one parallel pass. */
private data class TvHomeData(
    val resume: List<JellyMediaItem> = emptyList(),
    val nextUp: List<JellyMediaItem> = emptyList(),
    val latest: List<JellyMediaItem> = emptyList(),
    val favorites: List<JellyMediaItem> = emptyList(),
    val libraries: List<JellyMediaItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = resume.isEmpty() && nextUp.isEmpty() && latest.isEmpty() &&
            favorites.isEmpty() && libraries.isEmpty()
}

/**
 * The TV home screen: shelves, in the order someone sitting down actually wants them — carry on
 * with what was started, then the next episode waiting, then what is new.
 *
 * Every row is optional: an empty one renders nothing rather than an empty gap, so a music-only or
 * film-only server doesn't look broken.
 */
@Composable
internal fun TvHomeScreen(
    config: ServiceConfig,
    refreshTick: Int,
    onOpen: (JellyMediaItem) -> Unit,
    onSwitchServer: () -> Unit,
) {
    var data by remember { mutableStateOf(TvHomeData()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(config.id, refreshTick) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                // Each row is fetched independently: one failing endpoint (no Shows/NextUp on old
                // servers, no favourites) must not blank out the whole screen.
                val resume = async { runCatching { jellyfinResume(config) }.getOrDefault(emptyList()) }
                val nextUp = async { runCatching { jellyfinNextUp(config) }.getOrDefault(emptyList()) }
                val latest = async { runCatching { jellyfinLatest(config) }.getOrDefault(emptyList()) }
                val favorites = async { runCatching { jellyfinFavorites(config) }.getOrDefault(emptyList()) }
                val libraries = jellyfinLibraryViews(config) // the one that must work
                TvHomeData(resume.await(), nextUp.await(), latest.await(), favorites.await(), libraries)
            }
        }.onSuccess { data = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    // Park focus on the first poster once content arrives, so the remote has somewhere to start.
    LaunchedEffect(loading, data.isEmpty) {
        if (!loading && !data.isEmpty) runCatching { firstCardFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        TvTopBar(config.label, onSwitchServer)
        when {
            loading && data.isEmpty -> TvMessage("lade Bibliothek…", Modifier.padding(top = 60.dp))
            error != null && data.isEmpty -> TvMessage("Server nicht erreichbar — $error", Modifier.padding(top = 60.dp), error = true)
            data.isEmpty -> TvMessage("keine Medien gefunden", Modifier.padding(top = 60.dp))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
            ) {
                // The first non-empty row owns the initial focus.
                val focusOwner = when {
                    data.resume.isNotEmpty() -> "resume"
                    data.nextUp.isNotEmpty() -> "nextUp"
                    data.latest.isNotEmpty() -> "latest"
                    data.favorites.isNotEmpty() -> "favorites"
                    else -> "libraries"
                }
                item {
                    TvPosterRow("weiter schauen", data.resume, config, firstItemFocus = firstCardFocus.takeIf { focusOwner == "resume" }, onClick = onOpen)
                }
                item {
                    TvPosterRow("als nächstes", data.nextUp, config, firstItemFocus = firstCardFocus.takeIf { focusOwner == "nextUp" }, onClick = onOpen)
                }
                item {
                    TvPosterRow("neu hinzugefügt", data.latest, config, firstItemFocus = firstCardFocus.takeIf { focusOwner == "latest" }, onClick = onOpen)
                }
                item {
                    TvPosterRow("favoriten", data.favorites, config, firstItemFocus = firstCardFocus.takeIf { focusOwner == "favorites" }, onClick = onOpen)
                }
                item {
                    TvPosterRow(
                        "bibliotheken",
                        data.libraries,
                        config,
                        cardWidth = 168.dp,
                        firstItemFocus = firstCardFocus.takeIf { focusOwner == "libraries" },
                        onClick = onOpen,
                    )
                }
            }
        }
    }
}

/** Server name on the left, the escape hatch on the right. */
@Composable
internal fun TvTopBar(title: String, onSwitchServer: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = TvSidePad, end = TvSidePad, top = TvTopPad, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("sanctumd tv", color = MatrixGreen.copy(alpha = 0.45f), fontFamily = Mono, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(title, color = MatrixGreen, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (onSwitchServer != null) TvButton("Server wechseln", onClick = onSwitchServer)
    }
}
