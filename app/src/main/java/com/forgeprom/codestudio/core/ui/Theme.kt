package com.forgeprom.codestudio.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Eye-Comforting Nordic Slate & Ergonomic Teal-Jade Palette (Low-Glare 3D Studio Theme)
val PurpleBrandPrimary = Color(0xFF0D8A7F) // Calming Teal-Jade Primary
val PurpleBrandAccent = Color(0xFF54D9CC)  // Soft Bioluminescent Teal Accent
val ObsidianDarkBg = Color(0xFF0D141C)     // Deep Low-Glare Nordic Slate Canvas
val ObsidianDarkSurface = Color(0xFF152230)// Elevated 3D Matte Slate Surface
val TextPrimaryDark = Color(0xFFEAF0F6)    // Soft Alabaster Pearl (Zero Halation)
val TextSecondaryDark = Color(0xFF88A0B8)  // Muted Slate Secondary Text

private val DarkColorScheme = darkColorScheme(
    primary = PurpleBrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0C3D39),
    secondary = PurpleBrandAccent,
    background = ObsidianDarkBg,
    surface = ObsidianDarkSurface,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = PurpleBrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3FCF8),
    secondary = PurpleBrandAccent,
    background = Color(0xFFF2EFE9),
    surface = Color.White,
    onBackground = Color(0xFF18222D),
    onSurface = Color(0xFF18222D)
)

/**
 * سمة التطبيق القياسية (ForgepromTheme)
 * تفرض اتجاه اليمين لليسار (RTL) للغة العربية كأساس متين، وتدعم النصوص البرمجية المختلطة (LTR).
 */
@Composable
fun ForgepromTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
