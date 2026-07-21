package org.phioster.sanctumd.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.phioster.sanctumd.R

private val Grey = Color(0xFF2C2C2E) // Nothing-widget-style neutral dark grey

/** White monochrome icons offered for the 1×1 shortcut widget (index stored per widget). */
val WIDGET_ICONS = listOf(
    R.drawable.ic_wi_bolt, R.drawable.ic_wi_power, R.drawable.ic_wi_home, R.drawable.ic_wi_play,
    R.drawable.ic_wi_refresh, R.drawable.ic_wi_bulb, R.drawable.ic_wi_wifi, R.drawable.ic_wi_server,
    R.drawable.ic_wi_fire, R.drawable.ic_wi_settings, R.drawable.ic_wi_check, R.drawable.ic_wi_lock,
)

/**
 * A 1×1 icon widget that fires a single chosen HTTP shortcut (like the HTTP Shortcuts
 * app's icon widget). The chosen shortcut (serviceId + name) is stored per widget
 * instance in Glance state, set by [ShortcutWidgetConfigActivity] when it's placed.
 */
class ShortcutIconWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val serviceId = prefs[serviceIdKey] ?: ""
            val name = prefs[nameKey] ?: ""
            val idx = (prefs[iconKey]?.toIntOrNull() ?: 0).coerceIn(0, WIDGET_ICONS.lastIndex)
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(Grey)
                    .cornerRadius(18.dp)
                    .padding(4.dp)
                    .clickable(
                        actionRunCallback<RunShortcutAction>(
                            actionParametersOf(
                                RunShortcutAction.serviceIdKey to serviceId,
                                RunShortcutAction.shortcutNameKey to name,
                            ),
                        ),
                    ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(WIDGET_ICONS[idx]),
                    contentDescription = null,
                    modifier = GlanceModifier.size(30.dp),
                )
            }
        }
    }

    companion object {
        val serviceIdKey = stringPreferencesKey("icon_service_id")
        val nameKey = stringPreferencesKey("icon_shortcut_name")
        val iconKey = stringPreferencesKey("icon_index")
    }
}

/** Wires [ShortcutIconWidget] into the AppWidget framework. */
class ShortcutIconWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutIconWidget()
}
