package org.phioster.sanctumd.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.phioster.sanctumd.ui.common.PrimaryButton
import org.phioster.sanctumd.ui.common.SecondaryButton
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.BackgroundMode
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.CustomTheme
import org.phioster.sanctumd.ui.theme.CustomThemes
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.PALETTES
import org.phioster.sanctumd.ui.theme.Palette
import org.phioster.sanctumd.ui.theme.Readability
import org.phioster.sanctumd.ui.theme.ThemeStore
import org.phioster.sanctumd.ui.theme.WarnAmber
import org.phioster.sanctumd.ui.theme.dimInk
import org.phioster.sanctumd.ui.theme.liftedSurface
import org.phioster.sanctumd.ui.theme.withBackground

private val ReadGood = Color(0xFF3DDC84)
private fun levelColor(level: Int) = when (level) { 2 -> ReadGood; 1 -> WarnAmber; else -> ErrRed }

internal fun Color.hsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(toArgb(), it) }
internal fun hsvColor(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
internal fun Color.hex(): String = "%06X".format(toArgb() and 0xFFFFFF)
internal fun parseHex(text: String): Color? =
    text.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

private fun paletteOf(name: String, a: Color, b: Color, s: Color) = Palette("edit", name, a, b, s, liftedSurface(s))

/**
 * Nudges a theme into the readable range: first the accent's brightness, towards whichever end is
 * farther from the background; if that is not enough, the background and cards get darker.
 */
internal fun makeReadable(a: Color, b: Color, s: Color): Triple<Color, Color, Color> {
    fun ok(x: Color, y: Color, z: Color) = Readability.worst(paletteOf("", x, y, z)) >= Readability.GOOD
    if (ok(a, b, s)) return Triple(a, b, s)
    val (h, sat, v) = a.hsv()
    val up = b.hsv()[2] < 0.5f
    for (i in 1..50) {
        val nv = (if (up) v + i * 0.02f else v - i * 0.02f).coerceIn(0f, 1f)
        val na = hsvColor(h, sat, nv)
        if (ok(na, b, s)) return Triple(na, b, s)
    }
    val bright = hsvColor(h, sat.coerceAtMost(0.8f), 1f)
    var nb = b
    var ns = s
    repeat(30) {
        if (ok(bright, nb, ns)) return Triple(bright, nb, ns)
        nb = nb.hsv().let { hsvColor(it[0], it[1], it[2] * 0.85f) }
        ns = ns.hsv().let { hsvColor(it[0], it[1], it[2] * 0.85f) }
    }
    return Triple(bright, nb, ns)
}

/**
 * The theme editor: the three colours of a preset row, each with hex and hue/saturation/brightness
 * sliders. Under every slider a strip shows where along it the theme stays readable.
 */
@Composable
internal fun ThemeEditorDialog(
    editing: CustomTheme?,
    startFrom: Palette,
    mode: BackgroundMode,
    onDismiss: () -> Unit,
    onDone: (activeId: String?) -> Unit,
) {
    val context = LocalContext.current
    val seed = editing?.palette() ?: startFrom
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var accent by remember { mutableStateOf(seed.accent) }
    var background by remember { mutableStateOf(seed.background) }
    var surface by remember { mutableStateOf(seed.surface) }
    var confirmDelete by remember { mutableStateOf(false) }

    val palette = paletteOf(name, accent, background, surface)
    val worst = Readability.worst(palette)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Black).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(AppIcons.Back, contentDescription = "Back", tint = MatrixGreen) }
                Text(if (editing == null) "new theme" else "edit theme", fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            ThemePreview(palette.withBackground(mode))
            ReadabilityLine(worst)

            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                label = { Text("Name", fontFamily = Mono) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Text("START FROM", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (PALETTES + CustomThemes.all(context).map { it.palette() }).forEach { p ->
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(p.surface)
                            .clickable { accent = p.accent; background = p.background; surface = p.surface }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
                        Spacer(Modifier.width(6.dp))
                        Text(p.label, fontFamily = Mono, color = p.accent, fontSize = 12.sp)
                    }
                }
            }

            ColorEditor("ACCENT · text, numbers, buttons", accent, { accent = it }) { Readability.worst(paletteOf("", it, background, surface)) }
            ColorEditor("BACKGROUND", background, { background = it }) { Readability.worst(paletteOf("", accent, it, surface)) }
            ColorEditor("CARDS", surface, { surface = it }) { Readability.worst(paletteOf("", accent, background, it)) }

            Spacer(Modifier.height(12.dp))
            if (Readability.level(worst) < 2) {
                SecondaryButton("make readable", modifier = Modifier.fillMaxWidth()) {
                    makeReadable(accent, background, surface).let { (a, b, s) -> accent = a; background = b; surface = s }
                }
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton("save & use", modifier = Modifier.fillMaxWidth(), enabled = worst >= Readability.MIN) {
                val theme = CustomTheme.from(editing?.id ?: CustomThemes.newId(), name, accent, background, surface)
                ThemeStore.saveCustom(context, theme)
                onDone(theme.id)
            }
            if (worst < Readability.MIN) {
                Text("Too hard to read to save. Move the sliders into the green, or tap \"make readable\".",
                    fontFamily = Mono, color = ErrRed, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (editing != null) {
                TextButton(onClick = {
                    if (confirmDelete) { ThemeStore.deleteCustom(context, editing.id); onDone(null) } else confirmDelete = true
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (confirmDelete) "tap again to delete" else "delete theme", fontFamily = Mono, color = ErrRed)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadabilityLine(worst: Double) {
    val level = Readability.level(worst)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(levelColor(level)))
        Spacer(Modifier.width(8.dp))
        Text(
            when (level) { 2 -> "readable"; 1 -> "readable, but tight"; else -> "hard to read" } +
                " · contrast %.1f (in every background mode)".format(worst),
            fontFamily = Mono, color = levelColor(level), fontSize = 12.sp,
        )
    }
}

/** A small mock of the app in the edited colours: title, a card with numbers, a chip and a button. */
@Composable
private fun ThemePreview(p: Palette) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(p.background)
            .border(1.dp, MatrixGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(14.dp),
    ) {
        Text("> sanctumd_", fontFamily = Mono, color = p.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).padding(12.dp)) {
            Text("Jellyfin", fontFamily = Mono, color = p.accent, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("59" to "MOVIES", "35" to "SERIES", "4" to "PLAYING").forEach { (n, l) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(n, fontFamily = Mono, color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(l, fontFamily = Mono, color = p.dimInk(), fontSize = 9.sp)
                    }
                }
            }
            Text("admin: demo · last scan 3 min ago", fontFamily = Mono, color = p.dimInk(), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("view: mixed", fontFamily = Mono, color = p.accent, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.surfaceHi).padding(horizontal = 10.dp, vertical = 6.dp))
            Spacer(Modifier.weight(1f))
            Text("save", fontFamily = Mono, color = p.background, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** One colour: swatch, hex field, and hue/saturation/brightness sliders with readability strips. */
@Composable
private fun ColorEditor(label: String, color: Color, onChange: (Color) -> Unit, worstWith: (Color) -> Double) {
    // Kept apart from the colour: a grey has no hue, and recomputing it would make the hue slider jump.
    var hsv by remember { mutableStateOf(color.hsv()) }
    var hexText by remember { mutableStateOf(color.hex()) }
    LaunchedEffect(color) {
        if (hsvColor(hsv[0], hsv[1], hsv[2]).toArgb() != color.toArgb()) hsv = color.hsv()
        if (parseHex(hexText)?.toArgb() != color.toArgb()) hexText = color.hex()
    }
    fun set(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h, s, v)
        onChange(hsvColor(h, s, v))
    }

    Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(color).border(1.dp, MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = hexText, singleLine = true,
            onValueChange = { t -> hexText = t.take(7); parseHex(t)?.let { hsv = it.hsv(); onChange(it) } },
            prefix = { Text("#", fontFamily = Mono) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, color = MatrixGreen),
            modifier = Modifier.width(150.dp),
        )
    }
    val (h, s, v) = hsv
    ChannelSlider("hue", h / 360f, { hsvColor(it * 360f, s, v) }, worstWith) { set(it * 360f, s, v) }
    ChannelSlider("saturation", s, { hsvColor(h, it, v) }, worstWith) { set(h, it, v) }
    ChannelSlider("brightness", v, { hsvColor(h, s, it) }, worstWith) { set(h, s, it) }
}

/**
 * A slider over its own colour ramp, with a strip below: green where the theme reads well at that
 * position, amber where it gets tight, red where it is hard to read.
 */
@Composable
private fun ChannelSlider(name: String, value: Float, colorAt: (Float) -> Color, worstWith: (Color) -> Double, onChange: (Float) -> Unit) {
    val steps = 48
    // Recomputed each frame on purpose: 49 contrast checks are cheap, and stale strips would lie.
    val ramp = (0..steps).map { colorAt(it / steps.toFloat()) }
    val levels = ramp.map { Readability.level(worstWith(it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    val w = size.width / ramp.size
                    ramp.forEachIndexed { i, c -> drawRect(c, Offset(i * w, 0f), Size(w + 1f, size.height)) }
                }
                Spacer(Modifier.height(3.dp))
                Canvas(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                    val w = size.width / levels.size
                    levels.forEachIndexed { i, l -> drawRect(levelColor(l), Offset(i * w, 0f), Size(w + 1f, size.height)) }
                }
            }
            Slider(
                value = value, onValueChange = onChange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}
