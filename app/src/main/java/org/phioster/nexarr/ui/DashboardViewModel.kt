package org.phioster.nexarr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phioster.nexarr.data.ServiceStore
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ArrLibraryItem
import org.phioster.nexarr.model.ArrLookupItem
import org.phioster.nexarr.model.ArrMissingItem
import org.phioster.nexarr.model.ArrProfile
import org.phioster.nexarr.model.ArrQueueItem
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.SeerrIssueItem
import org.phioster.nexarr.model.SeerrRequestItem
import org.phioster.nexarr.model.SeerrSearchItem
import org.phioster.nexarr.net.arrAdd
import org.phioster.nexarr.net.arrLibrary
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
import org.phioster.nexarr.net.seerrCreateRequest
import org.phioster.nexarr.net.seerrIssues
import org.phioster.nexarr.net.seerrRequests
import org.phioster.nexarr.net.seerrSearch
import org.phioster.nexarr.net.runJellyfinScan
import org.phioster.nexarr.net.runNzbgetPause
import org.phioster.nexarr.net.runNzbgetResume
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

    /** One-off connection test used by the Add-service screen. */
    suspend fun test(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    /** Triggers a Jellyfin library scan; returns a result line for the UI. */
    suspend fun jellyfinScan(config: ServiceConfig): String = runJellyfinScan(config)

    suspend fun searchMissing(config: ServiceConfig): String = runSearchMissing(config)
    suspend fun prowlarrTestAll(config: ServiceConfig): String = runProwlarrTestAll(config)
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

    private fun setStatus(id: String, status: ServiceStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(id, status) }
    }
}
