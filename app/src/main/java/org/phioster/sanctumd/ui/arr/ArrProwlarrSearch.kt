package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.prowlarr.ProwlarrReleaseRow
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The newznab top-level category to search for a given service.
 *
 * 0 means "everything": better to search too widely than to pick a category the indexer files
 * things differently under and come back empty, which is indistinguishable from "not there".
 */
internal fun prowlarrCategoryFor(type: ServiceType): Int = when (type) {
    ServiceType.RADARR -> 2000
    ServiceType.SONARR -> 5000
    ServiceType.LIDARR -> 3000
    else -> 0
}

/**
 * Searches Prowlarr **by text** for something the service is missing, and pushes a chosen
 * release straight back to it.
 *
 * The service's own automatic search goes out by id — so when an indexer carries the wrong id on
 * a release, or none, the right file is invisible to it however often you search. A text search
 * finds it. The term starts as the title but is editable, because the release is often named
 * something else entirely.
 */
@Composable
internal fun ArrProwlarrSearchDialog(
    vm: DashboardViewModel,
    prowlarr: ServiceConfig,
    target: ServiceConfig,
    initialQuery: String,
    accent: Color,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<ProwlarrRelease>?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun search() {
        busy = true
        results = runCatching { vm.prowlarrSearchList(prowlarr, query.trim(), prowlarrCategoryFor(target.type)) }
            .getOrDefault(emptyList())
        busy = false
    }
    LaunchedEffect(Unit) { search() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Search on ${prowlarr.label}", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Field("Search term", query) { query = it }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { search() } },
                    enabled = query.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "searching…" else "Search", fontFamily = Mono, color = accent) }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (val list = results) {
                        null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (list.isEmpty()) {
                            Text(
                                if (busy) "searching…" else "nothing found — try the original title",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                            )
                        } else list.forEach { rel ->
                            ProwlarrReleaseRow(
                                item = rel,
                                accent = accent,
                                // One target only: the service this search was opened from.
                                arrTargets = listOf(target),
                                onGrab = { scope.launch { onResult(vm.sendReleaseToArr(target, rel)) } },
                                onSendTo = { t -> scope.launch { onResult(vm.sendReleaseToArr(t, rel)) } },
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
