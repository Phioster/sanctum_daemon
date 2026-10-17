package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Volume per news server and NZB files from the share menu. Method names, parameter order and
 * field names are the ones in NZBGet's daemon/remote/XmlRpc.cpp (servervolumes,
 * resetservervolume, config, append v13+) and daemon/main/Options.cpp (ServerN.*, CategoryN.*).
 */
internal interface NzbgetRawApi {
    @POST("jsonrpc") suspend fun call(@Body body: JsonObject): JsonElement
}

private fun raw(config: ServiceConfig) = apiFor<NzbgetRawApi>(config, basicHeader(config))
private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonElement.result(): JsonElement? = (this as? JsonObject)?.get("result")

// ---- Volume per news server ----

/** One counter of [NzbServerVolume]: entry 0 is all servers together, entry n is ServerN. */
data class NzbServerVolume(
    val id: Int,
    val name: String,
    val host: String,
    val active: Boolean,
    /** 0 = main server, higher = fill/backup servers. */
    val level: Int,
    val totalBytes: Long,
    val todayBytes: Long,
    val weekBytes: Long,
    val monthBytes: Long,
    /** The counter the user resets by hand, and since when it runs (unix seconds). */
    val customBytes: Long,
    val customSince: Long,
    /** The last 30 days, oldest first; shorter while the counter is younger. */
    val days: List<Long>,
    /** Articles over the same 30 days. */
    val articlesOk: Long,
    val articlesFailed: Long,
) {
    /** Share of articles the server had, or null before it was asked for any. */
    val successRate: Double? get() = (articlesOk + articlesFailed).takeIf { it > 0 }?.let { articlesOk.toDouble() / it }
}

/** Seconds and minutes are left out: a month of days is what the page shows. */
internal fun serverVolumesBody(): JsonObject = buildJsonObject {
    put("method", "servervolumes")
    putJsonArray("params") {
        add(false) // BytesPerSeconds
        add(false) // BytesPerMinutes
        add(false) // BytesPerHours
        add(true)  // BytesPerDays
        add(true)  // ArticlesPerDays
    }
}

internal fun resetVolumeBody(serverId: Int): JsonObject = buildJsonObject {
    put("method", "resetservervolume")
    putJsonArray("params") { add(serverId); add("CUSTOM") }
}

internal fun configBody(): JsonObject = buildJsonObject {
    put("method", "config")
    putJsonArray("params") {}
}

/** NZBGet splits 64-bit sizes into two unsigned 32-bit halves. */
internal fun splitSize(o: JsonObject, prefix: String = "Size"): Long =
    ((jsLong(o, "${prefix}Hi") ?: 0L) shl 32) + (jsLong(o, "${prefix}Lo") ?: 0L)

/** The slots up to and including [slot], at most [n] of them, oldest first. */
internal fun <T> lastSlots(list: List<T>, slot: Int, n: Int): List<T> {
    if (list.isEmpty()) return emptyList()
    val end = slot.coerceIn(0, list.size - 1)
    return list.subList((end - n + 1).coerceAtLeast(0), end + 1)
}

/** The ServerN.* and CategoryN.Name options as a plain map. Passwords are not kept. */
internal fun configMap(result: JsonElement?): Map<String, String> =
    result.objects().mapNotNull { o ->
        val name = jsStr(o, "Name") ?: return@mapNotNull null
        if (name.endsWith(".Password", ignoreCase = true) || name.endsWith(".Username", ignoreCase = true)) null
        else name to jsStr(o, "Value").orEmpty()
    }.toMap()

