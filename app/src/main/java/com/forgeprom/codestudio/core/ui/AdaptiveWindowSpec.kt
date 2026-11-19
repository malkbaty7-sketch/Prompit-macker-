package com.forgeprom.codestudio.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * تصنيف عرض الشاشة الفعلي المتاح للتطبيق (مبني على BoxWithConstraints لدعم النوافذ المنقسمة والقابلة للطي)
 */
enum class ScreenWidthTier(val labelAr: String) {
    ULTRA_COMPACT("هاتف صغير / نافذة ضيقة"),   // < 360dp (e.g., 320dp phones, Flip cover, Split-screen)
    COMPACT_PHONE("هاتف قياسي"),                // 360dp .. 599dp (Most Android phones 18:9 - 21:9)
    MEDIUM_FOLDABLE("هاتف عريض / قابل للطي"),   // 600dp .. 839dp (Foldables, landscape phones, 7" tablets)
    EXPANDED_TABLET("جهاز لوحي / شاشة عريضة")   // >= 840dp (Tablets, ChromeOS, Samsung DeX)
}

/**
 * تصنيف ارتفاع الشاشة المتاح للتطبيق (لاكتشاف الوضع الأفقي للهواتف والشاشات القصيرة)
 */
enum class ScreenHeightTier(val labelAr: String) {
    SHORT_LANDSCAPE("ارتفاع قصير / وضع أفقي"),  // < 480dp
    STANDARD_HEIGHT("ارتفاع قياسي"),            // 480dp .. 799dp
    TALL_DISPLAY("شاشة طولية حديثة")            // >= 800dp (20:9 / 21:9 flagship phones)
}

/**
 * محرك المواصفات التكيفية للشاشة (AdaptiveScreenSpec)
 * يحسب تلقائياً أبعاد التخطيط، عدد الأعمدة، نوع شريط التنقل، الهوامش، وحجم النصوص
 * ليتوافق التطبيق بسلاسة تامة مع جميع جوالات وأجهزة أندرويد بمختلف أحجامها وأشكالها.
 */
data class AdaptiveScreenSpec(
    val widthDp: Dp = 390.dp,
    val heightDp: Dp = 840.dp,
    val widthTier: ScreenWidthTier = ScreenWidthTier.COMPACT_PHONE,
    val heightTier: ScreenHeightTier = ScreenHeightTier.TALL_DISPLAY,
    val fontScale: Float = 1.0f
) {
    val isUltraCompact: Boolean get() = widthTier == ScreenWidthTier.ULTRA_COMPACT
    val isCompactPhone: Boolean get() = widthTier == ScreenWidthTier.ULTRA_COMPACT || widthTier == ScreenWidthTier.COMPACT_PHONE
    val isWideScreen: Boolean get() = widthTier == ScreenWidthTier.MEDIUM_FOLDABLE || widthTier == ScreenWidthTier.EXPANDED_TABLET
    val isExpandedTablet: Boolean get() = widthTier == ScreenWidthTier.EXPANDED_TABLET
    val isShortLandscape: Boolean get() = heightTier == ScreenHeightTier.SHORT_LANDSCAPE
    val isHighFontScale: Boolean get() = fontScale >= 1.25f

    /** استخدام NavigationRail الجانبي على التابلت والأجهزة القابلة للطي والهواتف بالوضع الأفقي */
    val useNavigationRail: Boolean get() = isWideScreen || isShortLandscape

    /** تفعيل التخطيط ثنائي الأجزاء (List-Detail / Dual-Pane) عندما يتسع العرض */
    val useDualPane: Boolean get() = widthDp >= 660.dp

    /** تفعيل الوضع المختصر القابل للتوسيع لشريط القياس على الشاشات القصيرة أو شديدة الضيق */
    val preferCompactHudByDefault: Boolean get() = isShortLandscape || isUltraCompact

    /** عدد أعمدة الشبكة التكيفية للبطاقات (القوالب والنماذج) */
    val gridColumns: Int
        get() = when {
            widthDp >= 1020.dp -> 3
            widthDp >= 600.dp -> 2
            else -> 1
        }

    /** هوامش أفقية متكيفة تمنع الهدر على الشاشات الصغيرة وتمنع التمدد على التابلت */
    val horizontalOuterPadding: Dp
        get() = when (widthTier) {
            ScreenWidthTier.ULTRA_COMPACT -> 8.dp
            ScreenWidthTier.COMPACT_PHONE -> 12.dp
            ScreenWidthTier.MEDIUM_FOLDABLE -> 16.dp
            ScreenWidthTier.EXPANDED_TABLET -> 22.dp
        }

    /** أقصى عرض للمحتوى في الشاشات العملاقة لضمان راحة القراءة */
    val maxContentWidth: Dp get() = 1280.dp

    /** تعديل تكيفي إضافي لمساحة البطاقات الداخلية على الهواتف الصغيرة جداً */
    fun adaptiveCardPadding(base: Dp): Dp {
        return when {
            isUltraCompact -> (base - 4.dp).coerceAtLeast(8.dp)
            isShortLandscape -> (base - 2.dp).coerceAtLeast(10.dp)
            else -> base
        }
    }

    /** وصف نصي واضح لحالة الشاشة الحالية لعرضه في استوديو المظهر */
    val summaryAr: String
        get() {
            val navLabel = if (useNavigationRail) "شريط جانبي (Rail)" else "شريط سفلي (Bar)"
            val paneLabel = if (useDualPane) "ثنائي الأجزاء" else "عمود واحد"
            return "${widthTier.labelAr} (${widthDp.value.toInt()}×${heightDp.value.toInt()} dp) • $navLabel • $paneLabel"
        }
}

val LocalAdaptiveScreenSpec = staticCompositionLocalOf { AdaptiveScreenSpec() }

@Composable
fun rememberAdaptiveScreenSpec(maxWidth: Dp, maxHeight: Dp): AdaptiveScreenSpec {
    val fontScale = LocalDensity.current.fontScale
    return remember(maxWidth, maxHeight, fontScale) {
        val widthTier = when {
            maxWidth < 360.dp -> ScreenWidthTier.ULTRA_COMPACT
            maxWidth < 600.dp -> ScreenWidthTier.COMPACT_PHONE
            maxWidth < 840.dp -> ScreenWidthTier.MEDIUM_FOLDABLE
            else -> ScreenWidthTier.EXPANDED_TABLET
        }
        val heightTier = when {
            maxHeight < 480.dp -> ScreenHeightTier.SHORT_LANDSCAPE
            maxHeight < 800.dp -> ScreenHeightTier.STANDARD_HEIGHT
            else -> ScreenHeightTier.TALL_DISPLAY
        }
        AdaptiveScreenSpec(
            widthDp = maxWidth,
            heightDp = maxHeight,
            widthTier = widthTier,
            heightTier = heightTier,
            fontScale = fontScale
        )
    }
}
