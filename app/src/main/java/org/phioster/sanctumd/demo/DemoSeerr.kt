package org.phioster.sanctumd.demo

import kotlinx.serialization.json.JsonObject

/** The made-up Seerr: discover from the catalog, and a household that requests a lot. */
internal object DemoSeerr {
    private fun agoIso(minutes: Long) = java.time.Instant.now().minusSeconds(minutes * 60).toString()

    /** Seerr's media status: 5 available, 4 partially, 3 processing, 2 pending, else unknown. */
    private fun mediaStatus(t: DemoTitle): Int? = when {
        t in DemoWorld.libraryMovies -> 5
        t in DemoWorld.libraryShows -> if (DemoWorld.episodes[t.tmdbId].orEmpty().all { it.hasFile || it.airDate > DemoWorld.today }) 5 else 4
        requests.any { it.title == t && it.status == 2 } -> 3
        requests.any { it.title == t && it.status == 1 } -> 2
        else -> null
    }

    private fun mediaInfo(t: DemoTitle) = mediaStatus(t)?.let { obj("id" to t.tmdbId, "tmdbId" to t.tmdbId, "status" to it, "mediaType" to t.type) }

    private fun result(t: DemoTitle) = obj(
        "id" to t.tmdbId, "mediaType" to t.type, (if (t.isTv) "name" else "title") to t.title,
        (if (t.isTv) "firstAirDate" else "releaseDate") to t.date, "posterPath" to t.posterPath, "backdropPath" to t.backdropPath,
        "overview" to t.overview, "voteAverage" to t.vote, "adult" to false, "mediaInfo" to mediaInfo(t),
    )

    private fun page(list: List<DemoTitle>) = json(obj("page" to 1, "totalPages" to 1, "totalResults" to list.size, "results" to list.map(::result)))

    private fun detail(t: DemoTitle) = JsonObject(result(t) + mapOf(
        "genres" to toJson(t.genres.mapIndexed { i, g -> obj("id" to i + 1, "name" to g) }),
        "runtime" to toJson(t.runtime), "status" to toJson(t.status), "originalLanguage" to toJson("en"),
        "credits" to obj("cast" to t.cast.map { obj("id" to it.id, "name" to it.name, "character" to it.character, "profilePath" to it.profilePath) }),
        "externalIds" to obj("imdbId" to t.imdbId, "tvdbId" to t.tvdbId),
        "seasons" to toJson(t.seasons.map { obj("seasonNumber" to it.n, "name" to it.name.ifBlank { "Season ${it.n}" }, "episodeCount" to it.episodes, "airDate" to it.airDate) }),
        "networks" to toJson(listOf(obj("name" to t.network))),
        "productionCompanies" to toJson(listOf(obj("name" to t.studio))),
        "watchProviders" to toJson(emptyList<Any>()),
        "relatedVideos" to toJson(emptyList<Any>()),
    ))

    data class Req(val id: Int, val title: DemoTitle, val user: DemoUser, val status: Int, val minutesAgo: Long)

    /** Requests of the last weeks: pending, approved and done, from everyone in the house. */
    val requests: List<Req> by lazy {
        val pool = (DemoWorld.upcomingMovies + DemoWorld.missingMovies + DemoWorld.libraryMovies.take(8) + DemoWorld.libraryShows.take(6))
        pool.take(24).mapIndexed { i, t ->
            val status = when {
                t in DemoWorld.upcomingMovies && i % 3 == 0 -> 1
                t in DemoWorld.upcomingMovies -> 2
                t in DemoWorld.missingMovies -> 2
                else -> 2
            }
            Req(i + 1, t, DemoWorld.users[1 + i % 6], status, i * 211L + 15)
        }
    }

