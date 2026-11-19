package com.forgeprom.codestudio.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.CardVisualStyle
import com.forgeprom.codestudio.core.ui.LocalAdaptiveScreenSpec
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.core.ui.StudioVisualSettings
import com.forgeprom.codestudio.core.ux.LocalOfflineTtsReader
import java.util.Locale

/**
 * 1. شريط التحكم البصري السريع القابل للتمرير الأفقي (لا ينقص النص على أي شاشة صغيرة أو خط كبير)
 */
@Composable
fun StudioVisualControlBar(
    visualSettings: StudioVisualSettings,
    isFocusMode: Boolean = false,
    onToggleFocusMode: () -> Unit = {},
    onToggle3DDepth: () -> Unit,
    onToggleEyeComfort: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onOpenAppearanceStudio: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp)
            .testTag("visual_control_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isFocusMode,
            onClick = onToggleFocusMode,
            label = { Text("وضع التركيز", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            leadingIcon = {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "وضع التركيز", modifier = Modifier.size(15.dp))
            },
            modifier = Modifier.testTag("toggle_focus_mode_chip")
        )

        FilterChip(
            selected = visualSettings.cardStyle == CardVisualStyle.EXTRUDED_3D,
            onClick = onToggle3DDepth,
            label = { Text("عمق 3D", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            leadingIcon = {
                Icon(Icons.Default.ViewInAr, contentDescription = "العمق ثلاثي الأبعاد", modifier = Modifier.size(15.dp))
            },
            modifier = Modifier.testTag("toggle_3d_depth_chip")
        )

        FilterChip(
            selected = visualSettings.isEyeComfortWarm,
            onClick = onToggleEyeComfort,
            label = { Text("راحة العين", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            leadingIcon = {
                Icon(Icons.Default.Visibility, contentDescription = "حماية العين", modifier = Modifier.size(15.dp))
            },
            modifier = Modifier.testTag("toggle_eye_comfort_chip")
        )

        FilterChip(
            selected = !visual.isDarkTheme,
            onClick = onToggleDarkTheme,
            label = { Text(if (visual.isDarkTheme) "ليلي" else "ورقي", fontSize = 11.sp, maxLines = 1, softWrap = false) },
            leadingIcon = {
                Icon(
                    imageVector = if (visual.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "تبديل السطوع",
                    modifier = Modifier.size(15.dp)
                )
            },
            modifier = Modifier.testTag("toggle_theme_mode_chip")
        )

        FilledTonalButton(
            onClick = onOpenAppearanceStudio,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 36.dp).testTag("open_appearance_studio_button")
        ) {
            Icon(Icons.Default.Palette, contentDescription = "تخصيص المظهر", modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("المظهر والشاشة", fontSize = 11.sp, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 2. لوحة قيادة العتاد التكيفية ثلاثية الأبعاد (Adaptive 3D Telemetry HUD)
 * تتحول تلقائياً إلى شريط مضغوط قابل للتوسيع على الشاشات الأفقية والهواتف الصغيرة جداً لحفظ مساحة المحتوى.
 */
@Composable
fun HardwareTelemetryHud3D(
    availableRamMB: Long,
    connectionType: String,
    projectsCount: Int,
    averageQualityScore: Int,
    isWideScreen: Boolean = false,
    onCheckServer: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val adaptive = LocalAdaptiveScreenSpec.current
    var isExpanded by remember(adaptive.preferCompactHudByDefault) {
        mutableStateOf(!adaptive.preferCompactHudByDefault)
    }

    val ramProgress = (availableRamMB.toFloat() / 6144f).coerceIn(0.15f, 1f)
    val ramGbFormatted = remember(availableRamMB) {
        String.format(Locale.US, "%.1f", availableRamMB / 1024f)
    }
    val accentPrimary = visual.accentPrimary
    val accentSecondary = visual.accentSecondary
    val cardPad = adaptive.adaptiveCardPadding(visual.contentPadding)

    Card(
        colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
        elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
        border = visual.cardBorder,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (adaptive.isShortLandscape) 6.dp else visual.itemSpacing)
            .testTag("hardware_hud_3d_card")
    ) {
        if (!isExpanded) {
            // شريط مضغوط ذكي للهواتف بالوضع الأفقي أو الشاشات شديدة الضيق (<360dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = accentSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "RAM ${ramGbFormatted}G • $connectionType • $projectsCount مشاريع • جودة $averageQualityScore%",
                        color = visual.primaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCheckServer,
                        modifier = Modifier.size(36.dp).testTag("check_server_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "فحص الخادم", modifier = Modifier.size(16.dp), tint = accentSecondary)
                    }
                    IconButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.size(36.dp).testTag("expand_hud_button")
                    ) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "توسيع لوحة العتاد", modifier = Modifier.size(18.dp), tint = visual.secondaryText)
                    }
                }
            }
            return@Card
        }

        val headerBgModifier = if (visual.performanceLiteMode) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentPrimary.copy(alpha = if (visual.is3DDepthEnabled) 0.14f else 0.05f),
                        Color.Transparent
                    )
                )
            )
        }

        Column(
            modifier = headerBgModifier.padding(cardPad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(if (adaptive.isUltraCompact) 44.dp else 52.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = size.minDimension * 0.13f
                            drawArc(
                                color = visual.recessedBg,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = accentSecondary,
                                startAngle = 135f,
                                sweepAngle = 270f * ramProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${ramGbFormatted}G",
                                fontWeight = FontWeight.ExtraBold,
                                color = visual.primaryText,
                                fontSize = (if (adaptive.isUltraCompact) 9f else 10f).sp,
                                maxLines = 1
                            )
                            Text(
                                text = "RAM",
                                color = accentSecondary,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (visual.is3DDepthEnabled) "لوحة العتاد التكيفية (3D HUD)"
                                else "فاحص العتاد والأمان",
                                fontWeight = FontWeight.Bold,
                                color = visual.primaryText,
                                fontSize = (12f + visual.fontDelta).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            ContextualHelpIconButton(
                                title = "لوحة مراقبة العتاد والأمان",
                                explanation = "تعرض الذاكرة العشوائية المتاحة (RAM) لتحديد نماذج الذكاء الاصطناعي المحلية (GGUF) الآمنة للتشغيل دون بطء، بالإضافة إلى حالة الاتصال ومتوسط جودة مشاريعك.",
                                testTag = "help_hud_button",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "الرام: $availableRamMB MB | $connectionType",
                            color = if (visual.isDarkTheme) accentSecondary else accentPrimary,
                            fontSize = (11f + visual.fontDelta).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isWideScreen && !adaptive.isShortLandscape) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        KpiMetricPill(
                            title = "المشاريع",
                            value = "$projectsCount",
                            accentColor = accentSecondary
                        )
                        KpiMetricPill(
                            title = "متوسط الجودة",
                            value = "$averageQualityScore%",
                            accentColor = if (averageQualityScore >= 85) Color(0xFF34D399) else Color(0xFFFBBF24)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (adaptive.isUltraCompact) {
                        IconButton(
                            onClick = onCheckServer,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("check_server_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "فحص الخادم", tint = accentSecondary)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onCheckServer,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 38.dp).testTag("check_server_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "فحص الخادم", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فحص", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    IconButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.size(34.dp).testTag("collapse_hud_button")
                    ) {
                        Icon(Icons.Default.ExpandLess, contentDescription = "طي لوحة العتاد", tint = visual.secondaryText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!isWideScreen || adaptive.isShortLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KpiMetricPill(
                        title = "المشاريع",
                        value = "$projectsCount",
                        accentColor = if (visual.isDarkTheme) accentSecondary else accentPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricPill(
                        title = "الجودة",
                        value = "$averageQualityScore%",
                        accentColor = if (averageQualityScore >= 85) Color(0xFF10B981) else Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricPill(
                        title = "التشفير",
                        value = "AES-256",
                        accentColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMetricPill(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val visual = LocalStudioVisualSettings.current
    Surface(
        color = visual.recessedBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = visual.secondaryText, fontSize = 9.sp, maxLines = 1)
            Text(text = value, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/**
 * 3. حلقة الجودة الدائرية المرسومة بـ Canvas
 */
@Composable
fun ProjectQualityRing(score: Int, modifier: Modifier = Modifier) {
    val visual = LocalStudioVisualSettings.current
    val animatedProgress by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = if (visual.performanceLiteMode) 0 else 650),
        label = "quality_ring_progress"
    )
    val ringColor = when {
        score >= 85 -> Color(0xFF10B981)
        score >= 65 -> if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary
        else -> Color(0xFFD97706)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(42.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.12f
            drawCircle(
                color = visual.recessedBg,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$score%",
            color = ringColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

/**
 * 4. مخطط المعمارية البصري التفاعلي (Visual Architecture Blueprint Canvas)
 */
@Composable
fun VisualArchitectureBlueprintCanvas(
    projectName: String,
    targetPlatform: String
) {
    val visual = LocalStudioVisualSettings.current
    var selectedLayerIndex by remember { mutableIntStateOf(0) }

    val layers = listOf(
        Triple(
            "1. طبقة العرض (Presentation & Adaptive UI)",
            "Jetpack Compose / Material 3 • NavigationRail • StateFlow",
            "تتولى عرض الشاشات التكيفية للهواتف والتابلت، معالجة إدخال الصوت والصور، ودعم تعدد السمات."
        ),
        Triple(
            "2. طبقة المجال والذكاء (Domain & AI Pipeline)",
            "PromptEngineeringEngine • QualityLinter • BlueprintScaffold",
            "تولّد سلسلة الـ PRD من 4 مراحل، وتفحص جودة البرومبت من 100%، وتبني هياكل المشاريع الكاملة."
        ),
        Triple(
            "3. طبقة البيانات والأمان (Data, Room & KeyStore)",
            "Room SQLite • Android KeyStore AES-256-GCM • Hybrid Cloud",
            "تحفظ جميع المشاريع محلياً 100% دون إنترنت، وتشفّر المفاتيح عتادياً، وتزامن مع الخادم عند الاتصال."
        )
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
        elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
        border = visual.cardBorder,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("visual_architecture_card")
    ) {
        Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = visual.accentSecondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "المخطط المعماري التفاعلي (Visual Architecture)",
                        fontWeight = FontWeight.Bold,
                        color = visual.primaryText,
                        fontSize = (13f + visual.fontDelta).sp
                    )
                }
                Text(
                    text = targetPlatform,
                    color = visual.accentSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val accent = visual.accentPrimary
            val accentBright = visual.accentSecondary
            val canvasBg = visual.recessedBg

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(canvasBg)
            ) {
                val w = size.width
                val h = size.height
                val nodeW = w * 0.26f
                val nodeH = h * 0.58f
                val topY = (h - nodeH) / 2f

                val xPositions = listOf(w * 0.69f, w * 0.37f, w * 0.05f)
                val nodeColors = listOf(accent, Color(0xFF257FB5), Color(0xFF6D28D9))

                drawLine(
                    color = accentBright.copy(alpha = 0.6f),
                    start = Offset(xPositions[0], h / 2f),
                    end = Offset(xPositions[1] + nodeW, h / 2f),
                    strokeWidth = 4f
                )
                drawLine(
                    color = accentBright.copy(alpha = 0.6f),
                    start = Offset(xPositions[1], h / 2f),
                    end = Offset(xPositions[2] + nodeW, h / 2f),
                    strokeWidth = 4f
                )

                for (i in 0..2) {
                    val isSelected = selectedLayerIndex == i
                    drawRoundRect(
                        color = if (isSelected) nodeColors[i] else nodeColors[i].copy(alpha = 0.38f),
                        topLeft = Offset(xPositions[i], topY),
                        size = Size(nodeW, nodeH),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    if (isSelected) {
                        drawRoundRect(
                            color = accentBright,
                            topLeft = Offset(xPositions[i], topY),
                            size = Size(nodeW, nodeH),
                            cornerRadius = CornerRadius(14f, 14f),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1. Presentation", "2. Domain & AI", "3. Data & Room").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedLayerIndex == idx,
                        onClick = { selectedLayerIndex = idx },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false) }
                    )
                }
            }

            val currentLayer = layers[selectedLayerIndex]
            Surface(
                color = visual.recessedBg,
                shape = RoundedCornerShape(10.dp),
                border = visual.cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${currentLayer.first} — ($projectName)",
                        color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = (11f + visual.fontDelta).sp
                    )
                    Text(
                        text = "المكونات: ${currentLayer.second}",
                        color = visual.primaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = currentLayer.third,
                        color = visual.secondaryText,
                        fontSize = (10f + visual.fontDelta).sp
                    )
                }
            }
        }
    }
}

/**
 * 5. عارض الأكواد والبرومبتات الاحترافي المتكيف مع السمة (Adaptive IDE Code Terminal Viewer)
 * يدعم البحث والتظليل الفوري، الاستماع الصوتي المحلي (TTS)، ووضع القراءة بملء الشاشة.
 */
@Composable
fun IdeCodeTerminalViewer(
    title: String,
    codeText: String,
    onCopy: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val ttsReader = LocalOfflineTtsReader.current
    val ttsState by (ttsReader?.state?.collectAsState() ?: remember { mutableStateOf(null) })
    val haptic = LocalHapticFeedback.current

    var fontSizeSp by remember(visual.densityMode) {
        mutableIntStateOf((11 + visual.fontDelta.toInt()).coerceIn(9, 16))
    }
    var showInlineSearch by remember { mutableStateOf(false) }
    var inlineSearchQuery by remember { mutableStateOf("") }
    var showFullScreenReader by remember { mutableStateOf(false) }

    val lines = remember(codeText) { codeText.lines() }
    val matchCount = remember(codeText, inlineSearchQuery) {
        if (inlineSearchQuery.isBlank()) 0
        else Regex(Regex.escape(inlineSearchQuery), RegexOption.IGNORE_CASE).findAll(codeText).count()
    }
    val terminalBg = if (visual.isDarkTheme) Color(0xFF090E14) else Color(0xFF141E2B)
    val contentId = remember(title, codeText) { "term_${title.hashCode()}_${codeText.length}" }
    val isSpeakingThis = ttsState?.isSpeaking == true && ttsState?.activeContentId == contentId

    Surface(
        color = terminalBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, visual.accentPrimary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("ide_terminal_viewer")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101923))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399)))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF60A5FA)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$title (${lines.size} سطر)",
                        color = Color(0xFF93C5FD),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // بحث وتظليل داخلي
                    Surface(
                        color = if (showInlineSearch) visual.accentPrimary.copy(alpha = 0.55f) else Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            showInlineSearch = !showInlineSearch
                            if (!showInlineSearch) inlineSearchQuery = ""
                        }.testTag("terminal_search_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث داخل النص",
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).size(14.dp)
                        )
                    }

                    // قراءة صوتية محلية (Offline TTS)
                    if (ttsReader != null && ttsState?.isReady == true) {
                        Surface(
                            color = if (isSpeakingThis) Color(0xFF065F46) else Color(0xFF1E2D3D),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ttsReader.toggleSpeak(contentId, codeText)
                            }.testTag("terminal_tts_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isSpeakingThis) "إيقاف القراءة الصوتية" else "استماع صوتي",
                                tint = if (isSpeakingThis) Color(0xFF34D399) else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).size(14.dp)
                            )
                        }
                    }

                    // ملء الشاشة
                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFullScreenReader = true
                        }.testTag("terminal_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "قراءة بملء الشاشة",
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).size(14.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { if (fontSizeSp > 9) fontSizeSp-- }
                    ) {
                        Text(
                            text = "-A",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { if (fontSizeSp < 18) fontSizeSp++ }
                    ) {
                        Text(
                            text = "+A",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = visual.accentPrimary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCopy()
                        }
                    ) {
                        Text(
                            text = "نسخ",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (showInlineSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E1621))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inlineSearchQuery,
                        onValueChange = { inlineSearchQuery = it },
                        placeholder = { Text("ابحث داخل الكود...", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = visual.accentSecondary,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("terminal_inline_search_input")
                    )
                    if (inlineSearchQuery.isNotBlank()) {
                        Text(
                            text = "$matchCount نتائج",
                            color = if (matchCount > 0) Color(0xFF34D399) else Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                lines.forEachIndexed { idx, line ->
                    val trimmed = line.trimStart()
                    val lineColor = when {
                        trimmed.startsWith("#") -> visual.accentSecondary
                        trimmed.startsWith("--") || trimmed.startsWith("//") -> Color(0xFF60A5FA)
                        trimmed.startsWith("CREATE") || trimmed.startsWith("openapi:") -> Color(0xFFFBBF24)
                        trimmed.startsWith("-") || trimmed.startsWith(">") -> Color(0xFFA7F3D0)
                        else -> Color(0xFFEAF0F6)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${idx + 1}".padStart(2, '0'),
                            color = Color(0xFF4A627A),
                            fontSize = (fontSizeSp - 1).sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = buildHighlightedLine(
                                text = line.ifBlank { " " },
                                query = inlineSearchQuery,
                                highlightBg = Color(0xFFFBBF24),
                                highlightFg = Color(0xFF090E14)
                            ),
                            color = lineColor,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp + 5).sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (trimmed.startsWith("#")) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showFullScreenReader) {
        FullScreenCodeReaderDialog(
            title = title,
            codeText = codeText,
            onCopy = onCopy,
            onDismiss = { showFullScreenReader = false }
        )
    }
}
