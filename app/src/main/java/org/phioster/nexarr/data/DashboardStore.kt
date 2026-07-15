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
import org.phioster.nexarr.model.DashTab

private val Context.dashboardDataStore by preferencesDataStore(name = "nexarr_dashboard")
private val TABS_KEY = stringPreferencesKey("tabs_json")
private val json = Json { ignoreUnknownKeys = true }

/** Persists the user's dashboard tabs (widget layout) as JSON in DataStore. */
class DashboardStore(private val context: Context) {

    val tabs: Flow<List<DashTab>> = context.dashboardDataStore.data.map { prefs ->
        prefs[TABS_KEY]?.let { stored ->
            runCatching { json.decodeFromString<List<DashTab>>(stored) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun save(list: List<DashTab>) {
        context.dashboardDataStore.edit { it[TABS_KEY] = json.encodeToString(list) }
    }
}
