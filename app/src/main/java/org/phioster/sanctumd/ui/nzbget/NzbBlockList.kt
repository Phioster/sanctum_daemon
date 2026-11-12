package org.phioster.sanctumd.ui.nzbget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.NzbBlock
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.errorText
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Every word of [query] must appear in the name; dots and spaces count the same. */
internal fun blockMatches(name: String, query: String): Boolean {
    val hay = name.lowercase().replace('.', ' ')
    return query.lowercase().replace('.', ' ').split(' ').filter { it.isNotBlank() }.all { it in hay }
}

/** "3 entries · 810 MB · 19.08.2026 · SUCCESS/HIDDEN" */
internal fun blockDetail(b: NzbBlock): String = listOfNotNull(
    if (b.ids.size > 1) "${b.ids.size} entries" else null,
    "${b.sizeMb} MB",
    if (b.time > 0) java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT).format(java.util.Date(b.time * 1000)) else null,
    b.status,
).joinToString(" · ")

/** What NZBGet's duplicate check still remembers: releases it will refuse to download again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NzbBlockListScreen(vm: DashboardViewModel, config: ServiceConfig, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<NzbBlock>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<NzbBlock?>(null) }
    suspend fun load() {
        runCatching { vm.nzbBlocks(config) }
            .onSuccess { list = it; error = null }
            .onFailure { error = errorText(it) }
    }
    LaunchedEffect(config.id) { load() }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("Block list", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back", tint = MatrixGreen) } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = MatrixGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                "NZBGet skips an NZB it has seen before. Unblock a release to let it download again.",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp,
            )
            Field("Search", query) { query = it }
            message?.let { m ->
                Text(m, fontFamily = Mono, fontSize = 12.sp, color = if (m.startsWith("error")) ErrRed else MatrixGreen, modifier = Modifier.padding(vertical = 4.dp))
            }
            val l = list
            val shown = l.orEmpty().filter { blockMatches(it.name, query) }
            LazyColumn(Modifier.fillMaxSize()) {
                when {
                    error != null && l == null -> item { Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)) }
                    l == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                    l.isEmpty() -> item { Text("nothing is blocked", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                    shown.isEmpty() -> item { Text("no match", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 16.dp)) }
                    else -> items(shown, key = { it.ids.first() }) { b -> BlockRow(b) { confirm = b } }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Surface,
            title = { Text("Unblock release", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Text(
                    "Remove ${b.name} from the duplicate history? NZBGet will accept it again; nothing is downloaded until Sonarr or Radarr grabs it.",
                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { message = vm.nzbUnblock(config, b.name); load() }
                }) { Text("Unblock", fontFamily = Mono, color = ErrRed) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun BlockRow(b: NzbBlock, onUnblock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(blockDetail(b), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            TextButton(onClick = onUnblock) { Text("unblock", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}
