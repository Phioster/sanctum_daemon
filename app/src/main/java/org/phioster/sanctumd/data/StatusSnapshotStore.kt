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

/** One service's last-known status, as shown by the status widget.
 *  [stats] carries the fetched stat pairs (e.g. "Queue"→"3", "Pending"→"5") so the
 *  1×1 stat tiles can read a single number without a second fetch. */
@Serializable
data class StatusSnap(
    val ok: Boolean,
    val label: String,
    val note: String = "",
    val stats: Map<String, String> = emptyMap(),
)

// Keeps the historic `nexarr_` prefix on purpose: renaming it orphans every
// existing install's data (see also the applicationId, which stays too).
private val Context.statusDataStore by preferencesDataStore(name = "nexarr_status")
private val SNAP_KEY = stringPreferencesKey("snap_json")
private val json = Json { ignoreUnknownKeys = true }

/** Caches the last fetched service statuses so the status widget renders instantly
 *  (no network at render time); refreshed by [org.phioster.sanctumd.widget.StatusCacheWorker]. */
class StatusSnapshotStore(private val context: Context) {
    suspend fun read(): Map<String, StatusSnap> =
        context.statusDataStore.data.first()[SNAP_KEY]?.let {
            runCatching { json.decodeFromString<Map<String, StatusSnap>>(it) }.getOrNull()
        } ?: emptyMap()

    suspend fun write(map: Map<String, StatusSnap>) {
        context.statusDataStore.edit { it[SNAP_KEY] = json.encodeToString(map) }
    }
}
