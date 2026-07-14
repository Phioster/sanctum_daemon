package org.phioster.nexarr.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.phioster.nexarr.model.ServiceConfig
import org.phioster.nexarr.model.ServiceStatus
import org.phioster.nexarr.model.ServiceType
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Injects the service's auth header plus any custom headers (e.g. CF Access). */
private fun headerInterceptor(config: ServiceConfig) = Interceptor { chain ->
    val builder = chain.request().newBuilder()
    when (config.type) {
        ServiceType.JELLYFIN -> builder.header("X-Emby-Token", config.apiKey)
        ServiceType.RADARR -> builder.header("X-Api-Key", config.apiKey)
    }
    config.customHeaders.forEach { (name, value) ->
        if (name.isNotBlank() && value.isNotBlank()) builder.header(name, value)
    }
    chain.proceed(builder.build())
}

private fun retrofitFor(config: ServiceConfig): Retrofit {
    val client = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor(config))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

// ---- Jellyfin ----

@Serializable
private data class JfCounts(
    val MovieCount: Int = 0,
    val SeriesCount: Int = 0,
    val EpisodeCount: Int = 0,
    val SongCount: Int = 0,
)

@Serializable
private data class JfSession(val NowPlayingItem: JfNowPlaying? = null)

@Serializable
private data class JfNowPlaying(val Name: String? = null)

private interface JellyfinApi {
    @GET("Items/Counts") suspend fun counts(): JfCounts
    @GET("Sessions") suspend fun sessions(): List<JfSession>
}

// ---- Radarr ----

@Serializable
private data class RadarrMovie(val hasFile: Boolean = false, val monitored: Boolean = false)

@Serializable
private data class RadarrPage(val totalRecords: Int = 0)

private interface RadarrApi {
    @GET("api/v3/movie") suspend fun movies(): List<RadarrMovie>
    @GET("api/v3/queue") suspend fun queue(@Query("pageSize") pageSize: Int = 1): RadarrPage
    @GET("api/v3/wanted/missing") suspend fun missing(@Query("pageSize") pageSize: Int = 1): RadarrPage
}

/** Runs the appropriate status calls for a service and maps them to a card. */
suspend fun fetchStatus(config: ServiceConfig): ServiceStatus = withContext(Dispatchers.IO) {
    try {
        when (config.type) {
            ServiceType.JELLYFIN -> {
                val api = retrofitFor(config).create<JellyfinApi>()
                val counts = api.counts()
                val playing = api.sessions().count { it.NowPlayingItem != null }
                ServiceStatus(
                    ok = true,
                    stats = listOf(
                        "Movies" to counts.MovieCount.toString(),
                        "Series" to counts.SeriesCount.toString(),
                        "Playing" to playing.toString(),
                    ),
                )
            }
            ServiceType.RADARR -> {
                val api = retrofitFor(config).create<RadarrApi>()
                val movies = api.movies()
                val missing = api.missing().totalRecords
                val queue = api.queue().totalRecords
                ServiceStatus(
                    ok = true,
                    stats = listOf(
                        "Movies" to movies.size.toString(),
                        "Missing" to missing.toString(),
                        "Queue" to queue.toString(),
                    ),
                )
            }
        }
    } catch (t: Throwable) {
        ServiceStatus(ok = false, error = t.message ?: t.javaClass.simpleName)
    }
}
