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
    val posterUrl: String,
    val tmdbId: Int,
    val genres: String,
)

/** A cast member (resolved via a Seerr/Overseerr TMDB proxy). */
data class ArrCastMember(
    val name: String,
    val character: String,
    val profileUrl: String,
)

/** A candidate file from a manual-import scan; [rawJson] is reused to build the import command. */
data class ArrImportItem(
    val relativePath: String,
    val matchedTitle: String,
    val quality: String,
    val rejection: String,
    val importable: Boolean,
    val rawJson: String,
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
    val customFormats: String, // joined custom-format names, "" if none
    val approved: Boolean,
    val rejection: String, // all rejection reasons joined, "" if approved
)

/** System status + health + disk space for a Servarr app. */
data class ArrSystemInfo(
    val version: String,
    val health: List<Pair<String, String>>, // type -> message
    val disks: List<Pair<String, String>>, // path -> "free / total"
)

/** A history event in a Servarr app. */
data class ArrHistoryItem(
    val title: String,
    val eventType: String,
    val date: String,
    val quality: String,
)
