package org.phioster.sanctumd.demo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

@Serializable internal data class DemoCast(val id: Int, val name: String, val character: String = "", val profilePath: String = "")
@Serializable internal data class DemoSeason(val n: Int, val episodes: Int, val airDate: String = "", val name: String = "")
@Serializable internal data class DemoNext(val season: Int? = null, val episode: Int? = null, val name: String = "", val airDate: String = "")

/** One title of the bundled catalog: public TMDb metadata, see assets/demo/catalog.json. */
@Serializable internal data class DemoTitle(
    val type: String,
    val tmdbId: Int,
    val title: String,
    val date: String = "",
    val overview: String = "",
    val posterPath: String = "",
    val backdropPath: String = "",
    val genres: List<String> = emptyList(),
    val vote: Double = 0.0,
    val imdbId: String = "",
    val cast: List<DemoCast> = emptyList(),
    val runtime: Int = 0,
    val status: String = "",
    val studio: String = "",
    val tvdbId: Int = 0,
    val network: String = "",
    val seasons: List<DemoSeason> = emptyList(),
    val next: DemoNext? = null,
) {
    val isTv get() = type == "tv"
    val year get() = date.take(4).toIntOrNull() ?: 0
    val poster get() = if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w342$posterPath"
    val backdrop get() = if (backdropPath.isBlank()) poster else "https://image.tmdb.org/t/p/w780$backdropPath"
}

@Serializable internal data class DemoTrackRaw(val n: Int, val disc: Int = 1, val title: String, val ms: Long = 180_000)

/** One album of the bundled music catalog (Apple's public charts), see assets/demo/music.json. */
@Serializable internal data class DemoAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val date: String = "",
    val genre: String = "",
    val cover: String = "",
    val tracks: List<DemoTrackRaw> = emptyList(),
)

internal data class DemoEpisode(val show: DemoTitle, val season: Int, val number: Int, val name: String, val airDate: LocalDate, val hasFile: Boolean) {
    val id get() = DemoWorld.episodeId(show.tmdbId, season, number)
    /** Sonarr's episode id: unique and stable, from the show and the position. */
    val arrId get() = show.tmdbId * 10_000 + season * 100 + number
}

internal data class DemoUser(val id: String, val name: String, val seerrId: Int, val watchSeconds: Long, val admin: Boolean = false)

/** Something downloading right now, as all of NZBGet, the *arr queues and the dashboards see it. */
internal data class DemoDownload(
    val nzbId: Int,
    val release: String,
    val sizeMb: Long,
    val doneFraction: Double,
    val category: String,
    val movie: DemoTitle? = null,
    val episode: DemoEpisode? = null,
    val album: DemoAlbum? = null,
    val post: String = "",
)

/**
 * The made-up server, derived from the two bundled catalogs. Deterministic: a fixed seed, and
 * every date counted from today, so the calendar is full on whatever day the demo is opened.
 */
internal object DemoWorld {
    const val ADMIN_ID = "demo-admin"
    private val json = Json { ignoreUnknownKeys = true }

    val today: LocalDate get() = LocalDate.now()

    /** Reads a bundled catalog; tests point it at the source tree instead of the APK. */
    internal var assetReader: (String) -> String = { name -> DemoMode.app.assets.open("demo/$name").bufferedReader().use { it.readText() } }

    private fun asset(name: String): String = runCatching { assetReader(name) }.getOrDefault("[]")

    val titles: List<DemoTitle> by lazy { json.decodeFromString<List<DemoTitle>>(asset("catalog.json")) }
    val albumsRaw: List<DemoAlbum> by lazy { json.decodeFromString<List<DemoAlbum>>(asset("music.json")) }

    val movies by lazy { titles.filter { !it.isTv } }
    val shows by lazy { titles.filter { it.isTv } }

    private fun parse(d: String): LocalDate? = runCatching { LocalDate.parse(d.take(10)) }.getOrNull()

