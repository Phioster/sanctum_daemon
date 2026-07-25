package org.phioster.sanctumd.model

import kotlinx.serialization.Serializable

/**
 * A kind of dashboard widget. [service] is the service type it pulls from, so the add-card
 * picker can offer the matching configured services. New types are added here over time.
 */
@Serializable
enum class CardType(val label: String, val service: ServiceType?) {
    SECTION("Section", null),
    QUICKBUTTONS("Quick Buttons", null),
    UNIFIED_CALENDAR("Universal calendar", null),
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
    RADARR_CALENDAR("Coming Soon", ServiceType.RADARR),
    SONARR_CALENDAR("Airing Next", ServiceType.SONARR),
    LIDARR_CALENDAR("Coming Soon", ServiceType.LIDARR),
    RADARR_HISTORY("Recently Downloaded", ServiceType.RADARR),
    SONARR_HISTORY("Recently Downloaded", ServiceType.SONARR),
    LIDARR_HISTORY("Recently Downloaded", ServiceType.LIDARR),
    NZBGET_QUEUE("Active Downloads", ServiceType.NZBGET),
    NZBGET_HISTORY("Recently Downloaded", ServiceType.NZBGET),
    SEERR_TRENDING("Trending", ServiceType.SEERR),
    SEERR_POPULAR_MOVIES("Popular Movies", ServiceType.SEERR),
    SEERR_POPULAR_TV("Popular Shows", ServiceType.SEERR),
    RADARR_HEALTH("Health", ServiceType.RADARR),
    SONARR_HEALTH("Health", ServiceType.SONARR),
    LIDARR_HEALTH("Health", ServiceType.LIDARR),
    JELLYFIN_STATS("Statistics", ServiceType.JELLYFIN),
    JELLYFIN_TOP("Watch Leaderboard", ServiceType.JELLYFIN),
    RADARR_STATS("Statistics", ServiceType.RADARR),
    SONARR_STATS("Statistics", ServiceType.SONARR),
    LIDARR_STATS("Statistics", ServiceType.LIDARR),
    PROWLARR_STATS("Statistics", ServiceType.PROWLARR),
    NZBGET_STATS("Statistics", ServiceType.NZBGET),
    SEERR_STATS("Statistics", ServiceType.SEERR),
    SHORTCUTS("Shortcuts", ServiceType.SHORTCUTS),
}

/** A single widget on a dashboard tab: a card type bound to one configured service. */
@Serializable
data class DashCard(
    val id: String,
    val type: CardType,
    val serviceId: String,
    val title: String = "", // optional override; blank = use the type's label
    val count: Int = 8, // how many entries the card shows
    val accent: Long = 0, // custom accent ARGB; 0 = use the service's default colour
    val icon: String = "", // optional header icon key (see tabIcon); blank = none
    val posterSize: String = "", // "small" | "large" | "" (medium) for poster-row cards
    val background: Boolean = false, // show a random shown item's art as a Ken-Burns card background
    val theme: String = "", // "" (flat) | "solid" | "glass" — card container look
    val density: String = "", // "compact" | "" (normal) | "detail" — row spacing / detail level
)

/** Per-row appearance of the Jellyfin Media home (Continue Watching / Recently Added / Libraries),
 *  keyed by row id. A subset of [DashCard]'s styling that makes sense for fixed media rows. */
@Serializable
data class MediaRowStyle(
    val accent: Long = 0, // custom accent ARGB; 0 = service default
    val posterSize: String = "", // "small" | "" (medium) | "large" — poster rows only
    val background: Boolean = false, // Ken-Burns fanart background behind the row
    val hidden: Boolean = false, // hide the row entirely
)

/** A dashboard tab (a bottom-nav category) holding an ordered list of cards. */
@Serializable
data class DashTab(
    val id: String,
    val name: String,
    val cards: List<DashCard> = emptyList(),
    val icon: String = "", // icon key (see tabIcon in UI); blank = default
    val accent: Long = 0, // custom tab accent ARGB; 0 = MatrixGreen default
)
