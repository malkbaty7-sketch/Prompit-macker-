package com.forgeprom.codestudio.presentation.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.diagnostics.ServiceDiagnosticsEngine
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine

/**
 * نافذة تخصيص هيكل الكود المصدري والحزمة التنفيذية الشاملة (Custom Scaffold & Executive Bundle Builder)
 */
@Composable
fun CustomScaffoldBuilderDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onGenerateZip: (BlueprintAndScaffoldManager.ScaffoldExportConfig) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val defaultPkg = remember(project.name) {
        val slug = project.name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(16)
            .ifBlank { "app" }
        "com.forgeprom.$slug"
    }

    var packageName by remember(project.id) { mutableStateOf(defaultPkg) }
    var includeAuth by remember { mutableStateOf(true) }
    var includeOfflineQueue by remember { mutableStateOf(true) }
    var includeAesVault by remember { mutableStateOf(true) }
    var includeUnitTests by remember { mutableStateOf(true) }
    var includeExecutivePdf by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "بناء وتصدير هيكل المشروع (${project.targetPlatform})",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .testTag("custom_scaffold_builder_dialog")
            ) {
                Text(
                    text = "خصص معرّف الحزمة والوحدات الجاهزة المضمنة داخل ملف ZIP، أو فعّل الحزمة التنفيذية الشاملة لدمج PDF العربي مع الكود وعقد OpenAPI وتقرير OWASP.",
                    color = visual.secondaryText,
                    fontSize = (11f + visual.fontDelta).sp
                )

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("معرّف الحزمة (Package / Namespace)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scaffold_package_name_input")
                )

                ScaffoldToggleRow(
                    title = "الحزمة التنفيذية الشاملة (+ PDF عربي RTL)",
                    subtitle = "يدمج الوثيقة التنفيذية PDF داخل docs/ بجانب الكود و OpenAPI و OWASP",
                    checked = includeExecutivePdf,
                    onCheckedChange = { includeExecutivePdf = it },
                    testTag = "toggle_executive_bundle_pdf"
                )
                ScaffoldToggleRow(
                    title = "وحدة المصادقة والصلاحيات (Auth / Bearer)",
                    subtitle = "SessionAuthManager + ضوابط التحقق من الملكية",
                    checked = includeAuth,
                    onCheckedChange = { includeAuth = it },
                    testTag = "toggle_scaffold_auth"
                )
                ScaffoldToggleRow(
                    title = "طابور المزامنة دون إنترنت (Offline Queue)",
                    subtitle = "PendingSyncTask لإدارة العمليات عند انقطاع الشبكة",
                    checked = includeOfflineQueue,
                    onCheckedChange = { includeOfflineQueue = it },
                    testTag = "toggle_scaffold_offline_queue"
                )
                ScaffoldToggleRow(
                    title = "خزنة التشفير العتادي (AES-256-GCM Vault)",
                    subtitle = "LocalAesVault لتشفير السجلات والمفاتيح محلياً",
                    checked = includeAesVault,
                    onCheckedChange = { includeAesVault = it },
                    testTag = "toggle_scaffold_aes_vault"
                )
                ScaffoldToggleRow(
                    title = "ملفات اختبارات الوحدة (JUnit Starter Tests)",
                    subtitle = "هيكل اختبار جاهز لطبقة ViewModel والمنطق الأساسي",
                    checked = includeUnitTests,
                    onCheckedChange = { includeUnitTests = it },
                    testTag = "toggle_scaffold_unit_tests"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = BlueprintAndScaffoldManager.ScaffoldExportConfig(
                        packageName = packageName.trim().ifBlank { defaultPkg },
                        includeAuthModule = includeAuth,
                        includeOfflineSyncQueue = includeOfflineQueue,
                        includeEncryptionVault = includeAesVault,
                        includeUnitTests = includeUnitTests,
                        includeExecutiveBundlePdf = includeExecutivePdf
                    )
                    onDismiss()
                    onGenerateZip(config)
                },
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_generate_custom_scaffold_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (includeExecutivePdf) "توليد الحزمة الشاملة ZIP" else "توليد هيكل الكود ZIP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

