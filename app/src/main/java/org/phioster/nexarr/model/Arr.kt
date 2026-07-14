package org.phioster.nexarr.model

/** A missing/wanted item across Radarr (movie), Sonarr (episode), Lidarr (album). */
data class ArrMissingItem(
    val id: Int,
    val title: String,
    val subtitle: String,
)

/** A download in a Servarr app's queue. */
data class ArrQueueItem(
    val id: Int,
    val title: String,
    val status: String,
    val progress: Float,
)
