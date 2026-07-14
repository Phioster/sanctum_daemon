package org.phioster.nexarr.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Which kind of service a config points at. */
@Serializable
enum class ServiceType(val label: String, val accent: Long) {
    JELLYFIN("Jellyfin", 0xFF00A4DCL),
    RADARR("Radarr", 0xFFFFC230L),
    NZBGET("NZBGet", 0xFF43B02AL),
}

/**
 * A single configured service.
 *
 * Auth depends on [type]:
 *  - Radarr: [apiKey]
 *  - Jellyfin: [apiKey], or [username]/[password] when [useLogin] is true
 *  - NZBGet: [username]/[password] (HTTP Basic)
 *
 * [customHeaders] carries the Cloudflare Access service token (CF-Access-Client-Id
 * / -Secret) sent on every request when the service sits behind CF Access.
 */
@Serializable
data class ServiceConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: ServiceType,
    val label: String,
    val baseUrl: String,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val useLogin: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
) {
    /** Retrofit needs a base URL that ends with a slash. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

/** Uniform status shown on a dashboard card, regardless of service type. */
data class ServiceStatus(
    val ok: Boolean,
    val stats: List<Pair<String, String>> = emptyList(),
    val note: String? = null,
    val error: String? = null,
) {
    companion object {
        val Loading = ServiceStatus(ok = false, error = null)
    }
}
