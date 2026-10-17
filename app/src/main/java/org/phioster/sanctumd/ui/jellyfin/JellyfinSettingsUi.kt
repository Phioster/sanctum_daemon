package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.phioster.sanctumd.model.JellyLibrary
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.JfChoiceSources
import org.phioster.sanctumd.net.JfField
import org.phioster.sanctumd.net.JfFetchers
import org.phioster.sanctumd.net.JfSection
import org.phioster.sanctumd.net.appliesTo
import org.phioster.sanctumd.net.at
import org.phioster.sanctumd.net.changedKeys
import org.phioster.sanctumd.net.fetchersOf
import org.phioster.sanctumd.net.formBool
import org.phioster.sanctumd.net.formList
import org.phioster.sanctumd.net.formText
import org.phioster.sanctumd.net.parseFormNumber
import org.phioster.sanctumd.net.parseIntList
import org.phioster.sanctumd.net.typedLike
import org.phioster.sanctumd.net.with
import org.phioster.sanctumd.net.withFetchers
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.PrimaryButton
import org.phioster.sanctumd.ui.common.SecondaryButton
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.SurfaceHi
import org.phioster.sanctumd.ui.theme.WarnAmber

/** Server or transcoding settings, as a page inside the admin dashboard. */
@Composable
internal fun JellySettingsPage(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    which: String,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<JsonObject?>(null) }
    var sources by remember { mutableStateOf(JfChoiceSources(emptyMap())) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(which, reloadTick) {
        error = null
        runCatching { if (which == "server") vm.jfServerSettings(config) else vm.jfEncodingSettings(config) }
            .onSuccess { original = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
        if (which == "server") sources = runCatching { vm.jfChoiceSources(config) }.getOrDefault(sources)
    }
    val o = original
    when {
        error != null -> Text("could not load: $error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        o == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
        else -> {
            if (which == "transcoding") {
                Text(
                    "Hardware acceleration only works when the server's ffmpeg can reach the GPU. Check the Jellyfin log after the first transcode.",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
            JellySettingsForm(
                sections = if (which == "server") org.phioster.sanctumd.net.SERVER_SETTINGS else org.phioster.sanctumd.net.ENCODING_SETTINGS,
                collectionType = null, original = o, sources = sources, accent = accent,
                onSave = { draft ->
                    scope.launch {
                        val msg = if (which == "server") vm.jfSaveServerSettings(config, draft) else vm.jfSaveEncodingSettings(config, draft)
                        onMessage(msg)
                        if (!msg.startsWith("error")) reloadTick++
                    }
                },
            )
        }
    }
}

/** A library's options, full screen, opened from the library dialog. */
@Composable
internal fun JellyLibraryOptionsDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    library: JellyLibrary,
    accent: Color,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<org.phioster.sanctumd.net.JfLibraryOptions?>(null) }
    var sources by remember { mutableStateOf(JfChoiceSources(emptyMap())) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(library.id) {
        runCatching { vm.jfLibraryOptions(config, library.id, library.collectionType) }
            .onSuccess { d -> data = d; sources = runCatching { vm.jfChoiceSources(config, d.available) }.getOrDefault(sources) }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Black).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(AppIcons.Back, contentDescription = "Back", tint = accent) }
                Column {
                    Text(library.name, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("library options · ${library.collectionType.ifBlank { "mixed" }}", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
            val d = data
            when {
                error != null -> Text("could not load: $error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
                d == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f))
                else -> JellySettingsForm(
                    sections = org.phioster.sanctumd.net.LIBRARY_SETTINGS, collectionType = library.collectionType,
                    original = d.options, sources = sources, accent = accent, defaultable = true,
                    extra = { draft, set -> FetcherEditor(fetchersOf(draft, d.available), accent) { f -> set(draft.withFetchers(f, d.available)) } },
                    onSave = { draft ->
                        scope.launch {
                            val msg = vm.jfSaveLibraryOptions(config, library.id, library.name, draft)
                            onMessage(msg)
                            if (!msg.startsWith("error")) onDismiss()
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The fields of [sections] over a draft of [original]. Changed fields are marked; save sends the
 * whole object back, only the touched keys different. [defaultable] adds an empty choice ("server
 * default") to server-provided lists, as library settings fall back to the server's.
 */
@Composable
private fun JellySettingsForm(
    sections: List<JfSection>,
    collectionType: String?,
    original: JsonObject,
    sources: JfChoiceSources,
    accent: Color,
    defaultable: Boolean = false,
    extra: (@Composable (JsonObject, (JsonObject) -> Unit) -> Unit)? = null,
    onSave: (JsonObject) -> Unit,
) {
    var draft by remember(original) { mutableStateOf(original) }
    var invalid by remember(original) { mutableStateOf(setOf<String>()) }
    val changes = changedKeys(original, draft)

    sections.forEach { section ->
        val fields = section.fields.filter { collectionType == null || it.appliesTo(collectionType) }
        if (fields.isEmpty()) return@forEach
        Text(section.title.uppercase(), fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        fields.forEach { f ->
            val changed = changes.any { it == f.key || it.startsWith(f.key + ".") }
            FieldRow(f, draft.at(f.path), changed, sources, accent, defaultable,
                onValid = { v -> draft = draft.with(f.path, v); invalid = invalid - f.key },
                onInvalid = { invalid = invalid + f.key },
            )
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.08f))
        }
    }
    extra?.invoke(draft) { draft = it }

    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        if (changes.isEmpty()) "no changes" else "save ${changes.size} change${if (changes.size == 1) "" else "s"}",
        modifier = Modifier.fillMaxWidth(), accent = accent, enabled = changes.isNotEmpty() && invalid.isEmpty(),
    ) { onSave(draft) }
    if (changes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SecondaryButton("undo changes", modifier = Modifier.fillMaxWidth(), accent = accent) { draft = original; invalid = emptySet() }
    }
    if (invalid.isNotEmpty()) {
        Text("Fix the fields marked red first.", fontFamily = Mono, color = ErrRed, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldRow(
    f: JfField,
    value: kotlinx.serialization.json.JsonElement?,
    changed: Boolean,
    sources: JfChoiceSources,
    accent: Color,
    defaultable: Boolean,
    onValid: (kotlinx.serialization.json.JsonElement) -> Unit,
    onInvalid: () -> Unit,
) {
    val labelColor = if (changed) WarnAmber else MatrixGreen
    @Composable fun Label(modifier: Modifier = Modifier) = Column(modifier) {
        Text((if (changed) "• " else "") + f.label, fontFamily = Mono, color = labelColor, fontSize = 13.sp)
        if (f.hint.isNotBlank()) Text(f.hint, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        when (f) {
            is JfField.Toggle -> Row(verticalAlignment = Alignment.CenterVertically) {
                Label(Modifier.weight(1f))
                Switch(
                    checked = value.formBool(), onCheckedChange = { onValid(JsonPrimitive(it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Black, checkedTrackColor = accent),
                )
            }
            is JfField.Text, is JfField.Number, is JfField.IntList -> {
                Label()
                fun parse(t: String): kotlinx.serialization.json.JsonElement? = when (f) {
                    is JfField.Number -> parseFormNumber(f, t)
                    is JfField.IntList -> parseIntList(t)
                    else -> JsonPrimitive(t)
                }
                // The typed text is kept apart from the value: "1." is on its way to 1.5, not 1.0.
                var text by remember(f.key) { mutableStateOf(value.formText()) }
                var bad by remember(f.key) { mutableStateOf(false) }
                LaunchedEffect(value) {
                    // Only an outside change (undo, reload) rewrites what is typed.
                    if (parse(text) != value && !bad) text = value.formText()
                }
                OutlinedTextField(
                    value = text, singleLine = true, isError = bad,
                    onValueChange = { t ->
                        text = t
                        val parsed = parse(t)
                        bad = parsed == null
                        if (parsed != null) onValid(parsed) else onInvalid()
                    },
                    supportingText = if (bad && f is JfField.Number) ({ Text("a number from ${fmt(f.min)} to ${fmt(f.max)}", fontFamily = Mono, fontSize = 10.sp) }) else null,
                    textStyle = TextStyle(fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            is JfField.Choice -> {
                val options = (if (f.source != null) sources.lists[f.source].orEmpty() else f.options)
                    .let { if (defaultable && f.source != null) listOf("" to "server default") + it else it }
                val current = value.formText()
                var open by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
                    Label(Modifier.weight(1f))
                    Text(options.firstOrNull { it.first == current }?.second ?: current.ifBlank { "—" }, fontFamily = Mono, color = accent, fontSize = 13.sp)
                    Icon(AppIcons.Expanded, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Box {
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                            options.forEach { (v, l) ->
                                DropdownMenuItem(
                                    text = { Text(l, fontFamily = Mono, color = if (v == current) accent else MatrixGreen) },
                                    onClick = { open = false; onValid(typedLike(value, v)) },
                                )
                            }
                        }
                    }
                }
            }
            is JfField.Multi -> {
                Label()
                val options = if (f.source != null) sources.lists[f.source].orEmpty() else f.options
                val selected = value.formList()
                fun set(list: List<String>) = onValid(JsonArray(list.map(::JsonPrimitive)))
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Long server lists (languages) show what is picked plus an "add" menu; short ones show every option.
                    val shown = if (options.size > 12) selected.map { s -> s to (options.firstOrNull { it.first == s }?.second ?: s) } else options
                    shown.forEach { (v, l) ->
                        val on = v in selected
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) accent else SurfaceHi)
                                .clickable { set(if (on) selected - v else selected + v) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(l, fontFamily = Mono, fontSize = 12.sp, color = if (on) Black else accent)
                            if (on && options.size > 12) {
                                Spacer(Modifier.width(4.dp))
                                Icon(AppIcons.Cancel, contentDescription = "Remove", tint = Black, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    if (options.size > 12) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            Text("+ add", fontFamily = Mono, fontSize = 12.sp, color = accent,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceHi).clickable { open = true }.padding(horizontal = 10.dp, vertical = 6.dp))
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                                options.filterNot { it.first in selected }.forEach { (v, l) ->
                                    DropdownMenuItem(text = { Text(l, fontFamily = Mono, color = MatrixGreen) }, onClick = { open = false; set(selected + v) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

/** Metadata and image providers per item type: tick to use, arrows for the order they are asked in. */
@Composable
private fun FetcherEditor(lists: List<JfFetchers>, accent: Color, onChange: (JfFetchers) -> Unit) {
    if (lists.isEmpty()) return
    Text("PROVIDERS", fontFamily = Mono, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
    Text("Asked top to bottom; the first one with an answer wins.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
    lists.forEach { f ->
        Text("${f.type} · ${f.kind.lowercase()}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        f.order.forEachIndexed { i, name ->
            val on = name in f.enabled
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { onChange(f.copy(enabled = if (on) f.enabled - name else f.enabled + name)) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (on) AppIcons.Selected else AppIcons.NotSelected, contentDescription = if (on) "used" else "not used", tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontFamily = Mono, color = if (on) MatrixGreen else MatrixGreen.copy(alpha = 0.45f), fontSize = 13.sp)
                }
                IconButton(enabled = i > 0, onClick = { onChange(f.copy(order = f.order.toMutableList().apply { add(i - 1, removeAt(i)) })) }) {
                    Icon(AppIcons.MoveUp, contentDescription = "Move up", tint = accent.copy(alpha = if (i > 0) 0.9f else 0.2f), modifier = Modifier.size(16.dp))
                }
                IconButton(enabled = i < f.order.lastIndex, onClick = { onChange(f.copy(order = f.order.toMutableList().apply { add(i + 1, removeAt(i)) })) }) {
                    Icon(AppIcons.MoveDown, contentDescription = "Move down", tint = accent.copy(alpha = if (i < f.order.lastIndex) 0.9f else 0.2f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
