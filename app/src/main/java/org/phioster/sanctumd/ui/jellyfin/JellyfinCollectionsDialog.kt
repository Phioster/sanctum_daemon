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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.JellyCollection
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The collections a title is in, toggled in place, plus a new one started from it. Each tap
 * goes to the server at once, so the list is re-read rather than trusted.
 */
@Composable
internal fun JellyfinCollectionsDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    target: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JellyCollection>?>(null) }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    suspend fun reload() {
        list = runCatching { vm.jellyfinCollectionList(config, target.id) }.getOrDefault(emptyList())
    }
    LaunchedEffect(target.id) { reload() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Collections", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(target.name, fontFamily = Mono, color = accent, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                val l = list
                when {
                    l == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    l.isEmpty() -> Text("no collections yet", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> l.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    onMessage(vm.jellyfinToggleCollection(config, c.id, target.id, add = !c.contains))
                                    reload()
                                    busy = false
                                }
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = c.contains,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = MatrixGreen.copy(alpha = 0.6f)),
                            )
                            Text(c.name, fontFamily = Mono, color = if (c.contains) accent else MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Field("New collection", newName) { newName = it }
                TextButton(enabled = newName.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        onMessage(vm.jellyfinNewCollection(config, newName, target.id))
                        newName = ""
                        reload()
                        busy = false
                    }
                }) { Text("create with this title", fontFamily = Mono, color = accent) }
            }
        },
        confirmButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
