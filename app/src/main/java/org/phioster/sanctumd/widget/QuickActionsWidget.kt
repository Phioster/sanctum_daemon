package org.phioster.sanctumd.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceConfig

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Dim = Color(0xFF7A9A7A)
private val Chip = Color(0xFF13251A)

/** Homescreen widget: quick-action buttons for one chosen service. */
class QuickActionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val services = runCatching { ServiceStore(context).services.first() }.getOrDefault(emptyList())
        provideContent {
            val sid = currentState<Preferences>()[serviceIdKey] ?: ""
            Content(services.firstOrNull { it.id == sid })
        }
    }

    @Composable
    private fun Content(config: ServiceConfig?) {
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(10.dp)) {
            if (config == null) {
                Text("open to pick a service", style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp))
                return@Column
            }
            Text(config.label, style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            val actions = WidgetActions.forType(config.type)
            if (actions.isEmpty()) {
                Text("no quick actions", style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp))
            } else {
                actions.forEachIndexed { i, a ->
                    if (i > 0) Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "▸ ${a.label}",
                        style = TextStyle(color = ColorProvider(Green), fontSize = 13.sp),
                        modifier = GlanceModifier.fillMaxWidth()
                            .background(Chip)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .clickable(
                                actionRunCallback<RunQuickAction>(
                                    actionParametersOf(
                                        RunQuickAction.pServiceId to config.id,
                                        RunQuickAction.pActionIndex to i,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }

    companion object {
        val serviceIdKey = stringPreferencesKey("qa_service_id")
    }
}

/** Runs the tapped quick action (side-effecting) and reports the result as a Toast. */
class RunQuickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sid = parameters[pServiceId] ?: return
        val idx = parameters[pActionIndex] ?: return
        val services = runCatching { ServiceStore(context).services.first() }.getOrDefault(emptyList())
        val config = services.firstOrNull { it.id == sid } ?: return
        val action = WidgetActions.forType(config.type).getOrNull(idx) ?: return
        val result = runCatching { action.run(config) }.getOrElse { "error: ${it.message ?: "failed"}" }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        val pServiceId = ActionParameters.Key<String>("serviceId")
        val pActionIndex = ActionParameters.Key<Int>("actionIndex")
    }
}

/** Wires [QuickActionsWidget] into the AppWidget framework. */
class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
