package org.phioster.nexarr.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.nexarr.model.ArrAlbum
import org.phioster.nexarr.model.ArrTrack
import org.phioster.nexarr.model.ArrCastMember
import org.phioster.nexarr.model.ArrDetail
import org.phioster.nexarr.model.ArrEpisode
import org.phioster.nexarr.model.ArrHistoryItem
import org.phioster.nexarr.model.ArrImportItem
import org.phioster.nexarr.model.ArrLibraryItem
import org.phioster.nexarr.model.ArrLookupItem
import org.phioster.nexarr.model.ArrMissingItem
import org.phioster.nexarr.model.ArrProfile
import org.phioster.nexarr.model.ArrQueueItem
import org.phioster.nexarr.model.ArrCalendarItem
import org.phioster.nexarr.model.NtfyMessage
import org.phioster.nexarr.model.JellyActivity
import org.phioster.nexarr.model.JellyChannel
import org.phioster.nexarr.model.JellyDevice
import org.phioster.nexarr.model.JellyGuideProvider
import org.phioster.nexarr.model.JellyLiveTv
import org.phioster.nexarr.model.JellyTuner
import org.phioster.nexarr.model.JellyLibrary
import org.phioster.nexarr.model.JellyLogFile
import org.phioster.nexarr.model.JellyMediaDetail
import org.phioster.nexarr.model.JellyMediaItem
import org.phioster.nexarr.model.JellyPackage
import org.phioster.nexarr.model.JellyPlugin
import org.phioster.nexarr.model.JellySession
import org.phioster.nexarr.model.JellySystemInfo
import org.phioster.nexarr.model.JellyTask
import org.phioster.nexarr.model.JellyUser
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
import org.phioster.nexarr.model.SeerrComment
import org.phioster.nexarr.model.SearchResult
import org.phioster.nexarr.model.SeerrDiscoverItem
import org.phioster.nexarr.model.SeerrIssueDetail
import org.phioster.nexarr.model.SeerrIssueItem
import org.phioster.nexarr.model.SeerrRequestItem
import org.phioster.nexarr.model.SeerrMediaDetail
import org.phioster.nexarr.model.SeerrSearchItem
import org.phioster.nexarr.model.SeerrUserInfo
import org.phioster.nexarr.model.SeerrSeason
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
private val jellyfinSession = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

/** config.id -> the resolved Jellyfin user id used for media-browsing endpoints. */
private val jellyfinUserIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** Drop a cached Jellyfin login token (e.g. after its config was edited). */
fun clearJellyfinSession(id: String) {
    jellyfinSession.remove(id)
    jellyfinUserIdCache.remove(id)
}

// One shared client so every per-call client below reuses the same dispatcher and
// connection pool (newBuilder() shares them) instead of spawning a pool per request.
private val baseOkClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun okClient(config: ServiceConfig, authHeaders: Map<String, String>): OkHttpClient =
    baseOkClient.newBuilder()
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            authHeaders.forEach { (k, v) -> if (v.isNotBlank()) b.header(k, v) }
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) b.header(k, v) }
            chain.proceed(b.build())
        }
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

@Serializable private data class JfSession(
    val Id: String = "",
    val UserName: String? = null,
    val Client: String? = null,
    val DeviceName: String? = null,
    val LastActivityDate: String? = null,
    val SupportsRemoteControl: Boolean = false,
    val NowPlayingItem: JfNowPlaying? = null,
    val PlayState: JfPlayState? = null,
)
@Serializable private data class JfNowPlaying(
    val Name: String? = null,
    val Type: String? = null,
    val SeriesName: String? = null,
    val ProductionYear: Int? = null,
    val RunTimeTicks: Long? = null,
)
@Serializable private data class JfPlayState(
    val PositionTicks: Long? = null,
    val IsPaused: Boolean = false,
)
@Serializable private data class JfUserFull(
    val Id: String = "",
    val Name: String = "",
    val LastActivityDate: String? = null,
    val Policy: JfPolicy = JfPolicy(),
)
@Serializable private data class JfVirtualFolder(
    val Name: String = "",
    val ItemId: String = "",
    val CollectionType: String? = null,
    val Locations: List<String> = emptyList(),
)
@Serializable private data class JfLogFile(
    val Name: String = "",
    val DateModified: String = "",
    val Size: Long = 0,
)
@Serializable private data class JfPlugin(
    val Id: String = "",
    val Version: String = "",
    val Name: String = "",
    val Description: String = "",
    val Status: String = "",
    val CanUninstall: Boolean = true,
)
@Serializable private data class JfPackageVersion(val version: String = "")
@Serializable private data class JfPackage(
    val name: String = "",
    val guid: String = "",
    val description: String = "",
    val overview: String = "",
    val versions: List<JfPackageVersion> = emptyList(),
)
@Serializable private data class JfMediaPathInfo(val Path: String = "")
@Serializable private data class JfMediaPath(val Name: String = "", val PathInfo: JfMediaPathInfo = JfMediaPathInfo())
@Serializable private data class JfLiveTvServiceInfo(
    val Name: String = "",
    val Status: String = "",
    val StatusMessage: String? = null,
    val Tuners: List<String> = emptyList(),
)
@Serializable private data class JfLiveTvInfo(
    val IsEnabled: Boolean = false,
    val Services: List<JfLiveTvServiceInfo> = emptyList(),
)
@Serializable private data class JfTunerHost(
    val Id: String = "",
    val Url: String = "",
    val Type: String = "",
    val FriendlyName: String? = null,
)
@Serializable private data class JfListingProvider(
    val Id: String = "",
    val Type: String = "",
    val Path: String? = null,
    val ListingsId: String? = null,
)
@Serializable private data class JfLiveTvOptions(
    val TunerHosts: List<JfTunerHost> = emptyList(),
    val ListingProviders: List<JfListingProvider> = emptyList(),
)
@Serializable private data class JfChannelProgram(val Name: String = "")
@Serializable private data class JfChannel(
    val Id: String = "",
    val Name: String = "",
    val ChannelNumber: String? = null,
    val CurrentProgram: JfChannelProgram? = null,
)
@Serializable private data class JfChannelsResp(val Items: List<JfChannel> = emptyList())
@Serializable private data class JfMessageReq(val Text: String, val Header: String = "Nexarr", val TimeoutMs: Long = 5000)

@Serializable private data class JfSystemInfo(
    val Version: String = "",
    val ServerName: String = "",
    val OperatingSystem: String = "",
)
@Serializable private data class JfTaskResult(val Status: String = "", val EndTimeUtc: String? = null)
@Serializable private data class JfTask(
    val Id: String = "",
    val Name: String = "",
    val State: String = "",
    val CurrentProgressPercentage: Double? = null,
    val LastExecutionResult: JfTaskResult? = null,
)
@Serializable private data class JfActivityEntry(
    val Name: String = "",
    val Type: String = "",
    val Date: String = "",
    val Severity: String = "",
    val ShortOverview: String? = null,
)
@Serializable private data class JfActivityPage(val Items: List<JfActivityEntry> = emptyList())

@Serializable private data class JfDevice(
    val Name: String = "",
    val AppName: String = "",
    val LastUserName: String? = null,
    val DateLastActivity: String? = null,
)
@Serializable private data class JfDevicePage(val Items: List<JfDevice> = emptyList())

