package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

/** Renaming, tags, import lists, download clients and Prowlarr proxies: same shapes on every *arr. */
internal interface ArrToolsApi {
    @GET suspend fun json(@Url url: String): JsonElement
    @POST suspend fun post(@Url url: String, @Body body: JsonElement): Response<ResponseBody>
    @PUT suspend fun put(@Url url: String, @Body body: JsonElement): Response<ResponseBody>
    @DELETE suspend fun delete(@Url url: String): Response<ResponseBody>
}

private fun tools(config: ServiceConfig) = apiFor<ArrToolsApi>(config, apiKeyHeader(config))
private fun base(config: ServiceConfig) = maintenanceBase(config.type)
private val lenient = Json { ignoreUnknownKeys = true }

private suspend fun write(op: String, success: String, block: suspend () -> Response<ResponseBody>): String =
    destructive(op) {
        withContext(Dispatchers.IO) {
            try { okOr(block(), success) } catch (t: Throwable) { "error: ${t.message ?: t.javaClass.simpleName}" }
        }
    }

private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonElement?.ints(): List<Int> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()

/** The query key that names a title on this *arr: series, movie or artist. */
internal fun itemKey(type: ServiceType) = when (type) {
    ServiceType.SONARR -> "seriesId"
    ServiceType.LIDARR -> "artistId"
    else -> "movieId"
}

// ---- Renaming ----

/** A file whose name the *arr's naming scheme would change. [label] says which episode/track it is. */
data class ArrRename(val fileId: Int, val label: String, val existing: String, val renamed: String)

internal fun renameOf(type: ServiceType, o: JsonObject): ArrRename {
    val label = when (type) {
        ServiceType.SONARR -> "S%02d E%s".format(jsInt(o, "seasonNumber") ?: 0, o["episodeNumbers"].ints().joinToString(",") { "%02d".format(it) })
        ServiceType.LIDARR -> "track ${o["trackNumbers"].ints().joinToString(",")}"
        else -> ""
    }
    val fileKey = when (type) { ServiceType.SONARR -> "episodeFileId"; ServiceType.LIDARR -> "trackFileId"; else -> "movieFileId" }
    return ArrRename(jsInt(o, fileKey) ?: 0, label, jsStr(o, "existingPath").orEmpty(), jsStr(o, "newPath").orEmpty())
}

suspend fun arrRenamePreview(config: ServiceConfig, itemId: Int): List<ArrRename> = withContext(Dispatchers.IO) {
    tools(config).json("${base(config)}/rename?${itemKey(config.type)}=$itemId").objects().map { renameOf(config.type, it) }
}

suspend fun arrRenameFiles(config: ServiceConfig, itemId: Int, fileIds: List<Int>): String =
    write("rename ${fileIds.size} file(s) on ${config.label}", "renaming started") {
        tools(config).post("${base(config)}/command", buildJsonObject {
            put("name", "RenameFiles")
            put(itemKey(config.type), itemId)
            put("files", JsonArray(fileIds.map { JsonPrimitive(it) }))
        })
    }

// ---- Tags ----

/** A tag and where it is used; [uses] reads like "3 series · 1 indexer". */
data class ArrTag(val id: Int, val label: String, val uses: String, val useCount: Int)

private val USAGE = listOf(
    "seriesIds" to "series", "movieIds" to "films", "artistIds" to "artists", "indexerIds" to "indexers",
    "downloadClientIds" to "download clients", "importListIds" to "import lists", "notificationIds" to "connections",
    "delayProfileIds" to "delay profiles", "restrictionIds" to "release profiles", "releaseProfileIds" to "release profiles",
    "indexerProxyIds" to "proxies", "applicationIds" to "apps", "autoTagIds" to "auto tags",
)

internal fun tagOf(o: JsonObject): ArrTag {
    val parts = USAGE.mapNotNull { (k, name) -> o[k].ints().size.takeIf { it > 0 }?.let { it to name } }
    return ArrTag(jsInt(o, "id") ?: 0, jsStr(o, "label").orEmpty(), parts.joinToString(" · ") { (n, name) -> "$n $name" }, parts.sumOf { it.first })
}

suspend fun arrTags(config: ServiceConfig): List<ArrTag> = withContext(Dispatchers.IO) {
    tools(config).json("${base(config)}/tag/detail").objects().map(::tagOf).sortedBy { it.label.lowercase() }
}

