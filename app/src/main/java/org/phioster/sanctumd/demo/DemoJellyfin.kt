package org.phioster.sanctumd.demo

import kotlinx.serialization.json.JsonObject
import org.phioster.sanctumd.demo.DemoWorld.albumId
import org.phioster.sanctumd.demo.DemoWorld.episodeId
import org.phioster.sanctumd.demo.DemoWorld.movieId
import org.phioster.sanctumd.demo.DemoWorld.personId
import org.phioster.sanctumd.demo.DemoWorld.seasonId
import org.phioster.sanctumd.demo.DemoWorld.seriesId
import org.phioster.sanctumd.demo.DemoWorld.trackId
import java.time.LocalDate
import java.time.ZoneOffset

/** The made-up Jellyfin: a family server with films, series and music that gets used a lot. */
internal object DemoJellyfin {
    private const val TICKS_PER_MIN = 600_000_000L
    private fun iso(d: LocalDate) = "${d}T20:00:00.0000000Z"
    private fun agoIso(minutes: Long) = java.time.Instant.now().minusSeconds(minutes * 60).toString()

    /** Stable pseudo-random per id, so "watched" and "favourite" don't change between screens. */
    private fun h(id: String, mod: Int) = (id.hashCode() and 0x7fffffff) % mod

    private fun userData(id: String, folderUnplayed: Int? = null): JsonObject {
        val played = folderUnplayed == null && h(id, 3) == 0
        return obj(
            "Played" to (if (folderUnplayed != null) folderUnplayed == 0 else played),
            "PlayedPercentage" to null,
            "UnplayedItemCount" to folderUnplayed,
            "IsFavorite" to (h(id + "fav", 9) == 0),
        )
    }

    private fun rating(t: DemoTitle) = when {
        t.genres.any { it == "Animation" || it == "Family" } -> "PG"
        t.genres.any { it == "Horror" || it == "Crime" } -> "R"
        else -> "PG-13"
    }

    fun movieItem(t: DemoTitle, progress: Double? = null) = obj(
        "Id" to movieId(t.tmdbId), "Name" to t.title, "Type" to "Movie", "IsFolder" to false,
        "ProductionYear" to t.year, "PremiereDate" to "${t.date}T00:00:00.0000000Z",
        "RunTimeTicks" to t.runtime * TICKS_PER_MIN, "OfficialRating" to rating(t), "CommunityRating" to t.vote,
        "ImageTags" to mapOf("Primary" to "d"), "LocationType" to "FileSystem",
        "UserData" to (progress?.let { obj("PlayedPercentage" to it * 100, "Played" to false, "PlaybackPositionTicks" to (t.runtime * TICKS_PER_MIN * it).toLong()) } ?: userData(movieId(t.tmdbId))),
    )

    private fun unplayed(t: DemoTitle) = DemoWorld.episodes[t.tmdbId].orEmpty().count { it.hasFile && h(it.id, 3) != 0 }

    fun seriesItem(t: DemoTitle) = obj(
        "Id" to seriesId(t.tmdbId), "Name" to t.title, "Type" to "Series", "IsFolder" to true,
        "ProductionYear" to t.year, "PremiereDate" to "${t.date}T00:00:00.0000000Z", "OfficialRating" to rating(t),
        "CommunityRating" to t.vote, "ImageTags" to mapOf("Primary" to "d"), "ChildCount" to t.seasons.size,
        "UserData" to userData(seriesId(t.tmdbId), unplayed(t)),
    )

    private fun seasonItem(t: DemoTitle, n: Int) = obj(
        "Id" to seasonId(t.tmdbId, n), "Name" to "Season $n", "Type" to "Season", "IsFolder" to true, "IndexNumber" to n,
        "SeriesName" to t.title, "ImageTags" to mapOf("Primary" to "d"),
        "UserData" to userData(seasonId(t.tmdbId, n), DemoWorld.episodes[t.tmdbId].orEmpty().count { it.season == n && it.hasFile && h(it.id, 3) != 0 }),
    )

    fun episodeItem(e: DemoEpisode, progress: Double? = null) = obj(
        "Id" to e.id, "Name" to e.name, "Type" to "Episode", "IsFolder" to false, "SeriesName" to e.show.title,
        "SeriesId" to seriesId(e.show.tmdbId), "ParentIndexNumber" to e.season, "IndexNumber" to e.number,
        "PremiereDate" to iso(e.airDate), "ProductionYear" to e.airDate.year, "RunTimeTicks" to (e.show.runtime.coerceAtLeast(22)) * TICKS_PER_MIN,
        "LocationType" to (if (e.hasFile) "FileSystem" else "Virtual"), "ImageTags" to mapOf("Primary" to "d"),
        "UserData" to (progress?.let { obj("PlayedPercentage" to it * 100, "Played" to false) } ?: userData(e.id)),
    )

