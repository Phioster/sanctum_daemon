package org.phioster.nexarr.widget

import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceType
import org.phioster.nexarr.net.arrRssSync
import org.phioster.nexarr.net.arrSearchAll
import org.phioster.nexarr.net.jellyfinRestart
import org.phioster.nexarr.net.runJellyfinScan
import org.phioster.nexarr.net.runNzbgetPause
import org.phioster.nexarr.net.runNzbgetResume
import org.phioster.nexarr.net.runProwlarrTestAll

/** A quick action a widget can run directly (no ViewModel) — hits the net layer. */
class WidgetAction(val label: String, val run: suspend (ServiceConfig) -> String)

/** VM-free mirror of the dashboard's quick actions, usable from widget callbacks. */
object WidgetActions {
    fun forType(type: ServiceType): List<WidgetAction> = when (type) {
        ServiceType.JELLYFIN -> listOf(
            WidgetAction("Scan libraries") { runJellyfinScan(it) },
            WidgetAction("Restart server") { jellyfinRestart(it) },
        )
        ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> listOf(
            WidgetAction("Search all missing") { arrSearchAll(it, false) },
            WidgetAction("RSS sync") { arrRssSync(it) },
        )
        ServiceType.PROWLARR -> listOf(
            WidgetAction("Test all indexers") { runProwlarrTestAll(it) },
        )
        ServiceType.NZBGET -> listOf(
            WidgetAction("Pause queue") { runNzbgetPause(it) },
            WidgetAction("Resume queue") { runNzbgetResume(it) },
        )
        ServiceType.SEERR, ServiceType.NTFY, ServiceType.SHORTCUTS -> emptyList()
    }
}
