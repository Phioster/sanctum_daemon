package org.phioster.sanctumd.notify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.phioster.sanctumd.model.NtfyMessage

private val json = Json { ignoreUnknownKeys = true }

private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013",
    "laquo" to "\u00AB", "raquo" to "\u00BB", "ldquo" to "\u201C", "rdquo" to "\u201D",
    "auml" to "\u00E4", "ouml" to "\u00F6", "uuml" to "\u00FC",
    "Auml" to "\u00C4", "Ouml" to "\u00D6", "Uuml" to "\u00DC", "szlig" to "\u00DF",
)

private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});")

/**
 * Turns HTML entities in a webhook's text back into characters.
 *
 * Jellyfin's webhook templates HTML-escape the values they interpolate, so a title with a
 * non-breaking space arrives as `Lion Fist -&#160;Kampf der Champions` and is shown verbatim in
 * the notification. Nothing here renders HTML, so the escaping has no purpose past this point.
 *
 * A non-breaking space is folded to an ordinary one on purpose: it is indistinguishable in a
 * notification and only causes odd line breaks in a narrow one. Anything not recognised is left
 * exactly as it came in, so a bare `&` or a `&nosuch;` survives instead of being eaten.
 */
internal fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    return ENTITY.replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.let(::codePoint) ?: m.value
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let(::codePoint) ?: m.value
            else -> NAMED[body] ?: m.value
        }
    }
}

private fun codePoint(cp: Int): String? =
    if (cp in 1..0x10FFFF && !(cp in 0xD800..0xDFFF)) {
        val c = String(Character.toChars(cp))
        if (c == "\u00A0") " " else c
    } else {
        null
    }

/**
 * Parses one line of a ntfy `/json` stream into a message, null for
 * open/keepalive/poll_request events or unparseable lines. The message body may
 * itself be JSON that a service (Radarr/Overseerr/…) posted via webhook, in which
 * case title/text are pulled out of the payload.
 */
fun parseNtfyLine(line: String): NtfyMessage? {
    val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
    fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
    if (str("event") != "message") return null
    val body = str("message") ?: return null
    val inner = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    val title: String
    val text: String
    if (inner != null) {
        fun s(key: String) = (inner[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val nested = (inner["movie"] as? JsonObject) ?: (inner["series"] as? JsonObject) ?: (inner["media"] as? JsonObject)
        val nestedTitle = nested?.let { (it["title"] as? JsonPrimitive)?.contentOrNull }
        title = decodeEntities(str("title") ?: s("title") ?: s("subject") ?: s("eventType") ?: "")
        text = decodeEntities(s("message") ?: s("body") ?: nestedTitle ?: body).take(180)
    } else {
        title = decodeEntities(str("title") ?: "")
        text = decodeEntities(body).take(180)
    }
    return NtfyMessage(
        id = str("id").orEmpty(),
        time = str("time")?.toLongOrNull() ?: 0L,
        topic = str("topic").orEmpty(),
        title = title,
        text = text,
        click = str("click").orEmpty(),
    )
}

/**
 * The Jellyfin item id a notification points at, or null when it points at nothing useful.
 *
 * ntfy carries an optional `click` address per message. The Jellyfin webhook templates put
 * `sanctumd://item/<ItemId>` there, which is the only exact link available, the visible text of
 * those messages is prose ("Jiggi schaut … · Android TV") with no id in it, and guessing the
 * title back out of that sentence is the title-matching that fails on German release names.
 */
fun jellyfinItemIdFromClick(click: String?): String? {
    val prefix = "sanctumd://item/"
    val id = click?.trim()?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.substringBefore('?')
    return id?.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
}
