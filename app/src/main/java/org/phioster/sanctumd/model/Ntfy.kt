package org.phioster.sanctumd.model

/** A message from a ntfy topic (live stream or history poll). */
data class NtfyMessage(
    val id: String,
    val time: Long, // unix seconds
    val topic: String,
    val title: String, // "" when the message has no usable title
    val text: String,
)
