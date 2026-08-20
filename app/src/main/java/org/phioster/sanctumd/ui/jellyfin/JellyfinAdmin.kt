package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.JellyActivity
import org.phioster.sanctumd.model.JellyDevice
import org.phioster.sanctumd.model.JellyLibrary
import org.phioster.sanctumd.model.JellyLogFile
import org.phioster.sanctumd.model.JellyPackage
import org.phioster.sanctumd.model.JellyPlugin
import org.phioster.sanctumd.model.JellySystemInfo
import org.phioster.sanctumd.model.JellyTask
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.DashCat
import org.phioster.sanctumd.ui.common.DashTile
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The server-administration side of the Jellyfin screen: what the Dashboard tab shows and which
 * of its categories is open.
 *
 * [libraries] is loaded here but read by the Users tab too — editing a user's access needs the
 * library list, and loading it twice would be the only alternative.
 */
internal class JellyfinAdminState {
    var info by mutableStateOf<JellySystemInfo?>(null)
    var tasks by mutableStateOf<List<JellyTask>?>(null)
    var activity by mutableStateOf<List<JellyActivity>?>(null)
    var devices by mutableStateOf<List<JellyDevice>?>(null)
    var libraries by mutableStateOf<List<JellyLibrary>?>(null)
    var plugins by mutableStateOf<List<JellyPlugin>?>(null)
    var logFiles by mutableStateOf<List<JellyLogFile>?>(null)

    /** The category opened from a tile; null shows the tile overview. */
    var section by mutableStateOf<String?>(null)

    var logView by mutableStateOf<String?>(null) // log file name being viewed
    var logText by mutableStateOf<String?>(null) // its content (null = loading)
    var editLibrary by mutableStateOf<JellyLibrary?>(null)
    var showAddLibrary by mutableStateOf(false)
    var pluginDetail by mutableStateOf<JellyPlugin?>(null)
    var showCatalog by mutableStateOf(false)
    var catalog by mutableStateOf<List<JellyPackage>?>(null)
    var confirmRestart by mutableStateOf(false)
    /** True while a restart is polling for the server to come back — keeps its status message
     *  from being auto-cleared. */
    var restartInProgress by mutableStateOf(false)

    /** Reloads every dashboard list. Returns an error message, or null when it worked. */
    suspend fun reload(vm: DashboardViewModel, config: ServiceConfig): String? = try {
        info = vm.jellyfinInfo(config)
        tasks = vm.jellyfinTaskList(config)
        activity = vm.jellyfinActivityLog(config)
        devices = runCatching { vm.jellyfinDeviceList(config) }.getOrDefault(emptyList())
        libraries = runCatching { vm.jellyfinLibraryList(config) }.getOrDefault(emptyList())
        plugins = runCatching { vm.jellyfinPluginList(config) }.getOrDefault(emptyList())
        logFiles = runCatching { vm.jellyfinLogList(config) }.getOrDefault(emptyList())
        null
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        t.message ?: "failed"
    }

    /** Loads the library list once, for the Users tab. */
    suspend fun ensureLibraries(vm: DashboardViewModel, config: ServiceConfig) {
        if (libraries == null) libraries = vm.jellyfinLibraryList(config)
    }
}

@Composable
internal fun rememberJellyfinAdminState() = remember { JellyfinAdminState() }

