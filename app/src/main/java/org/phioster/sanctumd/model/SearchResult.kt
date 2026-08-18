package org.phioster.sanctumd.model

/** A single hit from the cross-service global search. */
data class SearchResult(
    val serviceId: String,
    val serviceLabel: String,
    val serviceType: ServiceType,
    val title: String,
    val subtitle: String, // year + status, e.g. "2021 · in library"
    val posterUrl: String,
    val year: Int = 0, // release year, 0 = unknown (for the year filter)
    val inLibrary: Boolean = false, // already present in the service (library/available/on Jellyfin)
    // Deep-link handles so a tap can open the object itself, not just the service:
    val libraryId: Long = 0, // arr: the library item id; >0 = already in the library
    val tmdbId: Int = 0, // Seerr: TMDB id for the media-detail dialog
    val mediaType: String = "", // Seerr: "movie" / "tv"
    val jellyItemId: String = "", // Jellyfin: item id for the media-detail dialog
    /** Pornography only (see [org.phioster.sanctumd.net.ADULT_RATINGS]); mainstream 18/R/NC-17
     *  stay false on purpose, so 18-rated horror keeps showing up. */
    val adult: Boolean = false,
)
