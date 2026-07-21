package org.phioster.sanctumd.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
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
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ServiceLogo

/** What to open inside a service screen when a search hit is tapped. */
internal data class SearchDeepLink(
    val arrDetailId: Int? = null, // arr: open this library item's detail
    val arrAddTerm: String? = null, // arr: open the add dialog pre-filled with this lookup term
    val seerrTmdb: Int? = null, // Seerr: open this media detail
    val seerrMediaType: String = "",
    val jellyItemId: String? = null, // Jellyfin: open this item's detail
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearchScreen(
    vm: DashboardViewModel,
    onBack: () -> Unit,
    onOpenService: (ServiceConfig, SearchDeepLink?) -> Unit,
    initialTerm: String = "",
    onTermChange: (String) -> Unit = {},
) {
    val services by vm.services.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var term by remember { mutableStateOf(initialTerm) }
    var results by remember { mutableStateOf<List<org.phioster.sanctumd.model.SearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<org.phioster.sanctumd.model.SearchResult?>(null) } // tapped hit -> action dialog
    // Result filters: empty service set = all; year accepts "2021" or "2018-2022"; status 0=all 1=have 2=missing.
    var filterServices by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterYear by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    fun matchesYear(y: Int): Boolean {
        val f = filterYear.trim()
        if (f.isBlank()) return true
        if (y == 0) return false
        val range = f.split("-").mapNotNull { it.trim().toIntOrNull() }
        return when {
            range.size == 2 -> y in range[0]..range[1]
            range.size == 1 -> y == range[0]
            else -> true
        }
    }

    fun run() {
        val q = term.trim()
        if (q.isBlank()) return
        onTermChange(q) // hoist so returning from a result reopens this exact search
        scope.launch {
            searching = true
            results = runCatching { vm.globalSearch(q) }.getOrDefault(emptyList())
            searching = false
        }
    }
    LaunchedEffect(Unit) { if (initialTerm.isBlank()) runCatching { focusRequester.requestFocus() } else run() }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("search", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("title across all services", fontFamily = Mono) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { run() }) { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MatrixGreen) }
            }
            // Filters (shown once there are results to narrow down)
            if (results != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    services.filter { svc -> results.orEmpty().any { it.serviceId == svc.id } }.forEach { svc ->
                        val sel = svc.id in filterServices
                        FilterChip(
                            selected = sel || filterServices.isEmpty(),
                            onClick = { filterServices = if (sel) filterServices - svc.id else filterServices + svc.id },
                            leadingIcon = { ServiceLogo(svc.type, 16.dp) },
                            label = { Text(svc.label, fontFamily = Mono, fontSize = 11.sp) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = filterYear,
                        onValueChange = { filterYear = it },
                        label = { Text("year / from-to", fontFamily = Mono, fontSize = 10.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        modifier = Modifier.width(150.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    listOf("all", "have", "missing").forEachIndexed { i, lbl ->
                        FilterChip(
                            selected = filterStatus == i,
                            onClick = { filterStatus = i },
                            label = { Text(lbl, fontFamily = Mono, fontSize = 11.sp) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
            HorizontalDivider(color = MatrixGreen.copy(alpha = 0.2f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val r = results?.filter { hit ->
                    (filterServices.isEmpty() || hit.serviceId in filterServices) &&
                        matchesYear(hit.year) &&
                        when (filterStatus) { 1 -> hit.inLibrary; 2 -> !hit.inLibrary; else -> true }
                }
                when {
                    searching -> Text("searching…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    r == null -> Text("type a title, then search", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    r.isEmpty() -> Text(if (results.orEmpty().isEmpty()) "no matches" else "no matches with these filters", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    else -> {
                        val grouped = r.groupBy { it.serviceId }
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            grouped.forEach { (sid, hits) ->
                                val head = hits.first()
                                item(key = "h_$sid") {
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ServiceLogo(head.serviceType, 16.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "${head.serviceLabel} · ${head.serviceType.label}".uppercase(),
                                            fontFamily = Mono, color = Color(head.serviceType.accent), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
                                }
                                items(hits, key = { "${sid}_${it.title}_${it.subtitle}" }) { hit ->
                                    SearchResultRow(hit, services.firstOrNull { it.id == hit.serviceId }) { chosen = hit }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Tapped hit -> show the object with the actions that make sense for it.
    chosen?.let { hit ->
        val cfg = services.firstOrNull { it.id == hit.serviceId }
        val accent = Color(hit.serviceType.accent)
        fun open(link: SearchDeepLink?) {
            chosen = null
            cfg?.let { onOpenService(it, link) }
        }
        AlertDialog(
            onDismissRequest = { chosen = null },
            containerColor = Surface,
            title = { Text(hit.title, fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hit.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = searchPosterModel(hit, cfg),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(64.dp).height(96.dp).clip(RoundedCornerShape(6.dp)).background(Black),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text("${hit.serviceLabel} · ${hit.serviceType.label}".uppercase(), fontFamily = Mono, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (hit.subtitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(hit.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val actions: List<Pair<String, SearchDeepLink?>> = when (hit.serviceType) {
                        ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR ->
                            if (hit.libraryId > 0) listOf("open details" to SearchDeepLink(arrDetailId = hit.libraryId.toInt()))
                            else listOf("add to ${hit.serviceType.label}…" to SearchDeepLink(arrAddTerm = hit.title))
                        ServiceType.SEERR ->
                            if (hit.tmdbId > 0) listOf("details / request" to SearchDeepLink(seerrTmdb = hit.tmdbId, seerrMediaType = hit.mediaType)) else emptyList()
                        ServiceType.JELLYFIN ->
                            if (hit.jellyItemId.isNotBlank()) listOf("open details" to SearchDeepLink(jellyItemId = hit.jellyItemId)) else emptyList()
                        else -> emptyList()
                    }
                    (actions + ("open ${hit.serviceLabel}" to null)).forEach { (label, link) ->
                        Text(
                            "› $label",
                            fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { open(link) }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chosen = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) }
            },
        )
    }
}

/** Coil model for a search-hit poster; Jellyfin posters need auth headers. */
@Composable
internal fun searchPosterModel(hit: org.phioster.sanctumd.model.SearchResult, config: ServiceConfig?): Any {
    if (hit.serviceType != ServiceType.JELLYFIN || config == null) return hit.posterUrl
    val ctx = LocalContext.current
    return ImageRequest.Builder(ctx).data(hit.posterUrl).apply {
        config.customHeaders.forEach { (k, v) -> addHeader(k, v) }
        org.phioster.sanctumd.net.jellyfinImageHeaders(config).forEach { (k, v) -> addHeader(k, v) }
    }.build()
}

@Composable
internal fun SearchResultRow(hit: org.phioster.sanctumd.model.SearchResult, config: ServiceConfig?, onClick: () -> Unit) {
    val accent = Color(hit.serviceType.accent)
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hit.posterUrl.isNotBlank()) {
            AsyncImage(
                model = searchPosterModel(hit, config),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Surface),
            )
        } else {
            Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}
