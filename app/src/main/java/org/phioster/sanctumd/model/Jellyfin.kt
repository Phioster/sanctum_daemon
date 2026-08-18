package org.phioster.sanctumd.model

/** An active Jellyfin session (playback or idle). */
data class JellySession(
    val id: String,
    val user: String,
    val device: String,
    val client: String,
    val nowPlaying: String, // "" when idle
    val subtitle: String, // e.g. "Movie · 2021" or "SeriesName"
    val progressPct: Float, // 0..1
    val paused: Boolean,
    val canControl: Boolean,
    val lastActivity: String,
)

/** A Jellyfin user. */
data class JellyUser(
    val id: String,
    val name: String,
    val lastActivity: String,
    val admin: Boolean,
    val disabled: Boolean = false,
    val allowDownloads: Boolean = false,
    val enableAllFolders: Boolean = true,
    val enabledFolders: List<String> = emptyList(),
)

/** A Jellyfin library (virtual folder), for per-user access control and library management. */
data class JellyLibrary(
    val id: String,
    val name: String,
    val collectionType: String = "", // "movies"/"tvshows"/"music"/… or "" (mixed)
    val locations: List<String> = emptyList(), // folder paths on the server
)

/** A server log file (System/Logs). */
data class JellyLogFile(
    val name: String,
    val date: String, // last modified, "MM-dd HH:mm"
    val size: String, // human-readable, e.g. "1.2 MB"
)

/** An installed plugin. */
data class JellyPlugin(
    val id: String,
    val version: String,
    val name: String,
    val description: String,
    val status: String, // "Active" / "Disabled" / "Restart" / "Malfunctioned" / …
    val canUninstall: Boolean,
)

/** Live TV admin state: overall status plus configured tuners and guide providers. */
data class JellyLiveTv(
    val enabled: Boolean,
    val services: List<String>, // e.g. "Emby — Ok (2 tuners)"
    val tuners: List<JellyTuner>,
    val providers: List<JellyGuideProvider>,
)

/** A configured tuner host (M3U playlist or HDHomeRun). */
data class JellyTuner(
    val id: String,
    val name: String, // friendly name or the type
    val type: String, // "m3u" / "hdhomerun" / …
    val url: String,
)

/** A configured guide-data (EPG) provider. */
data class JellyGuideProvider(
    val id: String,
    val type: String, // "xmltv" / "schedulesdirect" / …
    val path: String, // file path or URL of the guide source
)

/** A Live TV channel with what's currently airing. */
data class JellyChannel(
    val id: String,
    val number: String,
    val name: String,
    val nowPlaying: String, // current program name, "" if unknown
)

/** A plugin available in the server's catalog (Packages). */
data class JellyPackage(
    val name: String,
    val guid: String,
    val description: String,
    val version: String, // latest available version
    val installed: Boolean = false,
)

/** Jellyfin server info for the admin dashboard. */
data class JellySystemInfo(
    val version: String,
    val serverName: String,
    val os: String,
)

/** A scheduled task on the server. */
data class JellyTask(
    val id: String,
    val name: String,
    val state: String, // "Idle" / "Running" / …
    val progress: Int, // 0..100 (when running)
    val lastResult: String, // "Completed" / "Failed" / ""
    val lastRun: String = "", // when it last finished, e.g. "07-15 14:03" or "" if never
)

/** An entry in the server activity log. */
data class JellyActivity(
    val name: String,
    val date: String,
    val severity: String,
    val overview: String,
)

/** A client device known to the server. */
data class JellyDevice(
    val name: String,
    val app: String,
    val user: String,
    val lastActivity: String,
)

/** One entry in the watch-time leaderboard (from the Playback Reporting plugin). */
data class JellyWatchStat(
    val name: String,
    val seconds: Long,
)

/** A browsable media item: a library, folder (series/album/season), or a playable leaf. */
data class JellyMediaItem(
    val id: String,
    val name: String,
    val kind: String, // "movies"/"tvshows"/"music" for libraries; else "Movie"/"Series"/"Episode"/"Audio"/"MusicAlbum"/…
    val subtitle: String,
    val posterUrl: String,
    val isFolder: Boolean,
    val progressPct: Float, // 0..1, for "continue watching" rows
    val number: Int? = null, // the item's own IndexNumber (season number for a Season, episode number for an Episode)
    val adult: Boolean = false, // official rating marks it as adult / XXX
    val played: Boolean = false, // fully watched (a Series/Season is played once every episode is)
    val unplayedCount: Int = 0, // folders: episodes still unwatched
    val favorite: Boolean = false,
)

/**
 * One track inside a media file, exactly as Jellyfin reports it.
 *
 * Everything is optional on the server side, so absent values arrive as empty/zero and the
 * UI simply leaves those rows out rather than printing "unknown".
 */
data class JellyStream(
    val type: String, // "Video" / "Audio" / "Subtitle"
    val codec: String = "",
    val profile: String = "",
    val language: String = "",
    val displayTitle: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Double = 0.0,
    val bitDepth: Int = 0,
    val bitrate: Int = 0,
    val channels: Int = 0,
    val channelLayout: String = "",
    val sampleRate: Int = 0,
    val videoRange: String = "",
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
)

/** The file behind a playable item: container, size and every track in it. */
data class JellyFileInfo(
    val container: String,
    val sizeBytes: Long,
    val path: String,
    val bitrate: Int,
    val streams: List<JellyStream>,
)

/** Full detail for a single media item. */
data class JellyMediaDetail(
    val id: String,
    val name: String,
    val overview: String,
    val posterUrl: String,
    val facts: List<Pair<String, String>>,
    val genres: String,
    val cast: List<ArrCastMember>,
    val kind: String = "", // "Movie"/"Episode"/"Video"/"Audio"/… — decides whether playback is offered
    val subtitle: String = "", // episodes: "SeriesName · S01E02" (used for downloads/notifications)
    val played: Boolean = false, // fully watched
    val unplayedCount: Int = 0, // folders: episodes still unwatched
    val favorite: Boolean = false,
    val fileInfo: JellyFileInfo? = null, // null for folders (Series/Season) and anything without a media source
    /** Tmdb/Imdb/Tvdb ids — how a Jellyfin item is matched to its Radarr/Sonarr entry exactly. */
    val providerIds: Map<String, String> = emptyMap(),
)

/**
 * One metadata candidate offered when re-identifying an item.
 *
 * [raw] is the provider's own result object, kept verbatim: Jellyfin wants it handed straight
 * back to pin the item, and re-assembling it from parsed fields would drop whatever the
 * provider sent that we did not model.
 */
data class JellyIdentifyCandidate(
    val name: String,
    val year: Int,
    val provider: String,
    val imageUrl: String,
    val raw: String,
)

/** One subtitle a provider offers for an item. */
data class JellySubtitle(
    val id: String,
    val provider: String,
    val name: String,
    val format: String,
    val downloads: Int,
    /** Made for this exact file — the one that will actually be in sync. */
    val hashMatch: Boolean,
    val forced: Boolean,
)
