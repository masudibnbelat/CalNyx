// FILE: app/src/main/java/com/calnyx/app/ui/theme/Theme.kt
// REPLACE the whole file. Only CalNyxTheme changes: it now starts
// from the SAVED theme (ThemePrefs) instead of the system default,
// so reopening the app shows the mode the user last picked (light
// stays light, dark stays dark) instead of falling back to dark.
// The two colour schemes are unchanged.
// DARK THEME (2026-11-20): the dark scheme now also wires the
// surface container tones, so dialogs / sheets / menus that use
// the theme default get the same 3 greys (#1F1F1F / #333333 /
// #434343) instead of Material's default dark tones.
// LIGHT THEME (2026-11-20): the surface / text slots are aligned
// to the CalNyx B/W design (off-white bg, white cards, near-black
// text) and the surface containers are wired too, so a screen
// that reads the theme looks exactly as it did before.
package com.calnyx.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.calnyx.app.core.ThemePrefs

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)

@Composable
fun CalNyxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // THEME PERSISTENCE (2026-11-06): the mode the user last picked
    // is saved in ThemePrefs and restored here, so the FIRST frame
    // already shows the saved mode (light stays light, dark stays
    // dark) instead of falling back to the system/default dark.
    // Live toggles still come through the darkTheme parameter
    // (MainActivity flips it); this state follows it and saves
    // every change, so the next launch restores it. The NavGraph
    // does the same sync for the app-level state — both read and
    // write the same one saved value, so they can never disagree.
    var effectiveDark by remember { mutableStateOf(ThemePrefs.isDarkTheme(context, darkTheme)) }
    var firstPass by remember { mutableStateOf(true) }
    LaunchedEffect(darkTheme) {
        if (firstPass) {
            // Startup: keep the saved value. Do NOT overwrite the
            // save with the parameter (which is still the parent's
            // default at this point).
            firstPass = false
            return@LaunchedEffect
        }
        if (darkTheme != effectiveDark) {
            effectiveDark = darkTheme
            ThemePrefs.setDarkTheme(context, darkTheme)
        }
    }
    val colorScheme = if (effectiveDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