@Serializable private data class JfUserData(
    val PlayedPercentage: Double? = null,
    val Played: Boolean = false,
)
@Serializable private data class JfItem(
    val Id: String = "",
    val Name: String = "",
    val Type: String = "",
    val CollectionType: String? = null,
    val ProductionYear: Int? = null,
    val IsFolder: Boolean = false,
    val SeriesName: String? = null,
    val ParentIndexNumber: Int? = null,
    val IndexNumber: Int? = null,
    val AlbumArtist: String? = null,
    val LocationType: String? = null, // "FileSystem"/"Remote" = present; "Virtual" = metadata only, no file
    val ImageTags: Map<String, String>? = null,
    val UserData: JfUserData? = null,
)
@Serializable private data class JfItemsResp(val Items: List<JfItem> = emptyList())
@Serializable private data class JfPerson(
    val Id: String = "",
    val Name: String = "",
    val Role: String? = null,
    val Type: String? = null,
    val PrimaryImageTag: String? = null,
)
@Serializable private data class JfStudio(val Name: String = "")
@Serializable private data class JfItemDetail(
    val Id: String = "",
    val Name: String = "",
    val Overview: String? = null,
    val ProductionYear: Int? = null,
    val Genres: List<String> = emptyList(),
    val RunTimeTicks: Long? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Double? = null,
    val Type: String = "",
    val Studios: List<JfStudio> = emptyList(),
    val People: List<JfPerson> = emptyList(),
    val ImageTags: Map<String, String>? = null,
)

@Serializable private data class JfAuthReq(val Username: String, val Pw: String)
@Serializable private data class JfAuthResp(val AccessToken: String = "", val User: JfUser = JfUser())
@Serializable private data class JfUser(val Name: String = "", val Policy: JfPolicy = JfPolicy())
@Serializable private data class JfPolicy(
    val IsAdministrator: Boolean = false,
    val IsDisabled: Boolean = false,
    val EnableContentDownloading: Boolean = false,
    val EnableAllFolders: Boolean = true,
    val EnabledFolders: List<String> = emptyList(),
)

private interface JellyfinAuthApi {
    @POST("Users/AuthenticateByName") suspend fun authenticate(@Body body: JfAuthReq): JfAuthResp
}

