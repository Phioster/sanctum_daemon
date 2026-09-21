package org.phioster.sanctumd.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.phioster.sanctumd.model.ArrCalendarItem
import org.phioster.sanctumd.model.ArrHistoryItem
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.JellySession
import org.phioster.sanctumd.model.JellyWatchStat
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.SeerrRequestItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import org.phioster.sanctumd.ui.DashboardViewModel

/**
 * What one dashboard card knows.
 *
 * A card shows one kind of thing, so exactly one of these is ever filled — which one is decided
 * by the card's type, in [load]. They sit together because the cache keys follow the same names
 * and a card is saved and restored as a whole.
 */
internal class DashCardData {
    var items by mutableStateOf<List<JellyMediaItem>?>(null)
    var sessions by mutableStateOf<List<JellySession>?>(null)
    var requests by mutableStateOf<List<SeerrRequestItem>?>(null)
    var queue by mutableStateOf<List<ArrQueueItem>?>(null)
    var missing by mutableStateOf<List<ArrMissingItem>?>(null)
    var calendar by mutableStateOf<List<ArrCalendarItem>?>(null)
    var history by mutableStateOf<List<ArrHistoryItem>?>(null)
    var nzbQueue by mutableStateOf<List<NzbQueueItem>?>(null)
    var nzbHistory by mutableStateOf<List<NzbHistoryEntry>?>(null)
    var discover by mutableStateOf<List<SeerrDiscoverItem>?>(null)
    var sysHealth by mutableStateOf<List<Pair<String, String>>?>(null)
    var stat by mutableStateOf<ServiceStatus?>(null)
    var topWatchers by mutableStateOf<List<JellyWatchStat>?>(null)
    var error by mutableStateOf<String?>(null)

    /**
     * Seeds the fields from the per-card cache, so switching tabs shows the last data at once
     * instead of a row of "loading…" that refetches what was already there.
     */
    @Suppress("UNCHECKED_CAST")
    fun restore(vm: DashboardViewModel, cardId: String) {
        val c = vm.cardDataCache
        items = c["$cardId#items"] as? List<JellyMediaItem>
        sessions = c["$cardId#sessions"] as? List<JellySession>
        requests = c["$cardId#requests"] as? List<SeerrRequestItem>
        queue = c["$cardId#queue"] as? List<ArrQueueItem>
        missing = c["$cardId#missing"] as? List<ArrMissingItem>
        calendar = c["$cardId#calendar"] as? List<ArrCalendarItem>
        history = c["$cardId#history"] as? List<ArrHistoryItem>
        nzbQueue = c["$cardId#nzbQueue"] as? List<NzbQueueItem>
        nzbHistory = c["$cardId#nzbHistory"] as? List<NzbHistoryEntry>
        discover = c["$cardId#discover"] as? List<SeerrDiscoverItem>
        sysHealth = c["$cardId#sysHealth"] as? List<Pair<String, String>>
        stat = c["$cardId#stat"] as? ServiceStatus
        topWatchers = c["$cardId#topWatchers"] as? List<JellyWatchStat>
    }

    private fun store(vm: DashboardViewModel, cardId: String, tick: Int) {
        val c = vm.cardDataCache
        c["$cardId#items"] = items
        c["$cardId#sessions"] = sessions
        c["$cardId#requests"] = requests
        c["$cardId#queue"] = queue
        c["$cardId#missing"] = missing
        c["$cardId#calendar"] = calendar
        c["$cardId#history"] = history
        c["$cardId#nzbQueue"] = nzbQueue
        c["$cardId#nzbHistory"] = nzbHistory
        c["$cardId#discover"] = discover
        c["$cardId#sysHealth"] = sysHealth
        c["$cardId#stat"] = stat
        c["$cardId#topWatchers"] = topWatchers
        vm.cardDataTick[cardId] = tick
    }

    /**
     * Fetches what this card shows, unless this refresh cycle already did.
     *
     * Retried twice: on a cold start the Jellyfin token may not be ready when the first card
     * asks, and a card that gave up then would stay empty until the next refresh.
     */
    suspend fun load(vm: DashboardViewModel, card: DashCard, config: ServiceConfig?) {
        if (card.type.service == null) return // Section, Quick Buttons, Shortcuts, calendar self-load
        if (config == null) { error = "service not found"; return }
        val tick = vm.dashRefreshTick.intValue
        if (vm.cardDataTick[card.id] == tick) return
        var attempt = 0
        while (attempt < 3) {
            error = null
            try {
                fetch(vm, card, config)
                store(vm, card.id, tick)
                return
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = t.message
                attempt++
                if (attempt < 3) delay(1200)
            }
        }
    }

    private suspend fun fetch(vm: DashboardViewModel, card: DashCard, config: ServiceConfig) {
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
    }
}

/** One holder per card, seeded from the cache the first time that card is composed. */
@Composable
internal fun rememberDashCardData(vm: DashboardViewModel, cardId: String): DashCardData =
    remember(cardId) { DashCardData().also { it.restore(vm, cardId) } }
