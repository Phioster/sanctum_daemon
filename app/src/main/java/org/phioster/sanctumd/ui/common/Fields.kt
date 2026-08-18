package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.SurfaceHi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun NotifyToggleRow(label: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = Mono, color = if (enabled) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 14.sp)
            if (sub.isNotBlank()) Text(sub, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Black, checkedTrackColor = MatrixGreen, uncheckedThumbColor = MatrixGreen.copy(alpha = 0.6f), uncheckedTrackColor = Surface, uncheckedBorderColor = MatrixGreen.copy(alpha = 0.4f)),
        )
    }
}


@Composable
internal fun JellyToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A category shown as a tile on the Jellyfin dashboard overview. */
internal data class DashCat(val key: String, val label: String, val count: Int?, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** A clickable dashboard category tile: icon + label + item count. */
@Composable
internal fun DashTile(label: String, count: Int?, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MatrixGreen.copy(alpha = 0.06f))
            .border(1.dp, MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(count?.toString() ?: "…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

/** Total watch time as a short "12h 34m" string. */

@Composable
internal fun DropdownField(label: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", fontFamily = Mono, color = MatrixGreen)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o, fontFamily = Mono) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}


@Composable
internal fun Field(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Mono) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
internal fun ActionBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) { Text(label, fontFamily = Mono) }
}

/** The app-wide section header: a short accent tick + a bold mono label. Use this everywhere a
 *  screen labels a group of content, so every screen reads the same. */
@Composable
internal fun SectionHeader(label: String, accent: Color = MatrixGreen, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 13.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.size(8.dp))
        Text(label.uppercase(), fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** The app-wide muted hint line (loading / empty / "nothing here"). One style for all of them. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 12.sp, modifier = modifier)
}

/** Filled accent button (dark ink) — the primary in-content action everywhere. */
@Composable
internal fun PrimaryButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = MatrixGreen, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.3f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, fontFamily = Mono, color = Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Outlined accent button (accent text on a slightly-raised surface) — the secondary action. */
@Composable
internal fun SecondaryButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = MatrixGreen, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(SurfaceHi)
            .border(1.dp, accent.copy(alpha = if (enabled) 0.6f else 0.25f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) accent else accent.copy(alpha = 0.4f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, fontFamily = Mono, color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** A compact bordered action chip — the standard chip for inline actions (back / scan / filters). */
@Composable
internal fun AppChip(label: String, accent: Color = MatrixGreen, onClick: () -> Unit) {
    Text(
        label, fontFamily = Mono, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceHi)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
