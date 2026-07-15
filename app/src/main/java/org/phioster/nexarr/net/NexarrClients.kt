package org.phioster.nexarr.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.nexarr.model.ArrDetail
import org.phioster.nexarr.model.ArrEpisode
import org.phioster.nexarr.model.ArrHistoryItem
import org.phioster.nexarr.model.ArrLibraryItem
import org.phioster.nexarr.model.ArrLookupItem
import org.phioster.nexarr.model.ArrMissingItem
import org.phioster.nexarr.model.ArrProfile
import org.phioster.nexarr.model.ArrQueueItem
import org.phioster.nexarr.model.ArrRelease
import org.phioster.nexarr.model.ArrSystemInfo
import org.phioster.nexarr.model.NzbHistoryEntry
import org.phioster.nexarr.model.NzbQueueItem
import org.phioster.nexarr.model.ProwlarrCategory
import org.phioster.nexarr.model.ProwlarrHistoryItem
import org.phioster.nexarr.model.ProwlarrIndexerItem
import org.phioster.nexarr.model.ProwlarrRelease
import org.phioster.nexarr.model.ProwlarrSystemInfo
import org.phioster.nexarr.model.ProwlarrTaskItem
import org.phioster.nexarr.model.SeerrIssueItem
import org.phioster.nexarr.model.SeerrRequestItem
import org.phioster.nexarr.model.SeerrSearchItem
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ServiceType
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true // JSON-RPC needs the default "method"/"id" fields in the body
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

// per-indexer stat rows (indexerstats has both a summary and per-indexer array)
@Serializable private data class ProwlarrIndexerStatRow(
    val indexerId: Int = 0,
    val indexerName: String = "",
    val numberOfQueries: Int = 0,
    val numberOfGrabs: Int = 0,
    val numberOfFailedQueries: Int = 0,
    val numberOfFailedGrabs: Int = 0,
)
@Serializable private data class ProwlarrStatsFull(
    val indexers: List<ProwlarrIndexerStatRow> = emptyList(),
)

@Serializable private data class ProwlarrIndexerRecord(
    val id: Int = 0,
    val name: String = "",
    val protocol: String = "",
    val enable: Boolean = false,
    val priority: Int = 0,
    val privacy: String = "",
)

@Serializable private data class ProwlarrIndexerStatusRecord(
    val indexerId: Int = 0,
    val disabledTill: String? = null,
)

