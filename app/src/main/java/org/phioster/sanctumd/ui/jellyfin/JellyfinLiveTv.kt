package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyChannel
import org.phioster.sanctumd.model.JellyLiveTv
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.AppIcons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon

/**
 * Everything the Live TV tab knows: its two lists and which of its confirmations is armed.
 *
 * Held together in one object so the tab can live in its own file — the screen around it used to
 * carry these six fields among fifty others, which is what made that file hard to work in.
 */
internal class JellyfinLiveTvState {
    var liveTv by mutableStateOf<JellyLiveTv?>(null)
    var channels by mutableStateOf<List<JellyChannel>?>(null)
    var showAddTuner by mutableStateOf(false)
    var showAddProvider by mutableStateOf(false)
    /** Tap-to-arm, tap-again-to-delete: the id whose remove control currently reads "remove?". */
    var confirmDeleteTuner by mutableStateOf<String?>(null)
    var confirmDeleteProvider by mutableStateOf<String?>(null)

    /** Reloads both lists. Returns an error message, or null when it worked. */
    suspend fun reload(vm: DashboardViewModel, config: ServiceConfig): String? {
        confirmDeleteTuner = null
        confirmDeleteProvider = null
        return try {
            liveTv = vm.jellyfinLiveTvStatus(config)
            channels = runCatching { vm.jellyfinChannelList(config) }.getOrDefault(emptyList())
            null
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            t.message ?: "failed"
        }
    }
}

@Composable
internal fun rememberJellyfinLiveTvState() = remember { JellyfinLiveTvState() }

/** Tuners, guide providers and channels, as rows in the screen's own list. */
internal fun LazyListScope.jellyfinLiveTvTab(
    st: JellyfinLiveTvState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onReload: () -> Unit,
) {
    val tv = st.liveTv
    item {
        Spacer(Modifier.height(8.dp))
        when {
            tv == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f))
            !tv.enabled -> Text("Live TV is not enabled on this server", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            else -> tv.services.forEach { s ->
                Text(s, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("TUNERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(
            "+ add tuner",
            fontFamily = Mono, color = accent, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable { st.showAddTuner = true }.padding(vertical = 6.dp),
        )
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
    val tuners = tv?.tuners
    when {
        tuners == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        tuners.isEmpty() -> item { Text("no tuners", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        else -> items(tuners) { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(t.type, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(t.url, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Two taps to remove: the first turns the cross into the question.
                    val removeTuner = Modifier.clickable {
                        if (st.confirmDeleteTuner == t.id) scope.launch { onMessage(vm.jellyfinTunerDelete(config, t.id)); onReload() }
                        else st.confirmDeleteTuner = t.id
                    }.padding(start = 12.dp)
                    if (st.confirmDeleteTuner == t.id) {
                        Text("remove?", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = removeTuner)
                    } else {
                        Icon(AppIcons.Cancel, contentDescription = "Remove", tint = ErrRed, modifier = removeTuner.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
            }
        }
    }
    item {
        Spacer(Modifier.height(12.dp))
        Text("GUIDE PROVIDERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(
            "+ add xmltv guide",
            fontFamily = Mono, color = accent, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable { st.showAddProvider = true }.padding(vertical = 6.dp),
        )
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
    val providers = tv?.providers
    when {
        providers == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        providers.isEmpty() -> item { Text("no guide providers", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        else -> items(providers) { p ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.type, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        if (p.path.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(p.path, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Two taps to remove: the first turns the cross into the question.
                    val removeProvider = Modifier.clickable {
                        if (st.confirmDeleteProvider == p.id) scope.launch { onMessage(vm.jellyfinProviderDelete(config, p.id)); onReload() }
                        else st.confirmDeleteProvider = p.id
                    }.padding(start = 12.dp)
                    if (st.confirmDeleteProvider == p.id) {
                        Text("remove?", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = removeProvider)
                    } else {
                        Icon(AppIcons.Cancel, contentDescription = "Remove", tint = ErrRed, modifier = removeProvider.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
            }
        }
    }
    val ch = st.channels
    item {
        Spacer(Modifier.height(12.dp))
        Text("CHANNELS${if (!ch.isNullOrEmpty()) " (${ch.size})" else ""}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
    }
    // Guide data expires daily — if no channel knows its current program, offer to run the
    // server's "Refresh Guide" task.
    if (!ch.isNullOrEmpty() && ch.none { it.nowPlaying.isNotBlank() }) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        val task = runCatching { vm.jellyfinTaskList(config) }.getOrNull()
                            ?.firstOrNull { it.name.contains("Guide", ignoreCase = true) }
                        onMessage(if (task == null) "guide task not found" else vm.jellyfinRunTaskById(config, task.id))
                    }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("no program data — guide may be stale · refresh guide", fontFamily = Mono, color = accent, fontSize = 12.sp)
            }
        }
    }
    when {
        ch == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        ch.isEmpty() -> item { Text("no channels", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
        else -> items(ch) { c -> JellyChannelRow(c, accent) }
    }
}

/** The two "add" dialogs the tab opens. */
@Composable
internal fun JellyfinLiveTvDialogs(
    st: JellyfinLiveTvState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onReload: () -> Unit,
) {
    if (st.showAddTuner) {
        var tunerType by remember { mutableStateOf("m3u") }
        var tunerUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { st.showAddTuner = false },
            containerColor = Surface,
            title = { Text("New tuner", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text("TYPE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row {
                        listOf("m3u" to "M3U playlist", "hdhomerun" to "HDHomeRun").forEach { (key, label) ->
                            FilterChip(
                                selected = tunerType == key,
                                onClick = { tunerType = key },
                                label = { Text(label, fontFamily = Mono, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    Field(if (tunerType == "m3u") "Playlist URL or file path" else "Device address", tunerUrl) { tunerUrl = it }
                }
            },
            confirmButton = {
                TextButton(enabled = tunerUrl.isNotBlank(), onClick = {
                    val ty = tunerType; val u = tunerUrl.trim(); st.showAddTuner = false
                    scope.launch { onMessage(vm.jellyfinTunerAdd(config, ty, u)); onReload() }
                }) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { st.showAddTuner = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (st.showAddProvider) {
        var providerPath by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { st.showAddProvider = false },
            containerColor = Surface,
            title = { Text("New XMLTV guide", fontFamily = Mono, color = MatrixGreen) },
            text = { Field("XMLTV URL or file path", providerPath) { providerPath = it } },
            confirmButton = {
                TextButton(enabled = providerPath.isNotBlank(), onClick = {
                    val p = providerPath.trim(); st.showAddProvider = false
                    scope.launch { onMessage(vm.jellyfinProviderAdd(config, p)); onReload() }
                }) { Text("Add", fontFamily = Mono, color = MatrixGreen) }
            },
            dismissButton = { TextButton(onClick = { st.showAddProvider = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
