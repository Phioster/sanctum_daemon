package org.phioster.nexarr.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_done")
private val HIDE_ADULT_KEY = booleanPreferencesKey("hide_adult")
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

    /** Whether the biometric app lock is enabled. */
    val appLock: Flow<Boolean> = context.dashboardDataStore.data.map { it[APP_LOCK_KEY] ?: false }

    suspend fun setAppLock(enabled: Boolean) {
        context.dashboardDataStore.edit { it[APP_LOCK_KEY] = enabled }
    }

    /** Whether the first-run onboarding has been completed/dismissed. */
    val onboardingDone: Flow<Boolean> = context.dashboardDataStore.data.map { it[ONBOARDING_KEY] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dashboardDataStore.edit { it[ONBOARDING_KEY] = done }
    }

    /** Hide adult / XXX content across Jellyfin browsing and Seerr discovery. */
    val hideAdult: Flow<Boolean> = context.dashboardDataStore.data.map { it[HIDE_ADULT_KEY] ?: false }

    suspend fun setHideAdult(enabled: Boolean) {
        context.dashboardDataStore.edit { it[HIDE_ADULT_KEY] = enabled }
    }
}
