package org.phioster.nexarr.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Which kind of service a config points at. */
@Serializable
enum class ServiceType(val label: String, val accent: Long) {
    JELLYFIN("Jellyfin", 0xFF00A4DCL),
    RADARR("Radarr", 0xFFFFC230L),
    SONARR("Sonarr", 0xFF35C5F4L),
    LIDARR("Lidarr", 0xFF159552L),
    PROWLARR("Prowlarr", 0xFFE66000L),
    NZBGET("NZBGet", 0xFF43B02AL),
    SEERR("Seerr", 0xFF818CF8L);

    /** Services that authenticate with a Servarr/Overseerr-style X-Api-Key header. */
    val usesApiKeyHeader: Boolean
        get() = this == RADARR || this == SONARR || this == LIDARR || this == PROWLARR || this == SEERR
}

/**
 * A single configured service.
 *
 * Auth depends on [type]:
 *  - Radarr/Sonarr/Lidarr/Prowlarr/Seerr: [apiKey] (X-Api-Key)
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
