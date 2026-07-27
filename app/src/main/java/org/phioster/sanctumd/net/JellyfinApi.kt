package org.phioster.sanctumd.net

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
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/** config.id -> (jellyfin access token, user label) once a login has succeeded. */
internal val jellyfinSession = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

/** config.id -> the resolved Jellyfin user id used for media-browsing endpoints. */
internal val jellyfinUserIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** Drop a cached Jellyfin login token (e.g. after its config was edited). */
fun clearJellyfinSession(id: String) {
    jellyfinSession.remove(id)
    jellyfinUserIdCache.remove(id)
}

@Serializable
internal data class JfCounts(
    val MovieCount: Int = 0,
    val SeriesCount: Int = 0,
    val EpisodeCount: Int = 0,
    val SongCount: Int = 0,
)

@Serializable internal data class JfSession(
    val Id: String = "",
    val UserName: String? = null,
    val Client: String? = null,
    val DeviceName: String? = null,
    val LastActivityDate: String? = null,
    val SupportsRemoteControl: Boolean = false,
    val NowPlayingItem: JfNowPlaying? = null,
    val PlayState: JfPlayState? = null,
)
@Serializable internal data class JfNowPlaying(
    val Name: String? = null,
    val Type: String? = null,
    val SeriesName: String? = null,
    val ProductionYear: Int? = null,
    val RunTimeTicks: Long? = null,
)
@Serializable internal data class JfPlayState(
    val PositionTicks: Long? = null,
    val IsPaused: Boolean = false,
)
@Serializable internal data class JfUserFull(
    val Id: String = "",
    val Name: String = "",
    val LastActivityDate: String? = null,
    val Policy: JfPolicy = JfPolicy(),
)
@Serializable internal data class JfVirtualFolder(
    val Name: String = "",
    val ItemId: String = "",
    val CollectionType: String? = null,
    val Locations: List<String> = emptyList(),
)
@Serializable internal data class JfLogFile(
    val Name: String = "",
    val DateModified: String = "",
    val Size: Long = 0,
)
@Serializable internal data class JfPlugin(
    val Id: String = "",
    val Version: String = "",
    val Name: String = "",
    val Description: String = "",
    val Status: String = "",
    val CanUninstall: Boolean = true,
)
@Serializable internal data class JfPackageVersion(val version: String = "")
@Serializable internal data class JfPackage(
    val name: String = "",
    val guid: String = "",
    val description: String = "",
    val overview: String = "",
    val versions: List<JfPackageVersion> = emptyList(),
)
@Serializable internal data class JfMediaPathInfo(val Path: String = "")
@Serializable internal data class JfMediaPath(val Name: String = "", val PathInfo: JfMediaPathInfo = JfMediaPathInfo())
@Serializable internal data class JfLiveTvServiceInfo(
    val Name: String = "",
    val Status: String = "",
    val StatusMessage: String? = null,
    val Tuners: List<String> = emptyList(),
)
@Serializable internal data class JfLiveTvInfo(
    val IsEnabled: Boolean = false,
    val Services: List<JfLiveTvServiceInfo> = emptyList(),
)
@Serializable internal data class JfTunerHost(
    val Id: String = "",
    val Url: String = "",
    val Type: String = "",
    val FriendlyName: String? = null,
)
@Serializable internal data class JfListingProvider(
    val Id: String = "",
    val Type: String = "",
    val Path: String? = null,
    val ListingsId: String? = null,
)
@Serializable internal data class JfLiveTvOptions(
    val TunerHosts: List<JfTunerHost> = emptyList(),
    val ListingProviders: List<JfListingProvider> = emptyList(),
)
@Serializable internal data class JfChannelProgram(val Name: String = "")
@Serializable internal data class JfChannel(
    val Id: String = "",
    val Name: String = "",
    val ChannelNumber: String? = null,
    val CurrentProgram: JfChannelProgram? = null,
)
@Serializable internal data class JfChannelsResp(val Items: List<JfChannel> = emptyList())
@Serializable internal data class JfMessageReq(val Text: String, val Header: String = "Sanctumd", val TimeoutMs: Long = 5000)

@Serializable internal data class JfSystemInfo(
    val Version: String = "",
    val ServerName: String = "",
    val OperatingSystem: String = "",
)
@Serializable internal data class JfPlaybackQueryReq(val CustomQueryString: String, val ReplaceUserId: Boolean = true)
@Serializable internal data class JfPlaybackQueryResp(
    val colums: List<String> = emptyList(),
    val results: List<List<String>> = emptyList(),
    val message: String? = null,
)
@Serializable internal data class JfTaskResult(val Status: String = "", val EndTimeUtc: String? = null)
@Serializable internal data class JfTask(
    val Id: String = "",
    val Name: String = "",
    val State: String = "",
    val CurrentProgressPercentage: Double? = null,
    val LastExecutionResult: JfTaskResult? = null,
)
@Serializable internal data class JfActivityEntry(
    val Name: String = "",
    val Type: String = "",
    val Date: String = "",
    val Severity: String = "",
    val ShortOverview: String? = null,
)
@Serializable internal data class JfActivityPage(val Items: List<JfActivityEntry> = emptyList())

