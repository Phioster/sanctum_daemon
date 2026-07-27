package org.phioster.sanctumd.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phioster.sanctumd.model.DashTab

// Keeps the historic `nexarr_` prefix on purpose: renaming it orphans every
// existing install's data (see also the applicationId, which stays too).
private val Context.dashboardDataStore by preferencesDataStore(name = "nexarr_dashboard")
private val TABS_KEY = stringPreferencesKey("tabs_json")
private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_done")
private val HIDE_ADULT_KEY = booleanPreferencesKey("hide_adult")
private val SWIPE_TABS_KEY = booleanPreferencesKey("swipe_tabs")
private val SWIPE_DRAWER_KEY = booleanPreferencesKey("swipe_drawer")
private val DRAWER_BAND_KEY = floatPreferencesKey("drawer_band")
private val SAFE_MODE_KEY = booleanPreferencesKey("safe_mode")
private val DOWNLOADS_WIFI_ONLY_KEY = booleanPreferencesKey("downloads_wifi_only")
private val HIDDEN_LIBRARIES_KEY = stringPreferencesKey("hidden_libraries_json")
private val MEDIA_ROW_STYLES_KEY = stringPreferencesKey("media_row_styles_json")
private val PLAYER_SWIPE_MAG_KEY = floatPreferencesKey("player_swipe_magnitude")
private val PLAYER_SWIPE_MARGIN_KEY = floatPreferencesKey("player_swipe_margin")
private val AUDIO_LANG_KEY = stringPreferencesKey("player_audio_lang")
private val SUB_LANG_KEY = stringPreferencesKey("player_sub_lang")
private val SUB_MODE_KEY = stringPreferencesKey("player_sub_mode")
private val SUB_SCALE_KEY = floatPreferencesKey("player_sub_scale")
private val AUTOPLAY_NEXT_KEY = booleanPreferencesKey("player_autoplay_next")
private val AUTO_SKIP_KEY = booleanPreferencesKey("player_auto_skip_segments")
private val ASK_RESUME_KEY = booleanPreferencesKey("player_ask_resume")
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

    // Dashboard gestures: swipe left/right in the upper area switches tabs; a right-swipe in the
    // bottom band opens the Services drawer. [drawerBand] = band height as fraction from the bottom (max 0.5).
    val swipeTabs: Flow<Boolean> = context.dashboardDataStore.data.map { it[SWIPE_TABS_KEY] ?: true }
    val swipeDrawer: Flow<Boolean> = context.dashboardDataStore.data.map { it[SWIPE_DRAWER_KEY] ?: true }
    val drawerBand: Flow<Float> = context.dashboardDataStore.data.map { it[DRAWER_BAND_KEY] ?: 0.4f }

    suspend fun setSwipeTabs(enabled: Boolean) {
        context.dashboardDataStore.edit { it[SWIPE_TABS_KEY] = enabled }
    }
    suspend fun setSwipeDrawer(enabled: Boolean) {
        context.dashboardDataStore.edit { it[SWIPE_DRAWER_KEY] = enabled }
    }
    suspend fun setDrawerBand(fraction: Float) {
        context.dashboardDataStore.edit { it[DRAWER_BAND_KEY] = fraction.coerceIn(0f, 0.5f) }
    }

    // Player brightness/volume swipe tuning: sensitivity multiplier + a top/bottom edge dead-zone
    // (fraction of screen height where a vertical swipe won't start).
    val playerSwipeMagnitude: Flow<Float> = context.dashboardDataStore.data.map { it[PLAYER_SWIPE_MAG_KEY] ?: 1.5f }
    val playerSwipeMargin: Flow<Float> = context.dashboardDataStore.data.map { it[PLAYER_SWIPE_MARGIN_KEY] ?: 0f }
    suspend fun setPlayerSwipeMagnitude(v: Float) {
        context.dashboardDataStore.edit { it[PLAYER_SWIPE_MAG_KEY] = v.coerceIn(0.3f, 3f) }
    }
    suspend fun setPlayerSwipeMargin(v: Float) {
        context.dashboardDataStore.edit { it[PLAYER_SWIPE_MARGIN_KEY] = v.coerceIn(0f, 0.3f) }
    }

    // ── Playback preferences ──────────────────────────────────────────────────────────────────
    // Language codes are ISO-639 ("de"/"en"/…); [subtitleMode] is "forced" (only a forced track for
    // the chosen language, else nothing), "any" (forced first, then a normal track) or "off".
    val audioLanguage: Flow<String> = context.dashboardDataStore.data.map { it[AUDIO_LANG_KEY] ?: "de" }
    val subtitleLanguage: Flow<String> = context.dashboardDataStore.data.map { it[SUB_LANG_KEY] ?: "de" }
    val subtitleMode: Flow<String> = context.dashboardDataStore.data.map { it[SUB_MODE_KEY] ?: "forced" }
    val subtitleScale: Flow<Float> = context.dashboardDataStore.data.map { it[SUB_SCALE_KEY] ?: 1f }
    val autoplayNext: Flow<Boolean> = context.dashboardDataStore.data.map { it[AUTOPLAY_NEXT_KEY] ?: true }
    val autoSkipSegments: Flow<Boolean> = context.dashboardDataStore.data.map { it[AUTO_SKIP_KEY] ?: false }
    val askResume: Flow<Boolean> = context.dashboardDataStore.data.map { it[ASK_RESUME_KEY] ?: true }
    suspend fun setAudioLanguage(v: String) { context.dashboardDataStore.edit { it[AUDIO_LANG_KEY] = v } }
    suspend fun setSubtitleLanguage(v: String) { context.dashboardDataStore.edit { it[SUB_LANG_KEY] = v } }
    suspend fun setSubtitleMode(v: String) { context.dashboardDataStore.edit { it[SUB_MODE_KEY] = v } }
    suspend fun setSubtitleScale(v: Float) { context.dashboardDataStore.edit { it[SUB_SCALE_KEY] = v.coerceIn(0.5f, 2.5f) } }
    suspend fun setAutoplayNext(v: Boolean) { context.dashboardDataStore.edit { it[AUTOPLAY_NEXT_KEY] = v } }
    suspend fun setAutoSkipSegments(v: Boolean) { context.dashboardDataStore.edit { it[AUTO_SKIP_KEY] = v } }
    suspend fun setAskResume(v: Boolean) { context.dashboardDataStore.edit { it[ASK_RESUME_KEY] = v } }

    // Offline downloads: only fetch on un-metered Wi-Fi when on.
    val downloadsWifiOnly: Flow<Boolean> = context.dashboardDataStore.data.map { it[DOWNLOADS_WIFI_ONLY_KEY] ?: false }
    suspend fun setDownloadsWifiOnly(enabled: Boolean) {
        context.dashboardDataStore.edit { it[DOWNLOADS_WIFI_ONLY_KEY] = enabled }
    }

    // Per Jellyfin service: library-view ids the user chose to hide from the media tab (e.g. Live TV).
    val hiddenLibraries: Flow<Map<String, List<String>>> = context.dashboardDataStore.data.map { prefs ->
        prefs[HIDDEN_LIBRARIES_KEY]?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() } ?: emptyMap()
    }
    suspend fun setHiddenLibraries(serviceId: String, hidden: List<String>) {
        context.dashboardDataStore.edit { prefs ->
            val map = prefs[HIDDEN_LIBRARIES_KEY]?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() } ?: emptyMap()
            prefs[HIDDEN_LIBRARIES_KEY] = json.encodeToString(map + (serviceId to hidden))
        }
    }

    // Per-row styling of the Jellyfin Media home, keyed by row id ("resume"/"recent"/"libraries").
    val mediaRowStyles: Flow<Map<String, org.phioster.sanctumd.model.MediaRowStyle>> = context.dashboardDataStore.data.map { prefs ->
        prefs[MEDIA_ROW_STYLES_KEY]?.let { runCatching { json.decodeFromString<Map<String, org.phioster.sanctumd.model.MediaRowStyle>>(it) }.getOrNull() } ?: emptyMap()
    }
    suspend fun setMediaRowStyle(rowKey: String, style: org.phioster.sanctumd.model.MediaRowStyle) {
        context.dashboardDataStore.edit { prefs ->
            val map = prefs[MEDIA_ROW_STYLES_KEY]?.let { runCatching { json.decodeFromString<Map<String, org.phioster.sanctumd.model.MediaRowStyle>>(it) }.getOrNull() } ?: emptyMap()
            prefs[MEDIA_ROW_STYLES_KEY] = json.encodeToString(map + (rowKey to style))
        }
    }

    /** Safe mode: block every call that would change something on a server. */
    val safeMode: Flow<Boolean> = context.dashboardDataStore.data.map { it[SAFE_MODE_KEY] ?: false }

    suspend fun setSafeMode(enabled: Boolean) {
        context.dashboardDataStore.edit { it[SAFE_MODE_KEY] = enabled }
    }
}
