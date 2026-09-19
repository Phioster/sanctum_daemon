package org.phioster.sanctumd.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
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
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ServiceLogo

/** Compact, tidy status error for the service cards — the raw DNS/connection exception is verbose and
 *  ugly (and the [err] badge already flags the failure), so collapse the common ones to a one-liner. */
internal fun friendlyStatusError(raw: String?): String {
    val e = raw?.trim().orEmpty()
    return when {
        e.isEmpty() -> "error"
        e.contains("Unable to resolve host", true) || e.contains("No address associated", true) ||
            e.contains("UnknownHost", true) -> "offline · server not reachable"
        e.contains("timeout", true) || e.contains("timed out", true) -> "timed out"
        e.contains("Failed to connect", true) || e.contains("ConnectException", true) ||
            e.contains("Connection refused", true) || e.contains("ECONNREFUSED", true) -> "connection refused"
        e.contains("trust anchor", true) || e.contains("CertPath", true) || e.contains("SSLHandshake", true) -> "TLS / certificate error"
        e.contains("HTTP 401", true) || e.contains("HTTP 403", true) -> "unauthorized · check API key"
        else -> e.take(80)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ServiceCard(
    config: ServiceConfig,
    status: ServiceStatus?,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onGroup: (() -> Unit)? = null,
) {
    val accent = Color(config.type.accent)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onOpen,
            onLongClick = { onLongPress?.invoke() },
        ),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(config.type, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (config.pinned) "★ ${config.label}" else config.label,
                        fontFamily = Mono, fontWeight = FontWeight.Bold, color = accent, fontSize = 18.sp,
                    )
                    // The type line is noise when the service is simply called after its type
                    // (the common case) — only show it when the label says something else.
                    if (!config.label.equals(config.type.label, ignoreCase = true)) {
                        Text(config.type.label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                val tag = when {
                    status == null || status.isLoading -> "[...]"
                    status.ok -> "[ok]"
                    else -> "[err]"
                }
                Text(tag, fontFamily = Mono, color = if (status == null || status.isLoading || status.ok) MatrixGreen else ErrRed)
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen.copy(alpha = 0.6f))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up", fontFamily = Mono) }, onClick = { menuOpen = false; onMoveUp() })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down", fontFamily = Mono) }, onClick = { menuOpen = false; onMoveDown() })
                        onPin?.let { pin ->
                            DropdownMenuItem(text = { Text(if (config.pinned) "Unpin" else "Pin to top", fontFamily = Mono) }, onClick = { menuOpen = false; pin() })
                        }
                        onGroup?.let { grp ->
                            DropdownMenuItem(text = { Text("Group…", fontFamily = Mono) }, onClick = { menuOpen = false; grp() })
                        }
                        DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                status == null || status.isLoading -> Text("connecting…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                status.ok -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    status.stats.forEach { (k, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 20.sp)
                            Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(friendlyStatusError(status.error), fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
            }
            status?.note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

/** One-line variant of [ServiceCard] for the "compact" list view: logo, label, stats, status. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ServiceRowCompact(
    config: ServiceConfig,
    status: ServiceStatus?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = Color(config.type.accent)
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceLogo(config.type, 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (config.pinned) "★ ${config.label}" else config.label,
                fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                status == null || status.isLoading -> "connecting…"
                status.ok -> status.stats.joinToString("  ") { "${it.second} ${it.first.lowercase()}" }
                else -> friendlyStatusError(status.error)
            }
            Text(
                sub,
                fontFamily = Mono,
                color = if (status?.ok == false && !status.isLoading) ErrRed else accent.copy(alpha = 0.8f),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                .background(
                    when {
                        status == null || status.isLoading -> MatrixGreen.copy(alpha = 0.35f)
                        status.ok -> MatrixGreen
                        else -> ErrRed
                    },
                ),
        )
    }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
}

/** Square tile for the "grid" list view: logo, label, status dot. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ServiceTile(
    config: ServiceConfig,
    status: ServiceStatus?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = Color(config.type.accent)
    Card(
        modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ServiceLogo(config.type, 30.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (config.pinned) "★ ${config.label}" else config.label,
                fontFamily = Mono, color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            // The same key numbers the card view shows, scaled down to fit a tile.
            when {
                status == null || status.isLoading ->
                    Text("connecting…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                status.ok && status.stats.isNotEmpty() ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        status.stats.take(3).forEach { (k, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 15.sp, maxLines = 1)
                                Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                status.ok -> Spacer(Modifier.height(0.dp))
                else -> Text(friendlyStatusError(status.error), fontFamily = Mono, color = ErrRed, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                status == null || status.isLoading -> MatrixGreen.copy(alpha = 0.35f)
                                status.ok -> MatrixGreen
                                else -> ErrRed
                            },
                        ),
                )
                Spacer(Modifier.width(5.dp))
                if (!config.label.equals(config.type.label, ignoreCase = true)) {
                    Text(config.type.label, fontFamily = Mono, color = accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceDetailScreen(
    vm: DashboardViewModel,
    config: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val statuses by vm.statuses.collectAsState()
    val status = statuses[config.id]
    val accent = Color(config.type.accent)
    val scope = rememberCoroutineScope()
    var actionResult by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf(false) }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { barMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MatrixGreen)
                        }
                        DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                            DropdownMenuItem(text = { Text("Edit", fontFamily = Mono) }, onClick = { barMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete", fontFamily = Mono) }, onClick = { barMenu = false; onDelete() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(config.type.label, fontFamily = Mono, color = accent, fontSize = 14.sp)
            Text(config.baseUrl, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            when {
                status?.ok == true -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    status.stats.forEach { (k, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MatrixGreen, fontSize = 22.sp)
                            Text(k.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
                status == null || status.isLoading -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp)
                else -> Text(friendlyStatusError(status.error), fontFamily = Mono, color = ErrRed, fontSize = 13.sp)
            }
            status?.note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Text("> actions", fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val perform: (suspend () -> String) -> Unit = { action ->
                busy = true
                actionResult = null
                scope.launch {
                    actionResult = action()
                    busy = false
                    vm.refreshAll()
                }
            }
            when (config.type) {
                ServiceType.JELLYFIN ->
                    ActionBtn("Scan library", !busy) { perform { vm.jellyfinScan(config) } }
                ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR ->
                    ActionBtn("Search missing", !busy) { perform { vm.searchMissing(config) } }
                ServiceType.PROWLARR ->
                    ActionBtn("Test all indexers", !busy) { perform { vm.prowlarrTestAll(config) } }
                ServiceType.NZBGET -> Row {
                    ActionBtn("Pause", !busy) { perform { vm.nzbgetPause(config) } }
                    Spacer(Modifier.width(12.dp))
                    ActionBtn("Resume", !busy) { perform { vm.nzbgetResume(config) } }
                }
                ServiceType.SEERR -> Text(
                    "request approve/decline comes with the list view",
                    fontFamily = Mono,
                    color = MatrixGreen.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                )
                else -> {} // NTFY has its own screen; nothing generic to offer here
            }
            actionResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontFamily = Mono, color = if (it.startsWith("error")) ErrRed else MatrixGreen, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddServiceScreen(
    existing: ServiceConfig? = null,
    onCancel: () -> Unit,
    onSave: (ServiceConfig) -> Unit,
    onTest: suspend (ServiceConfig) -> ServiceStatus,
) {
    var type by remember { mutableStateOf(existing?.type ?: ServiceType.JELLYFIN) }
    var label by remember { mutableStateOf(existing?.label ?: ServiceType.JELLYFIN.label) }
    var labelEdited by remember { mutableStateOf(existing != null) }
    var url by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var jellyLogin by remember { mutableStateOf(existing?.useLogin ?: false) }
    var cfId by remember { mutableStateOf(existing?.customHeaders?.get("CF-Access-Client-Id") ?: "") }
    var cfSecret by remember { mutableStateOf(existing?.customHeaders?.get("CF-Access-Client-Secret") ?: "") }
    var topics by remember { mutableStateOf(existing?.topics?.joinToString(", ") ?: "") }
    var shortcuts by remember { mutableStateOf(existing?.shortcuts ?: emptyList()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Keep label in sync with the chosen type until the user edits it manually.
    LaunchedEffect(type) { if (!labelEdited) label = type.label }

    val usesLogin = (type == ServiceType.JELLYFIN && jellyLogin) || type == ServiceType.NZBGET

    fun build(): ServiceConfig {
        val headers = buildMap {
            if (cfId.isNotBlank()) put("CF-Access-Client-Id", cfId.trim())
            if (cfSecret.isNotBlank()) put("CF-Access-Client-Secret", cfSecret.trim())
        }
        val base = ServiceConfig(
            type = type,
            label = label.ifBlank { type.label },
            baseUrl = url.trim(),
            apiKey = apiKey.trim(),
            username = username.trim(),
            password = password,
            useLogin = type == ServiceType.JELLYFIN && jellyLogin,
            customHeaders = headers,
            topics = topics.split(',', ' ').map { it.trim() }.filter { it.isNotBlank() },
            shortcuts = shortcuts.filter { it.name.isNotBlank() && it.url.isNotBlank() },
        )
        return if (existing != null) base.copy(id = existing.id) else base
    }

    val canSave = when {
        // Shortcuts carry their own URLs; no base URL or key needed.
        type == ServiceType.SHORTCUTS -> shortcuts.any { it.name.isNotBlank() && it.url.isNotBlank() }
        url.isBlank() -> false
        type == ServiceType.NZBGET -> username.isNotBlank() && password.isNotBlank()
        type == ServiceType.JELLYFIN && jellyLogin -> username.isNotBlank() && password.isNotBlank()
        type == ServiceType.NTFY -> topics.isNotBlank() // token optional (open servers exist)
        else -> apiKey.isNotBlank()
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "edit service" else "add service", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ServiceType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        leadingIcon = { ServiceLogo(t, 18.dp) },
                        label = { Text(t.label, fontFamily = Mono) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Field("Label", label) { label = it; labelEdited = true }
            if (type != ServiceType.SHORTCUTS) {
                Field("Base URL (https://…)", url) { url = it }
                // Cleartext stays allowed — a homelab on http://192.168.x.x is the normal case and
                // breaking it would help nobody. But the key below travels on every request, so say
                // so plainly instead of letting the hint in the label carry it.
                if (url.isNotBlank() && !url.trim().startsWith("https://", ignoreCase = true)) {
                    Text(
                        "⚠ not https — the API key and password below travel unencrypted and anyone on the same network can read them.",
                        fontFamily = Mono, color = Color(0xFFE0A030), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (type == ServiceType.SHORTCUTS) {
                Spacer(Modifier.height(8.dp))
                Text("SHORTCUTS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                shortcuts.forEachIndexed { i, sc ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${i + 1}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Row {
                            listOf("GET", "POST").forEach { m ->
                                FilterChip(
                                    selected = sc.method.equals(m, true),
                                    onClick = { shortcuts = shortcuts.toMutableList().also { it[i] = sc.copy(method = m) } },
                                    label = { Text(m, fontFamily = Mono, fontSize = 11.sp) },
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        Text("✕", fontFamily = Mono, color = ErrRed, fontSize = 16.sp, modifier = Modifier.clickable { shortcuts = shortcuts.toMutableList().also { it.removeAt(i) } }.padding(8.dp))
                    }
                    Field("Name (e.g. Homelab Start)", sc.name) { v -> shortcuts = shortcuts.toMutableList().also { it[i] = sc.copy(name = v) } }
                    Field("URL (https://…)", sc.url) { v -> shortcuts = shortcuts.toMutableList().also { it[i] = sc.copy(url = v) } }
                    if (sc.method.equals("POST", true)) {
                        Field("Body (optional, JSON or text)", sc.body) { v -> shortcuts = shortcuts.toMutableList().also { it[i] = sc.copy(body = v) } }
                    }
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ add shortcut",
                    fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp,
                    modifier = Modifier.clickable { shortcuts = shortcuts + org.phioster.sanctumd.model.HttpShortcut(name = "", url = "") }.padding(vertical = 8.dp),
                )
            }

            // Jellyfin can auth by API key or by login.
            if (type == ServiceType.JELLYFIN) {
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = !jellyLogin, onClick = { jellyLogin = false }, label = { Text("API key", fontFamily = Mono) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = jellyLogin, onClick = { jellyLogin = true }, label = { Text("Login", fontFamily = Mono) })
                }
            }

            if (type.usesApiKeyHeader || (type == ServiceType.JELLYFIN && !jellyLogin)) {
                Field("API key", apiKey, isPassword = true) { apiKey = it }
            }
            if (type == ServiceType.NTFY) {
                Field("Topics (comma-separated)", topics) { topics = it }
                Field("Access token (optional, tk_…)", apiKey) { apiKey = it }
            }
            if (usesLogin) {
                Field("Username", username) { username = it }
                Field("Password", password, isPassword = true) { password = it }
            }

            Spacer(Modifier.height(8.dp))
            Text("Cloudflare Access (optional)", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
            Field("CF-Access-Client-Id", cfId) { cfId = it }
            Field("CF-Access-Client-Secret", cfSecret, isPassword = true) { cfSecret = it }

            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = {
                    testResult = "testing…"
                    scope.launch {
                        val r = onTest(build())
                        testResult = if (r.ok) "ok: " + r.stats.joinToString { "${it.first}=${it.second}" } else "error: ${r.error}"
                    }
                }) { Text("Test", fontFamily = Mono) }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = { onSave(build()) }, enabled = canSave) {
                    Text("Save", fontFamily = Mono)
                }
            }
            testResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontFamily = Mono, color = if (it.startsWith("ok")) MatrixGreen else Color(0xFFFFAA00), fontSize = 13.sp)
            }
        }
    }
}
