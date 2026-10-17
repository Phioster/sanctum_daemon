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
import java.time.LocalDate

/** Raw JSON-RPC for the calls whose answers have no fixed shape here. */
internal interface NzbgetRawApi {
    @POST("jsonrpc") suspend fun call(@Body body: JsonObject): JsonObject
}

private fun raw(config: ServiceConfig) = apiFor<NzbgetRawApi>(config, basicHeader(config))
private fun rpc(method: String, vararg params: Any) = buildJsonObject {
    put("method", method)
    putJsonArray("params") {
        params.forEach { p -> when (p) { is Int -> add(p); is Boolean -> add(p); else -> add(p.toString()) } }
    }
}

private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** One news server's traffic. Sizes in MiB; [days] are the last 14 days, oldest first. */
data class NzbServerVolume(
    val serverId: Int,
    val name: String,
    val todayMb: Long,
    val monthMb: Long,
    val totalMb: Long,
    val customMb: Long,
    /** yyyy-MM-dd of the last reset of the custom counter, blank when never. */
    val customSince: String,
    val days: List<Long>,
    /** Share of articles that came through in the last 30 days, null without traffic. */
    val successRate: Double?,
)

/**
 * The servervolumes answer, per server. Day slots count from [FirstDay] (days since 1970, UTC), so
 * slot i is FirstDay + i; server 0 is the sum of all, named "all servers".
 */
internal fun volumesOf(list: List<JsonObject>, names: Map<Int, String>, today: LocalDate): List<NzbServerVolume> = list.map { o ->
    val id = jsInt(o, "ServerID") ?: 0
    val first = (jsLong(o, "FirstDay") ?: 0L)
    val perDay = o["BytesPerDays"].objects().map { jsLong(it, "SizeMB") ?: 0L }
    fun dayOf(i: Int): LocalDate = LocalDate.ofEpochDay(first + i)
    val todayIdx = (today.toEpochDay() - first).toInt()
    val month = perDay.indices.filter { dayOf(it).year == today.year && dayOf(it).month == today.month }.sumOf { perDay[it] }
    val last14 = (todayIdx - 13..todayIdx).map { perDay.getOrNull(it) ?: 0L }
    val articles = o["ArticlesPerDays"].objects()
    val recent = (todayIdx - 29..todayIdx).mapNotNull { articles.getOrNull(it) }
    val ok = recent.sumOf { jsLong(it, "Success") ?: 0L }
    val failed = recent.sumOf { jsLong(it, "Failed") ?: 0L }
    val customTime = jsLong(o, "CustomTime") ?: 0L
    NzbServerVolume(
        serverId = id,
        name = if (id == 0) "all servers" else names[id] ?: "server $id",
        todayMb = perDay.getOrNull(todayIdx) ?: 0L,
        monthMb = month,
        totalMb = jsLong(o, "TotalSizeMB") ?: 0L,
        customMb = jsLong(o, "CustomSizeMB") ?: 0L,
        customSince = if (customTime > 0) LocalDate.ofEpochDay(customTime / 86_400).toString() else "",
        days = last14,
        successRate = if (ok + failed > 0) ok.toDouble() / (ok + failed) else null,
    )
}

private fun jsLong(o: JsonObject, key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()

/**
 * Server and category names from NZBGet's settings. Only `ServerN.Name` and `CategoryN.Name` are
 * kept; everything else in that list (passwords among it) is dropped on the spot.
 */
internal fun namesOf(config: List<JsonObject>): Pair<Map<Int, String>, List<String>> {
    val servers = mutableMapOf<Int, String>()
    val categories = mutableListOf<String>()
    config.forEach { o ->
        val name = jsStr(o, "Name").orEmpty()
        Regex("""Server(\d+)\.Name""", RegexOption.IGNORE_CASE).matchEntire(name)?.let { m ->
            jsStr(o, "Value")?.takeIf { it.isNotBlank() }?.let { servers[m.groupValues[1].toInt()] = it }
        }
        Regex("""Category(\d+)\.Name""", RegexOption.IGNORE_CASE).matchEntire(name)?.let {
            jsStr(o, "Value")?.takeIf { it.isNotBlank() }?.let(categories::add)
        }
    }
    return servers to categories
}

suspend fun nzbgetNames(config: ServiceConfig): Pair<Map<Int, String>, List<String>> = withContext(Dispatchers.IO) {
    namesOf(raw(config).call(rpc("config"))["result"].objects())
}

suspend fun nzbgetServerVolumes(config: ServiceConfig): List<NzbServerVolume> = withContext(Dispatchers.IO) {
    val names = runCatching { nzbgetNames(config).first }.getOrDefault(emptyMap())
    volumesOf(raw(config).call(rpc("servervolumes"))["result"].objects(), names, LocalDate.now(java.time.ZoneOffset.UTC))
}

/** Starts the custom counter from zero, e.g. on the day a monthly quota renews. */
suspend fun nzbgetResetCustomCounter(config: ServiceConfig, serverId: Int): String =
    destructive("reset the NZBGet traffic counter") {
        withContext(Dispatchers.IO) {
            runCatching {
                val r = raw(config).call(rpc("resetservervolume", serverId, "CUSTOM"))
                if (jsBool(r, "result") == true) "counter reset" else "error: NZBGet refused"
            }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

/** `append` with the file itself: name, base64 content, category; NZBGet answers the new id. */
internal fun appendFileBody(fileName: String, base64: String, category: String): JsonObject = buildJsonObject {
    put("method", "append")
    putJsonArray("params") {
        add(fileName)   // NZBFilename
        add(base64)     // NZBContent
        add(category)   // Category
        add(0)          // Priority
        add(false)      // AddToTop
        add(false)      // AddPaused
        add("")         // DupeKey
        add(0)          // DupeScore
        add("SCORE")    // DupeMode
    }
}

/** An NZB is XML with this root; anything else is not worth sending. */
internal fun looksLikeNzb(bytes: ByteArray): Boolean =
    String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8).contains("<nzb", ignoreCase = true)

suspend fun runNzbAppendFile(config: ServiceConfig, fileName: String, bytes: ByteArray, category: String): String =
    destructive("add $fileName to the NZBGet queue") {
        withContext(Dispatchers.IO) {
            runCatching {
                val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
                val r = raw(config).call(appendFileBody(fileName, b64, category.trim()))
                val id = jsInt(r, "result") ?: 0
                if (id > 0) "added $fileName (id $id)" else "error: NZBGet rejected the file"
            }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
