// FILE: app/src/main/java/com/calnyx/app/ui/theme/Color.kt
package com.calnyx.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme
// (2026-11-20): the surface / text slots are aligned to the
// CalNyx B/W design the screens already use (off-white bg,
// white cards, near-black text), so a screen that reads the
// theme looks EXACTLY the same as it did with its hardcoded
// colours. Primary / secondary / tertiary / error are untouched
// (purple primary stays, as before).
val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFCDB2E8)
val md_theme_light_onPrimaryContainer = Color(0xFF2A1546)
val md_theme_light_secondary = Color(0xFF4A6363)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDCCFF2)
val md_theme_light_onSecondaryContainer = Color(0xFF241A2E)
val md_theme_light_tertiary = Color(0xFF4B607C)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFD3E4FF)
val md_theme_light_onTertiaryContainer = Color(0xFF041C35)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFF6F4EF)
val md_theme_light_onBackground = Color(0xFF0E0E0E)
val md_theme_light_surface = Color(0xFFF6F4EF)
val md_theme_light_onSurface = Color(0xFF0E0E0E)
val md_theme_light_surfaceVariant = Color(0xFFEDEBE6)
val md_theme_light_onSurfaceVariant = Color(0xFF6B6B6B)
val md_theme_light_outline = Color(0xFFB9B5AD)
// Surface containers (dialogs, sheets, menus that use the
// theme default): cards and raised surfaces are white in light.
val md_theme_light_surfaceContainerLowest = Color(0xFFF6F4EF)
val md_theme_light_surfaceContainerLow = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainer = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerHigh = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerHighest = Color(0xFFEDEBE6)

// Dark Theme — neutral grays only (user's 3 colours, 2026-11-20).
// #1F1F1F = page background, #333333 = cards / surfaces,
// #434343 = raised surfaces + outlines. No hue anywhere —
// the whole dark mode is black / white / grey, like the rest
// of CalNyx. Text is soft white / light grey. Primary is white
// (a white button on dark grey is the monochrome accent).
// Error red is kept as standard Material dark error, so
// errors still read as errors.
val md_theme_dark_primary = Color(0xFFFFFFFF)
val md_theme_dark_onPrimary = Color(0xFF1F1F1F)
val md_theme_dark_primaryContainer = Color(0xFF434343)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFFFFF)
val md_theme_dark_secondary = Color(0xFFD4D4D4)
val md_theme_dark_onSecondary = Color(0xFF1F1F1F)
val md_theme_dark_secondaryContainer = Color(0xFF333333)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFFFFF)
val md_theme_dark_tertiary = Color(0xFFBDBDBD)
val md_theme_dark_onTertiary = Color(0xFF1F1F1F)
val md_theme_dark_tertiaryContainer = Color(0xFF434343)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFFFFF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1F1F1F)
val md_theme_dark_onBackground = Color(0xFFF5F5F5)
val md_theme_dark_surface = Color(0xFF1F1F1F)
val md_theme_dark_onSurface = Color(0xFFF5F5F5)
val md_theme_dark_surfaceVariant = Color(0xFF333333)
val md_theme_dark_onSurfaceVariant = Color(0xFFC7C7C7)
val md_theme_dark_outline = Color(0xFF434343)
// Surface containers (dialogs, sheets, menus that use the
// theme default): the same 3 greys, darkest to lightest.
val md_theme_dark_surfaceContainerLowest = Color(0xFF1F1F1F)
val md_theme_dark_surfaceContainerLow = Color(0xFF333333)
val md_theme_dark_surfaceContainer = Color(0xFF333333)
val md_theme_dark_surfaceContainerHigh = Color(0xFF434343)
val md_theme_dark_surfaceContainerHighest = Color(0xFF434343)

// App accents — the user's explicit exceptions to B/W only.
// Edit is green, Delete is red. Screens import these from here,
// so the green / red live in one place too. Add future accents
// here the same way: one val in this file, use it anywhere.
val CalNyxGreen = Color(0xFF2E7D32)
val CalNyxRed = Color(0xFFC62828)

// SCIENTIFIC CALCULATOR (2026-11-20): the ONE screen where colour
// is allowed (user's explicit exception to B/W-only). Each key
// style has an explicit dark + light value, so both themes stay
// right. The values are the redesign's exact colours, only moved
// here so they live in one place: edit once, whole app changes.
val SciFuncContainerDark = Color(0xFF25313F)
val SciFuncContainerLight = Color(0xFFDCE7F5)
val SciFuncContentDark = Color(0xFFA9C8E8)
val SciFuncContentLight = Color(0xFF1B3A5D)
val SciDigitContainerDark = Color(0xFF26262B)
val SciDigitContainerLight = Color(0xFFF0EEE9)
val SciPurpleContainerDark = Color(0xFF322B4D)
val SciPurpleContainerLight = Color(0xFFE4DFF5)
val SciPurpleContentDark = Color(0xFFC9BFF0)
val SciPurpleContentLight = Color(0xFF4A3A8C)
val SciAmber = Color(0xFFFFB300)
val SciRed = Color(0xFFE53935)
val SciGreen = Color(0xFF34A853)