    private fun request(r: Req) = obj(
        "id" to r.id, "status" to r.status, "type" to r.title.type, "createdAt" to agoIso(r.minutesAgo), "is4k" to false,
        "media" to obj("id" to r.title.tmdbId, "tmdbId" to r.title.tmdbId, "mediaType" to r.title.type, "status" to (mediaStatus(r.title) ?: 2)),
        "requestedBy" to obj("id" to r.user.seerrId, "displayName" to r.user.name),
        "seasons" to if (r.title.isTv) r.title.seasons.takeLast(1).map { obj("seasonNumber" to it.n) } else emptyList(),
    )

    private fun filtered(filter: String?): List<Req> = when (filter) {
        "pending" -> requests.filter { it.status == 1 }
        "approved", "processing" -> requests.filter { it.status == 2 && (mediaStatus(it.title) ?: 0) < 5 }
        "available" -> requests.filter { (mediaStatus(it.title) ?: 0) >= 4 }
        "unavailable" -> requests.filter { (mediaStatus(it.title) ?: 0) < 5 }
        else -> requests
    }

    fun handle(r: DemoRequest): DemoAnswer {
        val p = r.path.removePrefix("api/v1/")
        val seg = p.split('/')
        if (!r.isRead) return ok()
        val trending = DemoWorld.titles.sortedByDescending { it.vote * 10 + (it.year - 2020) }
        return when {
            p == "request/count" -> json(obj(
                "total" to requests.size, "movie" to requests.count { !it.title.isTv }, "tv" to requests.count { it.title.isTv },
                "pending" to requests.count { it.status == 1 }, "approved" to requests.count { it.status == 2 },
                "processing" to requests.count { it.status == 2 && (mediaStatus(it.title) ?: 0) < 5 },
                "available" to requests.count { (mediaStatus(it.title) ?: 0) >= 5 }, "declined" to 0,
            ))
            p == "request" -> {
                val by = r.q("requestedBy")?.toIntOrNull()
                val list = filtered(r.q("filter")).filter { by == null || it.user.seerrId == by }
                json(obj("pageInfo" to obj("pages" to 1, "results" to list.size), "results" to list.take(r.q("take")?.toIntOrNull() ?: 30).map(::request)))
            }
            seg[0] == "request" && seg.size == 2 -> requests.firstOrNull { it.id == seg[1].toIntOrNull() }?.let { json(request(it)) } ?: DemoAnswer.NotFound
            p == "user" -> json(obj("results" to DemoWorld.users.map { u ->
                obj("id" to u.seerrId, "displayName" to u.name, "username" to u.name.lowercase(), "email" to "${u.name.lowercase()}@example.com", "requestCount" to requests.count { it.user == u })
            }))
            seg[0] == "user" && seg.getOrNull(2) == "quota" -> json(obj(
                "movie" to obj("days" to 7, "limit" to 10, "used" to 3, "remaining" to 7, "restricted" to false),
                "tv" to obj("days" to 7, "limit" to 0, "used" to 2, "remaining" to 0, "restricted" to false),
            ))
            p == "discover/trending" -> page(trending.take(40))
            p == "discover/movies" -> page(trending.filter { !it.isTv }.take(40))
            p == "discover/tv" -> page(trending.filter { it.isTv }.take(30))
            p == "discover/movies/upcoming" -> page(DemoWorld.upcomingMovies)
            p == "discover/tv/upcoming" -> page(DemoWorld.shows.filter { it.next != null })
            p == "discover/watchlist" -> page(DemoWorld.upcomingMovies.take(5))
            p.startsWith("discover/movies/genre") || p.startsWith("discover/tv/genre") -> page(trending.filter { it.isTv == p.startsWith("discover/tv") }.take(20))
            p == "genres/movie" || p == "genres/tv" -> json(arr(DemoWorld.titles.flatMap { it.genres }.distinct().mapIndexed { i, g -> obj("id" to i + 1, "name" to g) }))
            p == "search" -> page(DemoWorld.titles.filter { it.title.contains(r.q("query").orEmpty(), true) })
            (seg[0] == "movie" || seg[0] == "tv") && seg.size == 2 -> DemoWorld.titleByTmdb(seg[1].toIntOrNull() ?: 0)?.let { json(detail(it)) } ?: DemoAnswer.NotFound
            (seg[0] == "movie" || seg[0] == "tv") && seg.size == 3 && seg[2] in setOf("recommendations", "similar") -> {
                val me = DemoWorld.titleByTmdb(seg[1].toIntOrNull() ?: 0)
                page(DemoWorld.titles.filter { it != me && it.isTv == (seg[0] == "tv") && it.genres.any { g -> me?.genres?.contains(g) == true } }.take(12))
            }
            seg[0] == "tv" && seg.getOrNull(2) == "season" -> {
                val show = DemoWorld.titleByTmdb(seg[1].toIntOrNull() ?: 0)
                val n = seg.getOrNull(3)?.toIntOrNull() ?: 1
                json(obj("seasonNumber" to n, "episodes" to show?.let { DemoWorld.episodes[it.tmdbId] }.orEmpty().filter { it.season == n }.map {
                    obj("episodeNumber" to it.number, "name" to it.name, "airDate" to it.airDate.toString(), "overview" to "")
                }))
            }
            seg[0] == "person" && seg.size == 2 -> DemoWorld.titles.firstNotNullOfOrNull { t -> t.cast.firstOrNull { it.id == seg[1].toIntOrNull() } }?.let { c ->
                json(obj("id" to c.id, "name" to c.name, "knownForDepartment" to "Acting", "profilePath" to c.profilePath, "biography" to "", "birthday" to "", "placeOfBirth" to ""))
            } ?: DemoAnswer.NotFound
            seg[0] == "person" && seg.getOrNull(2) == "combined_credits" -> json(obj("cast" to DemoWorld.titles.filter { t -> t.cast.any { it.id == seg[1].toIntOrNull() } }.map(::result), "crew" to emptyList<Any>()))
            p == "issue" -> json(obj("results" to DemoWorld.libraryMovies.take(3).mapIndexed { i, t ->
                obj("id" to i + 1, "issueType" to listOf(1, 2, 3)[i], "status" to (if (i == 2) 2 else 1), "media" to obj("tmdbId" to t.tmdbId, "mediaType" to "movie"), "createdBy" to obj("displayName" to DemoWorld.users[2 + i].name))
            }))
            p == "blocklist" -> json(obj("results" to emptyList<Any>()))
            p == "settings/jobs" -> json(arr(listOf(
                Triple("jellyfin-recently-added-scan", "Jellyfin Recently Added Scan", "minutes"), Triple("jellyfin-full-scan", "Jellyfin Full Library Scan", "hours"),
                Triple("radarr-scan", "Radarr Scan", "hours"), Triple("sonarr-scan", "Sonarr Scan", "hours"), Triple("download-sync", "Download Sync", "seconds"),
            ).map { (id, name, iv) -> obj("id" to id, "name" to name, "type" to "process", "interval" to iv, "cronSchedule" to "0 */5 * * * *", "nextExecutionTime" to java.time.Instant.now().plusSeconds(600).toString(), "running" to false) }))
            p == "settings/cache" -> json(obj("apiCaches" to listOf(obj("id" to "tmdb", "name" to "The Movie Database API", "stats" to obj("hits" to 48_211, "misses" to 3_104, "keys" to 812, "ksize" to 140_000, "vsize" to 2_900_000)))))
            p == "settings/main" -> json(obj("applicationTitle" to "Seerr", "locale" to "en", "partialRequestsEnabled" to true))
            p == "settings/logs" -> json(obj("results" to listOf(obj("level" to "info", "message" to "Recently Added Scan Complete", "timestamp" to agoIso(3)))))
            p.startsWith("service/") -> json(arr(listOf(obj("id" to 0, "name" to seg.getOrNull(1).orEmpty().replaceFirstChar { it.uppercase() }, "isDefault" to true, "activeDirectory" to "/media", "activeProfileId" to 4))))
            p == "regions" || p == "languages" -> json(arr(emptyList()))
            else -> DemoAnswer.NotFound
        }
    }
}
