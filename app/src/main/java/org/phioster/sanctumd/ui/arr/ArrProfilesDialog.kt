package org.phioster.sanctumd.ui.arr

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
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Lists the quality profiles and can add an unrestricted copy of one.
 *
 * Read-only apart from that: editing an existing profile is deliberately not offered. A profile
 * managed by Recyclarr would have the edit silently reverted on its next sync, leaving something
 * that works some days and not others, far worse than having no button.
 */
@Composable
internal fun ArrProfilesDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<ArrProfile>?>(null) }
    var cloneOf by remember { mutableStateOf<ArrProfile?>(null) }
    var newName by remember { mutableStateOf("Any") }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        profiles = runCatching { vm.arrProfilesList(config) }.getOrDefault(emptyList())
    }
    LaunchedEffect(config.id) { reload() }

    val source = cloneOf
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = {
            Text(
                if (source == null) "Quality profiles" else "Unrestricted copy",
                fontFamily = Mono, color = MatrixGreen,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (source == null) {
                    when (val list = profiles) {
                        null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (list.isEmpty()) {
                            Text("no profiles", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        } else list.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { cloneOf = p; newName = "Any" }.padding(vertical = 9.dp),
                            ) {
                                Text(
                                    p.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Text("copy", fontFamily = Mono, color = accent, fontSize = 11.sp)
                            }
                            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tap a profile to create an unrestricted copy: every quality allowed and no " +
                            "custom-format score required. The original stays untouched.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp,
                    )
                } else {
                    Text("copy of ${source.name}", fontFamily = Mono, color = accent, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Field("New profile name", newName) { newName = it }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The copy allows every quality and requires no custom-format score, so a " +
                            "release that only exists in one language is no longer rejected.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (source != null) {
                TextButton(
                    enabled = newName.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val msg = vm.arrCloneProfile(config, source.id, newName.trim())
                            busy = false
                            cloneOf = null
                            reload()
                            onResult("${config.label}: $msg")
                        }
                    },
                ) { Text(if (busy) "creating…" else "Create", fontFamily = Mono, color = MatrixGreen) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) { if (source != null) cloneOf = null else onDismiss() } }) {
                Text(if (source != null) "Back" else "Close", fontFamily = Mono, color = MatrixGreen)
            }
        },
    )
}
