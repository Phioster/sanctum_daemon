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
)