internal fun volumeOf(o: JsonObject, options: Map<String, String>): NzbServerVolume {
    val id = jsInt(o, "ServerID") ?: 0
    val slot = jsInt(o, "DaySlot") ?: 0
    val days = (o["BytesPerDays"]).objects().map { splitSize(it) }
    val month = lastSlots(days, slot, 30)
    val articles = lastSlots((o["ArticlesPerDays"]).objects(), slot, 30)
    val host = options["Server$id.Host"].orEmpty()
    return NzbServerVolume(
        id = id,
        name = if (id == 0) "all servers" else options["Server$id.Name"]?.takeIf { it.isNotBlank() } ?: host.ifBlank { "server $id" },
        host = if (id == 0) "" else host,
        active = id == 0 || !options["Server$id.Active"].equals("no", ignoreCase = true),
        level = options["Server$id.Level"]?.toIntOrNull() ?: 0,
        totalBytes = splitSize(o, "TotalSize"),
        todayBytes = days.getOrNull(slot) ?: 0,
        weekBytes = lastSlots(days, slot, 7).sum(),
        monthBytes = month.sum(),
        customBytes = splitSize(o, "CustomSize"),
        customSince = jsLong(o, "CustomTime") ?: 0,
        days = month,
        articlesOk = articles.sumOf { jsLong(it, "Success") ?: 0 },
        articlesFailed = articles.sumOf { jsLong(it, "Failed") ?: 0 },
    )
}

/** All servers first, then each news server in the order NZBGet numbers them. */
suspend fun nzbServerVolumes(config: ServiceConfig): List<NzbServerVolume> = withContext(Dispatchers.IO) {
    val api = raw(config)
    val options = runCatching { configMap(api.call(configBody()).result()) }.getOrDefault(emptyMap())
    api.call(serverVolumesBody()).result().objects().map { volumeOf(it, options) }
}

/** Zeroes the hand-reset counter of one server, or of every server for -1. */
suspend fun runNzbResetVolume(config: ServiceConfig, serverId: Int, label: String): String =
    destructive("reset the NZBGet counter of $label") {
        withContext(Dispatchers.IO) {
            try {
                val r = raw(config).call(resetVolumeBody(serverId)).result()
                if (r?.toString() == "true") "counter reset" else "error: NZBGet did not reset it"
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** The category names set up in NZBGet, for the upload's picker. */
suspend fun nzbCategories(config: ServiceConfig): List<String> = withContext(Dispatchers.IO) {
    val options = configMap(raw(config).call(configBody()).result())
    options.filterKeys { Regex("""Category\d+\.Name""", RegexOption.IGNORE_CASE).matches(it) }
        .toSortedMap(compareBy { it.substringAfter("Category").substringBefore('.').toIntOrNull() ?: 0 })
        .values.filter { it.isNotBlank() }
}

// ---- NZB files ----

/** NZB files are a few hundred KB, rarely a few MB; anything this large is not one. */
internal const val NZB_MAX_BYTES = 32 * 1024 * 1024

/** An NZB is XML with an <nzb> root; the first few KB are enough to tell. */
internal fun looksLikeNzb(bytes: ByteArray): Boolean {
    if (bytes.isEmpty() || bytes.size > NZB_MAX_BYTES) return false
    val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8).lowercase()
    return "<nzb" in head
}

/** A file name NZBGet takes as an NZB: it names the download after it. */
internal fun nzbFileName(name: String?): String {
    val base = name?.substringAfterLast('/')?.trim().orEmpty().ifBlank { "upload" }
    return if (base.endsWith(".nzb", ignoreCase = true)) base else "$base.nzb"
}

/** append, v13+ order: the content is the file itself, Base64-encoded. */
internal fun appendFileBody(fileName: String, bytes: ByteArray, category: String, paused: Boolean): JsonObject =
    buildJsonObject {
        put("method", "append")
        putJsonArray("params") {
            add(fileName)  // NZBFilename
            add(java.util.Base64.getEncoder().encodeToString(bytes)) // NZBContent
            add(category)  // Category
            add(0)         // Priority
            add(false)     // AddToTop
            add(paused)    // AddPaused
            add("")        // DupeKey
            add(0)         // DupeScore
            add("SCORE")   // DupeMode
        }
    }

/** Uploads an NZB file to the queue. */
suspend fun runNzbAppendFile(config: ServiceConfig, name: String?, bytes: ByteArray, category: String, paused: Boolean): String {
    if (!looksLikeNzb(bytes)) return "error: that is not an NZB file"
    val fileName = nzbFileName(name)
    return destructive("add $fileName to ${config.label}") {
        withContext(Dispatchers.IO) {
            try {
                val id = raw(config).call(appendFileBody(fileName, bytes, category.trim(), paused)).result()?.toString()?.toIntOrNull() ?: 0
                if (id > 0) "added $fileName" else "error: NZBGet rejected the file"
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }
}
