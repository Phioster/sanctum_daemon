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

private const val FAILURE_SUFFIX = " - Health Check Failure"
private const val RESTORED_SUFFIX = " - Health Check Restored"
private const val RESOLVED_PREFIX = "The following issue is now resolved: "

/**
 * Reads one of those messages, or null when it is not one.
 *
 * The restore repeats the issue behind a fixed prefix — stripping it is what makes the two halves
 * comparable. A restore that arrives without the prefix keeps its text as the issue rather than
 * being discarded: an unpaired restore is still worth showing.
 */
internal fun parseHealthEvent(title: String, text: String): HealthEvent? = when {
    title.endsWith(FAILURE_SUFFIX) ->
        HealthEvent(title.removeSuffix(FAILURE_SUFFIX), text.trim(), resolved = false)
    title.endsWith(RESTORED_SUFFIX) ->
        HealthEvent(title.removeSuffix(RESTORED_SUFFIX), text.removePrefix(RESOLVED_PREFIX).trim(), resolved = true)
    else -> null
}
