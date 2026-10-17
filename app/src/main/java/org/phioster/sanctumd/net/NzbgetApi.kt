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

@Serializable internal data class NzbStatusResp(val result: NzbStatus = NzbStatus())
@Serializable internal data class NzbStatus(
    val DownloadRate: Long = 0,
    val RemainingSizeMB: Long = 0,
    val DownloadPaused: Boolean = false,
    val DownloadedSizeMB: Long = 0,
    val FreeDiskSpaceMB: Long = 0,
    val ArticleCacheMB: Long = 0,
    val UpTimeSec: Long = 0,
    val ThreadCount: Int = 0,
    val PostPaused: Boolean = false,
    val ResumeTime: Long = 0,
)

@Serializable internal data class NzbStringResp(val result: String = "")
@Serializable internal data class NzbIntResp(val result: Int = 0)

@Serializable internal data class NzbGroupsResp(val result: List<NzbGroup> = emptyList())
@Serializable internal data class NzbGroup(
    val NZBID: Int = 0,
    val NZBName: String = "",
    val Status: String = "",
    val FileSizeMB: Long = 0,
    val RemainingSizeMB: Long = 0,
    val Category: String = "",
    val MaxPriority: Int = 0,
    val Health: Int = 1000,
    val MinPostTime: Long = 0,
    val PostInfoText: String = "",
    val PostStageProgress: Int = 0,
)

@Serializable internal data class NzbRpcReq(val method: String, val params: List<String> = emptyList(), val id: Int = 1)
@Serializable internal data class NzbBoolResp(val result: Boolean = false)
@Serializable internal data class NzbLogMsg(val ID: Int = 0, val Kind: String = "", val Time: Long = 0, val Text: String = "")
@Serializable internal data class NzbLogResp(val result: List<NzbLogMsg> = emptyList())

@Serializable internal data class NzbHistoryReq(val params: List<Boolean>, val method: String = "history", val id: Int = 1)
@Serializable internal data class NzbHistoryResp(val result: List<NzbHistoryItem> = emptyList())
@Serializable internal data class NzbHistoryItem(
    val NZBID: Int = 0,
    val Name: String = "",
    val Status: String = "",
    val FileSizeMB: Long = 0,
    val ParStatus: String = "NONE",
    val UnpackStatus: String = "NONE",
    val MoveStatus: String = "NONE",
    val ScriptStatus: String = "NONE",
    val DeleteStatus: String = "NONE",
    val MarkStatus: String = "NONE",
    /** Per mille; below [CriticalHealth] the download cannot be repaired. */
    val Health: Int = 1000,
    val CriticalHealth: Int = 1000,
)

/**
 * The stage that broke, from the sub-statuses NZBGet reports next to the summary `Status`
 * (names and values as in its XmlRpc.cpp). A success, or a reason NZBGet doesn't give, is blank.
 */
internal fun nzbFailureReason(h: NzbHistoryItem): String {
    if (h.Status.startsWith("SUCCESS")) return ""
    val parts = buildList {
        if (h.Health < 1000) {
            // Each number formatted on its own: re-formatting "87.3 %" would read "% (" as a flag and throw.
            val health = "health %.1f %%".format(h.Health / 10.0)
            add(if (h.Health < h.CriticalHealth) health + " (needs %.1f %%)".format(h.CriticalHealth / 10.0) else health)
        }
        when (h.DeleteStatus) {
            "HEALTH" -> add("deleted: too many missing articles")
            "DUPE" -> add("deleted: duplicate")
            "BAD" -> add("marked bad")
            "MANUAL" -> add("deleted by hand")
        }
        when (h.ParStatus) {
            "FAILURE" -> add("repair failed")
            "MANUAL" -> add("repair needs manual action")
        }
        when (h.UnpackStatus) {
            "FAILURE" -> add("unpack failed")
            "SPACE" -> add("unpack: disk full")
            "PASSWORD" -> add("unpack: password protected")
        }
        if (h.MoveStatus == "FAILURE") add("move failed")
        if (h.ScriptStatus == "FAILURE") add("script failed")
    }
    return parts.joinToString(" · ")
}

