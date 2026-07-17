package org.phioster.nexarr.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import org.phioster.nexarr.data.ServiceStore
import org.phioster.nexarr.model.HttpShortcut
import org.phioster.nexarr.model.ServiceType

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Dim = Color(0xFF7A9A7A)
private val Chip = Color(0xFF13251A)

/** Homescreen widget: one tappable button per configured HTTP shortcut. */
class ShortcutsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val services = runCatching { ServiceStore(context).services.first() }.getOrDefault(emptyList())
        // Flatten every shortcut across all SHORTCUTS services, carrying its owning service id.
        val entries = services
            .filter { it.type == ServiceType.SHORTCUTS }
            .flatMap { svc -> svc.shortcuts.map { ShortcutEntry(svc.id, it) } }
        provideContent { Content(entries) }
    }

    @Composable
    private fun Content(entries: List<ShortcutEntry>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(10.dp),
        ) {
            Text(
                "⚡ sanctumd",
                style = TextStyle(color = ColorProvider(Green), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (entries.isEmpty()) {
                Text(
                    "no shortcuts — add a Shortcuts service in the app",
                    style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp),
                )
            } else {
                LazyColumn {
                    items(entries.size) { i ->
                        val e = entries[i]
                        Column {
                            Text(
                                "▸ ${e.shortcut.name}",
                                style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp),
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(Chip)
                                    .cornerRadius(10.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .clickable(
                                        actionRunCallback<RunShortcutAction>(
                                            actionParametersOf(
                                                RunShortcutAction.serviceIdKey to e.serviceId,
                                                RunShortcutAction.shortcutNameKey to e.shortcut.name,
                                            ),
                                        ),
                                    ),
                            )
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class ShortcutEntry(val serviceId: String, val shortcut: HttpShortcut)

/** Wires [ShortcutsWidget] into the AppWidget framework. */
class ShortcutsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutsWidget()
}
