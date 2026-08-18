package org.phioster.sanctumd.ui.jellyfin

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
import org.phioster.sanctumd.model.JellyIdentifyCandidate
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Re-pins an item to the right metadata entry.
 *
 * The search term starts as the current title but is editable, because the current title is
 * usually the problem: a German release name that matched nothing, or matched the wrong film.
 * Typing the original title is what makes the providers find it.
 */
@Composable
internal fun JellyfinIdentifyDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    detail: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onIdentified: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf(detail.name) }
    var year by remember { mutableStateOf(detail.facts.firstOrNull { it.first == "year" }?.second.orEmpty()) }
    var results by remember { mutableStateOf<List<JellyIdentifyCandidate>?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun search() {
        busy = true
        results = runCatching {
            vm.jellyfinIdentifySearch(config, detail.id, detail.kind, term.trim(), year.trim().toIntOrNull())
        }.getOrDefault(emptyList())
        busy = false
    }
    LaunchedEffect(detail.id) { search() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Identify", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Field("Title", term) { term = it }
                Spacer(Modifier.height(6.dp))
                Field("Year (optional)", year) { year = it }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { search() } },
                    enabled = term.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "searching…" else "Search", fontFamily = Mono, color = accent) }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (val list = results) {
                        null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (list.isEmpty()) {
                            Text(
                                "no candidates — try the original title instead of the release name",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                            )
                        } else list.forEach { c ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        busy = true
                                        scope.launch {
                                            val msg = vm.jellyfinIdentifyApply(config, detail.id, c.raw)
                                            busy = false
                                            onIdentified(msg)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    c.name + if (c.year > 0) " (${c.year})" else "",
                                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (c.provider.isNotBlank()) {
                                    Text(c.provider, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp)
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
