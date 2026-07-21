package org.phioster.sanctumd.model

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

    // Live push: Sanctumd subscribes directly to a topic on the user's ntfy server.
    val live: Boolean = false,
    val ntfyServer: String = "", // e.g. https://ntfy.sh or https://ntfy.myhomelab.xyz
    val ntfyTopic: String = "", // e.g. Homelab
    val ntfyToken: String = "", // optional ntfy access token (tk_...)
)
