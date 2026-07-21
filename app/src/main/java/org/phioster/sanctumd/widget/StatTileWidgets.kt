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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.MainActivity
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.data.StatusSnap
import org.phioster.sanctumd.data.StatusSnapshotStore
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

private val Bg = Color(0xFF0A0F0A)
private val Green = Color(0xFF00FF41)
private val Red = Color(0xFFFF5555)
private val Amber = Color(0xFFFFB454)
private val Dim = Color(0xFF7A9A7A)

/** Loads the shared status cache both stat tiles and the stack-health tile render from. */
private suspend fun loadState(context: Context): Pair<List<ServiceConfig>, Map<String, StatusSnap>> {
    val services = runCatching { ServiceStore(context).services.first() }
        .getOrDefault(emptyList())
        .filter { it.type != ServiceType.SHORTCUTS }
    val snaps = runCatching { StatusSnapshotStore(context).read() }.getOrDefault(emptyMap())
    return services to snaps
}

/** Shared 1×1 tile: a big number/value, a tiny caption, tap opens [openIntent]. */
@Composable
private fun StatTile(value: String, caption: String, valueColor: Color, openIntent: Intent) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(Bg)
            .cornerRadius(18.dp)
            .padding(4.dp)
            .clickable(actionStartActivity(openIntent)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            value,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(valueColor),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            caption,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Dim),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Intent that opens a specific service, or the dashboard if [serviceId] is null. */
private fun openIntent(ctx: Context, serviceId: String?): Intent {
    val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
    return if (serviceId != null) {
        i.putExtra("route", "service").putExtra("serviceId", serviceId)
    } else {
        i.putExtra("route", "home")
    }
}

// ── Stack health: how many services are up ───────────────────────────────────

/** 1×1 tile: online/total services at a glance (green all-up, red if any down). */
class StackHealthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (services, snaps) = loadState(context)
        provideContent {
            val ctx = LocalContext.current
            val total = services.size
            val up = services.count { snaps[it.id]?.ok == true }
            val color = when {
                total == 0 -> Dim
                up == total -> Green
                else -> Red
            }
            StatTile(
                value = if (total == 0) "—" else "$up/$total",
                caption = "online",
                valueColor = color,
                openIntent = openIntent(ctx, null),
            )
        }
    }
}

// ── Download queue: NZBGet queued items ───────────────────────────────────────

/** 1×1 tile: NZBGet queue size (⏸ when paused). */
class QueueTileWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (services, snaps) = loadState(context)
        provideContent {
            val ctx = LocalContext.current
            val svc = services.firstOrNull { it.type == ServiceType.NZBGET }
            val snap = svc?.let { snaps[it.id] }
            val queue = snap?.stats?.get("Queue")
            val paused = snap?.note?.contains("paused") == true
            StatTile(
                value = queue ?: "—",
                caption = if (paused) "⏸ queue" else "↓ queue",
                valueColor = if (paused) Amber else Green,
                openIntent = openIntent(ctx, svc?.id),
            )
        }
    }
}

// ── Seerr requests: pending requests ──────────────────────────────────────────

/** 1×1 tile: Jellyseerr pending request count. */
class SeerrTileWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (services, snaps) = loadState(context)
        provideContent {
            val ctx = LocalContext.current
            val svc = services.firstOrNull { it.type == ServiceType.SEERR }
            val snap = svc?.let { snaps[it.id] }
            val pending = snap?.stats?.get("Pending")
            StatTile(
                value = pending ?: "—",
                caption = "pending",
                valueColor = Green,
                openIntent = openIntent(ctx, svc?.id),
            )
        }
    }
}

// ── Library size: Jellyfin movies + series ────────────────────────────────────

/** 1×1 tile: total Jellyfin library items (movies + series). */
class LibraryTileWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (services, snaps) = loadState(context)
        provideContent {
            val ctx = LocalContext.current
            val svc = services.firstOrNull { it.type == ServiceType.JELLYFIN }
            val snap = svc?.let { snaps[it.id] }
            val movies = snap?.stats?.get("Movies")?.toIntOrNull()
            val series = snap?.stats?.get("Series")?.toIntOrNull()
            val total = if (movies == null && series == null) null else (movies ?: 0) + (series ?: 0)
            StatTile(
                value = total?.toString() ?: "—",
                caption = "library",
                valueColor = Green,
                openIntent = openIntent(ctx, svc?.id),
            )
        }
    }
}

// ── Receivers: each refreshes the shared status cache on update ────────────────

private fun enqueueStatusFetch(context: Context) {
    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<StatusCacheWorker>().build())
}

class StackHealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StackHealthWidget()
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueStatusFetch(context)
    }
}

class QueueTileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QueueTileWidget()
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueStatusFetch(context)
    }
}

class SeerrTileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SeerrTileWidget()
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueStatusFetch(context)
    }
}

class LibraryTileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LibraryTileWidget()
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueStatusFetch(context)
    }
}