private interface JellyfinApi {
    @GET("Items/Counts") suspend fun counts(): JfCounts
    @GET("Sessions") suspend fun sessions(): List<JfSession>
    @GET("Users") suspend fun users(): List<JfUserFull>
    @GET("Users/{id}") suspend fun user(@Path("id") id: String): JsonObject
    @POST("Users/New") suspend fun createUser(@Body body: JsonObject): Response<ResponseBody>
    @POST("Users/{id}/Policy") suspend fun setPolicy(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>
    @POST("Users/{id}/Password") suspend fun setPassword(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>
    @DELETE("Users/{id}") suspend fun deleteUser(@Path("id") id: String): Response<ResponseBody>
    @GET("Library/VirtualFolders") suspend fun virtualFolders(): List<JfVirtualFolder>
    @GET("Users/{uid}/Views") suspend fun views(@Path("uid") uid: String): JfItemsResp
    @GET("Users/{uid}/Items/Latest") suspend fun latest(@Path("uid") uid: String, @Query("Limit") limit: Int = 20, @Query("ParentId") parentId: String? = null): List<JfItem>
    @GET("Users/{uid}/Items/Resume") suspend fun resume(@Path("uid") uid: String, @Query("Limit") limit: Int = 20): JfItemsResp
    @GET("Users/{uid}/Items") suspend fun items(
        @Path("uid") uid: String,
        @Query("ParentId") parentId: String,
        @Query("SortBy") sortBy: String = "IsFolder,SortName",
        @Query("Limit") limit: Int = 300,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio",
    ): JfItemsResp
    @GET("Users/{uid}/Items/{id}") suspend fun itemDetail(@Path("uid") uid: String, @Path("id") id: String): JfItemDetail
    @GET("Users/{uid}/Items") suspend fun searchItems(
        @Path("uid") uid: String,
        @Query("searchTerm") term: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") types: String = "Movie,Series,MusicAlbum",
        @Query("Limit") limit: Int = 12,
    ): JfItemsResp
    @POST("Items/{id}/Refresh") suspend fun refreshItem(@Path("id") id: String): Response<ResponseBody>
    @POST("Sessions/{id}/Playing/{cmd}") suspend fun playCommand(@Path("id") id: String, @Path("cmd") cmd: String): Response<ResponseBody>
    @POST("Sessions/{id}/Message") suspend fun message(@Path("id") id: String, @Body body: JfMessageReq): Response<ResponseBody>
    @POST("Library/Refresh") suspend fun refreshLibrary(): Response<ResponseBody>
    @GET("System/Info") suspend fun systemInfo(): JfSystemInfo
    @GET("ScheduledTasks") suspend fun scheduledTasks(): List<JfTask>
    @POST("ScheduledTasks/Running/{id}") suspend fun runTask(@Path("id") id: String): Response<ResponseBody>
    @GET("System/ActivityLog/Entries") suspend fun activityLog(@Query("limit") limit: Int = 30): JfActivityPage
    @GET("Devices") suspend fun devices(): JfDevicePage
    @POST("System/Restart") suspend fun restartServer(): Response<ResponseBody>
    @POST("System/Shutdown") suspend fun shutdownServer(): Response<ResponseBody>
    @GET("System/Logs") suspend fun logFiles(): List<JfLogFile>
    @GET("System/Logs/Log") suspend fun logContent(@Query("name") name: String): Response<ResponseBody>
    @POST("Library/VirtualFolders") suspend fun addVirtualFolder(
        @Query("name") name: String,
        @Query("collectionType") collectionType: String?,
        @Query("paths") paths: List<String>,
        @Query("refreshLibrary") refresh: Boolean = true,
    ): Response<ResponseBody>
    @DELETE("Library/VirtualFolders") suspend fun deleteVirtualFolder(
        @Query("name") name: String,
        @Query("refreshLibrary") refresh: Boolean = true,
    ): Response<ResponseBody>
    @POST("Library/VirtualFolders/Name") suspend fun renameVirtualFolder(
        @Query("name") name: String,
        @Query("newName") newName: String,
        @Query("refreshLibrary") refresh: Boolean = true,
    ): Response<ResponseBody>
    @POST("Library/VirtualFolders/Paths") suspend fun addLibraryPath(
        @Body body: JfMediaPath,
        @Query("refreshLibrary") refresh: Boolean = true,
    ): Response<ResponseBody>
    @DELETE("Library/VirtualFolders/Paths") suspend fun removeLibraryPath(
        @Query("name") name: String,
        @Query("path") path: String,
        @Query("refreshLibrary") refresh: Boolean = true,
    ): Response<ResponseBody>
    @GET("Plugins") suspend fun plugins(): List<JfPlugin>
    @POST("Plugins/{id}/{version}/Enable") suspend fun enablePlugin(@Path("id") id: String, @Path("version") version: String): Response<ResponseBody>
    @POST("Plugins/{id}/{version}/Disable") suspend fun disablePlugin(@Path("id") id: String, @Path("version") version: String): Response<ResponseBody>
    @DELETE("Plugins/{id}/{version}") suspend fun uninstallPlugin(@Path("id") id: String, @Path("version") version: String): Response<ResponseBody>
    @GET("Packages") suspend fun packages(): List<JfPackage>
    @POST("Packages/Installed/{name}") suspend fun installPackage(
        @Path("name") name: String,
        @Query("assemblyGuid") guid: String,
    ): Response<ResponseBody>
    @GET("LiveTv/Info") suspend fun liveTvInfo(): JfLiveTvInfo
    @GET("System/Configuration/livetv") suspend fun liveTvOptions(): JfLiveTvOptions
    @GET("LiveTv/Channels") suspend fun liveTvChannels(
        @Query("userId") userId: String? = null, // CurrentProgram is only filled with a user context
        @Query("addCurrentProgram") addCurrentProgram: Boolean = true,
        @Query("limit") limit: Int = 300,
    ): JfChannelsResp
    @POST("LiveTv/TunerHosts") suspend fun addTunerHost(@Body body: JsonObject): Response<ResponseBody>
    @DELETE("LiveTv/TunerHosts") suspend fun deleteTunerHost(@Query("id") id: String): Response<ResponseBody>
    @POST("LiveTv/ListingProviders") suspend fun addListingProvider(@Body body: JsonObject): Response<ResponseBody>
    @DELETE("LiveTv/ListingProviders") suspend fun deleteListingProvider(@Query("id") id: String): Response<ResponseBody>
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
    @DELETE("api/v1/indexer/{id}") suspend fun deleteIndexer(@Path("id") id: Int): Response<ResponseBody>
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
    val total: Int = 0,
    val movie: Int = 0,
    val tv: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val processing: Int = 0,
    val available: Int = 0,
    val declined: Int = 0,
)
@Serializable private data class SeerrUserRec(
    val id: Int = 0,
    val displayName: String = "",
    val username: String? = null,
    val email: String? = null,
    val requestCount: Int = 0,
)
@Serializable private data class SeerrUserPage(val results: List<SeerrUserRec> = emptyList())

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
    @GET("api/v1/user") suspend fun users(@Query("take") take: Int = 100, @Query("sort") sort: String = "requests"): SeerrUserPage

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
    @GET("api/v1/movie/{id}") suspend fun movieRaw(@Path("id") id: Int): JsonObject
    @GET("api/v1/tv/{id}") suspend fun tvRaw(@Path("id") id: Int): JsonObject
    @POST("api/v1/request/{id}/approve") suspend fun approve(@Path("id") id: Int): Response<ResponseBody>
    @POST("api/v1/request/{id}/decline") suspend fun decline(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/v1/search") suspend fun search(@Query("query") query: String): SeerrSearchPage
    @GET("api/v1/search") suspend fun searchRaw(@Query("query") query: String): JsonObject
    @POST("api/v1/request") suspend fun createRequest(@Body body: JsonObject): Response<ResponseBody>
    @GET("api/v1/discover/trending") suspend fun trending(@Query("page") page: Int = 1): JsonObject
    @GET("api/v1/discover/movies") suspend fun discoverMovies(@Query("page") page: Int = 1): JsonObject
    @GET("api/v1/discover/tv") suspend fun discoverTv(@Query("page") page: Int = 1): JsonObject
    @GET("api/v1/issue/{id}") suspend fun issueDetail(@Path("id") id: Int): JsonObject
    @POST("api/v1/issue/{id}/comment") suspend fun addComment(@Path("id") id: Int, @Body body: JsonObject): Response<ResponseBody>
    @POST("api/v1/issue/{id}/{status}") suspend fun setIssueStatus(@Path("id") id: Int, @Path("status") status: String): Response<ResponseBody>
    @DELETE("api/v1/issue/{id}") suspend fun deleteIssue(@Path("id") id: Int): Response<ResponseBody>
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

private fun seerrMediaStatusText(status: Int?) = when (status) {
    2 -> "pending"
    3 -> "processing"
    4 -> "partial"
    5 -> "available"
    else -> ""
}

private fun parseDiscoverItems(page: JsonObject, defaultType: String?): List<SeerrDiscoverItem> {
    val results = page["results"] as? JsonArray ?: return emptyList()
    return results.mapNotNull { it as? JsonObject }
        .map { o -> o to (jsStr(o, "mediaType") ?: defaultType) }
        .filter { (_, type) -> type == "movie" || type == "tv" }
        .map { (o, typeNullable) ->
            val type = typeNullable ?: "movie"
            val date = jsStr(o, "releaseDate") ?: jsStr(o, "firstAirDate") ?: ""
            val poster = jsStr(o, "posterPath")
            val status = ((o["mediaInfo"] as? JsonObject)?.let { jsInt(it, "status") })
            SeerrDiscoverItem(
                tmdbId = jsInt(o, "id") ?: 0,
                title = jsStr(o, "title") ?: jsStr(o, "name") ?: "?",
                year = date.take(4),
                mediaType = type,
                posterUrl = if (!poster.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$poster" else "",
                status = seerrMediaStatusText(status),
            )
        }
}

/** Browse discover/trending. [kind] = "trending" | "movies" | "tv". */
suspend fun seerrDiscover(config: ServiceConfig, kind: String): List<SeerrDiscoverItem> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val (page, def) = when (kind) {
        "movies" -> api.discoverMovies() to "movie"
        "tv" -> api.discoverTv() to "tv"
        else -> api.trending() to null
    }
    parseDiscoverItems(page, def)
}

/** Seasons of a TV show (seasonNumber >= 1). */
suspend fun seerrSeasons(config: ServiceConfig, tmdbId: Int): List<SeerrSeason> = withContext(Dispatchers.IO) {
    val o = apiFor<SeerrApi>(config, apiKeyHeader(config)).tvRaw(tmdbId)
    (o["seasons"] as? JsonArray)?.mapNotNull { it as? JsonObject }
        ?.mapNotNull { s ->
            val n = jsInt(s, "seasonNumber") ?: return@mapNotNull null
            if (n < 1) null else SeerrSeason(n, jsStr(s, "name") ?: "Season $n", jsInt(s, "episodeCount") ?: 0)
        } ?: emptyList()
}

/** Request-count statistics for the admin stats view. */
suspend fun seerrRequestStats(config: ServiceConfig): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    val c = apiFor<SeerrApi>(config, apiKeyHeader(config)).counts()
    listOf(
        "total" to c.total.toString(),
        "movies" to c.movie.toString(),
        "tv" to c.tv.toString(),
        "pending" to c.pending.toString(),
        "approved" to c.approved.toString(),
        "processing" to c.processing.toString(),
        "available" to c.available.toString(),
        "declined" to c.declined.toString(),
    )
}

/** Seerr users, sorted by request count. */
suspend fun seerrUsers(config: ServiceConfig): List<SeerrUserInfo> = withContext(Dispatchers.IO) {
    apiFor<SeerrApi>(config, apiKeyHeader(config)).users().results.map { u ->
        SeerrUserInfo(
            name = u.displayName.ifBlank { u.username ?: "user #${u.id}" },
            email = u.email ?: "",
            requestCount = u.requestCount,
        )
    }
}

/** Create a request; [seasons] null = movie or all seasons, else the chosen season numbers. */
suspend fun seerrRequest(config: ServiceConfig, tmdbId: Int, mediaType: String, seasons: List<Int>?): String = withContext(Dispatchers.IO) {
    try {
        val body = buildJsonObject {
            put("mediaType", mediaType)
            put("mediaId", tmdbId)
            if (mediaType == "tv") {
                if (seasons.isNullOrEmpty()) put("seasons", "all")
                else putJsonArray("seasons") { seasons.forEach { add(it) } }
            }
        }
        val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).createRequest(body)
        if (r.isSuccessful) "requested" else "error: HTTP ${r.code()}"
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun seerrIssueDetail(config: ServiceConfig, id: Int): SeerrIssueDetail = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val o = api.issueDetail(id)
    val comments = (o["comments"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { c ->
        val author = (c["user"] as? JsonObject)?.let { jsStr(it, "displayName") } ?: "?"
        SeerrComment(
            author = author,
            message = jsStr(c, "message") ?: "",
            date = (jsStr(c, "createdAt") ?: "").take(16).replace('T', ' '),
        )
    } ?: emptyList()
    val media = o["media"] as? JsonObject
    val title = runCatching {
        val t = media?.let { jsInt(it, "tmdbId") } ?: 0
        val isTv = jsStr(media ?: JsonObject(emptyMap()), "mediaType") == "tv"
        if (t > 0) (if (isTv) api.tv(t).let { it.name ?: it.title } else api.movie(t).let { it.title ?: it.name }) else null
    }.getOrNull() ?: "Issue #$id"
    SeerrIssueDetail(
        id = id,
        title = title ?: "Issue #$id",
        type = seerrIssueType(jsInt(o, "issueType") ?: 0),
        status = if ((jsInt(o, "status") ?: 1) == 1) "open" else "resolved",
        description = comments.firstOrNull()?.message ?: "",
        comments = comments.drop(1),
    )
}

suspend fun seerrAddComment(config: ServiceConfig, id: Int, message: String): String = withContext(Dispatchers.IO) {
    try {
        val body = buildJsonObject { put("message", message) }
        okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).addComment(id, body), "commented")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun seerrSetIssueStatus(config: ServiceConfig, id: Int, resolved: Boolean): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).setIssueStatus(id, if (resolved) "resolved" else "open"), if (resolved) "resolved" else "reopened")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun seerrDeleteIssueById(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).deleteIssue(id), "deleted")
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
    @GET suspend fun albums(@Url url: String, @Query("artistId") artistId: Int): List<JsonObject>
    @GET suspend fun tracks(@Url url: String, @Query("albumId") albumId: Int): List<JsonObject>
    @GET suspend fun releases(@Url url: String): List<ArrReleaseRecord>
    @POST suspend fun downloadRelease(@Url url: String, @Body body: ArrGrabReq): Response<ResponseBody>
    @DELETE suspend fun deleteItem(@Url url: String): Response<ResponseBody>
    @GET suspend fun history(@Url url: String): ArrHistoryPage
    @GET suspend fun calendar(@Url url: String): List<JsonObject>
    @GET suspend fun diskspace(@Url url: String): List<ArrDiskRecord>
    @GET suspend fun systemStatus(@Url url: String): ArrSystemStatusRec
    @GET suspend fun healthChecks(@Url url: String): List<ArrHealthRecord>
    @GET suspend fun manualImport(@Url url: String): List<JsonObject>
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

// ---- Global (cross-service) search ----

/** Search one service by title. Returns [] for unsupported types or on any error. */
suspend fun serviceSearch(config: ServiceConfig, term: String): List<SearchResult> = withContext(Dispatchers.IO) {
    runCatching {
        when (config.type) {
            ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR -> arrSearchResults(config, term)
            ServiceType.SEERR -> seerrSearchResults(config, term)
            ServiceType.JELLYFIN -> jellyfinSearchResults(config, term)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private suspend fun arrSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
    val base = arrBase(config.type)
    val path = arrItemPath(config.type)
    return apiFor<ArrApi>(config, apiKeyHeader(config)).lookup("$base/$path/lookup", term).take(8).map { obj ->
        val title = jsStr(obj, "title") ?: jsStr(obj, "artistName") ?: "?"
        val year = jsInt(obj, "year") ?: 0
        val libId = jsLong(obj, "id") ?: 0L
        val poster = (obj["images"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { jsStr(it, "coverType") == "poster" }
            ?.let { jsStr(it, "remoteUrl") ?: jsStr(it, "url") } ?: ""
        SearchResult(
            serviceId = config.id,
            serviceLabel = config.label,
            serviceType = config.type,
            title = title,
            subtitle = listOfNotNull(year.takeIf { it > 0 }?.toString(), if (libId > 0) "in library" else "not added").joinToString(" · "),
            posterUrl = poster,
            libraryId = libId,
        )
    }
}

private suspend fun seerrSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
    val page = apiFor<SeerrApi>(config, apiKeyHeader(config)).searchRaw(term)
    return parseDiscoverItems(page, null).take(8).map { d ->
        SearchResult(
            serviceId = config.id,
            serviceLabel = config.label,
            serviceType = config.type,
            title = d.title,
            subtitle = listOfNotNull(d.year.takeIf { it.isNotBlank() }, d.status.ifBlank { "requestable" }).joinToString(" · "),
            posterUrl = d.posterUrl,
            tmdbId = d.tmdbId,
            mediaType = d.mediaType,
        )
    }
}

private suspend fun jellyfinSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    return api.searchItems(uid, term).Items.take(10).map { it ->
        SearchResult(
            serviceId = config.id,
            serviceLabel = config.label,
            serviceType = config.type,
            title = it.Name,
            subtitle = listOfNotNull(it.Type.takeIf { t -> t.isNotBlank() }, it.ProductionYear?.toString(), "on Jellyfin").joinToString(" · "),
            posterUrl = jellyImageUrl(config, it.Id, it.ImageTags?.get("Primary"), token),
            jellyItemId = it.Id,
        )
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

suspend fun arrCalendar(config: ServiceConfig): List<ArrCalendarItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val now = java.time.Instant.now()
    val start = now.toString()
    val end = now.plus(java.time.Duration.ofDays(30)).toString()
    val url = "$base/calendar?start=$start&end=$end&includeSeries=true&includeArtist=true&unmonitored=false"
    apiFor<ArrApi>(config, apiKeyHeader(config)).calendar(url).mapNotNull { o ->
        val hasFile = jsBool(o, "hasFile") ?: false
        when (config.type) {
            ServiceType.SONARR -> {
                val series = (o["series"] as? JsonObject)
                val show = series?.let { jsStr(it, "title") } ?: "?"
                val s = jsInt(o, "seasonNumber") ?: 0
                val e = jsInt(o, "episodeNumber") ?: 0
                val ep = jsStr(o, "title") ?: ""
                val date = jsStr(o, "airDateUtc") ?: jsStr(o, "airDate") ?: ""
                ArrCalendarItem(show, "S%02dE%02d%s".format(s, e, if (ep.isNotBlank()) " · $ep" else ""), date.take(10), hasFile)
            }
            ServiceType.LIDARR -> {
                val artist = (o["artist"] as? JsonObject)?.let { jsStr(it, "artistName") } ?: ""
                val album = jsStr(o, "title") ?: "?"
                val date = jsStr(o, "releaseDate") ?: ""
                ArrCalendarItem(album, artist, date.take(10), hasFile)
            }
            else -> {
                val title = jsStr(o, "title") ?: "?"
                val date = jsStr(o, "digitalRelease") ?: jsStr(o, "physicalRelease") ?: jsStr(o, "inCinemas") ?: ""
                val year = jsInt(o, "year")?.takeIf { it > 0 }?.toString() ?: ""
                ArrCalendarItem(title, year, date.take(10), hasFile)
            }
        }
    }.filter { it.date.isNotBlank() }.sortedBy { it.date }.take(30)
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
private fun jsLong(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toLongOrNull()
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
    val sizeMb = (statsObj?.let { jsLong(it, "sizeOnDisk") } ?: jsLong(o, "sizeOnDisk") ?: 0L) / (1024 * 1024)
    val poster = (o["images"] as? JsonArray)?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { jsStr(it, "coverType") == "poster" }
        ?.let { jsStr(it, "remoteUrl") ?: jsStr(it, "url") } ?: ""
    val genres = (o["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.joinToString(", ") ?: ""
    val ratingsObj = o["ratings"] as? JsonObject
    val rating = ratingsObj?.let { r ->
        (r["tmdb"] as? JsonObject ?: r["imdb"] as? JsonObject)?.get("value")?.let { (it as? JsonPrimitive)?.doubleOrNull }
    }
    val facts = buildList {
        rating?.let { add("rating" to "%.1f".format(it)) }
        jsStr(o, "certification")?.takeIf { it.isNotBlank() }?.let { add("rated" to it) }
        jsInt(o, "runtime")?.takeIf { it > 0 }?.let { add("runtime" to "${it}m") }
        when (config.type) {
            ServiceType.SONARR -> {
                statsObj?.let {
                    val total = jsInt(it, "episodeCount") ?: 0
                    val have = jsInt(it, "episodeFileCount") ?: 0
                    add("episodes" to "$have/$total")
                    jsInt(it, "seasonCount")?.let { s -> add("seasons" to s.toString()) }
                }
                jsStr(o, "network")?.takeIf { it.isNotBlank() }?.let { add("network" to it) }
            }
            ServiceType.LIDARR -> {
                statsObj?.let {
                    jsInt(it, "albumCount")?.let { c -> add("albums" to c.toString()) }
                    val total = jsInt(it, "trackCount") ?: 0
                    val have = jsInt(it, "trackFileCount") ?: 0
                    add("tracks" to "$have/$total")
                }
            }
            else -> {
                add("file" to if (jsBool(o, "hasFile") == true) "downloaded" else "missing")
                jsStr(o, "studio")?.takeIf { it.isNotBlank() }?.let { add("studio" to it) }
            }
        }
    }
    ArrDetail(
        id = id,
        title = jsStr(o, "title") ?: jsStr(o, "artistName") ?: "",
        year = jsInt(o, "year") ?: 0,
        overview = jsStr(o, "overview") ?: "",
        monitored = jsBool(o, "monitored") ?: false,
        status = jsStr(o, "status") ?: "",
        sizeMb = sizeMb,
        facts = facts,
        posterUrl = poster,
        tmdbId = jsInt(o, "tmdbId") ?: 0,
        genres = genres,
    )
}

/** Sonarr: episodes for a series, newest season first. */
suspend fun arrEpisodes(config: ServiceConfig, seriesId: Int): List<ArrEpisode> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).episodes("$base/episode", seriesId).map { e ->
        ArrEpisode(e.id, e.seasonNumber, e.episodeNumber, e.title, e.hasFile, e.monitored, e.airDate?.take(10) ?: "")
    }.sortedWith(compareByDescending<ArrEpisode> { it.seasonNumber }.thenByDescending { it.episodeNumber })
}

/** Lidarr: albums for an artist, newest first. */
suspend fun arrAlbums(config: ServiceConfig, artistId: Int): List<ArrAlbum> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).albums("$base/album", artistId).map { o ->
        val stats = o["statistics"] as? JsonObject
        ArrAlbum(
            id = jsInt(o, "id") ?: 0,
            title = jsStr(o, "title") ?: "?",
            year = (jsStr(o, "releaseDate") ?: "").take(4),
            trackCount = stats?.let { jsInt(it, "trackCount") } ?: 0,
            trackFileCount = stats?.let { jsInt(it, "trackFileCount") } ?: 0,
            monitored = jsBool(o, "monitored") ?: false,
        )
    }.sortedByDescending { it.year }
}

/** Lidarr: the track list of an album. */
suspend fun arrTracks(config: ServiceConfig, albumId: Int): List<ArrTrack> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).tracks("$base/track", albumId).map { o ->
        val ms = jsLong(o, "duration") ?: 0L
        val secs = ms / 1000
        ArrTrack(
            trackNumber = jsStr(o, "trackNumber") ?: (jsInt(o, "absoluteTrackNumber")?.toString() ?: ""),
            title = jsStr(o, "title") ?: "?",
            duration = if (secs > 0) "%d:%02d".format(secs / 60, secs % 60) else "",
            hasFile = jsBool(o, "hasFile") ?: false,
        )
    }
}

/** Interactive search. [movieId] for Radarr, [episodeId] for Sonarr, [albumId] for Lidarr. */
suspend fun arrReleases(config: ServiceConfig, movieId: Int?, episodeId: Int?, albumId: Int? = null): List<ArrRelease> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val q = when {
        episodeId != null -> "episodeId=$episodeId"
        albumId != null -> "albumId=$albumId"
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

/** Scans a folder for manually-importable files (Radarr/Sonarr). */
suspend fun arrManualImportScan(config: ServiceConfig, folder: String): List<ArrImportItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    val encoded = java.net.URLEncoder.encode(folder, "UTF-8")
    apiFor<ArrApi>(config, apiKeyHeader(config)).manualImport("$base/manualimport?folder=$encoded&filterExistingFiles=false").map { o ->
        val quality = ((o["quality"] as? JsonObject)?.get("quality") as? JsonObject)?.let { jsStr(it, "name") } ?: ""
        val matched = when (config.type) {
            ServiceType.SONARR -> {
                val series = jsStr((o["series"] as? JsonObject) ?: JsonObject(emptyMap()), "title")
                val eps = (o["episodes"] as? JsonArray)?.mapNotNull { (it as? JsonObject) }
                    ?.joinToString(",") { "S%02dE%02d".format(jsInt(it, "seasonNumber") ?: 0, jsInt(it, "episodeNumber") ?: 0) }
                listOfNotNull(series?.takeIf { it.isNotBlank() }, eps?.takeIf { it.isNotBlank() }).joinToString(" ")
            }
            else -> jsStr((o["movie"] as? JsonObject) ?: JsonObject(emptyMap()), "title") ?: ""
        }
        val rejections = (o["rejections"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { r -> jsStr(r, "reason") } } ?: emptyList()
        val hasMatch = when (config.type) {
            ServiceType.SONARR -> (o["series"] as? JsonObject) != null && (o["episodes"] as? JsonArray)?.isNotEmpty() == true
            else -> (o["movie"] as? JsonObject) != null
        }
        ArrImportItem(
            relativePath = jsStr(o, "relativePath") ?: jsStr(o, "name") ?: "?",
            matchedTitle = matched.ifBlank { "— unmatched —" },
            quality = quality,
            rejection = rejections.joinToString("; "),
            importable = hasMatch && rejections.isEmpty(),
            rawJson = json.encodeToString(JsonObject.serializer(), o),
        )
    }
}

/** Executes a manual import for the given scanned items (importMode "move"). */
suspend fun arrManualImportExecute(config: ServiceConfig, rawItems: List<String>): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val files = rawItems.map { raw ->
            val o = json.parseToJsonElement(raw).jsonObject
            buildJsonObject {
                jsStr(o, "path")?.let { put("path", it) }
                jsStr(o, "folderName")?.let { put("folderName", it) }
                o["quality"]?.let { put("quality", it) }
                o["languages"]?.let { put("languages", it) }
                jsStr(o, "releaseGroup")?.let { put("releaseGroup", it) }
                if (config.type == ServiceType.SONARR) {
                    (o["series"] as? JsonObject)?.let { s -> jsInt(s, "id")?.let { put("seriesId", it) } }
                    (o["episodes"] as? JsonArray)?.let { eps ->
                        putJsonArray("episodeIds") { eps.mapNotNull { (it as? JsonObject)?.let { e -> jsInt(e, "id") } }.forEach { add(it) } }
                    }
                } else {
                    (o["movie"] as? JsonObject)?.let { m -> jsInt(m, "id")?.let { put("movieId", it) } }
                }
            }
        }
        val body = buildJsonObject {
            put("name", "ManualImport")
            put("importMode", "move")
            put("files", JsonArray(files))
        }
        okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).command("$base/command", body), "importing ${files.size} file(s)")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Resolves cast for a tmdbId via a Seerr/Overseerr TMDB proxy. */
