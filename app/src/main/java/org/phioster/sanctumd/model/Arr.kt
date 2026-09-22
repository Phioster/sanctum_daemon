package org.phioster.sanctumd.model

/** A missing/wanted item across Radarr (movie), Sonarr (episode), Lidarr (album). */
data class ArrMissingItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    /** The library item this row belongs to (series / movie / artist). 0 when unknown. */
    val itemId: Int = 0,
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
    /** Downloaded but the app refuses to import it. Needs a hand, and leaves the file lying twice. */
    val blocked: Boolean = false,
    /**
     * The folder the download landed in. Empty when the service did not report one, and then
     * there is nothing to open. The manual-import shortcut must not be offered.
     */
    val outputPath: String = "",
    /** The library item this row belongs to (series / movie / artist). 0 when unknown. */
    val itemId: Int = 0,
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

/**
 * A lookup result for adding; [raw] is the original JSON reused as the add body.
 *
 * Everything below [raw] exists so a title can be looked at before it is added. The lookup
 * answer is the same record the service's own web UI renders, so none of it costs a second
 * request. The list simply used to throw it away and show a year.
 */
data class ArrLookupItem(
    val title: String,
    val year: Int,
    val raw: String,
    val overview: String = "",
    val posterUrl: String = "",
    val genres: String = "",
    /** label -> value chips, the same shape [ArrDetail.facts] uses. */
    val facts: List<Pair<String, String>> = emptyList(),
    /** The service's own id once it holds this title; 0 while it would be a new entry. */
    val libraryId: Int = 0,
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

/**
 * A TMDB collection as Radarr knows it. The whole film run, not only the parts you own.
 *
 * [qualityProfileId] and [rootFolderPath] come from the collection itself: Radarr already says
 * where films of this run belong, so adding a missing one needs no further questions.
 */
data class ArrCollection(
    val id: Int,
    val title: String,
    val tmdbId: Int,
    val monitored: Boolean,
    val qualityProfileId: Int,
    val rootFolderPath: String,
    val movies: List<ArrCollectionMovie>,
) {
    /** Films of the run that are neither owned nor deliberately excluded. */
    val missing: List<ArrCollectionMovie> get() = movies.filter { !it.existing && !it.excluded }
}

/** One film of an [ArrCollection]. */
data class ArrCollectionMovie(
    val tmdbId: Int,
    val title: String,
    val year: Int,
    val existing: Boolean,
    val excluded: Boolean,
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
    val about: List<Pair<String, String>> = emptyList(), // label -> value, as the service reports it
)

/** A history event in a Servarr app. */
data class ArrHistoryItem(
    /** The history entry's own id. What blocking a past release is addressed to. */
    val id: Int,
    val title: String,
    val eventType: String,
    val date: String,
    val quality: String,
    /** The library item this row belongs to (series / movie / artist). 0 when unknown. */
    val itemId: Int = 0,
)

/**
 * An indexer as Sonarr/Radarr/Lidarr sees it. Which is not the same view Prowlarr has.
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
    /** Locked out right now, per the service's own health check. */
    val failing: Boolean = false,
    /**
     * The health check could not be read, so nothing is known about this indexer's state.
     * Kept distinct from [failing] and from healthy on purpose: reporting an unreachable
     * status source as "fine" is how the previous version lied with a straight face.
     */
    val statusUnknown: Boolean = false,
)

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

/** A release the app was told never to grab again. */
data class ArrBlocklistItem(
    val id: Int,
    val title: String,
    val date: String,
)

/**
 * What a Servarr app makes of a release name: the quality it parses out, and what its own custom
 * formats score it at.
 *
 * The indexer's listing says none of this. A name can look like a clean 1080p Bluray and still be
 * scored far below zero because a marker in it ("MD" for mic-dubbed, say) is one the profile
 * penalises. That is exactly the judgement worth seeing before picking a file.
 */
data class ArrParsedRelease(
    val quality: String,
    val score: Int,
    val formats: String, // joined custom-format names, "" if none
    val languages: String,
    val matchedTitle: String, // which title the app thinks this release belongs to
)
