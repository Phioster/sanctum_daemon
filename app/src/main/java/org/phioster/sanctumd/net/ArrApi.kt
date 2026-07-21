package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.sanctumd.model.ArrAlbum
import org.phioster.sanctumd.model.ArrTrack
import org.phioster.sanctumd.model.ArrDetail
import org.phioster.sanctumd.model.ArrEpisode
import org.phioster.sanctumd.model.ArrHistoryItem
import org.phioster.sanctumd.model.ArrImportItem
import org.phioster.sanctumd.model.ArrLibraryItem
import org.phioster.sanctumd.model.ArrLookupItem
import org.phioster.sanctumd.model.ArrMissingItem
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.ArrCalendarItem
import org.phioster.sanctumd.model.ArrRelease
import org.phioster.sanctumd.model.ArrSystemInfo
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.SearchResult
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

@Serializable internal data class RadarrMovie(val hasFile: Boolean = false, val monitored: Boolean = false)
@Serializable internal data class RadarrPage(val totalRecords: Int = 0)

internal interface RadarrApi {
    @GET("api/v3/movie") suspend fun movies(): List<RadarrMovie>
    @GET("api/v3/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v3/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v3/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Sonarr (api/v3) ----

@Serializable internal data class SonarrSeries(val monitored: Boolean = false)

internal interface SonarrApi {
    @GET("api/v3/series") suspend fun series(): List<SonarrSeries>
    @GET("api/v3/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v3/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v3/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Lidarr (api/v1) ----

@Serializable internal data class LidarrArtist(val monitored: Boolean = false)

internal interface LidarrApi {
    @GET("api/v1/artist") suspend fun artists(): List<LidarrArtist>
    @GET("api/v1/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v1/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @POST("api/v1/command") suspend fun command(@Body body: CommandReq): Response<ResponseBody>
}

// ---- Prowlarr (api/v1) ----

@Serializable internal data class ArrRef(val title: String = "")
@Serializable internal data class ArrArtistRef(val artistName: String = "")
@Serializable internal data class ArrMissingRecord(
    val id: Int = 0,
    val title: String = "",
    val year: Int = 0,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val series: ArrRef? = null,
    val artist: ArrArtistRef? = null,
)
@Serializable internal data class ArrMissingPage(val records: List<ArrMissingRecord> = emptyList())

@Serializable internal data class ArrQueueRecord(
    val id: Int = 0,
    val title: String = "",
    val status: String = "",
    val size: Double = 0.0,
    val sizeleft: Double = 0.0,
)
@Serializable internal data class ArrQueuePage(val records: List<ArrQueueRecord> = emptyList())

@Serializable internal data class ArrStats(val sizeOnDisk: Long = 0)
@Serializable internal data class ArrLibraryRecord(
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

@Serializable internal data class ArrProfileRecord(val id: Int = 0, val name: String = "")
@Serializable internal data class ArrRootFolderRecord(val path: String = "")

@Serializable internal data class ArrEpisodeRecord(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String = "",
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val airDate: String? = null,
)

@Serializable internal data class ArrQualityRef(val quality: ArrQualityName = ArrQualityName())
@Serializable internal data class ArrQualityName(val name: String = "")
@Serializable internal data class ArrCustomFormatRef(val name: String = "")
@Serializable internal data class ArrReleaseRecord(
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

@Serializable internal data class ArrDiskRecord(
    val path: String = "",
    val freeSpace: Long = 0,
    val totalSpace: Long = 0,
)
@Serializable internal data class ArrSystemStatusRec(val version: String = "")
@Serializable internal data class ArrHealthRecord(val type: String = "", val message: String = "")

@Serializable internal data class ArrGrabReq(val guid: String, val indexerId: Int)

@Serializable internal data class ArrHistoryRec(
    val eventType: String = "",
    val date: String = "",
    val sourceTitle: String = "",
    val quality: ArrQualityRef = ArrQualityRef(),
)
@Serializable internal data class ArrHistoryPage(val records: List<ArrHistoryRec> = emptyList())

internal interface ArrApi {
    @GET suspend fun missing(@Url url: String): ArrMissingPage
    @GET suspend fun queue(@Url url: String): ArrQueuePage
    @GET suspend fun library(@Url url: String): List<ArrLibraryRecord>
    @GET suspend fun lookup(@Url url: String, @Query("term") term: String): List<JsonObject>
    @GET suspend fun profiles(@Url url: String): List<ArrProfileRecord>
    @GET suspend fun rootFolders(@Url url: String): List<ArrRootFolderRecord>
    @POST suspend fun command(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
    @PUT suspend fun putUrl(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
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

internal fun arrBase(type: ServiceType) = if (type == ServiceType.LIDARR) "api/v1" else "api/v3"

internal fun arrItemPath(type: ServiceType) = when (type) {
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

internal suspend fun arrSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
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
            year = year,
            inLibrary = libId > 0,
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
): String = destructive("add an item to ${config.type.label}") {
    withContext(Dispatchers.IO) {
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

suspend fun arrCalendar(config: ServiceConfig): List<ArrCalendarItem> {
    val now = java.time.Instant.now()
    return arrCalendarRange(config, now, now.plus(java.time.Duration.ofDays(30))).take(30)
}

/** arr calendar for an arbitrary date range (used by the unified month calendar). */
suspend fun arrCalendarRange(config: ServiceConfig, start: java.time.Instant, end: java.time.Instant): List<ArrCalendarItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
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
                val sid = jsInt(o, "seriesId") ?: series?.let { jsInt(it, "id") } ?: 0
                ArrCalendarItem(show, "S%02dE%02d%s".format(s, e, if (ep.isNotBlank()) " · $ep" else ""), date.take(10), hasFile, sid)
            }
            ServiceType.LIDARR -> {
                val artistObj = (o["artist"] as? JsonObject)
                val artist = artistObj?.let { jsStr(it, "artistName") } ?: ""
                val album = jsStr(o, "title") ?: "?"
                val date = jsStr(o, "releaseDate") ?: ""
                val aid = jsInt(o, "artistId") ?: artistObj?.let { jsInt(it, "id") } ?: 0
                ArrCalendarItem(album, artist, date.take(10), hasFile, aid)
            }
            else -> {
                val title = jsStr(o, "title") ?: "?"
                val date = jsStr(o, "digitalRelease") ?: jsStr(o, "physicalRelease") ?: jsStr(o, "inCinemas") ?: ""
                val year = jsInt(o, "year")?.takeIf { it > 0 }?.toString() ?: ""
                ArrCalendarItem(title, year, date.take(10), hasFile, jsInt(o, "id") ?: 0)
            }
        }
    }.filter { it.date.isNotBlank() }.sortedBy { it.date }
}

suspend fun arrQueue(config: ServiceConfig): List<ArrQueueItem> = withContext(Dispatchers.IO) {
    val base = arrBase(config.type)
    apiFor<ArrApi>(config, apiKeyHeader(config)).queue("$base/queue?pageSize=100").records.map { r ->
        val prog = if (r.size > 0) ((r.size - r.sizeleft) / r.size).toFloat().coerceIn(0f, 1f) else 0f
        ArrQueueItem(r.id, r.title, r.status, prog)
    }
}

suspend fun arrSearchItem(config: ServiceConfig, id: Int): String = destructive("search item $id on ${config.type.label}") {
    withContext(Dispatchers.IO) {
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
}

suspend fun arrQueueRemove(config: ServiceConfig, id: Int): String = destructive("remove queue item $id on ${config.type.label}") {
    withContext(Dispatchers.IO) {
        try {
            val base = arrBase(config.type)
            val r = apiFor<ArrApi>(config, apiKeyHeader(config))
                .deleteQueue("$base/queue/$id?removeFromClient=true&blocklist=false")
            if (r.isSuccessful) "removed" else "error: HTTP ${r.code()}"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
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
suspend fun arrLibrarySearch(config: ServiceConfig, id: Int): String = destructive("search library item $id on ${config.type.label}") {
    withContext(Dispatchers.IO) {
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
}

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

/** Sonarr: toggle monitoring for a single episode. */
suspend fun arrSetEpisodeMonitored(config: ServiceConfig, episodeId: Int, monitored: Boolean): String = withContext(Dispatchers.IO) {
    try {
        val base = arrBase(config.type)
        val body = buildJsonObject {
            putJsonArray("episodeIds") { add(episodeId) }
            put("monitored", monitored)
        }
        okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).putUrl("$base/episode/monitor", body), if (monitored) "monitoring" else "unmonitored")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
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
suspend fun arrManualImportExecute(config: ServiceConfig, rawItems: List<String>): String = destructive("manually import ${rawItems.size} file(s) on ${config.type.label}") {
    withContext(Dispatchers.IO) {
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
}

suspend fun arrGrab(config: ServiceConfig, guid: String, indexerId: Int): String = destructive("grab a release on ${config.type.label}") {
    withContext(Dispatchers.IO) {
        try {
            val base = arrBase(config.type)
            okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).downloadRelease("$base/release", ArrGrabReq(guid, indexerId)), "grabbed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun arrDelete(config: ServiceConfig, id: Int, deleteFiles: Boolean): String = destructive("delete ${config.type.label} item $id (files: $deleteFiles)") {
    withContext(Dispatchers.IO) {
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
suspend fun arrRssSync(config: ServiceConfig): String = destructive("RSS sync on ${config.type.label}") {
    withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject { put("name", "RssSync") }
            okOr(apiFor<ArrApi>(config, apiKeyHeader(config)).command("${arrBase(config.type)}/command", body), "RSS sync started")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun arrSearchAll(config: ServiceConfig, cutoff: Boolean): String = destructive("search all missing on ${config.type.label}") {
    withContext(Dispatchers.IO) {
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
}

// ---- NZBGet (JSON-RPC over HTTP + Basic auth) ----

/** Radarr/Sonarr/Lidarr: trigger a search for all missing monitored items. */
suspend fun runSearchMissing(config: ServiceConfig): String = destructive("search missing on ${config.type.label}") {
    withContext(Dispatchers.IO) {
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
}

/** Pushes a Prowlarr release to a Radarr/Sonarr instance via release/push. */
suspend fun arrPushRelease(arrConfig: ServiceConfig, release: ProwlarrRelease): String = destructive("push a release to ${arrConfig.type.label}") {
    withContext(Dispatchers.IO) {
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
}