    private fun albumItem(a: DemoAlbum) = obj(
        "Id" to albumId(a.id), "Name" to a.title, "Type" to "MusicAlbum", "IsFolder" to true, "AlbumArtist" to a.artist,
        "ProductionYear" to (a.date.take(4).toIntOrNull() ?: 0), "ImageTags" to mapOf("Primary" to "d"),
        "ChildCount" to a.tracks.size, "UserData" to userData(albumId(a.id)),
    )

    private fun trackItem(a: DemoAlbum, t: DemoTrackRaw, playlistEntry: String? = null) = obj(
        "Id" to trackId(a.id, t.n, t.disc), "Name" to t.title, "Type" to "Audio", "IsFolder" to false,
        "AlbumArtist" to a.artist, "Album" to a.title, "IndexNumber" to t.n, "ParentIndexNumber" to t.disc,
        "RunTimeTicks" to t.ms * 10_000, "ImageTags" to mapOf("Primary" to "d"), "PlaylistItemId" to playlistEntry,
        "UserData" to userData(trackId(a.id, t.n, t.disc)),
    )

    private fun library(id: String, name: String, type: String) = obj(
        "Id" to id, "Name" to name, "Type" to "CollectionFolder", "CollectionType" to type, "IsFolder" to true,
        "ImageTags" to mapOf("Primary" to "d"),
    )

    private val views get() = listOf(
        library("lib-movies", "Movies", "movies"), library("lib-tv", "Shows", "tvshows"),
        library("lib-music", "Music", "music"), library("lib-kids", "Kids", "movies"),
    )
    private val kidsMovies get() = DemoWorld.libraryMovies.filter { it.genres.any { g -> g == "Animation" || g == "Family" } }

    private fun items(list: List<JsonObject>) = json(obj("Items" to list, "TotalRecordCount" to list.size))

    /** Every item by id, for detail screens and "by id" queries. */
    private fun find(id: String): Any? = when {
        id.startsWith("m") -> DemoWorld.libraryMovies.firstOrNull { movieId(it.tmdbId) == id }
        id.startsWith("e") -> DemoWorld.allEpisodes.firstOrNull { it.id == id }
        id.startsWith("s") && '-' in id -> id.drop(1).split('-').let { (a, b) -> DemoWorld.titleByTmdb(a.toInt())?.let { it to b.toInt() } }
        id.startsWith("s") -> DemoWorld.libraryShows.firstOrNull { seriesId(it.tmdbId) == id }
        id.startsWith("a") -> DemoWorld.albums.firstOrNull { albumId(it.id) == id }
        id.startsWith("t") -> id.drop(1).split('-').let { p -> DemoWorld.albums.firstOrNull { it.id == p[0] }?.let { a -> a to a.tracks.firstOrNull { it.disc == p[1].toInt() && it.n == p[2].toInt() } } }
        else -> null
    }

    private fun itemOf(id: String): JsonObject? = when (val f = find(id)) {
        is DemoTitle -> if (f.isTv) seriesItem(f) else movieItem(f)
        is DemoEpisode -> episodeItem(f)
        is DemoAlbum -> albumItem(f)
        is Pair<*, *> -> when (val a = f.first) {
            is DemoTitle -> seasonItem(a, f.second as Int)
            is DemoAlbum -> (f.second as? DemoTrackRaw)?.let { trackItem(a, it) }
            else -> null
        }
        else -> null
    }

    private fun people(t: DemoTitle) = t.cast.map {
        obj("Id" to personId(it.id), "Name" to it.name, "Role" to it.character, "Type" to "Actor", "PrimaryImageTag" to (if (it.profilePath.isNotBlank()) "d" else null))
    }

