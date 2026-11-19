package com.forgeprom.codestudio.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine

/**
 * استوديو السلسلة الهندسية ومدقق الجودة الذكي والمخطط المعماري التفاعلي
 * يتكيف تلقائياً مع الشاشات الصغيرة والتابلت (List-Detail / Dual-Pane on Wide Screens).
 */
@Composable
fun ChainedStudioWorkspace(
    projects: List<ProjectEntity>,
    selectedProject: ProjectEntity?,
    serverUrl: String,
    isWideScreen: Boolean = false,
    onSelectProject: (ProjectEntity) -> Unit,
    onUpdateProjectPrompt: (ProjectEntity, String, String) -> Unit,
    onAnalyzeWireframeUri: (ProjectEntity, Uri) -> Unit,
    onCopyText: (String, String) -> Unit,
    onExportMarkdown: (ProjectEntity, String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val activeProject = selectedProject ?: projects.firstOrNull()
    if (activeProject == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد مشاريع حالياً — أنشئ مشروعاً جديداً للبدء.", color = visual.secondaryText)
        }
        return
    }

    var selectedStage by remember { mutableIntStateOf(0) }
    val pipeline = remember(activeProject.id, activeProject.rawIdea, activeProject.name, serverUrl) {
        PromptEngineeringEngine.generateChainedPipeline(activeProject, serverUrl)
    }
    val currentPrompt = activeProject.currentPromptText ?: activeProject.rawIdea
    val qualityAudit = remember(currentPrompt) {
        PromptEngineeringEngine.evaluatePromptQuality(currentPrompt)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onAnalyzeWireframeUri(activeProject, uri)
        }
    }

    val projectSelectorBar: @Composable () -> Unit = {
        Column {
            Text(
                text = "المشروع الهندسي النشط:",
                fontWeight = FontWeight.Bold,
                color = visual.primaryText,
                fontSize = (13f + visual.fontDelta).sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { proj ->
                    val isSelected = proj.id == activeProject.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectProject(proj) },
                        label = { Text(proj.name, fontSize = 11.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null
                    )
                }
            }
        }
    }

    val qualityCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
            elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
            border = visual.cardBorder,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = visual.accentSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "مدقق جودة البرومبت (Prompt Linter)",
                            fontWeight = FontWeight.Bold,
                            color = visual.primaryText,
                            fontSize = (13f + visual.fontDelta).sp
                        )
                    }
                    Surface(
                        color = if (qualityAudit.totalScore >= 85) Color(0xFF065F46) else Color(0xFF78350F),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${qualityAudit.totalScore} / 100",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { qualityAudit.totalScore / 100f },
                    color = if (qualityAudit.totalScore >= 85) Color(0xFF10B981) else visual.accentPrimary,
                    trackColor = visual.recessedBg,
                    modifier = Modifier.fillMaxWidth().height(7.dp)
                )

                Text(
                    text = qualityAudit.gradeLabel,
                    color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                    fontSize = (11f + visual.fontDelta).sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    qualityAudit.metrics.forEach { metric ->
                        Surface(
                            color = visual.recessedBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = metric.name, color = visual.secondaryText, fontSize = 9.sp, maxLines = 1)
                                Text(
                                    text = "${metric.score}/${metric.maxScore}",
                                    color = visual.primaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (qualityAudit.missingElements.isNotEmpty()) {
                    Text(
                        text = "نقاط مقترحة للتحسين: ${qualityAudit.missingElements.joinToString(" • ")}",
                        color = Color(0xFFD97706),
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val refined = PromptEngineeringEngine.autoRefinePrompt(activeProject)
                            onUpdateProjectPrompt(activeProject, refined, "✓ تم تحسين البرومبت تلقائياً ورفع معايير الجودة (+95%)")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("auto_refine_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحسين تلقائي (Auto-Refine)", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("vision_wireframe_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحليل مخطط بصري", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    val pipelineTerminalCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
            elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
            border = visual.cardBorder,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "السلسلة الهندسية المترابطة (4-Stage Chained PRD Terminal)",
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryText,
                    fontSize = (13f + visual.fontDelta).sp
                )

                val stages = listOf(
                    "1. وثيقة PRD",
                    "2. قاعدة البيانات",
                    "3. عقود OpenAPI",
                    "4. برومبتات التنفيذ"
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedStage,
                    containerColor = visual.recessedBg,
                    contentColor = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                    edgePadding = 4.dp
                ) {
                    stages.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedStage == index,
                            onClick = { selectedStage = index },
                            text = { Text(title, fontSize = 11.sp) }
                        )
                    }
                }

                val stageContent = when (selectedStage) {
                    0 -> pipeline.prdSpec
                    1 -> pipeline.databaseSchema
                    2 -> pipeline.apiContracts
                    else -> pipeline.stepByStepPrompts
                }

                IdeCodeTerminalViewer(
                    title = stages[selectedStage],
                    codeText = stageContent,
                    onCopy = { onCopyText(stages[selectedStage], stageContent) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            onUpdateProjectPrompt(
                                activeProject,
                                pipeline.combinedMarkdown,
                                "✓ تم اعتماد ودمج السلسلة الهندسية الكاملة (4 مراحل) في المشروع"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                        modifier = Modifier.weight(1.2f).heightIn(min = 42.dp).testTag("apply_chained_prd_button"),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اعتماد السلسلة الكاملة", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { onCopyText("مرحلة هندسية", stageContent) },
                        modifier = Modifier.weight(0.9f).heightIn(min = 42.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ المرحلة", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { onExportMarkdown(activeProject, pipeline.combinedMarkdown) },
                        modifier = Modifier.weight(0.9f).heightIn(min = 42.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصدير .MD", fontSize = 10.sp)
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        // تخطيط ثنائي الأجزاء للتابلت والشاشات العريضة (Dual-Pane Layout)
        Column(modifier = Modifier.fillMaxSize().testTag("chained_studio_wide")) {
            projectSelectorBar()
            Spacer(modifier = Modifier.height(visual.itemSpacing))
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(visual.itemSpacing)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    item { qualityCard() }
                    item {
                        VisualArchitectureBlueprintCanvas(
                            projectName = activeProject.name,
                            targetPlatform = activeProject.targetPlatform
                        )
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                    modifier = Modifier.weight(1.15f).fillMaxHeight()
                ) {
                    item { pipelineTerminalCard() }
                }
            }
        }
    } else {
        // تخطيط عمودي للهواتف القياسية
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
            modifier = Modifier.fillMaxSize().testTag("chained_studio_list")
        ) {
            item { projectSelectorBar() }
            item {
                VisualArchitectureBlueprintCanvas(
                    projectName = activeProject.name,
                    targetPlatform = activeProject.targetPlatform
                )
            }
            item { qualityCard() }
            item { pipelineTerminalCard() }
        }
    }
}

/**
 * جناح المؤسسات والأمان وحاسبة FinOps وخزنة التشفير العتادي
 */
@Composable
fun EnterpriseSuiteSection(
    activeProject: ProjectEntity?,
    isWideScreen: Boolean = false,
    onAppendSecurityToProject: (ProjectEntity, String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onBackupAllProjects: () -> Unit,
    onRestoreBackupProjects: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var selectedMau by remember { mutableIntStateOf(10000) }
    var vaultInputText by remember { mutableStateOf("") }
    var vaultOutputText by remember { mutableStateOf("") }

    val finOps = remember(selectedMau) {
        VaultAndEnterpriseManager.calculateFinOps(mau = selectedMau, dailyPrompts = 6)
    }

    val owaspList = remember(activeProject?.id) {
        if (activeProject != null) {
            VaultAndEnterpriseManager.auditProjectSecurity(activeProject)
        } else emptyList()
    }

    val finOpsCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
            elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
            border = visual.cardBorder,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Savings, contentDescription = null, tint = visual.accentSecondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "حاسبة التوفير السحابي (FinOps Cloud vs Local GGUF)",
                        fontWeight = FontWeight.Bold,
                        color = visual.primaryText,
                        fontSize = (13f + visual.fontDelta).sp
                    )
                }

                Text(
                    text = "عدد المستخدمين النشطين شهرياً (MAU):",
                    color = visual.secondaryText,
                    fontSize = (11f + visual.fontDelta).sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1000, 10000, 50000, 100000).forEach { mauOption ->
                        FilterChip(
                            selected = selectedMau == mauOption,
                            onClick = { selectedMau = mauOption },
                            label = { Text("${mauOption / 1000}K", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("سحابي بالكامل/شهر", color = visual.secondaryText, fontSize = 10.sp)
                            Text("$${finOps.cloudMonthlyUsd}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("هجين GGUF/شهر", color = visual.secondaryText, fontSize = 10.sp)
                            Text("$${finOps.hybridGgufMonthlyUsd}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Surface(
                        color = Color(0xFF065F46).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("الوفورات السنوية", color = Color(0xFF10B981), fontSize = 10.sp)
                            Text("$${finOps.annualSavingsUsd}", color = visual.primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    val owaspCard: @Composable () -> Unit = {
        Card(
            colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
            elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
            border = visual.cardBorder,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = visual.accentSecondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "التدقيق الأمني للمشروع (OWASP MASVS & API Top 10)",
                        fontWeight = FontWeight.Bold,
                        color = visual.primaryText,
                        fontSize = (13f + visual.fontDelta).sp
                    )
                }

                owaspList.forEach { item ->
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.title, color = visual.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                Text(
                                    text = "${item.code} (${item.severity})",
                                    color = if (item.severity == "CRITICAL") Color(0xFFEF4444) else Color(0xFFD97706),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(item.recommendation, color = visual.secondaryText, fontSize = 10.sp)
                        }
                    }
                }

                if (activeProject != null) {
                    Button(
                        onClick = {
                            val secBlock = buildString {
                                appendLine("\n## متطلبات الأمان الإلزامية المعتمدة (OWASP MASVS & API Security)")
                                owaspList.forEach {
                                    appendLine("- **[${it.code}] ${it.title}:** ${it.recommendation}")
                                }
                            }
                            onAppendSecurityToProject(activeProject, secBlock)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حقن ضوابط OWASP الأمنية في برومبت المشروع النشط", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    val vaultAndBackupCard: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(visual.itemSpacing)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                border = visual.cardBorder,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = visual.accentSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "خزنة التشفير العتادي (Android KeyStore AES-256-GCM)",
                            fontWeight = FontWeight.Bold,
                            color = visual.primaryText,
                            fontSize = (13f + visual.fontDelta).sp
                        )
                    }

                    OutlinedTextField(
                        value = vaultInputText,
                        onValueChange = { vaultInputText = it },
                        label = { Text("أدخل نصاً سرياً أو مفتاح API لتشفيره أو فك تشفيره...") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (vaultInputText.isNotBlank()) {
                                    vaultOutputText = VaultAndEnterpriseManager.encryptText(vaultInputText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                        ) {
                            Text("تشفير AES-256", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (vaultInputText.isNotBlank()) {
                                    vaultOutputText = VaultAndEnterpriseManager.decryptText(vaultInputText)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                        ) {
                            Text("فك التشفير", fontSize = 11.sp)
                        }
                    }

                    if (vaultOutputText.isNotBlank()) {
                        IdeCodeTerminalViewer(
                            title = "AES-256-GCM Payload",
                            codeText = vaultOutputText,
                            onCopy = { onCopyText("محتوى الخزنة", vaultOutputText) }
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                border = visual.cardBorder,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(visual.contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "النسخ الاحتياطي المحلي الشامل (JSON Backup & Restore)",
                        fontWeight = FontWeight.Bold,
                        color = visual.primaryText,
                        fontSize = (13f + visual.fontDelta).sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onBackupAllProjects,
                            colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ نسخة JSON", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onRestoreBackupProjects,
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة النسخة", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxSize().testTag("enterprise_suite_wide"),
            horizontalArrangement = Arrangement.spacedBy(visual.itemSpacing)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                item { finOpsCard() }
                item { owaspCard() }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                item { vaultAndBackupCard() }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
            modifier = Modifier.fillMaxSize().testTag("enterprise_suite_list")
        ) {
            item { finOpsCard() }
            item { owaspCard() }
            item { vaultAndBackupCard() }
        }
    }
}
