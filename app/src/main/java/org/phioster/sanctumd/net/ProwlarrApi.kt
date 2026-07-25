package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.sanctumd.model.ProwlarrCategory
import org.phioster.sanctumd.model.ProwlarrHistoryItem
import org.phioster.sanctumd.model.ProwlarrIndexerItem
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ProwlarrSystemInfo
import org.phioster.sanctumd.model.ProwlarrTaskItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable internal data class ProwlarrIndexerStat(
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
)

@Serializable internal data class ProwlarrStats(val indexers: List<ProwlarrIndexerStat> = emptyList())

// per-indexer stat rows (indexerstats has both a summary and per-indexer array)
@Serializable internal data class ProwlarrIndexerStatRow(
    val indexerId: Int = 0,
    val indexerName: String = "",
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
    val numberOfFailedQueries: Int = 0,
    val numberOfFailedGrabs: Int = 0,
)
@Serializable internal data class ProwlarrStatsFull(
    val indexers: List<ProwlarrIndexerStatRow> = emptyList(),
)

@Serializable internal data class ProwlarrIndexerRecord(
    val id: Int = 0,
    val name: String = "",
    val protocol: String = "",
    val enable: Boolean = false,
    val priority: Int = 0,
    val privacy: String = "",
)

@Serializable internal data class ProwlarrIndexerStatusRecord(
    val indexerId: Int = 0,
    val disabledTill: String? = null,
)

@Serializable internal data class ProwlarrCategoryRef(
    val id: Int = 0,
    val name: String = "",
)
@Serializable internal data class ProwlarrReleaseRecord(
    val guid: String = "",
    val indexerId: Int = 0,
    val indexer: String = "",
    val title: String = "",
    val size: Long = 0,
    val protocol: String = "",
    val seeders: Int? = null,
    val ageMinutes: Double = 0.0,
    val categories: List<ProwlarrCategoryRef> = emptyList(),
    val downloadUrl: String = "",
    val magnetUrl: String = "",
    val publishDate: String = "",
)

@Serializable internal data class ProwlarrGrabReq(val guid: String, val indexerId: Int)

@Serializable internal data class ProwlarrHistoryRec(
    val eventType: String = "",
    val date: String = "",
    val indexer: String = "",
    val data: ProwlarrHistoryData = ProwlarrHistoryData(),
)
@Serializable internal data class ProwlarrHistoryData(val query: String? = null, val title: String? = null)
@Serializable internal data class ProwlarrHistoryPage(val records: List<ProwlarrHistoryRec> = emptyList())

@Serializable internal data class ProwlarrTaskRec(
    val name: String = "",
    val lastExecution: String? = null,
    val nextExecution: String? = null,
)

@Serializable internal data class ProwlarrSystemStatusRec(val version: String = "")
@Serializable internal data class ProwlarrHealthRec(val type: String = "", val message: String = "")

internal interface ProwlarrApi {
    @GET("api/v1/indexerstats") suspend fun stats(): ProwlarrStats
    @POST("api/v1/indexer/testall") suspend fun testAll(): Response<ResponseBody>
    @GET("api/v1/indexer") suspend fun indexers(): List<ProwlarrIndexerRecord>
    @GET("api/v1/indexerstats") suspend fun statsFull(): ProwlarrStatsFull
    @GET("api/v1/indexerstatus") suspend fun indexerStatus(): List<ProwlarrIndexerStatusRecord>
    @GET("api/v1/indexer/{id}") suspend fun indexerRaw(@Path("id") id: Int): JsonObject
    @PUT("api/v1/indexer/{id}") suspend fun updateIndexer(@Path("id") id: Int, @Body body: JsonObject): Response<ResponseBody>
    @DELETE("api/v1/indexer/{id}") suspend fun deleteIndexer(@Path("id") id: Int): Response<ResponseBody>
    @GET("api/v1/indexer/schema") suspend fun indexerSchema(): List<JsonObject>
    @POST("api/v1/indexer") suspend fun addIndexer(@Body body: JsonObject): Response<ResponseBody>
    @GET("api/v1/appprofile") suspend fun appProfiles(): List<JsonObject>
    // Servarr has no /indexer/{id}/test route (405) — testing an existing indexer means POSTing its
    // definition to /indexer/test.
    @POST("api/v1/indexer/test") suspend fun testIndexerBody(@Body body: JsonObject): Response<ResponseBody>
    @GET("api/v1/search") suspend fun search(
        @Query("query") query: String,
        @Query("categories") categories: List<Int>,
        @Query("type") type: String = "search",
        @Query("limit") limit: Int = 100,
    ): List<ProwlarrReleaseRecord>
    @POST("api/v1/search") suspend fun grab(@Body body: ProwlarrGrabReq): Response<ResponseBody>
    @GET("api/v1/history") suspend fun history(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sortKey") sortKey: String = "date",
        @Query("sortDirection") sortDirection: String = "descending",
    ): ProwlarrHistoryPage
    @GET("api/v1/system/task") suspend fun tasks(): List<ProwlarrTaskRec>
    @GET("api/v1/system/status") suspend fun systemStatus(): ProwlarrSystemStatusRec
    @GET("api/v1/health") suspend fun health(): List<ProwlarrHealthRec>
}

