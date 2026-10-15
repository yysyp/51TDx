package com.ran.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = SurfaceWarm,
    primaryContainer = CoralSoft,
    onPrimaryContainer = CoralDark,
    secondary = Teal,
    onSecondary = SurfaceWarm,
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF0B5F58),
    tertiary = Amber,
    onTertiary = Ink,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Color(0xFF7A5C00),
    background = Cream,
    onBackground = Ink,
    surface = SurfaceWarm,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3E7DA),
    onSurfaceVariant = InkMuted,
    outline = LineWarm,
    outlineVariant = Color(0xFFE6D7C8)
)

private val DarkColors = darkColorScheme(
    primary = CoralDarkMode,
    onPrimary = Color(0xFF3A1200),
    primaryContainer = Color(0xFF5C2A18),
    onPrimaryContainer = CoralSoft,
    secondary = TealDarkMode,
    onSecondary = Color(0xFF052322),
    secondaryContainer = Color(0xFF14403C),
    onSecondaryContainer = TealSoft,
    tertiary = AmberDark,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF4E3A05),
    onTertiaryContainer = AmberSoft,
    background = DarkBackground,
    onBackground = Color(0xFFF3E9E0),
    surface = DarkSurface,
    onSurface = Color(0xFFF3E9E0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC9B8AA),
    outline = Color(0xFF55493C),
    outlineVariant = Color(0xFF38493C)
)

@Composable
fun RanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = RanTypography,
        content = content
    )
}