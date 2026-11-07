package org.phioster.sanctumd.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ---- SyncPlay: watching together through the Jellyfin server (2026-11-07) ----
//
// The server owns the group. A client never plays or pauses on its own while in one: it asks the
// server, and the server answers every member with a command and a moment ("unpause at 11:07:03")
// on the websocket. Members convert that moment into their own clock and act then, which is what
// keeps two players apart by a few hundred milliseconds instead of seconds.

internal interface SyncPlayApi {
    @GET("SyncPlay/List") suspend fun list(): List<JsonObject>
    @GET("SyncPlay/{id}") suspend fun group(@Path("id") id: String): JsonObject
    @POST("SyncPlay/New") suspend fun create(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Join") suspend fun join(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Leave") suspend fun leave(): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/SetNewQueue") suspend fun setNewQueue(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Pause") suspend fun pause(): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Unpause") suspend fun unpause(): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Seek") suspend fun seek(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Ready") suspend fun ready(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @POST("SyncPlay/Ping") suspend fun ping(@Body body: JsonObject): retrofit2.Response<ResponseBody>
    @GET("GetUtcTime") suspend fun utcTime(): JsonObject
}

/** A SyncPlay group as the list and the header show it. */
data class SyncGroup(val id: String, val name: String, val participants: List<String>, val state: String)

/** "Play this item from here", sent to every member when someone starts something. */
data class SyncQueue(val itemId: String, val playlistItemId: String, val startMs: Long, val isPlaying: Boolean)

/** A playback command for one moment, already converted to this phone's clock. */
data class SyncCommand(val type: String, val atLocalMs: Long, val positionMs: Long, val playlistItemId: String)

private const val TICKS = 10_000L

