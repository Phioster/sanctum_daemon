package org.phioster.sanctumd.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.phioster.sanctumd.model.ArrCollection
import org.phioster.sanctumd.model.ArrCollectionMovie

/**
 * Reads one entry of Radarr's `/api/v3/collection`.
 *
 * Radarr keeps the whole TMDB collection, not just the films you own, and marks each one with
 * `isExisting`. That is what makes "which films of this run am I missing" answerable — and it is
 * the same TMDB id Jellyfin stores on its BoxSet, so the two sides join exactly rather than by
 * title.
 */
internal fun parseArrCollection(o: JsonObject): ArrCollection? = null
