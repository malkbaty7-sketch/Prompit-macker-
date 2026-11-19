package com.forgeprom.codestudio.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.CardVisualStyle
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.core.ui.StudioVisualSettings

/**
 * 1. شريط التحكم البصري السريع + زر فتح استوديو تخصيص المظهر
 */
@Composable
fun StudioVisualControlBar(
    visualSettings: StudioVisualSettings,
    onToggle3DDepth: () -> Unit,
    onToggleEyeComfort: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onOpenAppearanceStudio: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("visual_control_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = visualSettings.cardStyle == CardVisualStyle.EXTRUDED_3D,
            onClick = onToggle3DDepth,
            label = { Text("3D", fontSize = 10.sp, maxLines = 1) },
            leadingIcon = {
                Icon(Icons.Default.ViewInAr, contentDescription = "العمق ثلاثي الأبعاد", modifier = Modifier.size(14.dp))
            },
            modifier = Modifier.weight(0.85f).testTag("toggle_3d_depth_chip")
        )

        FilterChip(
            selected = visualSettings.isEyeComfortWarm,
            onClick = onToggleEyeComfort,
            label = { Text("راحة العين", fontSize = 10.sp, maxLines = 1) },
            leadingIcon = {
                Icon(Icons.Default.Visibility, contentDescription = "حماية العين", modifier = Modifier.size(14.dp))
            },
            modifier = Modifier.weight(1.1f).testTag("toggle_eye_comfort_chip")
        )

        FilterChip(
            selected = !visual.isDarkTheme,
            onClick = onToggleDarkTheme,
            label = { Text(if (visual.isDarkTheme) "ليلي" else "ورقي", fontSize = 10.sp, maxLines = 1) },
            leadingIcon = {
                Icon(
                    imageVector = if (visual.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "تبديل السطوع",
                    modifier = Modifier.size(14.dp)
                )
            },
            modifier = Modifier.weight(0.95f).testTag("toggle_theme_mode_chip")
        )

        FilledTonalButton(
            onClick = onOpenAppearanceStudio,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp).testTag("open_appearance_studio_button")
        ) {
            Icon(Icons.Default.Palette, contentDescription = "تخصيص المظهر", modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("المظهر", fontSize = 10.sp, maxLines = 1)
        }
    }
}

/**
 * 2. لوحة قيادة العتاد التكيفية ثلاثية الأبعاد (Adaptive 3D Telemetry HUD)
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
    val ramProgress = (availableRamMB.toFloat() / 6144f).coerceIn(0.15f, 1f)
    val accentPrimary = visual.accentPrimary
    val accentSecondary = visual.accentSecondary

    Card(
        colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
        elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
        border = visual.cardBorder,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = visual.itemSpacing)
            .testTag("hardware_hud_3d_card")
    ) {
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
            modifier = headerBgModifier.padding(visual.contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
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
                                text = "${(availableRamMB / 1024f).let { String.format("%.1f", it) }}G",
                                fontWeight = FontWeight.ExtraBold,
                                color = visual.primaryText,
                                fontSize = (10f + visual.fontDelta).sp
                            )
                            Text(
                                text = "RAM",
                                color = accentSecondary,
                                fontSize = 8.sp
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (visual.is3DDepthEnabled) "لوحة قياس العتاد ثلاثية الأبعاد (3D HUD)"
                            else "فاحص العتاد والأمان (HardwareProfiler)",
                            fontWeight = FontWeight.Bold,
                            color = visual.primaryText,
                            fontSize = (12f + visual.fontDelta).sp
                        )
                        Text(
                            text = "الرام الحر: $availableRamMB MB | الشبكة: $connectionType",
                            color = if (visual.isDarkTheme) accentSecondary else accentPrimary,
                            fontSize = (11f + visual.fontDelta).sp
                        )
                    }
                }

                if (isWideScreen) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
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

                OutlinedButton(
                    onClick = onCheckServer,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 36.dp).testTag("check_server_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "فحص الخادم", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فحص الخادم", fontSize = 10.sp)
                }
            }

            if (!isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMetricPill(
                        title = "المشاريع",
                        value = "$projectsCount مشروع",
                        accentColor = if (visual.isDarkTheme) accentSecondary else accentPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricPill(
                        title = "متوسط الجودة",
                        value = "$averageQualityScore%",
                        accentColor = if (averageQualityScore >= 85) Color(0xFF10B981) else Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricPill(
                        title = "التشفير العتادي",
                        value = "AES-256 نشط",
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
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1. Presentation", "2. Domain & AI", "3. Data & Room").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedLayerIndex == idx,
                        onClick = { selectedLayerIndex = idx },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f)
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
 */
@Composable
fun IdeCodeTerminalViewer(
    title: String,
    codeText: String,
    onCopy: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var fontSizeSp by remember(visual.densityMode) {
        mutableIntStateOf((11 + visual.fontDelta.toInt()).coerceIn(9, 16))
    }
    val lines = remember(codeText) { codeText.lines() }
    val terminalBg = if (visual.isDarkTheme) Color(0xFF090E14) else Color(0xFF141E2B)

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
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { if (fontSizeSp > 9) fontSizeSp-- }
                    ) {
                        Text(
                            text = "-A",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = visual.accentPrimary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onCopy() }
                    ) {
                        Text(
                            text = "نسخ",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            text = line.ifBlank { " " },
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
}
