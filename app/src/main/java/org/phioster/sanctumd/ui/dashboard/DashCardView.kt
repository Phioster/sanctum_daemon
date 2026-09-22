package org.phioster.sanctumd.ui.dashboard

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
import org.phioster.sanctumd.model.ServiceConfig
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
import org.phioster.sanctumd.ServiceLogo
import org.phioster.sanctumd.ui.theme.AppIcons

@Composable
internal fun DashCardView(
    vm: DashboardViewModel,
    card: org.phioster.sanctumd.model.DashCard,
    config: ServiceConfig?,
    editMode: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenService: () -> Unit,
    /** Open this card's service at a specific item instead of at its front page. */
    onOpenLink: (org.phioster.sanctumd.ui.search.SearchDeepLink) -> Unit = {},
    onOpenAny: (ServiceConfig) -> Unit = {},
    onOpenAnyLink: (ServiceConfig, org.phioster.sanctumd.ui.search.SearchDeepLink) -> Unit = { cfg, _ -> onOpenAny(cfg) },
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
    val data = rememberDashCardData(vm, card.id)
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(card.id, config?.id, vm.dashRefreshTick.intValue) { data.load(vm, card, config) }

    val bgPool = when (card.type) {
        CardType.JELLYFIN_RECENT, CardType.JELLYFIN_RESUME -> data.items?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        CardType.SEERR_TRENDING, CardType.SEERR_POPULAR_MOVIES, CardType.SEERR_POPULAR_TV -> data.discover?.take(card.count)?.map { it.posterUrl }?.filter { it.isNotBlank() }
        else -> null
    }
    // Pick one poster at random per open; re-picks only when the data reloads (or the tab is reopened / app restarts).
    val bgUrl = remember(data.items, data.discover) { bgPool?.randomOrNull() }
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
                TextButton(onClick = { showConfig = true }, contentPadding = PaddingValues(4.dp)) {
                    Icon(AppIcons.Settings, contentDescription = "Configure", tint = MatrixGreen, modifier = Modifier.size(17.dp))
                }
                if (!isFirst) {
                    TextButton(onClick = onMoveUp, contentPadding = PaddingValues(4.dp)) {
                        Icon(AppIcons.MoveUp, contentDescription = "Move up", tint = MatrixGreen, modifier = Modifier.size(17.dp))
                    }
                }
                if (!isLast) {
                    TextButton(onClick = onMoveDown, contentPadding = PaddingValues(4.dp)) {
                        Icon(AppIcons.MoveDown, contentDescription = "Move down", tint = MatrixGreen, modifier = Modifier.size(17.dp))
                    }
                }
                TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) {
                    Icon(AppIcons.Cancel, contentDescription = "Remove", tint = ErrRed, modifier = Modifier.size(17.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // The three types that do more than show what they loaded; everything else is a
        // function of the card's data and lives in DashCardDataBody, where a test can reach it.
        when (card.type) {
            CardType.SHORTCUTS -> ShortcutsCardBody(vm, config, accentColor, scope, ctx)
            CardType.QUICKBUTTONS -> QuickActionsCardBody(vm, config, allServices, accentColor, scope, ctx)
            CardType.UNIFIED_CALENDAR -> UnifiedCalendarCard(vm, accent, onOpenAny, onOpenAnyLink)
            else -> DashCardDataBody(
                card = card,
                config = config,
                data = data,
                accent = accent,
                accentColor = accentColor,
                posterWidth = posterWidth,
                onOpenService = onOpenService,
                onOpenArrItem = { id ->
                    if (id > 0) onOpenLink(org.phioster.sanctumd.ui.search.SearchDeepLink(arrDetailId = id)) else onOpenService()
                },
                onOpenItem = { m ->
                    if (config != null) {
                        scope.launch { detail = runCatching { vm.jellyfinMediaDetail(config, m.id).toMediaDetail() }.getOrNull() }
                    }
                },
                onOpenDiscover = { di ->
                    config?.let { c ->
                        scope.launch { detail = runCatching { vm.seerrMediaDetailById(c, di.tmdbId, di.mediaType).toMediaDetail() }.getOrNull() }
                    }
                },
            )
        }
        if (!boxed) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
        }
    }
    }

    detail?.let { d -> if (config != null) MediaDetailDialog(d, config, onOpen = onOpenLink) { detail = null } }

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
                            ) { if (cfgBg) Icon(AppIcons.Done, contentDescription = "checked", tint = Black, modifier = Modifier.size(14.dp)) }
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
