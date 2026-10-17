package org.phioster.sanctumd.ui.nzbget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.NzbLogEntry
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/** Errors and warnings are what one opens a log for; DETAIL and INFO only bury them. */
internal fun problemsOnly(entries: List<NzbLogEntry>): List<NzbLogEntry> =
    entries.filter { it.kind == "ERROR" || it.kind == "WARNING" }

/** NZBGet's message log, the server's or one download's, newest first. */
@Composable
internal fun NzbLogDialog(title: String, load: suspend () -> List<NzbLogEntry>, onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf<List<NzbLogEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var onlyProblems by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { load() }.onSuccess { entries = it }.onFailure { error = it.message ?: "failed" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Row {
                    FilterChip(selected = !onlyProblems, onClick = { onlyProblems = false }, label = { Text("all", fontFamily = Mono, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
                    FilterChip(selected = onlyProblems, onClick = { onlyProblems = true }, label = { Text("errors & warnings", fontFamily = Mono, fontSize = 11.sp) })
                }
                Column(Modifier.heightIn(min = 120.dp, max = 460.dp).verticalScroll(rememberScrollState())) {
                    val list = entries?.let { if (onlyProblems) problemsOnly(it) else it }
                    when {
                        error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
                        list == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        list.isEmpty() -> Text(if (onlyProblems) "no errors or warnings" else "empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        else -> list.forEach { e ->
                            val c = when (e.kind) {
                                "ERROR" -> ErrRed
                                "WARNING" -> WarnAmber
                                else -> MatrixGreen.copy(alpha = 0.75f)
                            }
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text("${e.time}  ${e.kind}", fontFamily = Mono, color = c.copy(alpha = 0.7f), fontSize = 9.sp)
                                Text(e.text, fontFamily = Mono, color = c, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
