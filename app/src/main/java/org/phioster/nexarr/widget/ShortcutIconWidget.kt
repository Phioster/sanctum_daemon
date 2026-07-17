package org.phioster.nexarr.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val Green = Color(0xFF00FF41)
private val Grey = Color(0xFF2C2C2E) // Nothing-widget-style neutral dark grey

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
            val emoji = prefs[emojiKey]?.takeIf { it.isNotBlank() } ?: "⚡"
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
                Text(emoji, style = TextStyle(color = ColorProvider(Green), fontSize = 30.sp, textAlign = TextAlign.Center))
            }
        }
    }

    companion object {
        val serviceIdKey = stringPreferencesKey("icon_service_id")
        val nameKey = stringPreferencesKey("icon_shortcut_name")
        val emojiKey = stringPreferencesKey("icon_emoji")
    }
}

/** Wires [ShortcutIconWidget] into the AppWidget framework. */
class ShortcutIconWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutIconWidget()
}
