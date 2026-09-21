package org.phioster.sanctumd.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.DiscoveredServer
import org.phioster.sanctumd.net.jellyfinDiscoverServers
import org.phioster.sanctumd.net.jellyfinQuickConnectApproved
import org.phioster.sanctumd.net.jellyfinQuickConnectAvailable
import org.phioster.sanctumd.net.jellyfinQuickConnectFinish
import org.phioster.sanctumd.net.jellyfinQuickConnectStart
import org.phioster.sanctumd.net.jellyfinTvLogin
import org.phioster.sanctumd.net.jellyfinTvPublicInfo
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmberDim

/** Where the sign-in flow currently is. */
private sealed interface SetupStep {
    /** Pick a server found on the network, or choose to type one in. */
    data object Pick : SetupStep
    /** Type a server address by hand. */
    data object Manual : SetupStep
    /** Server known and reachable, now authenticate against it. */
    data class Auth(val baseUrl: String, val serverName: String) : SetupStep
}

/**
 * Address spellings worth trying for what the user typed.
 *
 * People type "192.168.1.20" or "jellyfin.local:8096", not a URL. Rather than making them find the
 * ":" and "/" keys on an on-screen keyboard, we try the plausible readings in order and keep the
 * first that answers.
 *
 * **https is tried first.** The sign-in that follows carries a password or a Quick Connect secret,
 * and a server that speaks both would otherwise be reached over plain http purely because that
 * spelling came first in the list. A server that only speaks http still works, it simply answers
 * one probe later, and [isPlainHttp] then says so on screen.
 */
internal fun candidateUrls(input: String): List<String> {
    val raw = input.trim().trimEnd('/')
    if (raw.isBlank()) return emptyList()
    if (raw.startsWith("http://") || raw.startsWith("https://")) return listOf(raw)
    val hasPort = raw.substringBefore('/').contains(':')
    return buildList {
        add("https://$raw")
        if (!hasPort) add("https://$raw:8920")
        add("http://$raw")
        if (!hasPort) add("http://$raw:8096")
    }
}

/** True when the address carries the sign-in unencrypted, which the screen has to admit to. */
internal fun isPlainHttp(url: String) = url.trim().startsWith("http://", ignoreCase = true)

/**
 * Sign-in for the TV.
 *
 * Two ways in, in the order a living room actually wants them: pick a server the app found on the
 * local network by UDP broadcast, or type an address for anything outside it (a reverse proxy, a
 * VPN). Authentication then prefers **Quick Connect**. The TV shows a code and the user approves it
 * on a phone. Because entering a password with a remote control is miserable.
 */
@Composable
internal fun TvSetupScreen(onConfigured: (ServiceConfig) -> Unit) {
    var step by remember { mutableStateOf<SetupStep>(SetupStep.Pick) }

    when (val s = step) {
        SetupStep.Pick -> ServerPickStep(
            onManual = { step = SetupStep.Manual },
            onPicked = { url, name -> step = SetupStep.Auth(url, name) },
        )
        SetupStep.Manual -> ManualServerStep(
            onBack = { step = SetupStep.Pick },
            onResolved = { url, name -> step = SetupStep.Auth(url, name) },
        )
        is SetupStep.Auth -> AuthStep(
            baseUrl = s.baseUrl,
            serverName = s.serverName,
            onBack = { step = SetupStep.Pick },
            onConfigured = onConfigured,
        )
    }
}

@Composable
private fun SetupFrame(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Black).padding(horizontal = TvSidePad, vertical = TvTopPad),
    ) {
        Text("sanctumd tv", color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text(title, color = MatrixGreen, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MatrixGreen.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

/** Step 1: the servers the network broadcast turned up. */
@Composable
private fun ServerPickStep(onManual: () -> Unit, onPicked: (String, String) -> Unit) {
    var servers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var scanNonce by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(scanNonce) {
        scanning = true
        servers = jellyfinDiscoverServers()
        scanning = false
    }
    // Move focus onto the list as soon as there is something to focus.
    LaunchedEffect(servers.isNotEmpty(), scanning) {
        if (!scanning) runCatching { firstFocus.requestFocus() }
    }

    SetupFrame(
        title = "pick a server",
        subtitle = when {
            scanning -> "looking for Jellyfin on this network…"
            servers.isEmpty() -> "nothing found on this network, type an address"
            else -> "${servers.size} found on this network"
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(servers, key = { _, it -> it.Id.ifBlank { it.Address } }) { index, server ->
                TvListRow(
                    title = server.displayName,
                    subtitle = server.Address,
                    focusRequester = firstFocus.takeIf { index == 0 && !scanning },
                    trailing = "verbinden ›",
                    onClick = { onPicked(server.Address.trimEnd('/'), server.displayName) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                "type an address",
                focusRequester = firstFocus.takeIf { servers.isEmpty() && !scanning },
                onClick = onManual,
            )
            TvButton(if (scanning) "searching…" else "search again", enabled = !scanning) { scanNonce++ }
        }
    }
}

/** A text field styled for the ten-foot UI (the system IME does the actual typing). */
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        label = { Text(label, fontFamily = Mono, fontSize = 13.sp) },
        singleLine = true,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 17.sp, color = MatrixGreen),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = imeAction,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MatrixGreen,
            unfocusedTextColor = MatrixGreen,
            focusedBorderColor = MatrixGreen,
            unfocusedBorderColor = MatrixGreen.copy(alpha = 0.4f),
            focusedLabelColor = MatrixGreen,
            unfocusedLabelColor = MatrixGreen.copy(alpha = 0.6f),
            cursorColor = MatrixGreen,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
        ),
    )
}

/** Step 1b: type an address, then confirm a Jellyfin actually answers there. */
@Composable
private fun ManualServerStep(onBack: () -> Unit, onResolved: (String, String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    fun connect() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            var lastError: String? = null
            for (url in candidateUrls(input)) {
                val info = runCatching { jellyfinTvPublicInfo(url) }
                    .onFailure { lastError = it.message ?: it.javaClass.simpleName }
                    .getOrNull()
                if (info != null) {
                    checking = false
                    onResolved(url, info.ServerName.ifBlank { url })
                    return@launch
                }
            }
            checking = false
            error = "no Jellyfin at that address" + (lastError?.let { " ($it)" } ?: "")
        }
    }

    SetupFrame(
        title = "add a server",
        subtitle = "IP or address. \"192.168.1.20\" is enough, https is tried first and port 8096 added",
    ) {
        Box(Modifier.width(560.dp)) {
            TvTextField(
                value = input,
                onValueChange = { input = it; error = null },
                label = "Serveradresse",
                modifier = Modifier.fillMaxWidth(),
                focusRequester = fieldFocus,
                imeAction = ImeAction.Done,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(if (checking) "verbinde…" else "verbinden", enabled = !checking && input.isNotBlank()) { connect() }
            TvButton("back", onClick = onBack)
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = ErrRed, fontFamily = Mono, fontSize = 14.sp)
        }
    }
}

