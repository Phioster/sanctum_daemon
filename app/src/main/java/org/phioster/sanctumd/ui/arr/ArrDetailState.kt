package org.phioster.sanctumd.ui.arr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import org.phioster.sanctumd.model.ArrAlbum
import org.phioster.sanctumd.model.ArrCastMember
import org.phioster.sanctumd.model.ArrDetail
import org.phioster.sanctumd.model.ArrEpisode
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.ArrTrack
import org.phioster.sanctumd.model.SeerrTitleExtras
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.WatchAvailability
import org.phioster.sanctumd.ui.DashboardViewModel

/**
 * Everything one library item's detail screen holds: what was loaded, and which of its five
 * dialogs is open.
 *
 * No CoroutineScope lives here. [load] is a suspend function the screen calls, so the work
 * belongs to whoever is still on screen rather than to this object.
 */
internal class ArrDetailState {
    var detail by mutableStateOf<ArrDetail?>(null)
    var episodes by mutableStateOf<List<ArrEpisode>?>(null)
    var albums by mutableStateOf<List<ArrAlbum>?>(null)
    var cast by mutableStateOf<List<ArrCastMember>?>(null)
    var availability by mutableStateOf(WatchAvailability.NONE)
    var loadError by mutableStateOf<String?>(null)
    var actionMsg by mutableStateOf<String?>(null)

    // ── The dialogs ──
    /** The album whose track list is open, and its tracks once they arrive. */
    var trackAlbum by mutableStateOf<ArrAlbum?>(null)
    var tracks by mutableStateOf<List<ArrTrack>?>(null)
    var barMenu by mutableStateOf(false)
    var showMove by mutableStateOf(false)
    var moveTargets by mutableStateOf<List<String>?>(null)
    var moving by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)
    var deleteFiles by mutableStateOf(false)
    /** Release picker: open with [releases] null while the list is still being fetched. */
    var pickerOpen by mutableStateOf(false)
    var releases by mutableStateOf<List<ArrRelease>?>(null)
    var pickerTitle by mutableStateOf("")
    var confirmGrab by mutableStateOf<ArrRelease?>(null)

    /**
     * Loads the item, its children, and — when a Seerr is configured — the cast and where it
     * streams. The extras are best-effort: a title still opens when Seerr is down.
     */
    suspend fun load(vm: DashboardViewModel, config: ServiceConfig, itemId: Int, isSonarr: Boolean, isLidarr: Boolean) {
        loadError = null
        try {
            val d = vm.arrDetailOf(config, itemId)
            detail = d
            if (isSonarr) episodes = vm.arrEpisodesOf(config, itemId)
            if (isLidarr) albums = vm.arrAlbumsOf(config, itemId)
            if (vm.hasSeerr() && d.tmdbId > 0) {
                val extras = runCatching { vm.arrTitleExtras(d.tmdbId, isSonarr) }.getOrDefault(SeerrTitleExtras())
                cast = extras.cast
                availability = extras.availability
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            loadError = t.message
        }
    }
}

@Composable
internal fun rememberArrDetailState(itemId: Int): ArrDetailState = remember(itemId) { ArrDetailState() }
