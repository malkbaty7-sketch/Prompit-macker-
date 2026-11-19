package com.forgeprom.codestudio.core.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Base fallback brand tokens (used where direct constants are referenced)
val PurpleBrandPrimary = Color(0xFF0D8A7F)
val PurpleBrandAccent = Color(0xFF54D9CC)
val ObsidianDarkBg = Color(0xFF0D141C)
val ObsidianDarkSurface = Color(0xFF152230)
val TextPrimaryDark = Color(0xFFEAF0F6)
val TextSecondaryDark = Color(0xFF88A0B8)

enum class ThemeSurfaceMode(val labelAr: String) {
    NORDIC_DARK("ليلي نورديك"),
    OLED_BLACK("أسود OLED"),
    WARM_DARK("ليلي دافئ"),
    WARM_PAPER_LIGHT("ورقي فاتح"),
    SYSTEM_AUTO("تلقائي (النظام)")
}

enum class AccentPalette(
    val labelAr: String,
    val primary: Color,
    val accent: Color,
    val containerDark: Color,
    val containerLight: Color
) {
    TEAL_JADE("فيروزي هندسي", Color(0xFF0D8A7F), Color(0xFF54D9CC), Color(0xFF0C3D39), Color(0xFFE3FCF8)),
    CYBER_VIOLET("بنفسجي تقني", Color(0xFF6D28D9), Color(0xFFA78BFA), Color(0xFF2E1065), Color(0xFFEDE9FE)),
    OCEAN_COBALT("أزرق محيطي", Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFF1E3A8A), Color(0xFFDBEAFE)),
    AMBER_GOLD("ذهبي كهرماني", Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFF451A03), Color(0xFFFEF3C7)),
    EMERALD_MATRIX("زمردي مشرق", Color(0xFF059669), Color(0xFF34D399), Color(0xFF064E3B), Color(0xFFD1FAE5))
}

enum class CardVisualStyle(val labelAr: String) {
    EXTRUDED_3D("بارز 3D"),
    FLAT_MINIMAL("مسطح عصري"),
    HIGH_CONTRAST("تباين عالٍ")
}

enum class UiDensityMode(
    val labelAr: String,
    val paddingDp: Dp,
    val spacingDp: Dp,
    val fontScaleDelta: Float
) {
    COMPACT("مضغوط", 10.dp, 8.dp, -1f),
    BALANCED("متوازن", 14.dp, 12.dp, 0f),
    LARGE_ACCESSIBLE("كبير مريح", 18.dp, 16.dp, 2f)
}

data class StudioVisualSettings(
    val surfaceMode: ThemeSurfaceMode = ThemeSurfaceMode.NORDIC_DARK,
    val accentPalette: AccentPalette = AccentPalette.TEAL_JADE,
    val useDynamicColor: Boolean = false,
    val cardStyle: CardVisualStyle = CardVisualStyle.EXTRUDED_3D,
    val densityMode: UiDensityMode = UiDensityMode.BALANCED,
    val isEyeComfortWarm: Boolean = false,
    val performanceLiteMode: Boolean = false,
    val resolvedSystemDark: Boolean = true
) {
    val isDarkTheme: Boolean
        get() = when (surfaceMode) {
            ThemeSurfaceMode.WARM_PAPER_LIGHT -> false
            ThemeSurfaceMode.SYSTEM_AUTO -> resolvedSystemDark
            else -> true
        }

    val is3DDepthEnabled: Boolean
        get() = cardStyle == CardVisualStyle.EXTRUDED_3D && !performanceLiteMode

    val accentPrimary: Color get() = accentPalette.primary
    val accentSecondary: Color get() = accentPalette.accent

    val cardElevation: Dp
        get() = when {
            performanceLiteMode -> 0.dp
            cardStyle == CardVisualStyle.EXTRUDED_3D -> 10.dp
            cardStyle == CardVisualStyle.FLAT_MINIMAL -> 1.dp
            else -> 2.dp
        }

    val cardBorder: BorderStroke
        get() = when (cardStyle) {
            CardVisualStyle.HIGH_CONTRAST -> BorderStroke(
                2.dp,
                if (isDarkTheme) accentSecondary.copy(alpha = 0.85f) else accentPrimary
            )
            CardVisualStyle.EXTRUDED_3D -> BorderStroke(
                1.25.dp,
                if (isDarkTheme) accentPrimary.copy(alpha = 0.45f) else Color(0xFFC5BEB2)
            )
            CardVisualStyle.FLAT_MINIMAL -> BorderStroke(
                1.dp,
                if (isDarkTheme) Color(0xFF1E2D3D) else Color(0xFFE0D9CE)
            )
        }

    val containerBg: Color
        get() = when {
            !isDarkTheme -> Color(0xFFF3EFE8)
            surfaceMode == ThemeSurfaceMode.OLED_BLACK -> Color(0xFF000000)
            surfaceMode == ThemeSurfaceMode.WARM_DARK || isEyeComfortWarm -> Color(0xFF141614)
            else -> Color(0xFF0D141C)
        }

    val surfaceBg: Color
        get() = when {
            !isDarkTheme -> Color(0xFFFCFBF8)
            surfaceMode == ThemeSurfaceMode.OLED_BLACK -> Color(0xFF0B0F14)
            surfaceMode == ThemeSurfaceMode.WARM_DARK || isEyeComfortWarm -> Color(0xFF1D211D)
            else -> Color(0xFF152230)
        }

    val recessedBg: Color
        get() = when {
            !isDarkTheme -> Color(0xFFECE6DC)
            surfaceMode == ThemeSurfaceMode.OLED_BLACK -> Color(0xFF05070A)
            isEyeComfortWarm -> Color(0xFF101310)
            else -> Color(0xFF090E14)
        }

    val primaryText: Color
        get() = when {
            !isDarkTheme -> Color(0xFF16202A)
            cardStyle == CardVisualStyle.HIGH_CONTRAST -> Color(0xFFFFFFFF)
            isEyeComfortWarm -> Color(0xFFF3EFE6)
            else -> Color(0xFFEAF0F6)
        }

    val secondaryText: Color
        get() = when {
            !isDarkTheme -> Color(0xFF4A5568)
            cardStyle == CardVisualStyle.HIGH_CONTRAST -> Color(0xFFD5E1EC)
            else -> Color(0xFF88A0B8)
        }

    val contentPadding: Dp get() = densityMode.paddingDp
    val itemSpacing: Dp get() = densityMode.spacingDp
    val fontDelta: Float get() = densityMode.fontScaleDelta
}

