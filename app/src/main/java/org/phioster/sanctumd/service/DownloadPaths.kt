package org.phioster.sanctumd.service

/**
 * The name a downloaded file is written under.
 *
 * Both parts used to come from the server unfiltered — the item id from the library listing, the
 * extension from the media source's `Container` — and `File(dir, "$id.$container")` does not
 * normalise. A container of `../cacert.pem` therefore wrote *outside* `downloads/`, one level up
 * into the app's private directory, where mpv's trusted CA bundle and the credential store live.
 * A hostile or compromised media server had an arbitrary file write on one tap.
 *
 * Anything that is not a plain name is dropped rather than escaped: escaping invites the next
 * bypass, and nothing legitimate here needs a separator, a dot or a space.
 */
internal fun downloadFileName(itemId: String, container: String): String {
    val id = itemId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64).ifBlank { "item" }
    val ext = container.filter { it.isLetterOrDigit() }.take(8).lowercase().ifBlank { "bin" }
    return "$id.$ext"
}

/** The prefix [downloadFileName] writes for [itemId] — used to find that item's files again. */
internal fun downloadFilePrefix(itemId: String): String =
    downloadFileName(itemId, "").substringBeforeLast('.') + "."
