package org.phioster.sanctumd.demo

import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Radarr, Sonarr and Lidarr of the made-up server; one shape, three libraries. */
internal object DemoArr {
    private fun agoIso(minutes: Long) = java.time.Instant.now().minusSeconds(minutes * 60).toString()
    private fun iso(d: LocalDate) = "${d}T20:00:00Z"
    private const val GB = 1_073_741_824L

    // *arr ids: 1-based positions in the catalog lists, stable for the same catalog.
    private fun movieArrId(t: DemoTitle) = DemoWorld.movies.indexOf(t) + 1
    private fun seriesArrId(t: DemoTitle) = DemoWorld.shows.indexOf(t) + 1
    private val artistIds: List<String> by lazy { DemoWorld.artists.keys.toList() }
    private fun artistArrId(a: DemoAlbum) = artistIds.indexOf(a.artistId) + 1
    private fun albumArrId(a: DemoAlbum) = DemoWorld.albums.indexOf(a) + 1

    private fun images(t: DemoTitle) = listOf(
        obj("coverType" to "poster", "remoteUrl" to t.poster, "url" to t.poster),
        obj("coverType" to "fanart", "remoteUrl" to t.backdrop, "url" to t.backdrop),
    )
    private fun albumImages(a: DemoAlbum) = listOf(obj("coverType" to "cover", "remoteUrl" to a.cover), obj("coverType" to "poster", "remoteUrl" to a.cover))

    private fun hasFile(t: DemoTitle) = t in DemoWorld.libraryMovies
    private fun movieSize(t: DemoTitle) = if (hasFile(t)) (t.runtime.coerceAtLeast(80) * (if (t.tmdbId % 3 == 0) 160L else 55L)) * 1_048_576 else 0L
    private fun seriesFiles(t: DemoTitle) = DemoWorld.episodes[t.tmdbId].orEmpty().filter { it.hasFile }
    private fun seriesSize(t: DemoTitle) = seriesFiles(t).size * 2_400L * 1_048_576

    private fun movie(t: DemoTitle) = obj(
        "id" to movieArrId(t), "title" to t.title, "year" to t.year, "overview" to t.overview, "monitored" to true,
        "status" to (if (t in DemoWorld.upcomingMovies) "announced" else "released"), "hasFile" to hasFile(t),
        "sizeOnDisk" to movieSize(t), "images" to images(t), "tmdbId" to t.tmdbId, "imdbId" to t.imdbId, "genres" to t.genres,
        "ratings" to obj("tmdb" to obj("value" to t.vote, "votes" to 1200)), "certification" to "PG-13", "runtime" to t.runtime,
        "studio" to t.studio, "qualityProfileId" to (if (t.tmdbId % 3 == 0) 6 else 4), "minimumAvailability" to "released",
        "inCinemas" to iso(LocalDate.parse(t.date)), "digitalRelease" to iso(LocalDate.parse(t.date).plusDays(45)), "added" to agoIso((t.tmdbId % 900).toLong() * 60),
    )

    private fun series(t: DemoTitle): JsonObject {
        val eps = DemoWorld.episodes[t.tmdbId].orEmpty()
        val aired = eps.filter { it.airDate <= DemoWorld.today }
        return obj(
            "id" to seriesArrId(t), "title" to t.title, "year" to t.year, "overview" to t.overview, "monitored" to true,
            "status" to (if (t.status == "Ended") "ended" else "continuing"), "network" to t.network, "images" to images(t),
            "tmdbId" to t.tmdbId, "tvdbId" to t.tvdbId, "genres" to t.genres, "runtime" to t.runtime,
            "ratings" to obj("value" to t.vote, "votes" to 900), "certification" to "TV-14", "seriesType" to "standard", "seasonFolder" to true,
            "qualityProfileId" to 4, "sizeOnDisk" to seriesSize(t),
            "statistics" to obj("episodeCount" to aired.size, "episodeFileCount" to aired.count { it.hasFile }, "seasonCount" to t.seasons.size, "sizeOnDisk" to seriesSize(t)),
            "seasons" to t.seasons.map { obj("seasonNumber" to it.n, "monitored" to true) },
        )
    }

