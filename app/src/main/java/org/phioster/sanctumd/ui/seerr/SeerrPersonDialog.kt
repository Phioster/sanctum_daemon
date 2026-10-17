package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.SeerrEpisode
import org.phioster.sanctumd.net.SeerrPerson
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/** An actor's page: who they are and what else they played in; a tap on a title opens it. */
@Composable
internal fun SeerrPersonDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    personId: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onOpen: (SeerrDiscoverItem) -> Unit,
) {
    var person by remember(personId) { mutableStateOf<SeerrPerson?>(null) }
    var error by remember(personId) { mutableStateOf<String?>(null) }
    var bioOpen by remember(personId) { mutableStateOf(false) }
    LaunchedEffect(personId) {
        runCatching { vm.seerrPersonOf(config, personId) }.onSuccess { person = it }.onFailure { error = it.message ?: "failed" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(person?.name ?: "…", fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                error?.let { Text("error: $it", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                val p = person ?: return@Column
                Row {
                    if (p.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = p.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(90.dp).height(130.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        if (p.knownFor.isNotBlank()) Text(p.knownFor, fontFamily = Mono, color = accent, fontSize = 12.sp)
                        if (p.born.isNotBlank()) Text("born ${p.born}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp)
                        if (p.died.isNotBlank()) Text("died ${p.died}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp)
                        if (p.place.isNotBlank()) Text(p.place, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
                if (p.biography.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        p.biography, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = if (bioOpen) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { bioOpen = !bioOpen },
                    )
                }
                // The poster row's own titles do the rest; [onOpen] swaps the dialog for that title.
                SeerrPosterRow("KNOWN FOR · ${p.credits.size}", p.credits, accent, onOpen)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** A season's episodes inside the request dialog; dates still ahead are marked. */
@Composable
internal fun SeerrEpisodeList(vm: DashboardViewModel, config: ServiceConfig, tmdbId: Int, season: Int) {
    var eps by remember(tmdbId, season) { mutableStateOf<List<SeerrEpisode>?>(null) }
    LaunchedEffect(tmdbId, season) { eps = runCatching { vm.seerrEpisodesOf(config, tmdbId, season) }.getOrDefault(emptyList()) }
    val today = remember { java.time.LocalDate.now().toString() }
    Column(Modifier.padding(start = 28.dp, bottom = 4.dp)) {
        val e = eps
        when {
            e == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
            e.isEmpty() -> Text("no episode list", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
            else -> e.forEach { ep ->
                val upcoming = ep.airDate.isBlank() || ep.airDate > today
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text("E%02d ".format(ep.number), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                    Text(ep.name, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        ep.airDate.ifBlank { "tba" }, fontFamily = Mono, fontSize = 10.sp,
                        color = if (upcoming) WarnAmber else MatrixGreen.copy(alpha = 0.5f),
                        fontWeight = if (upcoming) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