suspend fun seerrCast(seerrConfig: ServiceConfig, tmdbId: Int, isTv: Boolean): List<ArrCastMember> = withContext(Dispatchers.IO) {
    if (tmdbId <= 0) return@withContext emptyList()
    val api = apiFor<SeerrApi>(seerrConfig, apiKeyHeader(seerrConfig))
    val detail = if (isTv) api.tvRaw(tmdbId) else api.movieRaw(tmdbId)
    val cast = (detail["credits"] as? JsonObject)?.get("cast") as? JsonArray ?: return@withContext emptyList()
    cast.mapNotNull { it as? JsonObject }.take(20).map { c ->
        val profile = jsStr(c, "profilePath")
        ArrCastMember(
            name = jsStr(c, "name") ?: "?",
            character = jsStr(c, "character") ?: "",
            profileUrl = if (!profile.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$profile" else "",
        )
    }
}

/** Full media detail (poster, facts, genres, cast, availability) for a Seerr movie/show. */
suspend fun seerrMediaDetail(config: ServiceConfig, tmdbId: Int, mediaType: String): SeerrMediaDetail = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val isTv = mediaType == "tv"
    val o = if (isTv) api.tvRaw(tmdbId) else api.movieRaw(tmdbId)
    val title = jsStr(o, "title") ?: jsStr(o, "name") ?: "?"
    val date = jsStr(o, "releaseDate") ?: jsStr(o, "firstAirDate") ?: ""
    val poster = jsStr(o, "posterPath")
    val vote = (o["voteAverage"] as? JsonPrimitive)?.content?.toDoubleOrNull()
    val runtime = jsInt(o, "runtime")
    val genres = (o["genres"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { g -> jsStr(g, "name") } }?.joinToString(" · ") ?: ""
    val statusInt = (o["mediaInfo"] as? JsonObject)?.let { jsInt(it, "status") }
    val cast = ((o["credits"] as? JsonObject)?.get("cast") as? JsonArray)?.mapNotNull { it as? JsonObject }?.take(20)?.map { c ->
        val profile = jsStr(c, "profilePath")
        ArrCastMember(
            name = jsStr(c, "name") ?: "?",
            character = jsStr(c, "character") ?: "",
            profileUrl = if (!profile.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$profile" else "",
        )
    } ?: emptyList()
    val facts = buildList {
        date.take(4).takeIf { it.isNotBlank() }?.let { add("year" to it) }
        runtime?.takeIf { it > 0 }?.let { add("runtime" to "$it min") }
        vote?.takeIf { it > 0 }?.let { add("score" to "%.1f".format(it)) }
    }
    SeerrMediaDetail(
        tmdbId = tmdbId,
        title = title,
        year = date.take(4),
        mediaType = mediaType,
        overview = jsStr(o, "overview") ?: "",
        posterUrl = if (!poster.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$poster" else "",
        facts = facts,
        genres = genres,
        status = seerrMediaStatusText(statusInt),
        cast = cast,
    )
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
/** Triggers an RSS sync (check all indexer feeds for new releases now). */
suspend fun arrRssSync(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val body = buildJsonObject { put("name", "RssSync") }
        okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).command("${arrBase(config.type)}/command", body), "RSS sync started")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

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

// ---- ntfy ----

private fun ntfyRequest(config: ServiceConfig, url: String): Request =
    Request.Builder().url(url).apply {
        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
        config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) header(k, v) }
    }.build()

private suspend fun ntfyStatus(config: ServiceConfig): ServiceStatus {
    val resp = baseOkClient.newCall(ntfyRequest(config, "${config.normalizedBaseUrl}v1/health")).execute()
    resp.use { if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}") }
    return ServiceStatus(
        ok = true,
        stats = listOf("${config.topics.size}" to "TOPICS"),
        note = config.topics.joinToString(", ").takeIf { it.isNotBlank() },
    )
}

/** Cached messages of one topic, newest first (ntfy keeps ~12 h by default, more if configured). */
suspend fun ntfyHistory(config: ServiceConfig, topic: String, since: String = "48h"): List<NtfyMessage> = withContext(Dispatchers.IO) {
    val url = "${config.normalizedBaseUrl}$topic/json?poll=1&since=$since"
    val resp = baseOkClient.newCall(ntfyRequest(config, url)).execute()
    resp.use { r ->
        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
        val body = r.body?.string().orEmpty()
        body.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { org.phioster.nexarr.notify.parseNtfyLine(it) }
            .sortedByDescending { it.time }
            .toList()
    }
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
            ServiceType.NTFY -> ntfyStatus(config)
        }
    } catch (t: Throwable) {
        ServiceStatus(ok = false, error = t.message ?: t.javaClass.simpleName)
    }
}

