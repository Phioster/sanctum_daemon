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
import org.phioster.nexarr.net.fetchStatus

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

    fun addService(config: ServiceConfig) {
        viewModelScope.launch { store.save(_services.value + config) }
    }

    fun removeService(id: String) {
        viewModelScope.launch { store.save(_services.value.filterNot { it.id == id }) }
    }

    /** One-off connection test used by the Add-service screen. */
    suspend fun test(config: ServiceConfig): ServiceStatus = fetchStatus(config)

    private fun setStatus(id: String, status: ServiceStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(id, status) }
    }
}
