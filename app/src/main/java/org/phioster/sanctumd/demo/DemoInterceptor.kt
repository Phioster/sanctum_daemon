package org.phioster.sanctumd.demo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** One request as a demo service sees it: [path] without the service prefix, no leading slash. */
internal class DemoRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String,
) {
    fun q(key: String): String? = query.entries.firstOrNull { it.key.equals(key, true) }?.value
    val isRead get() = method == "GET"
}

/** What a demo service answers: JSON, plain text, or "fetch this public URL instead" for pictures. */
internal sealed class DemoAnswer {
    class Json(val body: JsonElement, val code: Int = 200) : DemoAnswer()
    class Text(val body: String, val code: Int = 200, val type: String = "text/plain") : DemoAnswer()
    class Fetch(val url: String) : DemoAnswer()
    object NotFound : DemoAnswer()
}

internal fun ok(): DemoAnswer = DemoAnswer.Json(buildJsonObject { })
internal fun json(e: JsonElement): DemoAnswer = DemoAnswer.Json(e)

/**
 * Answers every request to [DemoMode.HOST] from [DemoWorld]. Sits first in the shared client, so
 * Retrofit, Coil and the ntfy reader all go through it. Writes are accepted and change nothing;
 * a read the demo has no data for is a 404, which the screens already handle as "not there".
 */
internal object DemoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.url.host != DemoMode.HOST) return chain.proceed(req)
        val segs = req.url.pathSegments.filter { it.isNotEmpty() }
        val service = segs.firstOrNull().orEmpty()
        val body = runCatching {
            val buf = okio.Buffer()
            req.body?.writeTo(buf)
            buf.readUtf8()
        }.getOrDefault("")
        val dr = DemoRequest(
            method = req.method,
            path = segs.drop(1).joinToString("/"),
            query = req.url.queryParameterNames.associateWith { req.url.queryParameter(it).orEmpty() },
            body = body,
        )
        val answer = runCatching {
            when (service) {
                "jellyfin" -> DemoJellyfin.handle(dr)
                "radarr", "sonarr", "lidarr" -> DemoArr.handle(service, dr)
                "seerr" -> DemoSeerr.handle(dr)
                "nzbget" -> DemoNzbget.handle(dr)
                "prowlarr" -> DemoProwlarr.handle(dr)
                "ntfy" -> DemoNtfy.handle(dr)
                else -> DemoAnswer.NotFound
            }
        }.getOrElse { DemoAnswer.Text("demo: ${it.javaClass.simpleName}: ${it.message}", 500) }

        if (answer is DemoAnswer.Fetch) {
            // A picture: the public original (TMDb, Apple), fetched for real.
            return chain.proceed(req.newBuilder().url(answer.url).removeHeader("Authorization").removeHeader("X-Api-Key").build())
        }
        if (!dr.isRead && answer is DemoAnswer.NotFound) return respond(req, 200, "{}", "application/json")
        return when (answer) {
            is DemoAnswer.Json -> respond(req, answer.code, answer.body.toString(), "application/json")
            is DemoAnswer.Text -> respond(req, answer.code, answer.body, answer.type)
            else -> respond(req, 404, "{\"message\":\"not in the demo\"}", "application/json")
        }
    }

    private fun respond(req: okhttp3.Request, code: Int, body: String, type: String): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Demo")
            .body(body.toResponseBody(type.toMediaType()))
            .build()
}

/** Small JSON helpers shared by the demo services. */
internal fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> put(k, toJson(v)) }
}

internal fun toJson(v: Any?): JsonElement = when (v) {
    null -> kotlinx.serialization.json.JsonNull
    is JsonElement -> v
    is String -> kotlinx.serialization.json.JsonPrimitive(v)
    is Number -> kotlinx.serialization.json.JsonPrimitive(v)
    is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
    is Map<*, *> -> kotlinx.serialization.json.JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
    is Iterable<*> -> kotlinx.serialization.json.JsonArray(v.map { toJson(it) })
    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
}

internal fun arr(items: Iterable<Any?>): JsonElement = kotlinx.serialization.json.JsonArray(items.map { toJson(it) })
