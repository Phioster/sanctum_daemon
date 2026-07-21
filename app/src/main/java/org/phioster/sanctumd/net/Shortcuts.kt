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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import retrofit2.http.POST

/** Fires a one-tap shortcut; returns "HTTP 200 · <response head>" or an error line. */
suspend fun runHttpShortcut(config: ServiceConfig, sc: org.phioster.sanctumd.model.HttpShortcut): String = destructive("run HTTP shortcut ${sc.name}") {
    withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url(sc.url)
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) b.header(k, v) }
            if (sc.method.equals("POST", ignoreCase = true)) {
                val mediaType = (if (sc.body.trim().startsWith("{")) "application/json" else "text/plain").toMediaType()
                b.post(sc.body.toRequestBody(mediaType))
            }
            baseOkClient.newCall(b.build()).execute().use { r ->
                val head = r.body?.string().orEmpty().take(120).replace('\n', ' ').trim()
                if (r.isSuccessful) "HTTP ${r.code}${if (head.isNotBlank()) " · $head" else ""}" else "error: HTTP ${r.code}"
            }
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

internal fun shortcutsStatus(config: ServiceConfig): ServiceStatus = ServiceStatus(
    ok = true, // nothing to health-check: targets may be WOL bridges that are offline by design
    stats = listOf("${config.shortcuts.size}" to "SHORTCUTS"),
    note = config.shortcuts.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
)
