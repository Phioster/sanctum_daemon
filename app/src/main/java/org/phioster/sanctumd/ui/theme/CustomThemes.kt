package org.phioster.sanctumd.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A theme made in the editor: the same three colours a preset row shows, packed ARGB. */
@Serializable
data class CustomTheme(
    val id: String,
    val name: String,
    val accent: Long,
    val background: Long,
    val surface: Long,
) {
    fun palette(): Palette {
        val s = Color(surface.toInt())
        return Palette(id, name, Color(accent.toInt()), Color(background.toInt()), s, liftedSurface(s))
    }

    companion object {
        const val ID_PREFIX = "custom-"

        fun from(id: String, name: String, accent: Color, background: Color, surface: Color) =
            CustomTheme(id, name.trim().ifBlank { "My theme" }, accent.argbLong(), background.argbLong(), surface.argbLong())
    }
}

/** Own themes, next to the chosen theme in the same SharedPreferences (read synchronously, see [ThemeStore]). */
internal object CustomThemes {
    private const val KEY = "custom_themes"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("sanctumd_theme", Context.MODE_PRIVATE)

    fun all(context: Context): List<CustomTheme> =
        prefs(context).getString(KEY, null)?.let { runCatching { json.decodeFromString<List<CustomTheme>>(it) }.getOrNull() }.orEmpty()

    fun byId(context: Context, id: String): CustomTheme? = all(context).firstOrNull { it.id == id }

    fun upsert(context: Context, theme: CustomTheme) {
        val list = all(context).filterNot { it.id == theme.id } + theme
        prefs(context).edit().putString(KEY, json.encodeToString(list)).apply()
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().putString(KEY, json.encodeToString(all(context).filterNot { it.id == id })).apply()
    }

    fun newId() = CustomTheme.ID_PREFIX + java.util.UUID.randomUUID().toString().take(8)
}