@Composable
private fun ScaffoldToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val visual = LocalStudioVisualSettings.current
    Surface(
        color = visual.recessedBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    color = visual.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Text(
                    text = subtitle,
                    color = visual.secondaryText,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

/**
 * نافذة فحص صحة الخدمات والتشخيص الذاتي والإصلاح التلقائي (Service Health Diagnostics & Self-Heal)
 */
@Composable
fun ServiceDiagnosticsDialog(
    report: ServiceDiagnosticsEngine.FullDiagnosticsReport?,
    isRunning: Boolean,
    onReRunDiagnostics: () -> Unit,
    onRunSelfHeal: () -> Unit,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "فحص صحة الخدمات والتشخيص الذاتي",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("service_diagnostics_dialog")
            ) {
                if (isRunning || report == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = visual.accentPrimary
                        )
                        Text(
                            text = "جارٍ اختبار Room و KeyStore AES-256 و TTS والعتاد والكاش...",
                            color = visual.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val scoreColor = when {
                        report.overallScore >= 85 -> Color(0xFF10B981)
                        report.overallScore >= 65 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }

                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الجاهزية الكلية لخدمات التطبيق",
                                    color = visual.primaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${report.overallScore}%",
                                    color = scoreColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                            LinearProgressIndicator(
                                progress = { report.overallScore / 100f },
                                color = scoreColor,
                                trackColor = visual.surfaceBg,
                                modifier = Modifier.fillMaxWidth().height(7.dp)
                            )
                        }
                    }

                    report.checks.forEach { check ->
                        val (badgeText, badgeColor) = when (check.status) {
                            ServiceDiagnosticsEngine.CheckStatus.OK -> "✓ سليم" to Color(0xFF10B981)
                            ServiceDiagnosticsEngine.CheckStatus.WARN -> "⚠ تنبيه" to Color(0xFFF59E0B)
                            ServiceDiagnosticsEngine.CheckStatus.ERROR -> "✕ خطأ" to Color(0xFFEF4444)
                        }
                        Surface(
                            color = visual.recessedBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = check.title,
                                        color = visual.primaryText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$badgeText (${check.latencyMs}ms)",
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = check.detail,
                                    color = visual.secondaryText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRunSelfHeal,
                            enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                            modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("run_self_heal_button")
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إصلاح ذاتي شامل", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onReRunDiagnostics,
                            enabled = !isRunning,
                            modifier = Modifier.weight(0.9f).heightIn(min = 48.dp).testTag("rerun_diagnostics_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة الفحص", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إغلاق", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * نافذة مقارنة مشروعين جنباً إلى جنب (Side-by-Side Project Comparator)
 */
@Composable
fun ProjectComparatorDialog(
    projects: List<ProjectEntity>,
    qualityScoresMap: Map<String, Int>,
    securityScoresMap: Map<String, Int>,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    if (projects.size < 2) {
        onDismiss()
        return
    }
    var leftId by remember(projects) { mutableStateOf(projects.first().id) }
    var rightId by remember(projects) { mutableStateOf(projects[1].id) }
    val leftProject = projects.find { it.id == leftId } ?: projects.first()
    val rightProject = projects.find { it.id == rightId } ?: projects[1]

    val leftAudit = remember(leftProject.id, leftProject.currentPromptText) {
        PromptEngineeringEngine.evaluatePromptQuality(leftProject.currentPromptText ?: leftProject.rawIdea)
    }
    val rightAudit = remember(rightProject.id, rightProject.currentPromptText) {
        PromptEngineeringEngine.evaluatePromptQuality(rightProject.currentPromptText ?: rightProject.rawIdea)
    }
    val leftQuality = qualityScoresMap[leftProject.id] ?: leftAudit.totalScore
    val rightQuality = qualityScoresMap[rightProject.id] ?: rightAudit.totalScore
    val leftSecurity = securityScoresMap[leftProject.id] ?: 0
    val rightSecurity = securityScoresMap[rightProject.id] ?: 0
    val leftTokens = remember(leftProject.id, leftProject.currentPromptText) {
        estimatePromptTokens(leftProject.currentPromptText ?: leftProject.rawIdea)
    }
    val rightTokens = remember(rightProject.id, rightProject.currentPromptText) {
        estimatePromptTokens(rightProject.currentPromptText ?: rightProject.rawIdea)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "مقارنة مشروعين (Project Comparator)",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("project_comparator_dialog")
            ) {
                Text("المشروع الأول (A):", color = visual.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    projects.forEach { proj ->
                        FilterChip(
                            selected = proj.id == leftProject.id,
                            onClick = { leftId = proj.id },
                            label = { Text(proj.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Text("المشروع الثاني (B):", color = visual.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    projects.forEach { proj ->
                        FilterChip(
                            selected = proj.id == rightProject.id,
                            onClick = { rightId = proj.id },
                            label = { Text(proj.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComparatorProjectColumn(
                        tag = "A",
                        project = leftProject,
                        quality = leftQuality,
                        security = leftSecurity,
                        tokens = leftTokens,
                        missingCount = leftAudit.missingElements.size,
                        isWinner = (leftQuality + leftSecurity) >= (rightQuality + rightSecurity),
                        modifier = Modifier.weight(1f)
                    )
                    ComparatorProjectColumn(
                        tag = "B",
                        project = rightProject,
                        quality = rightQuality,
                        security = rightSecurity,
                        tokens = rightTokens,
                        missingCount = rightAudit.missingElements.size,
                        isWinner = (rightQuality + rightSecurity) > (leftQuality + leftSecurity),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (leftAudit.missingElements.isNotEmpty() || rightAudit.missingElements.isNotEmpty()) {
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الفجوات الهندسية المرصودة:", color = visual.primaryText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = "A: " + (leftAudit.missingElements.joinToString(" • ").ifBlank { "مكتمل 100% ✓" }),
                                color = if (leftAudit.missingElements.isEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "B: " + (rightAudit.missingElements.joinToString(" • ").ifBlank { "مكتمل 100% ✓" }),
                                color = if (rightAudit.missingElements.isEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("إغلاق")
            }
        },
        containerColor = visual.surfaceBg
    )
}

@Composable
private fun ComparatorProjectColumn(
    tag: String,
    project: ProjectEntity,
    quality: Int,
    security: Int,
    tokens: Int,
    missingCount: Int,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    val visual = LocalStudioVisualSettings.current
    Surface(
        color = visual.recessedBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isWinner) 1.5.dp else 1.dp,
            color = if (isWinner) Color(0xFF10B981).copy(alpha = 0.7f) else Color.Transparent
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$tag • ${project.name}",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isWinner) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "الأعلى جاهزية", tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                }
            }
            Text("المنصة: ${project.targetPlatform}", color = visual.accentSecondary, fontSize = 10.sp, maxLines = 1)
            HorizontalDivider(color = visual.surfaceBg)
            Text("الجودة: $quality%", color = if (quality >= 85) Color(0xFF10B981) else visual.primaryText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("OWASP: $security%", color = if (security >= 80) Color(0xFF10B981) else Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("التوكنز: ~$tokens", color = visual.secondaryText, fontSize = 10.sp)
            Text("نواقص: $missingCount", color = if (missingCount == 0) Color(0xFF10B981) else Color(0xFFF59E0B), fontSize = 10.sp)
        }
    }
}

/**
 * نافذة إضافة نموذج GGUF مخصّص برابط HTTPS مباشر (Add Custom GGUF Model)
 */
@Composable
fun AddCustomGgufModelDialog(
    onDismiss: () -> Unit,
    onAddModel: (name: String, url: String, sizeMB: Long, params: String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var modelName by remember { mutableStateOf("") }
    var modelUrl by remember { mutableStateOf("") }
    var sizeText by remember { mutableStateOf("950") }
    var paramsText by remember { mutableStateOf("1.5B") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "إضافة نموذج GGUF مخصّص",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .testTag("add_custom_gguf_dialog")
            ) {
                Text(
                    text = "أدخل رابط HTTPS مباشر لملف .gguf (مثل HuggingFace) ليتم دعم الاستكمال وفحص الذاكرة و SHA-256 تلقائياً.",
                    color = visual.secondaryText,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("اسم النموذج") },
                    placeholder = { Text("مثال: DeepSeek Coder 1.3B Q4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_model_name_input")
                )
                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = { modelUrl = it },
                    label = { Text("رابط HTTPS المباشر (.gguf)") },
                    placeholder = { Text("https://huggingface.co/.../model.gguf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_model_url_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { sizeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("الحجم (MB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_model_size_input")
                    )
                    OutlinedTextField(
                        value = paramsText,
                        onValueChange = { paramsText = it },
                        label = { Text("المعاملات") },
                        placeholder = { Text("1.5B / 3B") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_model_params_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sizeVal = sizeText.toLongOrNull() ?: 950L
                    onAddModel(modelName.trim(), modelUrl.trim(), sizeVal, paramsText.trim().ifBlank { "Custom" })
                    onDismiss()
                },
                enabled = modelName.isNotBlank() && modelUrl.startsWith("https://", ignoreCase = true),
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_add_custom_model_button")
            ) {
                Text("إضافة النموذج")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}

/**
 * نافذة عرض ونسخ وتصدير مخطط Mermaid.js (Architecture Flowchart + Domain ER Diagram)
 */
@Composable
fun MermaidDiagramDialog(
    project: ProjectEntity,
    mermaidCode: String,
    onCopy: () -> Unit,
    onExportMarkdown: () -> Unit,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schema, contentDescription = null, tint = visual.accentSecondary)
                Text(
                    text = "مخطط Mermaid.js — ${project.name}",
                    color = visual.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = (15f + visual.fontDelta).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("mermaid_diagram_dialog")
            ) {
                Text(
                    text = "كود Mermaid جاهز للصق في GitHub README أو Notion أو Obsidian (يتضمن مخطط الطبقات + جداول المجال ER Diagram):",
                    color = visual.secondaryText,
                    fontSize = 11.sp
                )
                IdeCodeTerminalViewer(
                    title = "architecture_erd.mmd",
                    codeText = mermaidCode,
                    onCopy = onCopy
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopy,
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_mermaid_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ Mermaid", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onExportMarkdown()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_mermaid_md_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير .MD", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إغلاق", color = visual.secondaryText)
            }
        },
        containerColor = visual.surfaceBg
    )
}
