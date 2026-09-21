package org.phioster.sanctumd.ui.seerr

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
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Seerr's own numbering. The picker must not offer a type the server does not know. */
private val ISSUE_TYPES = listOf(1 to "video", 2 to "audio", 3 to "subtitle", 4 to "other")

/**
 * Reports a problem with a title.
 *
 * Only reachable when Seerr already knows the title: an issue is filed against Seerr's internal
 * media id, which does not exist for something that was never requested.
 */
@Composable
internal fun SeerrReportIssueDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    mediaId: Int,
    title: String,
    accent: Color,
    onDismiss: () -> Unit,
    onReported: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(1) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Report an issue", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Text(title, fontFamily = Mono, color = accent, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                ISSUE_TYPES.forEach { (id, label) ->
                    val on = id == type
                    Row(
                        Modifier.fillMaxWidth().clickable { type = id }.padding(vertical = 5.dp),
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
                Spacer(Modifier.height(10.dp))
                Field("What is wrong?", message) { message = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = message.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val msg = vm.seerrReportIssue(config, mediaId, type, message.trim())
                        busy = false
                        onReported(msg)
                    }
                },
            ) { Text(if (busy) "sending…" else "Report", fontFamily = Mono, color = MatrixGreen) }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}
