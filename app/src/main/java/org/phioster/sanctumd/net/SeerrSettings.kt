package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.phioster.sanctumd.model.ServiceConfig

/**
 * The editable part of Seerr's General settings, by the names Seerr 3 uses (read off a live
 * instance: the published spec predates most of them). Region and language codes are blank for
 * "all"; [originalLanguage] and [blocklistLanguage] may hold several codes joined by "|".
 */
data class SeerrMainSettings(
    val applicationTitle: String,
    val applicationUrl: String,
    val locale: String,
    val cacheImages: Boolean,
    val discoverRegion: String,
    val originalLanguage: String,
    val streamingRegion: String,
    val blocklistRegion: String,
    val blocklistLanguage: String,
    val blocklistedTags: String,
    val blocklistedTagsLimit: Int,
    val hideAvailable: Boolean,
    val hideBlocklisted: Boolean,
    val partialRequestsEnabled: Boolean,
    val enableSpecialEpisodes: Boolean,
    val youtubeUrl: String,
)

/** A region or language Seerr offers, [code] as it stores it. */
data class SeerrOption(val code: String, val name: String)

data class SeerrCache(val id: String, val name: String, val hits: Int, val misses: Int, val keys: Int, val bytes: Long)

private fun seerr(config: ServiceConfig) = apiFor<SeerrApi>(config, apiKeyHeader(config))

