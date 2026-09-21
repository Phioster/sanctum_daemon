package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import okhttp3.Request
import org.phioster.sanctumd.model.NtfyMessage
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus

internal fun ntfyRequest(config: ServiceConfig, url: String): Request =
    Request.Builder().url(url).apply {
        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
        config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) header(k, v) }
    }.build()

/** For an unprotected ntfy topic the (random) topic name is effectively the access secret, so
 *  never render it in full on a status card or homescreen widget, reveal only a short prefix and
 *  hide the length. Full topic stays visible only in the service config the user manages. */
internal fun maskTopic(t: String): String = if (t.length <= 4) "•".repeat(t.length) else "${t.take(4)}••••••"

internal suspend fun ntfyStatus(config: ServiceConfig): ServiceStatus {
    val resp = baseOkClient.newCall(ntfyRequest(config, "${config.normalizedBaseUrl}v1/health")).execute()
    resp.use { if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}") }
    return ServiceStatus(
        ok = true,
        stats = listOf("${config.topics.size}" to "TOPICS"),
        note = config.topics.joinToString(", ") { maskTopic(it) }.takeIf { it.isNotBlank() },
    )
}

/** Cached messages of one topic, newest first (ntfy keeps ~12 h by default, more if configured). */
suspend fun ntfyHistory(config: ServiceConfig, topic: String, since: String = "48h"): List<NtfyMessage> = withContext(Dispatchers.IO) {
    val url = "${config.normalizedBaseUrl}$topic/json?poll=1&since=$since"
    val resp = baseOkClient.newCall(ntfyRequest(config, url)).execute()
    resp.use { r ->
        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
        val body = r.body?.string().orEmpty()
        body.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { org.phioster.sanctumd.notify.parseNtfyLine(it) }
            .sortedByDescending { it.time }
            .toList()
    }
}

// ---- HTTP shortcuts ----
