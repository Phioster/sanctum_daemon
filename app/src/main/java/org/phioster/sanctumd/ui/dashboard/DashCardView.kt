package org.phioster.sanctumd.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.ArrHistoryItem
import org.phioster.sanctumd.model.SeerrRequestItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
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
import org.phioster.sanctumd.ServiceLogo

@Composable
internal fun DashCardView(
    vm: DashboardViewModel,
    card: org.phioster.sanctumd.model.DashCard,
    config: ServiceConfig?,
    editMode: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenService: () -> Unit,
    onOpenAny: (ServiceConfig) -> Unit = {},
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSaveConfig: (String, Int, Long, String, String, Boolean, String, String) -> Unit,
    allServices: List<ServiceConfig> = emptyList(),
) {
    val serviceless = card.type.service == null
    val accentColor = if (card.accent != 0L) Color(card.accent) else if (config != null) Color(config.type.accent) else MatrixGreen
    // On a solid (accent-tinted) panel, accent-coloured text/icons flip to black for contrast.
    val accent = if (card.theme == "solid") Black else accentColor
    val posterWidth = when (card.posterSize) { "small" -> 84.dp; "large" -> 150.dp; else -> 120.dp }
    var showConfig by remember { mutableStateOf(false) }
    // Initialise from the per-card cache so a tab switch shows the last-loaded data instantly
    // (no refetch/flicker); the LaunchedEffect below only refetches when the refresh tick changed.
    @Suppress("UNCHECKED_CAST")
    var items by remember { mutableStateOf(vm.cardDataCache["${card.id}#items"] as? List<org.phioster.sanctumd.model.JellyMediaItem>) }
    @Suppress("UNCHECKED_CAST")
    var sessions by remember { mutableStateOf(vm.cardDataCache["${card.id}#sessions"] as? List<org.phioster.sanctumd.model.JellySession>) }
    @Suppress("UNCHECKED_CAST")
    var requests by remember { mutableStateOf(vm.cardDataCache["${card.id}#requests"] as? List<org.phioster.sanctumd.model.SeerrRequestItem>) }
    @Suppress("UNCHECKED_CAST")
    var queue by remember { mutableStateOf(vm.cardDataCache["${card.id}#queue"] as? List<org.phioster.sanctumd.model.ArrQueueItem>) }
    @Suppress("UNCHECKED_CAST")
    var missing by remember { mutableStateOf(vm.cardDataCache["${card.id}#missing"] as? List<org.phioster.sanctumd.model.ArrMissingItem>) }
    @Suppress("UNCHECKED_CAST")
    var calendar by remember { mutableStateOf(vm.cardDataCache["${card.id}#calendar"] as? List<org.phioster.sanctumd.model.ArrCalendarItem>) }
    @Suppress("UNCHECKED_CAST")
    var history by remember { mutableStateOf(vm.cardDataCache["${card.id}#history"] as? List<org.phioster.sanctumd.model.ArrHistoryItem>) }
    @Suppress("UNCHECKED_CAST")
    var nzbQueue by remember { mutableStateOf(vm.cardDataCache["${card.id}#nzbQueue"] as? List<org.phioster.sanctumd.model.NzbQueueItem>) }
    @Suppress("UNCHECKED_CAST")
    var nzbHistory by remember { mutableStateOf(vm.cardDataCache["${card.id}#nzbHistory"] as? List<org.phioster.sanctumd.model.NzbHistoryEntry>) }
    @Suppress("UNCHECKED_CAST")
    var discover by remember { mutableStateOf(vm.cardDataCache["${card.id}#discover"] as? List<org.phioster.sanctumd.model.SeerrDiscoverItem>) }
    @Suppress("UNCHECKED_CAST")
    var sysHealth by remember { mutableStateOf(vm.cardDataCache["${card.id}#sysHealth"] as? List<Pair<String, String>>) }
    var stat by remember { mutableStateOf(vm.cardDataCache["${card.id}#stat"] as? org.phioster.sanctumd.model.ServiceStatus) }
    @Suppress("UNCHECKED_CAST")
    var topWatchers by remember { mutableStateOf(vm.cardDataCache["${card.id}#topWatchers"] as? List<org.phioster.sanctumd.model.JellyWatchStat>) }
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(card.id, config?.id, vm.dashRefreshTick.intValue) {
        if (serviceless) return@LaunchedEffect // Section / Quick Buttons / calendar self-load
        if (config == null) { error = "service not found"; return@LaunchedEffect }
        // Already loaded this refresh cycle? Keep the cached data (fields are init'd from it) — no refetch.
        val tick = vm.dashRefreshTick.intValue
        if (vm.cardDataTick[card.id] == tick) return@LaunchedEffect
        // Retry a couple of times: on a cold start the Jellyfin token may not be ready yet.
        var attempt = 0
        while (attempt < 3) {
            error = null
            try {
                when (card.type) {
                    CardType.SECTION, CardType.QUICKBUTTONS, CardType.SHORTCUTS, CardType.UNIFIED_CALENDAR -> {}
                    CardType.JELLYFIN_SESSIONS -> sessions = vm.jellyfinSessionList(config)
                    CardType.JELLYFIN_RECENT -> items = vm.jellyfinRecent(config, null)
                    CardType.JELLYFIN_RESUME -> items = vm.jellyfinContinue(config)
                    CardType.SEERR_REQUESTS -> requests = vm.seerrList(config, "all")
                    CardType.RADARR_QUEUE, CardType.SONARR_QUEUE, CardType.LIDARR_QUEUE -> queue = vm.arrQueueList(config)
                    CardType.RADARR_MISSING, CardType.SONARR_MISSING, CardType.LIDARR_MISSING -> missing = vm.arrMissingList(config)
                    CardType.RADARR_CALENDAR, CardType.SONARR_CALENDAR, CardType.LIDARR_CALENDAR -> calendar = vm.arrCalendarList(config)
                    CardType.RADARR_HISTORY, CardType.SONARR_HISTORY, CardType.LIDARR_HISTORY -> history = vm.arrHistoryList(config)
                    CardType.NZBGET_QUEUE -> nzbQueue = vm.queue(config)
                    CardType.NZBGET_HISTORY -> nzbHistory = vm.history(config, false)
                    CardType.SEERR_TRENDING -> discover = vm.seerrDiscoverList(config, "trending")
                    CardType.SEERR_POPULAR_MOVIES -> discover = vm.seerrDiscoverList(config, "movies")
                    CardType.SEERR_POPULAR_TV -> discover = vm.seerrDiscoverList(config, "tv")
                    CardType.RADARR_HEALTH, CardType.SONARR_HEALTH, CardType.LIDARR_HEALTH -> sysHealth = vm.arrSystemInfo(config).health
                    CardType.JELLYFIN_STATS, CardType.RADARR_STATS, CardType.SONARR_STATS, CardType.LIDARR_STATS,
                    CardType.PROWLARR_STATS, CardType.NZBGET_STATS, CardType.SEERR_STATS -> stat = vm.serviceStats(config)
                    CardType.JELLYFIN_TOP -> topWatchers = vm.jellyfinTopWatchers(config)
                }
                // Cache this card's freshly-loaded data (one field is non-null) so a tab switch reuses it.
                vm.cardDataCache["${card.id}#items"] = items
                vm.cardDataCache["${card.id}#sessions"] = sessions
                vm.cardDataCache["${card.id}#requests"] = requests
                vm.cardDataCache["${card.id}#queue"] = queue
                vm.cardDataCache["${card.id}#missing"] = missing
                vm.cardDataCache["${card.id}#calendar"] = calendar
                vm.cardDataCache["${card.id}#history"] = history
                vm.cardDataCache["${card.id}#nzbQueue"] = nzbQueue
                vm.cardDataCache["${card.id}#nzbHistory"] = nzbHistory
                vm.cardDataCache["${card.id}#discover"] = discover
                vm.cardDataCache["${card.id}#sysHealth"] = sysHealth
                vm.cardDataCache["${card.id}#stat"] = stat
                vm.cardDataCache["${card.id}#topWatchers"] = topWatchers
                vm.cardDataTick[card.id] = tick
                break
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = t.message
                attempt++
                if (attempt < 3) kotlinx.coroutines.delay(1200)
            }
        }
    }

    val bgPool = when (card.type) {
        CardType.JELLYFIN_RECENT, CardType.JELLYFIN_RESUME -> items?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        CardType.SEERR_TRENDING, CardType.SEERR_POPULAR_MOVIES, CardType.SEERR_POPULAR_TV -> discover?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        else -> null
    }
    // Pick one poster at random per open; re-picks only when the data reloads (or the tab is reopened / app restarts).
    val bgUrl = remember(items, discover) { bgPool?.randomOrNull() }
    val hasBg = card.background && bgUrl != null && config != null
    val boxed = hasBg || card.theme == "solid" || card.theme == "glass"

    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = if (boxed) 8.dp else 0.dp)
            .then(if (boxed) Modifier.clip(RoundedCornerShape(14.dp)) else Modifier)
            .then(
                when {
                    hasBg -> Modifier
                    card.theme == "solid" -> Modifier.background(androidx.compose.ui.graphics.lerp(accentColor, Black, 0.4f))
                    card.theme == "glass" -> Modifier.background(Surface.copy(alpha = 0.5f)).border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    else -> Modifier
                },
            ),
    ) {
        if (hasBg) KenBurnsBackground(bgUrl!!, config!!)
        if (card.theme == "glass") {
            // Glass sheen: a soft diagonal highlight tinted with the accent.
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, accentColor.copy(alpha = 0.10f))),
                ),
            )
        }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = if (boxed) 12.dp else 0.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { onOpenService() }, verticalAlignment = Alignment.CenterVertically) {
                if (card.icon.isNotBlank()) {
                    Icon(tabIcon(card.icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                } else if (config != null && card.type != CardType.SECTION) {
                    // No custom icon chosen -> the service's brand logo.
                    if (card.theme == "glass") {
                        // Frosted chip: a rounded rectangle whose outer edge is blurred so
                        // it feathers softly into the glass, with the logo crisp on top.
                        // Keeps logos with dark parts (e.g. Radarr's navy ring) legible.
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(26.dp)
                                    .blur(4.dp, BlurredEdgeTreatment.Unbounded)
                                    .background(Color.White.copy(alpha = 0.26f), RoundedCornerShape(8.dp)),
                            )
                            ServiceLogo(config.type, 20.dp)
                        }
                    } else {
                        ServiceLogo(config.type, 18.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(card.title.ifBlank { card.type.label }.uppercase(), fontFamily = Mono, color = if (card.theme == "solid") Black else if (card.type == CardType.SECTION) accentColor else MatrixGreen, fontSize = if (card.type == CardType.SECTION) 15.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!serviceless || config != null) Text(config?.label ?: "?", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
            if (editMode) {
                TextButton(onClick = { showConfig = true }, contentPadding = PaddingValues(4.dp)) { Text("⚙", fontFamily = Mono, color = MatrixGreen) }
                if (!isFirst) TextButton(onClick = onMoveUp, contentPadding = PaddingValues(4.dp)) { Text("↑", fontFamily = Mono, color = MatrixGreen) }
                if (!isLast) TextButton(onClick = onMoveDown, contentPadding = PaddingValues(4.dp)) { Text("↓", fontFamily = Mono, color = MatrixGreen) }
                TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) { Text("✕", fontFamily = Mono, color = ErrRed) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val loading = @Composable { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
        val empty = @Composable { msg: String -> Text(msg, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
        when {
            card.type == CardType.JELLYFIN_TOP -> {
                val tw = topWatchers
                when {
                    error != null -> empty("no playback data — install the Jellyfin “Playback Reporting” plugin")
                    tw == null -> loading()
                    tw.isEmpty() -> empty("no playback data yet")
                    else -> JellyPodium(tw, accent, card.theme == "solid")
                }
            }
            error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
            card.type == CardType.SECTION -> HorizontalDivider(color = accentColor.copy(alpha = 0.6f), thickness = 2.dp)
            card.type == CardType.SHORTCUTS -> {
                val scs = config?.shortcuts.orEmpty()
                var pendingSc by remember { mutableStateOf<String?>(null) }
                if (scs.isEmpty()) empty("no shortcuts configured")
                else Column {
                    scs.forEach { sc ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (pendingSc == sc.name) {
                                        pendingSc = null
                                        scope.launch {
                                            val res = runCatching { vm.runShortcut(config!!, sc) }.getOrElse { it.message ?: "failed" }
                                            android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        pendingSc = sc.name
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("▸ ${sc.name}", fontFamily = Mono, color = accentColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (pendingSc == sc.name) Text("tap again", fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 10.sp)
                        }
                    }
                }
            }
            card.type == CardType.QUICKBUTTONS -> {
                // Bound to one service when the card has a serviceId; legacy cards
                // (serviceId "") keep the old all-services list.
                val actions =
                    if (config != null) quickActionsFor(config).map { config to it }
                    else allServices.flatMap { svc -> quickActionsFor(svc).map { svc to it } }
                if (actions.isEmpty()) empty("no actions available")
                else Column {
                    actions.forEach { (svc, qa) ->
                        Text(
                            "▸ ${qa.label}",
                            fontFamily = Mono, color = accentColor, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val res = runCatching { qa.run(vm, svc) }.getOrElse { it.message ?: "failed" }
                                        android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
            card.type == CardType.RADARR_HEALTH || card.type == CardType.SONARR_HEALTH || card.type == CardType.LIDARR_HEALTH -> {
                val h = sysHealth
                when {
                    h == null -> loading()
                    h.isEmpty() -> Text("✓ all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                    else -> Column {
                        h.take(card.count).forEach { (type, msg) ->
                            val col = when (type.lowercase()) { "error" -> ErrRed; "warning" -> Color(0xFFE0A030); else -> MatrixGreen.copy(alpha = 0.8f) }
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(msg, fontFamily = Mono, color = col, fontSize = 12.sp)
                                Text(type.uppercase(), fontFamily = Mono, color = col.copy(alpha = 0.6f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            card.type == CardType.JELLYFIN_STATS || card.type == CardType.RADARR_STATS || card.type == CardType.SONARR_STATS ||
                card.type == CardType.LIDARR_STATS || card.type == CardType.PROWLARR_STATS || card.type == CardType.NZBGET_STATS ||
                card.type == CardType.SEERR_STATS -> {
                val st = stat
                when {
                    st == null || st.isLoading -> loading()
                    !st.ok -> Text("offline${st.error?.let { ": $it" } ?: ""}", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
                    st.stats.isEmpty() -> empty("no stats")
                    else -> Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        st.stats.forEach { (k, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = if (card.theme == "solid") Black else MatrixGreen, fontSize = 22.sp)
                                Text(k.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            card.type == CardType.JELLYFIN_SESSIONS -> {
                val s = sessions
                when {
                    s == null -> loading()
                    s.isEmpty() -> empty("no active sessions")
                    else -> Column { s.forEach { DashSessionRow(it, accent, card.density) } }
                }
            }
            card.type == CardType.SEERR_REQUESTS -> {
                val r = requests
                when {
                    r == null -> loading()
                    r.isEmpty() -> empty("no requests")
                    else -> Column { r.take(card.count).forEach { DashLineRow(it.title, it.subtitle.ifBlank { it.status }, accent, card.density) { onOpenService() } } }
                }
            }
            card.type == CardType.RADARR_QUEUE || card.type == CardType.SONARR_QUEUE || card.type == CardType.LIDARR_QUEUE -> {
                val q = queue
                when {
                    q == null -> loading()
                    q.isEmpty() -> empty("queue empty")
                    else -> Column { q.take(card.count).forEach { DashQueueRow(it, accent, card.density) } }
                }
            }
            card.type == CardType.RADARR_MISSING || card.type == CardType.SONARR_MISSING || card.type == CardType.LIDARR_MISSING -> {
                val m = missing
                when {
                    m == null -> loading()
                    m.isEmpty() -> empty("nothing missing")
                    else -> Column { m.take(card.count).forEach { DashLineRow(it.title, it.subtitle, accent, card.density) { onOpenService() } } }
                }
            }
            card.type == CardType.RADARR_CALENDAR || card.type == CardType.SONARR_CALENDAR || card.type == CardType.LIDARR_CALENDAR -> {
                val c = calendar
                when {
                    c == null -> loading()
                    c.isEmpty() -> empty("nothing upcoming")
                    else -> Column { c.take(card.count).forEach { DashLineRow("${if (it.hasFile) "✓ " else ""}${it.title}", "${it.date}${if (it.subtitle.isNotBlank()) " · ${it.subtitle}" else ""}", accent, card.density) { onOpenService() } } }
                }
            }
            card.type == CardType.UNIFIED_CALENDAR -> UnifiedCalendarCard(vm, accent, onOpenAny)
            card.type == CardType.RADARR_HISTORY || card.type == CardType.SONARR_HISTORY || card.type == CardType.LIDARR_HISTORY -> {
                val h = history
                when {
                    h == null -> loading()
                    h.isEmpty() -> empty("no history")
                    else -> Column { h.take(card.count).forEach { DashLineRow(it.title, "${it.eventType} · ${it.date}", accent, card.density) { onOpenService() } } }
                }
            }
            card.type == CardType.NZBGET_QUEUE -> {
                val q = nzbQueue
                when {
                    q == null -> loading()
                    q.isEmpty() -> empty("queue empty")
                    else -> Column { q.take(card.count).forEach { DashNzbRow(it.name, it.status, it.progress, accent, card.density) } }
                }
            }
            card.type == CardType.NZBGET_HISTORY -> {
                val h = nzbHistory
                when {
                    h == null -> loading()
                    h.isEmpty() -> empty("no history")
                    else -> Column { h.take(card.count).forEach { DashLineRow(it.name, it.status, accent, card.density) { onOpenService() } } }
                }
            }
            card.type == CardType.SEERR_TRENDING || card.type == CardType.SEERR_POPULAR_MOVIES || card.type == CardType.SEERR_POPULAR_TV -> {
                val d = discover
                when {
                    d == null -> loading()
                    d.isEmpty() -> empty("nothing here")
                    else -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                        d.take(card.count).forEach { di ->
                            DashDiscoverPoster(di, posterWidth, card.density != "compact") {
                                config?.let { c -> scope.launch { detail = runCatching { vm.seerrMediaDetailById(c, di.tmdbId, di.mediaType).toMediaDetail() }.getOrNull() } }
                            }
                        }
                    }
                }
            }
            else -> {
                val it2 = items
                when {
                    it2 == null -> loading()
                    it2.isEmpty() -> empty("nothing here")
                    else -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                        it2.take(card.count).forEach { m ->
                            if (config != null) JellyPosterCard(m, config, accent, posterWidth, card.density != "compact") {
                                scope.launch { detail = runCatching { vm.jellyfinMediaDetail(config, m.id).toMediaDetail() }.getOrNull() }
                            }
                        }
                    }
                }
            }
        }
        if (!boxed) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
    }
    }

    detail?.let { d -> if (config != null) MediaDetailDialog(d, config) { detail = null } }

    if (showConfig) {
        var cfgTitle by remember { mutableStateOf(card.title) }
        var cfgCount by remember { mutableStateOf(card.count) }
        var cfgAccent by remember { mutableStateOf(card.accent) }
        var cfgIcon by remember { mutableStateOf(card.icon) }
        var cfgPoster by remember { mutableStateOf(card.posterSize) }
        var cfgBg by remember { mutableStateOf(card.background) }
        var cfgTheme by remember { mutableStateOf(card.theme) }
        var cfgDensity by remember { mutableStateOf(card.density) }
        val isPoster = card.type in setOf(CardType.JELLYFIN_RECENT, CardType.JELLYFIN_RESUME, CardType.SEERR_TRENDING, CardType.SEERR_POPULAR_MOVIES, CardType.SEERR_POPULAR_TV)
        val serviceColor = if (config != null) Color(config.type.accent) else MatrixGreen
        val label = @Composable { t: String -> Text(t, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp) }
        AlertDialog(
            onDismissRequest = { showConfig = false },
            containerColor = Surface,
            title = { Text("Card settings", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Field("Title (blank = ${card.type.label})", cfgTitle) { cfgTitle = it }
                    if (card.type != CardType.UNIFIED_CALENDAR) { // count/density don't apply to the calendar grid
                    Spacer(Modifier.height(16.dp))
                    label("ENTRIES SHOWN")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (cfgCount > 3) cfgCount-- }, contentPadding = PaddingValues(8.dp)) { Text("−", fontFamily = Mono, color = MatrixGreen, fontSize = 22.sp) }
                        Text("$cfgCount", fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, modifier = Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center)
                        TextButton(onClick = { if (cfgCount < 20) cfgCount++ }, contentPadding = PaddingValues(8.dp)) { Text("+", fontFamily = Mono, color = MatrixGreen, fontSize = 22.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    label("DENSITY")
                    Spacer(Modifier.height(6.dp))
                    Row {
                        listOf("compact" to "Compact", "" to "Normal", "detail" to "Detail").forEach { (value, lbl) ->
                            val sel = cfgDensity == value
                            Box(
                                Modifier.padding(end = 8.dp).size(width = 92.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { cfgDensity = value },
                                contentAlignment = Alignment.Center,
                            ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    }
                    label("CARD STYLE")
                    Spacer(Modifier.height(6.dp))
                    Row {
                        listOf("" to "Flat", "solid" to "Solid", "glass" to "Glass").forEach { (value, lbl) ->
                            val sel = cfgTheme == value
                            Box(
                                Modifier.padding(end = 8.dp).size(width = 74.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { cfgTheme = value },
                                contentAlignment = Alignment.Center,
                            ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    label("ACCENT (1st = service default)")
                    Spacer(Modifier.height(6.dp))
                    AccentPickerRow(cfgAccent, serviceColor) { cfgAccent = it }
                    Spacer(Modifier.height(16.dp))
                    label("HEADER ICON")
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        val noneSel = cfgIcon.isBlank()
                        Box(
                            Modifier.padding(end = 8.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (noneSel) MatrixGreen else Surface)
                                .border(1.dp, if (noneSel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { cfgIcon = "" },
                            contentAlignment = Alignment.Center,
                        ) { Text("∅", fontFamily = Mono, color = if (noneSel) Black else MatrixGreen, fontSize = 18.sp) }
                        tabIcons.forEach { (key, icon) ->
                            val sel = cfgIcon == key
                            Box(
                                Modifier.padding(end = 8.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { cfgIcon = key },
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, contentDescription = key, tint = if (sel) Black else MatrixGreen) }
                        }
                    }
                    if (isPoster) {
                        Spacer(Modifier.height(16.dp))
                        label("POSTER SIZE")
                        Spacer(Modifier.height(6.dp))
                        Row {
                            listOf("small" to "S", "" to "M", "large" to "L").forEach { (value, lbl) ->
                                val sel = cfgPoster == value
                                Box(
                                    Modifier.padding(end = 8.dp).size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) MatrixGreen else Surface)
                                        .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { cfgPoster = value },
                                    contentAlignment = Alignment.Center,
                                ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 15.sp) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { cfgBg = !cfgBg },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                    .background(if (cfgBg) MatrixGreen else Surface)
                                    .border(1.dp, if (cfgBg) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (cfgBg) Text("✓", fontFamily = Mono, color = Black, fontSize = 13.sp) }
                            Spacer(Modifier.width(10.dp))
                            Text("Fanart background (Ken Burns)", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSaveConfig(cfgTitle, cfgCount, cfgAccent, cfgIcon, cfgPoster, cfgBg, cfgTheme, cfgDensity); showConfig = false }) {
                    Text("Save", fontFamily = Mono, color = MatrixGreen)
                }
            },
            dismissButton = { TextButton(onClick = { showConfig = false }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}
