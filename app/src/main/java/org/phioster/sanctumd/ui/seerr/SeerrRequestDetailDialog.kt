package org.phioster.sanctumd.ui.seerr

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.phioster.sanctumd.model.SeerrRequestDetail
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * What a request was actually made with, plus the actions that still apply to it.
 *
 * The row used to be tappable only while a request was pending, so a settled one could neither
 * be inspected nor removed — the most common state was the one with no interaction at all.
 * Approve and Decline appear only while it is pending; Delete always does, because it is the
 * only thing left to do with a finished request.
 */
@Composable
internal fun SeerrRequestDetailDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    detail: SeerrRequestDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onActed: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun act(label: String, call: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = call()
            busy = false
            onActed("$label: $msg")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text(detail.title, fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Field("status", detail.status, accent)
                Field("type", detail.mediaType + if (detail.is4k) " · 4K" else "", accent)
                if (detail.seasonCount > 0) Field("seasons", detail.seasonCount.toString(), accent)
                Field("requested by", detail.requestedBy, accent)
                Field("created", detail.created, accent)
                Spacer(Modifier.height(8.dp))
                Field("folder", detail.rootFolder, accent)
                Field("quality", detail.profile, accent)
                if (detail.rootFolder == "default" && detail.profile == "default") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Made without steering — this used the server's own defaults.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (detail.pending) {
                    Row {
                        TextButton(enabled = !busy, onClick = { act("approve") { vm.seerrApproveReq(config, detail.id) } }) {
                            Text("Approve", fontFamily = Mono, color = MatrixGreen)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(enabled = !busy, onClick = { act("decline") { vm.seerrDeclineReq(config, detail.id) } }) {
                            Text("Decline", fontFamily = Mono, color = accent)
                        }
                    }
                }
                TextButton(enabled = !busy, onClick = { act("delete") { vm.seerrDeleteReq(config, detail.id) } }) {
                    Text(if (busy) "…" else "Delete request", fontFamily = Mono, color = ErrRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

@Composable
private fun Field(label: String, value: String, accent: Color) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(104.dp))
        Text(value, fontFamily = Mono, color = accent.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}