/** Returns the token to use for Jellyfin data calls (API key, or a login token). */
private val jellyfinAuthLock = kotlinx.coroutines.sync.Mutex()

private suspend fun jellyfinAccessToken(config: ServiceConfig): String {
    if (!config.useLogin) return config.apiKey
    jellyfinSession[config.id]?.let { return it.first }
    // Serialize login so concurrent cards on cold start don't each authenticate (and race a 401).
    return jellyfinAuthLock.withLock {
        jellyfinSession[config.id]?.let { return@withLock it.first }
        val resp = apiFor<JellyfinAuthApi>(config, mapOf("Authorization" to MB_AUTH))
            .authenticate(JfAuthReq(config.username, config.password))
        val label = (if (resp.User.Policy.IsAdministrator) "admin: " else "user: ") + resp.User.Name
        jellyfinSession[config.id] = resp.AccessToken to label
        resp.AccessToken
    }
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

private fun jfApi(config: ServiceConfig, token: String) = apiFor<JellyfinApi>(config, mapOf("X-Emby-Token" to token))

suspend fun jellyfinSessions(config: ServiceConfig): List<JellySession> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).sessions()
        .filter { it.UserName != null || it.NowPlayingItem != null }
        .map { s ->
            val np = s.NowPlayingItem
            val run = np?.RunTimeTicks ?: 0L
            val pos = s.PlayState?.PositionTicks ?: 0L
            val pct = if (run > 0) (pos.toFloat() / run).coerceIn(0f, 1f) else 0f
            val subtitle = when {
                np == null -> (s.Client ?: "").ifBlank { "idle" }
                np.Type == "Episode" -> np.SeriesName ?: "Episode"
                else -> listOfNotNull(np.Type, np.ProductionYear?.toString()).joinToString(" · ")
            }
            JellySession(
                id = s.Id,
                user = s.UserName ?: "?",
                device = s.DeviceName ?: "",
                client = s.Client ?: "",
                nowPlaying = np?.Name ?: "",
                subtitle = subtitle,
                progressPct = pct,
                paused = s.PlayState?.IsPaused ?: false,
                canControl = s.SupportsRemoteControl,
                lastActivity = (s.LastActivityDate ?: "").take(16).replace('T', ' '),
            )
        }
        .sortedByDescending { it.nowPlaying.isNotEmpty() }
}

