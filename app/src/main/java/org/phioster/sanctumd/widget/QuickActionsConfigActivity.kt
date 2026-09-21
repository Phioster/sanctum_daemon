package org.phioster.sanctumd.widget

import org.phioster.sanctumd.ui.theme.ThemeState
import org.phioster.sanctumd.ui.theme.ThemeStore
import org.phioster.sanctumd.ui.theme.dimInk
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceConfig

private val Bg: Color get() = ThemeState.palette.background
private val Green: Color get() = ThemeState.palette.accent
private val Chip = Color(0xFF13251A)
private val Dim: Color get() = ThemeState.palette.dimInk()

/** Shown when the quick-actions widget is placed: pick which service it controls. */
class QuickActionsConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.palette = ThemeStore.read(this)
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // Exported by necessity. So make sure the id is one of ours before showing anything.
        if (!ownsAppWidget(this, QuickActionsWidgetReceiver::class.java, appWidgetId)) { finish(); return }
        setContent { ConfigScreen(appWidgetId) }
    }

    @Composable
    private fun ConfigScreen(appWidgetId: Int) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var services by remember { mutableStateOf<List<ServiceConfig>?>(null) }

        LaunchedEffect(Unit) {
            services = runCatching { ServiceStore(ctx).services.first() }.getOrDefault(emptyList())
                .filter { WidgetActions.forType(it.type).isNotEmpty() }
        }

        Column(
            Modifier.fillMaxSize().background(Bg).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            Text("pick a service", fontFamily = FontFamily.Monospace, color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("this widget shows its quick actions", fontFamily = FontFamily.Monospace, color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            val list = services
            when {
                list == null -> Text("loading…", fontFamily = FontFamily.Monospace, color = Dim, fontSize = 13.sp)
                list.isEmpty() -> Text(
                    "no services with quick actions. Add Jellyfin/Radarr/Sonarr/Lidarr/Prowlarr/NZBGet",
                    fontFamily = FontFamily.Monospace, color = Dim, fontSize = 13.sp,
                )
                else -> list.forEach { svc ->
                    Text(
                        "▸ ${svc.label}",
                        fontFamily = FontFamily.Monospace, color = Green, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Chip)
                            .clickable {
                                scope.launch {
                                    val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
                                    updateAppWidgetState(ctx, glanceId) { prefs ->
                                        prefs[QuickActionsWidget.serviceIdKey] = svc.id
                                    }
                                    QuickActionsWidget().update(ctx, glanceId)
                                    setResult(
                                        Activity.RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                    )
                                    finish()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