    private fun artist(id: String): JsonObject {
        val albums = DemoWorld.artists[id].orEmpty()
        val a = albums.first()
        val tracks = albums.sumOf { it.tracks.size }
        val have = albums.filter { it in DemoWorld.libraryAlbums }.sumOf { it.tracks.size }
        return obj(
            "id" to artistArrId(a), "artistName" to a.artist, "monitored" to true, "status" to "continuing", "overview" to "",
            "images" to albumImages(a), "genres" to listOf(a.genre), "sizeOnDisk" to have * 38L * 1_048_576,
            "statistics" to obj("albumCount" to albums.size, "trackCount" to tracks, "trackFileCount" to have, "sizeOnDisk" to have * 38L * 1_048_576),
        )
    }

    private fun episode(e: DemoEpisode) = obj(
        "id" to e.arrId, "seriesId" to seriesArrId(e.show), "seasonNumber" to e.season, "episodeNumber" to e.number, "title" to e.name,
        "hasFile" to e.hasFile, "monitored" to true, "airDate" to e.airDate.toString(), "airDateUtc" to iso(e.airDate),
        "episodeFileId" to (if (e.hasFile) e.arrId else 0),
        "series" to obj("id" to seriesArrId(e.show), "title" to e.show.title, "images" to images(e.show)),
    )

    private fun file(id: Int, path: String, sizeBytes: Long, uhd: Boolean) = obj(
        "id" to id, "relativePath" to path, "size" to sizeBytes,
        "quality" to obj("quality" to obj("name" to if (uhd) "WEBDL-2160p" else "Bluray-1080p")),
        "releaseGroup" to if (uhd) "FLUX" else "SPARKS",
        "mediaInfo" to obj("videoCodec" to (if (uhd) "x265" else "x264"), "resolution" to (if (uhd) "3840x2160" else "1920x1080"),
            "videoDynamicRangeType" to (if (uhd) "DV HDR10" else ""), "audioCodec" to (if (uhd) "EAC3 Atmos" else "DTS"), "audioChannels" to 5.1,
            "audioLanguages" to "English/German", "subtitles" to "German/English"),
    )

    private fun page(records: List<Any?>) = json(obj("page" to 1, "pageSize" to records.size, "totalRecords" to records.size, "records" to records))

    private fun queue(kind: String): List<JsonObject> = DemoWorld.downloads.mapNotNull { d ->
        val left = (d.sizeMb * (1 - d.doneFraction) * 1_048_576).toLong()
        val base = mapOf(
            "id" to d.nzbId, "title" to d.release, "size" to d.sizeMb * 1_048_576.0, "sizeleft" to left.toDouble(),
            "status" to (if (d.doneFraction >= 1.0) "completed" else if (d.doneFraction > 0) "downloading" else "queued"),
            "trackedDownloadState" to (if (d.doneFraction >= 1.0) "importPending" else "downloading"), "protocol" to "usenet",
            "downloadClient" to "NZBGet", "timeleft" to "00:%02d:%02d".format((left / 52_000_000) / 60, (left / 52_000_000) % 60),
            "quality" to obj("quality" to obj("name" to "WEBDL-1080p")), "outputPath" to "/downloads/${d.category}/${d.release}",
        )
        when {
            kind == "radarr" && d.movie != null -> obj(*(base + ("movieId" to movieArrId(d.movie))).toList().toTypedArray())
            kind == "sonarr" && d.episode != null -> obj(*(base + mapOf("seriesId" to seriesArrId(d.episode.show), "episodeId" to d.episode.arrId)).toList().toTypedArray())
            kind == "lidarr" && d.album != null -> obj(*(base + ("artistId" to artistArrId(d.album))).toList().toTypedArray())
            else -> null
        }
    }

