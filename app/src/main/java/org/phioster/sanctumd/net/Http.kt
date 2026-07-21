package org.phioster.sanctumd.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import okhttp3.ResponseBody
import retrofit2.Response
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

internal val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true // JSON-RPC needs the default "method"/"id" fields in the body
}

internal const val MB_AUTH =
    "MediaBrowser Client=\"Sanctumd\", Device=\"Android\", DeviceId=\"sanctumd\", Version=\"0.3.0\""

// One shared client so every per-call client below reuses the same dispatcher and
// connection pool (newBuilder() shares them) instead of spawning a pool per request.
internal val baseOkClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

internal fun okClient(config: ServiceConfig, authHeaders: Map<String, String>): OkHttpClient =
    baseOkClient.newBuilder()
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            authHeaders.forEach { (k, v) -> if (v.isNotBlank()) b.header(k, v) }
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) b.header(k, v) }
            chain.proceed(b.build())
        }
        .build()

internal inline fun <reified T> apiFor(config: ServiceConfig, authHeaders: Map<String, String>): T =
    Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl)
        .client(okClient(config, authHeaders))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

internal fun apiKeyHeader(config: ServiceConfig) = mapOf("X-Api-Key" to config.apiKey)
internal fun basicHeader(config: ServiceConfig) =
    mapOf("Authorization" to Credentials.basic(config.username, config.password))

@Serializable private data class CommandReq(val name: String)

// ---- Jellyfin ----

internal fun jsStr(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content
internal fun jsInt(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.intOrNull
internal fun jsLong(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toLongOrNull()
internal fun jsBool(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toBoolean()

/** Ratings that mark porn / XXX only. Mainstream adult ratings (18, FSK 18, NC-17, R,
 *  TV-MA, and Australia's R18+ which covers violent/horror films) are intentionally NOT
 *  blocked — only actual pornography. Australia's porn rating is X18+, not R18+. */
internal val ADULT_RATINGS = setOf("XXX", "X", "X18+", "ADULT", "PORN")
fun isAdultRating(rating: String?): Boolean =
    rating != null && rating.trim().uppercase() in ADULT_RATINGS

/** ISO UTC timestamp -> compact "MM-dd HH:mm", or "" when absent. */
internal fun formatJellyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val s = iso.take(16) // "2026-07-15T14:03"
    return if (s.length >= 16) s.substring(5).replace('T', ' ') else s.replace('T', ' ')
}

internal fun humanSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

internal fun okOr(resp: Response<ResponseBody>, success: String): String =
    if (resp.isSuccessful) success else "error: HTTP ${resp.code()}"
