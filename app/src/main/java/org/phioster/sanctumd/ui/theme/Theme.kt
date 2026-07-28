package org.phioster.sanctumd.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * One theme.
 *
 * [accent] is everything you see: text, icons, buttons, markers. The three surface levels carry the
 * theme's base colour — that tint is where the variation between presets comes from, not from a
 * second ink colour (tried that, it only made buttons disagree with their labels).
 */
data class Palette(
    val id: String,
    val label: String,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHi: Color,
)

/**
 * Fixed presets — no free colour picker on purpose.
 *
 * NB for future additions: [Palette.background] doubles as the *ink* on the accent (chips, buttons,
 * FAB), so it has to stay dark enough to read on a bright accent. Nord was closest to that limit.
 */
val PALETTES: List<Palette> = listOf(
    Palette("matrix", "Matrix", Color(0xFF00FF41), Color(0xFF141619), Color(0xFF1E2126), Color(0xFF262A30)),
    Palette("ae86", "AE86 Panda", Color(0xFFEDEDED), Color(0xFF0A0A0A), Color(0xFF16181A), Color(0xFF202326)),
    Palette("amber", "Amber CRT", Color(0xFFFFB000), Color(0xFF14100C), Color(0xFF1E1810), Color(0xFF282016)),
    Palette("ice", "Ice", Color(0xFF7DD3FC), Color(0xFF0B1220), Color(0xFF121C2E), Color(0xFF1A2740)),
    // Tinted surfaces: the base colour lives in the background and the cards, the accent stays the
    // ink. "Purple with green accents" means green text and green buttons on purple furniture.
    Palette(
        "synthwave", "Synthwave",
        accent = Color(0xFF39FF14), background = Color(0xFF1B0733),
        surface = Color(0xFF2A0F52), surfaceHi = Color(0xFF38156B),
    ),
    Palette(
        "neon", "Japan Neon",
        accent = Color(0xFF00D5FF), background = Color(0xFF2A0619),
        surface = Color(0xFF400C2C), surfaceHi = Color(0xFF4F1038),
    ),
    Palette(
        "tron", "Tron",
        accent = Color(0xFFFF7A1A), background = Color(0xFF04121F),
        surface = Color(0xFF0A2236), surfaceHi = Color(0xFF0F2F49),
    ),
    // The violet is lifted from #9B5DE5: as a button fill it was fine, as body text on olive it
    // sat too close to the background to read comfortably.
    Palette(
        "toxic", "Toxic",
        accent = Color(0xFFBE8CFF), background = Color(0xFF10160A),
        surface = Color(0xFF1B2410), surfaceHi = Color(0xFF253116),
    ),
)

const val DEFAULT_PALETTE_ID = "matrix"

fun paletteById(id: String?): Palette = PALETTES.firstOrNull { it.id == id } ?: PALETTES.first()

/** The background can be chosen independently of the preset's own. */
enum class BackgroundMode(val id: String, val label: String) {
    PRESET("preset", "preset"),
    OLED("oled", "OLED black"),
    ANTHRACITE("anthracite", "anthracite"),
    ;

    companion object {
        fun from(id: String?): BackgroundMode = entries.firstOrNull { it.id == id } ?: PRESET
    }
}

private fun Color.mixWith(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = 1f,
)

/**
 * Applies the background choice on top of a preset, keeping its accent.
 *
 * OLED deliberately does *not* flatten the card surfaces to pure black: they stay a shade above it
 * (and keep a hint of the preset's tint) so cards remain distinguishable, while the large areas —
 * the ones that actually glow in a dark room and cost power — are true black.
 */
fun Palette.withBackground(mode: BackgroundMode): Palette = when (mode) {
    BackgroundMode.PRESET -> this
    BackgroundMode.OLED -> copy(
        background = Color(0xFF000000),
        surface = surface.mixWith(Color.Black, 0.60f),
        surfaceHi = surfaceHi.mixWith(Color.Black, 0.50f),
    )
    BackgroundMode.ANTHRACITE -> copy(
        background = Color(0xFF141619),
        surface = Color(0xFF1E2126),
        surfaceHi = Color(0xFF262A30),
    )
}

/** Secondary/muted ink derived from the accent — used where a fixed muted green used to sit. */
internal fun Palette.dimInk(): Color = accent.mixWith(background, 0.45f)

/** The live palette. Seeded from ThemeStore before the first frame; changing it repaints the app. */
object ThemeState {
    var palette by mutableStateOf(paletteById(DEFAULT_PALETTE_ID))
}

// These four keep their historic names although they now read the active palette: they are
// referenced ~900× across the UI, and renaming them would be churn without a payoff. Read them as
// "the accent" (MatrixGreen) and the three surface levels. `Black` is the base *and* the ink on the
// accent, which is why the presets keep their background dark.
internal val MatrixGreen: Color get() = ThemeState.palette.accent
internal val Black: Color get() = ThemeState.palette.background
internal val Surface: Color get() = ThemeState.palette.surface
internal val SurfaceHi: Color get() = ThemeState.palette.surfaceHi

// Errors stay red in every theme — that is the one colour that must not blend in.
internal val ErrRed = Color(0xFFFF5555)
internal val Mono = FontFamily.Monospace

// A getter, not a value: as a top-level `val` this would capture the colours once at class-init and
// freeze the app on whatever theme happened to be active then.
internal val SanctumdColors
    get() = darkColorScheme(
        primary = MatrixGreen,
        onPrimary = Black,
        background = Black,
        onBackground = MatrixGreen,
        surface = Surface,
        onSurface = MatrixGreen,
        surfaceVariant = SurfaceHi,
        onSurfaceVariant = MatrixGreen,
        outline = MatrixGreen.copy(alpha = 0.4f),
    )
