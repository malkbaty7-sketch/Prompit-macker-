package com.forgeprom.codestudio.presentation.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity

/**
 * نافذة إنشاء مشروع جديد مع دعم الإملاء الصوتي بالعربية (Voice-to-Spec) وحماية لوحة المفاتيح (imePadding)
 */
@Composable
fun NewProjectDialog(
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onCreateProject: (String, String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var newProjectName by remember { mutableStateOf("") }
    var newProjectIdea by remember { mutableStateOf("") }

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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("voice_to_spec_button")
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
                        onCreateProject(newProjectName, newProjectIdea)
                        onDismiss()
                    }
                },
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
 * نافذة إعدادات الخادم السحابي
 */
@Composable
fun ServerConfigDialog(
    currentServerUrl: String,
    onDismiss: () -> Unit,
    onSaveServerUrl: (String) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var serverUrlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الاتصال بالخادم السحابي", color = visual.primaryText, fontSize = 16.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.imePadding()
            ) {
                Text(
                    text = "يعمل التطبيق 100% بدون إنترنت، ويرتبط تلقائياً بالخادم السحابي عند توفر الشبكة:",
                    color = visual.secondaryText,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = { serverUrlInput = it },
                    label = { Text("رابط الخادم (Server URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveServerUrl(serverUrlInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary)
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
 * لوحة تفاصيل المشروع والإجراءات (تُستخدم كنافذة على الهواتف وكجزء جانبي على التابلت والشاشات العريضة)
 */
@Composable
fun ProjectDetailPanelContent(
    project: ProjectEntity,
    onOpenInStudio: (ProjectEntity) -> Unit,
    onShareProject: (ProjectEntity) -> Unit,
    onExportPdf: (ProjectEntity) -> Unit,
    onExportStarterZip: (ProjectEntity) -> Unit,
    onCopyPrompt: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
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

        Button(
            onClick = { onOpenInStudio(project) },
            colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("فتح في استوديو السلسلة والمعمارية", fontSize = 11.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onShareProject(project) },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                contentPadding = PaddingValues(6.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مشاركة", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = { onExportPdf(project) },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                contentPadding = PaddingValues(6.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF عربي", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = { onExportStarterZip(project) },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                contentPadding = PaddingValues(6.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("كود ZIP", fontSize = 10.sp)
            }
        }
    }
}
