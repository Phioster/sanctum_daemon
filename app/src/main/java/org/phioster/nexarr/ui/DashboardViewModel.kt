package org.phioster.nexarr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.phioster.nexarr.net.arrCutoff
import org.phioster.nexarr.net.arrDelete
import org.phioster.nexarr.net.arrDetail
import org.phioster.nexarr.net.arrEpisodes
import org.phioster.nexarr.net.arrGrab
import org.phioster.nexarr.net.arrHistory
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
import org.phioster.nexarr.net.seerrIssues
import org.phioster.nexarr.net.seerrRequest
import org.phioster.nexarr.net.seerrRequests
import org.phioster.nexarr.net.seerrSearch
import org.phioster.nexarr.net.seerrSeasons
import org.phioster.nexarr.net.seerrSetIssueStatus
import org.phioster.nexarr.net.jellyfinActivity
import org.phioster.nexarr.net.jellyfinCreateUser
import org.phioster.nexarr.net.jellyfinDeleteUser
import org.phioster.nexarr.net.jellyfinItemDetail
import org.phioster.nexarr.net.jellyfinItems
import org.phioster.nexarr.net.jellyfinLatest
import org.phioster.nexarr.net.jellyfinLibraries
import org.phioster.nexarr.net.jellyfinLibraryViews
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
import org.phioster.nexarr.net.prowlarrToggleIndexer
import org.phioster.nexarr.net.runProwlarrTestAll
import org.phioster.nexarr.net.runSearchMissing

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ServiceStore(app)

    private val _services = MutableStateFlow<List<ServiceConfig>>(emptyList())
    val services: StateFlow<List<ServiceConfig>> = _services.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    init {
        viewModelScope.launch {
            store.services.collect { list ->
                _services.value = list
                refreshAll()
            }
        }
    }

    fun refreshAll() {
        _services.value.forEach { config ->
            viewModelScope.launch {
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
        }
    }

    fun removeService(id: String) {
        viewModelScope.launch { store.save(_services.value.filterNot { it.id == id }) }
    }

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
    suspend fun arrAlbumsOf(config: ServiceConfig, artistId: Int): List<org.phioster.nexarr.model.ArrAlbum> =
        arrAlbums(config, artistId)
    suspend fun arrReleasesFor(config: ServiceConfig, movieId: Int?, episodeId: Int?, albumId: Int? = null): List<ArrRelease> =
        arrReleases(config, movieId, episodeId, albumId)
    suspend fun arrGrabRelease(config: ServiceConfig, guid: String, indexerId: Int): String =
        arrGrab(config, guid, indexerId)
    suspend fun arrDeleteItem(config: ServiceConfig, id: Int, deleteFiles: Boolean): String =
        arrDelete(config, id, deleteFiles)
    suspend fun arrHistoryList(config: ServiceConfig): List<ArrHistoryItem> = arrHistory(config)
    suspend fun arrSearchAllItems(config: ServiceConfig, cutoff: Boolean): String = arrSearchAll(config, cutoff)
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
