package org.phioster.sanctumd.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.security.Crypto

// Keeps the historic `nexarr_` prefix on purpose: renaming it orphans every
// existing install's data (see also the applicationId, which stays too).
private val Context.dataStore by preferencesDataStore(name = "nexarr_services")
private val SERVICES_KEY = stringPreferencesKey("services_json")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists the list of configured services as JSON in DataStore, encrypted with
 * AES-256-GCM via [Crypto] (key in the Android Keystore). The blob holds API
 * keys, CF tokens and passwords.
 */
class ServiceStore(private val context: Context) {

    /** True when a stored blob is encrypted but can't be decrypted, the data
     *  was restored from another device's backup and its Keystore key is gone. */
    private val _decryptFailed = MutableStateFlow(false)
    val decryptFailed: StateFlow<Boolean> = _decryptFailed.asStateFlow()

    /** What the screens show: the demo's services while demo mode is on, else the user's. */
    val services: Flow<List<ServiceConfig>> get() =
        kotlinx.coroutines.flow.combine(realServices, org.phioster.sanctumd.demo.DemoMode.enabled) { real, demo ->
            if (demo) org.phioster.sanctumd.demo.DemoMode.services else real
        }

    /** The user's own services, always; background work and widgets use these. */
    val realServices: Flow<List<ServiceConfig>> = context.dataStore.data.map { prefs ->
        prefs[SERVICES_KEY]?.let { stored ->
            val plain = runCatching { Crypto.decrypt(stored) }.getOrElse {
                _decryptFailed.value = true
                return@let null
            }
            // Decryptable but the JSON won't parse (corrupt / incompatible downgrade):
            // surface it via the same recovery banner instead of a silently-empty list.
            runCatching { json.decodeFromString<List<ServiceConfig>>(plain) }.getOrElse {
                _decryptFailed.value = true
                null
            }
        } ?: emptyList()
    }

    suspend fun save(list: List<ServiceConfig>) {
        // An edit in demo mode would replace the user's services with the demo's.
        if (org.phioster.sanctumd.demo.DemoMode.isOn) return
        val encrypted = Crypto.encrypt(json.encodeToString(list))
        context.dataStore.edit { it[SERVICES_KEY] = encrypted }
        _decryptFailed.value = false
    }

    /** Drop an unreadable blob (after a cross-device restore) so the user can start over. */
    suspend fun clearUnreadable() {
        context.dataStore.edit { it.remove(SERVICES_KEY) }
        _decryptFailed.value = false
    }
}
