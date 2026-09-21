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

/** One entry of the "continue watching" widget. */
@Serializable
data class ResumeSnap(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val percent: Int, // 0..100
    val serviceId: String,
)

// Same historic `nexarr_` prefix as the other snapshot stores, renaming orphans existing data.
private val Context.resumeDataStore by preferencesDataStore(name = "nexarr_resume")
private val SNAP_KEY = stringPreferencesKey("resume_json")
private val json = Json { ignoreUnknownKeys = true }

/** Caches "continue watching" so the widget renders without touching the network at draw time;
 *  refreshed by [org.phioster.sanctumd.widget.ResumeCacheWorker]. */
class ResumeSnapshotStore(private val context: Context) {
    suspend fun read(): List<ResumeSnap> =
        context.resumeDataStore.data.first()[SNAP_KEY]?.let {
            runCatching { json.decodeFromString<List<ResumeSnap>>(it) }.getOrNull()
        } ?: emptyList()

    suspend fun write(list: List<ResumeSnap>) {
        context.resumeDataStore.edit { it[SNAP_KEY] = json.encodeToString(list) }
    }
}