/** The Dashboard tab: a tile overview, or one opened category. */
internal fun LazyListScope.jellyfinDashboardTab(
    st: JellyfinAdminState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onReload: suspend () -> Unit,
) {
    if (st.section == null) {
        // ── Overview: server card + clickable category tiles ──
        item {
            val si = st.info
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MatrixGreen.copy(alpha = 0.06f))
                    .border(1.dp, MatrixGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Dns, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(si?.serverName ?: "…", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("v${si?.version ?: "…"}${if (!si?.os.isNullOrBlank()) " · ${si!!.os}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            val cats = listOf(
                DashCat("tasks", "Tasks", st.tasks?.size, Icons.Filled.Schedule),
                DashCat("activity", "Activity", st.activity?.size, Icons.Filled.History),
                DashCat("libraries", "Libraries", st.libraries?.size, Icons.Filled.VideoLibrary),
                DashCat("plugins", "Plugins", st.plugins?.size, Icons.Filled.Extension),
                DashCat("logs", "Logs", st.logFiles?.size, Icons.Filled.Description),
                DashCat("devices", "Devices", st.devices?.size, Icons.Filled.Devices),
            )
            cats.chunked(2).forEach { rowCats ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowCats.forEach { c ->
                        DashTile(c.label, c.count, c.icon, accent, Modifier.weight(1f)) { st.section = c.key }
                    }
                    if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        // ── One category, opened from a tile ──
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { st.section = null }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(st.section!!.uppercase(), fontFamily = Mono, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
        when (st.section) {
            "tasks" -> {
                val tk = st.tasks
                when {
                    tk == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(tk) { t -> JellyTaskRow(t, accent) { scope.launch { onMessage(vm.jellyfinRunTaskById(config, t.id)); onReload() } } }
                }
            }
            "activity" -> {
                val ac = st.activity
                when {
                    ac == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    ac.isEmpty() -> item { Text("no st.activity", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(ac) { e -> JellyActivityRow(e, accent) }
                }
            }
            "libraries" -> {
                item {
                    Text(
                        "+ add library",
                        fontFamily = Mono, color = accent, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { st.showAddLibrary = true }.padding(vertical = 8.dp),
                    )
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                }
                val lb = st.libraries
                when {
                    lb == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    lb.isEmpty() -> item { Text("no st.libraries", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(lb) { l -> JellyLibraryRow(l, accent) { st.editLibrary = l } }
                }
            }
            "plugins" -> {
                item {
                    Text(
                        "+ plugin catalog",
                        fontFamily = Mono, color = accent, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            catalog = null; st.showCatalog = true
                            scope.launch { catalog = runCatching { vm.jellyfinCatalog(config) }.getOrDefault(emptyList()) }
                        }.padding(vertical = 8.dp),
                    )
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                }
                val pl = st.plugins
                when {
                    pl == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    pl.isEmpty() -> item { Text("no st.plugins", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(pl) { p -> JellyPluginRow(p, accent) { st.pluginDetail = p } }
                }
            }
            "logs" -> {
                val lg = st.logFiles
                when {
                    lg == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    lg.isEmpty() -> item { Text("no logs", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(lg) { f ->
                        JellyLogRow(f, accent) {
                            st.logView = f.name; st.logText = null
                            scope.launch { st.logText = vm.jellyfinLogText(config, f.name) }
                        }
                    }
                }
            }
            "devices" -> {
                val dv = st.devices
                when {
                    dv == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    dv.isEmpty() -> item { Text("no st.devices", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> items(dv) { d -> JellyDeviceRow(d, accent) }
                }
            }
        }
    }
}

/** The dialogs the Dashboard tab opens: restart, libraries, plugins and the log viewer. */
@Composable
internal fun JellyfinAdminDialogs(
    st: JellyfinAdminState,
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    onReload: suspend () -> Unit,
) {
    if (st.confirmRestart) {
        AlertDialog(
            onDismissRequest = { st.confirmRestart = false },
            containerColor = Surface,
            title = { Text("Restart server?", fontFamily = Mono, color = MatrixGreen) },
            text = { Text("This restarts the Jellyfin server for everyone.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    st.confirmRestart = false
                    scope.launch {
                        st.restartInProgress = true
                        onMessage("restarting…")
                        val r = vm.jellyfinRestartServer(config)
                        if (r.startsWith("error")) {
                            st.restartInProgress = false
                            onMessage(r)
                            return@launch
                        }
                        // Give the server a moment to actually go down, then poll until it answers again.
                        kotlinx.coroutines.delay(3000)
                        onMessage("restarting… waiting for server to come back")
                        var back = false
                        val deadline = System.currentTimeMillis() + 120_000
                        while (System.currentTimeMillis() < deadline) {
                            if (runCatching { vm.jellyfinInfo(config) }.getOrNull() != null) { back = true; break }
                            kotlinx.coroutines.delay(3000)
                        }
                        st.restartInProgress = false
                        if (back) {
                            onMessage("✓ server back online")
                            onReload()
                        } else {
                            onMessage("restart sent — server hasn't responded yet")
                        }
                    }
                }) {
                    Text("Restart", fontFamily = Mono, color = ErrRed)
                }
            },
            dismissButton = { TextButton(onClick = { st.confirmRestart = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }

    if (st.showAddLibrary) {
        JellyAddLibraryDialog(
            accent = accent,
            onDismiss = { st.showAddLibrary = false },
            onCreate = { name, type, path ->
                st.showAddLibrary = false
                scope.launch { onMessage(vm.jellyfinCreateLibrary(config, name, type, path)); onReload() }
            },
        )
    }

    st.editLibrary?.let { lib ->
        JellyLibraryDialog(
            library = lib,
            accent = accent,
            onDismiss = { st.editLibrary = null },
            onRename = { newName ->
                st.editLibrary = null
                scope.launch { onMessage(vm.jellyfinRenameLibraryTo(config, lib.name, newName)); onReload() }
            },
            onAddPath = { path ->
                st.editLibrary = null
                scope.launch { onMessage(vm.jellyfinLibraryAddPath(config, lib.name, path)); onReload() }
            },
            onRemovePath = { path ->
                st.editLibrary = null
                scope.launch { onMessage(vm.jellyfinLibraryRemovePath(config, lib.name, path)); onReload() }
            },
            onDelete = {
                st.editLibrary = null
                scope.launch { onMessage(vm.jellyfinRemoveLibrary(config, lib.name)); onReload() }
            },
        )
    }

    st.pluginDetail?.let { p ->
        JellyPluginDialog(
            plugin = p,
            accent = accent,
            onDismiss = { st.pluginDetail = null },
            onToggle = {
                st.pluginDetail = null
                scope.launch { onMessage(vm.jellyfinPluginEnable(config, p.id, p.version, p.status.equals("Disabled", true))); onReload() }
            },
            onUninstall = {
                st.pluginDetail = null
                scope.launch { onMessage(vm.jellyfinPluginUninstall(config, p.id, p.version)); onReload() }
            },
        )
    }

    if (st.showCatalog) {
        JellyCatalogDialog(
            catalog = st.catalog,
            accent = accent,
            onDismiss = { st.showCatalog = false },
            onInstall = { pkg ->
                st.showCatalog = false
                scope.launch { onMessage(vm.jellyfinCatalogInstall(config, pkg.name, pkg.guid)); onReload() }
            },
        )
    }

    st.logView?.let { name ->
        AlertDialog(
            onDismissRequest = { st.logView = null },
            containerColor = Surface,
            title = { Text(name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        st.logText ?: "loading…",
                        fontFamily = Mono,
                        color = if (st.logText?.startsWith("error") == true) ErrRed else MatrixGreen.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { st.logView = null }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
