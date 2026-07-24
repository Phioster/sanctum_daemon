package org.phioster.sanctumd.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.sanctumd.model.DownloadEntry

private val Context.downloadDataStore by preferencesDataStore(name = "sanctumd_downloads")
private val ENTRIES_KEY = stringPreferencesKey("entries_json")
private val downloadJson = Json { ignoreUnknownKeys = true }

/** Registry of offline downloads, keyed by itemId. Progress updates flow live to the downloads UI. */
class DownloadStore(private val context: Context) {

    /** Live map itemId -> entry, sorted newest-first when collected as a list by callers. */
    val downloads: Flow<Map<String, DownloadEntry>> = context.downloadDataStore.data.map { prefs ->
        prefs[ENTRIES_KEY]?.let { runCatching { downloadJson.decodeFromString<Map<String, DownloadEntry>>(it) }.getOrNull() }
            ?: emptyMap()
    }

    suspend fun read(): Map<String, DownloadEntry> = downloads.first()

    suspend fun put(entry: DownloadEntry) {
        context.downloadDataStore.edit { prefs ->
            val map = prefs[ENTRIES_KEY]?.let { runCatching { downloadJson.decodeFromString<Map<String, DownloadEntry>>(it) }.getOrNull() } ?: emptyMap()
            prefs[ENTRIES_KEY] = downloadJson.encodeToString(map + (entry.itemId to entry))
        }
    }

    /** Atomically transform one entry (no-op if it's gone — e.g. deleted mid-download). */
    suspend fun update(itemId: String, transform: (DownloadEntry) -> DownloadEntry) {
        context.downloadDataStore.edit { prefs ->
            val map = prefs[ENTRIES_KEY]?.let { runCatching { downloadJson.decodeFromString<Map<String, DownloadEntry>>(it) }.getOrNull() } ?: emptyMap()
            val cur = map[itemId] ?: return@edit
            prefs[ENTRIES_KEY] = downloadJson.encodeToString(map + (itemId to transform(cur)))
        }
    }

    suspend fun remove(itemId: String) {
        context.downloadDataStore.edit { prefs ->
            val map = prefs[ENTRIES_KEY]?.let { runCatching { downloadJson.decodeFromString<Map<String, DownloadEntry>>(it) }.getOrNull() } ?: emptyMap()
            prefs[ENTRIES_KEY] = downloadJson.encodeToString(map - itemId)
        }
    }
}
