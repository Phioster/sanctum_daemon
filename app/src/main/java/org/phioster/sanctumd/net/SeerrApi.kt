package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.sanctumd.model.ArrCastMember
import org.phioster.sanctumd.model.SeerrComment
import org.phioster.sanctumd.model.SearchResult
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.SeerrIssueDetail
import org.phioster.sanctumd.model.SeerrIssueItem
import org.phioster.sanctumd.model.SeerrRequestItem
import org.phioster.sanctumd.model.SeerrMediaDetail
import org.phioster.sanctumd.model.SeerrSearchItem
import org.phioster.sanctumd.model.SeerrUserInfo
import org.phioster.sanctumd.model.SeerrSeason
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable internal data class SeerrCounts(
    val total: Int = 0,
    val movie: Int = 0,
    val tv: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val processing: Int = 0,
    val available: Int = 0,
    val declined: Int = 0,
)
@Serializable internal data class SeerrUserRec(
    val id: Int = 0,
    val displayName: String = "",
    val username: String? = null,
    val email: String? = null,
    val requestCount: Int = 0,
)
@Serializable internal data class SeerrUserPage(val results: List<SeerrUserRec> = emptyList())

@Serializable internal data class SeerrMedia(val tmdbId: Int = 0, val mediaType: String = "")
@Serializable internal data class SeerrUser(val displayName: String = "")
@Serializable internal data class SeerrRequest(
    val id: Int = 0,
    val status: Int = 0,
    val type: String = "",
    val media: SeerrMedia = SeerrMedia(),
    val requestedBy: SeerrUser = SeerrUser(),
)
@Serializable internal data class SeerrRequestPage(val results: List<SeerrRequest> = emptyList())
@Serializable internal data class SeerrMeta(val title: String? = null, val name: String? = null, val posterPath: String? = null)

@Serializable internal data class SeerrIssue(
    val id: Int = 0,
    val issueType: Int = 0,
    val status: Int = 0,
    val media: SeerrMedia = SeerrMedia(),
    val createdBy: SeerrUser = SeerrUser(),
)
@Serializable internal data class SeerrIssuePage(val results: List<SeerrIssue> = emptyList())

@Serializable internal data class SeerrSearchResult(
    val id: Int = 0,
    val mediaType: String = "",
    val title: String? = null,        // movie
    val name: String? = null,         // tv
    val releaseDate: String? = null,  // movie
    val firstAirDate: String? = null, // tv
    val adult: Boolean = false,       // TMDB adult (porn) flag
)
@Serializable internal data class SeerrSearchPage(val results: List<SeerrSearchResult> = emptyList())

@Serializable internal data class SeerrGenreDto(val id: Int = 0, val name: String = "")

internal interface SeerrApi {
    @GET("api/v1/request/count") suspend fun counts(): SeerrCounts
    @GET("api/v1/genres/movie") suspend fun genresMovie(): List<SeerrGenreDto>
    @GET("api/v1/genres/tv") suspend fun genresTv(): List<SeerrGenreDto>
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
    @GET("api/v1/discover/movies") suspend fun discoverMovies(@Query("page") page: Int = 1, @Query("genre") genre: Int? = null): JsonObject
    @GET("api/v1/discover/tv") suspend fun discoverTv(@Query("page") page: Int = 1, @Query("genre") genre: Int? = null): JsonObject
    @GET("api/v1/discover/watchlist") suspend fun watchlist(@Query("page") page: Int = 1): JsonObject
    @GET("api/v1/issue/{id}") suspend fun issueDetail(@Path("id") id: Int): JsonObject
    @POST("api/v1/issue/{id}/comment") suspend fun addComment(@Path("id") id: Int, @Body body: JsonObject): Response<ResponseBody>
    @POST("api/v1/issue/{id}/{status}") suspend fun setIssueStatus(@Path("id") id: Int, @Path("status") status: String): Response<ResponseBody>
    @DELETE("api/v1/issue/{id}") suspend fun deleteIssue(@Path("id") id: Int): Response<ResponseBody>
}

internal fun seerrStatusText(status: Int) = when (status) {
    1 -> "pending"
    2 -> "approved"
    3 -> "declined"
    4 -> "failed"
    else -> "?"
}

internal fun seerrIssueType(type: Int) = when (type) {
    1 -> "video"
    2 -> "audio"
    3 -> "subtitle"
    else -> "other"
}

