package org.phioster.sanctumd.ui.arr

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
import org.phioster.sanctumd.model.ArrCollection
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment

/**
 * A film run as Radarr knows it, opened from the Jellyfin collection you are looking at.
 *
 * Jellyfin shows what you own; Radarr knows the whole run. Joined on the TMDB collection id both
 * sides already store, so the match is exact rather than by title — the failure mode a German
 * release name would otherwise produce.
 *
 * Adding uses the collection's own quality profile and root folder: Radarr has already decided
 * where films of this run belong, so there is nothing left to ask.
 */
@Composable
internal fun ArrCollectionDialog(
    vm: DashboardViewModel,
    tmdbCollectionId: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pair by remember { mutableStateOf<Pair<org.phioster.sanctumd.model.ServiceConfig, ArrCollection>?>(null) }
    var lookupDone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(tmdbCollectionId) {
        pair = vm.indexerServices().firstOrNull { it.type == ServiceType.RADARR }?.let { svc ->
            vm.arrCollectionOf(svc, tmdbCollectionId)?.let { svc to it }
        }
        lookupDone = true
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text(pair?.second?.title ?: "Collection", fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                val p = pair
                when {
                    !lookupDone -> Text("looking it up…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    p == null -> Text(
                        "Radarr does not track this collection. It appears once one of its films is in the library.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                    )
                    else -> {
                        val (svc, col) = p
                        val owned = col.movies.count { it.existing }
                        Text(
                            "$owned of ${col.movies.size} in the library",
                            fontFamily = Mono, color = accent, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        col.movies.forEach { m ->
                            val here = m.existing || m.tmdbId in added
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (here) AppIcons.Done else if (m.excluded) AppIcons.Cancel else AppIcons.Add,
                                        contentDescription = if (here) "in the library" else if (m.excluded) "excluded" else "not in the library",
                                        tint = if (here) MatrixGreen else accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m.title + if (m.year > 0) "  (${m.year})" else "",
                                        fontFamily = Mono,
                                        color = if (here) MatrixGreen else MatrixGreen.copy(alpha = 0.85f),
                                        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (m.excluded) {
                                        Text(
                                            "excluded in Radarr",
                                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 10.sp,
                                        )
                                    }
                                }
                                if (!here && !m.excluded) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                val msg = vm.arrAddFromCollection(svc, col, m, searchNow = true)
                                                busy = false
                                                if (!msg.startsWith("error")) added = added + m.tmdbId
                                                onResult("${m.title}: $msg")
                                            }
                                        },
                                    ) { Text("add", fontFamily = Mono, color = accent, fontSize = 12.sp) }
                                }
                            }
                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                        }
                        if (col.missing.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Adding uses this collection's own profile and folder (${col.rootFolderPath.substringAfterLast('/')}), and starts a search.",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
                            )
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
