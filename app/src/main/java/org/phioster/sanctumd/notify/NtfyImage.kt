package org.phioster.sanctumd.notify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Its own short timeouts: a slow image must not hold up the notification for long. */
private val imageClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

/** Larger files are skipped rather than loaded into memory for a thumbnail. */
private const val MAX_BYTES = 6L * 1024 * 1024

/**
 * Whether [url] lives on the ntfy server itself. Only then does the request carry the token;
 * a URL in a message can point anywhere, and the token must not go along there.
 */
internal fun sameServer(url: String, server: String): Boolean {
    val a = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val b = runCatching { java.net.URI(server) }.getOrNull() ?: return false
    return a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && a.port == b.port
}

/** Largest power of two that brings the longer side down to about [target] pixels. */
internal fun sampleSizeFor(width: Int, height: Int, target: Int = 1024): Int {
    var s = 1
    while (maxOf(width, height) / (s * 2) >= target) s *= 2
    return s
}

/** The picture of a message, scaled for a notification; null when it can't be had. */
suspend fun fetchNtfyImage(url: String, server: String, token: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val req = Request.Builder().url(url).apply {
            if (token.isNotBlank() && sameServer(url, server)) header("Authorization", "Bearer $token")
        }.build()
        imageClient.newCall(req).execute().use { r ->
            val body = r.body ?: return@use null
            if (!r.isSuccessful || body.contentLength() > MAX_BYTES) return@use null
            val bytes = body.source().let { src ->
                src.request(MAX_BYTES + 1)
                if (src.buffer.size > MAX_BYTES) return@use null
                src.buffer.readByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@use null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }.getOrNull()
}
