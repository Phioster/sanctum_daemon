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

/** One upcoming release as shown by the calendar (agenda) widget. */
@Serializable
data class CalendarSnap(
    val date: String,       // yyyy-MM-dd
    val title: String,
    val subtitle: String,
    val serviceType: String, // ServiceType.name
    val serviceId: String,
    val hasFile: Boolean,
)

// Keeps the historic `nexarr_` prefix on purpose: renaming it orphans every
// existing install's data (see also the applicationId, which stays too).
private val Context.calendarDataStore by preferencesDataStore(name = "nexarr_calendar")
private val SNAP_KEY = stringPreferencesKey("cal_json")
private val json = Json { ignoreUnknownKeys = true }

/** Caches the next upcoming releases so the calendar widget renders instantly (no network at
 *  render time); refreshed by [org.phioster.sanctumd.widget.CalendarCacheWorker]. */
class CalendarSnapshotStore(private val context: Context) {
    suspend fun read(): List<CalendarSnap> =
        context.calendarDataStore.data.first()[SNAP_KEY]?.let {
            runCatching { json.decodeFromString<List<CalendarSnap>>(it) }.getOrNull()
        } ?: emptyList()

    suspend fun write(list: List<CalendarSnap>) {
        context.calendarDataStore.edit { it[SNAP_KEY] = json.encodeToString(list) }
    }
}
