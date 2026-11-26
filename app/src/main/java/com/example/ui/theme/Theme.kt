package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalReduceMotion = staticCompositionLocalOf { false }

private val DarkM3Scheme = darkColorScheme(
    primary = DuskAmber,
    onPrimary = DuskOnAmber,
    primaryContainer = DuskPanelElevated,
    onPrimaryContainer = DuskAmber,
    secondary = DuskLilac,
    onSecondary = DuskBackground,
    secondaryContainer = DuskPanel,
    onSecondaryContainer = DuskText,
    tertiary = DuskMint,
    onTertiary = DuskBackground,
    background = DuskBackground,
    onBackground = DuskText,
    surface = DuskPanel,
    onSurface = DuskText,
    surfaceVariant = DuskPanelElevated,
    onSurfaceVariant = DuskMuted,
    outline = DuskLine,
    outlineVariant = DuskLine,
    error = DuskCoral,
    onError = DuskBackground
)

private val LightM3Scheme = lightColorScheme(
    primary = LightAmber,
    onPrimary = LightOnAmber,
    primaryContainer = LightPanelElevated,
    onPrimaryContainer = LightAmber,
    secondary = LightLilac,
    onSecondary = LightPanel,
    secondaryContainer = LightPanel,
    onSecondaryContainer = LightText,
    tertiary = LightMint,
    onTertiary = LightPanel,
    background = LightBackground,
    onBackground = LightText,
    surface = LightPanel,
    onSurface = LightText,
    surfaceVariant = LightPanelElevated,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    outlineVariant = LightLine,
    error = LightCoral,
    onError = LightPanel
)

@Composable
fun DailyLogTheme(
    darkTheme: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkInstrumentColors else LightInstrumentColors
    val colorScheme = if (darkTheme) DarkM3Scheme else LightM3Scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalInstrumentColors provides palette,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DailyLogTypography,
            content = content
        )
    }
}
