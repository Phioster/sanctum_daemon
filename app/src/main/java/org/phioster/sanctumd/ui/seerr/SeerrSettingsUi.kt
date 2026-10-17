package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.SeerrCache
import org.phioster.sanctumd.net.SeerrMainSettings
import org.phioster.sanctumd.net.SeerrOption
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.common.SectionHeader
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Seerr's display languages; the server keeps its own list, this is the part it ships. */
private val LOCALES = listOf(
    "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español", "it" to "Italiano",
    "nl" to "Nederlands", "pt-BR" to "Português (Brasil)", "pl" to "Polski", "sv" to "Svenska", "da" to "Dansk",
    "nb-NO" to "Norsk Bokmål", "ru" to "Русский", "ja" to "日本語", "zh-Hans" to "简体中文",
)

@Composable
private fun Toggle(label: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
            if (hint.isNotBlank()) Text(hint, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 9.sp)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** A setting that names a region or language; blank means "all". Opens a searchable list. */
@Composable
private fun Picker(label: String, value: String, options: List<SeerrOption>, accent: Color, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = when {
        value.isBlank() -> "all"
        else -> value.split('|').joinToString(", ") { c -> options.firstOrNull { it.code == c }?.name ?: c }
    }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(shown, fontFamily = Mono, color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
    }
    if (open) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Surface,
            title = { Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp) },
            text = {
                Column {
                    Field("Search", q) { q = it }
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        Text("all", fontFamily = Mono, color = accent, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(""); open = false }.padding(vertical = 7.dp))
                        options.filter { q.isBlank() || it.name.contains(q, true) || it.code.equals(q, true) }.forEach { o ->
                            Text(
                                o.name, fontFamily = Mono, fontSize = 13.sp,
                                color = if (o.code == value) accent else MatrixGreen,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(o.code); open = false }.padding(vertical = 7.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

/** Seerr's General settings page, the parts that matter day to day. Saved in one go. */
@Composable
internal fun SeerrSettingsDialog(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<SeerrMainSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var regions by remember { mutableStateOf<List<SeerrOption>>(emptyList()) }
    var languages by remember { mutableStateOf<List<SeerrOption>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { vm.seerrMain(config) }.onSuccess { s = it }.onFailure { error = it.message ?: "failed" }
        regions = runCatching { vm.seerrRegionList(config) }.getOrDefault(emptyList())
        languages = runCatching { vm.seerrLanguageList(config) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Seerr settings", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                error?.let { Text("error: $it", fontFamily = Mono, color = ErrRed, fontSize = 12.sp) }
                val v = s ?: run {
                    if (error == null) Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    return@Column
                }
                fun set(f: (SeerrMainSettings) -> SeerrMainSettings) { s = f(v) }
                SectionHeader("GENERAL")
                Field("Application title", v.applicationTitle) { t -> set { it.copy(applicationTitle = t) } }
                Field("Application URL", v.applicationUrl) { t -> set { it.copy(applicationUrl = t) } }
                Picker("Display language", v.locale, LOCALES.map { SeerrOption(it.first, it.second) }, accent) { c -> set { it.copy(locale = c.ifBlank { "en" }) } }
                Toggle("Cache images", "needs a lot of disk space", v.cacheImages) { b -> set { it.copy(cacheImages = b) } }
                Spacer(Modifier.height(8.dp))
                SectionHeader("DISCOVER")
                Picker("Discover region", v.discoverRegion, regions, accent) { c -> set { it.copy(discoverRegion = c) } }
                Picker("Discover language", v.originalLanguage, languages, accent) { c -> set { it.copy(originalLanguage = c) } }
                Picker("Streaming region", v.streamingRegion, regions, accent) { c -> set { it.copy(streamingRegion = c) } }
                Toggle("Hide available media", "from discover, not from search", v.hideAvailable) { b -> set { it.copy(hideAvailable = b) } }
                Toggle("Hide blocklisted items", "from discover, for every user", v.hideBlocklisted) { b -> set { it.copy(hideBlocklisted = b) } }
                Spacer(Modifier.height(8.dp))
                SectionHeader("BLOCKLIST BY TAG")
                Picker("Blocklist region", v.blocklistRegion, regions, accent) { c -> set { it.copy(blocklistRegion = c) } }
                Picker("Blocklist language", v.blocklistLanguage, languages, accent) { c -> set { it.copy(blocklistLanguage = c) } }
                Field("Tags (keyword ids, comma separated)", v.blocklistedTags) { t -> set { it.copy(blocklistedTags = t) } }
                Field("Pages per tag", v.blocklistedTagsLimit.toString()) { t -> t.filter(Char::isDigit).toIntOrNull()?.let { n -> set { it.copy(blocklistedTagsLimit = n) } } }
                Spacer(Modifier.height(8.dp))
                SectionHeader("REQUESTS")
                Toggle("Allow partial series requests", "", v.partialRequestsEnabled) { b -> set { it.copy(partialRequestsEnabled = b) } }
                Toggle("Allow special episodes requests", "", v.enableSpecialEpisodes) { b -> set { it.copy(enableSpecialEpisodes = b) } }
                Field("YouTube URL (self-hosted)", v.youtubeUrl) { t -> set { it.copy(youtubeUrl = t) } }
            }
        },
        confirmButton = {
            TextButton(enabled = s != null && !busy, onClick = {
                val v = s ?: return@TextButton
                busy = true
                scope.launch { val r = vm.seerrMainSave(config, v); busy = false; onSaved(r); if (!r.startsWith("error")) onDismiss() }
            }) { Text(if (busy) "…" else "Save", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

private fun bytesLabel(b: Long) = when {
    b >= 1_048_576 -> "%.1f MB".format(java.util.Locale.ROOT, b / 1_048_576.0)
    b >= 1024 -> "%.1f KB".format(java.util.Locale.ROOT, b / 1024.0)
    else -> "$b B"
}

/** Seerr's API caches with their hit counts; flushing one makes Seerr fetch fresh data. */
@Composable
internal fun SeerrCacheSection(vm: DashboardViewModel, config: ServiceConfig, accent: Color, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var caches by remember { mutableStateOf<List<SeerrCache>?>(null) }
    LaunchedEffect(Unit) { caches = runCatching { vm.seerrCacheList(config) }.getOrDefault(emptyList()) }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
    Spacer(Modifier.height(8.dp))
    SectionHeader("CACHE")
    val cs = caches
    when {
        cs == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
        else -> cs.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontFamily = Mono, color = if (c.keys > 0) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${c.hits} hits · ${c.misses} misses · ${c.keys} keys · ${bytesLabel(c.bytes)}", fontFamily = Mono, color = accent.copy(alpha = 0.6f), fontSize = 9.sp)
                }
                TextButton(enabled = c.keys > 0, onClick = {
                    scope.launch {
                        onMessage("${c.name}: ${vm.seerrCacheFlush(config, c.id)}")
                        caches = runCatching { vm.seerrCacheList(config) }.getOrNull() ?: caches
                    }
                }) { Text("flush", fontFamily = Mono, color = if (c.keys > 0) ErrRed else ErrRed.copy(alpha = 0.3f), fontSize = 11.sp) }
            }
        }
    }
}
