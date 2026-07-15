package org.phioster.nexarr.model

import kotlinx.serialization.Serializable

/**
 * A kind of dashboard widget. [service] is the service type it pulls from, so the add-card
 * picker can offer the matching configured services. New types are added here over time.
 */
@Serializable
enum class CardType(val label: String, val service: ServiceType) {
    JELLYFIN_SESSIONS("Active Sessions", ServiceType.JELLYFIN),
    JELLYFIN_RECENT("Recently Added", ServiceType.JELLYFIN),
    JELLYFIN_RESUME("Continue Watching", ServiceType.JELLYFIN),
    SEERR_REQUESTS("Recent Requests", ServiceType.SEERR),
    RADARR_QUEUE("Download Queue", ServiceType.RADARR),
    SONARR_QUEUE("Download Queue", ServiceType.SONARR),
    LIDARR_QUEUE("Download Queue", ServiceType.LIDARR),
    RADARR_MISSING("Missing / Wanted", ServiceType.RADARR),
    SONARR_MISSING("Missing / Wanted", ServiceType.SONARR),
    LIDARR_MISSING("Missing / Wanted", ServiceType.LIDARR),
}

/** A single widget on a dashboard tab: a card type bound to one configured service. */
@Serializable
data class DashCard(
    val id: String,
    val type: CardType,
    val serviceId: String,
    val title: String = "", // optional override; blank = use the type's label
)

/** A dashboard tab (a bottom-nav category) holding an ordered list of cards. */
@Serializable
data class DashTab(
    val id: String,
    val name: String,
    val cards: List<DashCard> = emptyList(),
)
