package org.phioster.sanctumd.model

/** A message from a ntfy topic (live stream or history poll). */
data class NtfyMessage(
    val id: String,
    val time: Long, // unix seconds
    val topic: String,
    val title: String, // "" when the message has no usable title
    val text: String,
    /** Optional target the publisher attached; see jellyfinItemIdFromClick. */
    val click: String = "",
    /** ntfy's 1 (min) to 5 (max); 3 when the publisher set none. */
    val priority: Int = 3,
    /** Buttons the publisher attached, at most three of them shown. */
    val actions: List<NtfyAction> = emptyList(),
    /** An attached image (e.g. a poster), else the message icon; blank when neither. */
    val imageUrl: String = "",
    val tags: List<String> = emptyList(),
    /** The publisher asked for Markdown ("Markdown: yes"). */
    val markdown: Boolean = false,
)

/** One ntfy action button: "view" opens [url], "http" sends a request to it. */
data class NtfyAction(
    val kind: String,
    val label: String,
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    /** Dismiss the notification once the action ran. */
    val clear: Boolean = false,
)
