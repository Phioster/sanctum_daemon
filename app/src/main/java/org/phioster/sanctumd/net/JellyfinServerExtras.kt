package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.phioster.sanctumd.model.ServiceConfig

/** An episode in the upcoming or missing list: which show, which episode, when it aired. */
data class JellyEpisodeLine(
    val id: String,
    val name: String,
    /** "Show · S01E02". */
    val where: String,
    /** yyyy-MM-dd, blank when the server has no date. */
    val date: String,
)

/** A server backup archive (Jellyfin 10.11+). */
data class JellyBackup(
    val path: String,
    val created: String,
    val serverVersion: String,
)

private fun JfItem.toEpisodeLine() = JellyEpisodeLine(
    id = Id,
    name = Name,
    where = jfSubtitle(this),
    date = PremiereDate.orEmpty().take(10),
)

/** Episodes of the user's shows that air next. */
suspend fun jellyfinUpcoming(config: ServiceConfig): List<JellyEpisodeLine> = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    api.upcoming(jellyfinResolveUserId(config, api)).Items.map { it.toEpisodeLine() }
}

/**
 * Aired episodes the server knows of but has no file for, newest first. Browsing hides these
 * on purpose (they are metadata only); this is the one place they are wanted.
 */
suspend fun jellyfinMissing(config: ServiceConfig): List<JellyEpisodeLine> = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    api.missingEpisodes(jellyfinResolveUserId(config, api)).Items.map { it.toEpisodeLine() }
}

internal fun backupOf(o: JsonObject) = JellyBackup(
    path = jsStr(o, "Path").orEmpty(),
    created = jsStr(o, "DateCreated").orEmpty().take(16).replace('T', ' '),
    serverVersion = jsStr(o, "ServerVersion").orEmpty(),
)

suspend fun jellyfinBackups(config: ServiceConfig): List<JellyBackup> = withContext(Dispatchers.IO) {
    jfApi(config, jellyfinAccessToken(config)).backups().map(::backupOf).sortedByDescending { it.created }
}

/**
 * The database always goes in; metadata, trickplay and subtitles are what make an archive big.
 * The call only returns once the archive is written, which can take minutes, hence its own
 * long read timeout instead of the shared 20 s.
 */
suspend fun jellyfinCreateBackup(config: ServiceConfig, metadata: Boolean, trickplay: Boolean, subtitles: Boolean): String =
    destructive("create a Jellyfin backup") {
        withContext(Dispatchers.IO) {
            try {
                val api = apiFor<JellyfinApi>(config, jellyfinAuth(jellyfinAccessToken(config)), readTimeoutSeconds = 600)
                val body = buildJsonObject {
                    put("Database", true)
                    put("Metadata", metadata)
                    put("Trickplay", trickplay)
                    put("Subtitles", subtitles)
                }
                okOr(api.createBackup(body), "backup created")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Restarts the server and rolls it back to [path]. Everything since that backup is lost. */
suspend fun jellyfinRestoreBackup(config: ServiceConfig, path: String): String =
    destructive("restore Jellyfin backup $path") {
        withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject { put("ArchiveFileName", path) }
                okOr(jfApi(config, jellyfinAccessToken(config)).restoreBackup(body), "restoring, the server restarts")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }
