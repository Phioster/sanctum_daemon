package org.phioster.nexarr.model

/** A single hit from the cross-service global search. */
data class SearchResult(
    val serviceId: String,
    val serviceLabel: String,
    val serviceType: ServiceType,
    val title: String,
    val subtitle: String, // year + status, e.g. "2021 · in library"
    val posterUrl: String,
)
