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

/** Full detail of a movie/series for the detail screen. */
data class ArrDetail(
    val id: Int,
    val title: String,
    val year: Int,
    val overview: String,
    val monitored: Boolean,
    val status: String,
    val sizeMb: Long,
    val facts: List<Pair<String, String>>, // label -> value chips
)

/** A Sonarr episode (grouped by season in the UI). */
data class ArrEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val hasFile: Boolean,
    val monitored: Boolean,
    val airDate: String,
)

/** An interactive-search release from a Servarr app. */
data class ArrRelease(
    val guid: String,
    val indexerId: Int,
    val title: String,
    val indexer: String,
    val sizeMb: Long,
    val protocol: String,
    val seeders: Int?,
    val ageDays: Int,
    val quality: String,
    val score: Int,
    val approved: Boolean,
    val rejection: String,
)

/** A history event in a Servarr app. */
data class ArrHistoryItem(
    val title: String,
    val eventType: String,
    val date: String,
    val quality: String,
)
