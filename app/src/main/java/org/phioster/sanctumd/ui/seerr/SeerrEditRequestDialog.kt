package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.SeerrRequestDetail
import org.phioster.sanctumd.model.SeerrSeason
import org.phioster.sanctumd.model.SeerrServiceOptions
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * A pending request, changed before it is approved: the quality profile, the folder (e.g. the
 * private archive) and, for a series, which seasons. What Seerr's own "edit request" dialog does.
 */
@Composable
internal fun SeerrEditRequestDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    detail: SeerrRequestDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isTv = detail.mediaType == "tv"
    var options by remember { mutableStateOf<SeerrServiceOptions?>(null) }
    var seasons by remember { mutableStateOf<List<SeerrSeason>?>(if (isTv) null else emptyList()) }
    var profileId by remember { mutableStateOf(detail.profileId) }
    var folder by remember { mutableStateOf(detail.rootFolderPath) }
    var chosenSeasons by remember { mutableStateOf(detail.seasons.toSet()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        options = runCatching { vm.seerrOptionsFor(config, detail.mediaType) }.getOrNull()
        if (isTv && detail.tmdbId > 0) seasons = runCatching { vm.seerrSeasonsOf(config, detail.tmdbId) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Edit request", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                Text(detail.title, fontFamily = Mono, color = accent, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val o = options
                if (o == null) {
                    Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                } else {
                    Text("QUALITY", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Choice("server default", profileId == null, accent) { profileId = null }
                    o.profiles.forEach { p -> Choice(p.name, profileId == p.id, accent) { profileId = p.id } }
                    Spacer(Modifier.height(8.dp))
                    Text("FOLDER", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Choice("server default", folder == null, accent) { folder = null }
                    o.rootFolders.forEach { f -> Choice(f.path, folder == f.path, accent) { folder = f.path } }
                }
                if (isTv) {
                    Spacer(Modifier.height(8.dp))
                    Text("SEASONS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    val ss = seasons
                    when {
                        ss == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> ss.forEach { s ->
                            val on = s.seasonNumber in chosenSeasons
                            Row(
                                Modifier.fillMaxWidth().clickable { chosenSeasons = if (on) chosenSeasons - s.seasonNumber else chosenSeasons + s.seasonNumber },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { chosenSeasons = if (it) chosenSeasons + s.seasonNumber else chosenSeasons - s.seasonNumber },
                                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = MatrixGreen.copy(alpha = 0.6f)),
                                )
                                Text("${s.name} · ${s.episodeCount} ep.", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                // A series request without seasons is not a request; Seerr would reject it.
                enabled = !busy && options != null && (!isTv || chosenSeasons.isNotEmpty()),
                onClick = {
                    busy = true
                    scope.launch {
                        val msg = vm.seerrUpdateReq(
                            config, detail,
                            serverId = detail.serverId ?: options?.serverId,
                            profileId = profileId,
                            rootFolder = folder,
                            seasons = chosenSeasons.toList(),
                        )
                        busy = false
                        onDone(msg)
                    }
                },
            ) { Text(if (busy) "…" else "Save", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selected) AppIcons.Selected else AppIcons.NotSelected, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = Mono, color = if (selected) accent else MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
