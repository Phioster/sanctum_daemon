package org.phioster.sanctumd.ui.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** Where the TV UI currently is. The player is not a route — it is an overlay over whatever is below. */
sealed interface TvRoute {
    data object Home : TvRoute
    /** A library, series or season being browsed as a grid. */
    data class Browse(val id: String, val title: String) : TvRoute
    /** A single playable item (film, episode) with its description and actions. */
    data class Detail(val id: String, val title: String) : TvRoute
}

/** What the player overlay should play. */
data class TvPlayRequest(val itemId: String, val title: String)

/**
 * State for the TV client: which Jellyfin server is configured, and where in the UI we are.
 *
 * Deliberately thin. Screens fetch their own data with the suspend functions in `net` — a television
 * shows one screen at a time, so there is nothing to share and nothing to keep warm.
 */
class TvViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ServiceStore(app)

    /** null until DataStore has answered — distinguishes "loading" from "nothing configured", which
     *  otherwise makes the setup screen flash on every start. */
    private val _services = MutableStateFlow<List<ServiceConfig>?>(null)
    val services: StateFlow<List<ServiceConfig>?> = _services.asStateFlow()

    /** Back stack, oldest first; the last entry is on screen. */
    var stack by mutableStateOf<List<TvRoute>>(listOf(TvRoute.Home))
        private set

    var playing by mutableStateOf<TvPlayRequest?>(null)

    /** Bumped after playback so the screen underneath reloads its progress/watched state. */
    var refreshTick by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { store.services.collect { _services.value = it } }
    }

    val current: TvRoute get() = stack.lastOrNull() ?: TvRoute.Home

    fun open(route: TvRoute) { stack = stack + route }

    /** Returns false when there is nothing left to pop (the caller should let the system handle back). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    fun refresh() { refreshTick++ }

    /** Saves the signed-in server, replacing any Jellyfin entry that was there before. */
    fun saveServer(config: ServiceConfig, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = store.services.first()
            store.save(existing.filterNot { it.type == ServiceType.JELLYFIN } + config)
            stack = listOf(TvRoute.Home)
            onDone()
        }
    }

    /** Signs out: drops the server so the setup screen comes back. */
    fun forgetServer() {
        viewModelScope.launch {
            val existing = store.services.first()
            store.save(existing.filterNot { it.type == ServiceType.JELLYFIN })
            stack = listOf(TvRoute.Home)
        }
    }
}