@Serializable private data class ProwlarrCategoryRef(
    val id: Int = 0,
    val name: String = "",
)
@Serializable private data class ProwlarrReleaseRecord(
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

@Serializable private data class ProwlarrGrabReq(val guid: String, val indexerId: Int)

@Serializable private data class ProwlarrHistoryRec(
    val eventType: String = "",
    val date: String = "",
    val indexer: String = "",
    val data: ProwlarrHistoryData = ProwlarrHistoryData(),
)
@Serializable private data class ProwlarrHistoryData(val query: String? = null, val title: String? = null)
@Serializable private data class ProwlarrHistoryPage(val records: List<ProwlarrHistoryRec> = emptyList())

@Serializable private data class ProwlarrTaskRec(
    val name: String = "",
    val lastExecution: String? = null,
    val nextExecution: String? = null,
)

@Serializable private data class ProwlarrSystemStatusRec(val version: String = "")
@Serializable private data class ProwlarrHealthRec(val type: String = "", val message: String = "")

private interface ProwlarrApi {
    @GET("api/v1/indexerstats") suspend fun stats(): ProwlarrStats
    @POST("api/v1/indexer/testall") suspend fun testAll(): Response<ResponseBody>
    @GET("api/v1/indexer") suspend fun indexers(): List<ProwlarrIndexerRecord>
    @GET("api/v1/indexerstats") suspend fun statsFull(): ProwlarrStatsFull
    @GET("api/v1/indexerstatus") suspend fun indexerStatus(): List<ProwlarrIndexerStatusRecord>
    @GET("api/v1/indexer/{id}") suspend fun indexerRaw(@Path("id") id: Int): JsonObject
    @PUT("api/v1/indexer/{id}") suspend fun updateIndexer(@Path("id") id: Int, @Body body: JsonObject): Response<ResponseBody>
    @POST("api/v1/indexer/{id}/test") suspend fun testIndexer(@Path("id") id: Int): Response<ResponseBody>
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

@Serializable private data class SeerrCounts(
    val pending: Int = 0,
    val approved: Int = 0,
    val available: Int = 0,
)

@Serializable private data class SeerrMedia(val tmdbId: Int = 0, val mediaType: String = "")
@Serializable private data class SeerrUser(val displayName: String = "")
@Serializable private data class SeerrRequest(
    val id: Int = 0,
    val status: Int = 0,
    val type: String = "",
    val media: SeerrMedia = SeerrMedia(),
    val requestedBy: SeerrUser = SeerrUser(),
)
@Serializable private data class SeerrRequestPage(val results: List<SeerrRequest> = emptyList())
@Serializable private data class SeerrMeta(val title: String? = null, val name: String? = null)

@Serializable private data class SeerrIssue(
    val id: Int = 0,
    val issueType: Int = 0,
    val status: Int = 0,
    val media: SeerrMedia = SeerrMedia(),
    val createdBy: SeerrUser = SeerrUser(),
)
@Serializable private data class SeerrIssuePage(val results: List<SeerrIssue> = emptyList())

@Serializable private data class SeerrSearchResult(
    val id: Int = 0,
    val mediaType: String = "",
    val title: String? = null,        // movie
    val name: String? = null,         // tv
    val releaseDate: String? = null,  // movie
    val firstAirDate: String? = null, // tv
)
@Serializable private data class SeerrSearchPage(val results: List<SeerrSearchResult> = emptyList())

private interface SeerrApi {
    @GET("api/v1/request/count") suspend fun counts(): SeerrCounts

    @GET("api/v1/request") suspend fun requests(
        @Query("take") take: Int,
        @Query("filter") filter: String,
        @Query("sort") sort: String,
    ): SeerrRequestPage

    @GET("api/v1/issue") suspend fun issues(
        @Query("take") take: Int,
        @Query("filter") filter: String,
        @Query("sort") sort: String,
    ): SeerrIssuePage

    @GET("api/v1/movie/{id}") suspend fun movie(@Path("id") id: Int): SeerrMeta
    @GET("api/v1/tv/{id}") suspend fun tv(@Path("id") id: Int): SeerrMeta
    @POST("api/v1/request/{id}/approve") suspend fun approve(@Path("id") id: Int): Response<ResponseBody>
    @POST("api/v1/request/{id}/decline") suspend fun decline(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/v1/search") suspend fun search(@Query("query") query: String): SeerrSearchPage
    @POST("api/v1/request") suspend fun createRequest(@Body body: JsonObject): Response<ResponseBody>
}

private fun seerrStatusText(status: Int) = when (status) {
    1 -> "pending"
    2 -> "approved"
    3 -> "declined"
    4 -> "failed"
    else -> "?"
}

private fun seerrIssueType(type: Int) = when (type) {
    1 -> "video"
    2 -> "audio"
    3 -> "subtitle"
    else -> "other"
}

private suspend fun SeerrApi.resolveTitle(media: SeerrMedia, type: String): String {
    val isTv = type == "tv" || media.mediaType == "tv"
    return runCatching {
        if (isTv) tv(media.tmdbId).let { it.name ?: it.title }
        else movie(media.tmdbId).let { it.title ?: it.name }
    }.getOrNull() ?: "#${media.tmdbId}"
}

suspend fun seerrRequests(config: ServiceConfig, filter: String): List<SeerrRequestItem> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val reqs = api.requests(take = 30, filter = filter, sort = "added").results
    coroutineScope {
        reqs.map { r ->
            async {
                val isTv = r.type == "tv" || r.media.mediaType == "tv"
                val title = runCatching {
                    if (isTv) api.tv(r.media.tmdbId).let { it.name ?: it.title }
                    else api.movie(r.media.tmdbId).let { it.title ?: it.name }
                }.getOrNull() ?: "#${r.media.tmdbId}"
                SeerrRequestItem(
                    id = r.id,
                    title = title,
                    subtitle = "${r.type} · ${r.requestedBy.displayName}",
                    status = seerrStatusText(r.status),
                    pending = r.status == 1,
                )
            }
        }.awaitAll()
    }
}

suspend fun seerrApprove(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).approve(id)
        if (r.isSuccessful) "approved" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun seerrDecline(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).decline(id)
        if (r.isSuccessful) "declined" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun seerrIssues(config: ServiceConfig, filter: String): List<SeerrIssueItem> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val issues = api.issues(take = 30, filter = filter, sort = "added").results
    coroutineScope {
        issues.map { iss ->
            async {
                SeerrIssueItem(
                    id = iss.id,
                    title = api.resolveTitle(iss.media, iss.media.mediaType),
                    subtitle = "${seerrIssueType(iss.issueType)} · ${iss.createdBy.displayName}",
                    status = if (iss.status == 1) "open" else "resolved",
                )
            }
        }.awaitAll()
    }
}

suspend fun seerrSearch(config: ServiceConfig, query: String): List<SeerrSearchItem> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    api.search(query).results
        .filter { it.mediaType == "movie" || it.mediaType == "tv" }
        .map { r ->
            val date = r.releaseDate ?: r.firstAirDate ?: ""
            SeerrSearchItem(
                tmdbId = r.id,
                title = (r.title ?: r.name ?: "#${r.id}"),
                year = date.take(4),
                mediaType = r.mediaType,
            )
        }
}

