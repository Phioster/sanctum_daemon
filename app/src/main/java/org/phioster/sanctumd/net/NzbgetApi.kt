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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Credentials
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.NzbQueueItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable private data class NzbStatusResp(val result: NzbStatus = NzbStatus())
@Serializable private data class NzbStatus(
    val DownloadRate: Long = 0,
    val RemainingSizeMB: Long = 0,
    val DownloadPaused: Boolean = false,
    val DownloadedSizeMB: Long = 0,
    val FreeDiskSpaceMB: Long = 0,
    val ArticleCacheMB: Long = 0,
    val UpTimeSec: Long = 0,
    val ThreadCount: Int = 0,
)

@Serializable private data class NzbStringResp(val result: String = "")
@Serializable private data class NzbIntResp(val result: Int = 0)

@Serializable private data class NzbGroupsResp(val result: List<NzbGroup> = emptyList())
@Serializable private data class NzbGroup(
    val NZBID: Int = 0,
    val NZBName: String = "",
    val Status: String = "",
    val FileSizeMB: Long = 0,
    val RemainingSizeMB: Long = 0,
)

@Serializable private data class NzbRpcReq(val method: String, val params: List<String> = emptyList(), val id: Int = 1)
@Serializable private data class NzbBoolResp(val result: Boolean = false)

@Serializable private data class NzbHistoryReq(val params: List<Boolean>, val method: String = "history", val id: Int = 1)
@Serializable private data class NzbHistoryResp(val result: List<NzbHistoryItem> = emptyList())
@Serializable private data class NzbHistoryItem(
    val NZBID: Int = 0,
    val Name: String = "",
    val Status: String = "",
    val FileSizeMB: Long = 0,
)

internal interface NzbgetApi {
    @GET("jsonrpc/status") suspend fun status(): NzbStatusResp
    @GET("jsonrpc/listgroups") suspend fun listgroups(): NzbGroupsResp
    @POST("jsonrpc") suspend fun rpc(@Body req: NzbRpcReq): NzbBoolResp
    @POST("jsonrpc") suspend fun history(@Body req: NzbHistoryReq): NzbHistoryResp
    @POST("jsonrpc") suspend fun rpcJson(@Body body: JsonObject): NzbBoolResp
    @POST("jsonrpc") suspend fun rpcInt(@Body body: JsonObject): NzbIntResp
    @POST("jsonrpc") suspend fun version(@Body req: NzbRpcReq): NzbStringResp
}

internal fun appendUrlBody(url: String, category: String): JsonObject =
    buildJsonObject {
        put("method", "append")
        putJsonArray("params") {
            add("")        // NZBFilename
            add(url)       // NZBContent (a URL is fetched by NZBGet)
            add(category)  // Category
            add(0)         // Priority
            add(false)     // AddToTop
            add(false)     // AddPaused
            add("")        // DupeKey
            add(0)         // DupeScore
            add("SCORE")   // DupeMode
        }
    }

internal fun editqueueBody(command: String, editText: String, id: Int): JsonObject =
    buildJsonObject {
        put("method", "editqueue")
        putJsonArray("params") {
            add(command)
            add(0) // offset
            add(editText)
            addJsonArray { add(id) }
        }
    }

internal fun rateBody(kbps: Int): JsonObject =
    buildJsonObject {
        put("method", "rate")
        putJsonArray("params") { add(kbps) }
    }

// ---- ntfy ----

suspend fun runNzbgetPause(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpc(NzbRpcReq("pausedownload"))
        if (r.result) "paused" else "error: NZBGet did not accept pause"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun runNzbgetResume(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpc(NzbRpcReq("resumedownload"))
        if (r.result) "resumed" else "error: NZBGet did not accept resume"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun nzbgetQueue(config: ServiceConfig): List<NzbQueueItem> = withContext(Dispatchers.IO) {
    apiFor<NzbgetApi>(config, basicHeader(config)).listgroups().result.map {
        NzbQueueItem(it.NZBID, it.NZBName, it.Status, it.FileSizeMB, it.RemainingSizeMB)
    }
}

suspend fun nzbgetHistory(config: ServiceConfig, hidden: Boolean): List<NzbHistoryEntry> = withContext(Dispatchers.IO) {
    apiFor<NzbgetApi>(config, basicHeader(config)).history(NzbHistoryReq(params = listOf(hidden))).result.map {
        NzbHistoryEntry(it.NZBID, it.Name, it.Status, it.FileSizeMB)
    }
}

/** editqueue command (GroupPause/GroupDelete/HistoryRedownload/…) on a single item. */
suspend fun runNzbEditQueue(config: ServiceConfig, command: String, id: Int, editText: String = ""): String =
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcJson(editqueueBody(command, editText, id))
            if (r.result) "ok" else "error: NZBGet rejected $command"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

/** Sets the global download speed limit in KB/s (0 = unlimited). */
suspend fun runNzbRate(config: ServiceConfig, kbps: Int): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcJson(rateBody(kbps))
        if (r.result) (if (kbps == 0) "no speed limit" else "limit ${kbps / 1024} MB/s") else "error"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Adds an NZB by URL (NZBGet fetches it). Optional category. */
suspend fun runNzbAppendUrl(config: ServiceConfig, url: String, category: String): String =
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcInt(appendUrlBody(url.trim(), category.trim()))
            if (r.result > 0) "added (id ${r.result})" else "error: NZBGet rejected the URL"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

/** Server status + version for the details dialog. */
suspend fun nzbServerDetails(config: ServiceConfig): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    val api = apiFor<NzbgetApi>(config, basicHeader(config))
    val st = api.status().result
    val version = runCatching { api.version(NzbRpcReq("version")).result }.getOrDefault("?")
    listOf(
        "Version" to version,
        "State" to if (st.DownloadPaused) "paused" else "active",
        "Rate" to "${st.DownloadRate / 1024} KB/s",
        "Remaining" to "${st.RemainingSizeMB} MB",
        "Downloaded" to "${st.DownloadedSizeMB} MB",
        "Free disk" to "${st.FreeDiskSpaceMB} MB",
        "Cache" to "${st.ArticleCacheMB} MB",
        "Threads" to st.ThreadCount.toString(),
        "Uptime" to "${st.UpTimeSec / 3600}h ${(st.UpTimeSec % 3600) / 60}m",
    )
}

internal suspend fun nzbgetStatus(config: ServiceConfig): ServiceStatus {
    val auth = Credentials.basic(config.username, config.password)
    val api = apiFor<NzbgetApi>(config, mapOf("Authorization" to auth))
    val st = api.status().result
    val queue = runCatching { api.listgroups().result.size }.getOrDefault(0)
    return ServiceStatus(
        ok = true,
        note = if (st.DownloadPaused) "⏸ paused" else "▶ active",
        stats = listOf(
            "KB/s" to (st.DownloadRate / 1024).toString(),
            "Queue" to queue.toString(),
            "MB left" to st.RemainingSizeMB.toString(),
        ),
    )
}
