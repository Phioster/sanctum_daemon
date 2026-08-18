package org.phioster.sanctumd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.model.ServiceStatus
import org.phioster.sanctumd.model.ArrDetail
import org.phioster.sanctumd.model.ArrEpisode
import org.phioster.sanctumd.model.ArrHistoryItem
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ArrLookupItem
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.ProwlarrCategory
import org.phioster.sanctumd.model.ProwlarrHistoryItem
import org.phioster.sanctumd.model.ProwlarrIndexerItem
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ProwlarrSystemInfo
import org.phioster.sanctumd.model.ProwlarrTaskItem
import org.phioster.sanctumd.model.SeerrIssueItem
import org.phioster.sanctumd.model.SeerrRequestItem
import org.phioster.sanctumd.model.SeerrSearchItem
import org.phioster.sanctumd.net.arrAdd
import org.phioster.sanctumd.net.arrAlbums
import org.phioster.sanctumd.net.arrTracks
import org.phioster.sanctumd.net.arrCutoff
import org.phioster.sanctumd.net.arrDelete
import org.phioster.sanctumd.net.arrDetail
import org.phioster.sanctumd.net.arrEpisodes
import org.phioster.sanctumd.net.arrGrab
import org.phioster.sanctumd.net.arrHistory
import org.phioster.sanctumd.net.arrCalendar
import org.phioster.sanctumd.net.arrLibrary
import org.phioster.sanctumd.net.arrManualImportExecute
import org.phioster.sanctumd.net.arrManualImportScan
import org.phioster.sanctumd.net.arrReleases
import org.phioster.sanctumd.net.arrSearchAll
import org.phioster.sanctumd.net.arrSystem
import org.phioster.sanctumd.net.serviceSearch
import org.phioster.sanctumd.net.seerrCast
import org.phioster.sanctumd.net.arrLibrarySearch
import org.phioster.sanctumd.net.arrLookup
import org.phioster.sanctumd.net.arrMetadataProfiles
import org.phioster.sanctumd.net.arrProfiles
import org.phioster.sanctumd.net.arrRootFolders
import org.phioster.sanctumd.net.arrMissing
import org.phioster.sanctumd.net.arrQueue
import org.phioster.sanctumd.net.arrQueueRemove
import org.phioster.sanctumd.net.arrSearchItem
import org.phioster.sanctumd.net.clearJellyfinSession
import org.phioster.sanctumd.net.fetchStatus
import org.phioster.sanctumd.net.nzbgetHistory
import org.phioster.sanctumd.net.nzbgetQueue
import org.phioster.sanctumd.net.nzbServerDetails
import org.phioster.sanctumd.net.runNzbAppendUrl
import org.phioster.sanctumd.net.runNzbEditQueue
import org.phioster.sanctumd.net.runNzbRate
import org.phioster.sanctumd.net.seerrApprove
import org.phioster.sanctumd.net.seerrDecline
import org.phioster.sanctumd.net.seerrAddComment
import org.phioster.sanctumd.net.seerrDeleteIssueById
import org.phioster.sanctumd.net.seerrDiscover
import org.phioster.sanctumd.net.seerrIssueDetail
import org.phioster.sanctumd.net.seerrMediaDetail
import org.phioster.sanctumd.net.seerrRequestStats
import org.phioster.sanctumd.net.seerrUsers
import org.phioster.sanctumd.net.seerrIssues
import org.phioster.sanctumd.net.seerrRequest
import org.phioster.sanctumd.net.seerrRequests
import org.phioster.sanctumd.net.seerrSearch
import org.phioster.sanctumd.net.seerrSeasons
import org.phioster.sanctumd.net.seerrSetIssueStatus
import org.phioster.sanctumd.net.jellyfinActivity
import org.phioster.sanctumd.net.jellyfinCreateUser
import org.phioster.sanctumd.net.jellyfinDevices
import org.phioster.sanctumd.net.jellyfinDeleteUser
import org.phioster.sanctumd.net.jellyfinItemDetail
import org.phioster.sanctumd.net.jellyfinItems
import org.phioster.sanctumd.net.jellyfinLatest
import org.phioster.sanctumd.net.jellyfinAddLibrary
import org.phioster.sanctumd.net.jellyfinAddLibraryPath
import org.phioster.sanctumd.net.jellyfinAddTuner
import org.phioster.sanctumd.net.jellyfinAddXmltvProvider
import org.phioster.sanctumd.net.jellyfinChannels
import org.phioster.sanctumd.net.jellyfinDeleteProvider
import org.phioster.sanctumd.net.jellyfinDeleteTuner
import org.phioster.sanctumd.net.jellyfinLiveTv
import org.phioster.sanctumd.net.jellyfinDeleteLibrary
import org.phioster.sanctumd.net.jellyfinInstallPackage
import org.phioster.sanctumd.net.jellyfinLibraries
import org.phioster.sanctumd.net.jellyfinLibraryViews
import org.phioster.sanctumd.net.jellyfinLogContent
import org.phioster.sanctumd.net.jellyfinLogFiles
import org.phioster.sanctumd.net.jellyfinPackages
import org.phioster.sanctumd.net.jellyfinPlugins
import org.phioster.sanctumd.net.jellyfinRemoveLibraryPath
import org.phioster.sanctumd.net.jellyfinRenameLibrary
import org.phioster.sanctumd.net.jellyfinSetPluginEnabled
import org.phioster.sanctumd.net.jellyfinUninstallPlugin
import org.phioster.sanctumd.net.jellyfinResume
import org.phioster.sanctumd.net.jellyfinScanItem
import org.phioster.sanctumd.net.jellyfinSetPassword
import org.phioster.sanctumd.net.jellyfinSetPolicy
import org.phioster.sanctumd.net.jellyfinPlayCommand
import org.phioster.sanctumd.net.jellyfinRestart
import org.phioster.sanctumd.net.jellyfinRunTask
import org.phioster.sanctumd.net.jellyfinSendMessage
import org.phioster.sanctumd.net.jellyfinSessions
import org.phioster.sanctumd.net.jellyfinSystemInfo
import org.phioster.sanctumd.net.jellyfinTasks
import org.phioster.sanctumd.net.jellyfinUsers
import org.phioster.sanctumd.net.runJellyfinScan
import org.phioster.sanctumd.net.runNzbgetPause
import org.phioster.sanctumd.net.runNzbgetResume
import org.phioster.sanctumd.net.arrPushRelease
import org.phioster.sanctumd.net.prowlarrCategories
import org.phioster.sanctumd.net.prowlarrGrab
import org.phioster.sanctumd.net.prowlarrHistory
import org.phioster.sanctumd.net.prowlarrIndexers
import org.phioster.sanctumd.net.prowlarrSearch
import org.phioster.sanctumd.net.prowlarrSystem
import org.phioster.sanctumd.net.prowlarrTasks
import org.phioster.sanctumd.net.prowlarrTestIndexer
import org.phioster.sanctumd.net.prowlarrDeleteIndexer
import org.phioster.sanctumd.net.prowlarrToggleIndexer
import org.phioster.sanctumd.net.runProwlarrTestAll
import org.phioster.sanctumd.net.runSearchMissing