suspend fun seerrCreateRequest(config: ServiceConfig, item: SeerrSearchItem): String = withContext(Dispatchers.IO) {
    try {
        val body = buildJsonObject {
            put("mediaType", item.mediaType)
            put("mediaId", item.tmdbId)
            if (item.mediaType == "tv") put("seasons", "all")
        }
        val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).createRequest(body)
        if (r.isSuccessful) "requested" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

// ---- Servarr shared: Radarr/Sonarr/Lidarr missing + queue ----

@Serializable private data class ArrRef(val title: String = "")
@Serializable private data class ArrArtistRef(val artistName: String = "")
@Serializable private data class ArrMissingRecord(
    val id: Int = 0,
    val title: String = "",
    val year: Int = 0,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val series: ArrRef? = null,
    val artist: ArrArtistRef? = null,
)
@Serializable private data class ArrMissingPage(val records: List<ArrMissingRecord> = emptyList())

@Serializable private data class ArrQueueRecord(
    val id: Int = 0,
    val title: String = "",
    val status: String = "",
    val size: Double = 0.0,
    val sizeleft: Double = 0.0,
)
@Serializable private data class ArrQueuePage(val records: List<ArrQueueRecord> = emptyList())

@Serializable private data class ArrStats(val sizeOnDisk: Long = 0)
@Serializable private data class ArrLibraryRecord(
    val id: Int = 0,
    val title: String = "",
    val artistName: String = "",
    val year: Int = 0,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val status: String = "",
    val sizeOnDisk: Long = 0,
    val statistics: ArrStats? = null,
)

@Serializable private data class ArrProfileRecord(val id: Int = 0, val name: String = "")
@Serializable private data class ArrRootFolderRecord(val path: String = "")

@Serializable private data class ArrEpisodeRecord(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String = "",
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val airDate: String? = null,
)

@Serializable private data class ArrQualityRef(val quality: ArrQualityName = ArrQualityName())
@Serializable private data class ArrQualityName(val name: String = "")
@Serializable private data class ArrCustomFormatRef(val name: String = "")
@Serializable private data class ArrReleaseRecord(
    val guid: String = "",
    val indexerId: Int = 0,
    val title: String = "",
    val indexer: String = "",
    val size: Long = 0,
    val protocol: String = "",
    val seeders: Int? = null,
    val ageMinutes: Double = 0.0,
    val quality: ArrQualityRef = ArrQualityRef(),
    val customFormatScore: Int = 0,
    val customFormats: List<ArrCustomFormatRef> = emptyList(),
    val approved: Boolean = false,
    val rejections: List<String> = emptyList(),
)

@Serializable private data class ArrDiskRecord(
    val path: String = "",
    val freeSpace: Long = 0,
    val totalSpace: Long = 0,
)
@Serializable private data class ArrSystemStatusRec(val version: String = "")
@Serializable private data class ArrHealthRecord(val type: String = "", val message: String = "")

@Serializable private data class ArrGrabReq(val guid: String, val indexerId: Int)

@Serializable private data class ArrHistoryRec(
    val eventType: String = "",
    val date: String = "",
    val sourceTitle: String = "",
    val quality: ArrQualityRef = ArrQualityRef(),
)
@Serializable private data class ArrHistoryPage(val records: List<ArrHistoryRec> = emptyList())

private interface ArrApi {
    @GET suspend fun missing(@Url url: String): ArrMissingPage
    @GET suspend fun queue(@Url url: String): ArrQueuePage
    @GET suspend fun library(@Url url: String): List<ArrLibraryRecord>
    @GET suspend fun lookup(@Url url: String, @Query("term") term: String): List<JsonObject>
    @GET suspend fun profiles(@Url url: String): List<ArrProfileRecord>
    @GET suspend fun rootFolders(@Url url: String): List<ArrRootFolderRecord>
    @POST suspend fun command(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
    @POST suspend fun add(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
    @POST suspend fun releasePush(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
    @DELETE suspend fun deleteQueue(@Url url: String): Response<ResponseBody>
    @GET suspend fun itemDetail(@Url url: String): JsonObject
    @GET suspend fun episodes(@Url url: String, @Query("seriesId") seriesId: Int): List<ArrEpisodeRecord>
    @GET suspend fun releases(@Url url: String): List<ArrReleaseRecord>
    @POST suspend fun downloadRelease(@Url url: String, @Body body: ArrGrabReq): Response<ResponseBody>
    @DELETE suspend fun deleteItem(@Url url: String): Response<ResponseBody>
    @GET suspend fun history(@Url url: String): ArrHistoryPage
    @GET suspend fun diskspace(@Url url: String): List<ArrDiskRecord>
    @GET suspend fun systemStatus(@Url url: String): ArrSystemStatusRec
    @GET suspend fun healthChecks(@Url url: String): List<ArrHealthRecord>
}

private fun arrBase(type: ServiceType) = if (type == ServiceType.LIDARR) "api/v1" else "api/v3"

private fun arrItemPath(type: ServiceType) = when (type) {
    ServiceType.SONARR -> "series"
    ServiceType.LIDARR -> "artist"
    else -> "movie"
}

suspend fun arrLookup(config: ServiceConfig, term: String): List<ArrLookupItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val path = arrItemPath(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).lookup("$base/$path/lookup", term).map { obj ->
        val title = (obj["title"] as? JsonPrimitive)?.content
            ?: (obj["artistName"] as? JsonPrimitive)?.content ?: "?"
        val year = (obj["year"] as? JsonPrimitive)?.intOrNull ?: 0
        ArrLookupItem(title, year, json.encodeToString(JsonObject.serializer(), obj))
    }
}

suspend fun arrProfiles(config: ServiceConfig): List<ArrProfile> = withContext(Dispatchers.IO) {
    apiFor<ArrApi>(config, apiKeyHeader(config)).profiles("${arrBase(config.type)}/qualityprofile")
        .map { ArrProfile(it.id, it.name) }
}

suspend fun arrRootFolders(config: ServiceConfig): List<String> = withContext(Dispatchers.IO) {
    apiFor<ArrApi>(config, apiKeyHeader(config)).rootFolders("${arrBase(config.type)}/rootfolder")
        .map { it.path }.filter { it.isNotBlank() }
}

/** Lidarr only: metadata profiles (required to add an artist). */
suspend fun arrMetadataProfiles(config: ServiceConfig): List<ArrProfile> = withContext(Dispatchers.IO) {
    apiFor<ArrApi>(config, apiKeyHeader(config)).profiles("${arrBase(config.type)}/metadataprofile")
        .map { ArrProfile(it.id, it.name) }
}

suspend fun arrAdd(
    config: ServiceConfig,
    raw: String,
    qualityProfileId: Int,
    rootFolderPath: String,
    monitored: Boolean,
    metadataProfileId: Int = 0,
): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val path = arrItemPath(config.type)
        val original = json.parseToJsonElement(raw).jsonObject
        val body = buildJsonObject {
            original.forEach { (k, v) -> put(k, v) }
            put("qualityProfileId", qualityProfileId)
            put("rootFolderPath", rootFolderPath)
            put("monitored", monitored)
            when (config.type) {
                ServiceType.SONARR -> {
                    put("seasonFolder", true)
                    putJsonObject("addOptions") {
                        put("searchForMissingEpisodes", monitored)
                        put("monitor", if (monitored) "all" else "none")
                    }
                }
                ServiceType.LIDARR -> {
                    put("metadataProfileId", metadataProfileId)
                    putJsonObject("addOptions") {
                        put("monitor", if (monitored) "all" else "none")
                        put("searchForMissingAlbums", monitored)
                    }
                }
                else -> {
                    put("minimumAvailability", "released")
                    putJsonObject("addOptions") { put("searchForMovie", monitored) }
                }
            }
        }
        val r = apiFor<ArrApi>(config, apiKeyHeader(config)).add("$base/$path", body)
        if (r.isSuccessful) "added" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun arrMissing(config: ServiceConfig): List<ArrMissingItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).missing("$base/wanted/missing?pageSize=100").records.map { r ->
        when (config.type) {
            ServiceType.SONARR -> ArrMissingItem(
                r.id,
                r.series?.title ?: r.title,
                "S%02dE%02d · %s".format(r.seasonNumber ?: 0, r.episodeNumber ?: 0, r.title),
            )
            ServiceType.LIDARR -> ArrMissingItem(r.id, r.title, r.artist?.artistName ?: "")
            else -> ArrMissingItem(r.id, r.title, if (r.year > 0) r.year.toString() else "")
        }
    }
}

suspend fun arrQueue(config: ServiceConfig): List<ArrQueueItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).queue("$base/queue?pageSize=100").records.map { r ->
        val prog = if (r.size > 0) ((r.size - r.sizeleft) / r.size).toFloat().coerceIn(0f, 1f) else 0f
        ArrQueueItem(r.id, r.title, r.status, prog)
    }
}

