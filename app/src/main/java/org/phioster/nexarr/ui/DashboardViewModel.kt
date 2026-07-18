package org.phioster.nexarr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phioster.nexarr.data.ServiceStore
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceType
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ArrDetail
import org.phioster.nexarr.model.ArrEpisode
import org.phioster.nexarr.model.ArrHistoryItem
import org.phioster.nexarr.model.ArrLibraryItem
import org.phioster.nexarr.model.ArrLookupItem
import org.phioster.nexarr.model.ArrMissingItem
import org.phioster.nexarr.model.ArrProfile
import org.phioster.nexarr.model.ArrQueueItem
import org.phioster.nexarr.model.ArrRelease
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.ProwlarrCategory
import org.phioster.nexarr.model.ProwlarrHistoryItem
import org.phioster.nexarr.model.ProwlarrIndexerItem
import org.phioster.nexarr.model.ProwlarrRelease
import org.phioster.nexarr.model.ProwlarrSystemInfo
import org.phioster.nexarr.model.ProwlarrTaskItem
import org.phioster.nexarr.model.SeerrIssueItem
import org.phioster.nexarr.model.SeerrRequestItem
import org.phioster.nexarr.model.SeerrSearchItem
import org.phioster.nexarr.net.arrAdd
import org.phioster.nexarr.net.arrAlbums
import org.phioster.nexarr.net.arrTracks
import org.phioster.nexarr.net.arrCutoff
import org.phioster.nexarr.net.arrDelete
import org.phioster.nexarr.net.arrDetail
import org.phioster.nexarr.net.arrEpisodes
import org.phioster.nexarr.net.arrGrab
import org.phioster.nexarr.net.arrHistory
import org.phioster.nexarr.net.arrCalendar
import org.phioster.nexarr.net.arrLibrary
import org.phioster.nexarr.net.arrManualImportExecute
import org.phioster.nexarr.net.arrManualImportScan
import org.phioster.nexarr.net.arrReleases
import org.phioster.nexarr.net.arrSearchAll
import org.phioster.nexarr.net.arrSystem
import org.phioster.nexarr.net.serviceSearch
import org.phioster.nexarr.net.seerrCast
import org.phioster.nexarr.net.arrLibrarySearch
import org.phioster.nexarr.net.arrLookup
import org.phioster.nexarr.net.arrMetadataProfiles
import org.phioster.nexarr.net.arrProfiles
import org.phioster.nexarr.net.arrRootFolders
import org.phioster.nexarr.net.arrMissing
import org.phioster.nexarr.net.arrQueue
import org.phioster.nexarr.net.arrQueueRemove
import org.phioster.nexarr.net.arrSearchItem
import org.phioster.nexarr.net.clearJellyfinSession
import org.phioster.nexarr.net.fetchStatus
import org.phioster.nexarr.net.nzbgetHistory
import org.phioster.nexarr.net.nzbgetQueue
import org.phioster.nexarr.net.nzbServerDetails
import org.phioster.nexarr.net.runNzbAppendUrl
import org.phioster.nexarr.net.runNzbEditQueue
import org.phioster.nexarr.net.runNzbRate
import org.phioster.nexarr.net.seerrApprove
import org.phioster.nexarr.net.seerrDecline
import org.phioster.nexarr.net.seerrAddComment
import org.phioster.nexarr.net.seerrCreateRequest
import org.phioster.nexarr.net.seerrDeleteIssueById
import org.phioster.nexarr.net.seerrDiscover
import org.phioster.nexarr.net.seerrIssueDetail
import org.phioster.nexarr.net.seerrMediaDetail
import org.phioster.nexarr.net.seerrRequestStats
import org.phioster.nexarr.net.seerrUsers
import org.phioster.nexarr.net.seerrIssues
import org.phioster.nexarr.net.seerrRequest
import org.phioster.nexarr.net.seerrRequests
import org.phioster.nexarr.net.seerrSearch
import org.phioster.nexarr.net.seerrSeasons
import org.phioster.nexarr.net.seerrSetIssueStatus
import org.phioster.nexarr.net.jellyfinActivity
import org.phioster.nexarr.net.jellyfinCreateUser
import org.phioster.nexarr.net.jellyfinDevices
import org.phioster.nexarr.net.jellyfinDeleteUser
import org.phioster.nexarr.net.jellyfinItemDetail
import org.phioster.nexarr.net.jellyfinItems
import org.phioster.nexarr.net.jellyfinLatest
import org.phioster.nexarr.net.jellyfinAddLibrary
import org.phioster.nexarr.net.jellyfinAddLibraryPath
import org.phioster.nexarr.net.jellyfinAddTuner
import org.phioster.nexarr.net.jellyfinAddXmltvProvider
import org.phioster.nexarr.net.jellyfinChannels
import org.phioster.nexarr.net.jellyfinDeleteProvider
import org.phioster.nexarr.net.jellyfinDeleteTuner
import org.phioster.nexarr.net.jellyfinLiveTv
import org.phioster.nexarr.net.jellyfinDeleteLibrary
import org.phioster.nexarr.net.jellyfinInstallPackage
import org.phioster.nexarr.net.jellyfinLibraries
import org.phioster.nexarr.net.jellyfinLibraryViews
import org.phioster.nexarr.net.jellyfinLogContent
import org.phioster.nexarr.net.jellyfinLogFiles
import org.phioster.nexarr.net.jellyfinPackages
import org.phioster.nexarr.net.jellyfinPlugins
import org.phioster.nexarr.net.jellyfinRemoveLibraryPath
import org.phioster.nexarr.net.jellyfinRenameLibrary
import org.phioster.nexarr.net.jellyfinSetPluginEnabled
import org.phioster.nexarr.net.jellyfinUninstallPlugin
import org.phioster.nexarr.net.jellyfinResume
import org.phioster.nexarr.net.jellyfinScanItem
import org.phioster.nexarr.net.jellyfinSetPassword
import org.phioster.nexarr.net.jellyfinSetPolicy
import org.phioster.nexarr.net.jellyfinPlayCommand
import org.phioster.nexarr.net.jellyfinRestart
import org.phioster.nexarr.net.jellyfinRunTask
import org.phioster.nexarr.net.jellyfinSendMessage
import org.phioster.nexarr.net.jellyfinSessions
import org.phioster.nexarr.net.jellyfinSystemInfo
import org.phioster.nexarr.net.jellyfinTasks
import org.phioster.nexarr.net.jellyfinUsers
import org.phioster.nexarr.net.runJellyfinScan
import org.phioster.nexarr.net.runNzbgetPause
import org.phioster.nexarr.net.runNzbgetResume
import org.phioster.nexarr.net.arrPushRelease
import org.phioster.nexarr.net.prowlarrCategories
import org.phioster.nexarr.net.prowlarrGrab
import org.phioster.nexarr.net.prowlarrHistory
import org.phioster.nexarr.net.prowlarrIndexers
import org.phioster.nexarr.net.prowlarrSearch
import org.phioster.nexarr.net.prowlarrSystem
import org.phioster.nexarr.net.prowlarrTasks
import org.phioster.nexarr.net.prowlarrTestIndexer
import org.phioster.nexarr.net.prowlarrDeleteIndexer
import org.phioster.nexarr.net.prowlarrToggleIndexer
import org.phioster.nexarr.net.runProwlarrTestAll
import org.phioster.nexarr.net.runSearchMissing

