package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/**
 * Confirms deleting a media item, and offers to remove its Radarr/Sonarr entry in the same go.
 *
 * Without that second half the deletion does not hold: the *arr app still has the entry, sees
 * the file missing on its next scan and re-downloads it while it stays monitored. The paired
 * entry is looked up by provider id, so it is either exactly right or absent.
 *
 * Only whole movies and whole series get the pairing — for a single episode or season, removing
 * the *arr entry would take the entire series with it.
 */
@Composable
internal fun JellyfinDeleteDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    detail: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onDeleted: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var counterpart by remember { mutableStateOf<Pair<ServiceConfig, ArrLibraryItem>?>(null) }
    var lookupDone by remember { mutableStateOf(false) }
    var alsoArr by remember { mutableStateOf(true) }
    var exclude by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val pairable = detail.kind == "Movie" || detail.kind == "Series"

    LaunchedEffect(detail.id) {
        if (!pairable) { lookupDone = true; return@LaunchedEffect }
        val wanted = if (detail.kind == "Movie") ServiceType.RADARR else ServiceType.SONARR
        counterpart = vm.indexerServices().firstOrNull { it.type == wanted }?.let { svc ->
            runCatching {
                vm.arrFindByIds(svc, detail.providerIds["Tmdb"], detail.providerIds["Tvdb"])
            }.getOrNull()?.let { svc to it }
        }
        lookupDone = true
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Delete", fontFamily = Mono, color = ErrRed) },
        text = {
            Column {
                Text(detail.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Removes the entry and its file from Jellyfin.",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.75f), fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    !lookupDone -> Text("checking Radarr/Sonarr…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    counterpart != null -> {
                        val (svc, item) = counterpart!!
                        Check(alsoArr, "also remove from ${svc.label}") { alsoArr = !alsoArr }
                        Text(
                            "matched: ${item.title}${if (item.year > 0) " (${item.year})" else ""}",
                            fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp,
                            modifier = Modifier.padding(start = 18.dp),
                        )
                        if (alsoArr) {
                            Check(exclude, "add to import exclusion list") { exclude = !exclude }
                            Text(
                                "stops it from ever being re-added",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
                                modifier = Modifier.padding(start = 18.dp),
                            )
                        }
                    }
                    pairable -> Text(
                        "No matching Radarr/Sonarr entry — nothing there will re-download it.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                    )
                    else -> Text(
                        "Radarr/Sonarr keep their entry: removing it would take the whole series.",
                        fontFamily = Mono, color = WarnAmber, fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lookupDone && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val messages = mutableListOf<String>()
                        messages += "Jellyfin: " + vm.jellyfinDelete(config, detail.id)
                        counterpart?.takeIf { alsoArr }?.let { (svc, item) ->
                            messages += "${svc.label}: " + vm.arrDeleteItem(svc, item.id, deleteFiles = true, addImportExclusion = exclude)
                        }
                        busy = false
                        onDeleted(messages.joinToString("  ·  "))
                    }
                },
            ) { Text(if (busy) "deleting…" else "Delete", fontFamily = Mono, color = ErrRed) }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

@Composable
private fun Check(on: Boolean, label: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (on) "[x] " else "[ ] ",
            fontFamily = Mono, fontSize = 12.sp,
            color = if (on) MatrixGreen else MatrixGreen.copy(alpha = 0.4f),
        )
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
    }
}