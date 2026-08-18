package org.phioster.sanctumd.model

/** A missing/wanted item across Radarr (movie), Sonarr (episode), Lidarr (album). */
data class ArrMissingItem(
    val id: Int,
    val title: String,
    val subtitle: String,
)

/** An upcoming release from a Servarr calendar (movie/episode/album). */
data class ArrCalendarItem(
    val title: String,
    val subtitle: String,
    val date: String,
    val hasFile: Boolean,
    /** id of the parent library item (movie / series / artist) for opening detail. */
    val itemId: Int = 0,
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
    val posterUrl: String = "",
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

/** A Lidarr album (shown in the artist detail screen). */
data class ArrAlbum(
    val id: Int,
    val title: String,
    val year: String,
    val trackCount: Int,
    val trackFileCount: Int,
    val monitored: Boolean,
)

/** A single track on a Lidarr album. */
data class ArrTrack(
    val trackNumber: String,
    val title: String,
    val duration: String, // "3:45"
    val hasFile: Boolean,
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

/**
 * An indexer as Sonarr/Radarr/Lidarr sees it — which is not the same view Prowlarr has.
 *
 * Each *arr app keeps its own failure counter: when Prowlarr answers a query with
 * "429 Indexer is disabled till …", the app records that as a failure and locks the indexer
 * out on its own side, with its own escalating backoff. So an indexer can be healthy in
 * Prowlarr and still dead in Sonarr, which is why [disabledTill] is worth showing: it is the
 * difference between "recovers on its own at 02:03" and "needs a hand".
 */
data class ArrIndexerItem(
    val id: Int,
    val name: String,
    val protocol: String, // "usenet" or "torrent"
    val priority: Int,
    val enableRss: Boolean,
    val enableAutomaticSearch: Boolean,
    val enableInteractiveSearch: Boolean,
    val disabledTill: String? = null, // ISO timestamp; null when the indexer is healthy
) {
    val failing: Boolean get() = disabledTill != null
}

/** One entry when browsing the server's filesystem (manual import). */
data class ArrFsEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
)

/** A folder's contents. [parent] is null at the top, where there is nowhere to go up to. */
data class ArrFsListing(
    val parent: String?,
    val entries: List<ArrFsEntry>,
)
