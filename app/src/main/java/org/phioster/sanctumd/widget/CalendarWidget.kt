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
import androidx.glance.layout.Box
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
import org.phioster.sanctumd.MainActivity
import org.phioster.sanctumd.R
import org.phioster.sanctumd.data.CalendarSnap
import org.phioster.sanctumd.data.CalendarSnapshotStore
import org.phioster.sanctumd.model.ServiceType

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Dim = Color(0xFF7A9A7A)

private fun typeOf(name: String): ServiceType? = runCatching { ServiceType.valueOf(name) }.getOrNull()

private fun logoRes(type: ServiceType): Int = when (type) {
    ServiceType.JELLYFIN -> R.drawable.svc_jellyfin
    ServiceType.RADARR -> R.drawable.svc_radarr
    ServiceType.SONARR -> R.drawable.svc_sonarr
    ServiceType.LIDARR -> R.drawable.svc_lidarr
    ServiceType.PROWLARR -> R.drawable.svc_prowlarr
    ServiceType.NZBGET -> R.drawable.svc_nzbget
    ServiceType.SEERR -> R.drawable.svc_seerr
    ServiceType.NTFY -> R.drawable.svc_ntfy
    ServiceType.SHORTCUTS -> R.drawable.svc_shortcuts
}

/** One rendered line in the agenda: a day header, or a release row. */
private sealed interface Line {
    data class Header(val label: String) : Line
    data class Item(val snap: CalendarSnap) : Line
}

/** Turns the flat, date-sorted snapshot into header + item lines (a header whenever the day changes). */
private fun buildLines(snaps: List<CalendarSnap>): List<Line> {
    val today = java.time.LocalDate.now()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")
    val out = mutableListOf<Line>()
    var lastDay: String? = null
    for (s in snaps) {
        if (s.date != lastDay) {
            lastDay = s.date
            val label = runCatching {
                val d = java.time.LocalDate.parse(s.date)
                when (d) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    else -> d.format(fmt)
                }
            }.getOrDefault(s.date)
            out += Line.Header(label)
        }
        out += Line.Item(s)
    }
    return out
}

/** Homescreen widget: upcoming releases across Radarr/Sonarr/Lidarr as an agenda list.
 *  Renders from a cached snapshot ([CalendarSnapshotStore]); [CalendarCacheWorker] refreshes it. */
class CalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snaps = runCatching { CalendarSnapshotStore(context).read() }.getOrDefault(emptyList())
        provideContent { Content(snaps) }
    }

    @Composable
    private fun Content(snaps: List<CalendarSnap>) {
        val ctx = LocalContext.current
        val lines = buildLines(snaps)
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(10.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().clickable(
                    actionStartActivity(
                        Intent(ctx, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra("route", "home"),
                    ),
                ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text("upcoming", style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "⟳",
                    style = TextStyle(color = ColorProvider(Green), fontSize = 15.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshCalendarAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (lines.isEmpty()) {
                Text("no upcoming releases", style = TextStyle(color = ColorProvider(Dim), fontSize = 12.sp))
            } else {
                LazyColumn {
                    items(lines.size) { i ->
                        when (val line = lines[i]) {
                            is Line.Header -> Text(
                                line.label,
                                style = TextStyle(color = ColorProvider(Dim), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.padding(top = if (i == 0) 0.dp else 6.dp, bottom = 2.dp),
                            )
                            is Line.Item -> ItemRow(ctx, line.snap)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ItemRow(ctx: Context, snap: CalendarSnap) {
        val type = typeOf(snap.serviceType)
        val accent = type?.let { Color(it.accent) } ?: Green
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(
                actionStartActivity(
                    Intent(ctx, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra("route", "service")
                        .putExtra("serviceId", snap.serviceId),
                ),
            ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(6.dp).cornerRadius(3.dp).background(accent),
                content = {},
            )
            Spacer(GlanceModifier.width(7.dp))
            if (type != null) {
                Image(provider = ImageProvider(logoRes(type)), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(7.dp))
            }
            Column {
                Text(
                    "${if (snap.hasFile) "✓ " else ""}${snap.title}",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Green), fontSize = 12.sp),
                )
                if (snap.subtitle.isNotBlank()) {
                    Text(snap.subtitle, maxLines = 1, style = TextStyle(color = ColorProvider(Dim), fontSize = 10.sp))
                }
            }
        }
    }
}

/** Refresh button → kicks off a one-off calendar fetch. */
class RefreshCalendarAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CalendarCacheWorker>().build())
    }
}

/** Wires [CalendarWidget] into the AppWidget framework; refreshes the cache on every update. */
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CalendarCacheWorker>().build())
    }
}