@Serializable internal data class JfDevice(
    val Name: String = "",
    val AppName: String = "",
    val LastUserName: String? = null,
    val DateLastActivity: String? = null,
)
@Serializable internal data class JfDevicePage(val Items: List<JfDevice> = emptyList())

@Serializable internal data class JfUserData(
    val PlayedPercentage: Double? = null,
    val Played: Boolean = false,
    val PlaybackPositionTicks: Long? = null, // resume position (100ns ticks)
)
@Serializable internal data class JfItem(
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
    val OfficialRating: String? = null,
    val UserData: JfUserData? = null,
)
@Serializable internal data class JfItemsResp(val Items: List<JfItem> = emptyList())
@Serializable internal data class JfPerson(
    val Id: String = "",
    val Name: String = "",
    val Role: String? = null,
    val Type: String? = null,
    val PrimaryImageTag: String? = null,
)
@Serializable internal data class JfStudio(val Name: String = "")
@Serializable internal data class JfItemDetail(
    val Id: String = "",
    val Name: String = "",
    val Overview: String? = null,
    val ProductionYear: Int? = null,
    val Genres: List<String> = emptyList(),
    val RunTimeTicks: Long? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Double? = null,
    val Type: String = "",
    val SeriesName: String? = null, // episodes: the show they belong to
    val ParentIndexNumber: Int? = null, // episodes: season number
    val IndexNumber: Int? = null, // episodes: episode number
    val AlbumArtist: String? = null, // audio: performing artist
    val Studios: List<JfStudio> = emptyList(),
    val People: List<JfPerson> = emptyList(),
    val ImageTags: Map<String, String>? = null,
    val UserData: JfUserData? = null, // watched state / resume position for this user
)

@Serializable internal data class JfAuthReq(val Username: String, val Pw: String)
@Serializable internal data class JfAuthResp(val AccessToken: String = "", val User: JfUser = JfUser())
@Serializable internal data class JfUser(val Name: String = "", val Policy: JfPolicy = JfPolicy())
@Serializable internal data class JfPolicy(
    val IsAdministrator: Boolean = false,
    val IsDisabled: Boolean = false,
    val EnableContentDownloading: Boolean = false,
    val EnableAllFolders: Boolean = true,
    val EnabledFolders: List<String> = emptyList(),
)

internal interface JellyfinAuthApi {
    @POST("Users/AuthenticateByName") suspend fun authenticate(@Body body: JfAuthReq): JfAuthResp
}

internal interface JellyfinApi {
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
    @GET("Users/{uid}/Items/Latest") suspend fun latest(@Path("uid") uid: String, @Query("Limit") limit: Int = 20, @Query("ParentId") parentId: String? = null, @Query("Fields") fields: String = "OfficialRating"): List<JfItem>
    @GET("Users/{uid}/Items/Resume") suspend fun resume(@Path("uid") uid: String, @Query("Limit") limit: Int = 20, @Query("Fields") fields: String = "OfficialRating"): JfItemsResp
    @GET("Users/{uid}/Items") suspend fun items(
        @Path("uid") uid: String,
        @Query("ParentId") parentId: String,
        @Query("SortBy") sortBy: String = "IsFolder,SortName",
        @Query("Limit") limit: Int = 300,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,OfficialRating",
    ): JfItemsResp
    @GET("Users/{uid}/Items/{id}") suspend fun itemDetail(@Path("uid") uid: String, @Path("id") id: String): JfItemDetail
    @GET("Users/{uid}/Items") suspend fun searchItems(
        @Path("uid") uid: String,
        @Query("searchTerm") term: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") types: String = "Movie,Series,MusicAlbum",
        @Query("Limit") limit: Int = 12,
        @Query("Fields") fields: String = "OfficialRating",
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
    // Playback Reporting plugin (optional on the server): run a custom SQL query over PlaybackActivity.
    @POST("user_usage_stats/submit_custom_query") suspend fun playbackQuery(@Body body: JfPlaybackQueryReq): JfPlaybackQueryResp
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

/** Returns the token to use for Jellyfin data calls (API key, or a login token). */
internal val jellyfinAuthLock = kotlinx.coroutines.sync.Mutex()

internal fun jfApi(config: ServiceConfig, token: String) = apiFor<JellyfinApi>(config, mapOf("X-Emby-Token" to token))
