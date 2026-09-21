package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.phioster.sanctumd.model.ArrEpisode
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** What the caller gets back once the user has pinned a file to concrete episodes. */
internal data class EpisodeAssignment(
    val seriesId: Int,
    val seriesTitle: String,
    val episodeIds: List<Int>,
    val label: String, // e.g. "BLACK TORCH · S01E07"
)

/**
 * Picks a series and then its episodes for a file Sonarr could not identify itself.
 *
 * Two steps rather than one, because Sonarr needs the episode ids: a series on its own produces
 * an import command it silently ignores. Multi-select exists for the double episodes that
 * arrive as a single file.
 */
@Composable
internal fun SonarrAssignDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onDismiss: () -> Unit,
    onAssign: (EpisodeAssignment) -> Unit,
) {
    var series by remember { mutableStateOf<List<ArrLibraryItem>?>(null) }
    var chosenSeries by remember { mutableStateOf<ArrLibraryItem?>(null) }
    var episodes by remember { mutableStateOf<List<ArrEpisode>?>(null) }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        series = runCatching { vm.arrLibraryList(config) }.getOrDefault(emptyList())
    }
    LaunchedEffect(chosenSeries?.id) {
        val s = chosenSeries ?: return@LaunchedEffect
        episodes = null
        episodes = runCatching { vm.arrEpisodesOf(config, s.id) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                if (chosenSeries == null) "Assign to series" else "Pick episode(s)",
                fontFamily = Mono, color = MatrixGreen,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                val current = chosenSeries
                if (current == null) {
                    Field("Filter", query) { query = it }
                    Spacer(Modifier.height(8.dp))
                    when (val list = series) {
                        null -> Loading()
                        else -> {
                            val filtered = list
                                .filter { it.title.contains(query, ignoreCase = true) }
                                .sortedBy { it.title.lowercase() }
                            if (filtered.isEmpty()) Empty("no match")
                            else Column(Modifier.verticalScroll(rememberScrollState())) {
                                filtered.forEach { s ->
                                    Text(
                                        "${s.title}${if (s.year > 0) " (${s.year})" else ""}",
                                        fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { chosenSeries = s; picked = emptySet() }
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(current.title, fontFamily = Mono, color = accent, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    when (val eps = episodes) {
                        null -> Loading()
                        else -> if (eps.isEmpty()) Empty("no episodes") else {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                eps.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                                    .groupBy { it.seasonNumber }
                                    .forEach { (season, list) ->
                                        Text(
                                            if (season == 0) "Specials" else "Season $season",
                                            fontFamily = Mono, color = accent.copy(alpha = 0.8f),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                        )
                                        list.forEach { ep ->
                                            val on = ep.id in picked
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .clickable { picked = if (on) picked - ep.id else picked + ep.id }
                                                    .padding(vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    if (on) "[x] " else "[ ] ",
                                                    fontFamily = Mono, fontSize = 12.sp,
                                                    color = if (on) MatrixGreen else MatrixGreen.copy(alpha = 0.4f),
                                                )
                                                Text(
                                                    "S%02dE%02d · %s".format(ep.seasonNumber, ep.episodeNumber, ep.title),
                                                    fontFamily = Mono, fontSize = 12.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    // A file already on disk is the usual sign of picking the wrong one.
                                                    color = if (ep.hasFile) MatrixGreen.copy(alpha = 0.45f) else MatrixGreen,
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val current = chosenSeries
            if (current != null) {
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        val eps = (episodes ?: emptyList()).filter { it.id in picked }
                            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                        onAssign(
                            EpisodeAssignment(
                                seriesId = current.id,
                                seriesTitle = current.title,
                                episodeIds = eps.map { it.id },
                                label = current.title + " · " +
                                    eps.joinToString("+") { "S%02dE%02d".format(it.seasonNumber, it.episodeNumber) },
                            ),
                        )
                    },
                ) { Text("Assign (${picked.size})", fontFamily = Mono, color = MatrixGreen) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (chosenSeries != null) chosenSeries = null else onDismiss() },
            ) { Text(if (chosenSeries != null) "Back" else "Cancel", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

@Composable private fun Loading() =
    Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)

@Composable private fun Empty(text: String) =
    Text(text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