/** *arr tags are lower-case words; the server would refuse anything else. */
internal fun cleanTagLabel(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')

suspend fun arrCreateTag(config: ServiceConfig, label: String): String =
    write("create tag $label on ${config.label}", "tag created") {
        tools(config).post("${base(config)}/tag", buildJsonObject { put("label", cleanTagLabel(label)) })
    }

suspend fun arrRenameTag(config: ServiceConfig, id: Int, label: String): String =
    write("rename tag on ${config.label}", "tag renamed") {
        tools(config).put("${base(config)}/tag/$id", buildJsonObject { put("id", id); put("label", cleanTagLabel(label)) })
    }

suspend fun arrDeleteTag(config: ServiceConfig, id: Int): String =
    write("delete tag on ${config.label}", "tag deleted") { tools(config).delete("${base(config)}/tag/$id") }

/** The tag ids on one title (or Prowlarr indexer). */
suspend fun arrItemTags(config: ServiceConfig, itemId: Int): List<Int> = withContext(Dispatchers.IO) {
    val path = when (config.type) {
        ServiceType.SONARR -> "series"; ServiceType.LIDARR -> "artist"; ServiceType.PROWLARR -> "indexer"; else -> "movie"
    }
    (tools(config).json("${base(config)}/$path/$itemId") as? JsonObject)?.get("tags").ints()
}

/** The bulk editor body that sets [tagIds] on [itemIds]; "replace" makes them exactly that set. */
internal fun tagEditorBody(type: ServiceType, itemIds: List<Int>, tagIds: List<Int>, apply: String): Pair<String, JsonObject> {
    val (path, idsKey) = when (type) {
        ServiceType.SONARR -> "series/editor" to "seriesIds"
        ServiceType.LIDARR -> "artist/editor" to "artistIds"
        ServiceType.PROWLARR -> "indexer/bulk" to "ids"
        else -> "movie/editor" to "movieIds"
    }
    return path to buildJsonObject {
        put(idsKey, JsonArray(itemIds.map { JsonPrimitive(it) }))
        put("tags", JsonArray(tagIds.map { JsonPrimitive(it) }))
        put("applyTags", apply)
    }
}

suspend fun arrSetItemTags(config: ServiceConfig, itemIds: List<Int>, tagIds: List<Int>): String =
    write("set tags on ${itemIds.size} item(s) on ${config.label}", "tags saved") {
        val (path, body) = tagEditorBody(config.type, itemIds, tagIds, "replace")
        tools(config).put("${base(config)}/$path", body)
    }

// ---- Import lists, download clients, proxies ----

data class ArrProvider(
    val id: Int,
    val name: String,
    val kind: String,
    val enabled: Boolean,
    /** Extra facts for the row: protocol, priority, "adds automatically"… */
    val facts: List<String>,
)

internal fun importListOf(o: JsonObject) = ArrProvider(
    id = jsInt(o, "id") ?: 0,
    name = jsStr(o, "name").orEmpty(),
    kind = jsStr(o, "implementationName").orEmpty(),
    // Radarr says "enabled", Sonarr and Lidarr only have the automatic-add switch.
    enabled = jsBool(o, "enabled") ?: true,
    facts = listOfNotNull(
        if ((jsBool(o, "enableAutomaticAdd") ?: jsBool(o, "enableAuto")) == true) "adds automatically" else "manual review",
        jsStr(o, "minRefreshInterval")?.takeIf { it.isNotBlank() }?.let { "every ${it.substringBefore('.').ifBlank { it }}" },
    ),
)

internal fun downloadClientOf(o: JsonObject) = ArrProvider(
    id = jsInt(o, "id") ?: 0,
    name = jsStr(o, "name").orEmpty(),
    kind = jsStr(o, "implementationName").orEmpty(),
    enabled = jsBool(o, "enable") ?: true,
    facts = listOfNotNull(jsStr(o, "protocol"), jsInt(o, "priority")?.let { "priority $it" }),
)

internal fun proxyOf(o: JsonObject) = ArrProvider(
    id = jsInt(o, "id") ?: 0,
    name = jsStr(o, "name").orEmpty(),
    kind = jsStr(o, "implementationName").orEmpty(),
    enabled = true,
    facts = listOf(if (o["tags"].ints().isEmpty()) "no tags: used by no indexer" else "${o["tags"].ints().size} tag(s)"),
)

suspend fun arrImportLists(config: ServiceConfig): List<ArrProvider> = withContext(Dispatchers.IO) {
    tools(config).json("${base(config)}/importlist").objects().map(::importListOf)
}

suspend fun arrImportListSync(config: ServiceConfig): String =
    write("sync import lists on ${config.label}", "import list sync started") {
        tools(config).post("${base(config)}/command", buildJsonObject { put("name", "ImportListSync") })
    }

suspend fun arrDownloadClients(config: ServiceConfig): List<ArrProvider> = withContext(Dispatchers.IO) {
    tools(config).json("${base(config)}/downloadclient").objects().map(::downloadClientOf)
}

suspend fun prowlarrProxies(config: ServiceConfig): List<ArrProvider> = withContext(Dispatchers.IO) {
    tools(config).json("${base(config)}/indexerproxy").objects().map(::proxyOf)
}

/**
 * Test results per provider id: null = fine, else the reason. The *arrs answer 400 with the same
 * list when anything fails, so the error body is read too. A test only connects; nothing changes.
 */
internal fun testResults(body: String?): Map<Int, String?> =
    runCatching { lenient.parseToJsonElement(body.orEmpty()) }.getOrNull().objects().associate { o ->
        val failures = o["validationFailures"].objects().mapNotNull { jsStr(it, "errorMessage") }
        (jsInt(o, "id") ?: 0) to if (jsBool(o, "isValid") == true) null else failures.joinToString("; ").ifBlank { "failed" }
    }

private suspend fun testAll(config: ServiceConfig, what: String): Map<Int, String?> = withContext(Dispatchers.IO) {
    val r = tools(config).post("${base(config)}/$what/testall", JsonObject(emptyMap()))
    testResults(if (r.isSuccessful) r.body()?.string() else r.errorBody()?.string())
}

suspend fun arrTestDownloadClients(config: ServiceConfig) = testAll(config, "downloadclient")
suspend fun prowlarrTestProxies(config: ServiceConfig) = testAll(config, "indexerproxy")
