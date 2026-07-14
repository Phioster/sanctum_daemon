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

/** A library entry (movie/series/artist). */
data class ArrLibraryItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val year: Int,
    val sizeMb: Long,
)

/** A lookup result for adding; [raw] is the original JSON reused as the add body. */
data class ArrLookupItem(
    val title: String,
    val year: Int,
    val raw: String,
)

/** A Servarr quality profile. */
data class ArrProfile(val id: Int, val name: String)