suspend fun jellyfinUsers(config: ServiceConfig): List<JellyUser> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).users().map { u ->
        JellyUser(
            id = u.Id,
            name = u.Name,
            lastActivity = (u.LastActivityDate ?: "").take(16).replace('T', ' '),
            admin = u.Policy.IsAdministrator,
            disabled = u.Policy.IsDisabled,
            allowDownloads = u.Policy.EnableContentDownloading,
            enableAllFolders = u.Policy.EnableAllFolders,
            enabledFolders = u.Policy.EnabledFolders,
        )
    }.sortedByDescending { it.lastActivity }
}

suspend fun jellyfinLibraries(config: ServiceConfig): List<JellyLibrary> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    runCatching {
        jfApi(config, token).virtualFolders().map {
            JellyLibrary(id = it.ItemId, name = it.Name, collectionType = it.CollectionType ?: "", locations = it.Locations)
        }
    }.getOrDefault(emptyList())
}

/** Create a user (optionally with an initial password). */
suspend fun jellyfinCreateUser(config: ServiceConfig, name: String, password: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val body = buildJsonObject {
            put("Name", name)
            if (password.isNotBlank()) put("Password", password)
        }
        okOr(jfApi(config, token).createUser(body), "user created")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinDeleteUser(config: ServiceConfig, userId: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).deleteUser(userId), "user deleted")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/**
 * Update a user's policy. Fetches the current full policy first and overlays only the
 * fields we manage, so nothing else on the policy gets reset.
 */