    private fun videoSource(name: String, minutes: Int, uhd: Boolean) = obj(
        "Container" to "mkv", "Size" to (if (uhd) 21_474_836_480L else 6_442_450_944L) * minutes / 120,
        "Path" to "/media/$name.mkv", "Bitrate" to if (uhd) 24_000_000 else 8_000_000,
        "MediaStreams" to listOf(
            obj("Type" to "Video", "Codec" to (if (uhd) "hevc" else "h264"), "Width" to (if (uhd) 3840 else 1920), "Height" to (if (uhd) 2160 else 1080),
                "VideoRange" to (if (uhd) "HDR" else "SDR"), "BitDepth" to (if (uhd) 10 else 8), "AverageFrameRate" to 23.976, "DisplayTitle" to if (uhd) "4K HEVC HDR" else "1080p H264"),
            obj("Type" to "Audio", "Codec" to "eac3", "Language" to "eng", "DisplayLanguage" to "English", "Channels" to 6, "ChannelLayout" to "5.1", "IsDefault" to true, "DisplayTitle" to "English - Dolby Digital+ - 5.1"),
            obj("Type" to "Audio", "Codec" to "ac3", "Language" to "ger", "DisplayLanguage" to "German", "Channels" to 6, "ChannelLayout" to "5.1", "DisplayTitle" to "Deutsch - Dolby Digital - 5.1"),
            obj("Type" to "Subtitle", "Codec" to "subrip", "Language" to "ger", "DisplayLanguage" to "German", "DisplayTitle" to "Deutsch - SRT"),
            obj("Type" to "Subtitle", "Codec" to "subrip", "Language" to "eng", "DisplayLanguage" to "English", "IsForced" to true, "DisplayTitle" to "English (Forced) - SRT"),
        ),
    )

    private fun detail(id: String): JsonObject? {
        val base = itemOf(id) ?: return null
        val extra: Map<String, Any?> = when (val f = find(id)) {
            is DemoTitle -> mapOf(
                "Overview" to f.overview, "Genres" to f.genres, "People" to people(f),
                "Studios" to listOf(obj("Name" to f.studio.ifBlank { f.network })),
                "ProviderIds" to mapOf("Tmdb" to f.tmdbId.toString(), "Imdb" to f.imdbId, "Tvdb" to f.tvdbId.toString()),
                "MediaSources" to if (f.isTv) emptyList() else listOf(videoSource(f.title, f.runtime, h(f.title, 2) == 0)),
            )
            is DemoEpisode -> mapOf(
                "Overview" to f.show.overview, "Genres" to f.show.genres, "People" to people(f.show), "SeasonId" to seasonId(f.show.tmdbId, f.season),
                "MediaSources" to listOf(videoSource("%s S%02dE%02d".format(f.show.title, f.season, f.number), f.show.runtime.coerceAtLeast(22), false)),
            )
            is DemoAlbum -> mapOf("Overview" to "", "Genres" to listOf(f.genre))
            is Pair<*, *> -> (f.first as? DemoTitle)?.let { mapOf("Overview" to it.overview, "Genres" to it.genres) }.orEmpty()
            else -> emptyMap()
        }
        return JsonObject(base + extra.mapValues { toJson(it.value) })
    }

    /** A folder's children, as the browse screen lists them. */
    private fun children(parent: String, r: DemoRequest): List<JsonObject> {
        val list: List<JsonObject> = when {
            parent == "lib-movies" -> DemoWorld.libraryMovies.map { movieItem(it) }
            parent == "lib-kids" -> kidsMovies.map { movieItem(it) }
            parent == "lib-tv" -> DemoWorld.libraryShows.map { seriesItem(it) }
            parent == "lib-music" -> DemoWorld.libraryAlbums.map { albumItem(it) }
            parent.startsWith("s") && '-' in parent -> parent.drop(1).split('-').let { (a, b) ->
                DemoWorld.episodes[a.toInt()].orEmpty().filter { it.season == b.toInt() && it.airDate <= DemoWorld.today.plusDays(1) }.map { episodeItem(it) }
            }
            parent.startsWith("s") -> parent.drop(1).toIntOrNull()?.let(DemoWorld::titleByTmdb)
                ?.let { show -> show.seasons.map { seasonItem(show, it.n) } }.orEmpty()
            parent.startsWith("a") -> DemoWorld.albums.firstOrNull { albumId(it.id) == parent }?.let { a -> a.tracks.map { trackItem(a, it) } }.orEmpty()
            else -> emptyList()
        }
        val unplayed = r.q("Filters")?.contains("IsUnplayed") == true
        val filtered = if (unplayed) list.filter { (it["UserData"] as? JsonObject)?.get("Played")?.toString() != "true" } else list
        val desc = r.q("SortOrder") == "Descending"
        val sort = r.q("SortBy").orEmpty()
        fun key(o: JsonObject): String = when {
            sort.startsWith("DateCreated") || sort.startsWith("PremiereDate") -> o["PremiereDate"]?.toString().orEmpty()
            sort.startsWith("CommunityRating") -> "%05.1f".format(o["CommunityRating"]?.toString()?.toDoubleOrNull() ?: 0.0)
            o["Type"]?.toString()?.contains("Episode") == true || o["Type"]?.toString()?.contains("Audio") == true ->
                "%04d".format((o["ParentIndexNumber"]?.toString()?.toIntOrNull() ?: 0) * 100 + (o["IndexNumber"]?.toString()?.toIntOrNull() ?: 0))
            o["Type"]?.toString()?.contains("Season") == true -> "%04d".format(o["IndexNumber"]?.toString()?.toIntOrNull() ?: 0)
            else -> o["Name"]?.toString().orEmpty().removePrefix("\"The ")
        }
        return filtered.sortedBy(::key).let { if (desc) it.reversed() else it }
    }

