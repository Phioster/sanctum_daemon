package org.phioster.nexarr.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.nexarr.model.ServiceConfig

private val Context.dataStore by preferencesDataStore(name = "nexarr_services")
private val SERVICES_KEY = stringPreferencesKey("services_json")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Persists the list of configured services as JSON in DataStore.
 *
 * NOTE: not encrypted yet — API keys / CF tokens are stored in plaintext for
 * now. Encrypting this store is a tracked TODO before any wider release.
 */
class ServiceStore(private val context: Context) {

    val services: Flow<List<ServiceConfig>> = context.dataStore.data.map { prefs ->
        prefs[SERVICES_KEY]?.let { runCatching { json.decodeFromString<List<ServiceConfig>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun save(list: List<ServiceConfig>) {
        context.dataStore.edit { it[SERVICES_KEY] = json.encodeToString(list) }
    }
}