suspend fun arrSearchItem(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val (name, field) = when (config.type) {
            ServiceType.SONARR -> "EpisodeSearch" to "episodeIds"
            ServiceType.LIDARR -> "AlbumSearch" to "albumIds"
            else -> "MoviesSearch" to "movieIds"
        }
        val body = buildJsonObject {
            put("name", name)
            putJsonArray(field) { add(id) }
        }
        val r = apiFor<ArrApi>(config, apiKeyHeader(config)).command("$base/command", body)
        if (r.isSuccessful) "search started" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun arrQueueRemove(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val r = apiFor<ArrApi>(config, apiKeyHeader(config))
            .deleteQueue("$base/queue/$id?removeFromClient=true&blocklist=false")
        if (r.isSuccessful) "removed" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun arrLibrary(config: ServiceConfig): List<ArrLibraryItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val path = when (config.type) {
        ServiceType.SONARR -> "series"
        ServiceType.LIDARR -> "artist"
        else -> "movie"
    }
    apiFor<ArrApi>(config, apiKeyHeader(config)).library("$base/$path").map { r ->
        val title = if (config.type == ServiceType.LIDARR) r.artistName else r.title
        val size = (r.statistics?.sizeOnDisk ?: r.sizeOnDisk) / (1024 * 1024)
        val sub = when (config.type) {
            ServiceType.LIDARR -> if (r.monitored) "monitored" else "unmonitored"
            ServiceType.SONARR -> listOfNotNull(r.year.takeIf { it > 0 }?.toString(), r.status.ifBlank { null }).joinToString(" · ")
            else -> "${r.year} · ${if (r.hasFile) "downloaded" else "missing"}"
        }
        ArrLibraryItem(r.id, title, sub, r.year, size)
    }
}

/** Search at the library level (whole movie/series/artist). */
suspend fun arrLibrarySearch(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val (name, field) = when (config.type) {
            ServiceType.SONARR -> "SeriesSearch" to "seriesIds"
            ServiceType.LIDARR -> "ArtistSearch" to "artistIds"
            else -> "MoviesSearch" to "movieIds"
        }
        val body = buildJsonObject {
            put("name", name)
            putJsonArray(field) { add(id) }
        }
        val r = apiFor<ArrApi>(config, apiKeyHeader(config)).command("$base/command", body)
        if (r.isSuccessful) "search started" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

private fun jsStr(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content
private fun jsInt(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.intOrNull
private fun jsBool(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toBoolean()

/** Cutoff-unmet wanted list (quality below cutoff). Same shape as [arrMissing]. */
suspend fun arrCutoff(config: ServiceConfig): List<ArrMissingItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).missing("$base/wanted/cutoff?pageSize=100").records.map { r ->
        when (config.type) {
            ServiceType.SONARR -> ArrMissingItem(
                r.id,
                r.series?.title ?: r.title,
                "S%02dE%02d · %s".format(r.seasonNumber ?: 0, r.episodeNumber ?: 0, r.title),
            )
            ServiceType.LIDARR -> ArrMissingItem(r.id, r.title, r.artist?.artistName ?: "")
            else -> ArrMissingItem(r.id, r.title, if (r.year > 0) r.year.toString() else "")
        }
    }
}

/** Detail for a movie (Radarr) or series (Sonarr). */
suspend fun arrDetail(config: ServiceConfig, id: Int): ArrDetail = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val path = arrItemPath(config.type)
    val o = apiFor<ArrApi>(config, apiKeyHeader(config)).itemDetail("$base/$path/$id")
    val statsObj = o["statistics"] as? JsonObject
    val sizeMb = ((statsObj?.let { jsInt(it, "sizeOnDisk") } ?: jsInt(o, "sizeOnDisk") ?: 0).toLong()) / (1024 * 1024)
    val facts = buildList {
        jsStr(o, "status")?.takeIf { it.isNotBlank() }?.let { add("status" to it) }
        if (config.type == ServiceType.SONARR) {
            statsObj?.let {
                val total = jsInt(it, "episodeCount") ?: 0
                val have = jsInt(it, "episodeFileCount") ?: 0
                add("episodes" to "$have/$total")
                jsInt(it, "seasonCount")?.let { s -> add("seasons" to s.toString()) }
            }
            jsStr(o, "network")?.takeIf { it.isNotBlank() }?.let { add("network" to it) }
        } else {
            add("file" to if (jsBool(o, "hasFile") == true) "downloaded" else "missing")
            jsStr(o, "studio")?.takeIf { it.isNotBlank() }?.let { add("studio" to it) }
            jsInt(o, "runtime")?.takeIf { it > 0 }?.let { add("runtime" to "${it}m") }
        }
    }
    ArrDetail(
        id = id,
        title = jsStr(o, "title") ?: "",
        year = jsInt(o, "year") ?: 0,
        overview = jsStr(o, "overview") ?: "",
        monitored = jsBool(o, "monitored") ?: false,
        status = jsStr(o, "status") ?: "",
        sizeMb = sizeMb,
        facts = facts,
    )
}

/** Sonarr: episodes for a series, newest season first. */
suspend fun arrEpisodes(config: ServiceConfig, seriesId: Int): List<ArrEpisode> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).episodes("$base/episode", seriesId).map { e ->
        ArrEpisode(e.id, e.seasonNumber, e.episodeNumber, e.title, e.hasFile, e.monitored, e.airDate?.take(10) ?: "")
    }.sortedWith(compareByDescending<ArrEpisode> { it.seasonNumber }.thenByDescending { it.episodeNumber })
}

