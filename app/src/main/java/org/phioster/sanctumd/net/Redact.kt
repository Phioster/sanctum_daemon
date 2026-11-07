package org.phioster.sanctumd.net

/**
 * Error texts come from servers and exceptions, and some of them quote whole URLs or JSON:
 * Prowlarr's "Test all" once showed an indexer's `…&apikey=<full key>` on screen. Every text that
 * reaches the user goes through here first.
 */
private val SECRET_PARAM = Regex(
    "(?i)\\b(api[_-]?key|x-api-key|access[_-]?token|token|passkey|password|passwd|pass|secret)(\"?\\s*[=:]\\s*\"?)([^&\\s\"',;)\\]}]+)",
)
private val BEARER = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+")
private val URL_USERINFO = Regex("://[^/\\s:@]+:[^/\\s@]+@")

internal fun redactSecrets(text: String): String = text
    .replace(URL_USERINFO, "://•••@")
    .replace(BEARER) { "${it.groupValues[1]} •••" }
    .replace(SECRET_PARAM) { "${it.groupValues[1]}${it.groupValues[2]}•••" }

/** The user-facing text of a failure, with any credentials it quotes blanked out. */
internal fun errorText(t: Throwable): String = redactSecrets(t.message ?: t.javaClass.simpleName)
