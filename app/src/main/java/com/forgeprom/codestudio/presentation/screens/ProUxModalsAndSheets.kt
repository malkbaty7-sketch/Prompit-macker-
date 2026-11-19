package com.forgeprom.codestudio.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.core.ux.LocalOfflineTtsReader
import com.forgeprom.codestudio.core.ux.PromptSnapshot
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine

/**
 * دالة مساعدة لتظليل نتائج البحث داخل الأسطر النصية
 */
fun buildHighlightedLine(
    text: String,
    query: String,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var startIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        while (startIndex < lowerText.length) {
            val foundIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (foundIndex < 0) break
            addStyle(
                style = SpanStyle(
                    background = highlightBg,
                    color = highlightFg,
                    fontWeight = FontWeight.Bold
                ),
                start = foundIndex,
                end = foundIndex + lowerQuery.length
            )
            startIndex = foundIndex + lowerQuery.length
        }
    }
}

/**
 * 1. نافذة سجل إصدارات البرومبت والاستعادة بلمسة واحدة (Prompt Version History & 1-Tap Rollback)
 */
@Composable
fun PromptVersionHistoryDialog(
    project: ProjectEntity,
    snapshots: List<PromptSnapshot>,
    onDismiss: () -> Unit,
    onRestoreSnapshot: (PromptSnapshot) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    var expandedId by remember { mutableStateOf<String?>(snapshots.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "سجل إصدارات البرومبت (${snapshots.size})",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            if (snapshots.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HistoryToggleOff, contentDescription = null, tint = visual.secondaryText, modifier = Modifier.size(34.dp))
                    Text(
                        text = "لا توجد إصدارات سابقة محفوظة لهذا المشروع بعد.\nسيحفظ التطبيق لقطة تلقائياً قبل أي تعديل أو تحسين تلقائي.",
                        color = visual.secondaryText,
                        fontSize = (12f + visual.fontDelta).sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 380.dp).testTag("version_history_list")
                ) {
                    items(snapshots, key = { it.id }) { snap ->
                        val isExpanded = expandedId == snap.id
                        Surface(
                            color = visual.recessedBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isExpanded) visual.accentPrimary.copy(alpha = 0.6f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { expandedId = if (isExpanded) null else snap.id }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = snap.reason,
                                            color = visual.primaryText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (12f + visual.fontDelta).sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatRelativeTimeAr(snap.timestampMs)} • ~${estimatePromptTokens(snap.promptText)} توكن",
                                            color = visual.secondaryText,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Surface(
                                        color = if (snap.qualityScore >= 85) Color(0xFF065F46) else visual.accentPrimary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${snap.qualityScore}%",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = snap.promptText,
                                    color = visual.secondaryText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = if (isExpanded) 10 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isExpanded) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onRestoreSnapshot(snap)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).testTag("restore_snapshot_${snap.id}")
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("استعادة هذا الإصدار الآن", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * 2 + 3. قارئ الأكواد والبرومبت بملء الشاشة مع بحث وتظليل فوري + استماع صوتي محلي (Offline TTS)
 */
@Composable
fun FullScreenCodeReaderDialog(
    title: String,
    codeText: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val ttsReader = LocalOfflineTtsReader.current
    val ttsState by (ttsReader?.state?.collectAsState() ?: remember { mutableStateOf(null) })
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var fontSizeSp by remember { mutableIntStateOf(13) }
    var softWrapEnabled by remember { mutableStateOf(true) }

    val lines = remember(codeText) { codeText.lines() }
    val matchCount = remember(codeText, searchQuery) {
        if (searchQuery.isBlank()) 0
        else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(codeText).count()
    }
    val contentId = remember(title, codeText) { "fullscreen_${title.hashCode()}_${codeText.length}" }
    val isSpeakingThis = ttsState?.isSpeaking == true && ttsState?.activeContentId == contentId

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF090E14),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("fullscreen_code_reader")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // الشريط العلوي لقارئ ملء الشاشة
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101923))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_fullscreen_reader_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق ملء الشاشة", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${lines.size} سطر • ~${estimatePromptTokens(codeText)} توكن",
                                color = Color(0xFF93C5FD),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (ttsReader != null && ttsState?.isReady == true) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    ttsReader.toggleSpeak(contentId, codeText)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp).testTag("fullscreen_tts_button")
                            ) {
                                Icon(
                                    imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = if (isSpeakingThis) "إيقاف القراءة" else "استماع صوتي",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSpeakingThis) "إيقاف" else "استماع", fontSize = 10.sp)
                            }
                            Surface(
                                color = Color(0xFF1E2D3D),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { ttsReader.cycleSpeechRate() }
                                    .testTag("fullscreen_tts_speed_button")
                            ) {
                                Text(
                                    text = "${ttsState?.speechRate ?: 1.0f}x",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCopy()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ النص", tint = Color(0xFF34D399))
                        }
                    }
                }

                // شريط البحث الداخلي والتحكم بحجم الخط والالتفاف
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E1621))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث وتظليل داخل النص...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$matchCount",
                                        color = if (matchCount > 0) Color(0xFF34D399) else Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = visual.accentSecondary,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("fullscreen_search_input")
                    )

                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { if (fontSizeSp > 10) fontSizeSp-- }
                    ) {
                        Text("-A", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
                    }
                    Surface(
                        color = Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { if (fontSizeSp < 22) fontSizeSp++ }
                    ) {
                        Text("+A", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
                    }
                    Surface(
                        color = if (softWrapEnabled) visual.accentPrimary.copy(alpha = 0.45f) else Color(0xFF1E2D3D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { softWrapEnabled = !softWrapEnabled }
                    ) {
                        Icon(
                            imageVector = Icons.Default.WrapText,
                            contentDescription = "تبديل التفاف الأسطر",
                            tint = Color.White,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                }

                // عرض الكود مع أرقام الأسطر وتظليل فوري لنتائج البحث
                val horizontalModifier = if (softWrapEnabled) Modifier else Modifier.horizontalScroll(rememberScrollState())
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(lines.size) { idx ->
                        val line = lines[idx]
                        val trimmed = line.trimStart()
                        val lineColor = when {
                            trimmed.startsWith("#") -> visual.accentSecondary
                            trimmed.startsWith("--") || trimmed.startsWith("//") -> Color(0xFF60A5FA)
                            trimmed.startsWith("CREATE") || trimmed.startsWith("openapi:") -> Color(0xFFFBBF24)
                            trimmed.startsWith("-") || trimmed.startsWith(">") -> Color(0xFFA7F3D0)
                            else -> Color(0xFFEAF0F6)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().then(horizontalModifier),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${idx + 1}".padStart(3, '0'),
                                color = Color(0xFF4A627A),
                                fontSize = (fontSizeSp - 2).sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = buildHighlightedLine(
                                    text = line.ifBlank { " " },
                                    query = searchQuery,
                                    highlightBg = Color(0xFFFBBF24),
                                    highlightFg = Color(0xFF090E14)
                                ),
                                color = lineColor,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + 6).sp,
                                fontFamily = FontFamily.Monospace,
                                softWrap = softWrapEnabled,
                                fontWeight = if (trimmed.startsWith("#")) FontWeight.Bold else FontWeight.Normal,
                                modifier = if (softWrapEnabled) Modifier.weight(1f) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. نافذة معاينة القالب الهندسي قبل إنشاء المشروع (Blueprint Preview Dialog)
 */
@Composable
fun BlueprintPreviewDialog(
    blueprint: BlueprintAndScaffoldManager.IndustryBlueprint,
    onDismiss: () -> Unit,
    onCopyPrompt: () -> Unit,
    onUseBlueprint: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val score = remember(blueprint.prebuiltPrompt) {
        PromptEngineeringEngine.evaluatePromptQuality(blueprint.prebuiltPrompt).totalScore
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = blueprint.title,
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = visual.accentPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = blueprint.targetPlatform,
                            color = visual.accentSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(color = Color(0xFF065F46).copy(alpha = 0.35f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "جودة القالب $score%",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("blueprint_preview_dialog")
            ) {
                Text(
                    text = blueprint.description,
                    color = visual.secondaryText,
                    fontSize = (12f + visual.fontDelta).sp
                )
                IdeCodeTerminalViewer(
                    title = "${blueprint.id}.md",
                    codeText = blueprint.prebuiltPrompt,
                    onCopy = onCopyPrompt
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onUseBlueprint()
                },
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.testTag("confirm_use_previewed_blueprint")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إنشاء المشروع الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * 8. لوحة جاهزية المشاريع والإحصاءات الذكية (Actionable Readiness Insights Dialog)
 */
@Composable
fun ProjectReadinessInsightsDialog(
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onAutoRefineProject: (ProjectEntity) -> Unit,
    onOpenInStudio: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current

    val platformCounts = remember(projects) {
        projects.groupingBy { it.targetPlatform }.eachCount().entries.sortedByDescending { it.value }
    }
    val projectQualities = remember(projects) {
        projects.map { proj ->
            val quality = PromptEngineeringEngine.evaluatePromptQuality(proj.currentPromptText ?: proj.rawIdea).totalScore
            val security = VaultAndEnterpriseManager.calculateSecurityScore(
                VaultAndEnterpriseManager.auditProjectSecurity(proj)
            )
            Triple(proj, quality, security)
        }
    }
    val avgQuality = remember(projectQualities) {
        if (projectQualities.isEmpty()) 0 else projectQualities.map { it.second }.average().toInt()
    }
    val avgSecurity = remember(projectQualities) {
        if (projectQualities.isEmpty()) 0 else projectQualities.map { it.third }.average().toInt()
    }
    val projectsNeedingBoost = remember(projectQualities) {
        projectQualities.filter { it.second < 85 || it.third < 80 }.sortedBy { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "جاهزية مشاريعك وإحصاءات الإنجاز",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("readiness_insights_dialog")
            ) {
                // مؤشرات ملخصة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("متوسط الجودة", color = visual.secondaryText, fontSize = 10.sp)
                            Text(
                                text = "$avgQuality%",
                                color = if (avgQuality >= 85) Color(0xFF10B981) else visual.accentPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("جاهزية OWASP", color = visual.secondaryText, fontSize = 10.sp)
                            Text(
                                text = "$avgSecurity%",
                                color = if (avgSecurity >= 80) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                // توزيع المنصات
                if (platformCounts.isNotEmpty()) {
                    Text(
                        text = "توزيع المشاريع حسب المنصة (${projects.size}):",
                        color = visual.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    platformCounts.forEach { (platform, count) ->
                        val ratio = count.toFloat() / projects.size.coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(platform, color = visual.secondaryText, fontSize = 11.sp)
                                Text("$count (${(ratio * 100).toInt()}%)", color = visual.primaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = visual.accentSecondary,
                                trackColor = visual.recessedBg,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                HorizontalDivider(color = visual.recessedBg)

                // مشاريع يمكن رفع جودتها الآن
                Text(
                    text = if (projectsNeedingBoost.isEmpty()) "✓ جميع مشاريعك محصّنة وبجودة ممتازة (+85%)"
                    else "مشاريع يمكن رفع جودتها الآن (${projectsNeedingBoost.size}):",
                    color = if (projectsNeedingBoost.isEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                projectsNeedingBoost.forEach { (proj, quality, security) ->
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onDismiss()
                                        onOpenInStudio(proj)
                                    }
                            ) {
                                Text(
                                    text = proj.name,
                                    color = visual.primaryText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "جودة $quality% • أمان $security%",
                                    color = visual.secondaryText,
                                    fontSize = 10.sp
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAutoRefineProject(proj)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp).testTag("boost_project_${proj.id}")
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحسين ✨", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary)
            ) {
                Text("تم")
            }
        },
        containerColor = visual.surfaceBg
    )
}
