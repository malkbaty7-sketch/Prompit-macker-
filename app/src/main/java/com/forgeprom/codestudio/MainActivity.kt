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
import com.forgeprom.codestudio.core.ui.ForgepromTheme
import com.forgeprom.codestudio.data.local.db.AppDatabase
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
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
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgePOS\n- المعمارية: Clean Architecture + Room DB\n- الميزات: كاشير سريع، باركود ML Kit، طباعة حرارية ESC/POS دون إنترنت.",
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
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgeClinic\n- تشفير AES-256 محلي، سجل زيارات مرضى، وتصدير تقارير طبية PDF باللغة العربية.",
                        isPinned = false
                    )
                )
            }
        }

        setContent {
            ForgepromTheme {
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
                        showFeedback("✓ تم حفظ ملف PDF العربي في: ${file.name}")
                    },
                    onExportStarterZip = { proj ->
                        val file = BlueprintAndScaffoldManager.generateStarterBoilerplateZip(this@MainActivity, proj)
                        showFeedback("✓ تم توليد هيكل كود المشروع: ${file.name}")
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