/** Interactive search. [movieId] for Radarr, [episodeId] for Sonarr. */
suspend fun arrReleases(config: ServiceConfig, movieId: Int?, episodeId: Int?): List<ArrRelease> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val q = when {
        episodeId != null -> "episodeId=$episodeId"
        movieId != null -> "movieId=$movieId"
        else -> ""
    }
    apiFor<ArrApi>(config, apiKeyHeader(config)).releases("$base/release?$q").map { r ->
        ArrRelease(
            guid = r.guid,
            indexerId = r.indexerId,
            title = r.title,
            indexer = r.indexer,
            sizeMb = r.size / (1024 * 1024),
            protocol = r.protocol,
            seeders = if (r.protocol == "torrent") r.seeders else null,
            ageDays = (r.ageMinutes / (60 * 24)).toInt(),
            quality = r.quality.quality.name,
            score = r.customFormatScore,
            customFormats = r.customFormats.joinToString(", ") { it.name }.trim(),
            approved = r.approved,
            rejection = r.rejections.joinToString("; "),
        )
    }.sortedWith(compareByDescending<ArrRelease> { it.approved }.thenByDescending { it.score })
}

suspend fun arrSystem(config: ServiceConfig): ArrSystemInfo = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val api = apiFor<ArrApi>(config, apiKeyHeader(config))
    coroutineScope {
        val versionD = async { runCatching { api.systemStatus("$base/system/status").version }.getOrDefault("?") }
        val healthD = async { runCatching { api.healthChecks("$base/health").map { it.type to it.message } }.getOrDefault(emptyList()) }
        val disksD = async {
            runCatching {
                api.diskspace("$base/diskspace").map { d ->
                    val freeGb = d.freeSpace / (1024.0 * 1024 * 1024)
                    val totalGb = d.totalSpace / (1024.0 * 1024 * 1024)
                    d.path to "%.0f / %.0f GB free".format(freeGb, totalGb)
                }
            }.getOrDefault(emptyList())
        }
        ArrSystemInfo(version = versionD.await(), health = healthD.await(), disks = disksD.await())
    }
}