    private val resume: List<JsonObject> get() = buildList {
        DemoWorld.libraryMovies.drop(5).take(3).forEachIndexed { i, t -> add(movieItem(t, listOf(0.42, 0.71, 0.18)[i])) }
        DemoWorld.libraryShows.take(4).mapNotNull { s -> DemoWorld.episodes[s.tmdbId].orEmpty().lastOrNull { it.hasFile } }
            .forEachIndexed { i, e -> add(episodeItem(e, listOf(0.33, 0.58, 0.12, 0.86)[i])) }
    }

    private val latest: List<JsonObject> get() =
        (DemoWorld.libraryMovies.sortedByDescending { it.date }.take(8).map { movieItem(it) } +
            DemoWorld.allEpisodes.filter { it.hasFile }.sortedByDescending { it.airDate }.take(8).map { episodeItem(it) })
            .sortedByDescending { it["PremiereDate"]?.toString() }

    private val favorites: List<JsonObject> get() =
        DemoWorld.libraryMovies.filter { h(movieId(it.tmdbId) + "fav", 9) == 0 }.map { movieItem(it) } +
            DemoWorld.libraryShows.filter { h(seriesId(it.tmdbId) + "fav", 9) == 0 }.map { seriesItem(it) }

    private val upcoming: List<JsonObject> get() =
        DemoWorld.allEpisodes.filter { it.airDate > DemoWorld.today && it.airDate <= DemoWorld.today.plusDays(28) }
            .sortedBy { it.airDate }.map { episodeItem(it) }

    // Playlists: two musical, one a film evening.
    private data class Pl(val id: String, val name: String, val entries: List<Pair<String, JsonObject>>)
    private val playlists: List<Pl> by lazy {
        val a = DemoWorld.libraryAlbums
        fun mix(seed: Int, n: Int) = (0 until n).mapNotNull { i ->
            val album = a.getOrNull((seed + i * 3) % a.size.coerceAtLeast(1)) ?: return@mapNotNull null
            val t = album.tracks.getOrNull((seed + i) % album.tracks.size.coerceAtLeast(1)) ?: return@mapNotNull null
            "pe-$seed-$i" to trackItem(album, t, "pe-$seed-$i")
        }
        listOf(
            Pl("pl-roadtrip", "Roadtrip", mix(1, 22)),
            Pl("pl-workout", "Workout", mix(5, 18)),
            Pl("pl-filmabend", "Movie night classics", DemoWorld.libraryMovies.filter { it.vote >= 7.0 }.take(9).mapIndexed { i, t ->
                "pf-$i" to JsonObject(movieItem(t) + ("PlaylistItemId" to toJson("pf-$i")))
            }),
        )
    }
    private fun playlistItem(p: Pl) = obj("Id" to p.id, "Name" to p.name, "Type" to "Playlist", "IsFolder" to true, "ChildCount" to p.entries.size, "ImageTags" to mapOf("Primary" to "d"))

