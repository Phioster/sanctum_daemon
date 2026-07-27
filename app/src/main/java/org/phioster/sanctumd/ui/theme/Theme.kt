package org.phioster.sanctumd.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

internal val MatrixGreen = Color(0xFF00FF41)

// The app-wide base is a very dark anthracite (not pure black — that read as oppressive). The name
// `Black` is kept because it's referenced ~150× as both the background AND the ink on the green accent;
// as ink on bright green this anthracite is still maximally legible, and scrims just tint slightly.
internal val Black = Color(0xFF141619)
internal val Surface = Color(0xFF1E2126) // cards — a clear step lighter than the base
internal val SurfaceHi = Color(0xFF262A30) // elevated surfaces: chips, buttons, config sheets
internal val ErrRed = Color(0xFFFF5555)
internal val Mono = FontFamily.Monospace

internal val SanctumdColors = darkColorScheme(
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
