package org.phioster.sanctumd.demo

/** Tags, lists, clients, proxies and renames for the made-up *arrs, answered before the main router. */
internal object DemoArrTools {
    private val tagLabels = listOf("4k", "anime", "kids", "german-dub", "family")

    private fun tagDetail(kind: String) = arr(tagLabels.mapIndexed { i, label ->
        val itemKey = when (kind) { "sonarr" -> "seriesIds"; "lidarr" -> "artistIds"; "prowlarr" -> "indexerIds"; else -> "movieIds" }
        obj("id" to i + 1, "label" to label, itemKey to (1..(5 - i)).toList(), "downloadClientIds" to if (i == 0) listOf(1) else emptyList<Int>(),
            "indexerProxyIds" to if (kind == "prowlarr" && i == 0) listOf(1) else emptyList<Int>())
    })

    private val importLists = listOf(
        obj("id" to 1, "name" to "Trakt Trending", "implementationName" to "Trakt Popular List", "enabled" to true, "enableAutomaticAdd" to true, "enableAuto" to true, "minRefreshInterval" to "12:00:00"),
        obj("id" to 2, "name" to "Mara's Watchlist", "implementationName" to "Plex Watchlist", "enabled" to true, "enableAutomaticAdd" to true, "enableAuto" to true, "minRefreshInterval" to "06:00:00"),
        obj("id" to 3, "name" to "IMDb Top 250", "implementationName" to "IMDb Lists", "enabled" to false, "enableAutomaticAdd" to false, "enableAuto" to false, "minRefreshInterval" to "1.00:00:00"),
    )

    private val clients = listOf(
        obj("id" to 1, "name" to "NZBGet", "implementationName" to "NZBGet", "enable" to true, "protocol" to "usenet", "priority" to 1),
        obj("id" to 2, "name" to "NZBGet (backup)", "implementationName" to "NZBGet", "enable" to false, "protocol" to "usenet", "priority" to 50),
    )

    private val proxies = listOf(
        obj("id" to 1, "name" to "FlareSolverr", "implementationName" to "FlareSolverr", "tags" to listOf(1)),
        obj("id" to 2, "name" to "Socks5 VPN", "implementationName" to "Socks5", "tags" to emptyList<Int>()),
    )

    private fun rename(kind: String, id: Int): DemoAnswer {
        val list = when (kind) {
            "sonarr" -> DemoWorld.shows.getOrNull(id - 1)?.let { s ->
                DemoWorld.episodes[s.tmdbId].orEmpty().filter { it.hasFile }.takeLast(3).map { e ->
                    obj("seriesId" to id, "seasonNumber" to e.season, "episodeNumbers" to listOf(e.number), "episodeFileId" to e.arrId,
                        "existingPath" to "Season %d/%s.S%02dE%02d.1080p.WEB.H264-NTb.mkv".format(e.season, s.title.replace(' ', '.'), e.season, e.number),
                        "newPath" to "Season %02d/%s - S%02dE%02d - %s [WEBDL-1080p].mkv".format(e.season, s.title, e.season, e.number, e.name))
                }
            }.orEmpty()
            "radarr" -> DemoWorld.movies.getOrNull(id - 1)?.let { m ->
                listOf(obj("movieId" to id, "movieFileId" to id, "existingPath" to "${m.title.replace(' ', '.')}.${m.year}.1080p.BluRay.x264-SPARKS.mkv",
                    "newPath" to "${m.title} (${m.year}) [Bluray-1080p].mkv"))
            }.orEmpty()
            else -> emptyList()
        }
        return json(arr(list))
    }

    /** Null when the request isn't one of these. */
    fun handle(kind: String, path: String, r: DemoRequest): DemoAnswer? {
        // The *arrs answer a test with one result per provider; one of ours fails, as real setups do.
        if (path.endsWith("downloadclient/testall") || path.endsWith("indexerproxy/testall")) {
            val ids = if (path.endsWith("downloadclient/testall")) clients else proxies
            return json(arr(ids.mapIndexed { i, o ->
                val failed = i == 1
                obj("id" to o["id"], "isValid" to !failed, "validationFailures" to if (failed) listOf(obj("errorMessage" to "Unable to connect: connection refused")) else emptyList<Any>())
            }))
        }
        if (!r.isRead) return null
        if (kind == "prowlarr" && Regex("indexer/\\d+").matches(path)) {
            return json(obj("id" to path.substringAfter('/').toInt(), "tags" to listOf(1)))
        }
        return when (path) {
            "tag/detail" -> json(tagDetail(kind))
            "tag" -> json(arr(tagLabels.mapIndexed { i, l -> obj("id" to i + 1, "label" to l) }))
            "importlist" -> json(arr(importLists))
            "downloadclient" -> json(arr(clients))
            "indexerproxy" -> json(arr(proxies))
            "rename" -> rename(kind, (r.q("seriesId") ?: r.q("movieId") ?: r.q("artistId"))?.toIntOrNull() ?: 0)
            else -> null
        }
    }
}
