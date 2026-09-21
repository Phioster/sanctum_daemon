package org.phioster.sanctumd.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.WarnAmber
import org.phioster.sanctumd.ui.theme.WarnAmberDim

/**
 * What a card shows below its heading, for every type whose content is just its data.
 *
 * Deliberately free of the view model: everything here is a function of [data], which is what
 * makes it possible to render a card in a test. The three types that do more than show what
 * they loaded -- shortcuts, quick actions and the unified calendar -- are handled by their own
 * composables below, and the card picks between them.
 */
@Composable
internal fun DashCardDataBody(
    card: DashCard,
    config: ServiceConfig?,
    data: DashCardData,
    accent: Color,
    accentColor: Color,
    posterWidth: Dp,
    onOpenService: () -> Unit,
    onOpenItem: (JellyMediaItem) -> Unit,
    onOpenDiscover: (SeerrDiscoverItem) -> Unit,
) {
    val loading = @Composable { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
    val empty = @Composable { msg: String -> Text(msg, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
    when {
        card.type == CardType.JELLYFIN_TOP -> {
            val tw = data.topWatchers
            when {
                data.error != null -> empty("no playback data — install the Jellyfin “Playback Reporting” plugin")
                tw == null -> loading()
                tw.isEmpty() -> empty("no playback data yet")
                else -> JellyPodium(tw, accent, card.theme == "solid")
            }
        }
        data.error != null -> Text(org.phioster.sanctumd.ui.services.friendlyStatusError(data.error), fontFamily = Mono, color = ErrRed, fontSize = 11.sp)
        card.type == CardType.SECTION -> HorizontalDivider(color = accentColor.copy(alpha = 0.6f), thickness = 2.dp)
        card.type == CardType.RADARR_HEALTH || card.type == CardType.SONARR_HEALTH || card.type == CardType.LIDARR_HEALTH -> {
            val h = data.sysHealth
            when {
                h == null -> loading()
                h.isEmpty() -> Text("✓ all healthy", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                else -> Column {
                    h.take(card.count).forEach { (type, msg) ->
                        val col = when (type.lowercase()) { "error" -> ErrRed; "warning" -> WarnAmberDim; else -> MatrixGreen.copy(alpha = 0.8f) }
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
            val st = data.stat
            when {
                st == null || st.isLoading -> loading()
                !st.ok -> Text(st.error?.let { org.phioster.sanctumd.ui.services.friendlyStatusError(it) } ?: "offline", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
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
            val s = data.sessions
            when {
                s == null -> loading()
                s.isEmpty() -> empty("no active sessions")
                else -> Column { s.forEach { DashSessionRow(it, accent, card.density) } }
            }
        }
        card.type == CardType.SEERR_REQUESTS -> {
            val r = data.requests
            when {
                r == null -> loading()
                r.isEmpty() -> empty("no requests")
                else -> Column { r.take(card.count).forEach { DashLineRow(it.title, it.subtitle.ifBlank { it.status }, accent, card.density) { onOpenService() } } }
            }
        }
        card.type == CardType.RADARR_QUEUE || card.type == CardType.SONARR_QUEUE || card.type == CardType.LIDARR_QUEUE -> {
            val q = data.queue
            when {
                q == null -> loading()
                q.isEmpty() -> empty("queue empty")
                else -> Column { q.take(card.count).forEach { DashQueueRow(it, accent, card.density) } }
            }
        }
        card.type == CardType.RADARR_MISSING || card.type == CardType.SONARR_MISSING || card.type == CardType.LIDARR_MISSING -> {
            val m = data.missing
            when {
                m == null -> loading()
                m.isEmpty() -> empty("nothing missing")
                else -> Column { m.take(card.count).forEach { DashLineRow(it.title, it.subtitle, accent, card.density) { onOpenService() } } }
            }
        }
        card.type == CardType.RADARR_CALENDAR || card.type == CardType.SONARR_CALENDAR || card.type == CardType.LIDARR_CALENDAR -> {
            val c = data.calendar
            when {
                c == null -> loading()
                c.isEmpty() -> empty("nothing upcoming")
                else -> Column { c.take(card.count).forEach { DashLineRow("${if (it.hasFile) "✓ " else ""}${it.title}", "${it.date}${if (it.subtitle.isNotBlank()) " · ${it.subtitle}" else ""}", accent, card.density) { onOpenService() } } }
            }
        }
        card.type == CardType.RADARR_HISTORY || card.type == CardType.SONARR_HISTORY || card.type == CardType.LIDARR_HISTORY -> {
            val h = data.history
            when {
                h == null -> loading()
                h.isEmpty() -> empty("no history")
                else -> Column { h.take(card.count).forEach { DashLineRow(it.title, "${it.eventType} · ${it.date}", accent, card.density) { onOpenService() } } }
            }
        }
        card.type == CardType.NZBGET_QUEUE -> {
            val q = data.nzbQueue
            when {
                q == null -> loading()
                q.isEmpty() -> empty("queue empty")
                else -> Column { q.take(card.count).forEach { DashNzbRow(it.name, it.status, it.progress, accent, card.density) } }
            }
        }
        card.type == CardType.NZBGET_HISTORY -> {
            val h = data.nzbHistory
            when {
                h == null -> loading()
                h.isEmpty() -> empty("no history")
                else -> Column { h.take(card.count).forEach { DashLineRow(it.name, it.status, accent, card.density) { onOpenService() } } }
            }
        }
        card.type == CardType.SEERR_TRENDING || card.type == CardType.SEERR_POPULAR_MOVIES || card.type == CardType.SEERR_POPULAR_TV -> {
            val d = data.discover
            when {
                d == null -> loading()
                d.isEmpty() -> empty("nothing here")
                else -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                    d.take(card.count).forEach { di ->
                        DashDiscoverPoster(di, posterWidth, card.density != "compact") {
                            onOpenDiscover(di)
                        }
                    }
                }
            }
        }
        else -> {
            val it2 = data.items
            when {
                it2 == null -> loading()
                it2.isEmpty() -> empty("nothing here")
                else -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                    it2.take(card.count).forEach { m ->
                        if (config != null) JellyPosterCard(m, config, accent, posterWidth, card.density != "compact") {
                            onOpenItem(m)
                        }
                    }
                }
            }
        }
    }
}

/** The card that runs a service's configured HTTP shortcuts, two taps each. */
@Composable
internal fun ShortcutsCardBody(
    vm: DashboardViewModel,
    config: ServiceConfig?,
    accentColor: Color,
    scope: CoroutineScope,
    ctx: android.content.Context,
) {
    val empty = @Composable { msg: String -> Text(msg, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
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
                    if (pendingSc == sc.name) Text("tap again", fontFamily = Mono, color = WarnAmber, fontSize = 10.sp)
                }
            }
        }
}

/** The card that offers a service's quick actions, or every service's when it is not bound to one. */
@Composable
internal fun QuickActionsCardBody(
    vm: DashboardViewModel,
    config: ServiceConfig?,
    allServices: List<ServiceConfig>,
    accentColor: Color,
    scope: CoroutineScope,
    ctx: android.content.Context,
) {
    val empty = @Composable { msg: String -> Text(msg, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 12.sp) }
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
