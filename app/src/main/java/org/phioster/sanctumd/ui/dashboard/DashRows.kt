package org.phioster.sanctumd.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.common.*

@Composable
internal fun DashSessionRow(item: org.phioster.sanctumd.model.JellySession, accent: Color, density: String = "") {
    val playing = item.nowPlaying.isNotEmpty()
    val vpad = when (density) { "compact" -> 2.dp; "detail" -> 9.dp; else -> 6.dp }
    Column(Modifier.fillMaxWidth().padding(vertical = vpad)) {
        Text(if (playing) item.nowPlaying else "${item.user} · idle", fontFamily = Mono, color = if (playing) MatrixGreen else MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (density != "compact") Text("${item.user}${if (item.device.isNotBlank()) " · ${item.device}" else ""}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (playing) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { item.progressPct }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
        }
    }
}

@Composable
internal fun DashLineRow(title: String, subtitle: String, accent: Color, density: String = "", titleColor: Color = MatrixGreen, onClick: (() -> Unit)? = null) {
    val vpad = when (density) { "compact" -> 2.dp; "detail" -> 9.dp; else -> 5.dp }
    val showSub = density != "compact" && subtitle.isNotBlank()
    Column(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(vertical = vpad)) {
        Text(title, fontFamily = Mono, color = titleColor, fontSize = 13.sp, maxLines = if (density == "detail") 2 else 1, overflow = TextOverflow.Ellipsis)
        if (showSub) Text(subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DashQueueRow(item: org.phioster.sanctumd.model.ArrQueueItem, accent: Color, density: String = "") =
    DashNzbRow(item.title, item.status, item.progress, accent, density)

@Composable
internal fun DashNzbRow(title: String, status: String, progress: Float, accent: Color, density: String = "") {
    val vpad = when (density) { "compact" -> 2.dp; "detail" -> 9.dp; else -> 6.dp }
    Column(Modifier.fillMaxWidth().padding(vertical = vpad)) {
        Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = if (density == "detail") 2 else 1, overflow = TextOverflow.Ellipsis)
        if (density != "compact") Text("$status · ${(progress * 100).toInt()}%", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = MatrixGreen, trackColor = Surface)
    }
}

@Composable
internal fun DashDiscoverPoster(item: org.phioster.sanctumd.model.SeerrDiscoverItem, width: androidx.compose.ui.unit.Dp = 96.dp, caption: Boolean = true, onClick: () -> Unit) {
    val h = width * 1.5f
    Column(Modifier.width(width).padding(end = 10.dp).clickable { onClick() }) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface))
        }
        if (caption) {
            Spacer(Modifier.height(4.dp))
            Text(item.title, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Unified detail shown when a dashboard poster is tapped (from Jellyfin or Seerr). */
internal class MediaDetail(
    val title: String, val subtitle: String, val posterUrl: String,
    val genres: String, val facts: List<Pair<String, String>>, val overview: String,
    val cast: List<org.phioster.sanctumd.model.ArrCastMember>,
)

internal fun org.phioster.sanctumd.model.JellyMediaDetail.toMediaDetail() =
    MediaDetail(name, "", posterUrl, genres, facts, overview, cast)

internal fun org.phioster.sanctumd.model.SeerrMediaDetail.toMediaDetail() =
    MediaDetail(title, listOfNotNull(year.ifBlank { null }, if (mediaType == "tv") "series" else "movie").joinToString(" · "), posterUrl, genres, facts, overview, cast)

@Composable
internal fun MediaDetailDialog(d: MediaDetail, config: ServiceConfig, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(d.title, fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (d.posterUrl.isNotBlank()) {
                    JellyPoster(d.posterUrl, config, Modifier.fillMaxWidth().heightIn(max = 260.dp), RoundedCornerShape(8.dp), ContentScale.Fit)
                    Spacer(Modifier.height(8.dp))
                }
                if (d.subtitle.isNotBlank()) Text(d.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
                if (d.genres.isNotBlank()) Text(d.genres, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                if (d.facts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    d.facts.forEach { (k, v) -> Text("$k: $v", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp) }
                }
                if (d.overview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                if (d.cast.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        d.cast.take(12).forEach { member ->
                            Column(Modifier.width(64.dp).padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (member.profileUrl.isNotBlank()) {
                                    JellyPoster(member.profileUrl, config, Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)), RoundedCornerShape(28.dp), ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(Black))
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(member.name, fontFamily = Mono, color = MatrixGreen, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** A one-tap action a Quick Buttons card can run against a service. */
internal class QuickAction(val label: String, val run: suspend (DashboardViewModel, ServiceConfig) -> String)

internal fun quickActionsFor(svc: ServiceConfig): List<QuickAction> = when (svc.type) {
    ServiceType.JELLYFIN -> listOf(
        QuickAction("Scan libraries") { vm, s -> vm.jellyfinScan(s) },
        QuickAction("Restart server") { vm, s -> vm.jellyfinRestartServer(s) },
    )
    ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> listOf(
        QuickAction("Search all missing") { vm, s -> vm.arrSearchAllItems(s, false) },
        QuickAction("RSS sync") { vm, s -> vm.arrRssSyncNow(s) },
    )
    ServiceType.PROWLARR -> listOf(QuickAction("Test all indexers") { vm, s -> vm.prowlarrTestAll(s) })
    ServiceType.NZBGET -> listOf(
        QuickAction("Pause queue") { vm, s -> vm.nzbgetPause(s) },
        QuickAction("Resume queue") { vm, s -> vm.nzbgetResume(s) },
    )
    else -> emptyList()
}

@Composable
internal fun BoxScope.KenBurnsBackground(url: String, config: ServiceConfig) {
    val t = rememberInfiniteTransition(label = "kb")
    val scale by t.animateFloat(1.08f, 1.28f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val dx by t.animateFloat(-18f, 18f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "x")
    val dy by t.animateFloat(12f, -12f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    JellyPoster(
        url, config,
        Modifier.matchParentSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = dx; translationY = dy },
        RoundedCornerShape(0.dp), ContentScale.Crop,
    )
    // Dark scrim so the monospace foreground stays readable over any art.
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Black.copy(alpha = 0.55f), Black.copy(alpha = 0.82f)))))
}