/** Step 2: authenticate, Quick Connect by default, username/password on request. */
@Composable
private fun AuthStep(
    baseUrl: String,
    serverName: String,
    onBack: () -> Unit,
    onConfigured: (ServiceConfig) -> Unit,
) {
    var quickCode by remember { mutableStateOf<String?>(null) }
    var quickAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usePassword by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }
    val quickFocus = remember { FocusRequester() }

    fun finish(token: String, userId: String, userName: String) {
        onConfigured(
            ServiceConfig(
                type = ServiceType.JELLYFIN,
                label = serverName.ifBlank { "Jellyfin" },
                baseUrl = baseUrl,
                apiKey = token,
                userId = userId,
                username = userName,
            ),
        )
    }

    // Quick Connect: ask for a code, then poll until the user approves it elsewhere.
    LaunchedEffect(baseUrl, attempt, usePassword) {
        if (usePassword) return@LaunchedEffect
        error = null
        quickCode = null
        quickAvailable = jellyfinQuickConnectAvailable(baseUrl)
        if (quickAvailable != true) return@LaunchedEffect
        val started = runCatching { jellyfinQuickConnectStart(baseUrl) }.getOrElse {
            error = "Quick Connect unavailable: ${it.message ?: it.javaClass.simpleName}"
            quickAvailable = false
            return@LaunchedEffect
        }
        quickCode = started.code
        // The code expires server-side; ~5 minutes of polling is well past that, and the user can
        // always ask for a fresh one.
        repeat(150) {
            delay(2000)
            if (jellyfinQuickConnectApproved(baseUrl, started.secret)) {
                runCatching { jellyfinQuickConnectFinish(baseUrl, started.secret) }
                    .onSuccess { finish(it.accessToken, it.userId, it.userName) }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }
                return@LaunchedEffect
            }
        }
        quickCode = null
        error = "code expired, ask for a new one"
    }

    LaunchedEffect(usePassword) {
        if (usePassword) runCatching { userFocus.requestFocus() } else runCatching { quickFocus.requestFocus() }
    }

    fun login() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { jellyfinTvLogin(baseUrl, username, password) }
                .onSuccess { busy = false; finish(it.accessToken, it.userId, it.userName) }
                .onFailure {
                    busy = false
                    error = "sign-in failed, wrong username or password"
                }
        }
    }

    SetupFrame(title = "sign in", subtitle = "$serverName · $baseUrl") {
        // The password and the Quick Connect secret go over this address in a moment. Say it
        // plainly when that address is not encrypted -- on a TV nobody inspects the URL bar.
        if (isPlainHttp(baseUrl)) {
            Text(
                "not https, what you type next travels unencrypted across this network",
                color = WarnAmberDim, fontFamily = Mono, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (!usePassword) {
            when (quickAvailable) {
                null -> TvMessage("checking how to sign in…", Modifier.padding(vertical = 20.dp))
                true -> QuickConnectPanel(quickCode)
                false -> Text(
                    "Quick Connect is switched off on this server, sign in with a username.",
                    color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    "sign in with a username",
                    focusRequester = quickFocus,
                ) { usePassword = true }
                if (quickAvailable == true) TvButton("new code") { attempt++ }
                TvButton("another server", onClick = onBack)
            }
        } else {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TvTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = "username",
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = userFocus,
                )
                TvTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "password",
                    modifier = Modifier.fillMaxWidth(),
                    password = true,
                    imeAction = ImeAction.Done,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(if (busy) "melde an…" else "anmelden", enabled = !busy && username.isNotBlank()) { login() }
                if (quickAvailable == true) TvButton("back to Quick Connect") { usePassword = false }
                TvButton("another server", onClick = onBack)
            }
        }
        error?.let {
            Spacer(Modifier.height(18.dp))
            Text(it, color = ErrRed, fontFamily = Mono, fontSize = 14.sp)
        }
    }
}

/** The six-digit Quick Connect code, big enough to read from a sofa. */
@Composable
private fun QuickConnectPanel(code: String?) {
    Column {
        Text(
            "1. open Jellyfin on a phone or PC  →  2. menu → Quick Connect  →  3. enter the code",
            color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(2.dp, MatrixGreen, RoundedCornerShape(10.dp))
                .padding(horizontal = 34.dp, vertical = 22.dp),
        ) {
            Text(
                code ?: "······",
                color = MatrixGreen,
                fontFamily = Mono,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (code == null) "hole Code…" else "waiting for approval…",
            color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 13.sp,
        )
    }
}
