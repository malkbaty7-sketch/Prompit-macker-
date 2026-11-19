package com.forgeprom.codestudio.presentation.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * حدث تغذية راجعة عائم (Snackbar Event) يدعم التراجع عن الأرشفة أو المشاركة الفورية للملفات المُصدّرة
 */
data class UxFeedbackEvent(
    val id: Long = System.nanoTime(),
    val message: String,
    val exportedFile: Pair<File, String>? = null,
    val undoArchivedProject: ProjectEntity? = null
)

/**
 * تفضيلات تجربة المستخدم المحلية (الجولة الترحيبية + وضع التركيز)
 */
object UxPreferences {
    private const val PREFS_NAME = "forgeprom_ux_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_completed_v1"
    private const val KEY_FOCUS_MODE = "focus_mode_enabled"

    fun hasCompletedOnboarding(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_DONE, completed)
            .apply()
    }

    fun isFocusModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FOCUS_MODE, false)
    }

    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FOCUS_MODE, enabled)
            .apply()
    }
}

/**
 * تنسيق الوقت بصيغة عربية طبيعية مقروءة (منذ دقيقتين، منذ 3 ساعات، أمس...)
 */
fun formatRelativeTimeAr(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diffMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        minutes < 1 -> "الآن"
        minutes == 1L -> "منذ دقيقة"
        minutes == 2L -> "منذ دقيقتين"
        minutes in 3..10 -> "منذ $minutes دقائق"
        minutes < 60 -> "منذ $minutes دقيقة"
        hours == 1L -> "منذ ساعة"
        hours == 2L -> "منذ ساعتين"
        hours in 3..10 -> "منذ $hours ساعات"
        hours < 24 -> "منذ $hours ساعة"
        days == 1L -> "أمس"
        days == 2L -> "منذ يومين"
        days in 3..10 -> "منذ $days أيام"
        else -> "منذ $days يوماً"
    }
}

/**
 * تقدير تقريبي لعدد التوكنز (Tokens) للنصوص العربية والإنجليزية أثناء التحرير
 */
fun estimatePromptTokens(text: String): Int {
    if (text.isBlank()) return 0
    val words = text.trim().split(Regex("\\s+")).size
    val charEstimate = (text.length / 3.4).toInt()
    return maxOf(words, charEstimate)
}

/**
 * 8. أيقونة شرح إرشادي مبسّط (Contextual Help Tooltip) للمصطلحات التقنية
 */
@Composable
fun ContextualHelpIconButton(
    title: String,
    explanation: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val visual = LocalStudioVisualSettings.current
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = "شرح: $title",
            tint = visual.secondaryText,
            modifier = Modifier.size(18.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = visual.accentSecondary
                )
            },
            title = {
                Text(
                    text = title,
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            },
            text = {
                Text(
                    text = explanation,
                    color = visual.secondaryText,
                    fontSize = (12f + visual.fontDelta).sp,
                    lineHeight = (18f + visual.fontDelta).sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary)
                ) {
                    Text("فهمت")
                }
            },
            containerColor = visual.surfaceBg
        )
    }
}

private data class OnboardingStepData(
    val icon: ImageVector,
    val badge: String,
    val title: String,
    val description: String,
    val accent: Color
)

/**
 * 3. جولة ترحيبية تفاعلية مختصرة لأول تشغيل (3 خطوات قابلة للتخطي)
 */
