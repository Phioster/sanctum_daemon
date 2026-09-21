package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.MediaRowStyle
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.service.DownloadService
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The dialogs that hang off one media item: which quality to download, which other device to
 * play it on, and the warning before marking a whole series watched.
 */
@Composable
internal fun JellyfinItemDialogs(
    ds: JellyfinDetailState,
    ps: JellyfinPeopleState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onWatched: (String, String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    ds.downloadQuality?.let { d ->
        // Original file vs. a transcoded, smaller copy. The server re-encodes on the fly for the
        // capped options, so the size shown on the card is an estimate until it finishes.
        AlertDialog(
            onDismissRequest = { ds.downloadQuality = null },
            containerColor = Surface,
            title = { Text("download quality", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    listOf(
                        0 to "original file",
                        8_000_000 to "1080p  ·  smaller",
                        4_000_000 to "720p  ·  much smaller",
                        1_500_000 to "480p  ·  smallest",
                    ).forEach { (bitrate, label) ->
                        Text(
                            label,
                            fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                ds.downloadQuality = null
                                DownloadService.enqueue(
                                    context, config.id, d.id, d.name, d.subtitle, d.posterUrl, 0L, "Video", bitrate,
                                )
                                onMessage("download queued")
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ds.downloadQuality = null }) { Text("cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    ds.cast?.let { d ->
        // Hand the item to another Jellyfin client. Only sessions that accept remote control and
        // aren't this phone are useful here.
        val targets = ps.sessions.orEmpty().filter { it.canControl }
        AlertDialog(
            onDismissRequest = { ds.cast = null },
            containerColor = Surface,
            title = { Text("play on…", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    if (ps.sessions == null) {
                        Text("loading devices…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 13.sp)
                    } else if (targets.isEmpty()) {
                        Text(
                            "No other device is available. A client has to be open and allow remote control.",
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 13.sp,
                        )
                    } else {
                        targets.forEach { t ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    ds.cast = null
                                    scope.launch { onMessage(vm.jellyfinPlayOn(config, t.id, d.id)) }
                                }.padding(vertical = 8.dp),
                            ) {
                                Text(t.device.ifBlank { t.client }, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                                Text(
                                    listOfNotNull(t.user.takeIf { it.isNotBlank() }, t.client.takeIf { it.isNotBlank() }).joinToString(" · "),
                                    fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ds.cast = null }) { Text("close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    ds.confirmWatched?.let { (wid, wname, want) ->
        AlertDialog(
            onDismissRequest = { ds.confirmWatched = null },
            containerColor = Surface,
            title = { Text(if (want) "Mark everything watched?" else "Mark everything unwatched?", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Text(
                    "This applies to every episode in \"$wname\".",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { ds.confirmWatched = null; onWatched(wid, wname, want) }) {
                    Text(if (want) "mark watched" else "mark unwatched", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { ds.confirmWatched = null }) { Text("cancel", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f)) } },
        )
    }
}

/** Which rows the media tab shows, and which libraries feed them. */
@Composable
internal fun JellyfinRowDialogs(
    bs: JellyfinBrowseState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    hiddenSet: Set<String>,
    mediaStyles: Map<String, MediaRowStyle>,
    libraryFilterOpen: Boolean,
    rowPickerOpen: Boolean,
    onLibraryFilter: (Boolean) -> Unit,
    onRowPicker: (Boolean) -> Unit,
    onConfigRow: (String) -> Unit,
) {
    if (libraryFilterOpen) {
        AlertDialog(
            onDismissRequest = { onLibraryFilter(false) },
            containerColor = Surface,
            title = { Text("show libraries", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    bs.views.orEmpty().forEach { view ->
                        val shown = view.id !in hiddenSet
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val next = if (shown) hiddenSet + view.id else hiddenSet - view.id
                                vm.setHiddenLibraries(config.id, next.toList())
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (shown) "[x]" else "[ ]", fontFamily = Mono, color = if (shown) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 14.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(view.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onLibraryFilter(false) }) { Text("done", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (rowPickerOpen) {
        AlertDialog(
            onDismissRequest = { onRowPicker(false) },
            containerColor = Surface,
            title = { Text("customize rows", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    listOf("resume" to "Continue Watching", "recent" to "Recently Added", "libraries" to "Libraries").forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onRowPicker(false); onConfigRow(key) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            if ((mediaStyles[key] ?: MediaRowStyle()).hidden) {
                                Text("hidden", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                    Row(
                        Modifier.fillMaxWidth().clickable { onRowPicker(false); onLibraryFilter(true) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Watched, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Show / hide libraries", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onRowPicker(false) }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
