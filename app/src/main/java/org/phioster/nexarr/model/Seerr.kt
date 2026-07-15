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
