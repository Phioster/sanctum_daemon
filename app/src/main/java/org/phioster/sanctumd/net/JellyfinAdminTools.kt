package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.phioster.sanctumd.model.ServiceConfig

/** A server API key: what the *arr apps and Seerr use to reach Jellyfin. */
data class JellyApiKey(val token: String, val app: String, val created: String, val lastUsed: String)

private fun jf(config: ServiceConfig, token: String) = jfApi(config, token)

private suspend fun call(op: String, success: String, block: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): String =
    destructive(op) {
        withContext(Dispatchers.IO) {
            try {
                okOr(block(), success)
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

suspend fun jellyfinCancelTask(config: ServiceConfig, taskId: String): String =
    call("cancel Jellyfin task $taskId", "cancelled") { jf(config, jellyfinAccessToken(config)).cancelTask(taskId) }

suspend fun jellyfinApiKeys(config: ServiceConfig): List<JellyApiKey> = withContext(Dispatchers.IO) {
    val o = jf(config, jellyfinAccessToken(config)).apiKeys()
    (o["Items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { k ->
        JellyApiKey(
            token = jsStr(k, "AccessToken").orEmpty(),
            app = jsStr(k, "AppName").orEmpty(),
            created = jsStr(k, "DateCreated").orEmpty().take(10),
            lastUsed = jsStr(k, "DateLastActivity").orEmpty().take(10),
        )
    }.filter { it.token.isNotBlank() }
}

suspend fun jellyfinCreateApiKey(config: ServiceConfig, app: String): String =
    call("create Jellyfin API key for '$app'", "key created") { jf(config, jellyfinAccessToken(config)).createApiKey(app.trim()) }

/** Revoking cuts off whatever uses the key: an *arr app or Seerr stops reaching Jellyfin. */
suspend fun jellyfinRevokeApiKey(config: ServiceConfig, token: String): String =
    call("revoke a Jellyfin API key", "key revoked") { jf(config, jellyfinAccessToken(config)).revokeApiKey(token) }

/**
 * A remote-control command for another client, named as in Jellyfin's GeneralCommandType
 * (VolumeUp, ToggleMute, GoHome, MoveUp, Select, Back, ToggleOsd, …).
 */
suspend fun jellyfinRemote(config: ServiceConfig, sessionId: String, command: String): String =
    call("send $command to session $sessionId", "sent") { jf(config, jellyfinAccessToken(config)).sessionCommand(sessionId, command) }

/** Splits a movie that Jellyfin merged from several files back into one item per file. */
suspend fun jellyfinSplitVersions(config: ServiceConfig, itemId: String): String =
    call("split Jellyfin versions of $itemId", "versions split") { jf(config, jellyfinAccessToken(config)).splitVersions(itemId) }

/** The UploadSubtitleDto: the file goes in as base64 text, the format is its extension. */
internal fun subtitleUploadBody(language: String, format: String, forced: Boolean, hearingImpaired: Boolean, bytes: ByteArray): JsonObject =
    buildJsonObject {
        put("Language", language)
        put("Format", format)
        put("IsForced", forced)
        put("IsHearingImpaired", hearingImpaired)
        put("Data", java.util.Base64.getEncoder().encodeToString(bytes))
    }

suspend fun jellyfinUploadSubtitle(
    config: ServiceConfig,
    itemId: String,
    language: String,
    format: String,
    forced: Boolean,
    hearingImpaired: Boolean,
    bytes: ByteArray,
): String = call("upload a subtitle to $itemId", "subtitle uploaded") {
    jf(config, jellyfinAccessToken(config)).uploadSubtitle(itemId, subtitleUploadBody(language, format, forced, hearingImpaired, bytes))
}

/** True when [available] is a later version than [installed], compared number by number. */
internal fun isNewerVersion(installed: String, available: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(installed)
    val b = parts(available)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return y > x
    }
    return false
}

/** Plugin GUIDs come with dashes from the catalog and without from /Plugins. */
private fun guidKey(g: String) = g.replace("-", "").lowercase()

/**
 * Installed plugins with a newer catalog version, by plugin id. Matched on the GUID, falling
 * back to the name for repositories that leave it out.
 */
internal fun pluginUpdates(
    installed: List<org.phioster.sanctumd.model.JellyPlugin>,
    catalog: List<org.phioster.sanctumd.model.JellyPackage>,
): Map<String, org.phioster.sanctumd.model.JellyPackage> = installed.mapNotNull { p ->
    val pkg = catalog.firstOrNull { it.guid.isNotBlank() && guidKey(it.guid) == guidKey(p.id) }
        ?: catalog.firstOrNull { it.name.equals(p.name, ignoreCase = true) }
    if (pkg != null && pkg.version.isNotBlank() && isNewerVersion(p.version, pkg.version)) p.id to pkg else null
}.toMap()

/** SetAudioStreamIndex / SetSubtitleStreamIndex as a GeneralCommand; index -1 turns subtitles off. */
internal fun trackCommandBody(audio: Boolean, index: Int): JsonObject = buildJsonObject {
    put("Name", if (audio) "SetAudioStreamIndex" else "SetSubtitleStreamIndex")
    put("Arguments", buildJsonObject { put("Index", index.toString()) })
}

suspend fun jellyfinSetTrack(config: ServiceConfig, sessionId: String, audio: Boolean, index: Int): String =
    call("switch ${if (audio) "audio" else "subtitle"} track on $sessionId", "switched") {
        jf(config, jellyfinAccessToken(config)).sessionGeneralCommand(sessionId, trackCommandBody(audio, index))
    }

/** A plugin repository: where the catalog and its updates come from. */
data class JellyRepo(val name: String, val url: String, val enabled: Boolean)

suspend fun jellyfinRepos(config: ServiceConfig): List<JellyRepo> = withContext(Dispatchers.IO) {
    jf(config, jellyfinAccessToken(config)).repositories().map {
        JellyRepo(jsStr(it, "Name").orEmpty(), jsStr(it, "Url").orEmpty(), jsBool(it, "Enabled") != false)
    }
}

internal fun reposBody(repos: List<JellyRepo>): JsonArray = kotlinx.serialization.json.buildJsonArray {
    repos.forEach { r ->
        add(buildJsonObject { put("Name", r.name); put("Url", r.url); put("Enabled", r.enabled) })
    }
}

/** Jellyfin only takes the whole list, so every change sends all repositories back. */
suspend fun jellyfinSaveRepos(config: ServiceConfig, repos: List<JellyRepo>): String =
    call("save Jellyfin plugin repositories", "repositories saved") {
        jf(config, jellyfinAccessToken(config)).setRepositories(reposBody(repos))
    }
