package org.phioster.sanctumd.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.phioster.sanctumd.model.ServiceType

/**
 * How a scanned manual-import row is read, and what the import command is told about it.
 *
 * This used to sit inline in the scan and execute calls, where it could not be tested — and that
 * is exactly where a Sonarr row once carried a Radarr `movie` object, producing a command with
 * neither `seriesId` nor `episodeIds`: a button that looked like it worked and did nothing.
 *
 * Each service names the same ideas differently. Radarr has one `movie`; Sonarr has a `series`
 * plus the `episodes` the file covers; Lidarr has an `artist` (under `artistName`, not `title`),
 * an `album`, the `albumReleaseId` that says *which edition* of that album, and the `tracks`.
 */
private fun sub(o: JsonObject, key: String) = o[key] as? JsonObject

private fun ids(o: JsonObject, key: String): List<Int> =
    (o[key] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { e -> jsInt(e, "id") } }.orEmpty()

/** The human label for a scanned row: what the server thinks this file is. */
internal fun importMatchLabel(type: ServiceType, o: JsonObject): String = when (type) {
    ServiceType.SONARR -> {
        val series = sub(o, "series")?.let { jsStr(it, "title") }
        val eps = (o["episodes"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.joinToString(",") { "S%02dE%02d".format(jsInt(it, "seasonNumber") ?: 0, jsInt(it, "episodeNumber") ?: 0) }
        listOfNotNull(series?.takeIf { it.isNotBlank() }, eps?.takeIf { it.isNotBlank() }).joinToString(" ")
    }
    ServiceType.LIDARR -> {
        val artist = sub(o, "artist")?.let { jsStr(it, "artistName") }
        val album = sub(o, "album")?.let { jsStr(it, "title") }
        listOfNotNull(artist?.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() }).joinToString(" — ")
    }
    else -> sub(o, "movie")?.let { jsStr(it, "title") }.orEmpty()
}

/** Whether the row carries everything its own service needs to import the file. */
internal fun importHasMatch(type: ServiceType, o: JsonObject): Boolean = when (type) {
    ServiceType.SONARR -> sub(o, "series") != null && ids(o, "episodes").isNotEmpty()
    // Without tracks there is nothing to attach the file to, so an album match alone is not enough.
    ServiceType.LIDARR -> sub(o, "artist") != null && sub(o, "album") != null && ids(o, "tracks").isNotEmpty()
    else -> sub(o, "movie") != null
}

/** One entry of the ManualImport command's `files` array. */
internal fun importFileBody(type: ServiceType, o: JsonObject): JsonObject = buildJsonObject {
    jsStr(o, "path")?.let { put("path", it) }
    jsStr(o, "folderName")?.let { put("folderName", it) }
    o["quality"]?.let { put("quality", it) }
    when (type) {
        ServiceType.SONARR -> {
            sub(o, "series")?.let { s -> jsInt(s, "id")?.let { put("seriesId", it) } }
            putJsonArray("episodeIds") { ids(o, "episodes").forEach { add(it) } }
        }
        ServiceType.LIDARR -> {
            sub(o, "artist")?.let { a -> jsInt(a, "id")?.let { put("artistId", it) } }
            sub(o, "album")?.let { a -> jsInt(a, "id")?.let { put("albumId", it) } }
            jsInt(o, "albumReleaseId")?.let { put("albumReleaseId", it) }
            putJsonArray("trackIds") { ids(o, "tracks").forEach { add(it) } }
            // Lidarr would otherwise be free to swap the album's release edition during the
            // import, which silently changes what the user picked.
            put("disableReleaseSwitching", true)
        }
        else -> sub(o, "movie")?.let { m -> jsInt(m, "id")?.let { put("movieId", it) } }
    }
}

/**
 * Whether a scanned row may be imported despite what the server said about it.
 *
 * Lidarr judges each file on its own, so every file of a complete album is reported as
 * "Has missing tracks" — the other tracks are missing *from that one file*. Measured against a
 * live Lidarr 3.1.3 (2026-08-20): all 20 rows of a complete, correctly matched album carried it.
 * Treating it as a blocker would ship a manual import that can never import anything, so it is a
 * warning here and the row stays selectable. Every other rejection still blocks, and for Radarr
 * and Sonarr nothing changes.
 */
internal fun importAllowed(type: ServiceType, rejections: List<String>): Boolean =
    if (type == ServiceType.LIDARR) {
        rejections.none { !it.equals(LIDARR_PER_FILE_WARNING, ignoreCase = true) }
    } else {
        rejections.isEmpty()
    }

private const val LIDARR_PER_FILE_WARNING = "Has missing tracks"
