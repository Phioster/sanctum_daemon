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
import org.phioster.nexarr.security.Crypto

private val Context.notifyDataStore by preferencesDataStore(name = "nexarr_notify")
private val SETTINGS_KEY = stringPreferencesKey("settings_json")
private val SEEN_KEY = stringPreferencesKey("seen_json")
private val NTFY_LAST_TIME_KEY = longPreferencesKey("ntfy_last_time")
private val NTFY_LAST_ID_KEY = stringPreferencesKey("ntfy_last_id") // legacy single-id cursor
private val NTFY_RECENT_IDS_KEY = stringPreferencesKey("ntfy_recent_ids")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists notification settings plus the "already seen" keys per service/category,
 * so the background worker only notifies about genuinely new items.
 */
class NotifyStore(private val context: Context) {

    // The settings blob carries the ntfy token, so it is encrypted like the services
    // blob; Crypto.decrypt passes legacy plaintext through, migrated on the next save.
    val settings: Flow<NotifySettings> = context.notifyDataStore.data.map { prefs ->
        prefs[SETTINGS_KEY]?.let {
            runCatching { json.decodeFromString<NotifySettings>(Crypto.decrypt(it)) }.getOrNull()
        } ?: NotifySettings()
    }

    suspend fun currentSettings(): NotifySettings = settings.first()

    suspend fun save(s: NotifySettings) {
        val encrypted = Crypto.encrypt(json.encodeToString(s))
        context.notifyDataStore.edit { it[SETTINGS_KEY] = encrypted }
    }

    suspend fun seen(): Map<String, List<String>> =
        context.notifyDataStore.data.first()[SEEN_KEY]?.let {
            runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull()
        } ?: emptyMap()

    suspend fun saveSeen(m: Map<String, List<String>>) {
        context.notifyDataStore.edit { it[SEEN_KEY] = json.encodeToString(m) }
    }

    /** Timestamp of the last ntfy message + recently seen ids, so reconnects can
     *  catch up via ?since= without re-notifying messages from the same second. */
    suspend fun ntfyCursor(): Pair<Long, List<String>> = context.notifyDataStore.data.first().let { prefs ->
        val recent = prefs[NTFY_RECENT_IDS_KEY]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: prefs[NTFY_LAST_ID_KEY]?.let { listOf(it) } // migrate the legacy single-id cursor
            ?: emptyList()
        (prefs[NTFY_LAST_TIME_KEY] ?: 0L) to recent
    }

    suspend fun saveNtfyCursor(time: Long, recentIds: List<String>) {
        context.notifyDataStore.edit {
            it[NTFY_LAST_TIME_KEY] = time
            it[NTFY_RECENT_IDS_KEY] = json.encodeToString(recentIds.takeLast(20))
        }
    }
}
