package org.phioster.sanctumd.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.phioster.sanctumd.MainActivity
import org.phioster.sanctumd.ui.theme.ThemeState
import org.phioster.sanctumd.ui.theme.ThemeStore
import org.phioster.sanctumd.ui.theme.dimInk
import org.phioster.sanctumd.data.ResumeSnap
import org.phioster.sanctumd.data.ResumeSnapshotStore

private val Bg: Color get() = ThemeState.palette.background
private val Green: Color get() = ThemeState.palette.accent
private val Dim: Color get() = ThemeState.palette.dimInk()
private val Track: Color get() = ThemeState.palette.surfaceHi

/**
 * Homescreen widget: what's half-watched on Jellyfin, with a progress bar per row. Tapping a row
 * opens that item's detail in the app. Renders from a cached snapshot ([ResumeSnapshotStore]) so
 * nothing is fetched at draw time; [ResumeCacheWorker] refreshes it.
 */
class ResumeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ThemeState.palette = ThemeStore.read(context)
        val snaps = runCatching { ResumeSnapshotStore(context).read() }.getOrDefault(emptyList())
        provideContent { Content(snaps) }
    }

    @Composable
    private fun Content(snaps: List<ResumeSnap>) {
        val ctx = LocalContext.current
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("continue watching", style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "⟳",
                    style = TextStyle(color = ColorProvider(Green), fontSize = 15.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshResumeAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (snaps.isEmpty()) {
                Text("nothing in progress", style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp))
            } else {
                LazyColumn {
                    items(snaps.size) { i -> ItemRow(ctx, snaps[i]) }
                }
            }
        }
    }

    @Composable
    private fun ItemRow(ctx: Context, snap: ResumeSnap) {
        Column(
            GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(
                actionStartActivity(
                    Intent(ctx, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra("route", "service")
                        .putExtra("serviceId", snap.serviceId)
                        .putExtra("itemId", snap.itemId),
                ),
            ),
        ) {
            Text(snap.title, maxLines = 1, style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp))
            if (snap.subtitle.isNotBlank()) {
                Text(snap.subtitle, maxLines = 1, style = TextStyle(color = ColorProvider(Dim), fontSize = 10.sp))
            }
            Spacer(GlanceModifier.height(3.dp))
            // Progress bar: a filled sliver over a recessive track, sized in whole percent steps.
            Row(GlanceModifier.fillMaxWidth().height(3.dp).cornerRadius(2.dp).background(Track)) {
                Box(GlanceModifier.height(3.dp).width((snap.percent.coerceIn(2, 100) * 1.4).dp).cornerRadius(2.dp).background(Green)) {}
            }
        }
    }
}

/** Refresh button → one-off fetch. */
class RefreshResumeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ResumeCacheWorker>().build())
    }
}

/** Wires [ResumeWidget] into the AppWidget framework; refreshes the cache on every update. */
class ResumeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResumeWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ResumeCacheWorker>().build())
    }
}
