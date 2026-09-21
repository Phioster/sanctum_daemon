package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellySubtitle
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import org.phioster.sanctumd.ui.theme.AppIcons

/** Three-letter ISO codes, because that is what the endpoint takes. German first. */
private val SUBTITLE_LANGUAGES = listOf(
    "ger" to "Deutsch",
    "eng" to "English",
    "fre" to "Français",
    "spa" to "Español",
    "ita" to "Italiano",
    "jpn" to "日本語",
)

/**
 * Searches subtitles for one item and downloads the chosen one onto it.
 *
 * The list is ordered by usefulness rather than by provider order: a hash match was made for
 * this exact file and is the one that will be in sync, so it is marked and sorted first.
 */
@Composable
internal fun JellyfinSubtitlesDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    detail: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onDownloaded: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("ger") }
    var subs by remember { mutableStateOf<List<JellySubtitle>?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun search() {
        busy = true
        subs = runCatching { vm.jellyfinSubtitles(config, detail.id, language) }.getOrDefault(emptyList())
        busy = false
    }
    LaunchedEffect(language) { search() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Subtitles", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    SUBTITLE_LANGUAGES.forEach { (code, label) ->
                        val on = code == language
                        Text(
                            label,
                            fontFamily = Mono, fontSize = 12.sp,
                            color = if (on) MatrixGreen else MatrixGreen.copy(alpha = 0.45f),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable(enabled = !busy) { language = code }
                                .padding(end = 14.dp, top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (val list = subs) {
                        null -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (list.isEmpty()) {
                            Text(
                                if (busy) "searching…" else "nothing found for this language",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                            )
                        } else list.forEach { s ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        busy = true
                                        scope.launch {
                                            val msg = vm.jellyfinGetSubtitle(config, detail.id, s.id)
                                            busy = false
                                            onDownloaded(msg)
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                            ) {
                                Row {
                                    if (s.hashMatch) {
                                        Icon(AppIcons.Done, contentDescription = "exact match", tint = MatrixGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        s.name.ifBlank { s.provider }, fontFamily = Mono, color = MatrixGreen,
                                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    buildList {
                                        if (s.hashMatch) add("exact match")
                                        if (s.forced) add("forced")
                                        if (s.format.isNotBlank()) add(s.format.uppercase())
                                        if (s.provider.isNotBlank()) add(s.provider)
                                        if (s.downloads > 0) add("${s.downloads} ↓")
                                    }.joinToString(" · "),
                                    fontFamily = Mono, color = accent.copy(alpha = 0.75f), fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
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
