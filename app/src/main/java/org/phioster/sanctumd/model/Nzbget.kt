package org.phioster.sanctumd.model

/** A single download in the NZBGet queue. */
data class NzbQueueItem(
    val id: Int,
    val name: String,
    val status: String,
    val sizeMb: Long,
    val remainingMb: Long,
    val category: String = "",
    /** NZBGet's priority: -100 very low … 0 normal … 100 very high, 900 force. */
    val priority: Int = 0,
    /** Per mille, 1000 = nothing missing. */
    val health: Int = 1000,
    /** Days since the release was posted; -1 unknown. */
    val ageDays: Int = -1,
    /** Seconds until done at the current rate, counting the items ahead; null while it can't be known. */
    val etaSec: Long? = null,
    /** What post-processing is doing right now ("Unpacking…"), blank otherwise. */
    val postInfo: String = "",
    /** Post-processing progress, 0–1000. */
    val postProgress: Int = 0,
) {
    val progress: Float
        get() = if (sizeMb > 0) ((sizeMb - remainingMb).toFloat() / sizeMb).coerceIn(0f, 1f) else 0f
}

/** A completed/failed entry from NZBGet history (optionally including hidden/DUP). */
/** The queue-wide state the header shows. */
data class NzbPauseState(
    val paused: Boolean,
    /** Unix seconds when a timed pause ends; 0 without one. */
    val resumeAt: Long,
    val postPaused: Boolean,
    /** Bytes per second right now; 0 when paused or idle. */
    val rateBytesPerSec: Long = 0,
)

/** One line of NZBGet's message log. */
data class NzbLogEntry(
    /** INFO, WARNING, ERROR, DETAIL or DEBUG. */
    val kind: String,
    val time: String,
    val text: String,
)

data class NzbHistoryEntry(
    val id: Int,
    val name: String,
    val status: String,
    val sizeMb: Long,
    /** Why it did not succeed, in a few words; blank for a success. */
    val reason: String = "",
)