    /** Out already, so in the library; the newest few are still "missing" so Radarr has work. */
    val releasedMovies by lazy { movies.filter { (parse(it.date) ?: LocalDate.MAX) <= today } }
    val upcomingMovies by lazy { movies.filter { (parse(it.date) ?: LocalDate.MIN) > today }.sortedBy { it.date } }
    val missingMovies by lazy { releasedMovies.sortedByDescending { it.date }.take(4) }
    val libraryMovies by lazy { releasedMovies - missingMovies.toSet() }

    /**
     * Every season of every show, an episode a week from the season's first air date. A running
     * show's next season continues past today; each such show also gets a missing episode or two.
     */
    val episodes: Map<Int, List<DemoEpisode>> by lazy {
        val rnd = Random(7)
        shows.associate { show ->
            val list = mutableListOf<DemoEpisode>()
            show.seasons.forEach { s ->
                val start = parse(s.airDate) ?: return@forEach
                (1..s.episodes.coerceAtMost(24)).forEach { e ->
                    val air = start.plusWeeks((e - 1).toLong())
                    list += DemoEpisode(show, s.n, e, "Episode $e", air, air <= today)
                }
            }
            // A running show: the announced episode and its following weeks fill the calendar.
            show.next?.let { nx ->
                val air = parse(nx.airDate)
                if (air != null && nx.season != null && nx.episode != null) {
                    list.removeAll { it.season == nx.season && it.number >= nx.episode }
                    (0 until 6).forEach { k ->
                        val n = nx.episode + k
                        list += DemoEpisode(show, nx.season, n, if (k == 0 && nx.name.isNotBlank()) nx.name else "Episode $n", air.plusWeeks(k.toLong()), false)
                    }
                }
            }
            // A few aired episodes without a file: what Sonarr's "missing" lists.
            if (rnd.nextInt(4) == 0) {
                val aired = list.withIndex().filter { it.value.hasFile }.takeLast(2)
                aired.forEach { (i, ep) -> list[i] = ep.copy(hasFile = false) }
            }
            show.tmdbId to list.sortedWith(compareBy({ it.season }, { it.number }))
        }
    }
    val libraryShows by lazy { shows.filter { s -> episodes[s.tmdbId].orEmpty().any { it.hasFile } } }
    val allEpisodes by lazy { episodes.values.flatten() }
    val missingEpisodes by lazy { allEpisodes.filter { !it.hasFile && it.airDate <= today }.sortedByDescending { it.airDate } }

    /** Albums; the newest few are announced for the coming weeks so Lidarr's calendar has some. */
    val albums: List<DemoAlbum> by lazy {
        albumsRaw.mapIndexed { i, a ->
            if (i % 7 == 3) a.copy(date = today.plusDays((i / 7 + 1) * 6L).toString()) else a
        }
    }
    val libraryAlbums by lazy { albums.filter { (parse(it.date) ?: today) <= today } }
    val artists by lazy { albums.groupBy { it.artistId } }

    val users = listOf(
        DemoUser(ADMIN_ID, "admin", 1, 41_200, admin = true),
        DemoUser("demo-u1", "Mara", 2, 186_400),
        DemoUser("demo-u2", "Jonas", 3, 152_900),
        DemoUser("demo-u3", "Lea", 4, 121_700),
        DemoUser("demo-u4", "Tobi", 5, 88_300),
        DemoUser("demo-u5", "Emma", 6, 64_100),
        DemoUser("demo-u6", "Kai", 7, 23_800),
    )