/** Where a launcher shortcut wants to navigate. [serviceId] is set for kind "service". */
data class PendingRoute(val kind: String, val serviceId: String? = null, val itemId: String? = null)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    // Home navigation state lives here (not in composables) so it survives when HomeShell
    // leaves composition — otherwise back from a service always lands on the first tab.
    val homeTab = androidx.compose.runtime.mutableIntStateOf(0)

    /** Bumped by pull-to-refresh on a dashboard tab; dashboard cards key their data
     *  load on it, so incrementing forces every card to reload. */
    val dashRefreshTick = androidx.compose.runtime.mutableIntStateOf(0)

    // Per-card data cache so switching dashboard tabs doesn't refetch. Keyed by "cardId#field";
    // invalidated per card via cardDataTick (only pull-to-refresh bumps dashRefreshTick).
    val cardDataCache = HashMap<String, Any?>()
    val cardDataTick = HashMap<String, Int>()

    /** A pending navigation from a launcher shortcut, consumed once by the UI. */
    private val _pendingRoute = MutableStateFlow<PendingRoute?>(null)
    val pendingRoute: StateFlow<PendingRoute?> = _pendingRoute.asStateFlow()
    fun setRoute(route: PendingRoute?) { _pendingRoute.value = route }
    fun consumeRoute() { _pendingRoute.value = null }

    private val bundleJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Serialize services + notify settings + dashboard layout and encrypt them with
     *  [password] into a portable file (see [org.phioster.sanctumd.security.PortableCrypto]). */
    suspend fun exportConfig(password: String): ByteArray {
        val bundle = org.phioster.sanctumd.model.ConfigBundle(
            services = _services.value,
            notify = notifyStore.currentSettings(),
            tabs = _tabs.value,
        )
        val plain = bundleJson.encodeToString(org.phioster.sanctumd.model.ConfigBundle.serializer(), bundle)
        return org.phioster.sanctumd.security.PortableCrypto.encrypt(plain, password)
    }

    /** Decrypt + apply an exported bundle, replacing the current config. Returns the
     *  number of services imported, or a failure (wrong password / bad file). */
    suspend fun importConfig(data: ByteArray, password: String): Result<Int> = runCatching {
        val plain = org.phioster.sanctumd.security.PortableCrypto.decrypt(data, password)
        val bundle = bundleJson.decodeFromString(org.phioster.sanctumd.model.ConfigBundle.serializer(), plain)
        store.save(bundle.services)
        notifyStore.save(bundle.notify)
        dashStore.save(bundle.tabs)
        val ctx = getApplication<Application>()
        if (bundle.notify.enabled) org.phioster.sanctumd.notify.Notifications.schedule(ctx, bundle.notify.intervalMin)
        else org.phioster.sanctumd.notify.Notifications.cancel(ctx)
        org.phioster.sanctumd.notify.NtfyStreamService.restart(ctx)
        bundle.services.size
    }

    /** Set when navigating away from inside the Services drawer; HomeShell reopens it once on return. */
    var reopenDrawer: Boolean = false

    /** True until the "open on the services list" preference has been honoured for this launch. */
    var startScreenPending: Boolean = true

    /** App-lock session state: survives rotation (VM outlives the activity), reset on process death. */
    val unlocked = androidx.compose.runtime.mutableStateOf(false)

    private val store = ServiceStore(app)
    private val dashStore = org.phioster.sanctumd.data.DashboardStore(app)
    private val notifyStore = org.phioster.sanctumd.data.NotifyStore(app)
    private val downloadStore = org.phioster.sanctumd.data.DownloadStore(app)
    private val statsHistoryStore = org.phioster.sanctumd.data.StatsHistoryStore(app)

    /** Live offline-download registry (itemId -> entry) for the downloads UI + detail button state. */
    val downloads: kotlinx.coroutines.flow.Flow<Map<String, org.phioster.sanctumd.model.DownloadEntry>> = downloadStore.downloads

    val notifySettings: StateFlow<org.phioster.sanctumd.model.NotifySettings> =
        notifyStore.settings.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, org.phioster.sanctumd.model.NotifySettings())

    /** Persists notification prefs; (re)schedules the poller and starts/stops the live-push service. */
    fun saveNotifySettings(s: org.phioster.sanctumd.model.NotifySettings) {
        viewModelScope.launch {
            notifyStore.save(s)
            val ctx = getApplication<Application>()
            if (s.enabled) org.phioster.sanctumd.notify.Notifications.schedule(ctx, s.intervalMin)
            else org.phioster.sanctumd.notify.Notifications.cancel(ctx)
            // Restart so the stream re-evaluates all subscriptions (settings topic +
            // NTFY-service topics); it stops itself when none remain.
            org.phioster.sanctumd.notify.NtfyStreamService.restart(ctx)
        }
    }

    private val _services = MutableStateFlow<List<ServiceConfig>>(emptyList())
    val services: StateFlow<List<ServiceConfig>> = _services.asStateFlow()

    // Becomes true after the first load from the store, so the UI can tell "empty" from "not loaded yet".
    private val _servicesLoaded = MutableStateFlow(false)
    val servicesLoaded: StateFlow<Boolean> = _servicesLoaded.asStateFlow()

    /** True after a cross-device restore: the encrypted services blob exists but its Keystore key doesn't. */
    val servicesUnreadable: StateFlow<Boolean> = store.decryptFailed

    /** Drop the unreadable blob so the user can re-add their services. */
    fun clearUnreadableServices() = viewModelScope.launch { store.clearUnreadable() }

    val appLock: StateFlow<Boolean> =
        dashStore.appLock.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun setAppLock(enabled: Boolean) = viewModelScope.launch { dashStore.setAppLock(enabled) }

    /** Safe mode. Mirrored into [org.phioster.sanctumd.net.SafeMode] because the guard sits
     *  in plain suspend functions that have no access to a store or a scope. */
    val safeMode: StateFlow<Boolean> =
        dashStore.safeMode.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            dashStore.safeMode.collect { org.phioster.sanctumd.net.SafeMode.enabled = it }
        }
    }

    fun setSafeMode(enabled: Boolean) = viewModelScope.launch {
        org.phioster.sanctumd.net.SafeMode.enabled = enabled // takes effect before the write lands
        dashStore.setSafeMode(enabled)
    }

    /** null until loaded; then true once the first-run onboarding is completed/dismissed. */
    val onboardingDone: StateFlow<Boolean?> =
        dashStore.onboardingDone.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { dashStore.setOnboardingDone(done) }

    /** When true, adult / XXX content is filtered out of Jellyfin browsing and Seerr discovery. */
    val hideAdult: StateFlow<Boolean> =
        dashStore.hideAdult.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun setHideAdult(enabled: Boolean) = viewModelScope.launch { dashStore.setHideAdult(enabled) }

    /** Dashboard gestures: tab-swipe (upper area) + drawer-open swipe (bottom band, fraction ≤ 0.5). */
    val swipeTabs: StateFlow<Boolean> =
        dashStore.swipeTabs.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)
    val swipeDrawer: StateFlow<Boolean> =
        dashStore.swipeDrawer.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)
    val drawerBand: StateFlow<Float> =
        dashStore.drawerBand.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0.4f)
    val downloadsWifiOnly: StateFlow<Boolean> =
        dashStore.downloadsWifiOnly.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    val playerSwipeMagnitude: StateFlow<Float> =
        dashStore.playerSwipeMagnitude.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 1.5f)
    val playerSwipeMargin: StateFlow<Float> =
        dashStore.playerSwipeMargin.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0f)
    fun setPlayerSwipeMagnitude(v: Float) = viewModelScope.launch { dashStore.setPlayerSwipeMagnitude(v) }
    fun setPlayerSwipeMargin(v: Float) = viewModelScope.launch { dashStore.setPlayerSwipeMargin(v) }

    /** Playback preferences (track languages, subtitle look, autoplay, segment skipping, resume). */
    val audioLanguage: StateFlow<String> =
        dashStore.audioLanguage.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "de")
    val subtitleLanguage: StateFlow<String> =
        dashStore.subtitleLanguage.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "de")
    val subtitleMode: StateFlow<String> =
        dashStore.subtitleMode.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "forced")
    val subtitleScale: StateFlow<Float> =
        dashStore.subtitleScale.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 1f)
    val autoplayNext: StateFlow<Boolean> =
        dashStore.autoplayNext.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)
    val autoSkipSegments: StateFlow<Boolean> =
        dashStore.autoSkipSegments.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    val askResume: StateFlow<Boolean> =
        dashStore.askResume.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)
    fun setAudioLanguage(v: String) = viewModelScope.launch { dashStore.setAudioLanguage(v) }
    fun setSubtitleLanguage(v: String) = viewModelScope.launch { dashStore.setSubtitleLanguage(v) }
    fun setSubtitleMode(v: String) = viewModelScope.launch { dashStore.setSubtitleMode(v) }
    fun setSubtitleScale(v: Float) = viewModelScope.launch { dashStore.setSubtitleScale(v) }
    fun setAutoplayNext(v: Boolean) = viewModelScope.launch { dashStore.setAutoplayNext(v) }
    fun setAutoSkipSegments(v: Boolean) = viewModelScope.launch { dashStore.setAutoSkipSegments(v) }
    fun setAskResume(v: Boolean) = viewModelScope.launch { dashStore.setAskResume(v) }
    val nextEpisodeLead: StateFlow<Int> =
        dashStore.nextEpisodeLead.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 45)
    fun setNextEpisodeLead(v: Int) = viewModelScope.launch { dashStore.setNextEpisodeLead(v) }

    /** Intro/outro segments for an item (native MediaSegments, else the Intro Skipper plugin). */
    suspend fun jellyfinSegments(config: ServiceConfig, itemId: String) =
        org.phioster.sanctumd.net.jellyfinMediaSegments(config, itemId)
    /** The episode that follows [itemId] in its series, or null. */
    suspend fun jellyfinNextEpisode(config: ServiceConfig, itemId: String) =
        org.phioster.sanctumd.net.jellyfinNextEpisode(config, itemId)
    fun setDownloadsWifiOnly(enabled: Boolean) = viewModelScope.launch { dashStore.setDownloadsWifiOnly(enabled) }
    val downloadsDeleteWatched: StateFlow<Boolean> =
        dashStore.downloadsDeleteWatched.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    fun setDownloadsDeleteWatched(enabled: Boolean) = viewModelScope.launch { dashStore.setDownloadsDeleteWatched(enabled) }
    val hiddenLibraries: StateFlow<Map<String, List<String>>> =
        dashStore.hiddenLibraries.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())
    fun setHiddenLibraries(serviceId: String, hidden: List<String>) = viewModelScope.launch { dashStore.setHiddenLibraries(serviceId, hidden) }

    val mediaRowStyles: StateFlow<Map<String, org.phioster.sanctumd.model.MediaRowStyle>> =
        dashStore.mediaRowStyles.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyMap())
    fun setMediaRowStyle(rowKey: String, style: org.phioster.sanctumd.model.MediaRowStyle) = viewModelScope.launch { dashStore.setMediaRowStyle(rowKey, style) }

    fun setSwipeTabs(enabled: Boolean) = viewModelScope.launch { dashStore.setSwipeTabs(enabled) }
    fun setSwipeDrawer(enabled: Boolean) = viewModelScope.launch { dashStore.setSwipeDrawer(enabled) }
    fun setDrawerBand(fraction: Float) = viewModelScope.launch { dashStore.setDrawerBand(fraction) }

    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val _tabs = MutableStateFlow<List<org.phioster.sanctumd.model.DashTab>>(emptyList())
    val tabs: StateFlow<List<org.phioster.sanctumd.model.DashTab>> = _tabs.asStateFlow()

    init {
        viewModelScope.launch {
            store.services.collect { list ->
                _services.value = list
                _servicesLoaded.value = true
                refreshAll()
            }
        }
        viewModelScope.launch {
            dashStore.tabs.collect { list ->
                _tabs.value = if (list.isEmpty()) listOf(defaultHomeTab()) else list
            }
        }
    }

    private fun defaultHomeTab() = org.phioster.sanctumd.model.DashTab(
        id = java.util.UUID.randomUUID().toString(),
        name = "Home",
        cards = emptyList(),
    )

    private fun persistTabs(list: List<org.phioster.sanctumd.model.DashTab>) {
        _tabs.value = list
        viewModelScope.launch { dashStore.save(list) }
    }

    fun addTab(name: String, icon: String = "", accent: Long = 0) {
        persistTabs(_tabs.value + org.phioster.sanctumd.model.DashTab(java.util.UUID.randomUUID().toString(), name.ifBlank { "Tab" }, icon = icon, accent = accent))
    }

    fun setTabIcon(tabId: String, icon: String) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(icon = icon) else it })
    }

    fun setTabAccent(tabId: String, accent: Long) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(accent = accent) else it })
    }

    fun moveTab(tabId: String, direction: Int): Int {
        val (moved, target) = _tabs.value.movedTab(tabId, direction)
            ?: return _tabs.value.indexOfFirst { it.id == tabId }
        persistTabs(moved)
        return target
    }

    fun removeTab(tabId: String) {
        val list = _tabs.value.filterNot { it.id == tabId }
        persistTabs(list.ifEmpty { listOf(defaultHomeTab()) })
    }

    fun renameTab(tabId: String, name: String) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(name = name.ifBlank { it.name }) else it })
    }

    fun addCard(tabId: String, type: org.phioster.sanctumd.model.CardType, serviceId: String) {
        val card = org.phioster.sanctumd.model.DashCard(java.util.UUID.randomUUID().toString(), type, serviceId)
        persistTabs(_tabs.value.withCardAdded(tabId, card))
    }

    fun removeCard(tabId: String, cardId: String) {
        forgetCard(cardId)
        persistTabs(_tabs.value.withCardRemoved(tabId, cardId))
    }

    /** Drop a card's cached data + tick so a removed card doesn't linger in the cache. */
    private fun forgetCard(cardId: String) {
        cardDataCache.keys.removeAll { it.startsWith("$cardId#") }
        cardDataTick.remove(cardId)
    }

    fun updateCard(tabId: String, cardId: String, title: String, count: Int, accent: Long, icon: String, posterSize: String, background: Boolean, theme: String, density: String) {
        persistTabs(_tabs.value.withCardUpdated(tabId, cardId, title, count, accent, icon, posterSize, background, theme, density))
    }

    fun moveCard(tabId: String, cardId: String, direction: Int) {
        persistTabs(_tabs.value.movedCard(tabId, cardId, direction))
    }

    fun refreshAll() {
        _services.value.forEach { config ->
            viewModelScope.launch {
                setStatus(config.id, ServiceStatus.Loading)
                setStatus(config.id, fetchStatus(config))
            }
        }
    }

    /** Like [refreshAll] but suspends until every service status has reloaded, so a
     *  pull-to-refresh spinner can stay up until the work is actually done. */
    suspend fun refreshAllSuspend() = kotlinx.coroutines.coroutineScope {
        _services.value.forEach { config ->
            launch {
                setStatus(config.id, ServiceStatus.Loading)
                setStatus(config.id, fetchStatus(config))
            }
        }
    }

    /** Adds a new service or replaces an existing one with the same id. */
    fun upsertService(config: ServiceConfig) {
        clearJellyfinSession(config.id)
        viewModelScope.launch {
            val list = _services.value
            val idx = list.indexOfFirst { it.id == config.id }
            val updated = if (idx >= 0) list.toMutableList().apply { this[idx] = config } else list + config
            store.save(updated)
            // The ntfy stream snapshots its subscriptions on start — re-read them.
            if (config.type == org.phioster.sanctumd.model.ServiceType.NTFY) {
                org.phioster.sanctumd.notify.NtfyStreamService.restart(getApplication())
            }
        }
    }

    fun removeService(id: String) {
        viewModelScope.launch {
            val wasNtfy = _services.value.firstOrNull { it.id == id }?.type == org.phioster.sanctumd.model.ServiceType.NTFY
            store.save(_services.value.filterNot { it.id == id })
            if (wasNtfy) org.phioster.sanctumd.notify.NtfyStreamService.restart(getApplication())
        }
    }

    suspend fun ntfyMessages(config: ServiceConfig, topic: String): List<org.phioster.sanctumd.model.NtfyMessage> =
        org.phioster.sanctumd.net.ntfyHistory(config, topic)

    suspend fun runShortcut(config: ServiceConfig, sc: org.phioster.sanctumd.model.HttpShortcut): String =
        org.phioster.sanctumd.net.runHttpShortcut(config, sc)

    /** Reorder a service card. [direction] = -1 to move up, +1 to move down. */
    fun moveService(id: String, direction: Int) {
        viewModelScope.launch {
            val list = _services.value.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            val target = idx + direction
            if (idx < 0 || target < 0 || target >= list.size) return@launch
            list[idx] = list[target].also { list[target] = list[idx] }
            store.save(list)
        }
    }

    /** Move [id] so it lands directly where [targetId] sits — the primitive behind drag & drop.
     *  Reordering works on the global list, so a drag inside a group leaves other groups alone. */
    fun moveServiceTo(id: String, targetId: String) {
        viewModelScope.launch {
            val list = _services.value.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = list.indexOfFirst { it.id == targetId }
            if (from < 0 || to < 0 || from == to) return@launch
            list.add(to, list.removeAt(from))
            store.save(list)
        }
    }

    /** Assign a service to a group ("" = ungrouped). */
    fun setServiceGroup(id: String, group: String) {
        viewModelScope.launch {
            store.save(_services.value.map { if (it.id == id) it.copy(group = group.trim()) else it })
        }
    }

    /** Pin/unpin a service to the top of the list. */
    fun setServicePinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            store.save(_services.value.map { if (it.id == id) it.copy(pinned = pinned) else it })
        }
    }

    /** Services-list presentation + which surface the app opens on. */
    val serviceViewMode: StateFlow<String> =
        dashStore.serviceViewMode.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "cards")
    fun setServiceViewMode(v: String) = viewModelScope.launch { dashStore.setServiceViewMode(v) }
    val collapsedGroups: StateFlow<Set<String>> =
        dashStore.collapsedGroups.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())
    fun toggleGroupCollapsed(group: String) = viewModelScope.launch {
        val cur = collapsedGroups.value
        dashStore.setCollapsedGroups(if (group in cur) cur - group else cur + group)
    }
    val startScreen: StateFlow<String> =
        dashStore.startScreen.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "dashboard")
    fun setStartScreen(v: String) = viewModelScope.launch { dashStore.setStartScreen(v) }

    /** One-off connection test used by the Add-service screen. */
    suspend fun test(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    /** Live status (key stat numbers) for a Statistics card. */
    suspend fun serviceStats(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    /** Watch-time leaderboard (needs the Jellyfin Playback Reporting plugin). */
    suspend fun jellyfinTopWatchers(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyWatchStat> =
        org.phioster.sanctumd.net.jellyfinTopWatchers(config)

    /** Triggers a Jellyfin library scan; returns a result line for the UI. */
    suspend fun jellyfinScan(config: ServiceConfig): String = runJellyfinScan(config)
    suspend fun jellyfinSessionList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellySession> = jellyfinSessions(config)
    suspend fun jellyfinUserList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyUser> = jellyfinUsers(config)
    suspend fun jellyfinControl(config: ServiceConfig, sessionId: String, cmd: String): String =
        jellyfinPlayCommand(config, sessionId, cmd)
    suspend fun jellyfinMessage(config: ServiceConfig, sessionId: String, text: String): String =
        jellyfinSendMessage(config, sessionId, text)
    suspend fun jellyfinInfo(config: ServiceConfig): org.phioster.sanctumd.model.JellySystemInfo = jellyfinSystemInfo(config)
    suspend fun jellyfinTaskList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyTask> = jellyfinTasks(config)
    suspend fun jellyfinRunTaskById(config: ServiceConfig, taskId: String): String = jellyfinRunTask(config, taskId)
    suspend fun jellyfinActivityLog(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyActivity> = jellyfinActivity(config)
    suspend fun jellyfinRestartServer(config: ServiceConfig): String = jellyfinRestart(config)
    suspend fun jellyfinDeviceList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyDevice> =
        jellyfinDevices(config)
    suspend fun jellyfinLibraryList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyLibrary> =
        jellyfinLibraries(config)
    suspend fun jellyfinAddUser(config: ServiceConfig, name: String, password: String): String =
        jellyfinCreateUser(config, name, password)
    suspend fun jellyfinRemoveUser(config: ServiceConfig, userId: String): String =
        jellyfinDeleteUser(config, userId)
    suspend fun jellyfinUpdatePolicy(
        config: ServiceConfig,
        userId: String,
        admin: Boolean,
        disabled: Boolean,
        allowDownloads: Boolean,
        enableAllFolders: Boolean,
        enabledFolders: List<String>,
    ): String = jellyfinSetPolicy(config, userId, admin, disabled, allowDownloads, enableAllFolders, enabledFolders)
    suspend fun jellyfinResetPassword(config: ServiceConfig, userId: String, newPassword: String): String =
        jellyfinSetPassword(config, userId, newPassword)
    suspend fun jellyfinViews(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyMediaItem> =
        jellyfinLibraryViews(config)
    // Drop adult / XXX items when the 18+ filter is on.
    private fun noAdult(list: List<org.phioster.sanctumd.model.JellyMediaItem>) =
        if (hideAdult.value) list.filterNot { it.adult } else list
    suspend fun jellyfinContinue(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyMediaItem> =
        noAdult(jellyfinResume(config))
    suspend fun jellyfinRecent(config: ServiceConfig, parentId: String? = null): List<org.phioster.sanctumd.model.JellyMediaItem> =
        noAdult(jellyfinLatest(config, parentId))
    suspend fun jellyfinItemList(
        config: ServiceConfig,
        parentId: String,
        seasonNumber: Int? = null,
        sortBy: String = "IsFolder,SortName",
        descending: Boolean = false,
        unwatchedOnly: Boolean = false,
    ): List<org.phioster.sanctumd.model.JellyMediaItem> =
        noAdult(jellyfinItems(config, parentId, seasonNumber, sortBy, descending, unwatchedOnly))
    suspend fun jellyfinFavoriteList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyMediaItem> =
        noAdult(org.phioster.sanctumd.net.jellyfinFavorites(config))
    suspend fun jellyfinSetFavorite(config: ServiceConfig, itemId: String, favorite: Boolean) =
        org.phioster.sanctumd.net.jellyfinSetFavorite(config, itemId, favorite)
    /** Which of these items the user has already watched (used to clear finished downloads). */
    suspend fun jellyfinPlayedIds(config: ServiceConfig, ids: List<String>): Set<String> =
        org.phioster.sanctumd.net.jellyfinPlayedIds(config, ids)
    /** Hand an item to another Jellyfin client to play. */
    suspend fun jellyfinPlayOn(config: ServiceConfig, sessionId: String, itemId: String): String =
        org.phioster.sanctumd.net.jellyfinPlayOnSession(config, sessionId, itemId)
    suspend fun jellyfinMediaDetail(config: ServiceConfig, itemId: String): org.phioster.sanctumd.model.JellyMediaDetail =
        jellyfinItemDetail(config, itemId)
    // ---- Playback (streaming + progress reporting) ----
    suspend fun jellyfinPlaybackSource(config: ServiceConfig, itemId: String, maxBitrate: Int? = null): org.phioster.sanctumd.net.PlaybackSource =
        org.phioster.sanctumd.net.jellyfinPlaybackSource(config, itemId, maxBitrate)
    suspend fun jellyfinAlbumTracks(config: ServiceConfig, albumId: String, albumName: String): List<org.phioster.sanctumd.net.MusicTrack> =
        org.phioster.sanctumd.net.jellyfinAlbumTracks(config, albumId, albumName)
    suspend fun jellyfinTrack(config: ServiceConfig, itemId: String): org.phioster.sanctumd.net.MusicTrack =
        org.phioster.sanctumd.net.jellyfinTrack(config, itemId)

    // ---- Stats screen: aggregate numbers + bar charts across every configured service ----
    suspend fun loadStats(): org.phioster.sanctumd.model.StatsData = kotlinx.coroutines.coroutineScope {
        val svcs = services.value
        // Every service fetches concurrently; each statsForService also parallelizes its own calls.
        val parts = svcs
            .map { svc -> async(kotlinx.coroutines.Dispatchers.IO) { runCatching { statsForService(svc) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
        // Opportunistic once/day snapshot, built from the metrics we already fetched — no second
        // network pass — so a trend point exists even before the daily worker fires.
        val today = java.time.LocalDate.now().toEpochDay()
        val metrics = parts.fold(emptyMap<String, Long>()) { acc, p -> acc + p.third }
        if (metrics.isNotEmpty() && statsHistoryStore.read().none { it.epochDay == today }) {
            runCatching { statsHistoryStore.append(org.phioster.sanctumd.data.StatsSnapshot(today, metrics)) }
        }
        val (trendTiles, trends) = org.phioster.sanctumd.ui.stats.buildTrends(svcs, statsHistoryStore.read())
        org.phioster.sanctumd.model.StatsData(
            tiles = parts.flatMap { it.first },
            charts = parts.flatMap { it.second },
            trendTiles = trendTiles,
            trends = trends,
        )
    }

    private fun statBar(label: String, value: Int) =
        org.phioster.sanctumd.model.StatBar(label, value.toFloat(), value.toString())
    private fun freeGb(bytes: Long) = "%.0f GB".format(bytes / 1_000_000_000.0)
    private fun gbFromMb(mb: Long) = if (mb >= 1_000_000L) "%.1f TB".format(mb / 1_000_000.0) else "%.0f GB".format(mb / 1_000.0)

    private suspend fun statsForService(
        svc: ServiceConfig,
    ): Triple<List<org.phioster.sanctumd.model.StatTile>, List<org.phioster.sanctumd.model.StatChart>, Map<String, Long>> = kotlinx.coroutines.coroutineScope {
        val accent = svc.type.accent
        val id = svc.id
        val tiles = mutableListOf<org.phioster.sanctumd.model.StatTile>()
        val charts = mutableListOf<org.phioster.sanctumd.model.StatChart>()
        val metrics = mutableMapOf<String, Long>()
        val io = kotlinx.coroutines.Dispatchers.IO
        when (svc.type) {
            ServiceType.JELLYFIN -> {
                // Independent calls fire in parallel, then we await.
                val countsD = async(io) { org.phioster.sanctumd.net.jellyfinCounts(svc) }
                val usersD = async(io) { runCatching { org.phioster.sanctumd.net.jellyfinUsers(svc).size }.getOrNull() }
                val libsD = async(io) { runCatching { org.phioster.sanctumd.net.jellyfinLibraries(svc).size }.getOrNull() }
                val playingD = async(io) { runCatching { org.phioster.sanctumd.net.jellyfinSessions(svc).count { s -> s.nowPlaying.isNotEmpty() } }.getOrNull() }
                val c = countsD.await()
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · movies", c.movies.toString(), accent)
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · episodes", c.episodes.toString(), accent)
                usersD.await()?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · users", it.toString(), accent) }
                libsD.await()?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · libraries", it.toString(), accent) }
                playingD.await()?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · now playing", it.toString(), accent) }
                charts += org.phioster.sanctumd.model.StatChart(
                    "${svc.label} · library",
                    listOf(statBar("Movies", c.movies), statBar("Series", c.series), statBar("Episodes", c.episodes), statBar("Songs", c.songs)),
                    accent,
                )
                metrics["$id|movies"] = c.movies.toLong()
                metrics["$id|series"] = c.series.toLong()
                metrics["$id|episodes"] = c.episodes.toLong()
                metrics["$id|songs"] = c.songs.toLong()
            }
            ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> {
                val libD = async(io) { runCatching { org.phioster.sanctumd.net.arrLibrary(svc) }.getOrDefault(emptyList()) }
                val missingD = async(io) { runCatching { org.phioster.sanctumd.net.arrMissing(svc).size }.getOrDefault(0) }
                val queueD = async(io) { runCatching { org.phioster.sanctumd.net.arrQueue(svc).size }.getOrNull() }
                val healthD = async(io) { runCatching { org.phioster.sanctumd.net.arrHealthCount(svc) }.getOrNull() }
                val disksD = async(io) { runCatching { org.phioster.sanctumd.net.arrDiskSpace(svc) }.getOrDefault(emptyList()) }
                val now = java.time.Instant.now()
                val upcomingD = async(io) { runCatching { org.phioster.sanctumd.net.arrCalendarRange(svc, now, now.plus(java.time.Duration.ofDays(7))) }.getOrDefault(emptyList()) }
                val lib = libD.await()
                val missing = missingD.await()
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · items", lib.size.toString(), accent)
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · missing", missing.toString(), accent)
                val storageMb = lib.sumOf { it.sizeMb }
                if (storageMb > 0) tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · storage", gbFromMb(storageMb), accent)
                queueD.await()?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · queue", it.toString(), accent) }
                healthD.await()?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · health", it.toString(), accent) }
                val disks = disksD.await()
                if (disks.isNotEmpty()) {
                    charts += org.phioster.sanctumd.model.StatChart(
                        "${svc.label} · disk used",
                        disks.map { d ->
                            val usedFrac = if (d.totalBytes > 0) (d.totalBytes - d.freeBytes).toFloat() / d.totalBytes else 0f
                            org.phioster.sanctumd.model.StatBar(
                                d.path.takeLast(22), usedFrac * 100f,
                                "${(usedFrac * 100).toInt()}% · ${freeGb(d.freeBytes)} free",
                            )
                        },
                        accent,
                    )
                }
                val largest = lib.filter { it.sizeMb > 0 }.sortedByDescending { it.sizeMb }.take(8)
                if (largest.isNotEmpty()) {
                    charts += org.phioster.sanctumd.model.StatChart(
                        "${svc.label} · largest titles",
                        largest.map { org.phioster.sanctumd.model.StatBar(it.title, it.sizeMb.toFloat(), gbFromMb(it.sizeMb)) },
                        accent,
                    )
                }
                val upcoming = upcomingD.await()
                if (upcoming.isNotEmpty()) {
                    val byDay = upcoming.groupingBy { it.date }.eachCount().toSortedMap()
                    charts += org.phioster.sanctumd.model.StatChart(
                        "${svc.label} · upcoming 7d",
                        byDay.map { (day, n) -> statBar(day.takeLast(5), n) },
                        accent,
                    )
                }
                metrics["$id|items"] = lib.size.toLong()
                metrics["$id|missing"] = missing.toLong()
                if (storageMb > 0) metrics["$id|storageMb"] = storageMb
                disks.forEach { d -> metrics["$id|diskfree:${d.path}"] = d.freeBytes }
            }
            ServiceType.PROWLARR -> {
                val stats = org.phioster.sanctumd.net.prowlarrIndexerStats(svc).sortedByDescending { it.third }
                val totalGrabs = stats.sumOf { it.third }
                val totalQueries = stats.sumOf { it.second }
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · grabs", totalGrabs.toString(), accent)
                tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · queries", totalQueries.toString(), accent)
                val grabBars = stats.take(12).filter { it.third > 0 }
                if (grabBars.isNotEmpty()) {
                    charts += org.phioster.sanctumd.model.StatChart("${svc.label} · grabs per indexer", grabBars.map { statBar(it.first, it.third) }, accent)
                }
                val queryBars = stats.sortedByDescending { it.second }.take(12).filter { it.second > 0 }
                if (queryBars.isNotEmpty()) {
                    charts += org.phioster.sanctumd.model.StatChart("${svc.label} · queries per indexer", queryBars.map { statBar(it.first, it.second) }, accent)
                }
                metrics["$id|grabs"] = totalGrabs.toLong()
                metrics["$id|queries"] = totalQueries.toLong()
            }
            ServiceType.SEERR -> {
                val rsD = async(io) { runCatching { org.phioster.sanctumd.net.seerrRequestStats(svc) }.getOrDefault(emptyList()) }
                val usersD = async(io) { runCatching { org.phioster.sanctumd.net.seerrUsers(svc) }.getOrDefault(emptyList()) }
                val rs = rsD.await()
                val bars = rs.mapNotNull { (k, v) -> v.toIntOrNull()?.let { org.phioster.sanctumd.model.StatBar(k, it.toFloat(), v) } }
                rs.firstOrNull { it.first.equals("pending", true) }?.let { tiles += org.phioster.sanctumd.model.StatTile("${svc.label} · pending", it.second, accent) }
                if (bars.any { it.value > 0 }) charts += org.phioster.sanctumd.model.StatChart("${svc.label} · requests", bars, accent)
                val users = usersD.await().filter { it.requestCount > 0 }.sortedByDescending { it.requestCount }.take(8)
                if (users.isNotEmpty()) {
                    charts += org.phioster.sanctumd.model.StatChart(
                        "${svc.label} · top requesters",
                        users.map { statBar(it.name, it.requestCount) },
                        accent,
                    )
                }
                rs.forEach { (k, v) -> v.toLongOrNull()?.let { metrics["$id|req_${k.lowercase()}"] = it } }
            }
            else -> {}
        }
        Triple(tiles, charts, metrics)
    }
    suspend fun jellyfinReportStart(config: ServiceConfig, src: org.phioster.sanctumd.net.PlaybackSource, positionMs: Long) =
        org.phioster.sanctumd.net.jellyfinReportStart(config, src, positionMs)
    suspend fun jellyfinReportProgress(config: ServiceConfig, src: org.phioster.sanctumd.net.PlaybackSource, positionMs: Long, isPaused: Boolean) =
        org.phioster.sanctumd.net.jellyfinReportProgress(config, src, positionMs, isPaused)
    suspend fun jellyfinReportStopped(config: ServiceConfig, src: org.phioster.sanctumd.net.PlaybackSource, positionMs: Long) =
        org.phioster.sanctumd.net.jellyfinReportStopped(config, src, positionMs)
    /** Fire-and-forget progress report — used when the app goes to the background, where a swipe-kill
     *  can follow immediately and the player's own 10s loop would never get another turn. */
    fun jellyfinReportProgressAsync(config: ServiceConfig, src: org.phioster.sanctumd.net.PlaybackSource, positionMs: Long, isPaused: Boolean) {
        viewModelScope.launch { runCatching { org.phioster.sanctumd.net.jellyfinReportProgress(config, src, positionMs, isPaused) } }
    }
    /** Fire-and-forget stop report — survives the player screen leaving composition. */
    fun jellyfinReportStoppedAsync(config: ServiceConfig, src: org.phioster.sanctumd.net.PlaybackSource, positionMs: Long) {
        viewModelScope.launch { runCatching { org.phioster.sanctumd.net.jellyfinReportStopped(config, src, positionMs) } }
    }
    /** Mark a media item watched/unwatched (folders cascade to their children server-side). */
    suspend fun jellyfinSetWatched(config: ServiceConfig, itemId: String, played: Boolean) =
        org.phioster.sanctumd.net.jellyfinSetPlayed(config, itemId, played)
    suspend fun jellyfinScanLibrary(config: ServiceConfig, itemId: String): String =
        jellyfinScanItem(config, itemId)
    suspend fun jellyfinLogList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyLogFile> =
        jellyfinLogFiles(config)
    suspend fun jellyfinLogText(config: ServiceConfig, name: String): String =
        jellyfinLogContent(config, name)
    suspend fun jellyfinCreateLibrary(config: ServiceConfig, name: String, collectionType: String, path: String): String =
        jellyfinAddLibrary(config, name, collectionType, path)
    suspend fun jellyfinRemoveLibrary(config: ServiceConfig, name: String): String =
        jellyfinDeleteLibrary(config, name)
    suspend fun jellyfinRenameLibraryTo(config: ServiceConfig, name: String, newName: String): String =
        jellyfinRenameLibrary(config, name, newName)
    suspend fun jellyfinLibraryAddPath(config: ServiceConfig, libraryName: String, path: String): String =
        jellyfinAddLibraryPath(config, libraryName, path)
    suspend fun jellyfinLibraryRemovePath(config: ServiceConfig, libraryName: String, path: String): String =
        jellyfinRemoveLibraryPath(config, libraryName, path)
    suspend fun jellyfinPluginList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyPlugin> =
        jellyfinPlugins(config)
    suspend fun jellyfinPluginEnable(config: ServiceConfig, id: String, version: String, enabled: Boolean): String =
        jellyfinSetPluginEnabled(config, id, version, enabled)
    suspend fun jellyfinPluginUninstall(config: ServiceConfig, id: String, version: String): String =
        jellyfinUninstallPlugin(config, id, version)
    suspend fun jellyfinCatalog(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyPackage> =
        jellyfinPackages(config)
    suspend fun jellyfinCatalogInstall(config: ServiceConfig, name: String, guid: String): String =
        jellyfinInstallPackage(config, name, guid)
    suspend fun jellyfinLiveTvStatus(config: ServiceConfig): org.phioster.sanctumd.model.JellyLiveTv =
        jellyfinLiveTv(config)
    suspend fun jellyfinChannelList(config: ServiceConfig): List<org.phioster.sanctumd.model.JellyChannel> =
        jellyfinChannels(config)
    suspend fun jellyfinTunerAdd(config: ServiceConfig, type: String, url: String): String =
        jellyfinAddTuner(config, type, url)
    suspend fun jellyfinTunerDelete(config: ServiceConfig, id: String): String =
        jellyfinDeleteTuner(config, id)
    suspend fun jellyfinProviderAdd(config: ServiceConfig, path: String): String =
        jellyfinAddXmltvProvider(config, path)
    suspend fun jellyfinProviderDelete(config: ServiceConfig, id: String): String =
        jellyfinDeleteProvider(config, id)

    /** Cross-service search: query every configured service in parallel, flatten the hits. */
    suspend fun globalSearch(term: String): List<org.phioster.sanctumd.model.SearchResult> =
        kotlinx.coroutines.coroutineScope {
            _services.value
                .map { cfg -> async { serviceSearch(cfg, term) } }
                .awaitAll()
                .flatten()
                .let { if (hideAdult.value) it.filterNot { r -> r.adult } else it }
        }

    suspend fun searchMissing(config: ServiceConfig): String = runSearchMissing(config)
    suspend fun prowlarrTestAll(config: ServiceConfig): String = runProwlarrTestAll(config)
    suspend fun prowlarrIndexerList(config: ServiceConfig): List<ProwlarrIndexerItem> = prowlarrIndexers(config)
    suspend fun prowlarrToggle(config: ServiceConfig, id: Int, enable: Boolean): String =
        prowlarrToggleIndexer(config, id, enable)
    suspend fun prowlarrDelete(config: ServiceConfig, id: Int): String =
        prowlarrDeleteIndexer(config, id)
    suspend fun prowlarrTest(config: ServiceConfig, id: Int): String = prowlarrTestIndexer(config, id)
    suspend fun prowlarrIndexerEditOf(config: ServiceConfig, id: Int): org.phioster.sanctumd.model.ProwlarrIndexerEdit =
        org.phioster.sanctumd.net.prowlarrIndexerEdit(config, id)
    suspend fun prowlarrSaveIndexerOf(config: ServiceConfig, id: Int, values: Map<String, String>): String =
        org.phioster.sanctumd.net.prowlarrSaveIndexer(config, id, values)
    suspend fun prowlarrIndexerSchemasOf(config: ServiceConfig): List<org.phioster.sanctumd.net.ProwlarrSchemaEntry> =
        org.phioster.sanctumd.net.prowlarrIndexerSchemas(config)
    suspend fun prowlarrAddIndexerOf(config: ServiceConfig, entry: org.phioster.sanctumd.net.ProwlarrSchemaEntry, name: String, values: Map<String, String>): String =
        org.phioster.sanctumd.net.prowlarrAddIndexer(config, entry, name, values)
    suspend fun prowlarrTestNewIndexerOf(config: ServiceConfig, entry: org.phioster.sanctumd.net.ProwlarrSchemaEntry, name: String, values: Map<String, String>): String =
        org.phioster.sanctumd.net.prowlarrTestNewIndexer(config, entry, name, values)
    suspend fun prowlarrSearchList(config: ServiceConfig, query: String, categoryId: Int): List<ProwlarrRelease> =
        prowlarrSearch(config, query, categoryId)
    suspend fun prowlarrGrabRelease(config: ServiceConfig, release: ProwlarrRelease): String =
        prowlarrGrab(config, release)
    fun prowlarrCategoryOptions(): List<ProwlarrCategory> = prowlarrCategories
    suspend fun prowlarrHistoryList(config: ServiceConfig): List<ProwlarrHistoryItem> = prowlarrHistory(config)
    suspend fun prowlarrTaskList(config: ServiceConfig): List<ProwlarrTaskItem> = prowlarrTasks(config)
    suspend fun prowlarrSystemInfo(config: ServiceConfig): ProwlarrSystemInfo = prowlarrSystem(config)
    suspend fun sendReleaseToArr(arrConfig: ServiceConfig, release: ProwlarrRelease): String =
        arrPushRelease(arrConfig, release)
    /** Configured Radarr/Sonarr services, for the "send to" menu. */
    suspend fun arrIndexersOf(config: ServiceConfig): List<org.phioster.sanctumd.model.ArrIndexerItem> =
        org.phioster.sanctumd.net.arrIndexers(config)
    suspend fun arrTestIndexers(config: ServiceConfig): String =
        org.phioster.sanctumd.net.arrTestAllIndexers(config)

    /** Every Servarr app that owns indexers — a lockout normally hits all of them at once. */
    fun indexerServices(): List<ServiceConfig> = _services.value.filter {
        it.type == ServiceType.RADARR || it.type == ServiceType.SONARR || it.type == ServiceType.LIDARR
    }
    suspend fun arrRepairAllIndexers(): List<Pair<String, String>> =
        org.phioster.sanctumd.net.arrRepairIndexers(indexerServices())

    fun arrTargets(): List<ServiceConfig> =
        _services.value.filter { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }
    suspend fun nzbgetPause(config: ServiceConfig): String = runNzbgetPause(config)
    suspend fun nzbgetResume(config: ServiceConfig): String = runNzbgetResume(config)

    suspend fun queue(config: ServiceConfig): List<NzbQueueItem> = nzbgetQueue(config)
    suspend fun history(config: ServiceConfig, hidden: Boolean): List<NzbHistoryEntry> = nzbgetHistory(config, hidden)
    suspend fun nzbEdit(config: ServiceConfig, command: String, id: Int, editText: String = ""): String =
        runNzbEditQueue(config, command, id, editText)
    suspend fun nzbRate(config: ServiceConfig, kbps: Int): String = runNzbRate(config, kbps)
    suspend fun nzbAddUrl(config: ServiceConfig, url: String, category: String): String =
        runNzbAppendUrl(config, url, category)
    suspend fun nzbServer(config: ServiceConfig): List<Pair<String, String>> = nzbServerDetails(config)

    suspend fun arrMissingList(config: ServiceConfig): List<ArrMissingItem> = arrMissing(config)
    suspend fun arrQueueList(config: ServiceConfig): List<ArrQueueItem> = arrQueue(config)
    suspend fun arrSearch(config: ServiceConfig, id: Int): String = arrSearchItem(config, id)
    suspend fun arrRemove(config: ServiceConfig, id: Int): String = arrQueueRemove(config, id)
    suspend fun arrLibraryList(config: ServiceConfig): List<ArrLibraryItem> = arrLibrary(config)
    suspend fun arrLibSearch(config: ServiceConfig, id: Int): String = arrLibrarySearch(config, id)
    suspend fun arrLookupList(config: ServiceConfig, term: String): List<ArrLookupItem> = arrLookup(config, term)
    suspend fun arrProfilesList(config: ServiceConfig): List<ArrProfile> = arrProfiles(config)
    suspend fun arrRootFoldersList(config: ServiceConfig): List<String> = arrRootFolders(config)
    suspend fun arrMetaProfilesList(config: ServiceConfig): List<ArrProfile> = arrMetadataProfiles(config)
    suspend fun arrAddItem(config: ServiceConfig, raw: String, qualityProfileId: Int, rootFolderPath: String, monitored: Boolean, metadataProfileId: Int = 0): String =
        arrAdd(config, raw, qualityProfileId, rootFolderPath, monitored, metadataProfileId)
    suspend fun arrCutoffList(config: ServiceConfig): List<ArrMissingItem> = arrCutoff(config)
    suspend fun arrDetailOf(config: ServiceConfig, id: Int): ArrDetail = arrDetail(config, id)
    suspend fun arrSetLibraryMonitored(config: ServiceConfig, id: Int, monitored: Boolean): String =
        org.phioster.sanctumd.net.arrSetLibraryMonitored(config, id, monitored)
    suspend fun arrEpisodesOf(config: ServiceConfig, seriesId: Int): List<ArrEpisode> = arrEpisodes(config, seriesId)
    suspend fun arrSetEpisodeMonitored(config: ServiceConfig, episodeId: Int, monitored: Boolean): String =
        org.phioster.sanctumd.net.arrSetEpisodeMonitored(config, episodeId, monitored)
    suspend fun arrAlbumsOf(config: ServiceConfig, artistId: Int): List<org.phioster.sanctumd.model.ArrAlbum> =
        arrAlbums(config, artistId)
    suspend fun arrTracksOf(config: ServiceConfig, albumId: Int): List<org.phioster.sanctumd.model.ArrTrack> =
        arrTracks(config, albumId)
    suspend fun arrSetAlbumMonitored(config: ServiceConfig, albumId: Int, monitored: Boolean): String =
        org.phioster.sanctumd.net.arrSetAlbumMonitored(config, listOf(albumId), monitored)
    suspend fun arrReleasesFor(config: ServiceConfig, movieId: Int?, episodeId: Int?, albumId: Int? = null, seriesId: Int? = null, seasonNumber: Int? = null): List<ArrRelease> =
        arrReleases(config, movieId, episodeId, albumId, seriesId, seasonNumber)
    suspend fun arrGrabRelease(config: ServiceConfig, guid: String, indexerId: Int): String =
        arrGrab(config, guid, indexerId)
    suspend fun arrDeleteItem(config: ServiceConfig, id: Int, deleteFiles: Boolean): String =
        arrDelete(config, id, deleteFiles)
    suspend fun arrHistoryList(config: ServiceConfig): List<ArrHistoryItem> = arrHistory(config)
    suspend fun arrCalendarList(config: ServiceConfig): List<org.phioster.sanctumd.model.ArrCalendarItem> = arrCalendar(config)

    /** Merged upcoming releases across every Radarr/Sonarr/Lidarr service, tagged with
     *  the owning config, sorted by date (used by the unified calendar screen). */
    suspend fun unifiedCalendar(): List<Pair<ServiceConfig, org.phioster.sanctumd.model.ArrCalendarItem>> =
        kotlinx.coroutines.coroutineScope {
            _services.value
                .filter {
                    it.type == org.phioster.sanctumd.model.ServiceType.RADARR ||
                        it.type == org.phioster.sanctumd.model.ServiceType.SONARR ||
                        it.type == org.phioster.sanctumd.model.ServiceType.LIDARR
                }
                .map { svc -> async { runCatching { arrCalendar(svc) }.getOrDefault(emptyList()).map { svc to it } } }
                .map { it.await() }
                .flatten()
                .sortedBy { it.second.date }
        }

    /** Merged releases across all *arr for an arbitrary [from]..[to] date range (month calendar). */
    suspend fun unifiedCalendarRange(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
    ): List<Pair<ServiceConfig, org.phioster.sanctumd.model.ArrCalendarItem>> = kotlinx.coroutines.coroutineScope {
        val startI = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val endI = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        _services.value
            .filter {
                it.type == org.phioster.sanctumd.model.ServiceType.RADARR ||
                    it.type == org.phioster.sanctumd.model.ServiceType.SONARR ||
                    it.type == org.phioster.sanctumd.model.ServiceType.LIDARR
            }
            .map { svc -> async { runCatching { org.phioster.sanctumd.net.arrCalendarRange(svc, startI, endI) }.getOrDefault(emptyList()).map { svc to it } } }
            .map { it.await() }
            .flatten()
    }

    suspend fun arrSearchAllItems(config: ServiceConfig, cutoff: Boolean): String = arrSearchAll(config, cutoff)
    suspend fun arrRssSyncNow(config: ServiceConfig): String = org.phioster.sanctumd.net.arrRssSync(config)
    suspend fun arrSystemInfo(config: ServiceConfig): org.phioster.sanctumd.model.ArrSystemInfo = arrSystem(config)
    suspend fun jellyfinSubtitles(config: ServiceConfig, itemId: String, language: String): List<org.phioster.sanctumd.model.JellySubtitle> =
        org.phioster.sanctumd.net.jellyfinSubtitleCandidates(config, itemId, language)
    suspend fun jellyfinGetSubtitle(config: ServiceConfig, itemId: String, subtitleId: String): String =
        org.phioster.sanctumd.net.jellyfinDownloadSubtitle(config, itemId, subtitleId)
    suspend fun jellyfinIdentifySearch(
        config: ServiceConfig,
        itemId: String,
        kind: String,
        name: String,
        year: Int?,
    ): List<org.phioster.sanctumd.model.JellyIdentifyCandidate> =
        org.phioster.sanctumd.net.jellyfinIdentifyCandidates(config, itemId, kind, name, year)
    suspend fun jellyfinIdentifyApply(config: ServiceConfig, itemId: String, rawCandidate: String): String =
        org.phioster.sanctumd.net.jellyfinApplyIdentify(
            config, itemId, org.phioster.sanctumd.net.JellyIdentifyCandidateRaw(rawCandidate),
        )
    suspend fun jellyfinDelete(config: ServiceConfig, itemId: String): String =
        org.phioster.sanctumd.net.jellyfinDeleteItem(config, itemId)
    suspend fun arrFindByIds(config: ServiceConfig, tmdbId: String?, tvdbId: String?): org.phioster.sanctumd.model.ArrLibraryItem? =
        org.phioster.sanctumd.net.arrFindByProviderId(config, tmdbId, tvdbId)
    suspend fun arrDeleteItem(config: ServiceConfig, id: Int, deleteFiles: Boolean, addImportExclusion: Boolean): String =
        org.phioster.sanctumd.net.arrDelete(config, id, deleteFiles, addImportExclusion)
    suspend fun arrBlockedQueue(config: ServiceConfig): List<org.phioster.sanctumd.model.ArrQueueItem> =
        org.phioster.sanctumd.net.arrBlockedQueueItems(config)
    suspend fun arrBrowsePath(config: ServiceConfig, path: String): org.phioster.sanctumd.model.ArrFsListing =
        org.phioster.sanctumd.net.arrBrowse(config, path)
    suspend fun arrManualScan(config: ServiceConfig, folder: String): List<org.phioster.sanctumd.model.ArrImportItem> =
        arrManualImportScan(config, folder)
    suspend fun arrManualImport(config: ServiceConfig, rawItems: List<String>): String =
        arrManualImportExecute(config, rawItems)
    /** Patches a scanned manual-import row to target [movieId] (Radarr, unmatched files). */
    fun arrAssignImportMovie(rawJson: String, movieId: Int, title: String): String =
        org.phioster.sanctumd.net.arrImportAssignMovie(rawJson, movieId, title)
    /** Patches a scanned manual-import row to target concrete episodes (Sonarr, unmatched files). */
    fun arrAssignImportEpisodes(rawJson: String, seriesId: Int, seriesTitle: String, episodeIds: List<Int>): String =
        org.phioster.sanctumd.net.arrImportAssignEpisodes(rawJson, seriesId, seriesTitle, episodeIds)
    suspend fun arrCast(tmdbId: Int, isTv: Boolean): List<org.phioster.sanctumd.model.ArrCastMember> {
        val seerr = _services.value.firstOrNull { it.type == ServiceType.SEERR } ?: return emptyList()
        return seerrCast(seerr, tmdbId, isTv)
    }
    fun hasSeerr(): Boolean = _services.value.any { it.type == ServiceType.SEERR }

    suspend fun seerrList(config: ServiceConfig, filter: String): List<SeerrRequestItem> =
        seerrRequests(config, filter)
    suspend fun seerrIssuesList(config: ServiceConfig, filter: String): List<SeerrIssueItem> =
        seerrIssues(config, filter)
    suspend fun seerrApproveReq(config: ServiceConfig, id: Int): String = seerrApprove(config, id)
    suspend fun seerrDeclineReq(config: ServiceConfig, id: Int): String = seerrDecline(config, id)
    suspend fun seerrSearchList(config: ServiceConfig, query: String): List<SeerrSearchItem> =
        seerrSearch(config, query).let { if (hideAdult.value) it.filterNot { r -> r.adult } else it }
    suspend fun seerrDiscoverList(config: ServiceConfig, kind: String): List<org.phioster.sanctumd.model.SeerrDiscoverItem> =
        seerrDiscover(config, kind).let { if (hideAdult.value) it.filterNot { d -> d.adult } else it }
    suspend fun seerrWatchlistOf(config: ServiceConfig): List<org.phioster.sanctumd.model.SeerrDiscoverItem> =
        org.phioster.sanctumd.net.seerrWatchlist(config)
    suspend fun seerrAddToWatchlistOf(config: ServiceConfig, tmdbId: Int, mediaType: String, title: String): String =
        org.phioster.sanctumd.net.seerrAddToWatchlist(config, tmdbId, mediaType, title)
    suspend fun seerrRemoveFromWatchlistOf(config: ServiceConfig, tmdbId: Int, mediaType: String): String =
        org.phioster.sanctumd.net.seerrRemoveFromWatchlist(config, tmdbId, mediaType)
    suspend fun seerrGenresOf(config: ServiceConfig, kind: String): List<Pair<Int, String>> =
        org.phioster.sanctumd.net.seerrGenres(config, kind)
    suspend fun seerrDiscoverGenreOf(config: ServiceConfig, kind: String, genreId: Int, sortBy: String? = null, page: Int = 1): List<org.phioster.sanctumd.model.SeerrDiscoverItem> =
        org.phioster.sanctumd.net.seerrDiscoverGenre(config, kind, genreId, sortBy, page).let { if (hideAdult.value) it.filterNot { d -> d.adult } else it }
    suspend fun seerrMediaDetailById(config: ServiceConfig, tmdbId: Int, mediaType: String): org.phioster.sanctumd.model.SeerrMediaDetail =
        seerrMediaDetail(config, tmdbId, mediaType)
    suspend fun seerrStats(config: ServiceConfig): List<Pair<String, String>> = seerrRequestStats(config)
    suspend fun seerrUserList(config: ServiceConfig): List<org.phioster.sanctumd.model.SeerrUserInfo> = seerrUsers(config)
    suspend fun seerrSeasonsList(config: ServiceConfig, tmdbId: Int): List<org.phioster.sanctumd.model.SeerrSeason> =
        seerrSeasons(config, tmdbId)
    suspend fun seerrRequestMedia(
        config: ServiceConfig,
        tmdbId: Int,
        mediaType: String,
        seasons: List<Int>?,
        rootFolder: String? = null,
        serverId: Int? = null,
    ): String = seerrRequest(config, tmdbId, mediaType, seasons, rootFolder, serverId)
    suspend fun seerrRootFoldersOf(config: ServiceConfig, mediaType: String): List<org.phioster.sanctumd.model.SeerrRootFolder> =
        org.phioster.sanctumd.net.seerrRootFolders(config, mediaType)
    suspend fun seerrIssueDetailOf(config: ServiceConfig, id: Int): org.phioster.sanctumd.model.SeerrIssueDetail =
        seerrIssueDetail(config, id)
    suspend fun seerrComment(config: ServiceConfig, id: Int, message: String): String =
        seerrAddComment(config, id, message)
    suspend fun seerrIssueStatus(config: ServiceConfig, id: Int, resolved: Boolean): String =
        seerrSetIssueStatus(config, id, resolved)
    suspend fun seerrDeleteIssue(config: ServiceConfig, id: Int): String =
        seerrDeleteIssueById(config, id)

    private fun setStatus(id: String, status: ServiceStatus) {
        // update {} keeps the read-modify-write atomic even if this is ever called
        // off the main thread (refreshAll fans out one coroutine per service).
        _statuses.update { it + (id to status) }
    }
}