private suspend fun write(op: String, success: String, block: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): String =
    destructive(op) {
        withContext(Dispatchers.IO) {
            try {
                okOr(block(), success)
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

internal fun parseMainSettings(o: JsonObject) = SeerrMainSettings(
    applicationTitle = jsStr(o, "applicationTitle").orEmpty(),
    applicationUrl = jsStr(o, "applicationUrl").orEmpty(),
    locale = jsStr(o, "locale") ?: jsStr(o, "appLanguage") ?: "en",
    cacheImages = jsBool(o, "cacheImages") == true,
    discoverRegion = jsStr(o, "discoverRegion").orEmpty(),
    originalLanguage = jsStr(o, "originalLanguage").orEmpty(),
    streamingRegion = jsStr(o, "streamingRegion").orEmpty(),
    blocklistRegion = jsStr(o, "blocklistRegion").orEmpty(),
    blocklistLanguage = jsStr(o, "blocklistLanguage").orEmpty(),
    blocklistedTags = jsStr(o, "blocklistedTags").orEmpty(),
    blocklistedTagsLimit = jsInt(o, "blocklistedTagsLimit") ?: 50,
    hideAvailable = jsBool(o, "hideAvailable") == true,
    hideBlocklisted = jsBool(o, "hideBlocklisted") == true,
    partialRequestsEnabled = jsBool(o, "partialRequestsEnabled") == true,
    enableSpecialEpisodes = jsBool(o, "enableSpecialEpisodes") == true,
    youtubeUrl = jsStr(o, "youtubeUrl").orEmpty(),
)

/**
 * What goes back: the server's own object with the edited fields laid over it, so settings this
 * app doesn't show (login options, default permissions, quotas) are sent back unchanged.
 */
internal fun mainSettingsBody(server: JsonObject, s: SeerrMainSettings): JsonObject {
    val edits: Map<String, JsonElement> = mapOf(
        "applicationTitle" to JsonPrimitive(s.applicationTitle.trim()),
        "applicationUrl" to JsonPrimitive(s.applicationUrl.trim()),
        "locale" to JsonPrimitive(s.locale),
        "cacheImages" to JsonPrimitive(s.cacheImages),
        "discoverRegion" to JsonPrimitive(s.discoverRegion),
        "originalLanguage" to JsonPrimitive(s.originalLanguage),
        "streamingRegion" to JsonPrimitive(s.streamingRegion),
        "blocklistRegion" to JsonPrimitive(s.blocklistRegion),
        "blocklistLanguage" to JsonPrimitive(s.blocklistLanguage),
        "blocklistedTags" to JsonPrimitive(s.blocklistedTags.trim()),
        "blocklistedTagsLimit" to JsonPrimitive(s.blocklistedTagsLimit),
        "hideAvailable" to JsonPrimitive(s.hideAvailable),
        "hideBlocklisted" to JsonPrimitive(s.hideBlocklisted),
        "partialRequestsEnabled" to JsonPrimitive(s.partialRequestsEnabled),
        "enableSpecialEpisodes" to JsonPrimitive(s.enableSpecialEpisodes),
        "youtubeUrl" to JsonPrimitive(s.youtubeUrl.trim()),
    )
    return JsonObject(server + edits)
}

suspend fun seerrMainSettings(config: ServiceConfig): SeerrMainSettings = withContext(Dispatchers.IO) {
    parseMainSettings(seerr(config).mainSettings())
}

suspend fun seerrSaveMainSettings(config: ServiceConfig, s: SeerrMainSettings): String =
    write("save Seerr general settings", "settings saved") {
        val api = seerr(config)
        api.saveMainSettings(mainSettingsBody(api.mainSettings(), s))
    }

suspend fun seerrRegions(config: ServiceConfig): List<SeerrOption> = withContext(Dispatchers.IO) {
    seerr(config).regions().map { SeerrOption(jsStr(it, "iso_3166_1").orEmpty(), jsStr(it, "english_name").orEmpty()) }
        .filter { it.code.isNotBlank() }.sortedBy { it.name }
}

suspend fun seerrLanguages(config: ServiceConfig): List<SeerrOption> = withContext(Dispatchers.IO) {
    seerr(config).languages().map { SeerrOption(jsStr(it, "iso_639_1").orEmpty(), jsStr(it, "english_name").orEmpty()) }
        .filter { it.code.isNotBlank() }.sortedBy { it.name }
}

suspend fun seerrCaches(config: ServiceConfig): List<SeerrCache> = withContext(Dispatchers.IO) {
    val o = seerr(config).caches()
    (o["apiCaches"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { c ->
        val st = c["stats"] as? JsonObject
        SeerrCache(
            id = jsStr(c, "id").orEmpty(),
            name = jsStr(c, "name").orEmpty(),
            hits = st?.let { jsInt(it, "hits") } ?: 0,
            misses = st?.let { jsInt(it, "misses") } ?: 0,
            keys = st?.let { jsInt(it, "keys") } ?: 0,
            bytes = (st?.let { jsLong(it, "ksize") } ?: 0L) + (st?.let { jsLong(it, "vsize") } ?: 0L),
        )
    }
}

suspend fun seerrFlushCache(config: ServiceConfig, id: String): String =
    write("flush Seerr cache $id", "flushed") { seerr(config).flushCache(id) }

/**
 * A job's cron line for "every [every] minutes/hours", in Seerr's six-field form (seconds first).
 * Null for other kinds: a fixed job's schedule isn't Seerr's to change.
 */
internal fun jobCron(interval: String, every: Int): String? = when (interval) {
    "seconds" -> "*/$every * * * * *"
    "minutes" -> "0 */$every * * * *"
    "hours" -> "0 0 */$every * * *"
    else -> null
}

/** The "every N" a cron line of [jobCron]'s shape stands for; null when it is some other line. */
internal fun jobEvery(cron: String): Int? {
    val f = cron.trim().split(Regex("\\s+"))
    if (f.size != 6) return null
    return f.firstOrNull { it.startsWith("*/") }?.removePrefix("*/")?.toIntOrNull()
}

suspend fun seerrScheduleJob(config: ServiceConfig, jobId: String, cron: String): String =
    write("change Seerr job $jobId schedule", "schedule saved") {
        seerr(config).scheduleJob(jobId, buildJsonObject { put("schedule", cron) })
    }

suspend fun seerrCancelJob(config: ServiceConfig, jobId: String): String =
    write("cancel Seerr job $jobId", "cancelled") { seerr(config).cancelJob(jobId) }
