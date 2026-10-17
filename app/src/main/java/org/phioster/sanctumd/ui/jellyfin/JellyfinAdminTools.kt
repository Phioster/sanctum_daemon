package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellySession
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.JellyApiKey
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * A remote for another client, mostly a TV: navigation, volume and the OSD. Each press goes
 * out at once; the last answer is shown so a client that ignores a command is noticeable.
 */
@Composable
internal fun JellyRemoteDialog(vm: DashboardViewModel, config: ServiceConfig, session: JellySession, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<String?>(null) }
    fun send(cmd: String) = scope.launch { last = "$cmd: ${vm.jellyfinRemoteCmd(config, session.id, cmd)}" }

    @Composable
    fun Key(label: String, cmd: String, modifier: Modifier = Modifier) {
        OutlinedButton(onClick = { send(cmd) }, modifier = modifier) { Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, maxLines = 1) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Remote · ${session.device.ifBlank { session.client }}", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Key("up", "MoveUp") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    Key("left", "MoveLeft"); Key("OK", "Select"); Key("right", "MoveRight")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Key("down", "MoveDown") }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    Key("back", "Back"); Key("home", "GoHome"); Key("osd", "ToggleOsd")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    Key("vol -", "VolumeDown"); Key("mute", "ToggleMute"); Key("vol +", "VolumeUp")
                }
                last?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = Mono, color = if (it.contains("error")) ErrRed else MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** One API key. A tap copies the key itself, the only way to hand it to another app. */
@Composable
internal fun JellyApiKeyRow(item: JellyApiKey, accent: Color, onRevoke: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable {
                    clipboard.setText(AnnotatedString(item.token))
                    android.widget.Toast.makeText(context, "key copied", android.widget.Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(item.app.ifBlank { "?" }, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                Text(
                    listOf("created ${item.created}", item.lastUsed.takeIf { it.isNotBlank() }?.let { "used $it" }).filterNotNull().joinToString(" · ") +
                        " · ${item.token.take(6)}…",
                    fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1,
                )
            }
            TextButton(onClick = onRevoke) { Text("revoke", fontFamily = Mono, color = ErrRed.copy(alpha = 0.85f)) }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
    }
}

@Composable
internal fun JellyNewApiKeyDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var app by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New API key", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Text("Name it after what will use it, e.g. Sonarr.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                Field("App name", app) { app = it }
            }
        },
        confirmButton = { TextButton(enabled = app.isNotBlank(), onClick = { onCreate(app) }) { Text("Create", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** A revoked key stops whatever uses it, so the dialog names the app. */
@Composable
internal fun JellyRevokeKeyDialog(item: JellyApiKey, onDismiss: () -> Unit, onRevoke: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Revoke ${item.app}?", fontFamily = Mono, color = MatrixGreen) },
        text = { Text("Whatever uses this key can no longer reach Jellyfin.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onRevoke) { Text("Revoke", fontFamily = Mono, color = ErrRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** The subtitle format Jellyfin should store a file as, from its name; null when unsupported. */
internal fun subtitleFormatOf(fileName: String): String? =
    fileName.substringAfterLast('.', "").lowercase().takeIf { it in setOf("srt", "ass", "ssa", "vtt", "sub") }

/** An external subtitle from the phone, e.g. a German .srt the providers don't have. */
@Composable
internal fun JellyfinUploadSubtitleDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    target: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var picked by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var language by remember { mutableStateOf("ger") }
    var forced by remember { mutableStateOf(false) }
    var hearingImpaired by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } ?: uri.lastPathSegment.orEmpty()
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            when {
                subtitleFormatOf(name) == null -> { picked = null; error = "not a subtitle file: $name" }
                bytes == null -> { picked = null; error = "couldn't read $name" }
                else -> { picked = name to bytes; error = null }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Upload subtitle", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column {
                Text(target.name, fontFamily = Mono, color = accent, fontSize = 12.sp, maxLines = 1)
                TextButton(onClick = { picker.launch("*/*") }) {
                    Text(picked?.first ?: "choose .srt / .ass / .vtt", fontFamily = Mono, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                error?.let { Text(it, fontFamily = Mono, color = ErrRed, fontSize = 11.sp) }
                Field("Language (3 letters)", language) { language = it.take(3).lowercase() }
                Row(Modifier.fillMaxWidth().clickable { forced = !forced }, verticalAlignment = Alignment.CenterVertically) {
                    Text("forced (only foreign parts)", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(checked = forced, onCheckedChange = { forced = it })
                }
                Row(Modifier.fillMaxWidth().clickable { hearingImpaired = !hearingImpaired }, verticalAlignment = Alignment.CenterVertically) {
                    Text("for the hearing impaired", fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(checked = hearingImpaired, onCheckedChange = { hearingImpaired = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked != null && language.length == 3 && !busy, onClick = {
                val (name, bytes) = picked ?: return@TextButton
                busy = true
                scope.launch {
                    val msg = vm.jellyfinSubtitleUpload(config, target.id, language, subtitleFormatOf(name) ?: "srt", forced, hearingImpaired, bytes)
                    busy = false
                    onDone(msg)
                }
            }) { Text(if (busy) "…" else "Upload", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
