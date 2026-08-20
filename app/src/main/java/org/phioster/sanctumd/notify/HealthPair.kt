package org.phioster.sanctumd.notify

/**
 * A health message from Radarr/Sonarr/Lidarr/Prowlarr, split into what it is about and whether it
 * announces a problem or its end.
 *
 * The *arr apps send both halves — they are configured with OnHealthIssue *and* OnHealthRestored —
 * so a problem that lasts seconds still produces two messages per service. Recognising that the
 * second one answers the first is what lets them be shown as one line.
 */
internal data class HealthEvent(
    val service: String,
    val issue: String,
    val resolved: Boolean,
)

internal fun parseHealthEvent(title: String, text: String): HealthEvent? = null
