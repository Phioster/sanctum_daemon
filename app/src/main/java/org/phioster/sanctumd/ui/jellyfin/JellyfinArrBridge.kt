package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * Which service owns a Jellyfin item of this kind.
 *
 * An episode and a season reach Sonarr through their series. Excluding them — the first version
 * did — left the bridge working on films and quietly missing everywhere else, which is worse than
 * the risk it was avoiding. The risk is handled by [counterpartScopeNote] instead: say what the
 * actions reach rather than hide them.
 */
internal fun arrServiceTypeFor(kind: String): ServiceType? = when (kind) {
    "Movie" -> ServiceType.RADARR
    "Series", "Season", "Episode" -> ServiceType.SONARR
    else -> null
}

/** Warns when the actions reach past the item on screen. Empty when they do not. */
internal fun counterpartScopeNote(kind: String): String = when (kind) {
    "Season", "Episode" -> "These actions apply to the whole series, not to this one item."
    else -> ""
}

/**
 * Whether tapping a folder should open a detail sheet rather than descend the browse list.
 *
 * Jellyfin's own apps treat a series and a season as *things* with artwork, a synopsis and
 * actions — you tap the series, read about it, and its seasons sit inside that page. Only the
 * library roots ("movies"/"tvshows"/"music") are plain containers with nothing to say about
 * themselves, so those stay in the browse list. Music albums keep their list too: the album
 * screen is a track list, and folding it into a sheet would lose the queue controls.
 */
internal fun opensAsDetail(kind: String): Boolean = kind == "Series" || kind == "Season"
