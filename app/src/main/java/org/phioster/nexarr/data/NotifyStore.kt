package org.phioster.nexarr.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.nexarr.model.NotifySettings

private val Context.notifyDataStore by preferencesDataStore(name = "nexarr_notify")
private val SETTINGS_KEY = stringPreferencesKey("settings_json")
private val SEEN_KEY = stringPreferencesKey("seen_json")
private val NTFY_LAST_TIME_KEY = longPreferencesKey("ntfy_last_time")
private val NTFY_LAST_ID_KEY = stringPreferencesKey("ntfy_last_id")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists notification settings plus the "already seen" keys per service/category,
 * so the background worker only notifies about genuinely new items.
 */
class NotifyStore(private val context: Context) {

    val settings: Flow<NotifySettings> = context.notifyDataStore.data.map { prefs ->
        prefs[SETTINGS_KEY]?.let { runCatching { json.decodeFromString<NotifySettings>(it) }.getOrNull() } ?: NotifySettings()
    }

    suspend fun currentSettings(): NotifySettings = settings.first()

    suspend fun save(s: NotifySettings) {
        context.notifyDataStore.edit { it[SETTINGS_KEY] = json.encodeToString(s) }
    }

    suspend fun seen(): Map<String, List<String>> =
        context.notifyDataStore.data.first()[SEEN_KEY]?.let {
            runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull()
        } ?: emptyMap()

    suspend fun saveSeen(m: Map<String, List<String>>) {
        context.notifyDataStore.edit { it[SEEN_KEY] = json.encodeToString(m) }
    }

    /** Timestamp/id of the last ntfy message shown, so reconnects can catch up via ?since= without duplicates. */
    suspend fun ntfyCursor(): Pair<Long, String> = context.notifyDataStore.data.first().let {
        (it[NTFY_LAST_TIME_KEY] ?: 0L) to (it[NTFY_LAST_ID_KEY] ?: "")
    }

    suspend fun saveNtfyCursor(time: Long, id: String) {
        context.notifyDataStore.edit {
            it[NTFY_LAST_TIME_KEY] = time
            it[NTFY_LAST_ID_KEY] = id
        }
    }
}
