package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.JellyRefreshMode
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Jellyfin's own "Refresh metadata" dialog: the plain scan, filling in what is missing, or
 * replacing everything. Replacing images is the usual fix for a wrong poster.
 */
@Composable
internal fun JellyfinRefreshDialog(
    title: String,
    accent: Color,
    onDismiss: () -> Unit,
    onRefresh: (JellyRefreshMode, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(JellyRefreshMode.MISSING) }
    var replaceImages by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Refresh $title", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column {
                JellyRefreshMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { mode = m }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (mode == m) AppIcons.Selected else AppIcons.NotSelected,
                            contentDescription = null, tint = accent, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(m.label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    }
                }
                // Only a full refresh fetches images, so the box means nothing for a plain scan.
                if (mode != JellyRefreshMode.SCAN) {
                    Row(
                        Modifier.fillMaxWidth().clickable { replaceImages = !replaceImages },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = replaceImages,
                            onCheckedChange = { replaceImages = it },
                            colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = MatrixGreen.copy(alpha = 0.6f)),
                        )
                        Text("replace existing images", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRefresh(mode, replaceImages && mode != JellyRefreshMode.SCAN) }) {
                Text("Refresh", fontFamily = Mono, color = accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
