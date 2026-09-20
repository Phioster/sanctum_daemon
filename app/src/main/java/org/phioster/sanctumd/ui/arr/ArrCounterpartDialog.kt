package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/**
 * What to look for on the Radarr/Sonarr side, stated in their terms rather than the calling
 * screen's.
 *
 * Jellyfin and Seerr name the same things differently ("Series" against "tv"), so each caller
 * translates its own vocabulary once, here, instead of the dialog learning both.
 *
 * @param scopeNote warns when the actions reach past what is on screen; empty when they do not.
 */
internal data class ArrCounterpartTarget(
    val serviceType: ServiceType,
    val tmdbId: String?,
    val tvdbId: String?,
    val scopeNote: String = "",
)

/**
 * The bridge from something you are looking at — a film in Jellyfin, a request in Seerr — to the
 * Radarr or Sonarr entry that manages it.
 *
 * The question that prompts it — "this copy is poor, get a better one" — can only be answered on
 * the other side, and answering it meant leaving the app, opening Radarr and searching for the
 * title by hand. The match is by provider id, so it is exact or absent; a title comparison is
 * what fails on German release names.
 */
@Composable
internal fun ArrCounterpartDialog(
    vm: DashboardViewModel,
    target: ArrCounterpartTarget,
    accent: Color,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pair by remember { mutableStateOf<Pair<ServiceConfig, ArrLibraryItem>?>(null) }
    var lookupDone by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var showFolders by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var queued by remember { mutableStateOf<List<org.phioster.sanctumd.model.ArrQueueItem>>(emptyList()) }

    LaunchedEffect(target) {
        pair = vm.indexerServices().firstOrNull { it.type == target.serviceType }?.let { svc ->
            runCatching { vm.arrFindByIds(svc, target.tmdbId, target.tvdbId) }
                .getOrNull()?.let { svc to it }
        }
        lookupDone = true
        // The counterpart is already resolved here, so asking what is downloading for it costs
        // one more call and answers the question that usually follows: "is it on its way?"
        pair?.let { (svc, item) -> queued = vm.arrQueueForLibraryItem(svc, item.id) }
    }

    fun run(label: String, call: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = call()
            busy = false
            onResult("$label: $msg")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text(pair?.first?.label ?: "Manage", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val p = pair
                when {
                    !lookupDone -> Text("looking it up…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    p == null -> Text(
                        "No matching entry — this title is not managed by Radarr or Sonarr.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                    )
                    else -> {
                        val (svc, item) = p
                        Text(
                            item.title + if (item.year > 0) " (${item.year})" else "",
                            fontFamily = Mono, color = accent, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        queued.forEach { q ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (q.blocked) "⚠  downloaded, waiting to be imported"
                                else "⬇  ${(q.progress * 100).toInt()}%  ·  ${q.status.lowercase()}",
                                fontFamily = Mono,
                                color = if (q.blocked) WarnAmber else MatrixGreen,
                                fontSize = 11.sp,
                            )
                            Text(
                                q.title,
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        target.scopeNote.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(Modifier.height(6.dp))
                            Text(note, fontFamily = Mono, color = WarnAmber, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Action("Search for a better version", busy) {
                            run(svc.label) { vm.arrLibSearch(svc, item.id) }
                        }
                        Action("Stop monitoring", busy) {
                            run(svc.label) { vm.arrSetLibraryMonitored(svc, item.id, false) }
                        }
                        Action(if (showFolders) "Move to folder" else "Move to folder…", busy) {
                            showFolders = true
                            if (folders == null) {
                                scope.launch { folders = runCatching { vm.arrRootFoldersList(svc) }.getOrDefault(emptyList()) }
                            }
                        }
                        if (showFolders) {
                            when (val f = folders) {
                                null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                                else -> f.forEach { path ->
                                    Text(
                                        path,
                                        fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable(enabled = !busy) { run(svc.label) { vm.arrMoveItem(svc, item.id, path) } }
                                            .padding(start = 12.dp, top = 7.dp, bottom = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

@Composable
private fun Action(label: String, busy: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onClick() }.padding(vertical = 9.dp),
    )
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
}