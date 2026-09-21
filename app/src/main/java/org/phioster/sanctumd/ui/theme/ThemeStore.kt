package org.phioster.sanctumd.ui.theme

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where the chosen theme lives.
 *
 * SharedPreferences rather than DataStore on purpose: the palette has to be readable *synchronously*
 * in three places, in `onCreate` before the first frame (otherwise the app flashes green and then
 * repaints), inside the Glance widgets, and in the notification builders of workers and services.
 * One string does not justify a second asynchronous store.
 */
object ThemeStore {

    private const val PREFS = "sanctumd_theme"
    private const val KEY_PALETTE = "palette_id"
    private const val KEY_BACKGROUND = "background_mode"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun paletteId(context: Context): String =
        prefs(context).getString(KEY_PALETTE, DEFAULT_PALETTE_ID) ?: DEFAULT_PALETTE_ID

    fun backgroundMode(context: Context): BackgroundMode =
        BackgroundMode.from(prefs(context).getString(KEY_BACKGROUND, null))

    /** The preset with the background choice already applied. What the UI should render. */
    fun read(context: Context): Palette =
        paletteById(paletteId(context)).withBackground(backgroundMode(context))

    fun writePreset(context: Context, id: String) {
        prefs(context).edit().putString(KEY_PALETTE, id).apply()
        apply(context)
    }

    fun writeBackground(context: Context, mode: BackgroundMode) {
        prefs(context).edit().putString(KEY_BACKGROUND, mode.id).apply()
        apply(context)
    }

    /** Push the stored theme into the running UI and out to the homescreen widgets. */
    fun apply(context: Context) {
        ThemeState.palette = read(context)
        refreshWidgets(context)
    }

    /**
     * Notification accent: the same dark, desaturated relation the old fixed #14532D had to matrix
     * green, so a coloured strip stays a hint rather than a glare.
     */
    fun notificationColor(context: Context): Int {
        val a = read(context).accent
        fun ch(v: Float) = ((v * 0.35f) * 255).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(ch(a.red), ch(a.green), ch(a.blue))
    }

    /** Widgets cache their own rendering, so they have to be told; otherwise they'd lag by up to 30 min. */
    private fun refreshWidgets(context: Context) {
        val app = context.applicationContext
        val widgets: List<GlanceAppWidget> = listOf(
            org.phioster.sanctumd.widget.StatusWidget(),
            org.phioster.sanctumd.widget.CalendarWidget(),
            org.phioster.sanctumd.widget.ResumeWidget(),
            org.phioster.sanctumd.widget.ShortcutsWidget(),
            org.phioster.sanctumd.widget.ShortcutIconWidget(),
            org.phioster.sanctumd.widget.QuickActionsWidget(),
            org.phioster.sanctumd.widget.StackHealthWidget(),
            org.phioster.sanctumd.widget.QueueTileWidget(),
            org.phioster.sanctumd.widget.SeerrTileWidget(),
            org.phioster.sanctumd.widget.LibraryTileWidget(),
        )
        scope.launch {
            widgets.forEach { runCatching { it.updateAll(app) } }
        }
    }
}
