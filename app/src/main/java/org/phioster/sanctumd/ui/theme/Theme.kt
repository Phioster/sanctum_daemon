package org.phioster.sanctumd.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

internal val MatrixGreen = Color(0xFF00FF41)
internal val Black = Color(0xFF000000)
internal val Surface = Color(0xFF0A0A0A)
internal val ErrRed = Color(0xFFFF5555)
internal val Mono = FontFamily.Monospace

internal val SanctumdColors = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = Black,
    background = Black,
    onBackground = MatrixGreen,
    surface = Surface,
    onSurface = MatrixGreen,
    surfaceVariant = Surface,
    onSurfaceVariant = MatrixGreen,
    outline = MatrixGreen.copy(alpha = 0.4f),
)
