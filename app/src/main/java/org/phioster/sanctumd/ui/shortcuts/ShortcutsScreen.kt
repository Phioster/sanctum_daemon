package org.phioster.sanctumd.ui.shortcuts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.FilterChip
import org.phioster.sanctumd.model.HttpShortcut
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ServiceLogo
import androidx.compose.foundation.layout.size
import org.phioster.sanctumd.ui.theme.AppIcons

/** HTTP-shortcuts service screen: fire one-tap requests (tap again to confirm). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutsScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var barMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) } // shortcut name awaiting the confirm tap
    var running by remember { mutableStateOf<String?>(null) }
    // Local copy: the detail config is a snapshot, so edits made here must show without reopening.
    var list by remember(config) { mutableStateOf(config.shortcuts) }
    var editIndex by remember { mutableStateOf<Int?>(null) } // -1 = new shortcut
    fun save(updated: List<HttpShortcut>) {
        list = updated
        vm.upsertService(config.copy(shortcuts = updated))
    }

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
                    IconButton(onClick = { barMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = MatrixGreen) }
                    DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                        DropdownMenuItem(text = { Text("Add shortcut", fontFamily = Mono) }, onClick = { barMenu = false; editIndex = -1 })
                        DropdownMenuItem(text = { Text("Edit service", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete service", fontFamily = Mono) }, onClick = { barMenu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (list.isEmpty()) {
                item { Text("no shortcuts yet", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
            itemsIndexed(list, key = { i, it -> "$i:" + it.name + it.url }) { i, sc ->
                Column(
                    Modifier.fillMaxWidth()
                        .combinedClickable(enabled = running == null, onLongClick = { pending = null; editIndex = i }) {
                            if (pending == sc.name) {
                                pending = null
                                running = sc.name
                                scope.launch {
                                    val res = vm.runShortcut(config, sc)
                                    android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show()
                                    running = null
                                }
                            } else {
                                pending = sc.name
                            }
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Bullet, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Text(sc.name, fontFamily = Mono, color = accent, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        when {
                            running == sc.name -> Text("running…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                            pending == sc.name -> Text("tap again to run", fontFamily = Mono, color = WarnAmber, fontSize = 11.sp)
                        }
                    }
                    Text("${sc.method.uppercase()} ${sc.url}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
            }
            item {
                Text(
                    "+ add shortcut",
                    fontFamily = Mono, color = accent, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { editIndex = -1 }.padding(vertical = 14.dp),
                )
                if (list.isNotEmpty()) {
                    Text("long-press a shortcut to edit or remove it", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.4f), fontSize = 10.sp)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editIndex?.let { idx ->
        ShortcutEditDialog(
            initial = list.getOrNull(idx),
            onDismiss = { editIndex = null },
            onSave = { sc ->
                save(if (idx < 0) list + sc else list.toMutableList().also { it[idx] = sc })
                editIndex = null
            },
            onRemove = if (idx >= 0) ({ save(list.toMutableList().also { it.removeAt(idx) }); editIndex = null }) else null,
        )
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

/** Add or edit one shortcut without touching the others. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditDialog(
    initial: HttpShortcut?,
    onDismiss: () -> Unit,
    onSave: (HttpShortcut) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var method by remember { mutableStateOf(initial?.method ?: "GET") }
    var body by remember { mutableStateOf(initial?.body.orEmpty()) }
    val valid = name.isNotBlank() && url.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(if (initial == null) "New shortcut" else "Edit shortcut", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Row {
                    listOf("GET", "POST").forEach { m ->
                        FilterChip(
                            selected = method.equals(m, true),
                            onClick = { method = m },
                            label = { Text(m, fontFamily = Mono, fontSize = 11.sp) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Field("Name", name) { name = it }
                Field("URL (https://…)", url) { url = it }
                if (method.equals("POST", true)) Field("Body (optional, JSON or text)", body) { body = it }
                if (onRemove != null) {
                    Text(
                        "remove this shortcut",
                        fontFamily = Mono, color = ErrRed, fontSize = 12.sp,
                        modifier = Modifier.clickable { onRemove() }.padding(top = 14.dp, bottom = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(HttpShortcut(name.trim(), method, url.trim(), if (method == "POST") body else "")) }) {
                Text("Save", fontFamily = Mono, color = if (valid) MatrixGreen else MatrixGreen.copy(alpha = 0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
