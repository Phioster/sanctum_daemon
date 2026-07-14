package org.phioster.nexarr.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ServiceType
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private const val MB_AUTH =
    "MediaBrowser Client=\"Nexarr\", Device=\"Android\", DeviceId=\"nexarr\", Version=\"0.3.0\""

/** config.id -> (jellyfin access token, user label) once a login has succeeded. */
private val jellyfinSession = mutableMapOf<String, Pair<String, String>>()

/** Drop a cached Jellyfin login token (e.g. after its config was edited). */
fun clearJellyfinSession(id: String) {
    jellyfinSession.remove(id)
}

private fun okClient(config: ServiceConfig, authHeaders: Map<String, String>): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            authHeaders.forEach { (k, v) -> if (v.isNotBlank()) b.header(k, v) }
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) b.header(k, v) }
            chain.proceed(b.build())
        }
        .addInterceptor { chain ->
            // TEMP debug logging (no secrets: only method/url/bodies)
            val request = chain.request()
            val reqBody = request.body?.let { okio.Buffer().also { buf -> it.writeTo(buf) }.readUtf8() } ?: ""
            val response = chain.proceed(request)
            val respStr = runCatching { response.peekBody(1_000_000).string() }.getOrDefault("<unreadable>")
            android.util.Log.d("NEXARR_HTTP", "${request.method} ${request.url}\nREQ: $reqBody\nRESP: ${respStr.take(2000)}")
            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

private inline fun <reified T> apiFor(config: ServiceConfig, authHeaders: Map<String, String>): T =
    Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl)
        .client(okClient(config, authHeaders))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

private fun apiKeyHeader(config: ServiceConfig) = mapOf("X-Api-Key" to config.apiKey)
private fun basicHeader(config: ServiceConfig) =
    mapOf("Authorization" to Credentials.basic(config.username, config.password))

@Serializable private data class CommandReq(val name: String)

// ---- Jellyfin ----

@Serializable
private data class JfCounts(
    val MovieCount: Int = 0,
    val SeriesCount: Int = 0,
    val EpisodeCount: Int = 0,
    val SongCount: Int = 0,
)

@Serializable private data class JfSession(val NowPlayingItem: JfNowPlaying? = null)
@Serializable private data class JfNowPlaying(val Name: String? = null)

@Serializable private data class JfAuthReq(val Username: String, val Pw: String)
@Serializable private data class JfAuthResp(val AccessToken: String = "", val User: JfUser = JfUser())
@Serializable private data class JfUser(val Name: String = "", val Policy: JfPolicy = JfPolicy())
@Serializable private data class JfPolicy(val IsAdministrator: Boolean = false)

private interface JellyfinAuthApi {
    @POST("Users/AuthenticateByName") suspend fun authenticate(@Body body: JfAuthReq): JfAuthResp
}

private interface JellyfinApi {
    @GET("Items/Counts") suspend fun counts(): JfCounts
    @GET("Sessions") suspend fun sessions(): List<JfSession>
    @POST("Library/Refresh") suspend fun refreshLibrary(): Response<ResponseBody>
}

// ---- Radarr ----

@Serializable private data class RadarrMovie(val hasFile: Boolean = false, val monitored: Boolean = false)
@Serializable private data class RadarrPage(val totalRecords: Int = 0)

