package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/** Logs, backups and import exclusions: the same routes on every *arr, under its own api base. */
internal interface ArrMaintenanceApi {
    @GET suspend fun json(@Url url: String): JsonElement
    @DELETE suspend fun delete(@Url url: String): Response<ResponseBody>
    @POST suspend fun post(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
}

/** Lidarr and Prowlarr speak v1, Sonarr and Radarr v3. */
internal fun maintenanceBase(type: ServiceType) =
    if (type == ServiceType.LIDARR || type == ServiceType.PROWLARR) "api/v1" else "api/v3"

private fun maint(config: ServiceConfig) = apiFor<ArrMaintenanceApi>(config, apiKeyHeader(config))

private suspend fun write(op: String, success: String, block: suspend () -> Response<ResponseBody>): String =
    destructive(op) {
        withContext(Dispatchers.IO) {
            try {
                okOr(block(), success)
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

data class ArrLogLine(val level: String, val time: String, val logger: String, val message: String, val exception: String)

/** Newest first. [level] is the lowest shown: "error", "warn" or "info" (the *arr calls it so). */
suspend fun arrLogs(config: ServiceConfig, level: String): List<ArrLogLine> = withContext(Dispatchers.IO) {
    val base = maintenanceBase(config.type)
    val page = maint(config).json("$base/log?page=1&pageSize=100&sortKey=time&sortDirection=descending&level=$level") as? JsonObject
    (page?.get("records") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
        ArrLogLine(
            level = jsStr(o, "level").orEmpty().lowercase(),
            time = jsStr(o, "time").orEmpty().take(19).replace('T', ' '),
            logger = jsStr(o, "logger").orEmpty(),
            message = jsStr(o, "message").orEmpty(),
            exception = jsStr(o, "exception").orEmpty(),
        )
    }
}

data class ArrBackup(val id: Int, val name: String, val type: String, val sizeBytes: Long, val time: String)

suspend fun arrBackups(config: ServiceConfig): List<ArrBackup> = withContext(Dispatchers.IO) {
    val base = maintenanceBase(config.type)
    (maint(config).json("$base/system/backup") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
        ArrBackup(
            id = jsInt(o, "id") ?: 0,
            name = jsStr(o, "name").orEmpty(),
            type = jsStr(o, "type").orEmpty(),
            sizeBytes = jsLong(o, "size") ?: 0L,
            time = jsStr(o, "time").orEmpty().take(16).replace('T', ' '),
        )
    }.sortedByDescending { it.time }
}

/** Backups are made by the Backup command; it lands in the list once the command is done. */
suspend fun arrCreateBackup(config: ServiceConfig): String =
    write("back up ${config.label}", "backup started") {
        maint(config).post("${maintenanceBase(config.type)}/command", buildJsonObject { put("name", "Backup") })
    }

suspend fun arrDeleteBackup(config: ServiceConfig, id: Int): String =
    write("delete backup $id on ${config.label}", "backup deleted") {
        maint(config).delete("${maintenanceBase(config.type)}/system/backup/$id")
    }

/** A title an import list must not add again. [externalId] is the TVDB id (Sonarr) or TMDb id (Radarr). */
data class ArrExclusion(val id: Int, val title: String, val year: Int?, val externalId: Int)

/** Sonarr names the route after import lists, Radarr just "exclusions". */
internal fun exclusionPath(type: ServiceType) =
    if (type == ServiceType.RADARR) "api/v3/exclusions" else "api/v3/importlistexclusion"

suspend fun arrExclusions(config: ServiceConfig): List<ArrExclusion> = withContext(Dispatchers.IO) {
    val radarr = config.type == ServiceType.RADARR
    (maint(config).json(exclusionPath(config.type)) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
        ArrExclusion(
            id = jsInt(o, "id") ?: 0,
            title = jsStr(o, if (radarr) "movieTitle" else "title").orEmpty(),
            year = if (radarr) jsInt(o, "movieYear")?.takeIf { it > 0 } else null,
            externalId = jsInt(o, if (radarr) "tmdbId" else "tvdbId") ?: 0,
        )
    }.sortedBy { it.title.lowercase() }
}

/** Lets an import list add the title again. */
suspend fun arrRemoveExclusion(config: ServiceConfig, id: Int): String =
    write("remove import exclusion $id on ${config.label}", "exclusion removed") {
        maint(config).delete("${exclusionPath(config.type)}/$id")
    }
