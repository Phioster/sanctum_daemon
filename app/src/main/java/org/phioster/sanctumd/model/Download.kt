package org.phioster.sanctumd.model

import kotlinx.serialization.Serializable

/** One offline download: its source item, local files, and progress/state. Persisted in
 *  [org.phioster.sanctumd.data.DownloadStore]; the media file lives in the app's private storage. */
@Serializable
data class DownloadEntry(
    val itemId: String,
    val serverId: String,
    val name: String,
    val subtitle: String = "",
    val posterFile: String = "", // local cached poster path ("" = none)
    val filePath: String = "",   // local media file path (set once DONE)
    val sizeBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val runTimeTicks: Long = 0,
    val state: String = STATE_QUEUED,
    val error: String = "",
    val addedAt: Long = 0,
) {
    val progress: Float get() = if (sizeBytes > 0) (downloadedBytes.toFloat() / sizeBytes).coerceIn(0f, 1f) else 0f
    val done: Boolean get() = state == STATE_DONE

    companion object {
        const val STATE_QUEUED = "QUEUED"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_DONE = "DONE"
        const val STATE_FAILED = "FAILED"
    }
}
