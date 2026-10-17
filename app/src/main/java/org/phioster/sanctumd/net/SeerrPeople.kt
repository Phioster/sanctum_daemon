package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.phioster.sanctumd.model.SeerrDiscoverItem
import org.phioster.sanctumd.model.ServiceConfig

/** One episode as TMDb lists it; [airDate] is "YYYY-MM-DD" or blank when not announced. */
data class SeerrEpisode(val number: Int, val name: String, val airDate: String, val overview: String)

data class SeerrPerson(
    val id: Int,
    val name: String,
    val knownFor: String,
    val born: String,
    val died: String,
    val place: String,
    val biography: String,
    val photoUrl: String,
    /** Roles in front of the camera, newest first, each title once. */
    val credits: List<SeerrDiscoverItem>,
)

private fun seerr(config: ServiceConfig) = apiFor<SeerrApi>(config, apiKeyHeader(config))

suspend fun seerrSeasonEpisodes(config: ServiceConfig, tmdbId: Int, season: Int): List<SeerrEpisode> = withContext(Dispatchers.IO) {
    (seerr(config).season(tmdbId, season)["episodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { e ->
        SeerrEpisode(
            number = jsInt(e, "episodeNumber") ?: 0,
            name = jsStr(e, "name").orEmpty(),
            airDate = jsStr(e, "airDate").orEmpty().take(10),
            overview = jsStr(e, "overview").orEmpty(),
        )
    }.sortedBy { it.number }
}

/**
 * A person's page. The credits come in TMDb's discover shape, so the discover parser reads them;
 * a title they played several roles in appears once.
 */
suspend fun seerrPerson(config: ServiceConfig, personId: Int): SeerrPerson = withContext(Dispatchers.IO) {
    val api = seerr(config)
    val p = api.person(personId)
    val cast = runCatching { api.personCredits(personId)["cast"] as? JsonArray }.getOrNull().orEmpty()
    val credits = parseDiscoverItems(buildJsonObject { put("results", cast) }, null)
        .distinctBy { it.mediaType to it.tmdbId }
        .sortedByDescending { it.year.ifBlank { "0" } }
    val photo = jsStr(p, "profilePath")
    SeerrPerson(
        id = personId,
        name = jsStr(p, "name").orEmpty(),
        knownFor = jsStr(p, "knownForDepartment").orEmpty(),
        born = jsStr(p, "birthday").orEmpty(),
        died = jsStr(p, "deathday").orEmpty(),
        place = jsStr(p, "placeOfBirth").orEmpty(),
        biography = jsStr(p, "biography").orEmpty(),
        photoUrl = if (!photo.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$photo" else "",
        credits = credits,
    )
}
