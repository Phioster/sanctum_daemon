package org.phioster.nexarr.model

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

/** A Jellyfin library (virtual folder), for per-user access control. */
data class JellyLibrary(
    val id: String,
    val name: String,
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

/** A browsable media item: a library, folder (series/album/season), or a playable leaf. */
data class JellyMediaItem(
    val id: String,
    val name: String,
    val kind: String, // "movies"/"tvshows"/"music" for libraries; else "Movie"/"Series"/"Episode"/"Audio"/"MusicAlbum"/…
    val subtitle: String,
    val posterUrl: String,
    val isFolder: Boolean,
    val progressPct: Float, // 0..1, for "continue watching" rows
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