    private fun sessions(): List<JsonObject> {
        val users = DemoWorld.users
        fun tracks() = listOf(
            obj("Index" to 1, "Type" to "Audio", "DisplayTitle" to "English - Dolby Digital+ - 5.1", "Language" to "eng"),
            obj("Index" to 2, "Type" to "Audio", "DisplayTitle" to "Deutsch - Dolby Digital - 5.1", "Language" to "ger"),
            obj("Index" to 3, "Type" to "Subtitle", "DisplayTitle" to "Deutsch - SRT", "Language" to "ger"),
            obj("Index" to 4, "Type" to "Subtitle", "DisplayTitle" to "English (Forced) - SRT", "Language" to "eng"),
        )
        val m = DemoWorld.libraryMovies.getOrNull(1)
        val e1 = DemoWorld.libraryShows.getOrNull(0)?.let { DemoWorld.episodes[it.tmdbId].orEmpty().lastOrNull { e -> e.hasFile } }
        val e2 = DemoWorld.libraryShows.getOrNull(5)?.let { DemoWorld.episodes[it.tmdbId].orEmpty().lastOrNull { e -> e.hasFile } }
        val album = DemoWorld.libraryAlbums.firstOrNull()
        fun playing(np: JsonObject, run: Long, pos: Double, paused: Boolean) = mapOf(
            "NowPlayingItem" to JsonObject(np + mapOf("RunTimeTicks" to toJson(run), "MediaStreams" to toJson(tracks()))),
            "PlayState" to obj("PositionTicks" to (run * pos).toLong(), "IsPaused" to paused, "AudioStreamIndex" to 2, "SubtitleStreamIndex" to -1),
        )
        fun s(id: String, user: DemoUser, client: String, device: String, minutesAgo: Long, extra: Map<String, Any?> = emptyMap()) =
            obj("Id" to id, "UserName" to user.name, "Client" to client, "DeviceName" to device, "LastActivityDate" to agoIso(minutesAgo), "SupportsRemoteControl" to true, *extra.toList().toTypedArray())
        return listOfNotNull(
            m?.let { s("sess-1", users[1], "Jellyfin Android TV", "Living Room TV", 0, playing(obj("Name" to it.title, "Type" to "Movie", "ProductionYear" to it.year), it.runtime * TICKS_PER_MIN, 0.47, false)) },
            e1?.let { s("sess-2", users[2], "Jellyfin iOS", "iPhone 16", 1, playing(obj("Name" to it.name, "Type" to "Episode", "SeriesName" to it.show.title), 45 * TICKS_PER_MIN, 0.21, true)) },
            album?.tracks?.firstOrNull()?.let { t -> s("sess-3", users[3], "Jellyfin Web", "Chrome on Mac", 0, mapOf("NowPlayingItem" to obj("Name" to t.title, "Type" to "Audio", "RunTimeTicks" to t.ms * 10_000), "PlayState" to obj("PositionTicks" to t.ms * 4_000, "IsPaused" to false))) },
            e2?.let { s("sess-4", users[5], "Jellyfin Media Player", "Bedroom Fire TV", 2, playing(obj("Name" to it.name, "Type" to "Episode", "SeriesName" to it.show.title), 52 * TICKS_PER_MIN, 0.74, false)) },
            s("sess-5", users[4], "Jellyfin Android", "Pixel 9", 26),
            s("sess-6", users[0], "Sanctumd", "Nothing Phone (3)", 0),
        )
    }

    private val usersJson get() = DemoWorld.users.mapIndexed { i, u ->
        obj("Id" to u.id, "Name" to u.name, "LastActivityDate" to agoIso(listOf(0L, 3, 12, 45, 180, 600, 2_900)[i]),
            "Policy" to obj("IsAdministrator" to u.admin, "IsDisabled" to false, "EnableContentDownloading" to (i < 4), "EnableAllFolders" to (i != 6), "EnabledFolders" to if (i == 6) listOf("lib-kids") else emptyList()))
    }

    private val tasks = listOf(
        "Scan Media Library" to "Running", "Extract Chapter Images" to "Idle", "Refresh People" to "Idle", "Generate Trickplay Images" to "Idle",
        "Clean Cache Directory" to "Idle", "Clean Transcode Directory" to "Idle", "Download missing subtitles" to "Idle", "Update Plugins" to "Idle",
        "Optimize database" to "Idle", "Audio Normalization" to "Idle", "Clean up collections and playlists" to "Idle", "Keyframe Extractor" to "Idle",
    )

    private fun activity(): List<JsonObject> {
        val u = DemoWorld.users
        val titles = DemoWorld.libraryMovies.take(6).map { it.title } + DemoWorld.libraryShows.take(4).map { it.title }
        return (0 until 30).map { i ->
            val user = u[1 + i % 5]
            val t = titles[i % titles.size]
            val (name, type) = when (i % 5) {
                0 -> "${user.name} is playing $t on Living Room TV" to "VideoPlayback"
                1 -> "${user.name} has finished playing $t on iPhone 16" to "VideoPlaybackStopped"
                2 -> "$t was added to the library" to "ItemAdded"
                3 -> "${user.name} is online from Chrome on Mac" to "SessionStarted"
                else -> "Subtitles downloaded for $t" to "SubtitleDownload"
            }
            obj("Name" to name, "Type" to type, "Date" to agoIso(i * 37L + 2), "Severity" to "Information")
        }
    }