suspend fun jellyfinSetPolicy(
    config: ServiceConfig,
    userId: String,
    admin: Boolean,
    disabled: Boolean,
    allowDownloads: Boolean,
    enableAllFolders: Boolean,
    enabledFolders: List<String>,
): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val api = jfApi(config, token)
        val current = runCatching { api.user(userId)["Policy"]?.jsonObject }.getOrNull()
        val body = buildJsonObject {
            current?.forEach { (k, v) -> put(k, v) }
            put("IsAdministrator", admin)
            put("IsDisabled", disabled)
            put("EnableContentDownloading", allowDownloads)
            put("EnableAllFolders", enableAllFolders)
            putJsonArray("EnabledFolders") { enabledFolders.forEach { add(it) } }
        }
        okOr(api.setPolicy(userId, body), "policy saved")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Reset a user's password to a new value (admin reset; no current password needed). */
suspend fun jellyfinSetPassword(config: ServiceConfig, userId: String, newPassword: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val api = jfApi(config, token)
        // First clear the existing password, then set the new one (Jellyfin admin-reset flow).
        api.setPassword(userId, buildJsonObject { put("ResetPassword", true) })
        val body = buildJsonObject {
            put("NewPw", newPassword)
            put("ResetPassword", false)
        }
        okOr(api.setPassword(userId, body), "password reset")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

// ---- Media browsing ----

/** Resolve the user id whose libraries we browse (the logged-in user, else an admin). */
private suspend fun jellyfinResolveUserId(config: ServiceConfig, api: JellyfinApi): String {
    jellyfinUserIdCache[config.id]?.let { return it }
    val users = api.users()
    val chosen = if (config.useLogin) {
        users.firstOrNull { it.Name.equals(config.username, true) } ?: users.firstOrNull()
    } else {
        users.firstOrNull { it.Policy.IsAdministrator } ?: users.firstOrNull()
    }
    val id = chosen?.Id ?: ""
    if (id.isNotBlank()) jellyfinUserIdCache[config.id] = id
    return id
}

private fun jellyImageUrl(config: ServiceConfig, id: String, tag: String?, token: String): String {
    if (id.isBlank()) return ""
    var url = "${config.normalizedBaseUrl}Items/$id/Images/Primary?maxHeight=450&quality=90"
    if (!tag.isNullOrBlank()) url += "&tag=$tag"
    // No api_key in the URL — it would end up in Coil's disk cache. Loaders send
    // the token via jellyfinImageHeaders() instead.
    return url
}

/** Headers for loading Jellyfin images (Coil), keeping the token out of the URL. */
fun jellyfinImageHeaders(config: ServiceConfig): Map<String, String> {
    val token = if (!config.useLogin) config.apiKey else jellyfinSession[config.id]?.first.orEmpty()
    return if (token.isNotBlank()) mapOf("X-Emby-Token" to token) else emptyMap()
}

private fun jfSubtitle(item: JfItem): String = when (item.Type) {
    "Episode" -> buildString {
        item.SeriesName?.let { append(it) }
        val s = item.ParentIndexNumber; val e = item.IndexNumber
        if (s != null && e != null) { if (isNotEmpty()) append(" · "); append("S%02dE%02d".format(s, e)) }
    }
    "Audio", "MusicAlbum" -> item.AlbumArtist ?: (item.ProductionYear?.toString() ?: "")
    else -> item.ProductionYear?.toString() ?: ""
}

private fun JfItem.toMediaItem(config: ServiceConfig, token: String) = JellyMediaItem(
    id = Id,
    name = Name,
    kind = CollectionType ?: Type,
    subtitle = jfSubtitle(this),
    posterUrl = jellyImageUrl(config, Id, ImageTags?.get("Primary"), token),
    isFolder = IsFolder,
    progressPct = ((UserData?.PlayedPercentage ?: 0.0) / 100.0).toFloat(),
    number = IndexNumber,
)

/** The user's libraries (Movies, Shows, Music, …). */
suspend fun jellyfinLibraryViews(config: ServiceConfig): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.views(uid).Items.map { it.toMediaItem(config, token) }
}

/** "Continue watching" — partially played items. */
suspend fun jellyfinResume(config: ServiceConfig): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.resume(uid).Items.map { it.toMediaItem(config, token) }
}

/** "Recently added" — newest items, optionally within one library. */
suspend fun jellyfinLatest(config: ServiceConfig, parentId: String? = null): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.latest(uid, 20, parentId).map { it.toMediaItem(config, token) }
}

/**
 * Contents of a library or folder.
 *
 * Virtual items (metadata placeholders with no actual media file) are dropped, matching what
 * Jellyfin itself shows — this hides "missing" episodes and specials that aren't really present.
 * When [seasonNumber] is given (parent is a season), episodes are also filtered to that exact
 * season, since Jellyfin otherwise merges Specials (season 0) into the season they aired within.
 */
suspend fun jellyfinItems(config: ServiceConfig, parentId: String, seasonNumber: Int? = null): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.items(uid, parentId).Items
        .filter { it.LocationType != "Virtual" }
        .filter { seasonNumber == null || it.Type != "Episode" || it.ParentIndexNumber == seasonNumber }
        .map { it.toMediaItem(config, token) }
}

/** Full detail for one media item, including cast. */
suspend fun jellyfinItemDetail(config: ServiceConfig, itemId: String): JellyMediaDetail = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    val d = api.itemDetail(uid, itemId)
    val facts = buildList {
        d.ProductionYear?.takeIf { it > 0 }?.let { add("year" to it.toString()) }
        d.RunTimeTicks?.takeIf { it > 0 }?.let { add("runtime" to "${it / 600_000_000} min") }
        d.OfficialRating?.takeIf { it.isNotBlank() }?.let { add("rating" to it) }
        d.CommunityRating?.let { add("score" to "%.1f".format(it)) }
        d.Studios.firstOrNull()?.Name?.takeIf { it.isNotBlank() }?.let { add("studio" to it) }
    }
    val cast = d.People.filter { it.Type == "Actor" }.take(20).map { p ->
        ArrCastMember(
            name = p.Name,
            character = p.Role ?: "",
            profileUrl = if (!p.PrimaryImageTag.isNullOrBlank()) jellyImageUrl(config, p.Id, p.PrimaryImageTag, token) else "",
        )
    }
    JellyMediaDetail(
        id = d.Id,
        name = d.Name,
        overview = d.Overview ?: "",
        posterUrl = jellyImageUrl(config, d.Id, d.ImageTags?.get("Primary"), token),
        facts = facts,
        genres = d.Genres.joinToString(" · "),
        cast = cast,
    )
}

/** Trigger a metadata/library refresh for a single library or item. */
suspend fun jellyfinScanItem(config: ServiceConfig, itemId: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).refreshItem(itemId), "scan started")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Playback control: cmd = "Pause" | "Unpause" | "Stop" | "PlayPause". */
suspend fun jellyfinPlayCommand(config: ServiceConfig, sessionId: String, cmd: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).playCommand(sessionId, cmd), cmd.lowercase())
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinSendMessage(config: ServiceConfig, sessionId: String, text: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).message(sessionId, JfMessageReq(text)), "message sent")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinSystemInfo(config: ServiceConfig): JellySystemInfo = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val i = jfApi(config, token).systemInfo()
    JellySystemInfo(version = i.Version, serverName = i.ServerName, os = i.OperatingSystem)
}