    private fun calendar(kind: String, r: DemoRequest): List<JsonObject> {
        val start = r.q("start")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: DemoWorld.today.minusDays(7)
        val end = r.q("end")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: DemoWorld.today.plusDays(30)
        fun inRange(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
        return when (kind) {
            "radarr" -> DemoWorld.movies.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> inRange(d) || inRange(d.plusDays(45)) } == true }.map(::movie)
            "sonarr" -> DemoWorld.allEpisodes.filter { inRange(it.airDate) }.map(::episode)
            else -> DemoWorld.albums.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let(::inRange) == true }.map { a ->
                obj("id" to albumArrId(a), "title" to a.title, "releaseDate" to "${a.date}T00:00:00Z", "hasFile" to (a in DemoWorld.libraryAlbums),
                    "artistId" to artistArrId(a), "artist" to obj("id" to artistArrId(a), "artistName" to a.artist), "images" to albumImages(a))
            }
        }
    }

    private fun history(kind: String, filterId: Int? = null): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        when (kind) {
            "radarr" -> DemoWorld.libraryMovies.sortedByDescending { it.date }.take(25).forEachIndexed { i, t ->
                val rel = "${t.title.replace(' ', '.')}.${t.year}.${if (i % 3 == 0) "2160p.WEB-DL.DV.HDR" else "1080p.BluRay.x264"}-GRP"
                rows += obj("id" to i * 2 + 1, "eventType" to "downloadFolderImported", "date" to agoIso(i * 190L + 40), "sourceTitle" to rel, "quality" to obj("quality" to obj("name" to if (i % 3 == 0) "WEBDL-2160p" else "Bluray-1080p")), "movieId" to movieArrId(t))
                rows += obj("id" to i * 2 + 2, "eventType" to "grabbed", "date" to agoIso(i * 190L + 75), "sourceTitle" to rel, "quality" to obj("quality" to obj("name" to if (i % 3 == 0) "WEBDL-2160p" else "Bluray-1080p")), "movieId" to movieArrId(t))
            }
            "sonarr" -> DemoWorld.allEpisodes.filter { it.hasFile }.sortedByDescending { it.airDate }.take(40).forEachIndexed { i, e ->
                val rel = "%s.S%02dE%02d.1080p.WEB.H264-NTb".format(e.show.title.replace(' ', '.'), e.season, e.number)
                rows += obj("id" to i + 1, "eventType" to (if (i % 4 == 3) "grabbed" else "downloadFolderImported"), "date" to agoIso(i * 95L + 20), "sourceTitle" to rel,
                    "quality" to obj("quality" to obj("name" to "WEBDL-1080p")), "seriesId" to seriesArrId(e.show), "episode" to obj("seasonNumber" to e.season, "episodeNumber" to e.number))
            }
            else -> DemoWorld.libraryAlbums.take(15).forEachIndexed { i, a ->
                rows += obj("id" to i + 1, "eventType" to "trackFileImported", "date" to agoIso(i * 260L + 30), "sourceTitle" to "${a.artist} - ${a.title} (${a.date.take(4)}) [FLAC]",
                    "quality" to obj("quality" to obj("name" to "FLAC")), "artistId" to artistArrId(a))
            }
        }
        return if (filterId == null) rows else rows.filter { (it["movieId"] ?: it["seriesId"] ?: it["artistId"]).toString() == filterId.toString() }
    }

    private fun missing(kind: String): List<JsonObject> = when (kind) {
        "radarr" -> DemoWorld.missingMovies.map { obj("id" to movieArrId(it), "title" to it.title, "year" to it.year) }
        "sonarr" -> DemoWorld.missingEpisodes.take(25).map { e ->
            obj("id" to e.arrId, "title" to e.name, "seasonNumber" to e.season, "episodeNumber" to e.number, "series" to obj("id" to seriesArrId(e.show), "title" to e.show.title))
        }
        else -> DemoWorld.albums.filter { it !in DemoWorld.libraryAlbums }.take(5).map { a ->
            obj("id" to albumArrId(a), "title" to a.title, "artist" to obj("id" to artistArrId(a), "artistName" to a.artist))
        }
    }

    fun handle(kind: String, r: DemoRequest): DemoAnswer {
        val v = if (kind == "lidarr") "api/v1/" else "api/v3/"
        if (!r.path.startsWith(v)) return DemoAnswer.NotFound
        val p = r.path.removePrefix(v)
        val seg = p.split('/')
        if (!r.isRead) return ok()
        return when {
            p == "movie" -> json(arr(DemoWorld.movies.map(::movie)))
            p == "series" -> json(arr(DemoWorld.shows.map(::series)))
            p == "artist" -> json(arr(artistIds.map(::artist)))
            seg[0] == "movie" && seg.size == 2 -> DemoWorld.movies.getOrNull((seg[1].toIntOrNull() ?: 0) - 1)?.let { json(movie(it)) } ?: DemoAnswer.NotFound
            seg[0] == "series" && seg.size == 2 -> DemoWorld.shows.getOrNull((seg[1].toIntOrNull() ?: 0) - 1)?.let { json(series(it)) } ?: DemoAnswer.NotFound
            seg[0] == "artist" && seg.size == 2 -> artistIds.getOrNull((seg[1].toIntOrNull() ?: 0) - 1)?.let { json(artist(it)) } ?: DemoAnswer.NotFound
            p == "episode" -> DemoWorld.shows.getOrNull((r.q("seriesId")?.toIntOrNull() ?: 0) - 1)
                ?.let { s -> json(arr(DemoWorld.episodes[s.tmdbId].orEmpty().map(::episode))) } ?: json(arr(emptyList()))
            p == "album" -> json(arr(DemoWorld.albums.filter { artistArrId(it) == r.q("artistId")?.toIntOrNull() }.map { a ->
                obj("id" to albumArrId(a), "title" to a.title, "releaseDate" to "${a.date}T00:00:00Z", "monitored" to true,
                    "statistics" to obj("trackCount" to a.tracks.size, "trackFileCount" to if (a in DemoWorld.libraryAlbums) a.tracks.size else 0))
            }))
            p == "track" -> DemoWorld.albums.getOrNull((r.q("albumId")?.toIntOrNull() ?: 0) - 1)?.let { a ->
                json(arr(a.tracks.map { t -> obj("trackNumber" to t.n.toString(), "title" to t.title, "duration" to t.ms, "hasFile" to (a in DemoWorld.libraryAlbums)) }))
            } ?: json(arr(emptyList()))
            p == "moviefile" -> DemoWorld.movies.getOrNull((r.q("movieId")?.toIntOrNull() ?: 0) - 1)?.takeIf(::hasFile)?.let { t ->
                json(arr(listOf(file(movieArrId(t), "${t.title} (${t.year}).mkv", movieSize(t), t.tmdbId % 3 == 0))))
            } ?: json(arr(emptyList()))
            p == "episodefile" -> DemoWorld.shows.getOrNull((r.q("seriesId")?.toIntOrNull() ?: 0) - 1)?.let { s ->
                json(arr(seriesFiles(s).map { e -> file(e.arrId, "Season %02d/%s - S%02dE%02d - %s.mkv".format(e.season, s.title, e.season, e.number, e.name), 2_400L * 1_048_576, false) }))
            } ?: json(arr(emptyList()))
            p == "queue" -> page(queue(kind))
            p == "queue/details" -> json(arr(queue(kind)))
            p == "wanted/missing" -> page(missing(kind))
            p == "wanted/cutoff" -> page(if (kind == "radarr") DemoWorld.libraryMovies.filter { it.tmdbId % 7 == 0 }.take(4).map { obj("id" to movieArrId(it), "title" to it.title, "year" to it.year) } else emptyList())
            p == "calendar" -> json(arr(calendar(kind, r)))
            p == "history" -> page(history(kind))
            p.startsWith("history/") -> json(arr(history(kind, (r.q("movieId") ?: r.q("seriesId") ?: r.q("artistId"))?.toIntOrNull())))
            p == "system/status" -> json(obj("version" to when (kind) { "radarr" -> "6.0.4.10291"; "sonarr" -> "4.0.15.2941"; else -> "2.13.3.4711" },
                "runtimeVersion" to "8.0.18", "databaseType" to "SQLite", "databaseVersion" to "3.46.1", "migrationVersion" to 241,
                "appData" to "/config", "startupPath" to "/app/bin", "mode" to "console", "startTime" to agoIso(9 * 1440L + 260)))
            p == "health" -> json(arr(if (kind == "sonarr") listOf(obj("type" to "warning", "message" to "Indexer NZBgeek is unavailable due to failures for more than 6 hours", "source" to "IndexerStatusCheck")) else emptyList()))
            p == "diskspace" -> json(arr(listOf(obj("path" to "/media", "freeSpace" to 3_180 * GB, "totalSpace" to 26_000 * GB), obj("path" to "/downloads", "freeSpace" to 820 * GB, "totalSpace" to 1_800 * GB))))
            p == "qualityprofile" -> json(arr(listOf(obj("id" to 4, "name" to "HD-1080p"), obj("id" to 6, "name" to "Ultra-HD"), obj("id" to 7, "name" to "Any"))))
            p == "rootfolder" -> json(arr(listOf(obj("path" to "/media/${if (kind == "sonarr") "tv" else if (kind == "lidarr") "music" else "movies"}/", "freeSpace" to 3_180 * GB))))
            p == "indexer" -> json(arr(listOf("NZBgeek" to 25, "DrunkenSlug" to 20, "NZBFinder" to 30, "Tabula Rasa" to 35).mapIndexed { i, (n, prio) ->
                obj("id" to i + 1, "name" to "$n (Prowlarr)", "protocol" to "usenet", "priority" to prio, "enableRss" to true, "enableAutomaticSearch" to true, "enableInteractiveSearch" to true)
            }))
            p == "log" -> page(listOf(
                obj("time" to agoIso(4), "level" to "Info", "logger" to "RssSyncService", "message" to "RSS Sync Completed. Reports found: 312, Reports grabbed: 2"),
                obj("time" to agoIso(9), "level" to "Info", "logger" to "DownloadDecisionMaker", "message" to "Processing 312 releases"),
                obj("time" to agoIso(64), "level" to "Warn", "logger" to "Newznab", "message" to "NZBgeek: API request limit reached, retrying in 15 minutes", "exception" to "NzbDrone.Core.Indexers.Exceptions.RequestLimitReachedException: API limit reached"),
                obj("time" to agoIso(130), "level" to "Info", "logger" to "ImportApprovedEpisodes", "message" to "Import completed"),
            ))
            p == "system/backup" -> json(arr((0 until 5).map { obj("id" to it + 1, "name" to "${kind}_backup_v${it}.zip", "type" to (if (it == 0) "manual" else "scheduled"), "size" to 14_200_000L - it * 120_000, "time" to agoIso(it * 10_080L + 300)) }))
            p == "importlistexclusion" || p == "exclusions" -> json(arr(DemoWorld.titles.filter { it.isTv == (kind == "sonarr") }.takeLast(4).mapIndexed { i, t ->
                obj("id" to i + 1, "tvdbId" to t.tvdbId, "title" to t.title, "tmdbId" to t.tmdbId, "movieTitle" to t.title, "movieYear" to t.year)
            }))
            p == "blocklist" -> page(listOf(obj("id" to 1, "sourceTitle" to "Some.Movie.2024.1080p.WEB-DL.DUBBED-FAKE", "date" to agoIso(3_000))))
            else -> DemoAnswer.NotFound
        }
    }
}
