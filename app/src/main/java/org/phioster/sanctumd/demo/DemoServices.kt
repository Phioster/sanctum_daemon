package org.phioster.sanctumd.demo

import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.DashTab

/** The made-up NZBGet: a fast line, a queue that matches the *arr queues, a long history. */
internal object DemoNzbget {
    private val now get() = System.currentTimeMillis() / 1000

    private fun method(body: String) = Regex("\"method\"\\s*:\\s*\"([a-z]+)\"").find(body)?.groupValues?.get(1).orEmpty()

    private fun groups() = DemoWorld.downloads.mapIndexed { i, d ->
        val left = (d.sizeMb * (1 - d.doneFraction)).toLong()
        obj(
            "NZBID" to d.nzbId, "NZBName" to d.release, "FileSizeMB" to d.sizeMb, "RemainingSizeMB" to left, "Category" to d.category,
            "Status" to when { d.post.isNotBlank() -> "PP_QUEUED"; i == 0 -> "DOWNLOADING"; d.doneFraction > 0 -> "DOWNLOADING"; else -> "QUEUED" },
            "MaxPriority" to (if (i == 1) 50 else 0), "Health" to (if (i == 3) 987 else 1000), "MinPostTime" to now - (i + 1) * 86_400L,
            "PostInfoText" to d.post, "PostStageProgress" to (if (d.post.isNotBlank()) 640 else 0),
        )
    }

    private fun history() = (DemoWorld.libraryMovies.take(14).map { "${it.title.replace(' ', '.')}.${it.year}.1080p.BluRay.x264-SPARKS" to "movies" } +
        DemoWorld.allEpisodes.filter { it.hasFile }.takeLast(16).map { "%s.S%02dE%02d.1080p.WEB.H264-NTb".format(it.show.title.replace(' ', '.'), it.season, it.number) to "tv" })
        .mapIndexed { i, (name, cat) ->
            val failed = i == 5
            obj("NZBID" to 3000 + i, "Name" to name, "Category" to cat, "Status" to (if (failed) "FAILURE/HEALTH" else "SUCCESS/ALL"), "FileSizeMB" to 2_000L + i * 377,
                "ParStatus" to (if (failed) "FAILURE" else "SUCCESS"), "UnpackStatus" to (if (failed) "NONE" else "SUCCESS"), "MoveStatus" to "SUCCESS",
                "ScriptStatus" to "SUCCESS", "DeleteStatus" to (if (failed) "HEALTH" else "NONE"), "MarkStatus" to "NONE",
                "Health" to (if (failed) 912 else 1000), "CriticalHealth" to 958, "HistoryTime" to now - i * 3_600L)
        }

    fun handle(r: DemoRequest): DemoAnswer {
        val rate = 54_525_952L
        return when {
            r.path == "jsonrpc/status" -> json(obj("result" to obj(
                "DownloadRate" to rate, "RemainingSizeMB" to DemoWorld.downloads.sumOf { (it.sizeMb * (1 - it.doneFraction)).toLong() },
                "DownloadPaused" to false, "DownloadedSizeMB" to 1_843_220, "FreeDiskSpaceMB" to 842_000, "ArticleCacheMB" to 412,
                "UpTimeSec" to 9 * 86_400 + 14_400, "ThreadCount" to 48, "PostPaused" to false, "ResumeTime" to 0,
            )))
            r.path == "jsonrpc/listgroups" -> json(obj("result" to groups()))
            r.path == "jsonrpc" -> when (method(r.body)) {
                "history" -> json(obj("result" to history()))
                "version" -> json(obj("result" to "25.3"))
                "log", "loadlog" -> json(obj("result" to listOf(
                    obj("ID" to 1, "Kind" to "INFO", "Time" to now - 60, "Text" to "Collection ${DemoWorld.downloads.firstOrNull()?.release} added to queue"),
                    obj("ID" to 2, "Kind" to "INFO", "Time" to now - 300, "Text" to "Unpack for ${DemoWorld.downloads.getOrNull(2)?.release} started"),
                    obj("ID" to 3, "Kind" to "WARNING", "Time" to now - 2_400, "Text" to "news.example-provider.com: article not found, trying backup server"),
                )))
                "servervolumes" -> json(obj("result" to emptyList<Any>()))
                else -> json(obj("result" to true))
            }
            else -> DemoAnswer.NotFound
        }
    }
}

/** The made-up Prowlarr: a handful of usenet indexers that see real use. */
internal object DemoProwlarr {
    private val names = listOf("NZBgeek", "DrunkenSlug", "NZBFinder", "Tabula Rasa", "altHUB", "NinjaCentral")
    private fun agoIso(minutes: Long) = java.time.Instant.now().minusSeconds(minutes * 60).toString()

