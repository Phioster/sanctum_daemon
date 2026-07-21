package org.phioster.sanctumd.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.HttpShortcut
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Chip = Color(0xFF13251A)
private val Dim = Color(0xFF7A9A7A)

/** Shown when the 1×1 icon widget is placed: pick which shortcut it fires + an icon. */
class ShortcutWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cancel by default, so backing out doesn't leave a half-placed widget.
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent { ConfigScreen(appWidgetId) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ConfigScreen(appWidgetId: Int) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var entries by remember { mutableStateOf<List<Pair<ServiceConfig, HttpShortcut>>?>(null) }
        var chosen by remember { mutableStateOf<Pair<ServiceConfig, HttpShortcut>?>(null) }

        LaunchedEffect(Unit) {
            val services = runCatching { ServiceStore(ctx).services.first() }.getOrDefault(emptyList())
            entries = services
                .filter { it.type == ServiceType.SHORTCUTS }
                .flatMap { svc -> svc.shortcuts.map { svc to it } }
        }

        fun save(svc: ServiceConfig, sc: HttpShortcut, iconIndex: Int) {
            scope.launch {
                val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(ctx, glanceId) { prefs ->
                    prefs[ShortcutIconWidget.serviceIdKey] = svc.id
                    prefs[ShortcutIconWidget.nameKey] = sc.name
                    prefs[ShortcutIconWidget.iconKey] = iconIndex.toString()
                }
                ShortcutIconWidget().update(ctx, glanceId)
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
        }

        Column(
            Modifier.fillMaxSize().background(Bg).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            val sel = chosen
            if (sel == null) {
                Text("pick a shortcut", fontFamily = FontFamily.Monospace, color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("choose which one this icon fires", fontFamily = FontFamily.Monospace, color = Dim, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                val list = entries
                when {
                    list == null -> Text("loading…", fontFamily = FontFamily.Monospace, color = Dim, fontSize = 13.sp)
                    list.isEmpty() -> Text(
                        "no shortcuts — add a Shortcuts service in the app first",
                        fontFamily = FontFamily.Monospace, color = Dim, fontSize = 13.sp,
                    )
                    else -> list.forEach { pair ->
                        Text(
                            "▸ ${pair.second.name}",
                            fontFamily = FontFamily.Monospace, color = Green, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Chip)
                                .clickable { chosen = pair }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        )
                    }
                }
            } else {
                Text("pick an icon", fontFamily = FontFamily.Monospace, color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("▸ ${sel.second.name}", fontFamily = FontFamily.Monospace, color = Dim, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WIDGET_ICONS.forEachIndexed { i, res ->
                        Box(
                            Modifier.size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Chip)
                                .clickable { save(sel.first, sel.second, i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(res),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "‹ back",
                    fontFamily = FontFamily.Monospace, color = Dim, fontSize = 14.sp,
                    modifier = Modifier.clickable { chosen = null },
                )
            }
        }
    }
}
