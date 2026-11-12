package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.JellyWatchedTitle
import org.phioster.sanctumd.model.JellyWatcher
import org.phioster.sanctumd.model.ServiceConfig

/**
 * Everybody's watch time, longest first. `UserId` comes back as the name (the plugin swaps that
 * column), the aliased copy keeps the raw id the per-person query needs. Measured 2026-11-12.
 */
internal const val WATCHERS_SQL =
    "SELECT UserId, UserId AS RawId, count(*), sum($SANE_DURATION), max(DateCreated) FROM PlaybackActivity " +
        "GROUP BY UserId ORDER BY sum($SANE_DURATION) DESC"

/** The id goes into the SQL text, so anything that is not a plain Jellyfin id gets no query. */
internal fun watcherTitlesSql(userId: String): String? {
    if (userId.isBlank() || !userId.all { it.isLetterOrDigit() || it == '-' }) return null
    return "SELECT ItemName, ItemId, ItemType, count(*), sum($SANE_DURATION), max(DateCreated) FROM PlaybackActivity " +
        "WHERE UserId = '$userId' GROUP BY ItemId ORDER BY sum($SANE_DURATION) DESC LIMIT 300"
}

internal fun watcherRows(rows: List<List<String>>): List<JellyWatcher> = rows.mapNotNull { r ->
    val name = r.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    JellyWatcher(
        name = name,
        userId = r.getOrNull(1).orEmpty(),
        plays = r.getOrNull(2)?.toIntOrNull() ?: 0,
        seconds = r.getOrNull(3)?.toDoubleOrNull()?.toLong() ?: 0L,
        lastPlayed = r.getOrNull(4).orEmpty().take(10),
    )
}

internal fun watchedTitleRows(rows: List<List<String>>): List<JellyWatchedTitle> = rows.map { r ->
    JellyWatchedTitle(
        name = r.getOrNull(0).orEmpty(),
        itemId = r.getOrNull(1).orEmpty(),
        type = r.getOrNull(2).orEmpty(),
        plays = r.getOrNull(3)?.toIntOrNull() ?: 0,
        seconds = r.getOrNull(4)?.toDoubleOrNull()?.toLong() ?: 0L,
        lastPlayed = r.getOrNull(5).orEmpty().take(10),
    )
}

/** Needs the Playback Reporting plugin; throws without it and the caller shows a hint. */
suspend fun jellyfinWatchers(config: ServiceConfig): List<JellyWatcher> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    watcherRows(jfApi(config, token).playbackQuery(JfPlaybackQueryReq(WATCHERS_SQL, ReplaceUserId = true)).results)
}

suspend fun jellyfinWatcherTitles(config: ServiceConfig, userId: String): List<JellyWatchedTitle> = withContext(Dispatchers.IO) {
    val sql = watcherTitlesSql(userId) ?: return@withContext emptyList()
    val token = jellyfinAccessToken(config)
    watchedTitleRows(jfApi(config, token).playbackQuery(JfPlaybackQueryReq(sql, ReplaceUserId = false)).results)
}