/** ISO UTC timestamp -> compact "MM-dd HH:mm", or "" when absent. */
private fun formatJellyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val s = iso.take(16) // "2026-07-15T14:03"
    return if (s.length >= 16) s.substring(5).replace('T', ' ') else s.replace('T', ' ')
}

suspend fun jellyfinTasks(config: ServiceConfig): List<JellyTask> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).scheduledTasks().map { t ->
        JellyTask(
            id = t.Id,
            name = t.Name,
            state = t.State,
            progress = (t.CurrentProgressPercentage ?: 0.0).toInt(),
            lastResult = t.LastExecutionResult?.Status ?: "",
            lastRun = formatJellyDate(t.LastExecutionResult?.EndTimeUtc),
        )
    }.sortedBy { it.name }
}

suspend fun jellyfinRunTask(config: ServiceConfig, taskId: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).runTask(taskId), "started")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinActivity(config: ServiceConfig): List<JellyActivity> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).activityLog().Items.map { e ->
        JellyActivity(
            name = e.Name,
            date = e.Date.take(16).replace('T', ' '),
            severity = e.Severity,
            overview = e.ShortOverview ?: "",
        )
    }
}

suspend fun jellyfinDevices(config: ServiceConfig): List<JellyDevice> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).devices().Items.map { d ->
        JellyDevice(
            name = d.Name.ifBlank { "?" },
            app = d.AppName,
            user = d.LastUserName ?: "",
            lastActivity = (d.DateLastActivity ?: "").take(16).replace('T', ' '),
        )
    }.sortedByDescending { it.lastActivity }
}

suspend fun jellyfinRestart(config: ServiceConfig): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).restartServer(), "restarting")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

suspend fun jellyfinLogFiles(config: ServiceConfig): List<JellyLogFile> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).logFiles().map {
        JellyLogFile(
            name = it.Name,
            date = it.DateModified.take(16).replace('T', ' ').drop(5),
            size = humanSize(it.Size),
        )
    }.sortedByDescending { it.date }
}

/** Returns the tail of a server log file (last [maxLines] lines — files can be several MB). */
suspend fun jellyfinLogContent(config: ServiceConfig, name: String, maxLines: Int = 400): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val resp = jfApi(config, token).logContent(name)
        if (!resp.isSuccessful) return@withContext "error: HTTP ${resp.code()}"
        val text = resp.body()?.string() ?: return@withContext "error: empty response"
        val lines = text.lines()
        if (lines.size <= maxLines) text
        else "… (${lines.size - maxLines} earlier lines truncated)\n" + lines.takeLast(maxLines).joinToString("\n")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinAddLibrary(config: ServiceConfig, name: String, collectionType: String, path: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).addVirtualFolder(name, collectionType.ifBlank { null }, listOf(path)), "library added")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinDeleteLibrary(config: ServiceConfig, name: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).deleteVirtualFolder(name), "library deleted")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinRenameLibrary(config: ServiceConfig, name: String, newName: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).renameVirtualFolder(name, newName), "library renamed")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinAddLibraryPath(config: ServiceConfig, libraryName: String, path: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).addLibraryPath(JfMediaPath(Name = libraryName, PathInfo = JfMediaPathInfo(Path = path))), "path added")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinRemoveLibraryPath(config: ServiceConfig, libraryName: String, path: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).removeLibraryPath(libraryName, path), "path removed")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinPlugins(config: ServiceConfig): List<JellyPlugin> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).plugins().map {
        JellyPlugin(
            id = it.Id,
            version = it.Version,
            name = it.Name,
            description = it.Description,
            status = it.Status,
            canUninstall = it.CanUninstall,
        )
    }.sortedBy { it.name.lowercase() }
}

suspend fun jellyfinSetPluginEnabled(config: ServiceConfig, id: String, version: String, enabled: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val api = jfApi(config, token)
        okOr(if (enabled) api.enablePlugin(id, version) else api.disablePlugin(id, version), if (enabled) "plugin enabled (restart server to apply)" else "plugin disabled (restart server to apply)")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinUninstallPlugin(config: ServiceConfig, id: String, version: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).uninstallPlugin(id, version), "plugin uninstalled (restart server to apply)")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** The server's plugin catalog, with installed ones flagged. */
suspend fun jellyfinPackages(config: ServiceConfig): List<JellyPackage> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val installed = runCatching { api.plugins().map { it.Name.lowercase() }.toSet() }.getOrDefault(emptySet())
    api.packages().map {
        JellyPackage(
            name = it.name,
            guid = it.guid,
            description = it.description.ifBlank { it.overview },
            version = it.versions.firstOrNull()?.version ?: "",
            installed = it.name.lowercase() in installed,
        )
    }.sortedBy { it.name.lowercase() }
}

suspend fun jellyfinInstallPackage(config: ServiceConfig, name: String, guid: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).installPackage(name, guid), "installing… (restart server when done)")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinLiveTv(config: ServiceConfig): JellyLiveTv = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val info = api.liveTvInfo()
    val opts = runCatching { api.liveTvOptions() }.getOrDefault(JfLiveTvOptions())
    JellyLiveTv(
        enabled = info.IsEnabled,
        services = info.Services.map { s ->
            buildString {
                append(s.Name.ifBlank { "Live TV" })
                append(" — ").append(s.Status.ifBlank { "?" })
                append(" (${s.Tuners.size} tuner${if (s.Tuners.size == 1) "" else "s"})")
                if (!s.StatusMessage.isNullOrBlank()) append(" · ${s.StatusMessage}")
            }
        },
        tuners = opts.TunerHosts.map {
            JellyTuner(id = it.Id, name = it.FriendlyName?.takeIf { n -> n.isNotBlank() } ?: it.Type, type = it.Type, url = it.Url)
        },
        providers = opts.ListingProviders.map {
            JellyGuideProvider(id = it.Id, type = it.Type, path = it.Path ?: it.ListingsId ?: "")
        },
    )
}

suspend fun jellyfinChannels(config: ServiceConfig): List<JellyChannel> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = runCatching { jellyfinResolveUserId(config, api) }.getOrNull()
    api.liveTvChannels(userId = uid).Items.map {
        JellyChannel(
            id = it.Id,
            number = it.ChannelNumber ?: "",
            name = it.Name,
            nowPlaying = it.CurrentProgram?.Name ?: "",
        )
    }
}

suspend fun jellyfinAddTuner(config: ServiceConfig, type: String, url: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val body = buildJsonObject { put("Type", type); put("Url", url) }
        okOr(jfApi(config, token).addTunerHost(body), "tuner added")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinDeleteTuner(config: ServiceConfig, id: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).deleteTunerHost(id), "tuner removed")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

/** Adds an XMLTV guide provider (file path or URL). */
suspend fun jellyfinAddXmltvProvider(config: ServiceConfig, path: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val body = buildJsonObject { put("Type", "xmltv"); put("Path", path) }
        okOr(jfApi(config, token).addListingProvider(body), "guide provider added")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinDeleteProvider(config: ServiceConfig, id: String): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        okOr(jfApi(config, token).deleteListingProvider(id), "guide provider removed")
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

suspend fun prowlarrDeleteIndexer(config: ServiceConfig, id: Int): String = withContext(Dispatchers.IO) {
    try {
        okOr(apiFor<ProwlarrApi>(config, apiKeyHeader(config)).deleteIndexer(id), "deleted")
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
