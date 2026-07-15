package org.phioster.nexarr.model

import kotlinx.serialization.Serializable

/** User preferences for background notifications. */
@Serializable
data class NotifySettings(
    val enabled: Boolean = false,
    val newMedia: Boolean = true, // Jellyfin: newly added items
    val imports: Boolean = true, // Radarr/Sonarr/Lidarr: download imported
    val requests: Boolean = true, // Seerr: new pending requests
    val health: Boolean = false, // Radarr/Sonarr/Lidarr: health warnings/errors
    val intervalMin: Int = 15, // background check interval (WorkManager min is 15)
)
