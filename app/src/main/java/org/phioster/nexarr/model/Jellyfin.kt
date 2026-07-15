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
    val name: String,
    val lastActivity: String,
    val admin: Boolean,
)
