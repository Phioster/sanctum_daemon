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
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.net.clearJellyfinSession
import org.phioster.nexarr.net.fetchStatus
import org.phioster.nexarr.net.nzbgetHistory
import org.phioster.nexarr.net.nzbgetQueue
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

    private fun setStatus(id: String, status: ServiceStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(id, status) }
    }
}
