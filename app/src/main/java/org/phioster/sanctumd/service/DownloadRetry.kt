package org.phioster.sanctumd.service

import org.phioster.sanctumd.model.DownloadEntry
import java.io.IOException

/** A server answer that will not change on retry (bad id, no access, gone). */
internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Network hiccups and 5xx/408/429 are worth another try; other HTTP errors are final. */
internal fun isRetryable(t: Throwable): Boolean = when (t) {
    is HttpStatusException -> t.code >= 500 || t.code == 408 || t.code == 429
    is IOException -> true
    else -> false
}

/** Wait before retry [attempt] (1-based): 5s, 15s, 30s, 60s, then 60s. */
internal fun retryDelayMs(attempt: Int): Long = when (attempt) {
    1 -> 5_000L
    2 -> 15_000L
    3 -> 30_000L
    else -> 60_000L
}

/** Consecutive failures without a single new byte before a download is marked failed. */
internal const val MAX_STALLED_ATTEMPTS = 6

/**
 * Entries left RUNNING while no download loop is active: the process was killed or the
 * service stopped mid-transfer. They go back into the queue and continue from the partial file.
 */
internal fun orphanedRunning(entries: Collection<DownloadEntry>): List<String> =
    entries.filter { it.state == DownloadEntry.STATE_RUNNING }.map { it.itemId }
