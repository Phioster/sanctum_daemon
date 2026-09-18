package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.WatchAvailability
import org.phioster.sanctumd.model.WatchProvider
import org.phioster.sanctumd.model.WatchProviderKind
import java.util.Locale

/** Provider logos are square; 92px covers the tile size the detail screens draw them at. */
private const val PROVIDER_LOGO_BASE = "https://image.tmdb.org/t/p/w92"

/**
 * The country whose availability to show: the user's choice, else the device's own region.
 *
 * Availability is worthless in the wrong country — a title on Netflix in the US may be nowhere
 * here — so a blank device region falls back to "US" only because TMDB always has that one, and
 * the region is written next to the logos so it is never a silent assumption.
 */
internal fun watchRegionOf(preferred: String): String =
    preferred.trim().uppercase(Locale.ROOT)
        .ifBlank { Locale.getDefault().country.uppercase(Locale.ROOT) }
        .ifBlank { "US" }

/**
 * Reads the `watchProviders` block of a Seerr movie/tv detail for one region.
 *
 * Seerr hands out TMDB's watch providers as a list of per-country entries
 * (`[{iso_3166_1, link, flatrate, rent, buy}]`); an older/raw TMDB payload keys the same entries
 * by country instead, and both are accepted. A region TMDB has no entry for means the title
 * streams nowhere **here**, which is a real answer and not an error — the caller hides the
 * section for it. Providers are never borrowed from another country to fill the gap.
 */
internal fun parseWatchProviders(detail: JsonObject, preferredRegion: String): WatchAvailability {
    val region = watchRegionOf(preferredRegion)
    val entry = when (val raw = detail["watchProviders"]) {
        is JsonArray -> raw.mapNotNull { it as? JsonObject }
            .firstOrNull { jsStr(it, "iso_3166_1").equals(region, ignoreCase = true) }
        is JsonObject -> raw.entries.firstOrNull { it.key.equals(region, ignoreCase = true) }?.value as? JsonObject
        else -> null
    } ?: return WatchAvailability(region = region)

    // A provider offering the title in several ways is kept once, under the cheapest offer:
    // the lists are concatenated stream-first and then deduplicated by provider id.
    val providers = (
        offers(entry, "flatrate", WatchProviderKind.STREAM) +
            offers(entry, "free", WatchProviderKind.STREAM) +
            offers(entry, "ads", WatchProviderKind.STREAM) +
            offers(entry, "rent", WatchProviderKind.RENT) +
            offers(entry, "buy", WatchProviderKind.BUY)
        ).distinctBy { it.id }

    return WatchAvailability(region = region, providers = providers, link = jsStr(entry, "link").orEmpty())
}

/** One offer list of a country entry, in TMDB's own display order. */
private fun offers(entry: JsonObject, key: String, kind: WatchProviderKind): List<WatchProvider> =
    (entry[key] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.sortedBy { jsInt(it, "displayPriority") ?: Int.MAX_VALUE }
        ?.mapNotNull { p ->
            val name = jsStr(p, "name")?.trim().orEmpty()
            val id = jsInt(p, "id") ?: jsInt(p, "providerId") ?: 0
            if (name.isBlank() && id == 0) return@mapNotNull null
            val logo = jsStr(p, "logoPath")?.trim().orEmpty()
            WatchProvider(
                id = id,
                name = name.ifBlank { "?" },
                logoUrl = if (logo.isNotBlank()) "$PROVIDER_LOGO_BASE$logo" else "",
                kind = kind,
            )
        }
        .orEmpty()

/**
 * Where a TMDB title streams, asked through a configured Seerr as the TMDB proxy.
 *
 * Sanctumd has no TMDB key of its own; Seerr already proxies TMDB for cast and detail, so the
 * same call answers this. Without a Seerr the feature simply has no source and stays hidden.
 */
suspend fun seerrWatchProviders(
    seerrConfig: ServiceConfig,
    tmdbId: Int,
    isTv: Boolean,
    region: String,
): WatchAvailability = withContext(Dispatchers.IO) {
    if (tmdbId <= 0) return@withContext WatchAvailability(region = watchRegionOf(region))
    val api = apiFor<SeerrApi>(seerrConfig, apiKeyHeader(seerrConfig))
    val detail = runCatching { if (isTv) api.tvRaw(tmdbId) else api.movieRaw(tmdbId) }.getOrNull()
        ?: return@withContext WatchAvailability(region = watchRegionOf(region))
    parseWatchProviders(detail, region)
}
