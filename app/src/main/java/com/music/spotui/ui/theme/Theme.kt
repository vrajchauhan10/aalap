package com.music.spotui.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.music.spotui.data.preferences.ThemeMode
import com.music.spotui.data.preferences.getThemeMode

/** Live theme choice, so Settings can switch light/dark without restarting. */
object ThemeState {
    private var loaded = false
    var mode by mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: android.content.Context) {
        if (!loaded) { mode = getThemeMode(context); loaded = true }
    }

    fun set(context: android.content.Context, newMode: ThemeMode) {
        com.music.spotui.data.preferences.setThemeMode(context, newMode)
        mode = newMode
    }
}

// The scheme only drives Material defaults (dialogs, sheets, switches); screens
// draw from the Aalap tokens in Color.kt, which flip with [AalapPalette.isLight].
private fun schemeFor(light: Boolean) = if (light) lightColorScheme(
    primary = Saffron, onPrimary = OnSaffron,
    primaryContainer = SurfaceHigh, onPrimaryContainer = Saffron,
    secondary = SaffronGold, onSecondary = OnSaffron, tertiary = SaffronDeep,
    background = AppBackground, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh, onSurfaceVariant = TextMuted,
    surfaceContainer = Surface, surfaceContainerHigh = SurfaceHigh, surfaceContainerLow = AppBackground,
    outline = TextMuted.copy(alpha = 0.4f),
) else darkColorScheme(
    primary = Saffron, onPrimary = OnSaffron,
    primaryContainer = SurfaceHigh, onPrimaryContainer = SaffronGold,
    secondary = SaffronGold, onSecondary = OnSaffron, tertiary = SaffronDeep,
    background = AppBackground, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh, onSurfaceVariant = TextMuted,
    surfaceContainer = Surface, surfaceContainerHigh = SurfaceHigh, surfaceContainerLow = AppBackground,
    outline = TextMuted.copy(alpha = 0.4f),
)

@Composable
fun SpotuiTheme(
    content: @Composable () -> Unit
) {
    ThemeState.load(LocalContext.current)
    val light = when (ThemeState.mode) {
        ThemeMode.LIGHT -> true
        ThemeMode.DARK -> false
        ThemeMode.SYSTEM -> !isSystemInDarkTheme()
    }
    AalapPalette.isLight = light

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(AppBackground.toArgb()))
            // Dark status/nav-bar icons on the cream canvas, light ones on indigo.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    MaterialTheme(
        colorScheme = schemeFor(light),
        typography = Typography,
    ) {
        // Bare Text() uses LocalTextStyle, which MaterialTheme does NOT derive
        // from the typography — so provide Poppins (and the theme's text colour)
        // as the global default so every screen picks it up.
        androidx.compose.material3.ProvideTextStyle(
            value = androidx.compose.material3.LocalTextStyle.current.copy(
                fontFamily = Poppins,
                letterSpacing = (-0.2).sp,
                color = TextPrimary,
            ),
            content = content,
        )
    }
}
