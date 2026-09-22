package org.phioster.sanctumd.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import org.phioster.sanctumd.model.ArrDetail
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
import org.phioster.sanctumd.ui.theme.AppIcons
import androidx.compose.material3.Icon

/** Dashboard card: a month calendar grid of upcoming releases merged across all *arr,
 *  month-switchable, services marked by their accent colour; tap a day for its list. */
@Composable
internal fun UnifiedCalendarCard(
    vm: DashboardViewModel,
    accent: Color,
    onOpenService: (ServiceConfig) -> Unit,
    /** The card spans several services, so the item link needs to say which one. */
    onOpenLink: (ServiceConfig, org.phioster.sanctumd.ui.search.SearchDeepLink) -> Unit = { cfg, _ -> onOpenService(cfg) },
) {
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    // Init from the cache so a tab switch shows the month instantly (no reload flash).
    @Suppress("UNCHECKED_CAST")
    var byDay by remember {
        mutableStateOf(vm.cardDataCache["unifiedCal#$month"] as? Map<java.time.LocalDate, List<Pair<ServiceConfig, org.phioster.sanctumd.model.ArrCalendarItem>>>)
    }
    var selected by remember { mutableStateOf<java.time.LocalDate?>(java.time.LocalDate.now()) }
    var info by remember { mutableStateOf<Pair<ServiceConfig, org.phioster.sanctumd.model.ArrCalendarItem>?>(null) }

    LaunchedEffect(month, vm.dashRefreshTick.intValue) {
        val tick = vm.dashRefreshTick.intValue
        val key = "unifiedCal#$month"
        @Suppress("UNCHECKED_CAST")
        val cached = vm.cardDataCache[key] as? Map<java.time.LocalDate, List<Pair<ServiceConfig, org.phioster.sanctumd.model.ArrCalendarItem>>>
        val today = java.time.LocalDate.now()
        if (cached != null && vm.cardDataTick[key] == tick) {
            byDay = cached
            selected = if (java.time.YearMonth.from(today) == month) today else null
            return@LaunchedEffect
        }
        byDay = null
        val list = runCatching { vm.unifiedCalendarRange(month.atDay(1), month.atEndOfMonth()) }.getOrDefault(emptyList())
        byDay = list.groupBy { runCatching { java.time.LocalDate.parse(it.second.date) }.getOrNull() ?: java.time.LocalDate.MIN }
        vm.cardDataCache[key] = byDay
        vm.cardDataTick[key] = tick
        selected = if (java.time.YearMonth.from(today) == month) today else null
    }

    val services by vm.services.collectAsState()
    val arrServices = services.filter {
        it.type == ServiceType.RADARR || it.type == ServiceType.SONARR || it.type == ServiceType.LIDARR
    }
    val map = byDay ?: emptyMap()
    val today = java.time.LocalDate.now()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Back, contentDescription = "earlier month", tint = accent, modifier = Modifier.size(18.dp).clickable { month = month.minusMonths(1) }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text(
                month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                fontFamily = Mono, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            if (month != java.time.YearMonth.now()) {
                Text(
                    "•now", fontFamily = Mono, color = accent.copy(alpha = 0.6f), fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { month = java.time.YearMonth.now(); selected = java.time.LocalDate.now() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Icon(AppIcons.Forward, contentDescription = "later month", tint = accent, modifier = Modifier.size(18.dp).clickable { month = month.plusMonths(1) }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(d, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday = 0 leading blanks
        val cells = buildList<java.time.LocalDate?> {
            repeat(lead) { add(null) }
            for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(38.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val items = map[date].orEmpty()
                            val isSel = date == selected
                            Column(
                                Modifier.fillMaxSize()
                                    .then(if (isSel) Modifier.background(accent.copy(alpha = 0.22f), RoundedCornerShape(6.dp)) else Modifier)
                                    .clickable { selected = date },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    fontFamily = Mono,
                                    color = if (date == today) accent else accent.copy(alpha = if (items.isEmpty()) 0.75f else 1f),
                                    fontSize = 11.sp,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (items.isNotEmpty()) {
                                    Row {
                                        items.map { it.first.type }.distinct().take(3).forEach { t ->
                                            Box(Modifier.padding(horizontal = 1.dp).size(4.dp).background(Color(t.accent), androidx.compose.foundation.shape.CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = accent.copy(alpha = 0.25f))
        val sel = selected
        val selItems = sel?.let { map[it] }.orEmpty()
        when {
            byDay == null -> Text("loading…", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            sel == null -> Text("pick a day", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            selItems.isEmpty() -> Text("nothing on ${sel.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            else -> Column {
                selItems.forEach { (cfg, ci) ->
                    DashLineRow(ci.title, "${cfg.type.label}${if (ci.subtitle.isNotBlank()) " · ${ci.subtitle}" else ""}", Color(cfg.type.accent), titleColor = accent, leading = if (ci.hasFile) AppIcons.Done else null) { info = cfg to ci }
                }
            }
        }
        if (arrServices.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                arrServices.forEach { svc ->
                    Box(Modifier.size(6.dp).background(Color(svc.type.accent), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(3.dp))
                    Text(svc.label, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 9.sp)
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }

    info?.let { (cfg, ci) ->
        CalendarItemInfoDialog(
            vm, cfg, ci,
            onOpen = {
                if (ci.itemId > 0) onOpenLink(cfg, org.phioster.sanctumd.ui.search.SearchDeepLink(arrDetailId = ci.itemId)) else onOpenService(cfg)
                info = null
            },
            onDismiss = { info = null },
        )
    }
}

/** Loads full arr detail for a tapped calendar entry and shows poster + facts + overview. */
@Composable
internal fun CalendarItemInfoDialog(
    vm: DashboardViewModel,
    cfg: ServiceConfig,
    ci: org.phioster.sanctumd.model.ArrCalendarItem,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var detail by remember { mutableStateOf<ArrDetail?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(cfg.id, ci.itemId) {
        if (ci.itemId > 0) {
            detail = runCatching { vm.arrDetailOf(cfg, ci.itemId) }.getOrElse { failed = true; null }
        } else failed = true
    }
    val d = detail
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(d?.title ?: ci.title, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    if (!d?.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = d!!.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)).background(Black),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${cfg.type.label}${if (ci.subtitle.isNotBlank()) " · ${ci.subtitle}" else ""}",
                            fontFamily = Mono, color = Color(cfg.type.accent), fontSize = 11.sp,
                        )
                        runCatching {
                            java.time.LocalDate.parse(ci.date).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
                        }.getOrNull()?.let {
                            Text(it, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        Text(
                            if (ci.hasFile) "downloaded" else "not yet available",
                            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                        )
                        if (!d?.genres.isNullOrBlank()) Text(d!!.genres, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                        d?.facts?.forEach { (k, v) -> Text("$k: $v", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 10.sp) }
                    }
                }
                when {
                    d != null && d.overview.isNotBlank() -> {
                        Spacer(Modifier.height(10.dp))
                        Text(d.overview, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    detail == null && !failed -> {
                        Spacer(Modifier.height(10.dp))
                        Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open ${cfg.type.label}", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f)) } },
    )
}
