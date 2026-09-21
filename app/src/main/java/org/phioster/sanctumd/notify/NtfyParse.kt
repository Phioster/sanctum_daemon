package org.phioster.sanctumd.notify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.phioster.sanctumd.model.NtfyMessage

private val json = Json { ignoreUnknownKeys = true }

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
        title = str("title") ?: s("title") ?: s("subject") ?: s("eventType") ?: ""
        text = s("message") ?: s("body") ?: nestedTitle ?: body.take(180)
    } else {
        title = str("title") ?: ""
        text = body.take(180)
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
