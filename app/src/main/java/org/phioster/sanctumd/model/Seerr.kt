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
    val isDefault: Boolean, // the server's activeDirectory. What a request uses when nothing is picked
)

/** A quality profile a Seerr request can be steered to. */
data class SeerrProfile(val id: Int, val name: String)

/**
 * Everything the request dialog needs from Seerr's *arr configuration, fetched in one pass.
 *
 * Folders and profiles come from the same two calls, so asking for them separately would double
 * the round trips for no gain.
 */
data class SeerrServiceOptions(
    val serverId: Int,
    val rootFolders: List<SeerrRootFolder>,
    val profiles: List<SeerrProfile>,
    /** The server's own default. What a request uses when nothing is chosen. */
    val defaultProfileId: Int,
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
    /** Seerr's own media id, NOT the TMDB id. 0 when the title is not in Seerr's library yet;
     *  issues can only be opened against a title Seerr actually knows. */
    val mediaId: Int = 0,
    /** Where the title streams in the chosen region; empty when it streams nowhere there. */
    val availability: WatchAvailability = WatchAvailability.NONE,
)

/**
 * The two things Radarr/Sonarr cannot answer about a title and Seerr can: who is in it, and
 * where it streams. They come out of one Seerr detail call, so they travel together.
 */
data class SeerrTitleExtras(
    val cast: List<ArrCastMember> = emptyList(),
    val availability: WatchAvailability = WatchAvailability.NONE,
)

/**
 * What one request was actually made with, the settings behind a row.
 *
 * [rootFolder] and [profile] read "default" when the request took the server's own, which is
 * the common case and must not look like missing data.
 */
data class SeerrRequestDetail(
    val id: Int,
    val title: String,
    val status: String,
    val mediaType: String,
    val requestedBy: String,
    val created: String,
    val rootFolder: String,
    val profile: String,
    val seasonCount: Int,
    val is4k: Boolean,
    val pending: Boolean,
    /** Provider ids, for finding the same title in Radarr/Sonarr. 0 = not reported. */
    val tmdbId: Int = 0,
    val tvdbId: Int = 0,
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
