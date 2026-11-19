package com.forgeprom.codestudio.presentation.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.LocalAdaptiveScreenSpec
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine

/**
 * نافذة إنشاء مشروع جديد مع دعم الإملاء الصوتي بالعربية (Voice-to-Spec) وحماية لوحة المفاتيح (imePadding)
 */
@Composable
fun NewProjectDialog(
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onCreateProject: (String, String, String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    var newProjectName by remember { mutableStateOf("") }
    var newProjectIdea by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("ANDROID_KOTLIN") }
    val platformOptions = remember {
        listOf(
            "ANDROID_KOTLIN" to "Android Kotlin",
            "FLUTTER" to "Flutter",
            "REACT_NATIVE" to "React Native",
            "WEB" to "Web App"
        )
    }
    val starterIdeas = remember {
        listOf(
            Triple("متجر إلكتروني", "متجر سوقي السريع", "تطبيق متجر إلكتروني محلي مع سلة مشتريات، فواتير PDF، تتبع طلبات، والعمل دون إنترنت مع مزامنة آمنة."),
            Triple("حجز مواعيد", "منصة موعدي الذكية", "نظام حجز مواعيد للعيادات والخدمات مع تذكيرات محلية، تقويم عربي RTL، وإدارة ملفات العملاء بتشفير AES-256."),
            Triple("إدارة مصاريف", "محفظتي للميزانية", "تطبيق تتبع مصاريف يومية وميزانية شخصية مع تقارير رسومية، تصنيف ذكي، ونسخ احتياطي محلي مشفّر."),
            Triple("منصة تعليمية", "أكاديمية تعلّم", "تطبيق دورات تعليمية تفاعلية مع اختبارات قصيرة، تتبع التقدم، ومذاكرة دون اتصال بالإنترنت.")
        )
    }
    val liveIdeaAudit = remember(newProjectIdea) {
        PromptEngineeringEngine.evaluatePromptQuality(newProjectIdea)
    }
    val liveIdeaTokens = remember(newProjectIdea) {
        estimatePromptTokens(newProjectIdea)
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                newProjectIdea = if (newProjectIdea.isBlank()) spokenText else "$newProjectIdea\n$spokenText"
                if (newProjectName.isBlank()) {
                    newProjectName = spokenText.take(32)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إنشاء مشروع وتحليل هندسي جديد",
                color = visual.primaryText,
                fontSize = (16f + visual.fontDelta).sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
                Text(
                    text = "بداية سريعة (انقر لاختيار فكرة جاهزة):",
                    color = visual.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    starterIdeas.forEach { (chipLabel, sampleName, sampleIdea) ->
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                newProjectName = sampleName
                                newProjectIdea = sampleIdea
                            },
                            label = { Text(chipLabel, fontSize = 10.sp, maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            modifier = Modifier.testTag("starter_idea_chip_$chipLabel")
                        )
                    }
                }

                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("اسم المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                )
                OutlinedTextField(
                    value = newProjectIdea,
                    onValueChange = { newProjectIdea = it },
                    label = { Text("وصف الفكرة الحرة باللغة الطبيعية") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_idea_input")
                )
                if (newProjectIdea.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جودة الوصف: ${liveIdeaAudit.totalScore}%",
                            color = if (liveIdeaAudit.totalScore >= 70) Color(0xFF10B981) else visual.accentSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "~$liveIdeaTokens توكن • ${newProjectIdea.length} حرف",
                            color = visual.secondaryText,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = "المنصة المستهدفة:",
                    color = visual.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    platformOptions.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedPlatform == key,
                                    onClick = { selectedPlatform = key },
                                    label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                                    modifier = Modifier.weight(1f).testTag("platform_chip_$key")
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        try {
                            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن لوصف فكرة مشروعك الهندسي...")
                            }
                            speechRecognizerLauncher.launch(speechIntent)
                        } catch (_: Exception) {
                            newProjectIdea = if (newProjectIdea.isBlank()) {
                                "تطبيق أندرويد متكامل يعمل محلياً 100% مع مزامنة سحابية وتشفير AES-256"
                            } else newProjectIdea
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("voice_to_spec_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "إملاء صوتي", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إملاء الفكرة صوتياً بالعربية (Voice-to-Spec)", fontSize = 11.sp)
                }
                Text(
                    text = if (isOnline) "سيتم تحليل الفكرة عبر الخادم السحابي المتصل وحفظها محلياً."
                    else "سيتم تحليل الفكرة وتوليد البرومبت عبر المحرك المحلي (بدون إنترنت).",
                    color = if (isOnline) Color(0xFF10B981) else visual.accentPrimary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newProjectName.isNotBlank()) {
                        onCreateProject(newProjectName.trim(), newProjectIdea.trim(), selectedPlatform)
                        onDismiss()
                    }
                },
                enabled = newProjectName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.testTag("confirm_new_project_button")
            ) {
                Text("تحليل وحفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * نافذة إعدادات الخادم السحابي الآمن (HTTPS Only + استعادة الافتراضي)
 */
@Composable
fun ServerConfigDialog(
    currentServerUrl: String,
    onDismiss: () -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onResetDefaultUrl: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var serverUrlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الخادم السحابي الآمن (HTTPS)", color = visual.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.imePadding()
            ) {
                Text(
                    text = "يفرض التطبيق اتصال HTTPS مشفّر فقط (مع ترقية http:// تلقائياً) ويعمل 100% محلياً عند انقطاع الشبكة:",
                    color = visual.secondaryText,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = { serverUrlInput = it },
                    label = { Text("رابط الخادم (HTTPS URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                )
                OutlinedButton(
                    onClick = {
                        onResetDefaultUrl()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("reset_server_url_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "استعادة الخادم الافتراضي", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة الخادم الافتراضي الآمن", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveServerUrl(serverUrlInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.testTag("save_server_url_button")
            ) {
                Text("حفظ وفحص الاتصال")
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
 * محرر البرومبت والمواصفات الفوري داخل التطبيق مع تقييم الجودة المباشر (Live Quality Preview)
 */
@Composable
fun EditPromptDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onSavePrompt: (String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    var draftText by remember(project.id) {
        mutableStateOf(project.currentPromptText ?: project.rawIdea)
    }
    val liveAudit = remember(draftText) {
        PromptEngineeringEngine.evaluatePromptQuality(draftText)
    }
    val liveTokens = remember(draftText) { estimatePromptTokens(draftText) }
    val wordCount = remember(draftText) {
        if (draftText.isBlank()) 0 else draftText.trim().split(Regex("\\s+")).size
    }
    val quickSnippets = remember {
        listOf(
            "+ معمارية Clean" to "\n- المعمارية: Clean Architecture + MVVM + Repository مع فصل الطبقات.",
            "+ أمان OWASP" to "\n- الأمان: تشفير Android KeyStore AES-256-GCM وفرض HTTPS فقط وفق معايير OWASP.",
            "+ شاشات UI/RTL" to "\n- الواجهة: تصميم Material 3 تكيفي بالكامل يدعم العربية (RTL) والوضع الليلي.",
            "+ عمل Offline" to "\n- الحالات الحدية: العمل دون إنترنت 100% عبر Room SQLite ومنع أخطاء الذاكرة (OOM).",
            "+ خطة الاختبار" to "\n- معايير القبول: اختبارات وحدة Robolectric وتغطية مسارات الاستخدام الأساسية."
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تحرير المواصفات والبرومبت — ${project.name}",
                color = visual.primaryText,
                fontSize = (15f + visual.fontDelta).sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الجودة الحية: ${liveAudit.totalScore}/100",
                        color = if (liveAudit.totalScore >= 85) Color(0xFF10B981) else visual.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "$wordCount كلمة • ~$liveTokens توكن",
                        color = visual.secondaryText,
                        fontSize = 10.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { liveAudit.totalScore / 100f },
                    color = if (liveAudit.totalScore >= 85) Color(0xFF10B981) else visual.accentPrimary,
                    trackColor = visual.recessedBg,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Text(
                    text = "إدراج سريع لرفع الجودة:",
                    color = visual.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSnippets.forEach { (label, snippet) ->
                        SuggestionChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftText = draftText.trimEnd() + snippet
                            },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("quick_insert_chip_$label")
                        )
                    }
                }
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("نص البرومبت الهندسي والمواصفات") },
                    minLines = 7,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().testTag("edit_prompt_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (draftText.isNotBlank()) {
                        onSavePrompt(draftText.trim())
                        onDismiss()
                    }
                },
                enabled = draftText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.testTag("save_edited_prompt_button")
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * لوحة تفاصيل المشروع والإجراءات (تُستخدم كنافذة على الهواتف وكجزء جانبي على التابلت والشاشات العريضة)
 */
@Composable
fun ProjectDetailPanelContent(
    project: ProjectEntity,
    onOpenInStudio: (ProjectEntity) -> Unit,
    onEditPrompt: (ProjectEntity) -> Unit,
    onDuplicateProject: (ProjectEntity) -> Unit,
    onArchiveProject: (ProjectEntity) -> Unit,
    onShareProject: (ProjectEntity) -> Unit,
    onExportPdf: (ProjectEntity) -> Unit,
    onExportStarterZip: (ProjectEntity) -> Unit,
    onCopyPrompt: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val adaptive = LocalAdaptiveScreenSpec.current
    val promptPreview = project.currentPromptText ?: project.rawIdea

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = project.name,
            color = visual.primaryText,
            fontSize = (15f + visual.fontDelta).sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "المنصة: ${project.targetPlatform} | الوصف: ${project.description}",
            color = visual.secondaryText,
            fontSize = (11f + visual.fontDelta).sp
        )

        IdeCodeTerminalViewer(
            title = "SPEC_TERMINAL.md",
            codeText = promptPreview.take(900),
            onCopy = { onCopyPrompt(project) }
        )

        if (adaptive.isUltraCompact || adaptive.isHighFontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onOpenInStudio(project) },
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فتح في استوديو السلسلة", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onEditPrompt(project) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("edit_prompt_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تعديل النص", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onOpenInStudio(project) },
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                    modifier = Modifier.weight(1.3f).heightIn(min = 46.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استوديو السلسلة", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onEditPrompt(project) },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp).testTag("edit_prompt_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تعديل النص", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onShareProject(project) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("مشاركة", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { onExportPdf(project) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("PDF", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { onExportStarterZip(project) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("ZIP", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onDuplicateProject(project) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("duplicate_project_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("استنساخ المشروع", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { onArchiveProject(project) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("archive_project_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("نقل للأرشيف", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * نافذة تأكيد حذف المشروع لمنع الفقدان العرضي للبيانات
 */
@Composable
fun DeleteProjectConfirmDialog(
    project: ProjectEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "تحذير حذف المشروع",
                tint = Color(0xFFEF4444)
            )
        },
        title = {
            Text(
                text = "تأكيد حذف المشروع",
                color = visual.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = (15f + visual.fontDelta).sp
            )
        },
        text = {
            Text(
                text = "هل أنت متأكد من حذف مشروع \"${project.name}\" نهائياً من قاعدة البيانات المحلية؟ لا يمكن التراجع عن هذا الإجراء إلا عبر استعادة نسخة JSON احتياطية.",
                color = visual.secondaryText,
                fontSize = (12f + visual.fontDelta).sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.testTag("confirm_delete_project_button")
            ) {
                Text("حذف نهائي", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_project_button")
            ) {
                Text("إلغاء", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}
