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
import kotlinx.serialization.json.contentOrNull
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

private const val MB_CLIENT =
    "Client=\"Sanctumd\", Device=\"Android\", DeviceId=\"sanctumd\", Version=\"0.3.0\""

/** Login (no token yet). */
internal const val MB_AUTH = "MediaBrowser $MB_CLIENT"

/** Auth for an existing Jellyfin token. The X-Emby-Token header is a legacy method that server
 *  12 disables by default; only the MediaBrowser scheme and the ApiKey query parameter survive. */
internal fun jellyfinAuth(token: String) = mapOf("Authorization" to "MediaBrowser Token=\"$token\", $MB_CLIENT")

// One shared client so every per-call client below reuses the same dispatcher and
// connection pool (newBuilder() shares them) instead of spawning a pool per request.
internal val baseOkClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

/** [readTimeoutSeconds] overrides the shared 20 s read timeout for one endpoint. Only worth it
 *  where the server is known to be slow rather than broken, see [org.phioster.sanctumd.net.arrDisks]. */
internal fun okClient(
    config: ServiceConfig,
    authHeaders: Map<String, String>,
    readTimeoutSeconds: Long = 0,
): OkHttpClient =
    baseOkClient.newBuilder()
        .apply { if (readTimeoutSeconds > 0) readTimeout(readTimeoutSeconds, TimeUnit.SECONDS) }
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            authHeaders.forEach { (k, v) -> if (v.isNotBlank()) b.header(k, v) }
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) b.header(k, v) }
            chain.proceed(b.build())
        }
        .build()

internal inline fun <reified T> apiFor(
    config: ServiceConfig,
    authHeaders: Map<String, String>,
    readTimeoutSeconds: Long = 0,
): T =
    Retrofit.Builder()
        .baseUrl(config.normalizedBaseUrl)
        .client(okClient(config, authHeaders, readTimeoutSeconds))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

internal fun apiKeyHeader(config: ServiceConfig) = mapOf("X-Api-Key" to config.apiKey)
internal fun basicHeader(config: ServiceConfig) =
    mapOf("Authorization" to Credentials.basic(config.username, config.password))

@Serializable internal data class CommandReq(val name: String)

// ---- Jellyfin ----

/**
 * A string field, or null when it is absent **or JSON null**.
 *
 * `contentOrNull` rather than `content`: `JsonNull` is itself a `JsonPrimitive`, and its
 * `content` is the literal text `"null"`. Reading `.content` therefore turned every JSON null
 * into a four-character string that is not blank, so the usual `isNotBlank()` guards passed it
 * through and it reached the UI.
 */
internal fun jsStr(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
internal fun jsInt(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.intOrNull
internal fun jsLong(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toLongOrNull()
internal fun jsBool(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.content?.toBoolean()

/** Ratings that mark porn / XXX only. Mainstream adult ratings (18, FSK 18, NC-17, R,
 *  TV-MA, and Australia's R18+ which covers violent/horror films) are intentionally NOT
 *  blocked, only actual pornography. Australia's porn rating is X18+, not R18+. */
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
