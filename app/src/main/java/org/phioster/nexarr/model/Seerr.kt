package org.phioster.nexarr.model

/** A media request in Seerr (Overseerr/Jellyseerr). */
data class SeerrRequestItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val status: String,
    val pending: Boolean,
)