suspend fun arrGrab(config: ServiceConfig, guid: String, indexerId: Int): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).downloadRelease("$base/release", ArrGrabReq(guid, indexerId)), "grabbed")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun arrDelete(config: ServiceConfig, id: Int, deleteFiles: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val path = arrItemPath(config.type)
        val r = apiFor<ArrApi>(config, apiKeyHeader(config))
            .deleteItem("$base/$path/$id?deleteFiles=$deleteFiles&addImportExclusion=false")
        if (r.isSuccessful) "deleted" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun arrHistory(config: ServiceConfig): List<ArrHistoryItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val url = "$base/history?page=1&pageSize=50&sortKey=date&sortDirection=descending"
    apiFor<ArrApi>(config, apiKeyHeader(config)).history(url).records.map { h ->
        ArrHistoryItem(
            title = h.sourceTitle,
            eventType = h.eventType,
            date = h.date.take(16).replace('T', ' '),
            quality = h.quality.quality.name,
        )
    }
}

/** Search all missing or cutoff-unmet items. */
suspend fun arrSearchAll(config: ServiceConfig, cutoff: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val name = when (config.type) {
            ServiceType.SONARR -> if (cutoff) "CutoffUnmetEpisodeSearch" else "MissingEpisodeSearch"
            ServiceType.LIDARR -> if (cutoff) "CutoffUnmetAlbumSearch" else "MissingAlbumSearch"
            else -> if (cutoff) "CutoffUnmetMoviesSearch" else "MissingMoviesSearch"
        }
        val body = buildJsonObject { put("name", name) }
        okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).command("$base/command", body), "search started")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

