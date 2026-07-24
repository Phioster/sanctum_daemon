package org.phioster.sanctumd.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One day's aggregated stat metrics. [epochDay] is [java.time.LocalDate.toEpochDay]; [metrics]
 *  maps namespaced keys ("<serviceId>|movies", "<serviceId>|storageMb", "<serviceId>|diskfree:<path>", …)
 *  to a single numeric value. Kept deliberately compact so 90 days fit comfortably in DataStore. */
@Serializable
data class StatsSnapshot(val epochDay: Long, val metrics: Map<String, Long>)

private val Context.statsHistoryDataStore by preferencesDataStore(name = "sanctumd_stats_history")
private val HISTORY_KEY = stringPreferencesKey("history_json")
private val historyJson = Json { ignoreUnknownKeys = true }
private const val MAX_DAYS = 90

/** Persists a rolling window of daily stat snapshots for the trend charts on the stats screen.
 *  One entry per calendar day (a same-day [append] replaces it); the window is capped to
 *  [MAX_DAYS] most-recent days. */
class StatsHistoryStore(private val context: Context) {
    suspend fun read(): List<StatsSnapshot> =
        context.statsHistoryDataStore.data.first()[HISTORY_KEY]?.let {
            runCatching { historyJson.decodeFromString<List<StatsSnapshot>>(it) }.getOrNull()
        }?.sortedBy { it.epochDay } ?: emptyList()

    /** Adds today's snapshot, replacing any existing entry for the same [StatsSnapshot.epochDay],
     *  then trims to the most-recent [MAX_DAYS] days. */
    suspend fun append(snap: StatsSnapshot) {
        val merged = mergeSnapshots(read(), snap, MAX_DAYS)
        context.statsHistoryDataStore.edit { it[HISTORY_KEY] = historyJson.encodeToString(merged) }
    }
}

/** Pure merge used by [StatsHistoryStore.append]: same-day replace, sorted, capped to [maxDays]. */
internal fun mergeSnapshots(existing: List<StatsSnapshot>, snap: StatsSnapshot, maxDays: Int): List<StatsSnapshot> =
    (existing.filter { it.epochDay != snap.epochDay } + snap)
        .sortedBy { it.epochDay }
        .takeLast(maxDays)
