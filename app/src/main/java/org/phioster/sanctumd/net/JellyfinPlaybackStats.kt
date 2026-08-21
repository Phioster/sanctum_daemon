package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.JellyPlayEntry
import org.phioster.sanctumd.model.JellyPlaybackStats
import org.phioster.sanctumd.model.PlaybackKind
import org.phioster.sanctumd.model.ServiceConfig

/**
 * How Jellyfin delivered a file, read out of the Playback Reporting plugin's `PlaybackMethod`.
 *
 * This matters more here than on a normal server: the whole stack runs on a phone, and
 * transcoding is by far the most expensive thing it can be asked to do. Knowing *which* titles
 * force it is what lets a file be replaced on purpose instead of the server being blamed.
 *
 * The values are not a tidy enum — a transcode reports what it had to touch, e.g.
 * `Transcode (v:h264 a:direct)` means the video was re-encoded while the audio was passed
 * through. Measured against the live server on 2026-08-21.
 */
internal fun playbackKind(method: String?): PlaybackKind = when {
    method.isNullOrBlank() -> PlaybackKind.DIRECT
    method.startsWith("Transcode", ignoreCase = true) -> PlaybackKind.TRANSCODE
    method.equals("DirectStream", ignoreCase = true) -> PlaybackKind.STREAM
    // Anything unrecognised counts as direct: an unknown label is not evidence of a problem,
    // and reporting it as a transcode would put a title on the "replace me" list for nothing.
    else -> PlaybackKind.DIRECT
}

/**
 * Whether a transcode is worth counting against the server.
 *
 * Live TV is excluded, and that is not a detail: a `TvChannel` is transcoded **every single
 * time** by design — there is no original file to hand through. Counting those made the tile
 * report work nobody can avoid or act on. Measured on the live server: of 12 transcodes, 3 were
 * live TV, and the remaining 9 all came from third-party clients (JellyWatch TV, the official
 * Jellyfin apps) while Sanctumd itself caused none in 48 playbacks.
 */
internal fun countsAsTranscode(method: String?, itemType: String?): Boolean =
    playbackKind(method) == PlaybackKind.TRANSCODE && !itemType.equals("TvChannel", ignoreCase = true)

/** The part of a transcode label that says what was re-encoded, or "" when it says nothing. */
internal fun transcodeDetail(method: String?): String =
    method?.substringAfter('(', "")?.substringBefore(')')?.trim().orEmpty()

private const val ACTIVITY = "PlaybackActivity"

/** Seconds as reported, turned into hours for display. */
private fun hours(secs: String?): Double = (secs?.toDoubleOrNull() ?: 0.0) / 3600.0

/**
 * Everything the stats section shows, in four queries against the plugin's own table.
 *
 * The plugin exposes raw SQL through `submit_custom_query`, which is what makes this possible at
 * all — Jellyfin's normal API has no endpoint for "what did we actually watch". Needs the
 * Playback Reporting plugin; without it the endpoint 404s and the caller shows a hint.
 */
suspend fun jellyfinPlaybackStats(config: ServiceConfig): JellyPlaybackStats =
    withContext(Dispatchers.IO) {
        val token = jellyfinAccessToken(config)
        val api = jfApi(config, token)
        suspend fun q(sql: String) =
            api.playbackQuery(JfPlaybackQueryReq(sql, ReplaceUserId = false)).results

        val summary = q(
            "SELECT count(*), sum(PlayDuration), min(DateCreated) FROM $ACTIVITY",
        ).firstOrNull().orEmpty()

        // ItemType mitzählen, damit die Entscheidung "zählt das?" in Kotlin fällt, wo sie
        // getestet werden kann, statt in einer SQL-WHERE-Klausel zu verschwinden.
        val methods = q(
            "SELECT PlaybackMethod, count(*), ItemType FROM $ACTIVITY GROUP BY PlaybackMethod, ItemType",
        )
        val transcodes = methods
            .filter { countsAsTranscode(it.getOrNull(0), it.getOrNull(2)) }
            .sumOf { it.getOrNull(1)?.toIntOrNull() ?: 0 }

        // Grouped by ItemId, not by name: two files can share a title, and the id is also what
        // makes the row tappable.
        val top = q(
            "SELECT ItemName, ItemId, count(*), sum(PlayDuration) FROM $ACTIVITY " +
                "GROUP BY ItemId ORDER BY sum(PlayDuration) DESC LIMIT 8",
        ).map {
            JellyPlayEntry(
                label = it.getOrNull(0).orEmpty(),
                itemId = it.getOrNull(1).orEmpty(),
                plays = it.getOrNull(2)?.toIntOrNull() ?: 0,
                hours = hours(it.getOrNull(3)),
            )
        }

        val devices = q(
            "SELECT DeviceName, count(*), sum(PlayDuration) FROM $ACTIVITY " +
                "GROUP BY DeviceName ORDER BY sum(PlayDuration) DESC LIMIT 6",
        ).map {
            JellyPlayEntry(
                label = it.getOrNull(0).orEmpty(),
                plays = it.getOrNull(1)?.toIntOrNull() ?: 0,
                hours = hours(it.getOrNull(2)),
            )
        }

        val forced = q(
            "SELECT ItemName, ItemId, PlaybackMethod, count(*), ItemType FROM $ACTIVITY " +
                "WHERE PlaybackMethod LIKE 'Transcode%' GROUP BY ItemId ORDER BY count(*) DESC LIMIT 12",
        ).filter { countsAsTranscode(it.getOrNull(2), it.getOrNull(4)) }.take(6).map {
            JellyPlayEntry(
                label = it.getOrNull(0).orEmpty(),
                itemId = it.getOrNull(1).orEmpty(),
                plays = it.getOrNull(3)?.toIntOrNull() ?: 0,
                detail = transcodeDetail(it.getOrNull(2)),
            )
        }

        JellyPlaybackStats(
            plays = summary.getOrNull(0)?.toIntOrNull() ?: 0,
            hours = hours(summary.getOrNull(1)),
            since = summary.getOrNull(2).orEmpty().take(10),
            transcodes = transcodes,
            topTitles = top,
            devices = devices,
            forced = forced,
        )
    }
