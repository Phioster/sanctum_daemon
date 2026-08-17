package org.phioster.sanctumd.model

/** A media request in Seerr (Overseerr/Jellyseerr). */
data class SeerrRequestItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val status: String,
    val pending: Boolean,
    val posterUrl: String = "",
)

/** An issue reported in Seerr. */
data class SeerrIssueItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val status: String,
)

/** A search result for creating a new request. */
data class SeerrSearchItem(
    val tmdbId: Int,
    val title: String,
    val year: String,
    val mediaType: String, // "movie" or "tv"
    val adult: Boolean = false, // TMDB adult (porn) flag
)

/**
 * One root folder a Seerr request can be steered into.
 *
 * The list comes from Seerr's own service config rather than from Radarr/Sonarr directly:
 * the value travels back to Seerr, so it has to be a path Seerr knows, and reading it here
 * works even when the *arr service itself is not configured in Sanctumd.
 */
data class SeerrRootFolder(
    val path: String,
    val serverId: Int,
    val isDefault: Boolean, // the server's activeDirectory — what a request uses when nothing is picked
)

/** A discover/trending browse item. */
data class SeerrDiscoverItem(
    val tmdbId: Int,
    val title: String,
    val year: String,
    val mediaType: String, // "movie" or "tv"
    val posterUrl: String,
    val status: String, // "available" / "processing" / "pending" / "" (not requested)
    val adult: Boolean = false, // TMDB adult (porn) flag
)

/** Full media detail for a Seerr movie/show (poster, cast, status). */
data class SeerrMediaDetail(
    val tmdbId: Int,
    val title: String,
    val year: String,
    val mediaType: String, // "movie" or "tv"
    val overview: String,
    val posterUrl: String,
    val facts: List<Pair<String, String>>,
    val genres: String,
    val status: String, // "available" / "processing" / … / "" (not requested)
    val cast: List<ArrCastMember>,
    val onWatchlist: Boolean = false,
)

/** A Seerr user, for the admin user list. */
data class SeerrUserInfo(
    val name: String,
    val email: String,
    val requestCount: Int,
)

/** A season of a TV show, for per-season requests. */
data class SeerrSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
)

/** A comment on an issue. */
data class SeerrComment(
    val author: String,
    val message: String,
    val date: String,
)

/** Full detail of an issue including comments. */
data class SeerrIssueDetail(
    val id: Int,
    val title: String,
    val type: String,
    val status: String, // "open" / "resolved"
    val description: String,
    val comments: List<SeerrComment>,
)