private interface RadarrApi {
    @GET("api/v3/movie") suspend fun movies(): List<RadarrMovie>
    @GET("api/v3/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v3/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v3/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Sonarr (api/v3) ----

@Serializable private data class SonarrSeries(val monitored: Boolean = false)

private interface SonarrApi {
    @GET("api/v3/series") suspend fun series(): List<SonarrSeries>
    @GET("api/v3/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v3/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v3/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Lidarr (api/v1) ----

@Serializable private data class LidarrArtist(val monitored: Boolean = false)

private interface LidarrApi {
    @GET("api/v1/artist") suspend fun artists(): List<LidarrArtist>
    @GET("api/v1/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v1/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v1/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Prowlarr (api/v1) ----

@Serializable private data class ProwlarrIndexerStat(
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
)

@Serializable private data class ProwlarrStats(val indexers: List<ProwlarrIndexerStat> = emptyList())

private interface ProwlarrApi {
    @GET("api/v1/indexerstats") suspend fun stats(): ProwlarrStats
    @POST("api/v1/indexer/testall") suspend fun testAll(): Response<ResponseBody>
}

// ---- Seerr (Overseerr-compatible, api/v1) ----

@Serializable private data class SeerrCounts(
    val pending: Int = 0,
    val approved: Int = 0,
    val available: Int = 0,
)

private interface SeerrApi {
    @GET("api/v1/request/count") suspend fun counts(): SeerrCounts
}

// ---- NZBGet (JSON-RPC over HTTP + Basic auth) ----

@Serializable private data class NzbStatusResp(val result: NzbStatus = NzbStatus())
@Serializable private data class NzbStatus(
    val DownloadRate: Long = 0,
    val RemainingSizeMB: Long = 0,
    val DownloadPaused: Boolean = false,
)

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

private interface NzbgetApi {
    @GET("jsonrpc/status") suspend fun status(): NzbStatusResp
    @GET("jsonrpc/listgroups") suspend fun listgroups(): NzbGroupsResp
    @POST("jsonrpc") suspend fun rpc(@Body req: NzbRpcReq): NzbBoolResp
    @POST("jsonrpc") suspend fun history(@Body req: NzbHistoryReq): NzbHistoryResp
}

/** Runs the appropriate status calls for a service and maps them to a card. */
suspend fun fetchStatus(config: ServiceConfig): ServiceStatus = withContext(Dispatchers.IO) {
    try {
        when (config.type) {
            ServiceType.JELLYFIN -> jellyfinStatus(config)
            ServiceType.RADARR -> radarrStatus(config)
            ServiceType.SONARR -> sonarrStatus(config)
            ServiceType.LIDARR -> lidarrStatus(config)
            ServiceType.PROWLARR -> prowlarrStatus(config)
            ServiceType.SEERR -> seerrStatus(config)
            ServiceType.NZBGET -> nzbgetStatus(config)
        }
    } catch (t: Throwable) {
        ServiceStatus(ok = false, error = t.message ?: t.javaClass.simpleName)
    }
}

/** Returns the token to use for Jellyfin data calls (API key, or a login token). */
private suspend fun jellyfinAccessToken(config: ServiceConfig): String {
    if (!config.useLogin) return config.apiKey
    jellyfinSession[config.id]?.let { return it.first }
    val resp = apiFor<JellyfinAuthApi>(config, mapOf("Authorization" to MB_AUTH))
        .authenticate(JfAuthReq(config.username, config.password))
    val label = (if (resp.User.Policy.IsAdministrator) "admin: " else "user: ") + resp.User.Name
    jellyfinSession[config.id] = resp.AccessToken to label
    return resp.AccessToken
}

private suspend fun jellyfinStatus(config: ServiceConfig): ServiceStatus {
    val token = jellyfinAccessToken(config)
    val note = if (config.useLogin) jellyfinSession[config.id]?.second else null
    val jf = apiFor<JellyfinApi>(config, mapOf("X-Emby-Token" to token))
    val counts = jf.counts()
    val playing = jf.sessions().count { it.NowPlayingItem != null }
    return ServiceStatus(
        ok = true,
        note = note,
        stats = listOf(
            "Movies" to counts.MovieCount.toString(),
            "Series" to counts.SeriesCount.toString(),
            "Playing" to playing.toString(),
        ),
    )
}

/** Triggers a full library scan on Jellyfin. Returns a user-facing result line. */
suspend fun runJellyfinScan(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val resp = apiFor<JellyfinApi>(config, mapOf("X-Emby-Token" to token)).refreshLibrary()
        if (resp.isSuccessful) "library scan started" else "error: HTTP ${resp.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

private fun okOr(resp: Response<ResponseBody>, success: String): String =
    if (resp.isSuccessful) success else "error: HTTP ${resp.code()}"

/** Radarr/Sonarr/Lidarr: trigger a search for all missing monitored items. */
suspend fun runSearchMissing(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val resp = when (config.type) {
            ServiceType.RADARR -> apiFor<RadarrApi>(config, apiKeyHeader(config)).command(CommandReq("MissingMoviesSearch"))
            ServiceType.SONARR -> apiFor<SonarrApi>(config, apiKeyHeader(config)).command(CommandReq("MissingEpisodeSearch"))
            ServiceType.LIDARR -> apiFor<LidarrApi>(config, apiKeyHeader(config)).command(CommandReq("MissingAlbumSearch"))
            else -> return@withContext "unsupported"
        }
        okOr(resp, "search started")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun runProwlarrTestAll(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<ProwlarrApi>(config, apiKeyHeader(config)).testAll(), "testing indexers")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

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

private suspend fun radarrStatus(config: ServiceConfig): ServiceStatus {
    val api = apiFor<RadarrApi>(config, mapOf("X-Api-Key" to config.apiKey))
    val movies = api.movies().size
    val missing = api.missing().totalRecords
    val queue = api.queue().totalRecords
    return ServiceStatus(
        ok = true,
        stats = listOf(
            "Movies" to movies.toString(),
            "Missing" to missing.toString(),
            "Queue" to queue.toString(),
        ),
    )
}

private suspend fun sonarrStatus(config: ServiceConfig): ServiceStatus {
    val api = apiFor<SonarrApi>(config, mapOf("X-Api-Key" to config.apiKey))
    return ServiceStatus(
        ok = true,
        stats = listOf(
            "Series" to api.series().size.toString(),
            "Missing" to api.missing().totalRecords.toString(),
            "Queue" to api.queue().totalRecords.toString(),
        ),
    )
}

private suspend fun lidarrStatus(config: ServiceConfig): ServiceStatus {
    val api = apiFor<LidarrApi>(config, mapOf("X-Api-Key" to config.apiKey))
    return ServiceStatus(
        ok = true,
        stats = listOf(
            "Artists" to api.artists().size.toString(),
            "Missing" to api.missing().totalRecords.toString(),
            "Queue" to api.queue().totalRecords.toString(),
        ),
    )
}

private suspend fun prowlarrStatus(config: ServiceConfig): ServiceStatus {
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

private suspend fun seerrStatus(config: ServiceConfig): ServiceStatus {
    val api = apiFor<SeerrApi>(config, mapOf("X-Api-Key" to config.apiKey))
    val c = api.counts()
    return ServiceStatus(
        ok = true,
        stats = listOf(
            "Pending" to c.pending.toString(),
            "Approved" to c.approved.toString(),
            "Available" to c.available.toString(),
        ),
    )
}

private suspend fun nzbgetStatus(config: ServiceConfig): ServiceStatus {
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