    private val devices get() = listOf(
        "Living Room TV" to "Jellyfin Android TV", "iPhone 16" to "Jellyfin iOS", "Chrome on Mac" to "Jellyfin Web", "Bedroom Fire TV" to "Jellyfin Media Player",
        "Pixel 9" to "Jellyfin Android", "Nothing Phone (3)" to "Sanctumd", "iPad Air" to "Swiftfin", "Kitchen Echo Show" to "Jellyfin Web", "Steam Deck" to "Jellyfin Media Player",
    ).mapIndexed { i, (n, app) ->
        obj("Id" to "dev-$i", "Name" to n, "AppName" to app, "LastUserName" to DemoWorld.users[(i + 1) % DemoWorld.users.size].name, "DateLastActivity" to agoIso(i * 95L))
    }

    private val plugins = listOf(
        "TMDb" to "10.11.2.0", "OMDb" to "10.11.2.0", "MusicBrainz" to "10.11.2.0", "AudioDB" to "10.11.2.0", "Playback Reporting" to "16.0.0.0",
        "Open Subtitles" to "20.0.0.0", "Intro Skipper" to "1.10.11.3", "Trakt" to "26.0.0.0", "Webhook" to "17.0.0.0", "Fanart" to "11.0.0.0",
        "LDAP Authentication" to "20.0.0.0", "Kodi Sync Queue" to "11.0.0.0", "Merge Versions" to "4.0.0.0", "Subtitle Extract" to "5.0.0.0",
    )

    private fun playbackQuery(sql: String): DemoAnswer {
        val s = sql.lowercase()
        val rows: List<List<String>> = when {
            s.startsWith("select userid, sum(playduration)") -> DemoWorld.users.drop(1).sortedByDescending { it.watchSeconds }.take(3).map { listOf(it.name, it.watchSeconds.toString()) }
            s.startsWith("select count(*), sum(playduration), min(datecreated)") -> listOf(listOf("2847", "9384210", "2025-03-14 19:02:11"))
            s.startsWith("select playbackmethod") -> listOf(listOf("DirectPlay", "2210", "Movie"), listOf("DirectStream", "394", "Episode"), listOf("Transcode (v:direct a:aac)", "171", "Episode"), listOf("Transcode (v:h264 a:aac)", "72", "Movie"))
            s.startsWith("select itemname, itemid, count(*), sum(playduration)") -> DemoWorld.libraryMovies.take(8).mapIndexed { i, t -> listOf(t.title, movieId(t.tmdbId), (14 - i).toString(), ((14 - i) * 7_200).toString()) }
            s.startsWith("select devicename") -> listOf("Living Room TV" to 912, "iPhone 16" to 604, "Bedroom Fire TV" to 488, "Chrome on Mac" to 377, "Pixel 9" to 241, "iPad Air" to 98).map { (d, n) -> listOf(d, n.toString(), (n * 2_400).toString()) }
            s.contains("transcode") -> DemoWorld.libraryMovies.drop(10).take(4).map { listOf(it.title, movieId(it.tmdbId), "Transcode (v:h264 a:aac)", "5", "Movie") }
            else -> emptyList()
        }
        return json(obj("colums" to emptyList<String>(), "results" to rows))
    }