internal suspend fun SeerrApi.resolveTitle(media: SeerrMedia, type: String): String {
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
                val meta = runCatching { if (isTv) api.tv(r.media.tmdbId) else api.movie(r.media.tmdbId) }.getOrNull()
                val title = (if (isTv) meta?.name ?: meta?.title else meta?.title ?: meta?.name) ?: "#${r.media.tmdbId}"
                SeerrRequestItem(
                    id = r.id,
                    title = title,
                    subtitle = "${r.type} · ${r.requestedBy.displayName}",
                    status = seerrStatusText(r.status),
                    pending = r.status == 1,
                    posterUrl = meta?.posterPath?.let { "https://image.tmdb.org/t/p/w300$it" } ?: "",
                )
            }
        }.awaitAll()
    }
}

suspend fun seerrApprove(config: ServiceConfig, id: Int): String = destructive("approve Seerr request $id") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).approve(id)
            if (r.isSuccessful) "approved" else "error: HTTP ${r.code()}"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun seerrDecline(config: ServiceConfig, id: Int): String = destructive("decline Seerr request $id") {
    withContext(Dispatchers.IO) {
        try {
            val r = apiFor<SeerrApi>(config, apiKeyHeader(config)).decline(id)
            if (r.isSuccessful) "declined" else "error: HTTP ${r.code()}"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
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
                adult = r.adult,
            )
        }
}

suspend fun seerrCreateRequest(config: ServiceConfig, item: SeerrSearchItem): String = destructive("create a Seerr request") {
    withContext(Dispatchers.IO) {
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
}

internal fun seerrMediaStatusText(status: Int?) = when (status) {
    2 -> "pending"
    3 -> "processing"
    4 -> "partial"
    5 -> "available"
    else -> ""
}

internal fun parseDiscoverItems(page: JsonObject, defaultType: String?): List<SeerrDiscoverItem> {
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
                adult = jsBool(o, "adult") ?: false,
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

/** Genre list for [kind] = "movies" | "tv" (id + name), for the discover genre rows. */
suspend fun seerrGenres(config: ServiceConfig, kind: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val genres = if (kind == "tv") api.genresTv() else api.genresMovie()
    genres.filter { it.id > 0 && it.name.isNotBlank() }.map { it.id to it.name }
}

/** Discover [kind] = "movies" | "tv" filtered to one [genreId]. */
suspend fun seerrDiscoverGenre(config: ServiceConfig, kind: String, genreId: Int): List<SeerrDiscoverItem> = withContext(Dispatchers.IO) {
    val api = apiFor<SeerrApi>(config, apiKeyHeader(config))
    val (page, def) = if (kind == "tv") api.discoverTv(genre = genreId) to "tv" else api.discoverMovies(genre = genreId) to "movie"
    parseDiscoverItems(page, def)
}

/** The signed-in user's Plex watchlist (synced via Seerr). Items carry tmdbId + mediaType. */
suspend fun seerrWatchlist(config: ServiceConfig): List<SeerrDiscoverItem> = withContext(Dispatchers.IO) {
    val page = apiFor<SeerrApi>(config, apiKeyHeader(config)).watchlist()
    val results = page["results"] as? JsonArray ?: return@withContext emptyList()
    results.mapNotNull { it as? JsonObject }.mapNotNull { o ->
        val tmdb = jsInt(o, "tmdbId") ?: return@mapNotNull null
        val type = jsStr(o, "mediaType") ?: "movie"
        val poster = jsStr(o, "posterPath")
        SeerrDiscoverItem(
            tmdbId = tmdb,
            title = jsStr(o, "title") ?: "?",
            year = "",
            mediaType = type,
            posterUrl = if (!poster.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$poster" else "",
            status = "",
        )
    }
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
suspend fun seerrRequest(config: ServiceConfig, tmdbId: Int, mediaType: String, seasons: List<Int>?): String = destructive("create a Seerr request for tmdb $tmdbId") {
    withContext(Dispatchers.IO) {
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

suspend fun seerrAddComment(config: ServiceConfig, id: Int, message: String): String = destructive("comment on Seerr issue $id") {
    withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject { put("message", message) }
            okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).addComment(id, body), "commented")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun seerrSetIssueStatus(config: ServiceConfig, id: Int, resolved: Boolean): String = destructive("change the status of Seerr issue $id") {
    withContext(Dispatchers.IO) {
        try {
            okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).setIssueStatus(id, if (resolved) "resolved" else "open"), if (resolved) "resolved" else "reopened")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun seerrDeleteIssueById(config: ServiceConfig, id: Int): String = destructive("delete Seerr issue $id") {
    withContext(Dispatchers.IO) {
        try {
            okOr(apiFor<SeerrApi>(config, apiKeyHeader(config)).deleteIssue(id), "deleted")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

// ---- Servarr shared: Radarr/Sonarr/Lidarr missing + queue ----

internal suspend fun seerrSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
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
            year = d.year.toIntOrNull() ?: 0,
            inLibrary = d.status == "available",
        )
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

internal suspend fun seerrStatus(config: ServiceConfig): ServiceStatus {
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