// ---- NZBGet (JSON-RPC over HTTP + Basic auth) ----

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

private interface NzbgetApi {
    @GET("jsonrpc/status") suspend fun status(): NzbStatusResp
    @GET("jsonrpc/listgroups") suspend fun listgroups(): NzbGroupsResp
    @POST("jsonrpc") suspend fun rpc(@Body req: NzbRpcReq): NzbBoolResp
    @POST("jsonrpc") suspend fun history(@Body req: NzbHistoryReq): NzbHistoryResp
    @POST("jsonrpc") suspend fun rpcJson(@Body body: JsonObject): NzbBoolResp
    @POST("jsonrpc") suspend fun rpcInt(@Body body: JsonObject): NzbIntResp
    @POST("jsonrpc") suspend fun version(@Body req: NzbRpcReq): NzbStringResp
}

private fun appendUrlBody(url: String, category: String): JsonObject =
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

private fun editqueueBody(command: String, editText: String, id: Int): JsonObject =
    buildJsonObject {
        put("method", "editqueue")
        putJsonArray("params") {
            add(command)
            add(0) // offset
            add(editText)
            addJsonArray { add(id) }
        }
    }

private fun rateBody(kbps: Int): JsonObject =
    buildJsonObject {
        put("method", "rate")
        putJsonArray("params") { add(kbps) }
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

suspend fun prowlarrToggleIndexer(config: ServiceConfig, id: Int, enable: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val api = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
        val raw = api.indexerRaw(id)
        val body = JsonObject(raw.toMutableMap().apply { put("enable", JsonPrimitive(enable)) })
        okOr(api.updateIndexer(id, body), if (enable) "enabled" else "disabled")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun prowlarrTestIndexer(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<ProwlarrApi>(config, apiKeyHeader(config)).testIndexer(id), "ok")
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

/** Pushes a Prowlarr release to a Radarr/Sonarr instance via release/push. */
suspend fun arrPushRelease(arrConfig: ServiceConfig, release: ProwlarrRelease): String = withContext(Dispatchers.IO) {
    try {
        val api = apiFor<ArrApi>(arrConfig, apiKeyHeader(arrConfig))
        val url = "${arrBase(arrConfig.type)}/release/push"
        val body = buildJsonObject {
            put("title", release.title)
            val dl = release.downloadUrl.ifBlank { release.magnetUrl }
            put("downloadUrl", dl)
            if (release.magnetUrl.isNotBlank()) put("magnetUrl", release.magnetUrl)
            put("protocol", if (release.protocol == "torrent") "torrent" else "usenet")
            put("publishDate", release.publishDate.ifBlank { "1970-01-01T00:00:00Z" })
            put("size", release.sizeBytes)
            put("indexer", release.indexer)
        }
        okOr(api.releasePush(url, body), "sent to ${arrConfig.label}")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun prowlarrGrab(config: ServiceConfig, release: ProwlarrRelease): String = withContext(Dispatchers.IO) {
    try {
        val r = apiFor<ProwlarrApi>(config, apiKeyHeader(config))
            .grab(ProwlarrGrabReq(release.guid, release.indexerId))
        okOr(r, "grabbed")
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
