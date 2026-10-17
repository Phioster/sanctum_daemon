package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.SeerrRequestItem
import org.phioster.sanctumd.model.SeerrUserInfo
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.SeerrBlockedItem
import org.phioster.sanctumd.net.SeerrLogLine
import org.phioster.sanctumd.net.SeerrQuota
import org.phioster.sanctumd.net.SeerrQuotaPart
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.SectionHeader
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/** A titled row of posters, e.g. recommendations; hidden while empty. */
@Composable
internal fun SeerrPosterRow(title: String, items: List<SeerrDiscoverItem>, accent: Color, onOpen: (SeerrDiscoverItem) -> Unit) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    SectionHeader(title)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        items.forEach { it ->
            Column(Modifier.width(96.dp).padding(end = 10.dp).clickable { onOpen(it) }) {
                if (it.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = it.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
                    )
                } else {
                    Box(Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)).background(Surface))
                }
                Spacer(Modifier.height(4.dp))
                Text(it.title, fontFamily = Mono, color = MatrixGreen, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOf(it.year, it.status).filter { s -> s.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

private fun quotaLine(label: String, q: SeerrQuotaPart?): String = when {
    q == null -> "$label: ?"
    q.limit <= 0 -> "$label: unlimited"
    else -> "$label: ${q.used}/${q.limit} in ${q.days} days, ${q.remaining} left"
}

/** One user: what their quota allows and what they asked for lately. */
@Composable
internal fun SeerrUserDialog(vm: DashboardViewModel, config: ServiceConfig, user: SeerrUserInfo, accent: Color, onDismiss: () -> Unit) {
    var quota by remember { mutableStateOf<SeerrQuota?>(null) }
    var requests by remember { mutableStateOf<List<SeerrRequestItem>?>(null) }
    LaunchedEffect(user.id) {
        quota = runCatching { vm.seerrQuotaOf(config, user.id) }.getOrNull()
        requests = runCatching { vm.seerrRequestsBy(config, user.id) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(user.name, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SectionHeader("QUOTA")
                Spacer(Modifier.height(4.dp))
                val q = quota
                if (q == null) {
                    Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                } else {
                    listOf(quotaLine("movies", q.movie) to (q.movie?.restricted == true), quotaLine("series", q.tv) to (q.tv?.restricted == true))
                        .forEach { (line, restricted) ->
                            Text(line, fontFamily = Mono, color = if (restricted) WarnAmber else accent, fontSize = 12.sp)
                        }
                }
                Spacer(Modifier.height(10.dp))
                SectionHeader("REQUESTS")
                Spacer(Modifier.height(4.dp))
                val rs = requests
                when {
                    rs == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    rs.isEmpty() -> Text("none", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> rs.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(r.title, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(r.status, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Titles kept out of discover; one tap lets a title back in. */
@Composable
internal fun SeerrBlocklistDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<SeerrBlockedItem>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { list = runCatching { vm.seerrBlocklistOf(config) }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Blocklist", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                message?.let { Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 11.sp) }
                val l = list
                when {
                    l == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    l.isEmpty() -> Text("nothing blocked", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> l.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title.ifBlank { "tmdb ${b.tmdbId}" }, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(b.mediaType, b.by).filter { it.isNotBlank() }.joinToString(" · "), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    message = vm.seerrUnblockTitle(config, b.tmdbId, b.mediaType)
                                    list = runCatching { vm.seerrBlocklistOf(config) }.getOrNull() ?: list
                                }
                            }) { Text("unblock", fontFamily = Mono, color = accent) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Seerr's own log, from the chosen level up: where a failed request says why. */
@Composable
internal fun SeerrLogsDialog(vm: DashboardViewModel, config: ServiceConfig, onDismiss: () -> Unit) {
    var level by remember { mutableStateOf("warn") }
    var lines by remember { mutableStateOf<List<SeerrLogLine>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(level) {
        lines = null; error = null
        runCatching { vm.seerrLogLines(config, level) }.onSuccess { lines = it }.onFailure { error = it.message ?: "failed" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Seerr log", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Row {
                    listOf("error", "warn", "info").forEach { l ->
                        FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l, fontFamily = Mono, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                Column(Modifier.heightIn(min = 120.dp, max = 460.dp).verticalScroll(rememberScrollState())) {
                    val ls = lines
                    when {
                        error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
                        ls == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        ls.isEmpty() -> Text("nothing at this level", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        else -> ls.forEach { e ->
                            val c = when (e.level) {
                                "error" -> ErrRed
                                "warn" -> WarnAmber
                                else -> MatrixGreen.copy(alpha = 0.75f)
                            }
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text("${e.time}  ${e.level.uppercase()}", fontFamily = Mono, color = c.copy(alpha = 0.7f), fontSize = 9.sp)
                                Text(e.message, fontFamily = Mono, color = c, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
