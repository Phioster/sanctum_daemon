package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ServiceLogo
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** One entry of a service's menu. */
internal data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** A titled group of entries; empty groups are left out. */
internal data class MenuSection(val title: String, val actions: List<MenuAction>)

/**
 * The ⋮ button of a service screen and the sheet it opens.
 *
 * The *arr screens had grown a dropdown of fifteen plain lines, taller than the phone, with
 * "Delete" one slip below "Edit". The sheet sorts them instead: the one or two things you came
 * for as buttons on top, the rest as two-column tiles under a heading, and the service itself
 * (edit, remove) apart at the bottom, where removing asks once more.
 */
@Composable
internal fun ServiceMenuButton(
    config: ServiceConfig,
    primary: List<MenuAction> = emptyList(),
    sections: List<MenuSection> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(AppIcons.More, contentDescription = "More", tint = MatrixGreen) }
    if (open) {
        ServiceMenuSheet(config, primary, sections, onEdit, onDelete, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceMenuSheet(
    config: ServiceConfig,
    primary: List<MenuAction>,
    sections: List<MenuSection>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    fun run(action: () -> Unit) { onDismiss(); action() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 12.dp).navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(config.type, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(config.label, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        config.baseUrl.substringAfter("://").trimEnd('/'),
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (primary.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    primary.forEachIndexed { i, a ->
                        val m = Modifier.weight(1f)
                        if (i == 0) PrimaryButton(a.label, m, a.icon) { run(a.onClick) }
                        else SecondaryButton(a.label, m, a.icon) { run(a.onClick) }
                    }
                }
            }

            sections.filter { it.actions.isNotEmpty() }.forEach { section ->
                Spacer(Modifier.height(18.dp))
                SectionHeader(section.title)
                Spacer(Modifier.height(8.dp))
                section.actions.chunked(2).forEach { pair ->
                    Row(Modifier.padding(bottom = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { a -> MenuTile(a, Modifier.weight(1f).fillMaxHeight()) { run(a.onClick) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
            Spacer(Modifier.height(4.dp))
            if (!confirmDelete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FooterAction("edit service", AppIcons.Edit, MatrixGreen) { run(onEdit) }
                    Spacer(Modifier.weight(1f))
                    FooterAction("remove", AppIcons.Delete, ErrRed) { confirmDelete = true }
                }
            } else {
                Text(
                    "Remove ${config.label} from the app? The server itself is not touched.",
                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("keep", Modifier.weight(1f)) { confirmDelete = false }
                    PrimaryButton("remove", Modifier.weight(1f), AppIcons.Delete, accent = ErrRed) { run(onDelete) }
                }
            }
        }
    }
}

@Composable
private fun MenuTile(action: MenuAction, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(MatrixGreen.copy(alpha = 0.06f))
            .border(1.dp, MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(action.label, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FooterAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = Mono, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
