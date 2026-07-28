package org.phioster.sanctumd.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.util.UnstableApi
import org.phioster.sanctumd.ui.theme.SanctumdColors
import org.phioster.sanctumd.ui.theme.ThemeState
import org.phioster.sanctumd.ui.theme.ThemeStore

/**
 * The television entry point.
 *
 * A separate activity from [org.phioster.sanctumd.MainActivity] rather than a responsive layout:
 * the phone app is a homelab console with drawers, dialogs and long forms, none of which can be
 * driven by a five-button remote. This one does one job — browse a Jellyfin library and play it.
 *
 * It is the only activity registered for `LEANBACK_LAUNCHER`, so it is what an Android TV launcher
 * shows; phone launchers ignore that category and keep showing MainActivity. One APK, two front
 * doors, one shared network and playback layer.
 */
@UnstableApi
class TvActivity : ComponentActivity() {

    private val vm: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Read the theme synchronously before the first frame, exactly like the phone activity —
        // otherwise the UI paints in the default palette and then repaints.
        ThemeState.palette = ThemeStore.read(this)
        setContent {
            MaterialTheme(colorScheme = SanctumdColors) {
                TvApp(vm = vm, onExit = { finish() })
            }
        }
    }
}
