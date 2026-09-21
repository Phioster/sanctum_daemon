package org.phioster.sanctumd.ui.ntfy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ServiceLogo

/** ntfy service screen: per-topic message history (read-only; live pushes come via the stream service). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NtfyScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf(config.topics.firstOrNull() ?: "") }
    var messages by remember { mutableStateOf<List<org.phioster.sanctumd.model.NtfyMessage>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var barMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    suspend fun load() {
        listError = null
        messages = null
        try {
            messages = vm.ntfyMessages(config, topic)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            listError = t.message
        }
    }
    LaunchedEffect(topic) { if (topic.isNotBlank()) load() }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceLogo(config.type, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(config.label, fontFamily = Mono, color = MatrixGreen)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen) }
                    IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = MatrixGreen) }
                    DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit service", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete service", fontFamily = Mono) }, onClick = { barMenu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (config.topics.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    config.topics.forEach { t ->
                        FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(t, fontFamily = Mono) })
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            val msgs = messages
            when {
                topic.isBlank() -> Text("no topics configured — edit the service", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                listError != null -> Text("error: $listError", fontFamily = Mono, color = WarnAmber, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                msgs == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                msgs.isEmpty() -> Text("no cached messages (server keeps ~12 h)", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(msgs) { m ->
                        val time = if (m.time > 0) java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(m.time * 1000)) else ""
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.title.ifBlank { m.topic }, fontFamily = Mono, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(time, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            Text(m.text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface,
            title = { Text("Delete ${config.label}?", fontFamily = Mono, color = MatrixGreen) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", fontFamily = Mono, color = ErrRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