    fun handle(r: DemoRequest): DemoAnswer {
        val p = r.path.removePrefix("api/v1/")
        if (!r.isRead) return ok()
        return when (p) {
            "indexer" -> json(arr(names.mapIndexed { i, n -> obj("id" to i + 1, "name" to n, "protocol" to "usenet", "enable" to true, "priority" to 25, "privacy" to "private") }))
            "indexerstats" -> json(obj("indexers" to names.mapIndexed { i, n ->
                obj("indexerId" to i + 1, "indexerName" to n, "numberOfQueries" to 4_812 - i * 611, "numberOfGrabs" to 1_204 - i * 173, "numberOfFailedQueries" to i * 9, "numberOfFailedGrabs" to i)
            }))
            "indexerstatus" -> json(arr(emptyList()))
            "history" -> json(obj("records" to DemoWorld.libraryMovies.take(20).mapIndexed { i, t ->
                obj("eventType" to (if (i % 3 == 0) "releaseGrabbed" else "indexerQuery"), "date" to agoIso(i * 41L + 3), "indexer" to names[i % names.size],
                    "data" to obj("query" to t.title, "title" to "${t.title.replace(' ', '.')}.${t.year}.1080p.WEB-DL"))
            }))
            "system/status" -> json(obj("version" to "2.0.5.5160"))
            "health", "applications" -> json(arr(emptyList()))
            "system/task" -> json(arr(listOf("Application Indexer Sync", "Check Health", "Clean Up Database", "Backup").mapIndexed { i, n ->
                obj("name" to n, "lastExecution" to agoIso(i * 120L + 5), "nextExecution" to java.time.Instant.now().plusSeconds(i * 3_600L + 600).toString())
            }))
            "log" -> json(obj("records" to listOf(obj("time" to agoIso(2), "level" to "Info", "logger" to "IndexerSearch", "message" to "Searching 6 indexers for [The Matrix]"))))
            "system/backup" -> json(arr(listOf(obj("id" to 1, "name" to "prowlarr_backup.zip", "type" to "scheduled", "size" to 3_100_000, "time" to agoIso(1_440)))))
            else -> DemoAnswer.NotFound
        }
    }
}

/** The made-up ntfy topic: the webhooks of the other services, posters attached. */
internal object DemoNtfy {
    fun handle(r: DemoRequest): DemoAnswer {
        val now = System.currentTimeMillis() / 1000
        val lines = buildList {
            DemoWorld.libraryMovies.take(5).forEachIndexed { i, t ->
                add(obj("id" to "dm$i", "time" to now - i * 2_700L - 120, "event" to "message", "topic" to "homelab", "title" to "Radarr: movie imported",
                    "message" to "${t.title} (${t.year}) · Bluray-1080p", "tags" to listOf("clapper", "radarr"),
                    "attachment" to obj("name" to "poster.jpg", "type" to "image/jpeg", "url" to t.poster)))
            }
            DemoWorld.allEpisodes.filter { it.hasFile }.takeLast(4).forEachIndexed { i, e ->
                add(obj("id" to "de$i", "time" to now - i * 3_100L - 400, "event" to "message", "topic" to "homelab", "title" to "Sonarr: episode downloaded",
                    "message" to "%s · S%02dE%02d · %s".format(e.show.title, e.season, e.number, e.name), "tags" to listOf("tv", "sonarr"),
                    "attachment" to obj("name" to "poster.jpg", "type" to "image/jpeg", "url" to e.show.poster)))
            }
            add(obj("id" to "dw", "time" to now - 9_000, "event" to "message", "topic" to "homelab", "title" to "Disk space low",
                "message" to "**/downloads** has *12 %* free (`220 GB`)", "tags" to listOf("warning"), "content_type" to "text/markdown", "priority" to 4))
            add(obj("id" to "dj", "time" to now - 12_000, "event" to "message", "topic" to "homelab", "title" to "Jellyfin: Mara started watching",
                "message" to "${DemoWorld.libraryMovies.getOrNull(1)?.title} on Living Room TV", "tags" to listOf("tv")))
        }
        return DemoAnswer.Text(lines.joinToString("\n") { it.toString() }, type = "application/x-ndjson")
    }
}

/** A layout for when the user has none yet: what a well-used setup would show. */
internal object DemoLayout {
    private fun card(id: String, type: CardType, svc: String, posterSize: String = "", background: Boolean = false) =
        DashCard(id = id, type = type, serviceId = svc, posterSize = posterSize, background = background)

    fun defaultTabs(): List<DashTab> = listOf(
        DashTab("demo-home", "Home", icon = "home", cards = listOf(
            card("d1", CardType.UNIFIED_CALENDAR, ""),
            card("d2", CardType.JELLYFIN_SESSIONS, "demo-jellyfin"),
            card("d3", CardType.JELLYFIN_RESUME, "demo-jellyfin", background = true),
            card("d4", CardType.NZBGET_QUEUE, "demo-nzbget"),
            card("d5", CardType.SEERR_REQUESTS, "demo-seerr"),
        )),
        DashTab("demo-movies", "Movies", icon = "movie", cards = listOf(
            card("d6", CardType.SEERR_POPULAR_MOVIES, "demo-seerr", background = true),
            card("d7", CardType.RADARR_CALENDAR, "demo-radarr"),
            card("d8", CardType.RADARR_MISSING, "demo-radarr"),
            card("d9", CardType.RADARR_HISTORY, "demo-radarr"),
        )),
        DashTab("demo-series", "Series", icon = "tv", cards = listOf(
            card("d10", CardType.SONARR_CALENDAR, "demo-sonarr"),
            card("d11", CardType.SEERR_POPULAR_TV, "demo-seerr", background = true),
            card("d12", CardType.SONARR_MISSING, "demo-sonarr"),
            card("d13", CardType.SONARR_HISTORY, "demo-sonarr"),
        )),
    )
}
