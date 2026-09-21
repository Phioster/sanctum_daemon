package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Sign-in for the TV client.
 *
 * A television has no keyboard worth the name, so the primary route is Jellyfin's **Quick Connect**:
 * the TV shows a six-digit code, the user approves it from a device that *does* have a keyboard, and
 * the TV receives a real user access token. Username/password stays as the fallback for servers with
 * Quick Connect switched off.
 */

/** The TV identifies as its own device. Sharing a DeviceId with the phone would collide sessions. */
internal const val TV_MB_AUTH =
    "MediaBrowser Client=\"Sanctumd TV\", Device=\"Android TV\", DeviceId=\"sanctumd-tv\", Version=\"1.0.0\""

@Serializable internal data class JfQuickConnectState(
    val Authenticated: Boolean = false,
    val Secret: String = "",
    val Code: String = "",
)

@Serializable internal data class JfQcSecretReq(val Secret: String)
@Serializable internal data class JfTvUser(val Id: String = "", val Name: String = "")
@Serializable internal data class JfTvAuthResp(val AccessToken: String = "", val User: JfTvUser = JfTvUser())
@Serializable data class JfPublicInfo(val ServerName: String = "", val Version: String = "", val Id: String = "")

internal interface JellyfinTvAuthApi {
    @GET("System/Info/Public") suspend fun publicInfo(): JfPublicInfo
    @GET("QuickConnect/Enabled") suspend fun quickConnectEnabled(): Boolean

    // Jellyfin moved Initiate from GET to POST across releases; the caller tries both.
    @POST("QuickConnect/Initiate") suspend fun initiatePost(): JfQuickConnectState
    @GET("QuickConnect/Initiate") suspend fun initiateGet(): JfQuickConnectState

    @GET("QuickConnect/Connect") suspend fun connect(@Query("secret") secret: String): JfQuickConnectState
    @POST("Users/AuthenticateWithQuickConnect") suspend fun authWithQuickConnect(@Body body: JfQcSecretReq): JfTvAuthResp
    @POST("Users/AuthenticateByName") suspend fun authByName(@Body body: JfAuthReq): JfTvAuthResp
}

private fun tvAuthApi(baseUrl: String): JellyfinTvAuthApi {
    val stub = ServiceConfig(type = ServiceType.JELLYFIN, label = "setup", baseUrl = baseUrl)
    return apiFor(stub, mapOf("Authorization" to TV_MB_AUTH))
}

/** A completed sign-in: the token goes into [ServiceConfig.apiKey], the id into [ServiceConfig.userId]. */
data class TvAuth(val accessToken: String, val userId: String, val userName: String)

/** A Quick Connect attempt in flight: [code] is shown on screen, [secret] identifies it to the server. */
data class QuickConnectAttempt(val code: String, val secret: String)

/** Reachability + identity check for a typed-in server address. Throws when the URL isn't a Jellyfin. */
suspend fun jellyfinTvPublicInfo(baseUrl: String): JfPublicInfo = withContext(Dispatchers.IO) {
    tvAuthApi(baseUrl).publicInfo()
}

/** Whether the server offers Quick Connect at all (admins can disable it). */
suspend fun jellyfinQuickConnectAvailable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
    runCatching { tvAuthApi(baseUrl).quickConnectEnabled() }.getOrDefault(false)
}

/** Starts a Quick Connect attempt; the returned [QuickConnectAttempt.code] is what the user types in. */
suspend fun jellyfinQuickConnectStart(baseUrl: String): QuickConnectAttempt = withContext(Dispatchers.IO) {
    val api = tvAuthApi(baseUrl)
    val state = runCatching { api.initiatePost() }.getOrElse { api.initiateGet() }
    if (state.Code.isBlank() || state.Secret.isBlank()) error("server returned no Quick Connect code")
    QuickConnectAttempt(state.Code, state.Secret)
}

/** True once the user has approved the code elsewhere. Polled by the setup screen. */
suspend fun jellyfinQuickConnectApproved(baseUrl: String, secret: String): Boolean = withContext(Dispatchers.IO) {
    runCatching { tvAuthApi(baseUrl).connect(secret).Authenticated }.getOrDefault(false)
}

/** Exchanges an approved Quick Connect secret for a real access token. */
suspend fun jellyfinQuickConnectFinish(baseUrl: String, secret: String): TvAuth = withContext(Dispatchers.IO) {
    val resp = tvAuthApi(baseUrl).authWithQuickConnect(JfQcSecretReq(secret))
    if (resp.AccessToken.isBlank()) error("server returned no access token")
    TvAuth(resp.AccessToken, resp.User.Id, resp.User.Name)
}

/** Fallback sign-in for servers without Quick Connect. */
suspend fun jellyfinTvLogin(baseUrl: String, username: String, password: String): TvAuth = withContext(Dispatchers.IO) {
    val resp = tvAuthApi(baseUrl).authByName(JfAuthReq(username, password))
    if (resp.AccessToken.isBlank()) error("server returned no access token")
    TvAuth(resp.AccessToken, resp.User.Id, resp.User.Name)
}

/**
 * "Next up", the next unwatched episode of every series in progress. Distinct from Continue
 * Watching (which is a *partially played* item): on a TV this is the row people actually use.
 */
suspend fun jellyfinNextUp(config: ServiceConfig, limit: Int = 20): List<JellyMediaItem> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    api.nextUp(uid, limit).Items.map { it.toMediaItem(config, token) }
}

/**
 * The episode a "play" button on a series or season should start.
 *
 * Asks the server what is next up for that series; if nothing comes back (nothing watched yet, or a
 * season with everything already seen) it falls back to the first episode underneath, so the button
 * always does something rather than sitting there dead.
 */
suspend fun jellyfinPlayableEpisode(
    config: ServiceConfig,
    folderId: String,
    isSeason: Boolean,
): JellyMediaItem? = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)

    if (!isSeason) {
        runCatching { api.nextUp(uid, limit = 1, seriesId = folderId).Items.firstOrNull() }
            .getOrNull()?.let { return@withContext it.toMediaItem(config, token) }
    }
    // Fall back to the episodes themselves. A season's children *are* episodes; a series' children
    // are seasons, so that one has to go through the flat per-series episode route instead.
    val episodes = if (isSeason) {
        runCatching {
            api.items(uid, folderId, sortBy = "ParentIndexNumber,IndexNumber,SortName").Items
        }.getOrDefault(emptyList())
    } else {
        runCatching { jfSegmentsApi(config, token).seriesEpisodes(folderId, uid).Items }
            .getOrDefault(emptyList())
    }.filter { it.Type == "Episode" && it.LocationType != "Virtual" }

    val pick = episodes.firstOrNull { it.UserData?.Played != true } ?: episodes.firstOrNull()
    pick?.toMediaItem(config, token)
}