    fun handle(r: DemoRequest): DemoAnswer {
        val p = r.path
        val seg = p.split('/')
        if (seg.size >= 4 && seg[0] == "Items" && seg[2] == "Images") {
            return DemoWorld.imageFor(seg[1], seg[3])?.let { DemoAnswer.Fetch(it) } ?: DemoAnswer.NotFound
        }
        if (p == "user_usage_stats/submit_custom_query") {
            val sql = Regex("\"CustomQueryString\"\\s*:\\s*\"(.*?)\"").find(r.body)?.groupValues?.get(1).orEmpty()
            return playbackQuery(sql)
        }
        if (!r.isRead) return ok()
        return when {
            p == "Items/Counts" -> json(obj("MovieCount" to DemoWorld.libraryMovies.size, "SeriesCount" to DemoWorld.libraryShows.size,
                "EpisodeCount" to DemoWorld.allEpisodes.count { it.hasFile }, "SongCount" to DemoWorld.libraryAlbums.sumOf { it.tracks.size } * 9))
            p == "Sessions" -> json(arr(sessions()))
            p == "Users" -> json(arr(usersJson))
            p.startsWith("Users/") -> usersJson.firstOrNull { it["Id"].toString().trim('"') == seg[1] }?.let { json(it) } ?: DemoAnswer.NotFound
            p == "UserViews" -> items(views)
            p == "Library/VirtualFolders" -> json(arr(views.map { v ->
                val id = v["Id"].toString().trim('"')
                obj("Name" to v["Name"], "ItemId" to id, "CollectionType" to v["CollectionType"], "Locations" to listOf("/media/${id.removePrefix("lib-")}"))
            }))
            p == "Items/Latest" -> json(arr(when (r.q("ParentId")) {
                "lib-movies" -> DemoWorld.libraryMovies.sortedByDescending { it.date }.take(16).map { movieItem(it) }
                "lib-tv" -> DemoWorld.allEpisodes.filter { it.hasFile }.sortedByDescending { it.airDate }.take(16).map { episodeItem(it) }
                "lib-music" -> DemoWorld.libraryAlbums.take(12).map { albumItem(it) }
                else -> latest
            }))
            p == "UserItems/Resume" -> items(resume)
            p == "Shows/NextUp" -> items(DemoWorld.libraryShows.take(10).mapNotNull { s -> DemoWorld.episodes[s.tmdbId].orEmpty().lastOrNull { it.hasFile }?.let { episodeItem(it) } })
            p == "Shows/Upcoming" -> items(upcoming)
            p == "Items" -> itemsQuery(r)
            p.startsWith("Items/") && seg.size == 2 -> detail(seg[1])?.let { json(it) } ?: DemoAnswer.NotFound
            p.endsWith("/Collections") -> items(emptyList())
            p.startsWith("Playlists/") && p.endsWith("/Items") -> playlists.firstOrNull { it.id == seg[1] }?.let { pl -> items(pl.entries.map { it.second }) } ?: DemoAnswer.NotFound
            p.startsWith("Playlists/") && seg.size == 2 -> json(obj("OpenAccess" to (seg[1] == "pl-filmabend"), "Shares" to listOf(obj("UserId" to "demo-u1", "CanEdit" to true)), "ItemIds" to emptyList<String>()))
            p == "System/Info" -> json(obj("Version" to "10.11.2", "ServerName" to "Wohnzimmer-Server", "OperatingSystem" to "Linux"))
            p == "System/Info/Storage" -> json(storage())
            p == "ScheduledTasks" -> json(arr(tasks.mapIndexed { i, (n, st) ->
                obj("Id" to "task-$i", "Name" to n, "State" to st, "CurrentProgressPercentage" to (if (st == "Running") 64.0 else null),
                    "LastExecutionResult" to obj("Status" to (if (i == 6) "Failed" else "Completed"), "EndTimeUtc" to agoIso(i * 140L + 30)))
            }))
            p == "System/ActivityLog/Entries" -> json(obj("Items" to activity()))
            p == "Devices" -> json(obj("Items" to devices))
            p == "System/Logs" -> json(arr((0 until 3).map { obj("Name" to "log_2026101${7 - it}.log", "DateModified" to agoIso(it * 1440L), "Size" to 1_200_000L - it * 300_000) }))
            p == "System/Logs/Log" -> DemoAnswer.Text(logText())
            p == "Plugins" -> json(arr(plugins.mapIndexed { i, (n, v) -> obj("Id" to "plugin$i", "Version" to v, "Name" to n, "Description" to "", "Status" to (if (i == 11) "Disabled" else "Active"), "CanUninstall" to (i > 3)) }))
            p == "Packages" -> json(arr(plugins.mapIndexed { i, (n, v) -> obj("name" to n, "guid" to "plugin$i", "description" to "", "versions" to listOf(obj("version" to if (i == 6) "1.10.12.0" else v))) }))
            p == "Repositories" -> json(arr(listOf(obj("Name" to "Jellyfin Stable", "Url" to "https://repo.jellyfin.org/files/plugin/manifest.json", "Enabled" to true))))
            p == "Auth/Keys" -> json(obj("Items" to listOf("Sonarr", "Radarr", "Seerr", "Home Assistant").mapIndexed { i, a ->
                obj("AccessToken" to "demo${i}0f3c9a7b2e41d8", "AppName" to a, "DateCreated" to "2025-0${i + 3}-12T10:00:00Z", "DateLastActivity" to agoIso(i * 300L + 5))
            }))
            p == "Backup" -> json(arr((0 until 4).map { obj("ServerVersion" to "10.11.2", "DateCreated" to agoIso(it * 10_080L + 200), "Path" to "/config/backups/jellyfin-backup-${it}.zip") }))
            p == "LiveTv/Info" -> json(obj("IsEnabled" to false))
            p == "System/Configuration/livetv" -> json(obj())
            else -> DemoAnswer.NotFound
        }
    }

