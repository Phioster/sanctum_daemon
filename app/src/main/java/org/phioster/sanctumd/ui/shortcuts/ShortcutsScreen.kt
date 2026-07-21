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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ServiceLogo

/** HTTP-shortcuts service screen: fire one-tap requests (tap again to confirm). */
@OptIn(ExperimentalMaterial3Api::class)
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
                        DropdownMenuItem(text = { Text("Edit service", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete service", fontFamily = Mono) }, onClick = { barMenu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (config.shortcuts.isEmpty()) {
                item { Text("no shortcuts yet — edit the service to add some", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
            items(config.shortcuts, key = { it.name + it.url }) { sc ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = running == null) {
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
                        Text("▸ ${sc.name}", fontFamily = Mono, color = accent, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        when {
                            running == sc.name -> Text("running…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                            pending == sc.name -> Text("tap again to run", fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 11.sp)
                        }
                    }
                    Text("${sc.method.uppercase()} ${sc.url}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface,
            title = { Text("Delete ${config.label}?", fontFamily = Mono, color = MatrixGreen) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", fontFamily = Mono, color = Color(0xFFFF5555)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
