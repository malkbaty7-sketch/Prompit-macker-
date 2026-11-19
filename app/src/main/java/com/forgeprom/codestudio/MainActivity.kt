package com.forgeprom.codestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.service.ForegroundGenerationService
import com.forgeprom.codestudio.core.ui.CardVisualStyle
import com.forgeprom.codestudio.core.ui.ForgepromTheme
import com.forgeprom.codestudio.core.ui.StudioVisualSettings
import com.forgeprom.codestudio.core.ui.ThemeSurfaceMode
import com.forgeprom.codestudio.core.ui.VisualSettingsPreferences
import com.forgeprom.codestudio.data.local.db.AppDatabase
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import com.forgeprom.codestudio.presentation.screens.MainAppScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var hardwareProfiler: HardwareProfiler
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var cloudApiService: CloudApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getInstance(this)
        hardwareProfiler = HardwareProfiler(this)
        networkMonitor = NetworkMonitor(this)
        modelDownloadManager = ModelDownloadManager(this, hardwareProfiler)
        cloudApiService = CloudApiService(this)

        // Seed initial showcase projects if empty
        lifecycleScope.launch {
            val dao = database.projectDao()
            val initial = dao.getProjectById("forgeprom-seed-1")
            if (initial == null) {
                dao.insertOrUpdate(
                    ProjectEntity(
                        id = "forgeprom-seed-1",
                        name = "نظام نقاط البيع المحلي المتكامل (ForgePOS)",
                        description = "تطبيق POS مخصص لإدارة المبيعات والمخزون وطباعة الفواتير عبر البلوتوث محلياً 100% دون إنترنت مع مزامنة سحابية اختيارية.",
                        type = "NEW_APP",
                        targetPlatform = "ANDROID_KOTLIN",
                        rawIdea = "تطبيق أندرويد متكامل لنقاط البيع السريعة بدون إنترنت",
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgePOS\n- المعمارية: Clean Architecture + MVVM + Room DB\n- الواجهة: Jetpack Compose RTL مع دعم الوضع الداكن\n- الأمان: تشفير الفواتير والمفاتيح عبر Android KeyStore AES-256-GCM وفق معايير OWASP\n- الحالات الحدية: العمل دون إنترنت 100% ومنع استنزاف الذاكرة RAM (OOM)\n- معايير القبول: اختبار العمليات الحسابية وتصدير التقارير PDF.",
                        isPinned = true
                    )
                )
                dao.insertOrUpdate(
                    ProjectEntity(
                        id = "forgeprom-seed-2",
                        name = "نظام إدارة العيادات الطبية الخاصة (ForgeClinic)",
                        description = "منصة إدارة ملفات المرضى والمواعيد والوصفات الطبية مع تشفير محلي صارم وحماية بيانات الخصوصية وفق HIPAA.",
                        type = "NEW_APP",
                        targetPlatform = "FLUTTER",
                        rawIdea = "إدارة ملفات المرضى وتاريخ الزيارات الطبية محلياً على التابلت",
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgeClinic\n- المعمارية: Clean Architecture + SQLite Room\n- الأمان: تشفير AES-256-GCM محلي وفق OWASP MASVS\n- الميزات: سجل زيارات مرضى، عمل دون إنترنت، وتصدير تقارير طبية PDF باللغة العربية (RTL).",
                        isPinned = false
                    )
                )
            }
        }

        setContent {
            var visualSettings by remember {
                mutableStateOf(VisualSettingsPreferences.load(this@MainActivity))
            }

            fun updateAndSaveVisualSettings(updated: StudioVisualSettings) {
                visualSettings = updated
                VisualSettingsPreferences.save(this@MainActivity, updated)
            }

            ForgepromTheme(visualSettings = visualSettings) {
                val projects by database.projectDao().getActiveProjectsFlow().collectAsState(initial = emptyList())
                val networkState by networkMonitor.observeNetworkState().collectAsState(
                    initial = networkMonitor.getCurrentNetworkState()
                )
                val availableRam = remember { hardwareProfiler.getAvailableRamMB() }
                val models = remember { modelDownloadManager.getRecommendedModels() }
                val blueprints = remember { BlueprintAndScaffoldManager.getReadyBlueprints() }

                var serverStatus by remember { mutableStateOf<CloudApiService.ServerHealthResult?>(null) }
                var isCheckingServer by remember { mutableStateOf(false) }
                var currentServerUrl by remember { mutableStateOf(cloudApiService.getServerUrl()) }
                var downloadStatusMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var researchResult by remember { mutableStateOf("") }
                var isResearching by remember { mutableStateOf(false) }
                var actionFeedback by remember { mutableStateOf<String?>(null) }

                fun showFeedback(msg: String) {
                    lifecycleScope.launch {
                        actionFeedback = msg
                        delay(4000)
                        actionFeedback = null
                    }
                }

                fun checkServer() {
                    lifecycleScope.launch {
                        isCheckingServer = true
                        serverStatus = cloudApiService.checkServerConnection()
                        isCheckingServer = false
                    }
                }

                LaunchedEffect(networkState.isOnline, networkState.isAirplaneMode) {
                    checkServer()
                }

                MainAppScreen(
                    projects = projects,
                    availableRamMB = availableRam,
                    networkState = networkState,
                    serverStatus = serverStatus,
                    isCheckingServer = isCheckingServer,
                    currentServerUrl = currentServerUrl,
                    models = models,
                    blueprints = blueprints,
                    downloadStatusMap = downloadStatusMap,
                    researchResult = researchResult,
                    isResearching = isResearching,
                    actionFeedback = actionFeedback,
                    visualSettings = visualSettings,
                    onUpdateVisualSettings = { updateAndSaveVisualSettings(it) },
                    onToggle3DDepth = {
                        val nextStyle = if (visualSettings.cardStyle == CardVisualStyle.EXTRUDED_3D) {
                            CardVisualStyle.FLAT_MINIMAL
                        } else {
                            CardVisualStyle.EXTRUDED_3D
                        }
                        updateAndSaveVisualSettings(visualSettings.copy(cardStyle = nextStyle))
                    },
                    onToggleEyeComfort = {
                        updateAndSaveVisualSettings(
                            visualSettings.copy(isEyeComfortWarm = !visualSettings.isEyeComfortWarm)
                        )
                    },
                    onToggleDarkTheme = {
                        val nextMode = if (visualSettings.surfaceMode == ThemeSurfaceMode.WARM_PAPER_LIGHT) {
                            ThemeSurfaceMode.NORDIC_DARK
                        } else {
                            ThemeSurfaceMode.WARM_PAPER_LIGHT
                        }
                        updateAndSaveVisualSettings(visualSettings.copy(surfaceMode = nextMode))
                    },
                    onCheckServer = { checkServer() },
                    onUpdateServerUrl = { newUrl ->
                        cloudApiService.setServerUrl(newUrl)
                        currentServerUrl = cloudApiService.getServerUrl()
                        checkServer()
                    },
                    onDownloadModel = { model ->
                        lifecycleScope.launch {
                            ForegroundGenerationService.startTask(
                                this@MainActivity,
                                "تنزيل النموذج ${model.name}",
                                "جارٍ التنزيل والتحقق من SHA-256..."
                            )
                            modelDownloadManager.downloadModelWithResume(model).collect { progress ->
                                val statusMsg = when (progress) {
                                    is ModelDownloadManager.DownloadProgress.CheckingMemory ->
                                        "جارٍ فحص الذاكرة العشوائية (${progress.availableMB} MB)..."
                                    is ModelDownloadManager.DownloadProgress.Downloading ->
                                        "جارٍ التنزيل: ${progress.progressPercent}% (${progress.downloadedMB} / ${progress.totalMB} MB)"
                                    is ModelDownloadManager.DownloadProgress.VerifyingChecksum ->
                                        "جارٍ التحقق من البصمة الرقمية SHA-256..."
                                    is ModelDownloadManager.DownloadProgress.Completed -> {
                                        ForegroundGenerationService.stopTask(this@MainActivity)
                                        "✓ اكتمل التنزيل والتحقق من SHA-256 بنجاح"
                                    }
                                    is ModelDownloadManager.DownloadProgress.Failed -> {
                                        ForegroundGenerationService.stopTask(this@MainActivity)
                                        "⚠️ ${progress.reason}"
                                    }
                                }
                                downloadStatusMap = downloadStatusMap + (model.id to statusMsg)
                            }
                        }
                    },
                    onRunResearch = { query ->
                        lifecycleScope.launch {
                            isResearching = true
                            researchResult = cloudApiService.performTechnicalResearch(query)
                            isResearching = false
                        }
                    },
                    onUseBlueprint = { bp ->
                        lifecycleScope.launch {
                            database.projectDao().insertOrUpdate(
                                ProjectEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = bp.title,
                                    description = bp.description,
                                    type = "NEW_APP",
                                    targetPlatform = bp.targetPlatform,
                                    rawIdea = bp.rawIdea,
                                    currentPromptText = bp.prebuiltPrompt
                                )
                            )
                            showFeedback("✓ تم إنشاء المشروع من قالب: ${bp.title}")
                        }
                    },
                    onShareProject = { proj ->
                        NativeShareAndPdfHelper.shareProjectPrompt(this@MainActivity, proj)
                    },
                    onExportPdf = { proj ->
                        val file = NativeShareAndPdfHelper.generateNativeRtlPdf(this@MainActivity, proj)
                        showFeedback("✓ تم حفظ ملف PDF العربي المتعدد الصفحات في: ${file.name}")
                    },
                    onExportStarterZip = { proj ->
                        val file = BlueprintAndScaffoldManager.generateStarterBoilerplateZip(
                            this@MainActivity,
                            proj,
                            currentServerUrl
                        )
                        showFeedback("✓ تم توليد أرشيف الكود المتكامل: ${file.name}")
                    },
                    onExportMarkdown = { proj, customContent ->
                        val file = NativeShareAndPdfHelper.exportMarkdownSpec(this@MainActivity, proj, customContent)
                        showFeedback("✓ تم تصدير وثيقة Markdown: ${file.name}")
                    },
                    onUpdateProjectPrompt = { proj, updatedPrompt, feedbackMsg ->
                        lifecycleScope.launch {
                            database.projectDao().insertOrUpdate(
                                proj.copy(
                                    currentPromptText = updatedPrompt.trim(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            showFeedback(feedbackMsg)
                        }
                    },
                    onTogglePinProject = { proj ->
                        lifecycleScope.launch {
                            database.projectDao().insertOrUpdate(
                                proj.copy(
                                    isPinned = !proj.isPinned,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            showFeedback(if (!proj.isPinned) "⭐ تم تثبيت المشروع في الأعلى" else "تم إلغاء تثبيت المشروع")
                        }
                    },
                    onDeleteProject = { proj ->
                        lifecycleScope.launch {
                            database.projectDao().deleteById(proj.id)
                            showFeedback("🗑️ تم حذف المشروع: ${proj.name}")
                        }
                    },
                    onAnalyzeWireframeUri = { proj, uri ->
                        lifecycleScope.launch {
                            val visionSpec = PromptEngineeringEngine.analyzeWireframeImage(
                                this@MainActivity,
                                uri,
                                proj.name
                            )
                            val merged = buildString {
                                appendLine(proj.currentPromptText ?: proj.rawIdea)
                                appendLine("\n---\n")
                                appendLine(visionSpec)
                            }
                            database.projectDao().insertOrUpdate(
                                proj.copy(
                                    currentPromptText = merged,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            showFeedback("✓ تم تحليل المخطط البصري وإلحاق مواصفات UI/UX بالمشروع")
                        }
                    },
                    onCopyText = { label, text ->
                        NativeShareAndPdfHelper.copyToClipboard(this@MainActivity, label, text)
                        showFeedback("✓ تم نسخ ($label) إلى الحافظة")
                    },
                    onBackupAllProjects = {
                        lifecycleScope.launch {
                            val file = NativeShareAndPdfHelper.exportAllProjectsBackupJson(this@MainActivity, projects)
                            showFeedback("✓ تم حفظ النسخة الاحتياطية لجميع المشاريع: ${file.name}")
                        }
                    },
                    onRestoreBackupProjects = {
                        lifecycleScope.launch {
                            val restored = NativeShareAndPdfHelper.restoreProjectsFromBackupJson(this@MainActivity)
                            if (restored.isNotEmpty()) {
                                restored.forEach { database.projectDao().insertOrUpdate(it) }
                                showFeedback("✓ تمت استعادة ${restored.size} مشاريع من النسخة الاحتياطية بنجاح")
                            } else {
                                showFeedback("ℹ️ لا توجد نسخة احتياطية محفوظة مسبقاً للاستعادة")
                            }
                        }
                    },
                    onNewProject = { name, idea ->
                        lifecycleScope.launch {
                            ForegroundGenerationService.startTask(
                                this@MainActivity,
                                "تحليل مشروع: $name",
                                "جارٍ توليد المواصفات الهندسية..."
                            )
                            val generatedPrompt = cloudApiService.analyzeAndGeneratePrompt(
                                idea = idea.ifBlank { name },
                                targetPlatform = "ANDROID_KOTLIN"
                            ).getOrNull()

                            database.projectDao().insertOrUpdate(
                                ProjectEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    description = idea.ifBlank { name }.take(140),
                                    type = "NEW_APP",
                                    targetPlatform = "ANDROID_KOTLIN",
                                    rawIdea = idea,
                                    currentPromptText = generatedPrompt
                                )
                            )
                            ForegroundGenerationService.stopTask(this@MainActivity)
                            showFeedback("✓ تم حفظ المشروع وتوليد البرومبت الهندسي بنجاح")
                        }
                    },
                    onSelectProject = { }
                )
            }
        }
    }
}