internal fun syncGroupOf(o: JsonObject) = SyncGroup(
    id = jsStr(o, "GroupId").orEmpty(),
    name = jsStr(o, "GroupName").orEmpty(),
    participants = (o["Participants"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
    state = jsStr(o, "State").orEmpty(),
)

/**
 * Clock offset from one NTP-style exchange: [sentAt] and [receivedAt] on this phone, the two
 * stamps from the server. Returns (offset server minus local, round trip without server time).
 */
internal fun clockSample(sentAt: Long, serverReceived: Long, serverSent: Long, receivedAt: Long): Pair<Long, Long> {
    val offset = ((serverReceived - sentAt) + (serverSent - receivedAt)) / 2
    val rtt = (receivedAt - sentAt) - (serverSent - serverReceived)
    return offset to rtt
}

/** The playing entry of a PlayQueue update, or null when the queue is empty. */
internal fun syncQueueOf(data: JsonObject): SyncQueue? {
    val list = (data["Playlist"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val entry = list.getOrNull(jsInt(data, "PlayingItemIndex") ?: 0) ?: return null
    return SyncQueue(
        itemId = jsStr(entry, "ItemId").orEmpty(),
        playlistItemId = jsStr(entry, "PlaylistItemId").orEmpty(),
        startMs = (jsLong(data, "StartPositionTicks") ?: 0L) / TICKS,
        isPlaying = jsBool(data, "IsPlaying") == true,
    )
}

object SyncPlay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var config: ServiceConfig? = null
    private var token: String = ""
    private var socket: WebSocket? = null
    private var keepAlive: Job? = null
    private var clockJob: Job? = null

    /** Server clock minus phone clock, in ms. */
    @Volatile private var offsetMs = 0L
    @Volatile private var rttMs = 0L
    @Volatile var playlistItemId: String = ""
        private set

    private val _group = MutableStateFlow<SyncGroup?>(null)
    val group: StateFlow<SyncGroup?> = _group
    private val _queue = MutableSharedFlow<SyncQueue>(replay = 1, extraBufferCapacity = 4)
    val queue: SharedFlow<SyncQueue> = _queue
    private val _commands = MutableSharedFlow<SyncCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<SyncCommand> = _commands
    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short lines for the screen: who joined, who left, why something failed. */
    val notice: SharedFlow<String> = _notice

    val active: Boolean get() = _group.value != null

    /** Keep the player on the group's clock while playing (like Jellyfin's "sync correction"). */
    @Volatile var correction: Boolean = true
        private set
    fun loadPrefs(context: android.content.Context) {
        correction = context.getSharedPreferences("syncplay", 0).getBoolean("correction", true)
    }
    fun setCorrection(context: android.content.Context, on: Boolean) {
        correction = on
        context.getSharedPreferences("syncplay", 0).edit().putBoolean("correction", on).apply()
    }

    private fun api(): SyncPlayApi = apiFor(config!!, jellyfinAuth(token))

    fun serverNowIso(): String = java.time.Instant.ofEpochMilli(System.currentTimeMillis() + offsetMs).toString()
    private fun toLocal(iso: String?): Long =
        runCatching { java.time.Instant.parse(iso).toEpochMilli() - offsetMs }.getOrDefault(System.currentTimeMillis())

    suspend fun groups(cfg: ServiceConfig): List<SyncGroup> = withContext(Dispatchers.IO) {
        connect(cfg)
        api().list().map(::syncGroupOf)
    }

    suspend fun create(cfg: ServiceConfig, name: String): String = withContext(Dispatchers.IO) {
        connect(cfg)
        val r = api().create(buildJsonObject { put("GroupName", name) })
        if (r.isSuccessful) "group created" else "error: HTTP ${r.code()}"
    }

    suspend fun join(cfg: ServiceConfig, groupId: String): String = withContext(Dispatchers.IO) {
        connect(cfg)
        val r = api().join(buildJsonObject { put("GroupId", groupId) })
        if (r.isSuccessful) "joined" else "error: HTTP ${r.code()}"
    }

    suspend fun leave(): String = withContext(Dispatchers.IO) {
        if (config == null) return@withContext "not in a group"
        val r = runCatching { api().leave() }.getOrNull()
        _group.value = null
        disconnect()
        if (r?.isSuccessful == true) "left the group" else "left"
    }

    /** Someone here started [itemId]: the server turns that into a queue for every member. */
    fun requestPlay(itemId: String, startMs: Long) = post {
        api().setNewQueue(buildJsonObject {
            putJsonArray("PlayingQueue") { add(JsonPrimitive(itemId)) }
            put("PlayingItemPosition", 0)
            put("StartPositionTicks", startMs * TICKS)
        })
    }
    fun requestPause() = post { api().pause() }
    fun requestUnpause() = post { api().unpause() }
    fun requestSeek(positionMs: Long) = post { api().seek(buildJsonObject { put("PositionTicks", positionMs.coerceAtLeast(0) * TICKS) }) }

    /** This player has the item loaded at [positionMs] and can go when the group goes. */
    fun ready(positionMs: Long, isPlaying: Boolean) = post {
        api().ready(buildJsonObject {
            put("When", serverNowIso())
            put("PositionTicks", positionMs.coerceAtLeast(0) * TICKS)
            put("IsPlaying", isPlaying)
            put("PlaylistItemId", playlistItemId)
        })
    }

    private fun post(call: suspend () -> retrofit2.Response<ResponseBody>) {
        if (config == null) return
        scope.launch {
            val r = runCatching { call() }.getOrNull()
            if (r == null || !r.isSuccessful) _notice.tryEmit("SyncPlay: request failed${r?.let { " (HTTP ${it.code()})" } ?: ""}")
        }
    }

    // ---- connection ----

    private suspend fun connect(cfg: ServiceConfig) {
        if (socket != null && config?.id == cfg.id) return
        disconnect()
        config = cfg
        token = jellyfinAccessToken(cfg)
        syncClock()
        val wsUrl = cfg.normalizedBaseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "socket"
        socket = okClient(cfg, jellyfinAuth(token)).newWebSocket(Request.Builder().url(wsUrl).build(), listener)
        clockJob = scope.launch {
            while (isActive) {
                delay(60_000)
                runCatching { syncClock() }
                if (active) runCatching { api().ping(buildJsonObject { put("Ping", rttMs) }) }
            }
        }
    }

    private fun disconnect() {
        keepAlive?.cancel(); keepAlive = null
        clockJob?.cancel(); clockJob = null
        socket?.close(1000, null); socket = null
    }

    /** A few exchanges with the server clock; the one with the shortest round trip wins. */
    private suspend fun syncClock() {
        val best = (1..5).mapNotNull {
            runCatching {
                val sent = System.currentTimeMillis()
                val o = api().utcTime()
                val got = System.currentTimeMillis()
                clockSample(
                    sent,
                    java.time.Instant.parse(jsStr(o, "RequestReceptionTime")).toEpochMilli(),
                    java.time.Instant.parse(jsStr(o, "ResponseTransmissionTime")).toEpochMilli(),
                    got,
                )
            }.getOrNull()
        }.minByOrNull { it.second } ?: return
        offsetMs = best.first
        rttMs = best.second
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (jsStr(msg, "MessageType")) {
                "ForceKeepAlive" -> {
                    val every = ((jsInt(msg, "Data") ?: 60) * 1000L / 2).coerceAtLeast(5_000)
                    keepAlive?.cancel()
                    keepAlive = scope.launch {
                        while (isActive) { webSocket.send("{\"MessageType\":\"KeepAlive\"}"); delay(every) }
                    }
                }
                "SyncPlayCommand" -> (msg["Data"] as? JsonObject)?.let { d ->
                    _commands.tryEmit(
                        SyncCommand(
                            type = jsStr(d, "Command").orEmpty(),
                            atLocalMs = toLocal(jsStr(d, "When")),
                            positionMs = (jsLong(d, "PositionTicks") ?: 0L) / TICKS,
                            playlistItemId = jsStr(d, "PlaylistItemId").orEmpty(),
                        ),
                    )
                }
                "SyncPlayGroupUpdate" -> (msg["Data"] as? JsonObject)?.let(::onGroupUpdate)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) return
            socket = null
            if (_group.value != null) _notice.tryEmit("SyncPlay connection lost, reconnecting…")
            val cfg = config ?: return
            val gid = _group.value?.id
            scope.launch {
                delay(3_000)
                runCatching { connect(cfg); if (gid != null) api().join(buildJsonObject { put("GroupId", gid) }) }
            }
        }
    }

    private fun onGroupUpdate(u: JsonObject) {
        val data = u["Data"]
        when (jsStr(u, "Type")) {
            "GroupJoined" -> (data as? JsonObject)?.let { _group.value = syncGroupOf(it) }
            "UserJoined", "UserLeft" -> {
                val who = (data as? JsonPrimitive)?.content.orEmpty()
                _notice.tryEmit(if (jsStr(u, "Type") == "UserJoined") "$who joined" else "$who left")
                val id = _group.value?.id ?: return
                scope.launch { runCatching { api().group(id) }.getOrNull()?.let { _group.value = syncGroupOf(it) } }
            }
            "StateUpdate" -> (data as? JsonObject)?.let { d ->
                _group.value = _group.value?.copy(state = jsStr(d, "State").orEmpty())
            }
            "PlayQueue" -> (data as? JsonObject)?.let { d ->
                val q = syncQueueOf(d) ?: return
                playlistItemId = q.playlistItemId
                // Only a new item reloads the player; reorders and repeat changes don't concern it.
                if (jsStr(d, "Reason") in setOf("NewPlaylist", "SetCurrentItem", "NextItem", "PreviousItem")) _queue.tryEmit(q)
            }
            "GroupLeft", "NotInGroup", "GroupDoesNotExist" -> { _group.value = null; disconnect() }
            "LibraryAccessDenied" -> _notice.tryEmit("SyncPlay: someone in the group can't see this item")
        }
    }
}
