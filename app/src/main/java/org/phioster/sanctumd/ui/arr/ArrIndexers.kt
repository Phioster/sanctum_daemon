package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import org.phioster.sanctumd.model.ArrIndexerItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The indexers of one Servarr app, with the one repair that matters.
 *
 * Deliberately read-only apart from testing: Prowlarr owns these definitions and re-syncs them,
 * so adding or editing one here would only be overwritten. What cannot be done from Prowlarr is
 * clearing *this* app's own failure lockout, and that is what Test does.
 */
@Composable
internal fun ArrIndexersDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var indexers by remember { mutableStateOf<List<ArrIndexerItem>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        indexers = runCatching { vm.arrIndexersOf(config) }.getOrDefault(emptyList())
    }
    LaunchedEffect(config.id) { reload() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Indexers · ${config.label}", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when (val list = indexers) {
                    null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> if (list.isEmpty()) {
                        Text("no indexers configured", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    } else {
                        list.forEach { ix -> ArrIndexerRow(ix, accent) }
                    }
                }
                msg?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontFamily = Mono, color = accent, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        busy = true; msg = "testing…"
                        scope.launch {
                            msg = "${config.label}: ${vm.arrTestIndexers(config)}"
                            reload()
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Test all · ${config.label}", fontFamily = Mono, color = accent) }
                Spacer(Modifier.height(6.dp))
                // The reason this screen exists: a lockout usually hits Sonarr *and* Radarr, and
                // fixing one while the other stays blocked is the trap.
                OutlinedButton(
                    onClick = {
                        busy = true; msg = "testing all services…"
                        scope.launch {
                            msg = vm.arrRepairAllIndexers().joinToString("  ·  ") { "${it.first}: ${it.second}" }
                            reload()
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Repair every service", fontFamily = Mono, color = MatrixGreen) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

@Composable
private fun ArrIndexerRow(item: ArrIndexerItem, accent: Color) {
    val stateColor = when {
        item.failing -> ErrRed
        item.statusUnknown -> Color(0xFFFFAA00)
        else -> MatrixGreen
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.name, fontFamily = Mono, color = stateColor, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    item.failing -> "failing"
                    item.statusUnknown -> "state unknown"
                    else -> item.protocol
                },
                fontFamily = Mono, color = stateColor, fontSize = 11.sp,
            )
        }
        if (item.statusUnknown) {
            Spacer(Modifier.height(2.dp))
            Text(
                "health check unreachable — cannot tell",
                fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildList {
                add("prio ${item.priority}")
                if (item.enableRss) add("rss")
                if (item.enableAutomaticSearch) add("auto")
                if (item.enableInteractiveSearch) add("interactive")
            }.joinToString(" · "),
            fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}
