package org.phioster.sanctumd.ui.seerr

import org.phioster.sanctumd.model.ServiceType

/**
 * Which service a Seerr request of this media type ends up in.
 *
 * Seerr calls its kinds "movie" and "tv"; Jellyfin calls the same things "Movie" and "Series".
 * Mapping them separately keeps each vocabulary where it belongs instead of translating one into
 * the other at the call site.
 */
internal fun seerrServiceTypeFor(mediaType: String): ServiceType? =
    when (mediaType.lowercase()) {
        "movie" -> ServiceType.RADARR
        "tv" -> ServiceType.SONARR
        else -> null
    }