    private fun itemsQuery(r: DemoRequest): DemoAnswer {
        val types = r.q("IncludeItemTypes").orEmpty()
        r.q("searchTerm")?.let { term ->
            val hits = DemoWorld.libraryMovies.filter { it.title.contains(term, true) }.map { movieItem(it) } +
                DemoWorld.libraryShows.filter { it.title.contains(term, true) }.map { seriesItem(it) } +
                DemoWorld.libraryAlbums.filter { it.title.contains(term, true) || it.artist.contains(term, true) }.map { albumItem(it) }
            return items(hits.take(r.q("Limit")?.toIntOrNull() ?: 12))
        }
        if (r.q("IsMissing") == "true") {
            return items(DemoWorld.missingEpisodes.take(40).map { JsonObject(episodeItem(it) + ("LocationType" to toJson("Virtual"))) })
        }
        if (types == "Playlist") return items(playlists.map { playlistItem(it) })
        if (r.q("Filters")?.contains("IsFavorite") == true) return items(favorites)
        r.q("Ids")?.let { ids -> return items(ids.split(',').mapNotNull { itemOf(it) }) }
        r.q("ParentId")?.let { return items(children(it, r)) }
        val list = buildList {
            if ("Movie" in types) addAll(DemoWorld.libraryMovies.map { movieItem(it) })
            if ("Series" in types) addAll(DemoWorld.libraryShows.map { seriesItem(it) })
            if ("MusicAlbum" in types) addAll(DemoWorld.libraryAlbums.map { albumItem(it) })
        }
        return items(list.take(r.q("Limit")?.toIntOrNull() ?: 100))
    }

    private fun storage(): JsonObject {
        fun f(path: String, usedGb: Long, freeGb: Long) = obj("Path" to path, "UsedSpace" to usedGb * 1_073_741_824, "FreeSpace" to freeGb * 1_073_741_824)
        return obj(
            "ProgramDataFolder" to f("/config", 38, 412), "CacheFolder" to f("/cache", 64, 412),
            "TranscodingTempFolder" to f("/transcode", 12, 412), "InternalMetadataFolder" to f("/config/metadata", 21, 412),
            "Libraries" to listOf(
                obj("Name" to "Movies", "Folders" to listOf(f("/media/movies", 9_420, 3_180))),
                obj("Name" to "Shows", "Folders" to listOf(f("/media/tv", 12_870, 3_180))),
                obj("Name" to "Music", "Folders" to listOf(f("/media/music", 640, 3_180))),
                obj("Name" to "Kids", "Folders" to listOf(f("/media/kids", 1_210, 3_180))),
            ),
        )
    }

    private fun logText(): String = buildString {
        val now = java.time.LocalDateTime.now(ZoneOffset.UTC).withNano(0)
        listOf(
            "[INF] [12] Emby.Server.Implementations.ScheduledTasks.TaskManager: Scan Media Library Completed after 2 minute(s) and 14 seconds",
            "[INF] [31] Jellyfin.Api.Helpers.MediaInfoHelper: User policy for Mara. EnablePlaybackRemuxing: True EnableVideoPlaybackTranscoding: True",
            "[INF] [8] MediaBrowser.MediaEncoding.Transcoding.TranscodeManager: ffmpeg -analyzeduration 200M -i file:\"/media/tv/…\" … -hls_time 6",
            "[WRN] [44] MediaBrowser.Providers.Manager.ProviderManager: Fanart: no artwork found for a music album",
            "[INF] [17] Emby.Server.Implementations.Session.SessionManager: Playback stopped reported by app Jellyfin iOS playing an episode",
        ).forEachIndexed { i, line -> appendLine("[${now.minusMinutes(i * 7L)}.000 +00:00] $line") }
    }
}
