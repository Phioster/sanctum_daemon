package org.phioster.sanctumd.demo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.phioster.sanctumd.model.DashTab
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * A busy made-up server, for screenshots and for trying the app without one. Every request to
 * [HOST] is answered inside the app by [DemoInterceptor]; nothing about the user's own setup is
 * shown, and nothing of it is changed: the real service list and dashboard stay stored untouched
 * and are back the moment demo mode is switched off.
 */
object DemoMode {
    /** ".invalid" is reserved and never resolves, so a slip past the interceptor can't reach anyone. */
    const val HOST = "demo.sanctumd.invalid"

    private const val PREFS = "sanctumd_demo"
    private const val KEY = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val isOn: Boolean get() = _enabled.value

    internal lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
        _enabled.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        _enabled.value = on
    }

    private fun svc(id: String, type: ServiceType, label: String, path: String, extra: ServiceConfig.() -> ServiceConfig = { this }) =
        ServiceConfig(id = id, type = type, label = label, baseUrl = "https://$HOST/$path/", apiKey = "demo").extra()

    /** The made-up server's services, in the order a well-kept list would have them. */
    val services: List<ServiceConfig> by lazy {
        listOf(
            svc("demo-jellyfin", ServiceType.JELLYFIN, "Jellyfin", "jellyfin") { copy(useLogin = false, userId = DemoWorld.ADMIN_ID) },
            svc("demo-seerr", ServiceType.SEERR, "Seerr", "seerr"),
            svc("demo-radarr", ServiceType.RADARR, "Radarr", "radarr"),
            svc("demo-sonarr", ServiceType.SONARR, "Sonarr", "sonarr"),
            svc("demo-lidarr", ServiceType.LIDARR, "Lidarr", "lidarr"),
            svc("demo-prowlarr", ServiceType.PROWLARR, "Prowlarr", "prowlarr"),
            svc("demo-nzbget", ServiceType.NZBGET, "NZBGet", "nzbget") { copy(username = "demo", password = "demo") },
            svc("demo-ntfy", ServiceType.NTFY, "ntfy", "ntfy") { copy(topics = listOf("homelab")) },
        )
    }

    /**
     * The user's own dashboard with every card pointed at the demo service of the same kind, so
     * the demo looks like their app rather than a stock layout. Cards for kinds the demo lacks go.
     */
    fun tabsFor(real: List<DashTab>, realServices: List<ServiceConfig>): List<DashTab> {
        val typeOf = realServices.associate { it.id to it.type }
        val demoOf = services.associateBy { it.type }
        val mapped = real.map { tab ->
            tab.copy(cards = tab.cards.mapNotNull { card ->
                if (card.serviceId.isBlank()) return@mapNotNull card
                val type = typeOf[card.serviceId] ?: card.type.service ?: return@mapNotNull null
                demoOf[type]?.let { card.copy(serviceId = it.id) }
            })
        }
        return mapped.ifEmpty { DemoLayout.defaultTabs() }
    }
}
