package org.phioster.sanctumd.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.MainActivity
import org.phioster.sanctumd.R
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.data.StatusSnap
import org.phioster.sanctumd.data.StatusSnapshotStore
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.service.ServiceRegistry

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Red = Color(0xFFFF5555)
private val Dim = Color(0xFF7A9A7A)

private fun logoRes(type: ServiceType): Int = ServiceRegistry.logoRes(type)

/** Homescreen widget: every service at a glance ([ok]/down), tap opens it. Renders from
 *  a cached snapshot ([StatusSnapshotStore]); [StatusCacheWorker] refreshes it. */
class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val services = runCatching { ServiceStore(context).services.first() }
            .getOrDefault(emptyList())
            .filter { it.type != ServiceType.SHORTCUTS }
        val snaps = runCatching { StatusSnapshotStore(context).read() }.getOrDefault(emptyMap())
        provideContent { Content(services, snaps) }
    }

    @Composable
    private fun Content(services: List<ServiceConfig>, snaps: Map<String, StatusSnap>) {
        val ctx = LocalContext.current
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    "services",
                    style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "⟳",
                    style = TextStyle(color = ColorProvider(Green), fontSize = 15.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshStatusAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (services.isEmpty()) {
                Text("no services yet", style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp))
            } else {
                LazyColumn {
                    items(services.size) { i ->
                        val svc = services[i]
                        val snap = snaps[svc.id]
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clickable(
                                    actionStartActivity(
                                        Intent(ctx, MainActivity::class.java)
                                            .setAction(Intent.ACTION_VIEW)
                                            .putExtra("route", "service")
                                            .putExtra("serviceId", svc.id),
                                    ),
                                ),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Text(
                                "●",
                                style = TextStyle(
                                    color = ColorProvider(if (snap?.ok == true) Green else if (snap == null) Dim else Red),
                                    fontSize = 13.sp,
                                ),
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Image(
                                provider = ImageProvider(logoRes(svc.type)),
                                contentDescription = null,
                                modifier = GlanceModifier.size(20.dp),
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            Column {
                                Text(svc.label, maxLines = 1, style = TextStyle(color = ColorProvider(Green), fontSize = 13.sp))
                                val note = snap?.note.orEmpty()
                                if (note.isNotBlank()) {
                                    Text(note, maxLines = 1, style = TextStyle(color = ColorProvider(Dim), fontSize = 10.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Refresh button → kicks off a one-off status fetch. */
class RefreshStatusAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<StatusCacheWorker>().build())
    }
}

/** Wires [StatusWidget] into the AppWidget framework; refreshes the cache on every update. */
class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<StatusCacheWorker>().build())
    }
}
