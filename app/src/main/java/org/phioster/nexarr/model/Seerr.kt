package org.phioster.nexarr.model

/** A media request in Seerr (Overseerr/Jellyseerr). */
data class SeerrRequestItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val status: String,
    val pending: Boolean,
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
)

/** A discover/trending browse item. */
data class SeerrDiscoverItem(
    val tmdbId: Int,
    val title: String,
    val year: String,
    val mediaType: String, // "movie" or "tv"
    val posterUrl: String,
    val status: String, // "available" / "processing" / "pending" / "" (not requested)
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
