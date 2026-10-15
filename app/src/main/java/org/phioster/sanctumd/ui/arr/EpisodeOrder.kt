package org.phioster.sanctumd.ui.arr

import android.content.Context

/**
 * Whether a Sonarr season lists episode 1 first or the newest first. Remembered for every
 * series, so it is set once rather than on each screen.
 *
 * SharedPreferences for the same reason as [org.phioster.sanctumd.ui.theme.ThemeStore]: one
 * flag, read synchronously when the screen opens.
 */
internal object EpisodeOrder {
    private const val PREFS = "sanctumd_ui"
    private const val KEY_OLDEST_FIRST = "episodes_oldest_first"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun oldestFirst(context: Context): Boolean = prefs(context).getBoolean(KEY_OLDEST_FIRST, true)

    fun setOldestFirst(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OLDEST_FIRST, value).apply()
    }
}