val LocalStudioVisualSettings = staticCompositionLocalOf { StudioVisualSettings() }

/**
 * مدير حفظ واسترجاع تفضيلات المظهر محلياً عبر SharedPreferences
 */
object VisualSettingsPreferences {
    private const val PREFS_NAME = "forgeprom_visual_prefs"
    private const val KEY_SURFACE = "surface_mode"
    private const val KEY_PALETTE = "accent_palette"
    private const val KEY_DYNAMIC = "use_dynamic_color"
    private const val KEY_CARD_STYLE = "card_style"
    private const val KEY_DENSITY = "density_mode"
    private const val KEY_EYE_WARM = "eye_comfort_warm"
    private const val KEY_PERF_LITE = "performance_lite"

    fun load(context: Context): StudioVisualSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val surface = runCatching {
            ThemeSurfaceMode.valueOf(prefs.getString(KEY_SURFACE, ThemeSurfaceMode.NORDIC_DARK.name)!!)
        }.getOrDefault(ThemeSurfaceMode.NORDIC_DARK)

        val palette = runCatching {
            AccentPalette.valueOf(prefs.getString(KEY_PALETTE, AccentPalette.TEAL_JADE.name)!!)
        }.getOrDefault(AccentPalette.TEAL_JADE)

        val cardStyle = runCatching {
            CardVisualStyle.valueOf(prefs.getString(KEY_CARD_STYLE, CardVisualStyle.EXTRUDED_3D.name)!!)
        }.getOrDefault(CardVisualStyle.EXTRUDED_3D)

        val density = runCatching {
            UiDensityMode.valueOf(prefs.getString(KEY_DENSITY, UiDensityMode.BALANCED.name)!!)
        }.getOrDefault(UiDensityMode.BALANCED)

        return StudioVisualSettings(
            surfaceMode = surface,
            accentPalette = palette,
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
            cardStyle = cardStyle,
            densityMode = density,
            isEyeComfortWarm = prefs.getBoolean(KEY_EYE_WARM, false),
            performanceLiteMode = prefs.getBoolean(KEY_PERF_LITE, false)
        )
    }

    fun save(context: Context, settings: StudioVisualSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SURFACE, settings.surfaceMode.name)
            .putString(KEY_PALETTE, settings.accentPalette.name)
            .putBoolean(KEY_DYNAMIC, settings.useDynamicColor)
            .putString(KEY_CARD_STYLE, settings.cardStyle.name)
            .putString(KEY_DENSITY, settings.densityMode.name)
            .putBoolean(KEY_EYE_WARM, settings.isEyeComfortWarm)
            .putBoolean(KEY_PERF_LITE, settings.performanceLiteMode)
            .apply()
    }
}

/**
 * سمة التطبيق التكيفية الشاملة (ForgepromTheme)
 * تدعم: Android 8.0 إلى Android 15+، Material You (Dynamic Color على Android 12+)،
 * تعدد الهويات اللونية، وضع OLED وورقي، ضبط تباين أشرطة النظام تلقائياً، واتجاه RTL.
 */
@Composable
fun ForgepromTheme(
    visualSettings: StudioVisualSettings = StudioVisualSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedSettings = visualSettings.copy(resolvedSystemDark = systemDark)
    val isDark = resolvedSettings.isDarkTheme
    val context = LocalContext.current

    val colorScheme = when {
        resolvedSettings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) {
                dynamicDarkColorScheme(context).copy(
                    background = resolvedSettings.containerBg,
                    surface = resolvedSettings.surfaceBg
                )
            } else {
                dynamicLightColorScheme(context).copy(
                    background = resolvedSettings.containerBg,
                    surface = resolvedSettings.surfaceBg
                )
            }
        }
        isDark -> darkColorScheme(
            primary = resolvedSettings.accentPrimary,
            onPrimary = Color.White,
            primaryContainer = resolvedSettings.accentPalette.containerDark,
            secondary = resolvedSettings.accentSecondary,
            background = resolvedSettings.containerBg,
            surface = resolvedSettings.surfaceBg,
            onBackground = resolvedSettings.primaryText,
            onSurface = resolvedSettings.primaryText
        )
        else -> lightColorScheme(
            primary = resolvedSettings.accentPrimary,
            onPrimary = Color.White,
            primaryContainer = resolvedSettings.accentPalette.containerLight,
            secondary = resolvedSettings.accentPrimary,
            background = resolvedSettings.containerBg,
            surface = resolvedSettings.surfaceBg,
            onBackground = resolvedSettings.primaryText,
            onSurface = resolvedSettings.primaryText
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalStudioVisualSettings provides resolvedSettings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