// ---- Seerr (Overseerr-compatible, api/v1) ----

suspend fun runProwlarrTestAll(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<ProwlarrApi>(config, apiKeyHeader(config)).testAll(), "testing indexers")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Top-level newznab categories Prowlarr maps per-indexer; used as the search filter. */
val prowlarrCategories = listOf(
    ProwlarrCategory(0, "all"),
    ProwlarrCategory(2000, "movies"),
    ProwlarrCategory(5000, "tv"),
    ProwlarrCategory(3000, "audio"),
    ProwlarrCategory(7000, "books"),
    ProwlarrCategory(4000, "pc"),
    ProwlarrCategory(6000, "xxx"),
    ProwlarrCategory(8000, "other"),
)

suspend fun prowlarrIndexers(config: ServiceConfig): List<ProwlarrIndexerItem> = withContext(Dispatchers.IO) {
    val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
    val indexers = api.indexers()
    val statById = runCatching { api.statsFull().indexers.associateBy { it.indexerId } }.getOrDefault(emptyMap())
    val failingIds = runCatching {
        api.indexerStatus().filter { it.disabledTill != null }.map { it.indexerId }.toSet()
    }.getOrDefault(emptySet())
    indexers.map { ix ->
        val s = statById[ix.id]
        val q = s?.numberOfQueries ?: 0
        val failedQ = s?.numberOfFailedQueries ?: 0
        val rate = if (q > 0) (failedQ * 100) / q else 0
        ProwlarrIndexerItem(
            id = ix.id,
            name = ix.name,
            protocol = ix.protocol,
            enable = ix.enable,
            priority = ix.priority,
            privacy = ix.privacy,
            queries = q,
            grabs = s?.numberOfGrabs ?: 0,
            failRate = rate,
            failing = ix.id in failingIds,
        )
    }.sortedByDescending { it.grabs }
}

