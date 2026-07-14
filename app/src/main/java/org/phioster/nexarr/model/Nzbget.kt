package org.phioster.nexarr.model

/** A single download in the NZBGet queue. */
data class NzbQueueItem(
    val id: Int,
    val name: String,
    val status: String,
    val sizeMb: Long,
    val remainingMb: Long,
) {
    val progress: Float
        get() = if (sizeMb > 0) ((sizeMb - remainingMb).toFloat() / sizeMb).coerceIn(0f, 1f) else 0f
}

/** A completed/failed entry from NZBGet history (optionally including hidden/DUP). */
data class NzbHistoryEntry(
    val id: Int,
    val name: String,
    val status: String,
    val sizeMb: Long,
)
