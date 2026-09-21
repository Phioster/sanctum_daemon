package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
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

/** Version, health checks and disk space, straight from the service. */
@Composable
internal fun ArrSystemDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onDismiss: () -> Unit,
) {
    var arrSys by remember { mutableStateOf<org.phioster.sanctumd.model.ArrSystemInfo?>(null) }
    LaunchedEffect(Unit) { arrSys = runCatching { vm.arrSystemInfo(config) }.getOrNull() }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("System & health", fontFamily = Mono, color = MatrixGreen) },
            text = {
                val si = arrSys
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("version ${si?.version ?: "…"}", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("DISK", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.disks.isEmpty() -> Text("—", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> si.disks.forEach { (path, info) ->
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text(path, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(info, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("HEALTH", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    when {
                        si == null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        si.health.isEmpty() -> Text("all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
                        else -> si.health.forEach { (type, msg) ->
                            val c = if (type.equals("error", true)) ErrRed else WarnAmber
                            Text("• $msg", fontFamily = Mono, color = c, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
}

/**
 * The interactive release list for one wanted item: what the indexers have, and a confirmation
 * before grabbing. Closing it throws the list away -- it is a live search, not a cached one.
 */
@Composable
internal fun ArrReleasePicker(
    vm: DashboardViewModel,
    config: ServiceConfig,
    item: ArrMissingItem,
    accent: Color,
    onMessage: (String) -> Unit,
    onGrabbed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var releases by remember(item) { mutableStateOf<List<ArrRelease>?>(null) }
    var confirmGrab by remember(item) { mutableStateOf<ArrRelease?>(null) }
    LaunchedEffect(item) {
        releases = runCatching {
            when (config.type) {
                ServiceType.SONARR -> vm.arrReleasesFor(config, movieId = null, episodeId = item.id)
                ServiceType.LIDARR -> vm.arrReleasesFor(config, movieId = null, episodeId = null, albumId = item.id)
                else -> vm.arrReleasesFor(config, movieId = item.id, episodeId = null)
            }
        }.getOrElse { onMessage("error: ${it.message}"); onDismiss(); emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Releases", fontFamily = Mono, color = MatrixGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            val rs = releases
            Column(Modifier.heightIn(max = 460.dp)) {
                Text(item.title, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                when {
                    rs == null -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    rs.isEmpty() -> Text("no releases", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        rs.forEach { rel -> ArrReleaseRow(rel, accent) { confirmGrab = rel } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )

    confirmGrab?.let { rel ->
        AlertDialog(
            onDismissRequest = { confirmGrab = null },
            containerColor = Surface,
            title = { Text("Grab release", fontFamily = Mono, color = MatrixGreen) },
            text = { Text(rel.title, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    val r = rel
                    confirmGrab = null
                    onDismiss()
                    scope.launch { onMessage(vm.arrGrabRelease(config, r.guid, r.indexerId)); onGrabbed() }
                }) { Text("Grab", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { confirmGrab = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
