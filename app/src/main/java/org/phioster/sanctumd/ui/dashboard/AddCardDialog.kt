package org.phioster.sanctumd.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
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
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ServiceLogo

@Composable
internal fun AddCardDialog(
    services: List<ServiceConfig>,
    onDismiss: () -> Unit,
    onAdd: (org.phioster.sanctumd.model.CardType, String) -> Unit,
) {
    // Card types grouped by the service they pull from, only for service types the
    // user actually has configured — like nzb360's per-service "Add new card" sheet.
    // Service-less types (Section, Quick Buttons) live in a "Layout" group shown first.
    val groups = remember(services) {
        val byService = CardType.entries.groupBy { it.service }
        val layout = byService[null]?.let { listOf<Pair<ServiceType?, List<CardType>>>(null to it) } ?: emptyList()
        // Quick Buttons is also offered per service (bound to that service's actions).
        layout + byService.filterKeys { st -> st != null && services.any { it.type == st } }
            .map { (st, types) -> st to (types + CardType.QUICKBUTTONS) }
    }
    fun keyOf(st: ServiceType?) = st?.name ?: "layout"
    var expandedKey by remember { mutableStateOf(groups.firstOrNull()?.let { keyOf(it.first) } ?: "") }
    var pendingType by remember { mutableStateOf<CardType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add card", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) {
                    Text("no services configured yet", fontFamily = Mono, color = ErrRed, fontSize = 13.sp)
                }
                groups.forEach { (svcType, types) ->
                    val accent = if (svcType != null) Color(svcType.accent) else MatrixGreen
                    val open = expandedKey == keyOf(svcType)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { expandedKey = if (open) "" else keyOf(svcType); pendingType = null }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (svcType != null) ServiceLogo(svcType, 18.dp) else Text("●", color = accent, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(svcType?.label ?: "Layout", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${types.size} cards", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (open) "▾" else "▸", fontFamily = Mono, color = MatrixGreen)
                    }
                    if (open) {
                        types.forEach { t ->
                            val cfgs = services.filter { it.type == svcType }
                            Text(
                                "› ${t.label}",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        when {
                                            svcType == null -> onAdd(t, "") // service-less card
                                            cfgs.size == 1 -> onAdd(t, cfgs.first().id)
                                            else -> pendingType = if (pendingType == t) null else t
                                        }
                                    }
                                    .padding(start = 20.dp, top = 7.dp, bottom = 7.dp),
                            )
                            // If several services of this type exist, pick which one.
                            if (pendingType == t && svcType != null) {
                                cfgs.forEach { c ->
                                    Text(
                                        "  → ${c.label}",
                                        fontFamily = Mono, color = accent, fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().clickable { onAdd(t, c.id) }.padding(start = 40.dp, top = 6.dp, bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
