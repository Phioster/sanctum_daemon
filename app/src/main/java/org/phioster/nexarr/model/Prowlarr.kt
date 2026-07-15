package org.phioster.nexarr.model

/** An indexer in Prowlarr with merged stats/status. */
data class ProwlarrIndexerItem(
    val id: Int,
    val name: String,
    val protocol: String, // "usenet" or "torrent"
    val enable: Boolean,
    val priority: Int,
    val privacy: String,
    val queries: Int,
    val grabs: Int,
    val failRate: Int, // percent 0..100
    val failing: Boolean, // currently disabled by failures
)

/** A search result (release) from Prowlarr. */
data class ProwlarrRelease(
    val guid: String,
    val indexerId: Int,
    val indexer: String,
    val title: String,
    val sizeMb: Long,
    val protocol: String,
    val seeders: Int?, // torrent only
    val ageDays: Int,
    val categories: String, // joined category names from the response
)

/** A top-level search category filter. [id] 0 means "all". */
data class ProwlarrCategory(val id: Int, val name: String)