/** Where a launcher shortcut wants to navigate. [serviceId] is set for kind "service". */
data class PendingRoute(val kind: String, val serviceId: String? = null)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    // Home navigation state lives here (not in composables) so it survives when HomeShell
    // leaves composition — otherwise back from a service always lands on the first tab.
    val homeTab = androidx.compose.runtime.mutableIntStateOf(0)

    /** Bumped by pull-to-refresh on a dashboard tab; dashboard cards key their data
     *  load on it, so incrementing forces every card to reload. */
    val dashRefreshTick = androidx.compose.runtime.mutableIntStateOf(0)

    /** A pending navigation from a launcher shortcut, consumed once by the UI. */
    private val _pendingRoute = MutableStateFlow<PendingRoute?>(null)
    val pendingRoute: StateFlow<PendingRoute?> = _pendingRoute.asStateFlow()
    fun setRoute(route: PendingRoute?) { _pendingRoute.value = route }
    fun consumeRoute() { _pendingRoute.value = null }

    private val bundleJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Serialize services + notify settings + dashboard layout and encrypt them with
     *  [password] into a portable file (see [org.phioster.nexarr.security.PortableCrypto]). */
    suspend fun exportConfig(password: String): ByteArray {
        val bundle = org.phioster.nexarr.model.ConfigBundle(
            services = _services.value,
            notify = notifyStore.currentSettings(),
            tabs = _tabs.value,
        )
        val plain = bundleJson.encodeToString(org.phioster.nexarr.model.ConfigBundle.serializer(), bundle)
        return org.phioster.nexarr.security.PortableCrypto.encrypt(plain, password)
    }

    /** Decrypt + apply an exported bundle, replacing the current config. Returns the
     *  number of services imported, or a failure (wrong password / bad file). */
    suspend fun importConfig(data: ByteArray, password: String): Result<Int> = runCatching {
        val plain = org.phioster.nexarr.security.PortableCrypto.decrypt(data, password)
        val bundle = bundleJson.decodeFromString(org.phioster.nexarr.model.ConfigBundle.serializer(), plain)
        store.save(bundle.services)
        notifyStore.save(bundle.notify)
        dashStore.save(bundle.tabs)
        val ctx = getApplication<Application>()
        if (bundle.notify.enabled) org.phioster.nexarr.notify.Notifications.schedule(ctx, bundle.notify.intervalMin)
        else org.phioster.nexarr.notify.Notifications.cancel(ctx)
        org.phioster.nexarr.notify.NtfyStreamService.restart(ctx)
        bundle.services.size
    }

    /** Set when navigating away from inside the Services drawer; HomeShell reopens it once on return. */
    var reopenDrawer: Boolean = false

    /** App-lock session state: survives rotation (VM outlives the activity), reset on process death. */
    val unlocked = androidx.compose.runtime.mutableStateOf(false)

    private val store = ServiceStore(app)
    private val dashStore = org.phioster.nexarr.data.DashboardStore(app)
    private val notifyStore = org.phioster.nexarr.data.NotifyStore(app)

    val notifySettings: StateFlow<org.phioster.nexarr.model.NotifySettings> =
        notifyStore.settings.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, org.phioster.nexarr.model.NotifySettings())

    /** Persists notification prefs; (re)schedules the poller and starts/stops the live-push service. */
    fun saveNotifySettings(s: org.phioster.nexarr.model.NotifySettings) {
        viewModelScope.launch {
            notifyStore.save(s)
            val ctx = getApplication<Application>()
            if (s.enabled) org.phioster.nexarr.notify.Notifications.schedule(ctx, s.intervalMin)
            else org.phioster.nexarr.notify.Notifications.cancel(ctx)
            // Restart so the stream re-evaluates all subscriptions (settings topic +
            // NTFY-service topics); it stops itself when none remain.
            org.phioster.nexarr.notify.NtfyStreamService.restart(ctx)
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

    /** null until loaded; then true once the first-run onboarding is completed/dismissed. */
    val onboardingDone: StateFlow<Boolean?> =
        dashStore.onboardingDone.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { dashStore.setOnboardingDone(done) }

    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val _tabs = MutableStateFlow<List<org.phioster.nexarr.model.DashTab>>(emptyList())
    val tabs: StateFlow<List<org.phioster.nexarr.model.DashTab>> = _tabs.asStateFlow()

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

    private fun defaultHomeTab() = org.phioster.nexarr.model.DashTab(
        id = java.util.UUID.randomUUID().toString(),
        name = "Home",
        cards = emptyList(),
    )

    private fun persistTabs(list: List<org.phioster.nexarr.model.DashTab>) {
        _tabs.value = list
        viewModelScope.launch { dashStore.save(list) }
    }

    fun addTab(name: String, icon: String = "", accent: Long = 0) {
        persistTabs(_tabs.value + org.phioster.nexarr.model.DashTab(java.util.UUID.randomUUID().toString(), name.ifBlank { "Tab" }, icon = icon, accent = accent))
    }

    fun setTabIcon(tabId: String, icon: String) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(icon = icon) else it })
    }

    fun setTabAccent(tabId: String, accent: Long) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(accent = accent) else it })
    }

    fun moveTab(tabId: String, direction: Int): Int {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == tabId }
        val target = idx + direction
        if (idx < 0 || target < 0 || target >= list.size) return idx
        list[idx] = list[target].also { list[target] = list[idx] }
        persistTabs(list)
        return target
    }

    fun removeTab(tabId: String) {
        val list = _tabs.value.filterNot { it.id == tabId }
        persistTabs(list.ifEmpty { listOf(defaultHomeTab()) })
    }

    fun renameTab(tabId: String, name: String) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(name = name.ifBlank { it.name }) else it })
    }

    fun addCard(tabId: String, type: org.phioster.nexarr.model.CardType, serviceId: String) {
        val card = org.phioster.nexarr.model.DashCard(java.util.UUID.randomUUID().toString(), type, serviceId)
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(cards = it.cards + card) else it })
    }

    fun removeCard(tabId: String, cardId: String) {
        persistTabs(_tabs.value.map { if (it.id == tabId) it.copy(cards = it.cards.filterNot { c -> c.id == cardId }) else it })
    }

    fun updateCard(tabId: String, cardId: String, title: String, count: Int, accent: Long, icon: String, posterSize: String, background: Boolean, theme: String, density: String) {
        persistTabs(_tabs.value.map { tab ->
            if (tab.id != tabId) tab
            else tab.copy(cards = tab.cards.map {
                if (it.id == cardId) it.copy(title = title.trim(), count = count.coerceIn(3, 20), accent = accent, icon = icon, posterSize = posterSize, background = background, theme = theme, density = density) else it
            })
        })
    }

    fun moveCard(tabId: String, cardId: String, direction: Int) {
        persistTabs(_tabs.value.map { tab ->
            if (tab.id != tabId) return@map tab
            val cards = tab.cards.toMutableList()
            val idx = cards.indexOfFirst { it.id == cardId }
            val target = idx + direction
            if (idx < 0 || target < 0 || target >= cards.size) return@map tab
            cards[idx] = cards[target].also { cards[target] = cards[idx] }
            tab.copy(cards = cards)
        })
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
            if (config.type == org.phioster.nexarr.model.ServiceType.NTFY) {
                org.phioster.nexarr.notify.NtfyStreamService.restart(getApplication())
            }
        }
    }

    fun removeService(id: String) {
        viewModelScope.launch {
            val wasNtfy = _services.value.firstOrNull { it.id == id }?.type == org.phioster.nexarr.model.ServiceType.NTFY
            store.save(_services.value.filterNot { it.id == id })
            if (wasNtfy) org.phioster.nexarr.notify.NtfyStreamService.restart(getApplication())
        }
    }

    suspend fun ntfyMessages(config: ServiceConfig, topic: String): List<org.phioster.nexarr.model.NtfyMessage> =
        org.phioster.nexarr.net.ntfyHistory(config, topic)

    suspend fun runShortcut(config: ServiceConfig, sc: org.phioster.nexarr.model.HttpShortcut): String =
        org.phioster.nexarr.net.runHttpShortcut(config, sc)

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

    /** One-off connection test used by the Add-service screen. */
    suspend fun test(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    /** Live status (key stat numbers) for a Statistics card. */
    suspend fun serviceStats(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    /** Watch-time leaderboard (needs the Jellyfin Playback Reporting plugin). */
    suspend fun jellyfinTopWatchers(config: ServiceConfig): List<org.phioster.nexarr.model.JellyWatchStat> =
        org.phioster.nexarr.net.jellyfinTopWatchers(config)

    /** Triggers a Jellyfin library scan; returns a result line for the UI. */
    suspend fun jellyfinScan(config: ServiceConfig): String = runJellyfinScan(config)
    suspend fun jellyfinSessionList(config: ServiceConfig): List<org.phioster.nexarr.model.JellySession> = jellyfinSessions(config)
    suspend fun jellyfinUserList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyUser> = jellyfinUsers(config)
    suspend fun jellyfinControl(config: ServiceConfig, sessionId: String, cmd: String): String =
        jellyfinPlayCommand(config, sessionId, cmd)
    suspend fun jellyfinMessage(config: ServiceConfig, sessionId: String, text: String): String =
        jellyfinSendMessage(config, sessionId, text)
    suspend fun jellyfinInfo(config: ServiceConfig): org.phioster.nexarr.model.JellySystemInfo = jellyfinSystemInfo(config)
    suspend fun jellyfinTaskList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyTask> = jellyfinTasks(config)
    suspend fun jellyfinRunTaskById(config: ServiceConfig, taskId: String): String = jellyfinRunTask(config, taskId)
    suspend fun jellyfinActivityLog(config: ServiceConfig): List<org.phioster.nexarr.model.JellyActivity> = jellyfinActivity(config)
    suspend fun jellyfinRestartServer(config: ServiceConfig): String = jellyfinRestart(config)
    suspend fun jellyfinDeviceList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyDevice> =
        jellyfinDevices(config)
    suspend fun jellyfinLibraryList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyLibrary> =
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
    suspend fun jellyfinViews(config: ServiceConfig): List<org.phioster.nexarr.model.JellyMediaItem> =
        jellyfinLibraryViews(config)
    suspend fun jellyfinContinue(config: ServiceConfig): List<org.phioster.nexarr.model.JellyMediaItem> =
        jellyfinResume(config)
    suspend fun jellyfinRecent(config: ServiceConfig, parentId: String? = null): List<org.phioster.nexarr.model.JellyMediaItem> =
        jellyfinLatest(config, parentId)
    suspend fun jellyfinItemList(config: ServiceConfig, parentId: String, seasonNumber: Int? = null): List<org.phioster.nexarr.model.JellyMediaItem> =
        jellyfinItems(config, parentId, seasonNumber)
    suspend fun jellyfinMediaDetail(config: ServiceConfig, itemId: String): org.phioster.nexarr.model.JellyMediaDetail =
        jellyfinItemDetail(config, itemId)
    suspend fun jellyfinScanLibrary(config: ServiceConfig, itemId: String): String =
        jellyfinScanItem(config, itemId)
    suspend fun jellyfinLogList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyLogFile> =
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
    suspend fun jellyfinPluginList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyPlugin> =
        jellyfinPlugins(config)
    suspend fun jellyfinPluginEnable(config: ServiceConfig, id: String, version: String, enabled: Boolean): String =
        jellyfinSetPluginEnabled(config, id, version, enabled)
    suspend fun jellyfinPluginUninstall(config: ServiceConfig, id: String, version: String): String =
        jellyfinUninstallPlugin(config, id, version)
    suspend fun jellyfinCatalog(config: ServiceConfig): List<org.phioster.nexarr.model.JellyPackage> =
        jellyfinPackages(config)
    suspend fun jellyfinCatalogInstall(config: ServiceConfig, name: String, guid: String): String =
        jellyfinInstallPackage(config, name, guid)
    suspend fun jellyfinLiveTvStatus(config: ServiceConfig): org.phioster.nexarr.model.JellyLiveTv =
        jellyfinLiveTv(config)
    suspend fun jellyfinChannelList(config: ServiceConfig): List<org.phioster.nexarr.model.JellyChannel> =
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
    suspend fun globalSearch(term: String): List<org.phioster.nexarr.model.SearchResult> =
        kotlinx.coroutines.coroutineScope {
            _services.value
                .map { cfg -> async { serviceSearch(cfg, term) } }
                .awaitAll()
                .flatten()
        }

    suspend fun searchMissing(config: ServiceConfig): String = runSearchMissing(config)
    suspend fun prowlarrTestAll(config: ServiceConfig): String = runProwlarrTestAll(config)
    suspend fun prowlarrIndexerList(config: ServiceConfig): List<ProwlarrIndexerItem> = prowlarrIndexers(config)
    suspend fun prowlarrToggle(config: ServiceConfig, id: Int, enable: Boolean): String =
        prowlarrToggleIndexer(config, id, enable)
    suspend fun prowlarrDelete(config: ServiceConfig, id: Int): String =
        prowlarrDeleteIndexer(config, id)
    suspend fun prowlarrTest(config: ServiceConfig, id: Int): String = prowlarrTestIndexer(config, id)
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
    suspend fun arrEpisodesOf(config: ServiceConfig, seriesId: Int): List<ArrEpisode> = arrEpisodes(config, seriesId)
    suspend fun arrSetEpisodeMonitored(config: ServiceConfig, episodeId: Int, monitored: Boolean): String =
        org.phioster.nexarr.net.arrSetEpisodeMonitored(config, episodeId, monitored)
    suspend fun arrAlbumsOf(config: ServiceConfig, artistId: Int): List<org.phioster.nexarr.model.ArrAlbum> =
        arrAlbums(config, artistId)
    suspend fun arrTracksOf(config: ServiceConfig, albumId: Int): List<org.phioster.nexarr.model.ArrTrack> =
        arrTracks(config, albumId)
    suspend fun arrReleasesFor(config: ServiceConfig, movieId: Int?, episodeId: Int?, albumId: Int? = null): List<ArrRelease> =
        arrReleases(config, movieId, episodeId, albumId)
    suspend fun arrGrabRelease(config: ServiceConfig, guid: String, indexerId: Int): String =
        arrGrab(config, guid, indexerId)
    suspend fun arrDeleteItem(config: ServiceConfig, id: Int, deleteFiles: Boolean): String =
        arrDelete(config, id, deleteFiles)
    suspend fun arrHistoryList(config: ServiceConfig): List<ArrHistoryItem> = arrHistory(config)
    suspend fun arrCalendarList(config: ServiceConfig): List<org.phioster.nexarr.model.ArrCalendarItem> = arrCalendar(config)

    /** Merged upcoming releases across every Radarr/Sonarr/Lidarr service, tagged with
     *  the owning config, sorted by date (used by the unified calendar screen). */
    suspend fun unifiedCalendar(): List<Pair<ServiceConfig, org.phioster.nexarr.model.ArrCalendarItem>> =
        kotlinx.coroutines.coroutineScope {
            _services.value
                .filter {
                    it.type == org.phioster.nexarr.model.ServiceType.RADARR ||
                        it.type == org.phioster.nexarr.model.ServiceType.SONARR ||
                        it.type == org.phioster.nexarr.model.ServiceType.LIDARR
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
    ): List<Pair<ServiceConfig, org.phioster.nexarr.model.ArrCalendarItem>> = kotlinx.coroutines.coroutineScope {
        val startI = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val endI = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        _services.value
            .filter {
                it.type == org.phioster.nexarr.model.ServiceType.RADARR ||
                    it.type == org.phioster.nexarr.model.ServiceType.SONARR ||
                    it.type == org.phioster.nexarr.model.ServiceType.LIDARR
            }
            .map { svc -> async { runCatching { org.phioster.nexarr.net.arrCalendarRange(svc, startI, endI) }.getOrDefault(emptyList()).map { svc to it } } }
            .map { it.await() }
            .flatten()
    }

    suspend fun arrSearchAllItems(config: ServiceConfig, cutoff: Boolean): String = arrSearchAll(config, cutoff)
    suspend fun arrRssSyncNow(config: ServiceConfig): String = org.phioster.nexarr.net.arrRssSync(config)
    suspend fun arrSystemInfo(config: ServiceConfig): org.phioster.nexarr.model.ArrSystemInfo = arrSystem(config)
    suspend fun arrManualScan(config: ServiceConfig, folder: String): List<org.phioster.nexarr.model.ArrImportItem> =
        arrManualImportScan(config, folder)
    suspend fun arrManualImport(config: ServiceConfig, rawItems: List<String>): String =
        arrManualImportExecute(config, rawItems)
    suspend fun arrCast(tmdbId: Int, isTv: Boolean): List<org.phioster.nexarr.model.ArrCastMember> {
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
        seerrSearch(config, query)
    suspend fun seerrRequestItem(config: ServiceConfig, item: SeerrSearchItem): String =
        seerrCreateRequest(config, item)
    suspend fun seerrDiscoverList(config: ServiceConfig, kind: String): List<org.phioster.nexarr.model.SeerrDiscoverItem> =
        seerrDiscover(config, kind)
    suspend fun seerrWatchlistOf(config: ServiceConfig): List<org.phioster.nexarr.model.SeerrDiscoverItem> =
        org.phioster.nexarr.net.seerrWatchlist(config)
    suspend fun seerrMediaDetailById(config: ServiceConfig, tmdbId: Int, mediaType: String): org.phioster.nexarr.model.SeerrMediaDetail =
        seerrMediaDetail(config, tmdbId, mediaType)
    suspend fun seerrStats(config: ServiceConfig): List<Pair<String, String>> = seerrRequestStats(config)
    suspend fun seerrUserList(config: ServiceConfig): List<org.phioster.nexarr.model.SeerrUserInfo> = seerrUsers(config)
    suspend fun seerrSeasonsList(config: ServiceConfig, tmdbId: Int): List<org.phioster.nexarr.model.SeerrSeason> =
        seerrSeasons(config, tmdbId)
    suspend fun seerrRequestMedia(config: ServiceConfig, tmdbId: Int, mediaType: String, seasons: List<Int>?): String =
        seerrRequest(config, tmdbId, mediaType, seasons)
    suspend fun seerrIssueDetailOf(config: ServiceConfig, id: Int): org.phioster.nexarr.model.SeerrIssueDetail =
        seerrIssueDetail(config, id)
    suspend fun seerrComment(config: ServiceConfig, id: Int, message: String): String =
        seerrAddComment(config, id, message)
    suspend fun seerrIssueStatus(config: ServiceConfig, id: Int, resolved: Boolean): String =
        seerrSetIssueStatus(config, id, resolved)
    suspend fun seerrDeleteIssue(config: ServiceConfig, id: Int): String =
        seerrDeleteIssueById(config, id)

    private fun setStatus(id: String, status: ServiceStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(id, status) }
    }
}
