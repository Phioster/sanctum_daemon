package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.phioster.sanctumd.model.JellyWatchedTitle
import org.phioster.sanctumd.model.JellyWatcher
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.errorText
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.fmtWatch
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/** "23 plays · last 2026-11-11" */
internal fun watchDetail(plays: Int, lastPlayed: String, type: String = ""): String = listOfNotNull(
    type.takeIf { it.isNotBlank() },
    "$plays ${if (plays == 1) "play" else "plays"}",
    lastPlayed.takeIf { it.isNotBlank() }?.let { "last $it" },
).joinToString(" · ")

/**
 * The full leaderboard behind the podium: everybody, and per person what they watched.
 * One dialog with two levels; back leaves the person first, then the dialog.
 */
@Composable
internal fun JellyWatchersDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit) {
    var watchers by remember { mutableStateOf<List<JellyWatcher>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var person by remember { mutableStateOf<JellyWatcher?>(null) }
    var titles by remember { mutableStateOf<List<JellyWatchedTitle>?>(null) }
    LaunchedEffect(config.id) {
        runCatching { vm.jellyfinWatchers(config) }.onSuccess { watchers = it }.onFailure { error = errorText(it) }
    }
    LaunchedEffect(person?.userId) {
        titles = null
        val p = person ?: return@LaunchedEffect
        runCatching { vm.jellyfinWatcherTitles(config, p.userId) }.onSuccess { titles = it }.onFailure { error = errorText(it) }
    }
    val back: () -> Unit = { if (person != null) person = null else onDismiss() }

    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Black).padding(16.dp)) {
            val p = person
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(AppIcons.Back, contentDescription = "Back", tint = accent) }
                Column {
                    Text(p?.name ?: "Watch time", fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (p != null) "${fmtWatch(p.seconds)} · ${watchDetail(p.plays, p.lastPlayed)}" else "everybody, longest first",
                        fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val w = watchers
            val t = titles
            val dim = MatrixGreen.copy(alpha = 0.6f)
            LazyColumn(Modifier.fillMaxSize()) {
                when {
                    error != null -> item { Text("could not load: $error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                    p == null && w == null -> item { Text("loading…", fontFamily = Mono, color = dim) }
                    p == null && w!!.isEmpty() -> item { Text("no playback data yet", fontFamily = Mono, color = dim) }
                    p == null -> {
                        val max = w!!.maxOf { it.seconds }.coerceAtLeast(1L)
                        itemsIndexed(w, key = { _, x -> x.userId.ifBlank { x.name } }) { i, x ->
                            WatcherRow(i + 1, x, x.seconds.toFloat() / max, accent) { person = x }
                        }
                    }
                    t == null -> item { Text("loading…", fontFamily = Mono, color = dim) }
                    t.isEmpty() -> item { Text("nothing on record", fontFamily = Mono, color = dim) }
                    else -> items(t) { x -> TitleRow(x) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun WatcherRow(rank: Int, w: JellyWatcher, share: Float, accent: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank.", fontFamily = Mono, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(watchDetail(w.plays, w.lastPlayed), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Text(fmtWatch(w.seconds), fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
            Icon(AppIcons.Collapsed, contentDescription = null, tint = accent.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        }
    }
}

@Composable
private fun TitleRow(t: JellyWatchedTitle) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(watchDetail(t.plays, t.lastPlayed, t.type), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(fmtWatch(t.seconds), fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}
