package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.OpenInNew
import androidx.activity.compose.BackHandler
import org.phioster.sanctumd.model.SeerrSearchItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.model.SeerrMediaDetail

/**
 * The full-screen look at one title, opened from discover, search or a genre row.
 * Requesting and reporting go back to [SeerrScreen], which owns those dialogs.
 */
@Composable
internal fun SeerrMediaDetailView(
    vm: DashboardViewModel,
    config: ServiceConfig,
    d: SeerrMediaDetail,
    accent: Color,
    onBack: () -> Unit,
    onRequest: (SeerrSearchItem) -> Unit,
    onReportIssue: (Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var onWatchlist by remember(d.tmdbId) { mutableStateOf(d.onWatchlist) }
    var watchlistBusy by remember(d.tmdbId) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Banner: blurred poster backdrop + sharp poster + title ──
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (d.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = d.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().blur(28.dp).background(Surface),
                    )
                } else {
                    Box(Modifier.matchParentSize().background(Surface))
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Black.copy(alpha = 0.35f), Black.copy(alpha = 0.65f), Black))))
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    if (d.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = d.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).background(Surface),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.title, fontFamily = Mono, color = MatrixGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val quick = d.facts.take(3).joinToString("  ·  ") { it.second }
                        if (quick.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(quick, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (d.status.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(d.status, fontFamily = Mono, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // ── Body ──
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                PrimaryButton("request", Modifier.fillMaxWidth(), accent = accent) {
                    onRequest(SeerrSearchItem(d.tmdbId, d.title, d.year, d.mediaType))
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    if (onWatchlist) "on watchlist" else "add to watchlist",
                    Modifier.fillMaxWidth(),
                    icon = if (onWatchlist) AppIcons.Unwatched else AppIcons.Watched,
                    accent = if (onWatchlist) MatrixGreen else accent,
                    enabled = !watchlistBusy,
                ) {
                    watchlistBusy = true
                    scope.launch {
                        val res = if (onWatchlist) {
                            vm.seerrRemoveFromWatchlistOf(config, d.tmdbId, d.mediaType)
                        } else {
                            vm.seerrAddToWatchlistOf(config, d.tmdbId, d.mediaType, d.title)
                        }
                        if (!res.startsWith("error")) onWatchlist = !onWatchlist
                        watchlistBusy = false
                        android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("open in Seerr", Modifier.fillMaxWidth(), accent = accent) {
                    openExternal(context, seerrAppPackages, "${config.normalizedBaseUrl}${d.mediaType}/${d.tmdbId}")
                }
                if (d.facts.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FactGrid(d.facts, accent)
                }
                if (d.genres.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(d.genres, fontFamily = Mono, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                if (!d.availability.isEmpty) {
                    Spacer(Modifier.height(16.dp))
                    WatchProviderSection(d.availability, accent)
                }
                // Only for titles Seerr already knows — an issue is filed against its own id.
                if (d.mediaId > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "report an issue",
                        fontFamily = Mono, color = accent, fontSize = 12.sp,
                        modifier = Modifier.clickable { onReportIssue(d.mediaId, d.title) }.padding(vertical = 4.dp),
                    )
                }
                if (d.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (d.cast.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("CAST")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        d.cast.forEach { member ->
                            Column(Modifier.width(84.dp).padding(end = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (member.profileUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = member.profileUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Surface),
                                    )
                                } else {
                                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Surface))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(member.name, fontFamily = Mono, color = MatrixGreen, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                if (member.character.isNotBlank()) {
                                    Text(member.character, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        // ── Top bar overlay: back + open-in-Seerr ──
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { openExternal(context, seerrAppPackages, "${config.normalizedBaseUrl}${d.mediaType}/${d.tmdbId}") }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in Seerr", tint = accent)
            }
        }
    }
}