    /** What is downloading: films, episodes and an album, at different stages. */
    val downloads: List<DemoDownload> by lazy {
        val m = missingMovies
        val eps = missingEpisodes
        buildList {
            m.getOrNull(0)?.let { add(DemoDownload(4101, release(it.title, it.year, "2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX"), 18_420, 0.62, "movies", movie = it)) }
            m.getOrNull(1)?.let { add(DemoDownload(4102, release(it.title, it.year, "1080p.BluRay.x264-SPARKS"), 9_870, 0.18, "movies", movie = it)) }
            eps.getOrNull(0)?.let { add(DemoDownload(4103, epRelease(it, "1080p.WEB.H264-SuccessfulCrab"), 2_310, 1.0, "tv", episode = it, post = "Unpacking")) }
            eps.getOrNull(1)?.let { add(DemoDownload(4104, epRelease(it, "2160p.WEB-DL.DDP5.1.H.265-NTb"), 6_540, 0.41, "tv", episode = it)) }
            eps.getOrNull(2)?.let { add(DemoDownload(4105, epRelease(it, "720p.HDTV.x264-SYNCOPY"), 1_120, 0.0, "tv", episode = it)) }
            albums.firstOrNull { (parse(it.date) ?: today) <= today }?.let { a ->
                add(DemoDownload(4106, "${a.artist.replace(' ', '.')}-${a.title.replace(' ', '.')}-WEB-${a.date.take(4)}-FLAC", 612, 0.83, "music", album = a))
            }
        }
    }

    private fun release(title: String, year: Int, tail: String) = "${dots(title)}.$year.$tail"
    private fun epRelease(e: DemoEpisode, tail: String) = "%s.S%02dE%02d.%s".format(dots(e.show.title), e.season, e.number, tail)
    private fun dots(s: String) = s.replace(Regex("[^A-Za-z0-9]+"), ".").trim('.')

    // Ids: readable, stable, and telling what they are.
    fun movieId(tmdb: Int) = "m$tmdb"
    fun seriesId(tmdb: Int) = "s$tmdb"
    fun seasonId(tmdb: Int, n: Int) = "s$tmdb-$n"
    fun episodeId(tmdb: Int, s: Int, e: Int) = "e$tmdb-$s-$e"
    fun albumId(id: String) = "a$id"
    fun trackId(album: String, n: Int, disc: Int) = "t$album-$disc-$n"
    fun personId(tmdb: Int) = "p$tmdb"

    fun titleByTmdb(tmdb: Int): DemoTitle? = titles.firstOrNull { it.tmdbId == tmdb }

    /** The picture a Jellyfin image request asks for, as a public URL; null when there is none. */
    fun imageFor(itemId: String, kind: String): String? {
        val backdrop = kind.equals("Backdrop", true) || kind.equals("Thumb", true)
        fun t(tmdb: Int) = titleByTmdb(tmdb)?.let { if (backdrop) it.backdrop else it.poster }
        return when {
            itemId.startsWith("lib-") -> when (itemId) {
                "lib-movies" -> libraryMovies.getOrNull(3)?.backdrop
                "lib-tv" -> libraryShows.getOrNull(2)?.backdrop
                "lib-music" -> libraryAlbums.getOrNull(1)?.cover
                else -> libraryMovies.getOrNull(8)?.backdrop
            }
            // Before "p": a playlist id starts with that letter too.
            itemId.startsWith("pl-") -> libraryMovies.getOrNull(itemId.length % 10)?.poster
            itemId.startsWith("m") -> itemId.drop(1).toIntOrNull()?.let(::t)
            itemId.startsWith("s") -> itemId.drop(1).substringBefore('-').toIntOrNull()?.let(::t)
            itemId.startsWith("e") -> itemId.drop(1).substringBefore('-').toIntOrNull()?.let { titleByTmdb(it)?.backdrop }
            itemId.startsWith("a") -> albums.firstOrNull { it.id == itemId.drop(1) }?.cover
            itemId.startsWith("t") -> albums.firstOrNull { it.id == itemId.drop(1).substringBefore('-') }?.cover
            itemId.startsWith("p") -> itemId.drop(1).toIntOrNull()?.let { pid ->
                titles.firstNotNullOfOrNull { t -> t.cast.firstOrNull { it.id == pid }?.profilePath?.takeIf { it.isNotBlank() } }
                    ?.let { "https://image.tmdb.org/t/p/w185$it" }
            }
            else -> null
        }
    }
}
