package org.phioster.sanctumd.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.StatChart
import org.phioster.sanctumd.model.StatTile
import org.phioster.sanctumd.model.StatsData
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Hint
import org.phioster.sanctumd.ui.common.SectionHeader
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** Service-wide stats overview: headline number tiles + horizontal bar charts, aggregated across
 *  every configured service. Opened from the drawer's bar-chart icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(vm: DashboardViewModel, onBack: () -> Unit) {
    var data by remember { mutableStateOf<StatsData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(reloadTick) {
        loading = true
        data = runCatching { vm.loadStats() }.getOrNull()
        loading = false
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("stats", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) } },
                actions = { IconButton(onClick = { reloadTick++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MatrixGreen) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        val d = data
        when {
            loading && d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MatrixGreen)
            }
            d == null || (d.tiles.isEmpty() && d.charts.isEmpty()) -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Hint("no stats — add services with data")
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (d.tiles.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        // Tiles wrap two per row.
                        d.tiles.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { t -> StatTileView(t, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(d.charts) { chart ->
                    StatChartView(chart)
                    Spacer(Modifier.height(18.dp))
                }
                // TRENDS section: derived from the recorded stat history (grows over days).
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("TRENDS")
                    Spacer(Modifier.height(10.dp))
                    if (d.trendTiles.isEmpty() && d.trends.isEmpty()) {
                        Text(
                            "collecting data — check back in a few days",
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp,
                        )
                    } else if (d.trendTiles.isNotEmpty()) {
                        d.trendTiles.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { t -> StatTileView(t, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(d.trends) { chart ->
                    StatChartView(chart)
                    Spacer(Modifier.height(18.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun StatTileView(tile: StatTile, modifier: Modifier) {
    val accent = Color(tile.accentArgb)
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(tile.value, fontFamily = Mono, color = MatrixGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tile.label.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.75f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatChartView(chart: StatChart) {
    val accent = Color(chart.accentArgb)
    val max = chart.bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(chart.title, accent)
        Spacer(Modifier.height(8.dp))
        chart.bars.forEach { bar ->
            val color = bar.colorArgb?.let { Color(it) } ?: accent
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(bar.label, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.height(0.dp))
                    Text(bar.display, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(4.dp)).background(MatrixGreen.copy(alpha = 0.12f))) {
                    val frac = (bar.value / max).coerceIn(0f, 1f).coerceAtLeast(if (bar.value > 0f) 0.02f else 0f)
                    Box(Modifier.fillMaxWidth(frac).height(9.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
        }
    }
}