@Composable
fun FirstLaunchOnboardingDialog(
    onComplete: () -> Unit,
    onCreateFirstProject: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    var stepIndex by remember { mutableIntStateOf(0) }

    val steps = remember(visual.accentPrimary, visual.accentSecondary) {
        listOf(
            OnboardingStepData(
                icon = Icons.Default.Shield,
                badge = "الخطوة 1 من 3",
                title = "محلي 100% ومشفّر عتادياً",
                description = "تُحفظ مشاريعك محلياً في قاعدة بيانات Room وتُحمى نسخك الاحتياطية بتشفير AES-256-GCM دون الاعتماد على الإنترنت، مع إمكانية الاتصال السحابي الاختياري.",
                accent = visual.accentPrimary
            ),
            OnboardingStepData(
                icon = Icons.Default.AccountTree,
                badge = "الخطوة 2 من 3",
                title = "استوديو السلسلة الهندسية (PRD)",
                description = "حوّل أي فكرة عربية إلى وثيقة PRD متكاملة، مخطط قاعدة بيانات، عقود OpenAPI، وبرومبتات تنفيذ جاهزة مع فاحص جودة حي وتحليل المخططات البصرية.",
                accent = visual.accentSecondary
            ),
            OnboardingStepData(
                icon = Icons.Default.RocketLaunch,
                badge = "الخطوة 3 من 3",
                title = "تصدير فوري وسلاسة في التحكم",
                description = "اسحب بطاقة أي مشروع لتثبيته أو أرشفته مع إمكانية التراجع، استخدم البحث الشامل من الأعلى، وصدّر مشاريعك كملفات PDF عربية أو أرشيف كود ZIP بنقرة واحدة.",
                accent = Color(0xFF10B981)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onComplete,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_dialog_content"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedContent(
                    targetState = stepIndex,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "onboarding_step_transition"
                ) { idx ->
                    val step = steps[idx]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = step.accent.copy(alpha = 0.18f),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = step.title,
                                    tint = step.accent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Surface(
                            color = visual.recessedBg,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = step.badge,
                                color = step.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = step.title,
                            color = visual.primaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = (16f + visual.fontDelta).sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = step.description,
                            color = visual.secondaryText,
                            fontSize = (12f + visual.fontDelta).sp,
                            lineHeight = (19f + visual.fontDelta).sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // مؤشر الخطوات النقطي
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { i ->
                        val active = i == stepIndex
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(if (active) 22.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (active) visual.accentPrimary else visual.secondaryText.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (stepIndex < steps.lastIndex) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        stepIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text("التالي")
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                        onCreateFirstProject()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                    modifier = Modifier.testTag("onboarding_start_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إنشاء أول مشروع")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onComplete,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(
                    text = if (stepIndex < steps.lastIndex) "تخطي" else "ابدأ التصفح",
                    color = visual.secondaryText
                )
            }
        },
        containerColor = visual.surfaceBg
    )
}

private data class CommandPaletteEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val onExecute: () -> Unit
)

/**
 * 4. لوحة الأوامر والبحث الشامل (Global Quick Search / Command Palette)
 */
@Composable
fun GlobalCommandPaletteDialog(
    projects: List<ProjectEntity>,
    blueprints: List<BlueprintAndScaffoldManager.IndustryBlueprint>,
    models: List<ModelDownloadManager.GgufModelInfo>,
    isFocusMode: Boolean,
    onDismiss: () -> Unit,
    onSelectProject: (ProjectEntity) -> Unit,
    onUseBlueprint: (BlueprintAndScaffoldManager.IndustryBlueprint) -> Unit,
    onJumpToModels: () -> Unit,
    onNewProject: () -> Unit,
    onOpenAppearance: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onRunBackup: () -> Unit,
    onCheckServer: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }

    val allEntries = remember(projects, blueprints, models, isFocusMode) {
        buildList {
            // أوامر سريعة
            add(
                CommandPaletteEntry(
                    id = "cmd_new_project",
                    title = "إنشاء مشروع هندسي جديد",
                    subtitle = "فتح نافذة إنشاء مشروع مع قوالب أفكار فورية وإملاء صوتي",
                    category = "أمر سريع",
                    icon = Icons.Default.AddCircleOutline,
                    onExecute = onNewProject
                )
            )
            add(
                CommandPaletteEntry(
                    id = "cmd_focus_mode",
                    title = if (isFocusMode) "إيقاف وضع التركيز (إظهار لوحة العتاد)" else "تفعيل وضع التركيز (مساحة عمل كاملة)",
                    subtitle = "إخفاء أو إظهار شريط المظهر ولوحة العتاد تلقائياً",
                    category = "أمر سريع",
                    icon = Icons.Default.CenterFocusStrong,
                    onExecute = onToggleFocusMode
                )
            )
            add(
                CommandPaletteEntry(
                    id = "cmd_appearance",
                    title = "تخصيص المظهر والشاشة (Appearance Studio)",
                    subtitle = "تغيير السمة والألوان والكثافة وراحة العين",
                    category = "أمر سريع",
                    icon = Icons.Default.Palette,
                    onExecute = onOpenAppearance
                )
            )
            add(
                CommandPaletteEntry(
                    id = "cmd_backup",
                    title = "إنشاء نسخة احتياطية مشفّرة (AES-256)",
                    subtitle = "حفظ جميع المشاريع محلياً مع بصمة تحقق SHA-256",
                    category = "أمان",
                    icon = Icons.Default.Backup,
                    onExecute = onRunBackup
                )
            )
            add(
                CommandPaletteEntry(
                    id = "cmd_server",
                    title = "فحص الاتصال بالخادم السحابي",
                    subtitle = "اختبار زمن الاستجابة وحالة HTTPS والذاكرة المتاحة",
                    category = "أمر سريع",
                    icon = Icons.Default.CloudSync,
                    onExecute = onCheckServer
                )
            )
            add(
                CommandPaletteEntry(
                    id = "cmd_onboarding",
                    title = "عرض الجولة الترحيبية السريعة",
                    subtitle = "مراجعة أبرز ميزات التطبيق في 3 خطوات",
                    category = "مساعدة",
                    icon = Icons.Default.HelpOutline,
                    onExecute = onReplayOnboarding
                )
            )
            // المشاريع
            projects.forEach { proj ->
                add(
                    CommandPaletteEntry(
                        id = "proj_${proj.id}",
                        title = proj.name,
                        subtitle = "${proj.targetPlatform} • ${proj.description.take(60)}",
                        category = "مشروع",
                        icon = Icons.Default.FolderOpen,
                        onExecute = { onSelectProject(proj) }
                    )
                )
            }
            // القوالب
            blueprints.forEach { bp ->
                add(
                    CommandPaletteEntry(
                        id = "bp_${bp.id}",
                        title = bp.title,
                        subtitle = "قالب جاهز (${bp.category}) • إنشاء فوري",
                        category = "قالب",
                        icon = Icons.Default.AutoAwesomeMosaic,
                        onExecute = { onUseBlueprint(bp) }
                    )
                )
            }
            // النماذج
            models.forEach { model ->
                add(
                    CommandPaletteEntry(
                        id = "model_${model.id}",
                        title = model.name,
                        subtitle = "${model.fileSizeMB} MB • ${model.arabicSupportLevel}",
                        category = "نموذج GGUF",
                        icon = Icons.Default.Memory,
                        onExecute = onJumpToModels
                    )
                )
            }
        }
    }

    val filtered = remember(allEntries, query) {
        if (query.isBlank()) allEntries.take(10)
        else allEntries.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "البحث الشامل والأوامر السريعة",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().imePadding()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن مشروع، قالب، نموذج، أو أمر...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح البحث", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("command_palette_input")
                )

                if (filtered.isEmpty()) {
                    Text(
                        text = "لا توجد نتائج مطابقة لـ \"$query\"",
                        color = visual.secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 320.dp).testTag("command_palette_results")
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            Surface(
                                color = visual.recessedBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDismiss()
                                        entry.onExecute()
                                    }
                                    .testTag("cmd_entry_${entry.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        tint = visual.accentSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            color = visual.primaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = (12f + visual.fontDelta).sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.subtitle,
                                            color = visual.secondaryText,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        color = visual.accentPrimary.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = entry.category,
                                            color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
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
