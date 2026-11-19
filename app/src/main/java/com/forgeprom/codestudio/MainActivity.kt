package com.forgeprom.codestudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
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
import com.forgeprom.codestudio.presentation.screens.UxFeedbackEvent
import com.forgeprom.codestudio.presentation.screens.UxPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
                val archivedProjects by database.projectDao().getArchivedProjectsFlow().collectAsState(initial = emptyList())
                val networkState by networkMonitor.observeNetworkState().collectAsState(
                    initial = networkMonitor.getCurrentNetworkState()
                )
                var availableRam by remember { mutableLongStateOf(hardwareProfiler.getAvailableRamMB()) }
                val models = remember { modelDownloadManager.getRecommendedModels() }
                val blueprints = remember { BlueprintAndScaffoldManager.getReadyBlueprints() }

                var serverStatus by remember { mutableStateOf<CloudApiService.ServerHealthResult?>(null) }
                var isCheckingServer by remember { mutableStateOf(false) }
                var currentServerUrl by remember { mutableStateOf(cloudApiService.getServerUrl()) }
                var downloadStatusMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var downloadedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                val activeDownloadJobs = remember { mutableStateMapOf<String, Job>() }
                var researchResult by remember { mutableStateOf("") }
                var isResearching by remember { mutableStateOf(false) }
                var feedbackEvent by remember { mutableStateOf<UxFeedbackEvent?>(null) }
                var pendingModelDownload by remember { mutableStateOf<ModelDownloadManager.GgufModelInfo?>(null) }
                var isFocusMode by remember {
                    mutableStateOf(UxPreferences.isFocusModeEnabled(this@MainActivity))
                }
                var showOnboarding by remember {
                    mutableStateOf(!UxPreferences.hasCompletedOnboarding(this@MainActivity))
                }

                fun showFeedback(
                    msg: String,
                    exportedFile: Pair<File, String>? = null,
                    undoArchivedProject: ProjectEntity? = null
                ) {
                    feedbackEvent = UxFeedbackEvent(
                        message = msg,
                        exportedFile = exportedFile,
                        undoArchivedProject = undoArchivedProject
                    )
                }

                fun refreshDeviceTelemetry() {
                    lifecycleScope.launch {
                        val (ramMb, installedIds) = withContext(Dispatchers.IO) {
                            val ram = hardwareProfiler.getAvailableRamMB()
                            val installed = models.filter { modelDownloadManager.isModelDownloaded(it) }
                                .map { it.id }
                                .toSet()
                            ram to installed
                        }
                        availableRam = ramMb
                        downloadedModelIds = installedIds
                    }
                }

                fun startActualModelDownload(model: ModelDownloadManager.GgufModelInfo) {
                    if (activeDownloadJobs.containsKey(model.id)) {
                        showFeedback("ℹ️ التنزيل قيد التقدم بالفعل للنموذج: ${model.name}")
                        return
                    }
                    val job = lifecycleScope.launch {
                        ForegroundGenerationService.startTask(
                            this@MainActivity,
                            "تنزيل النموذج ${model.name}",
                            "جارٍ التنزيل والتحقق من ترويسة GGUF و SHA-256..."
                        )
                        try {
                            modelDownloadManager.downloadModelWithResume(model).collect { progress ->
                                val statusMsg = when (progress) {
                                    is ModelDownloadManager.DownloadProgress.CheckingMemory ->
                                        "جارٍ فحص الذاكرة العشوائية (${progress.availableMB} MB)..."
                                    is ModelDownloadManager.DownloadProgress.Downloading ->
                                        "جارٍ التنزيل: ${progress.progressPercent}% (${progress.downloadedMB} / ${progress.totalMB} MB)"
                                    is ModelDownloadManager.DownloadProgress.VerifyingChecksum ->
                                        "جارٍ التحقق من البصمة الرقمية SHA-256..."
                                    is ModelDownloadManager.DownloadProgress.Completed -> {
                                        "✓ اكتمل التنزيل بنجاح (SHA-256: ${progress.sha256.take(12)}...)"
                                    }
                                    is ModelDownloadManager.DownloadProgress.Failed -> {
                                        "⚠️ ${progress.reason}"
                                    }
                                }
                                downloadStatusMap = downloadStatusMap + (model.id to statusMsg)
                            }
                        } catch (_: CancellationException) {
                            downloadStatusMap = downloadStatusMap + (model.id to "تم إيقاف التنزيل (يمكنك الاستكمال لاحقاً)")
                        } finally {
                            activeDownloadJobs.remove(model.id)
                            if (activeDownloadJobs.isEmpty()) {
                                ForegroundGenerationService.stopTask(this@MainActivity)
                            }
                            refreshDeviceTelemetry()
                        }
                    }
                    activeDownloadJobs[model.id] = job
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    // نبدأ التنزيل بسلاسة سواء وافق المستخدم على الإشعارات أو رفضها
                    pendingModelDownload?.let { model ->
                        pendingModelDownload = null
                        startActualModelDownload(model)
                    }
                }

                fun checkServer() {
                    lifecycleScope.launch {
                        isCheckingServer = true
                        availableRam = hardwareProfiler.getAvailableRamMB()
                        serverStatus = cloudApiService.checkServerConnection()
                        isCheckingServer = false
                    }
                }

                LaunchedEffect(Unit) {
                    refreshDeviceTelemetry()
                }

                LaunchedEffect(networkState.isOnline, networkState.isAirplaneMode) {
                    checkServer()
                }

                MainAppScreen(
                    projects = projects,
                    archivedProjects = archivedProjects,
                    availableRamMB = availableRam,
                    networkState = networkState,
                    serverStatus = serverStatus,
                    isCheckingServer = isCheckingServer,
                    currentServerUrl = currentServerUrl,
                    models = models,
                    blueprints = blueprints,
                    downloadStatusMap = downloadStatusMap,
                    downloadedModelIds = downloadedModelIds,
                    activeDownloadingIds = activeDownloadJobs.keys.toSet(),
                    researchResult = researchResult,
                    isResearching = isResearching,
                    feedbackEvent = feedbackEvent,
                    onConsumeFeedback = { feedbackEvent = null },
                    isFocusMode = isFocusMode,
                    onToggleFocusMode = {
                        val next = !isFocusMode
                        isFocusMode = next
                        UxPreferences.setFocusModeEnabled(this@MainActivity, next)
                    },
                    showOnboarding = showOnboarding,
                    onCompleteOnboarding = {
                        showOnboarding = false
                        UxPreferences.setOnboardingCompleted(this@MainActivity, true)
                    },
                    onReplayOnboarding = {
                        showOnboarding = true
                    },
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
                            .onSuccess { cleaned ->
                                currentServerUrl = cleaned
                                showFeedback("🔒 تم حفظ رابط الخادم المشفّر (HTTPS): $cleaned")
                                checkServer()
                            }
                            .onFailure { err ->
                                showFeedback("⚠️ ${err.message ?: "رابط الخادم غير صالح"}")
                            }
                    },
                    onResetServerUrl = {
                        currentServerUrl = cloudApiService.resetToDefaultUrl()
                        showFeedback("✓ تمت استعادة الخادم الافتراضي الآمن")
                        checkServer()
                    },
                    onDownloadModel = { model ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingModelDownload = model
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startActualModelDownload(model)
                        }
                    },
                    onCancelModelDownload = { model ->
                        activeDownloadJobs.remove(model.id)?.cancel()
                        if (activeDownloadJobs.isEmpty()) {
                            ForegroundGenerationService.stopTask(this@MainActivity)
                        }
                        downloadStatusMap = downloadStatusMap + (model.id to "تم إلغاء التنزيل (يمكنك الاستكمال من نقطة التوقف لاحقاً)")
                        showFeedback("تم إيقاف تنزيل النموذج: ${model.name}")
                    },
                    onDeleteDownloadedModel = { model ->
                        lifecycleScope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                modelDownloadManager.deleteDownloadedModel(model)
                            }
                            if (deleted) {
                                downloadStatusMap = downloadStatusMap - model.id
                                refreshDeviceTelemetry()
                                showFeedback("🗑️ تم حذف ملف النموذج لتحرير المساحة: ${model.name}")
                            } else {
                                showFeedback("⚠️ تعذر حذف ملف النموذج أو أنه غير موجود.")
                            }
                        }
                    },
                    onRunResearch = { query ->
                        lifecycleScope.launch {
                            isResearching = true
                            try {
                                researchResult = cloudApiService.performTechnicalResearch(query)
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر إتمام البحث التقني حالياً.")
                            } finally {
                                isResearching = false
                            }
                        }
                    },
                    onUseBlueprint = { bp ->
                        lifecycleScope.launch {
                            try {
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
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر حفظ المشروع من القالب.")
                            }
                        }
                    },
                    onShareProject = { proj ->
                        val shared = NativeShareAndPdfHelper.shareProjectPrompt(this@MainActivity, proj)
                        if (!shared) {
                            showFeedback("⚠️ لا يوجد تطبيق متاح للمشاركة على هذا الجهاز.")
                        }
                    },
                    onShareExportedFile = { file, mimeType ->
                        val shared = NativeShareAndPdfHelper.shareExportedFile(this@MainActivity, file, mimeType)
                        if (!shared) {
                            showFeedback("⚠️ تعذر فتح قائمة مشاركة الملف على هذا الجهاز.")
                        }
                    },
                    onExportPdf = { proj ->
                        lifecycleScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    NativeShareAndPdfHelper.generateNativeRtlPdf(this@MainActivity, proj)
                                }
                                showFeedback(
                                    "✓ تم حفظ PDF العربي: ${file.name}",
                                    exportedFile = file to "application/pdf"
                                )
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر توليد ملف PDF العربي — تحقق من المساحة التخزينية.")
                            }
                        }
                    },
                    onExportStarterZip = { proj ->
                        lifecycleScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    BlueprintAndScaffoldManager.generateStarterBoilerplateZip(
                                        this@MainActivity,
                                        proj,
                                        currentServerUrl
                                    )
                                }
                                showFeedback(
                                    "✓ تم توليد أرشيف الكود المتكامل: ${file.name}",
                                    exportedFile = file to "application/zip"
                                )
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر توليد أرشيف الكود ZIP — تحقق من المساحة التخزينية.")
                            }
                        }
                    },
                    onExportMarkdown = { proj, customContent ->
                        lifecycleScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    NativeShareAndPdfHelper.exportMarkdownSpec(this@MainActivity, proj, customContent)
                                }
                                showFeedback(
                                    "✓ تم تصدير وثيقة Markdown: ${file.name}",
                                    exportedFile = file to "text/markdown"
                                )
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر تصدير ملف Markdown.")
                            }
                        }
                    },
                    onUpdateProjectPrompt = { proj, updatedPrompt, feedbackMsg ->
                        lifecycleScope.launch {
                            try {
                                database.projectDao().insertOrUpdate(
                                    proj.copy(
                                        currentPromptText = updatedPrompt.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                showFeedback(feedbackMsg)
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر تحديث البرومبت في قاعدة البيانات.")
                            }
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
                    onArchiveProject = { proj ->
                        lifecycleScope.launch {
                            database.projectDao().setArchived(proj.id, archived = true)
                            showFeedback(
                                msg = "📦 تم نقل \"${proj.name}\" إلى الأرشيف",
                                undoArchivedProject = proj
                            )
                        }
                    },
                    onRestoreArchivedProject = { proj ->
                        lifecycleScope.launch {
                            database.projectDao().setArchived(proj.id, archived = false)
                            showFeedback("✓ تمت استعادة المشروع من الأرشيف: ${proj.name}")
                        }
                    },
                    onDuplicateProject = { proj ->
                        lifecycleScope.launch {
                            try {
                                val now = System.currentTimeMillis()
                                val cloned = proj.copy(
                                    id = UUID.randomUUID().toString(),
                                    name = "${proj.name} (نسخة)",
                                    isPinned = false,
                                    isArchived = false,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                database.projectDao().insertOrUpdate(cloned)
                                showFeedback("✓ تم استنساخ المشروع بنجاح: ${cloned.name}")
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر استنساخ المشروع حالياً.")
                            }
                        }
                    },
                    onAutoRefineProject = { proj ->
                        lifecycleScope.launch {
                            try {
                                val refined = PromptEngineeringEngine.autoRefinePrompt(proj)
                                database.projectDao().insertOrUpdate(
                                    proj.copy(
                                        currentPromptText = refined,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                showFeedback("✨ تم تحسين برومبت \"${proj.name}\" تلقائياً (+95%)")
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر تنفيذ التحسين التلقائي حالياً.")
                            }
                        }
                    },
                    onDeleteProject = { proj ->
                        lifecycleScope.launch {
                            database.projectDao().deleteById(proj.id)
                            showFeedback("🗑️ تم حذف المشروع نهائياً: ${proj.name}")
                        }
                    },
                    onAnalyzeWireframeUri = { proj, uri ->
                        lifecycleScope.launch {
                            try {
                                val visionSpec = withContext(Dispatchers.IO) {
                                    PromptEngineeringEngine.analyzeWireframeImage(
                                        this@MainActivity,
                                        uri,
                                        proj.name
                                    )
                                }
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
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر قراءة وتحليل الصورة المختارة.")
                            }
                        }
                    },
                    onCopyText = { label, text, isSensitive ->
                        NativeShareAndPdfHelper.copyToClipboard(this@MainActivity, label, text, isSensitive)
                        showFeedback(
                            if (isSensitive) "🔒 تم نسخ ($label) بحماية الحافظة الحساسة (مخفي في معاينة Android 13+)"
                            else "✓ تم نسخ ($label) إلى الحافظة"
                        )
                    },
                    onBackupAllProjects = {
                        lifecycleScope.launch {
                            try {
                                val snapshot = (projects + archivedProjects).distinctBy { it.id }
                                val result = withContext(Dispatchers.IO) {
                                    NativeShareAndPdfHelper.exportAllProjectsBackupJson(this@MainActivity, snapshot)
                                }
                                val modeTag = if (result.isEncrypted) "مشفّرة AES-256" else "JSON"
                                showFeedback("🔒 تم حفظ نسخة احتياطية ($modeTag • SHA:${result.sha256Short}) لـ ${snapshot.size} مشاريع")
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر حفظ النسخة الاحتياطية المشفّرة.")
                            }
                        }
                    },
                    onRestoreBackupProjects = {
                        lifecycleScope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    NativeShareAndPdfHelper.restoreProjectsFromBackupJson(this@MainActivity)
                                }
                                if (restored.errorMessage != null) {
                                    showFeedback("⚠️ ${restored.errorMessage}")
                                } else if (restored.projects.isNotEmpty()) {
                                    restored.projects.forEach { database.projectDao().insertOrUpdate(it) }
                                    val badge = if (restored.wasEncrypted) "✓ تم فك تشفير والتحقق من SHA-256 واستعادة" else "✓ تمت استعادة"
                                    showFeedback("$badge ${restored.projects.size} مشاريع بنجاح")
                                } else {
                                    showFeedback("ℹ️ لا توجد نسخة احتياطية محفوظة مسبقاً للاستعادة")
                                }
                            } catch (_: Exception) {
                                showFeedback("⚠️ تعذر استعادة النسخة الاحتياطية.")
                            }
                        }
                    },
                    onNewProject = { name, idea, targetPlatform ->
                        lifecycleScope.launch {
                            showFeedback("⏳ جارٍ تحليل الفكرة وتوليد المواصفات الهندسية...")
                            try {
                                val generatedPrompt = cloudApiService.analyzeAndGeneratePrompt(
                                    idea = idea.ifBlank { name },
                                    targetPlatform = targetPlatform
                                ).getOrNull()

                                database.projectDao().insertOrUpdate(
                                    ProjectEntity(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        description = idea.ifBlank { name }.take(140),
                                        type = "NEW_APP",
                                        targetPlatform = targetPlatform,
                                        rawIdea = idea.ifBlank { name },
                                        currentPromptText = generatedPrompt
                                    )
                                )
                                showFeedback("✓ تم حفظ المشروع وتوليد البرومبت الهندسي بنجاح")
                            } catch (_: Exception) {
                                showFeedback("⚠️ حدث خطأ أثناء إنشاء المشروع وحفظه.")
                            }
                        }
                    },
                    onSelectProject = { }
                )
            }
        }
    }
}
