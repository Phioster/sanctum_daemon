package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.JellyPlayEntry
import org.phioster.sanctumd.model.JellyPlaybackStats
import org.phioster.sanctumd.model.PlaybackKind
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.TranscodeCost

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

/**
 * What the transcode actually cost, read out of the `v:`/`a:` halves of the label.
 *
 * `Transcode (v:direct a:direct)` re-encoded nothing — only the container changed, which is what
 * a browser gets handed when it cannot read mkv. Lumping that in with a full h264+aac re-encode
 * made the tile alarm about work the server never did. A label whose detail cannot be read counts
 * as the worst case: guessing "cheap" would hide a real cost.
 */
internal fun transcodeCost(method: String?): TranscodeCost {
    if (playbackKind(method) != PlaybackKind.TRANSCODE) return TranscodeCost.NONE
    val parts = transcodeDetail(method).split(' ').filter { it.isNotBlank() }
    fun stream(prefix: String) =
        parts.firstOrNull { it.startsWith(prefix, ignoreCase = true) }?.substringAfter(':')?.trim()
    val video = stream("v:")
    val audio = stream("a:")
    if (video == null && audio == null) return TranscodeCost.FULL
    val videoEncoded = video != null && !video.equals("direct", ignoreCase = true)
    val audioEncoded = audio != null && !audio.equals("direct", ignoreCase = true)
    return when {
        videoEncoded && audioEncoded -> TranscodeCost.FULL
        videoEncoded -> TranscodeCost.VIDEO
        audioEncoded -> TranscodeCost.AUDIO
        else -> TranscodeCost.REMUX
    }
}

/** The two numbers the tile shows, counted apart. */
internal data class TranscodeTally(val reencoded: Int, val remuxed: Int)

/**
 * Adds up grouped `(method, count, itemType)` rows into [TranscodeTally].
 *
 * Kept pure and separate from the query so both halves of the split can be tested without a
 * server — the counting is where a wrong bucket would go unnoticed.
 */
internal fun tallyTranscodes(rows: List<Triple<String?, Int, String?>>): TranscodeTally {
    var reencoded = 0
    var remuxed = 0
    for ((method, count, itemType) in rows) {
        if (!countsAsTranscode(method, itemType)) continue
        if (transcodeCost(method) == TranscodeCost.REMUX) remuxed += count else reencoded += count
    }
    return TranscodeTally(reencoded, remuxed)
}

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
        val tally = tallyTranscodes(
            methods.map {
                Triple(it.getOrNull(0), it.getOrNull(1)?.toIntOrNull() ?: 0, it.getOrNull(2))
            },
        )

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
        ).filter {
            // A remux forces nothing worth replacing a file over, so it does not belong on a list
            // headed "forces transcoding" — it is counted in its own tile instead.
            countsAsTranscode(it.getOrNull(2), it.getOrNull(4)) &&
                transcodeCost(it.getOrNull(2)) != TranscodeCost.REMUX
        }.take(6).map {
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
            transcodes = tally.reencoded,
            remuxes = tally.remuxed,
            topTitles = top,
            devices = devices,
            forced = forced,
        )
    }
