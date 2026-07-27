package org.phioster.sanctumd.widget

import org.phioster.sanctumd.ui.theme.ThemeState
import org.phioster.sanctumd.ui.theme.ThemeStore
import org.phioster.sanctumd.ui.theme.dimInk
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.HttpShortcut
import org.phioster.sanctumd.model.ServiceType

private val Bg: Color get() = ThemeState.palette.background
private val Green: Color get() = ThemeState.palette.accent
private val Dim: Color get() = ThemeState.palette.dimInk()
private val Chip = Color(0xFF13251A)

/** Homescreen widget: one tappable button per configured HTTP shortcut. */
class ShortcutsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ThemeState.palette = ThemeStore.read(context)
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
            modifier = GlanceModifier.fillMaxSize().background(Bg).cornerRadius(14.dp).padding(6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            if (entries.isEmpty()) {
                Text(
                    "no shortcuts — add a Shortcuts service in the app",
                    style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp, textAlign = TextAlign.Center),
                )
            } else {
                entries.forEachIndexed { i, e ->
                    if (i > 0) Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "▸ ${e.shortcut.name}",
                        style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, textAlign = TextAlign.Center),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(Chip)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .clickable(
                                actionRunCallback<RunShortcutAction>(
                                    actionParametersOf(
                                        RunShortcutAction.serviceIdKey to e.serviceId,
                                        RunShortcutAction.shortcutNameKey to e.shortcut.name,
                                    ),
                                ),
                            ),
                    )
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
