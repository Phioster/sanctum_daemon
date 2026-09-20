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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.ArrFsListing
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.WarnAmber

/**
 * Taps its way to a folder on the server instead of making the user type an absolute path.
 *
 * Files are listed but not selectable — they are here so you can see that this is the folder
 * holding the release, which is the only way to be sure before scanning it.
 */
@Composable
internal fun ArrFolderBrowser(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    path: String,
    onPathChange: (String) -> Unit,
) {
    var listing by remember { mutableStateOf<ArrFsListing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        listing = null; error = null
        runCatching { vm.arrBrowsePath(config, path) }
            .onSuccess { listing = it }
            .onFailure { error = it.message ?: "cannot list this folder" }
    }

    Text(
        path.ifBlank { "/" },
        fontFamily = Mono, color = accent, fontSize = 11.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        val l = listing
        when {
            error != null -> Text(error!!, fontFamily = Mono, color = WarnAmber, fontSize = 11.sp)
            l == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            else -> {
                l.parent?.let { up ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPathChange(up) }.padding(vertical = 6.dp),
                    ) { Text("..", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp) }
                }
                if (l.entries.isEmpty()) {
                    Text("empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                l.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth()
                            .let { m -> if (e.isDirectory) m.clickable { onPathChange(e.path) } else m }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            (if (e.isDirectory) "▸ " else "  ") + e.name,
                            fontFamily = Mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (e.isDirectory) MatrixGreen else MatrixGreen.copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}