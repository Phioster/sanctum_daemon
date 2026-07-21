package org.phioster.sanctumd.service

import androidx.annotation.DrawableRes
import org.phioster.sanctumd.R
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.arrRssSync
import org.phioster.sanctumd.net.arrSearchAll
import org.phioster.sanctumd.net.jellyfinRestart
import org.phioster.sanctumd.net.runJellyfinScan
import org.phioster.sanctumd.net.runNzbgetPause
import org.phioster.sanctumd.net.runNzbgetResume
import org.phioster.sanctumd.net.runProwlarrTestAll

/** A one-tap action a service offers. VM-free, so widgets and the dashboard share it. */
class ServiceAction(val label: String, val run: suspend (ServiceConfig) -> String)

/**
 * Everything about a service type that is not its screen.
 *
 * Before this, each new service type meant hunting down the same facts in several
 * places: the logo lived in three separate `when` blocks (app, status widget, calendar
 * widget) and the quick-action table in two. Adding one was a scavenger hunt whose
 * misses only showed up as a non-exhaustive `when` breaking the build - four CI runs
 * were lost that way. Now a service is described once, here.
 *
 * Composable screens stay out on purpose: a `when` over the type in one place is
 * clearer than composable references inside a data class.
 */
class ServiceSpec(
    val type: ServiceType,
    @DrawableRes val logoRes: Int,
    val actions: List<ServiceAction> = emptyList(),
)

object ServiceRegistry {

    private val specs: Map<ServiceType, ServiceSpec> = listOf(
        ServiceSpec(
            ServiceType.JELLYFIN, R.drawable.svc_jellyfin,
            listOf(
                ServiceAction("Scan libraries") { runJellyfinScan(it) },
                ServiceAction("Restart server") { jellyfinRestart(it) },
            ),
        ),
        ServiceSpec(ServiceType.RADARR, R.drawable.svc_radarr, arrActions()),
        ServiceSpec(ServiceType.SONARR, R.drawable.svc_sonarr, arrActions()),
        ServiceSpec(ServiceType.LIDARR, R.drawable.svc_lidarr, arrActions()),
        ServiceSpec(
            ServiceType.PROWLARR, R.drawable.svc_prowlarr,
            listOf(ServiceAction("Test all indexers") { runProwlarrTestAll(it) }),
        ),
        ServiceSpec(
            ServiceType.NZBGET, R.drawable.svc_nzbget,
            listOf(
                ServiceAction("Pause queue") { runNzbgetPause(it) },
                ServiceAction("Resume queue") { runNzbgetResume(it) },
            ),
        ),
        ServiceSpec(ServiceType.SEERR, R.drawable.svc_seerr),
        ServiceSpec(ServiceType.NTFY, R.drawable.svc_ntfy),
        ServiceSpec(ServiceType.SHORTCUTS, R.drawable.svc_shortcuts),
    ).associateBy { it.type }

    private fun arrActions() = listOf(
        ServiceAction("Search all missing") { arrSearchAll(it, false) },
        ServiceAction("RSS sync") { arrRssSync(it) },
    )

    /** Every type has an entry — ServiceRegistryTest fails the build if one is missing. */
    operator fun get(type: ServiceType): ServiceSpec = specs.getValue(type)

    @DrawableRes
    fun logoRes(type: ServiceType): Int = get(type).logoRes

    fun actions(type: ServiceType): List<ServiceAction> = get(type).actions

    internal fun registered(): Set<ServiceType> = specs.keys
}