internal interface NzbgetApi {
    @GET("jsonrpc/status") suspend fun status(): NzbStatusResp
    @GET("jsonrpc/listgroups") suspend fun listgroups(): NzbGroupsResp
    @POST("jsonrpc") suspend fun rpc(@Body req: NzbRpcReq): NzbBoolResp
    @POST("jsonrpc") suspend fun history(@Body req: NzbHistoryReq): NzbHistoryResp
    @POST("jsonrpc") suspend fun rpcJson(@Body body: JsonObject): NzbBoolResp
    @POST("jsonrpc") suspend fun logRpc(@Body body: JsonObject): NzbLogResp
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

internal fun editqueueBody(command: String, editText: String, id: Int): JsonObject = editqueueBody(command, editText, listOf(id))

internal fun editqueueBody(command: String, editText: String, ids: List<Int>): JsonObject =
    buildJsonObject {
        put("method", "editqueue")
        putJsonArray("params") {
            add(command)
            add(0) // offset
            add(editText)
            addJsonArray { ids.forEach { add(it) } }
        }
    }

internal fun rateBody(kbps: Int): JsonObject =
    buildJsonObject {
        put("method", "rate")
        putJsonArray("params") { add(kbps) }
    }

// ---- ntfy ----

suspend fun runNzbgetPause(config: ServiceConfig): String = destructive("pause the NZBGet queue") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpc(NzbRpcReq("pausedownload"))
            if (r.result) "paused" else "error: NZBGet did not accept pause"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun runNzbgetResume(config: ServiceConfig): String = destructive("resume the NZBGet queue") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpc(NzbRpcReq("resumedownload"))
            if (r.result) "resumed" else "error: NZBGet did not accept resume"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/**
 * Seconds until each item is done. The queue downloads in order, so an item waits for the ones
 * ahead of it: its ETA is everything remaining up to and including it, at the current rate.
 * Null for all when nothing is moving; a paused item adds nothing and gets null itself.
 */
internal fun queueEtas(remainingMb: List<Long>, paused: List<Boolean>, rateBytesPerSec: Long): List<Long?> {
    if (rateBytesPerSec <= 0) return remainingMb.map { null }
    var ahead = 0L
    return remainingMb.indices.map { i ->
        if (paused[i]) {
            null
        } else {
            ahead += remainingMb[i]
            ahead * 1024 * 1024 / rateBytesPerSec
        }
    }
}

/** The queue with [NzbQueueItem.etaSec] filled in from the current [rateBytesPerSec]. */
internal fun withEtas(queue: List<NzbQueueItem>, rateBytesPerSec: Long): List<NzbQueueItem> {
    val etas = queueEtas(queue.map { it.remainingMb }, queue.map { it.status.startsWith("PAUSED") }, rateBytesPerSec)
    return queue.mapIndexed { i, q -> q.copy(etaSec = etas[i]) }
}

suspend fun nzbgetQueue(config: ServiceConfig): List<NzbQueueItem> = withContext(Dispatchers.IO) {
    val groups = apiFor<NzbgetApi>(config, basicHeader(config)).listgroups().result
    val now = System.currentTimeMillis() / 1000
    groups.map {
        NzbQueueItem(
            it.NZBID, it.NZBName, it.Status, it.FileSizeMB, it.RemainingSizeMB,
            category = it.Category,
            priority = it.MaxPriority,
            health = it.Health,
            ageDays = if (it.MinPostTime > 0) ((now - it.MinPostTime) / 86_400).toInt() else -1,
            postInfo = it.PostInfoText,
            postProgress = it.PostStageProgress,
        )
    }
}

suspend fun nzbgetPauseState(config: ServiceConfig): org.phioster.sanctumd.model.NzbPauseState = withContext(Dispatchers.IO) {
    val s = apiFor<NzbgetApi>(config, basicHeader(config)).status().result
    org.phioster.sanctumd.model.NzbPauseState(s.DownloadPaused, s.ResumeTime, s.PostPaused, if (s.DownloadPaused) 0L else s.DownloadRate)
}

/** Pauses downloading and lets NZBGet itself resume after [minutes], no app needed. */
suspend fun runNzbPauseFor(config: ServiceConfig, minutes: Int): String = destructive("pause NZBGet for $minutes min") {
    withContext(Dispatchers.IO) {
        try {
            val api = apiFor<NzbgetApi>(config, basicHeader(config))
            if (!api.rpc(NzbRpcReq("pausedownload")).result) return@withContext "error: pause refused"
            val ok = api.rpcJson(
                buildJsonObject {
                    put("method", "scheduleresume")
                    putJsonArray("params") { add(minutes * 60) }
                },
            ).result
            if (ok) "paused for $minutes min" else "error: paused, but the timer was refused"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Holds or releases post-processing (repair, unpack, scripts) without touching downloads. */
suspend fun runNzbPostPause(config: ServiceConfig, pause: Boolean): String = destructive("${if (pause) "pause" else "resume"} NZBGet post-processing") {
    withContext(Dispatchers.IO) {
        try {
            val ok = apiFor<NzbgetApi>(config, basicHeader(config)).rpc(NzbRpcReq(if (pause) "pausepost" else "resumepost")).result
            if (ok) (if (pause) "post-processing paused" else "post-processing resumed") else "error: refused"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** editqueue on several items in one call; NZBGet takes the id list as it is. */
suspend fun runNzbEditMany(config: ServiceConfig, command: String, ids: List<Int>, editText: String = ""): String =
    destructive("NZBGet $command on ${ids.size} items") {
        withContext(Dispatchers.IO) {
            try {
                val ok = apiFor<NzbgetApi>(config, basicHeader(config)).rpcJson(editqueueBody(command, editText, ids)).result
                if (ok) "done (${ids.size})" else "error: refused"
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

suspend fun nzbgetHistory(config: ServiceConfig, hidden: Boolean): List<NzbHistoryEntry> = withContext(Dispatchers.IO) {
    apiFor<NzbgetApi>(config, basicHeader(config)).history(NzbHistoryReq(params = listOf(hidden))).result.map {
        NzbHistoryEntry(it.NZBID, it.Name, it.Status, it.FileSizeMB, nzbFailureReason(it))
    }
}

/** `log(IDFrom, NumberOfEntries)`: IDFrom 0 asks for the last [count] messages. */
internal fun logBody(count: Int): JsonObject = buildJsonObject {
    put("method", "log")
    putJsonArray("params") { add(0); add(count) }
}

/** `loadlog(NZBID, IDFrom, NumberOfEntries)`: one download's own messages. */
internal fun loadLogBody(nzbId: Int, count: Int): JsonObject = buildJsonObject {
    put("method", "loadlog")
    putJsonArray("params") { add(nzbId); add(0); add(count) }
}

private fun NzbLogMsg.toEntry() = org.phioster.sanctumd.model.NzbLogEntry(
    kind = Kind,
    time = if (Time > 0) java.text.SimpleDateFormat("dd.MM. HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(Time * 1000)) else "",
    text = Text,
)

/** NZBGet's own messages, newest first: server errors, unpack failures, health warnings. */
suspend fun nzbgetLog(config: ServiceConfig, count: Int = 200): List<org.phioster.sanctumd.model.NzbLogEntry> = withContext(Dispatchers.IO) {
    apiFor<NzbgetApi>(config, basicHeader(config)).logRpc(logBody(count)).result.sortedByDescending { it.ID }.map { it.toEntry() }
}

suspend fun nzbgetItemLog(config: ServiceConfig, nzbId: Int, count: Int = 300): List<org.phioster.sanctumd.model.NzbLogEntry> =
    withContext(Dispatchers.IO) {
        apiFor<NzbgetApi>(config, basicHeader(config)).logRpc(loadLogBody(nzbId, count)).result.sortedByDescending { it.ID }.map { it.toEntry() }
    }

/** editqueue command (GroupPause/GroupDelete/HistoryRedownload/…) on a single item. */
suspend fun runNzbEditQueue(config: ServiceConfig, command: String, id: Int, editText: String = ""): String =
    destructive("NZBGet $command on item $id") {
        withContext(Dispatchers.IO) {
            try {
                val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcJson(editqueueBody(command, editText, id))
                if (r.result) "ok" else "error: NZBGet rejected $command"
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Sets the global download speed limit in KB/s (0 = unlimited). */
suspend fun runNzbRate(config: ServiceConfig, kbps: Int): String = destructive("set the NZBGet speed limit to $kbps KB/s") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcJson(rateBody(kbps))
            if (r.result) (if (kbps == 0) "no speed limit" else "limit ${kbps / 1024} MB/s") else "error"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Adds an NZB by URL (NZBGet fetches it). Optional category. */
suspend fun runNzbAppendUrl(config: ServiceConfig, url: String, category: String): String =
    destructive("add a link from ${hostOnly(url)} to the NZBGet queue") {
        withContext(Dispatchers.IO) {
            try {
                val r = apiFor<NzbgetApi>(config, basicHeader(config)).rpcInt(appendUrlBody(url.trim(), category.trim()))
                if (r.result > 0) "added (id ${r.result})" else "error: NZBGet rejected the URL"
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
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
        note = if (st.DownloadPaused) "paused" else "active",
        stats = listOf(
            "KB/s" to (st.DownloadRate / 1024).toString(),
            "Queue" to queue.toString(),
            "MB left" to st.RemainingSizeMB.toString(),
        ),
    )
}
