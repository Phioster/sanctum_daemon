package org.phioster.sanctumd.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.jellyfinItemDetail
import org.phioster.sanctumd.net.jellyfinPlayableEpisode
import org.phioster.sanctumd.net.jellyfinSetFavorite
import org.phioster.sanctumd.net.jellyfinSetPlayed
import org.phioster.sanctumd.ui.jellyfin.JellyPoster
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Container kinds that get a "carry on watching" button instead of a plain play button. */
internal val FOLDER_KINDS = setOf("Series", "Season")

/**
 * One item: poster, facts, description, and the actions that belong on a remote control.
 *
 * "Play" is the first focused control on purpose — the overwhelmingly common intent is to press OK
 * once more and have the film start.
 */
@Composable
internal fun TvDetailScreen(
    config: ServiceConfig,
    itemId: String,
    fallbackTitle: String,
    refreshTick: Int,
    onPlay: (String, String) -> Unit,
    onBrowse: (String, String) -> Unit,
) {
    var detail by remember { mutableStateOf<JellyMediaDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyMsg by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // For a series or season: the episode the play button will start, resolved up front so the
    // button can name it instead of making the user guess where it lands.
    var upNext by remember(itemId) { mutableStateOf<JellyMediaItem?>(null) }
    var resolvingNext by remember(itemId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(itemId, refreshTick, reload) {
        error = null
        runCatching { jellyfinItemDetail(config, itemId) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    // A folder's play button needs a target episode before it can do anything.
    LaunchedEffect(itemId, refreshTick, reload, detail?.kind) {
        val kind = detail?.kind ?: return@LaunchedEffect
        if (kind !in FOLDER_KINDS) return@LaunchedEffect
        resolvingNext = true
        upNext = runCatching { jellyfinPlayableEpisode(config, itemId, isSeason = kind == "Season") }.getOrNull()
        resolvingNext = false
    }

    LaunchedEffect(detail != null) {
        if (detail != null) runCatching { playFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        TvTopBar(detail?.name ?: fallbackTitle)
        val d = detail
        when {
            error != null && d == null -> TvMessage("could not load — $error", Modifier.padding(top = 60.dp), error = true)
            d == null -> TvMessage("lade…", Modifier.padding(top = 60.dp))
            else -> Row(
                Modifier.fillMaxSize().padding(horizontal = TvSidePad),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                if (d.posterUrl.isNotBlank()) {
                    JellyPoster(
                        d.posterUrl,
                        config,
                        Modifier.width(210.dp).height(315.dp),
                        RoundedCornerShape(10.dp),
                        ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.width(210.dp).height(315.dp).background(Surface))
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (d.subtitle.isNotBlank()) {
                        Text(d.subtitle, color = MatrixGreen.copy(alpha = 0.65f), fontFamily = Mono, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(d.name, color = MatrixGreen, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    val facts = buildList {
                        d.facts.forEach { add("${it.first} ${it.second}") }
                        if (d.genres.isNotBlank()) add(d.genres)
                    }
                    if (facts.isNotEmpty()) {
                        Text(
                            facts.joinToString("  ·  "),
                            color = MatrixGreen.copy(alpha = 0.6f),
                            fontFamily = Mono,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (d.overview.isNotBlank()) {
                        Text(
                            d.overview,
                            color = MatrixGreen.copy(alpha = 0.85f),
                            fontFamily = Mono,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                        Spacer(Modifier.height(22.dp))
                    }

                    val isFolder = d.kind in FOLDER_KINDS
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (isFolder) {
                            // The point of this button: start watching without first drilling through
                            // seasons and episodes to find where you left off.
                            val next = upNext
                            TvButton(
                                when {
                                    next != null -> "▶  ${next.subtitle.ifBlank { next.name }}"
                                    resolvingNext -> "finding the episode…"
                                    else -> "no episode found"
                                },
                                focusRequester = playFocus,
                                enabled = next != null,
                            ) { next?.let { onPlay(it.id, it.name) } }
                            TvButton("Folgen") { onBrowse(d.id, d.name) }
                        } else {
                            TvButton("▶  abspielen", focusRequester = playFocus) { onPlay(d.id, d.name) }
                        }
                        TvButton(if (d.played) "als ungesehen markieren" else "als gesehen markieren") {
                            scope.launch {
                                busyMsg = null
                                runCatching { jellyfinSetPlayed(config, d.id, !d.played) }
                                    .onSuccess { reload++ }
                                    .onFailure { busyMsg = "did not work: ${it.message}" }
                            }
                        }
                        TvButton(if (d.favorite) "♥ Favorit entfernen" else "♡ Favorit") {
                            scope.launch {
                                busyMsg = null
                                runCatching { jellyfinSetFavorite(config, d.id, !d.favorite) }
                                    .onSuccess { reload++ }
                                    .onFailure { busyMsg = "did not work: ${it.message}" }
                            }
                        }
                    }
                    busyMsg?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(it, color = org.phioster.sanctumd.ui.theme.ErrRed, fontFamily = Mono, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