suspend fun prowlarrToggleIndexer(config: ServiceConfig, id: Int, enable: Boolean): String = destructive("toggle Prowlarr indexer $id") {
    withContext(Dispatchers.IO) {
        try {
            val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
            val raw = api.indexerRaw(id)
            val body = JsonObject(raw.toMutableMap().apply { put("enable", JsonPrimitive(enable)) })
            okOr(api.updateIndexer(id, body), if (enable) "enabled" else "disabled")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun prowlarrDeleteIndexer(config: ServiceConfig, id: Int): String = destructive("delete Prowlarr indexer $id") {
    withContext(Dispatchers.IO) {
        try {
            okOr(apiFor<ProwlarrApi>(config, apiKeyHeader(config)).deleteIndexer(id), "deleted")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Parse an indexer/schema JSON's editable scalar fields (multi-value fields like categories are
 *  left out so they're preserved untouched on save). Shared by edit + add. */
private fun parseIndexerFields(raw: JsonObject): List<org.phioster.sanctumd.model.ProwlarrField> =
    (raw["fields"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { f ->
        val name = jsStr(f, "name") ?: return@mapNotNull null
        val type = jsStr(f, "type") ?: "textbox"
        if (type == "info") return@mapNotNull null
        val vEl = f["value"]
        if (vEl is JsonArray) return@mapNotNull null // multi-value (e.g. categories) — don't expose
        val value = (vEl as? JsonPrimitive)?.content ?: ""
        val options = (f["selectOptions"] as? JsonArray)?.mapNotNull { o ->
            (o as? JsonObject)?.let { so ->
                val ov = so["value"]?.let { v -> (v as? JsonPrimitive)?.content } ?: return@mapNotNull null
                ov to (jsStr(so, "name") ?: ov)
            }
        }.orEmpty()
        org.phioster.sanctumd.model.ProwlarrField(
            name = name,
            label = jsStr(f, "label") ?: name,
            type = type,
            value = value,
            helpText = jsStr(f, "helpText") ?: "",
            advanced = jsBool(f, "advanced") ?: false,
            options = options,
        )
    }

/** Merge stringified [values] (keyed by field name) into a copy of [raw]'s fields[] with the correct
 *  JSON types, leaving every other field/key untouched. Shared by edit + add. */
private fun mergeIndexerFields(raw: JsonObject, values: Map<String, String>): JsonArray = buildJsonArray {
    (raw["fields"] as? JsonArray).orEmpty().forEach { fe ->
        val fo = fe as? JsonObject
        val fname = fo?.let { jsStr(it, "name") }
        if (fo != null && fname != null && values.containsKey(fname)) {
            val type = jsStr(fo, "type") ?: "textbox"
            val nv = values.getValue(fname)
            val prim = when (type) {
                "checkbox" -> JsonPrimitive(nv.toBoolean())
                "number", "select" -> nv.toIntOrNull()?.let { JsonPrimitive(it) }
                    ?: nv.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(nv)
                else -> JsonPrimitive(nv)
            }
            add(JsonObject(fo.toMutableMap().apply { put("value", prim) }))
        } else {
            add(fe)
        }
    }
}

/** The editable scalar fields of an existing indexer. */
suspend fun prowlarrIndexerEdit(config: ServiceConfig, id: Int): org.phioster.sanctumd.model.ProwlarrIndexerEdit = withContext(Dispatchers.IO) {
    val raw = apiFor<ProwlarrApi>(config, apiKeyHeader(config)).indexerRaw(id)
    org.phioster.sanctumd.model.ProwlarrIndexerEdit(id, jsStr(raw, "name") ?: "indexer", parseIndexerFields(raw))
}

/** One selectable indexer definition from the schema catalogue, with its raw JSON kept for the POST. */
class ProwlarrSchemaEntry internal constructor(
    internal val raw: JsonObject,
    val name: String,
    val protocol: String,
    val privacy: String,
    val fields: List<org.phioster.sanctumd.model.ProwlarrField>,
)

/** The full catalogue of addable indexer definitions (GET /indexer/schema), sorted by name. */
suspend fun prowlarrIndexerSchemas(config: ServiceConfig): List<ProwlarrSchemaEntry> = withContext(Dispatchers.IO) {
    apiFor<ProwlarrApi>(config, apiKeyHeader(config)).indexerSchema().mapNotNull { raw ->
        val name = jsStr(raw, "name") ?: return@mapNotNull null
        ProwlarrSchemaEntry(
            raw = raw,
            name = name,
            protocol = jsStr(raw, "protocol") ?: "",
            privacy = jsStr(raw, "privacy") ?: "",
            fields = parseIndexerFields(raw),
        )
    }.sortedBy { it.name.lowercase() }
}

/** Add a new indexer from a chosen schema [entry] with the user-supplied [name] + field [values]. */
suspend fun prowlarrAddIndexer(config: ServiceConfig, entry: ProwlarrSchemaEntry, name: String, values: Map<String, String>): String =
    destructive("add Prowlarr indexer $name") {
        withContext(Dispatchers.IO) {
            try {
                val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
                val appProfileId = runCatching {
                    api.appProfiles().firstNotNullOfOrNull { jsInt(it, "id") }
                }.getOrNull() ?: 1
                val body = JsonObject(
                    entry.raw.toMutableMap().apply {
                        put("fields", mergeIndexerFields(entry.raw, values))
                        put("name", JsonPrimitive(name))
                        put("enable", JsonPrimitive(true))
                        put("appProfileId", JsonPrimitive(appProfileId))
                    },
                )
                okOrServarr(api.addIndexer(body), "added")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Merge edited field [values] (keyed by field name) into the indexer's raw JSON and PUT it. */
suspend fun prowlarrSaveIndexer(config: ServiceConfig, id: Int, values: Map<String, String>): String =
    destructive("edit Prowlarr indexer $id") {
        withContext(Dispatchers.IO) {
            try {
                val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
                val raw = api.indexerRaw(id)
                val body = JsonObject(raw.toMutableMap().apply { put("fields", mergeIndexerFields(raw, values)) })
                okOrServarr(api.updateIndexer(id, body), "saved")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** The first Servarr validation errorMessage from a 400 body ([{"errorMessage": "…"}]), or null. */
private fun servarrError(resp: Response<ResponseBody>): String? {
    val body = runCatching { resp.errorBody()?.string() }.getOrNull() ?: return null
    return runCatching {
        (json.parseToJsonElement(body) as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("errorMessage")?.let { m -> (m as? JsonPrimitive)?.content } }
    }.getOrNull()
}

/** "ok" on 2xx, else the first Servarr errorMessage or the HTTP code. */
private fun testResult(resp: Response<ResponseBody>): String =
    if (resp.isSuccessful) "ok" else "error: ${servarrError(resp) ?: "HTTP ${resp.code()}"}"

/** [success] on 2xx, else the Servarr validation message (so the user sees *why* a 400 happened). */
private fun okOrServarr(resp: Response<ResponseBody>, success: String): String =
    if (resp.isSuccessful) success else "error: ${servarrError(resp) ?: "HTTP ${resp.code()}"}"

suspend fun prowlarrTestIndexer(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
        testResult(api.testIndexerBody(api.indexerRaw(id))) // saved config POSTed to /indexer/test
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Test a not-yet-added indexer: build its definition from the chosen schema [entry] + [name] +
 *  field [values] and POST it to /indexer/test (no changes made). */
suspend fun prowlarrTestNewIndexer(config: ServiceConfig, entry: ProwlarrSchemaEntry, name: String, values: Map<String, String>): String =
    withContext(Dispatchers.IO) {
        try {
            val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
            val body = JsonObject(
                entry.raw.toMutableMap().apply {
                    put("fields", mergeIndexerFields(entry.raw, values))
                    put("name", JsonPrimitive(name))
                },
            )
            testResult(api.testIndexerBody(body))
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

suspend fun prowlarrSearch(config: ServiceConfig, query: String, categoryId: Int): List<ProwlarrRelease> = withContext(Dispatchers.IO) {
    val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
    val cats = if (categoryId == 0) emptyList() else listOf(categoryId)
    api.search(query = query, categories = cats).map { r ->
        ProwlarrRelease(
            guid = r.guid,
            indexerId = r.indexerId,
            indexer = r.indexer,
            title = r.title,
            sizeMb = r.size / (1024 * 1024),
            sizeBytes = r.size,
            protocol = r.protocol,
            seeders = if (r.protocol == "torrent") r.seeders else null,
            ageDays = (r.ageMinutes / (60 * 24)).toInt(),
            categories = r.categories.joinToString(", ") { it.name }.ifBlank { "—" },
            downloadUrl = r.downloadUrl,
            magnetUrl = r.magnetUrl,
            publishDate = r.publishDate,
        )
    }
}

suspend fun prowlarrHistory(config: ServiceConfig): List<ProwlarrHistoryItem> = withContext(Dispatchers.IO) {
    val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
    api.history().records.map { h ->
        ProwlarrHistoryItem(
            title = h.data.title ?: h.data.query ?: "—",
            indexer = h.indexer,
            eventType = h.eventType,
            date = h.date.take(16).replace('T', ' '),
        )
    }
}

suspend fun prowlarrTasks(config: ServiceConfig): List<ProwlarrTaskItem> = withContext(Dispatchers.IO) {
    val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
    api.tasks().map { t ->
        ProwlarrTaskItem(
            name = t.name,
            lastExecution = (t.lastExecution ?: "").take(16).replace('T', ' '),
            nextExecution = (t.nextExecution ?: "").take(16).replace('T', ' '),
        )
    }
}

suspend fun prowlarrSystem(config: ServiceConfig): ProwlarrSystemInfo = withContext(Dispatchers.IO) {
    val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
    val version = runCatching { api.systemStatus().version }.getOrDefault("?")
    val health = runCatching { api.health().map { it.type to it.message } }.getOrDefault(emptyList())
    ProwlarrSystemInfo(version = version, health = health)
}

suspend fun prowlarrGrab(config: ServiceConfig, release: ProwlarrRelease): String = destructive("grab a release via Prowlarr") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
                .grab(ProwlarrGrabReq(release.guid, release.indexerId))
            okOr(r, "grabbed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

internal suspend fun prowlarrStatus(config: ServiceConfig): ServiceStatus {
    val api = apiFor<ProwlarrApi>(config, mapOf("X-Api-Key" to config.apiKey))
    val stats = api.stats()
    return ServiceStatus(
        ok = true,
        stats = listOf(
            "Indexers" to stats.indexers.size.toString(),
            "Grabs" to stats.indexers.sumOf { it.numberOfGrabs }.toString(),
            "Queries" to stats.indexers.sumOf { it.numberOfQueries }.toString(),
        ),
    )
}
