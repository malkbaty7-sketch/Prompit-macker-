package com.forgeprom.codestudio.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgeprom.codestudio.core.diagnostics.ServiceDiagnosticsEngine
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.core.service.ForegroundGenerationService
import com.forgeprom.codestudio.core.ux.PromptSnapshot
import com.forgeprom.codestudio.data.local.db.AppDatabase
import com.forgeprom.codestudio.data.local.model.CustomBlueprintEntity
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.local.repository.ProjectRepository
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import com.forgeprom.codestudio.presentation.screens.UxFeedbackEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * وحدة إدارة الحالة والخدمات الرئيسية (StudioViewModel)
 * تحفظ كافة حالات التطبيق والعمليات الجارية عبر تغييرات التوجيه (Rotation) والهواتف القابلة للطي وتعدد النوافذ،
 * وتنسّق بين المستودع المحلي Room v3 وسجل الإصدارات والقوالب المخصصة ومحرك التشخيص والتشفير والتصدير.
 */
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val repository = ProjectRepository(
        projectDao = database.projectDao(),
        promptVersionDao = database.promptVersionDao(),
        customBlueprintDao = database.customBlueprintDao()
    )
    private val hardwareProfiler = HardwareProfiler(appContext)
    private val networkMonitor = NetworkMonitor(appContext)
    private val modelDownloadManager = ModelDownloadManager(appContext, hardwareProfiler)
    private val cloudApiService = CloudApiService(appContext)

    private val _models = MutableStateFlow(modelDownloadManager.getAllModels())
    val models: StateFlow<List<ModelDownloadManager.GgufModelInfo>> = _models.asStateFlow()
    private val builtInBlueprints: List<BlueprintAndScaffoldManager.IndustryBlueprint> =
        BlueprintAndScaffoldManager.getReadyBlueprints()

    val projects: StateFlow<List<ProjectEntity>> = repository.activeProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedProjects: StateFlow<List<ProjectEntity>> = repository.archivedProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * سجل إصدارات البرومبت من Room (مع ربط CASCADE تلقائي عند حذف المشروع)
     */
    val promptVersionsMap: StateFlow<Map<String, List<PromptSnapshot>>> =
        repository.promptVersionsByProject.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /**
     * دمج القوالب المخصصة (Room) في المقدمة متبوعة بالقوالب الصناعية الجاهزة
     */
    val allBlueprints: StateFlow<List<BlueprintAndScaffoldManager.IndustryBlueprint>> =
        repository.customBlueprints
            .map { customEntities ->
                val customList = customEntities.map { entity ->
                    BlueprintAndScaffoldManager.IndustryBlueprint(
                        id = entity.id,
                        title = entity.title,
                        category = entity.category,
                        targetPlatform = entity.targetPlatform,
                        description = entity.description,
                        rawIdea = entity.rawIdea,
                        prebuiltPrompt = entity.prebuiltPrompt,
                        isCustom = true
                    )
                }
                customList + builtInBlueprints
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = builtInBlueprints
            )

    /**
     * حساب درجات جودة البرومبت خارج خيط الواجهة (Dispatchers.Default) لسلاسة 60fps
     */
    val projectScoresMap: StateFlow<Map<String, Int>> =
        combine(projects, archivedProjects) { active, archived -> active + archived }
            .map { all ->
                all.associate { proj ->
                    proj.id to PromptEngineeringEngine
                        .evaluatePromptQuality(proj.currentPromptText ?: proj.rawIdea)
                        .totalScore
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    /**
     * حساب درجات الأمان OWASP خارج خيط الواجهة (Dispatchers.Default)
     */
    val projectSecurityScoresMap: StateFlow<Map<String, Int>> =
        combine(projects, archivedProjects) { active, archived -> active + archived }
            .map { all ->
                all.associate { proj ->
                    proj.id to VaultAndEnterpriseManager.calculateSecurityScore(
                        VaultAndEnterpriseManager.auditProjectSecurity(proj)
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    val networkState: StateFlow<NetworkMonitor.NetworkState> = networkMonitor.observeNetworkState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = networkMonitor.getCurrentNetworkState()
    )

    private val _hardwareReport = MutableStateFlow(hardwareProfiler.getFullHardwareReport())
    val hardwareReport: StateFlow<HardwareProfiler.DeviceHardwareReport> = _hardwareReport.asStateFlow()

    private val _serverStatus = MutableStateFlow<CloudApiService.ServerHealthResult?>(null)
    val serverStatus: StateFlow<CloudApiService.ServerHealthResult?> = _serverStatus.asStateFlow()

    private val _isCheckingServer = MutableStateFlow(false)
    val isCheckingServer: StateFlow<Boolean> = _isCheckingServer.asStateFlow()

    private val _currentServerUrl = MutableStateFlow(cloudApiService.getServerUrl())
    val currentServerUrl: StateFlow<String> = _currentServerUrl.asStateFlow()

    private val _downloadStatusMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, String>> = _downloadStatusMap.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    private val _partialModelSizesMB = MutableStateFlow<Map<String, Long>>(emptyMap())
    val partialModelSizesMB: StateFlow<Map<String, Long>> = _partialModelSizesMB.asStateFlow()

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

    private val _activeDownloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val activeDownloadingIds: StateFlow<Set<String>> = _activeDownloadingIds.asStateFlow()

    private val _researchResult = MutableStateFlow("")
    val researchResult: StateFlow<String> = _researchResult.asStateFlow()

    private val _isResearching = MutableStateFlow(false)
    val isResearching: StateFlow<Boolean> = _isResearching.asStateFlow()

    private val _researchCacheCount = MutableStateFlow(cloudApiService.getCachedResearchCount())
    val researchCacheCount: StateFlow<Int> = _researchCacheCount.asStateFlow()

    private val _exportCacheStats = MutableStateFlow(NativeShareAndPdfHelper.ExportCacheStats(0, 0L))
    val exportCacheStats: StateFlow<NativeShareAndPdfHelper.ExportCacheStats> = _exportCacheStats.asStateFlow()

    private val _busyStatusMessage = MutableStateFlow<String?>(null)
    val busyStatusMessage: StateFlow<String?> = _busyStatusMessage.asStateFlow()

    private val _diagnosticsReport = MutableStateFlow<ServiceDiagnosticsEngine.FullDiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<ServiceDiagnosticsEngine.FullDiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _isRunningDiagnostics = MutableStateFlow(false)
    val isRunningDiagnostics: StateFlow<Boolean> = _isRunningDiagnostics.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<UxFeedbackEvent?>(null)
    val feedbackEvent: StateFlow<UxFeedbackEvent?> = _feedbackEvent.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.seedInitialProjectsIfEmpty()
            withContext(Dispatchers.IO) {
                repository.migrateLegacyHistoryIfNeeded(appContext)
            }
        }
        refreshDeviceTelemetry()
        observeNotificationCancelRequests()
        observeNetworkChangesForServerProbe()
    }

    private fun observeNotificationCancelRequests() {
        viewModelScope.launch {
            ForegroundGenerationService.cancelRequests.collect {
                cancelAllActiveDownloads()
            }
        }
    }

    private fun observeNetworkChangesForServerProbe() {
        viewModelScope.launch {
            var lastOnline: Boolean? = null
            var lastAirplane: Boolean? = null
            networkState.collect { state ->
                if (lastOnline != state.isOnline || lastAirplane != state.isAirplaneMode) {
                    lastOnline = state.isOnline
                    lastAirplane = state.isAirplaneMode
                    checkServer(forceCheck = false)
                }
            }
        }
    }

    fun showFeedback(
        msg: String,
        exportedFile: Pair<File, String>? = null,
        undoArchivedProject: ProjectEntity? = null
    ) {
        _feedbackEvent.value = UxFeedbackEvent(
            message = msg,
            exportedFile = exportedFile,
            undoArchivedProject = undoArchivedProject
        )
    }

    fun consumeFeedback() {
        _feedbackEvent.value = null
    }

    fun refreshDeviceTelemetry() {
        viewModelScope.launch {
            val currentModels = _models.value
            val snapshot = withContext(Dispatchers.IO) {
                val hw = hardwareProfiler.getFullHardwareReport()
                val installed = currentModels.filter { modelDownloadManager.isModelDownloaded(it) }
                    .map { it.id }
                    .toSet()
                val partials = currentModels.associate { it.id to modelDownloadManager.getPartialDownloadedMB(it) }
                    .filterValues { it > 0L }
                val exports = NativeShareAndPdfHelper.getExportCacheStats(appContext)
                TelemetrySnapshot(hw, installed, partials, exports)
            }
            _hardwareReport.value = snapshot.hardware
            _downloadedModelIds.value = snapshot.installedIds
            _partialModelSizesMB.value = snapshot.partialSizesMB
            _exportCacheStats.value = snapshot.exportStats
            _researchCacheCount.value = cloudApiService.getCachedResearchCount()
        }
    }

    private data class TelemetrySnapshot(
        val hardware: HardwareProfiler.DeviceHardwareReport,
        val installedIds: Set<String>,
        val partialSizesMB: Map<String, Long>,
        val exportStats: NativeShareAndPdfHelper.ExportCacheStats
    )

    fun checkServer(forceCheck: Boolean = true) {
        viewModelScope.launch {
            _isCheckingServer.value = true
            _hardwareReport.value = withContext(Dispatchers.IO) { hardwareProfiler.getFullHardwareReport() }
            _serverStatus.value = cloudApiService.checkServerConnection(forceCheck = forceCheck)
            _isCheckingServer.value = false
        }
    }

    fun updateServerUrl(newUrl: String) {
        cloudApiService.setServerUrl(newUrl)
            .onSuccess { cleaned ->
                _currentServerUrl.value = cleaned
                showFeedback("🔒 تم حفظ رابط الخادم المشفّر (HTTPS): $cleaned")
                checkServer(forceCheck = true)
            }
            .onFailure { err ->
                showFeedback("⚠️ ${err.message ?: "رابط الخادم غير صالح"}")
            }
    }

    fun resetServerUrl() {
        _currentServerUrl.value = cloudApiService.resetToDefaultUrl()
        showFeedback("✓ تمت استعادة الخادم الافتراضي الآمن")
        checkServer(forceCheck = true)
    }

    fun startActualModelDownload(model: ModelDownloadManager.GgufModelInfo) {
        if (activeDownloadJobs.containsKey(model.id)) {
            showFeedback("ℹ️ التنزيل قيد التقدم بالفعل للنموذج: ${model.name}")
            return
        }

        val job = viewModelScope.launch {
            _activeDownloadingIds.update { it + model.id }
            ForegroundGenerationService.startTask(
                appContext,
                "تنزيل النموذج ${model.name}",
                "جارٍ فحص الذاكرة وبدء التنزيل الآمن...",
                progressPercent = 0
            )
            try {
                modelDownloadManager.downloadModelWithResume(model).collect { progress ->
                    when (progress) {
                        is ModelDownloadManager.DownloadProgress.CheckingMemory -> {
                            val msg = "جارٍ فحص الذاكرة الحرة (${progress.availableMB} MB)..."
                            _downloadStatusMap.update { it + (model.id to msg) }
                            ForegroundGenerationService.updateProgress(appContext, "تنزيل ${model.name}", msg, 0)
                        }
                        is ModelDownloadManager.DownloadProgress.Downloading -> {
                            val speedEta = ModelDownloadManager.formatSpeedAndEta(progress.speedMBps, progress.etaSeconds)
                            val msg = buildString {
                                append("جارٍ التنزيل: ${progress.progressPercent}% (${progress.downloadedMB}/${progress.totalMB} MB)")
                                if (speedEta.isNotBlank()) append(" • $speedEta")
                            }
                            _downloadStatusMap.update { it + (model.id to msg) }
                            _downloadProgressMap.update { it + (model.id to progress.progressPercent) }
                            ForegroundGenerationService.updateProgress(
                                appContext,
                                "تنزيل ${model.name}",
                                msg,
                                progress.progressPercent
                            )
                        }
                        is ModelDownloadManager.DownloadProgress.VerifyingChecksum -> {
                            val msg = "جارٍ التحقق من سلامة الملف وبصمة SHA-256..."
                            _downloadStatusMap.update { it + (model.id to msg) }
                            _downloadProgressMap.update { it + (model.id to 100) }
                            ForegroundGenerationService.updateProgress(appContext, "تحقق ${model.name}", msg, 100)
                        }
                        is ModelDownloadManager.DownloadProgress.Completed -> {
                            val msg = "✓ اكتمل التنزيل بنجاح (SHA-256: ${progress.sha256.take(12)}...)"
                            _downloadStatusMap.update { it + (model.id to msg) }
                            _downloadProgressMap.update { it - model.id }
                            showFeedback("✓ تم تثبيت النموذج المحلي بنجاح: ${model.name}")
                        }
                        is ModelDownloadManager.DownloadProgress.Failed -> {
                            _downloadStatusMap.update { it + (model.id to "⚠️ ${progress.reason}") }
                            _downloadProgressMap.update { it - model.id }
                        }
                    }
                }
            } catch (_: CancellationException) {
                _downloadStatusMap.update { it + (model.id to "تم إيقاف التنزيل مؤقتاً (يمكنك الاستكمال من نقطة التوقف)") }
                _downloadProgressMap.update { it - model.id }
            } finally {
                activeDownloadJobs.remove(model.id)
                _activeDownloadingIds.update { it - model.id }
                if (activeDownloadJobs.isEmpty()) {
                    ForegroundGenerationService.stopTask(appContext)
                }
                refreshDeviceTelemetry()
            }
        }
        activeDownloadJobs[model.id] = job
    }

    fun cancelModelDownload(model: ModelDownloadManager.GgufModelInfo) {
        activeDownloadJobs.remove(model.id)?.cancel()
        _activeDownloadingIds.update { it - model.id }
        _downloadProgressMap.update { it - model.id }
        if (activeDownloadJobs.isEmpty()) {
            ForegroundGenerationService.stopTask(appContext)
        }
        _downloadStatusMap.update { it + (model.id to "تم إيقاف التنزيل (يمكنك الاستكمال من نقطة التوقف لاحقاً)") }
        showFeedback("تم إيقاف تنزيل النموذج: ${model.name}")
        refreshDeviceTelemetry()
    }

    private fun cancelAllActiveDownloads() {
        val ids = activeDownloadJobs.keys.toList()
        ids.forEach { id ->
            activeDownloadJobs.remove(id)?.cancel()
            _downloadStatusMap.update { it + (id to "تم إيقاف التنزيل من شريط الإشعارات") }
        }
        _activeDownloadingIds.value = emptySet()
        _downloadProgressMap.value = emptyMap()
        ForegroundGenerationService.stopTask(appContext)
        refreshDeviceTelemetry()
    }

    fun deleteDownloadedModel(model: ModelDownloadManager.GgufModelInfo) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                modelDownloadManager.deleteDownloadedModel(model)
            }
            if (deleted) {
                _downloadStatusMap.update { it - model.id }
                _downloadProgressMap.update { it - model.id }
                refreshDeviceTelemetry()
                showFeedback("🗑️ تم حذف ملف النموذج لتحرير المساحة: ${model.name}")
            } else {
                showFeedback("⚠️ تعذر حذف ملف النموذج أو أنه غير موجود.")
            }
        }
    }

    fun estimateModelFit(model: ModelDownloadManager.GgufModelInfo): ModelDownloadManager.ModelRuntimeFit {
        return modelDownloadManager.estimateRuntimeFit(model, _hardwareReport.value)
    }

    fun addCustomGgufModel(name: String, url: String, sizeMB: Long, params: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                modelDownloadManager.addCustomModel(
                    name = name,
                    downloadUrl = url,
                    fileSizeMB = sizeMB,
                    parameters = params
                )
            }
            result.onSuccess { added ->
                _models.value = modelDownloadManager.getAllModels()
                refreshDeviceTelemetry()
                showFeedback("✓ تمت إضافة النموذج المخصّص (${added.name}) بنجاح إلى مركز النماذج")
            }.onFailure { err ->
                showFeedback("⚠️ ${err.message ?: "تعذر إضافة النموذج المخصّص"}")
            }
        }
    }

    fun removeCustomGgufModel(modelId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                modelDownloadManager.removeCustomModel(modelId)
            }
            _downloadStatusMap.update { it - modelId }
            _downloadProgressMap.update { it - modelId }
            _models.value = modelDownloadManager.getAllModels()
            refreshDeviceTelemetry()
            showFeedback("🗑️ تم حذف النموذج المخصّص وملفاته بنجاح")
        }
    }

    fun verifyModelIntegrity(model: ModelDownloadManager.GgufModelInfo) {
        viewModelScope.launch {
            _downloadStatusMap.update { it + (model.id to "جارٍ فحص ترويسة GGUF وحساب بصمة SHA-256...") }
            val result = withContext(Dispatchers.IO) {
                modelDownloadManager.verifyInstalledModelIntegrity(model)
            }
            _downloadStatusMap.update { it + (model.id to result.message) }
            showFeedback(if (result.isValid) result.message else "⚠️ ${result.message}")
        }
    }

    fun runResearch(query: String, bypassCache: Boolean = false) {
        viewModelScope.launch {
            _isResearching.value = true
            try {
                _researchResult.value = cloudApiService.performTechnicalResearch(query, bypassCache = bypassCache)
                _researchCacheCount.value = cloudApiService.getCachedResearchCount()
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر إتمام البحث التقني حالياً.")
            } finally {
                _isResearching.value = false
            }
        }
    }

    fun clearResearchCache() {
        val cleared = cloudApiService.clearResearchCache()
        _researchCacheCount.value = 0
        showFeedback("🧹 تم مسح الذاكرة المؤقتة للبحث التقني ($cleared استعلامات)")
    }

    fun clearExportCache() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NativeShareAndPdfHelper.clearExportCache(appContext)
            }
            refreshDeviceTelemetry()
            if (result.deletedFiles > 0) {
                showFeedback("🧹 تم تنظيف ${result.deletedFiles} ملفات تصدير وتحرير ${result.freedKB} KB")
            } else {
                showFeedback("ℹ️ مجلدات التصدير نظيفة بالفعل ولا توجد ملفات مؤقتة")
            }
        }
    }

    fun runServiceDiagnostics(ttsReady: Boolean) {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            try {
                _diagnosticsReport.value = ServiceDiagnosticsEngine.runFullDiagnostics(
                    context = appContext,
                    repository = repository,
                    hardwareProfiler = hardwareProfiler,
                    cloudApiService = cloudApiService,
                    ttsReady = ttsReady
                )
            } finally {
                _isRunningDiagnostics.value = false
            }
        }
    }

    fun runSelfHeal(ttsReady: Boolean) {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            try {
                cloudApiService.resetCircuitBreaker()
                val cleanup = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.clearExportCache(appContext)
                }
                refreshDeviceTelemetry()
                val report = ServiceDiagnosticsEngine.runFullDiagnostics(
                    context = appContext,
                    repository = repository,
                    hardwareProfiler = hardwareProfiler,
                    cloudApiService = cloudApiService,
                    ttsReady = ttsReady
                )
                _diagnosticsReport.value = report
                showFeedback("🩺 تم تنفيذ الإصلاح الذاتي (تصفير قاطع الشبكة + تنظيف ${cleanup.deletedFiles} ملفات) — الجاهزية ${report.overallScore}%")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر إتمام خطوات الإصلاح الذاتي.")
            } finally {
                _isRunningDiagnostics.value = false
            }
        }
    }

    fun getProjectHistory(projectId: String): List<PromptSnapshot> =
        promptVersionsMap.value[projectId].orEmpty()

    fun restorePromptSnapshot(project: ProjectEntity, snapshot: PromptSnapshot) {
        viewModelScope.launch {
            try {
                repository.recordPromptVersion(
                    projectId = project.id,
                    previousText = project.currentPromptText ?: project.rawIdea,
                    reason = "قبل استعادة إصدار سابق"
                )
                repository.saveProject(
                    project.copy(
                        currentPromptText = snapshot.promptText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showFeedback("✓ تمت استعادة الإصدار بنجاح (${snapshot.reason})")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر استعادة الإصدار المختار.")
            }
        }
    }

    fun batchPinProjects(selectedList: List<ProjectEntity>, pin: Boolean) {
        viewModelScope.launch {
            repository.setPinnedBatch(selectedList.map { it.id }, pin)
            showFeedback(if (pin) "⭐ تم تثبيت ${selectedList.size} مشاريع بمعاملة ذرية" else "تم إلغاء تثبيت ${selectedList.size} مشاريع")
        }
    }

    fun batchArchiveProjects(selectedList: List<ProjectEntity>) {
        viewModelScope.launch {
            repository.setArchivedBatch(selectedList.map { it.id }, archived = true)
            showFeedback("📦 تم نقل ${selectedList.size} مشاريع إلى الأرشيف الآمن")
        }
    }

    fun batchDeleteProjects(selectedList: List<ProjectEntity>) {
        viewModelScope.launch {
            repository.deleteBatch(selectedList.map { it.id })
            showFeedback("🗑️ تم حذف ${selectedList.size} مشاريع نهائياً")
        }
    }

    fun batchBackupProjects(selectedList: List<ProjectEntity>) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ إنشاء نسخة احتياطية مشفّرة للمشاريع المحددة..."
            try {
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportAllProjectsBackupJson(appContext, selectedList)
                }
                showFeedback("🔒 تم حفظ نسخة مشفّرة لـ ${selectedList.size} مشاريع (SHA:${result.sha256Short})")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر حفظ النسخة الاحتياطية للمشاريع المحددة.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun useBlueprint(bp: BlueprintAndScaffoldManager.IndustryBlueprint) {
        viewModelScope.launch {
            try {
                repository.saveProject(
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
    }

    fun saveProjectAsCustomBlueprint(project: ProjectEntity) {
        viewModelScope.launch {
            try {
                val blueprint = CustomBlueprintEntity(
                    id = "custom-${UUID.randomUUID()}",
                    title = project.name,
                    category = "قوالبي المخصصة",
                    targetPlatform = project.targetPlatform,
                    description = project.description.ifBlank { "قالب هندسي مخصّص محفوظ من مشروع ${project.name}" },
                    rawIdea = project.rawIdea,
                    prebuiltPrompt = project.currentPromptText ?: project.rawIdea,
                    createdAt = System.currentTimeMillis()
                )
                repository.saveCustomBlueprint(blueprint)
                showFeedback("★ تم حفظ \"${project.name}\" كقالب قابل لإعادة الاستخدام في تبويب القوالب")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر حفظ القالب المخصّص حالياً.")
            }
        }
    }

    fun deleteCustomBlueprint(blueprintId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomBlueprint(blueprintId)
                showFeedback("🗑️ تم حذف القالب المخصّص من مكتبة القوالب")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر حذف القالب المخصّص.")
            }
        }
    }

    fun redactSecretsInProject(project: ProjectEntity) {
        viewModelScope.launch {
            try {
                val currentText = project.currentPromptText ?: project.rawIdea
                val promptScan = withContext(Dispatchers.Default) {
                    VaultAndEnterpriseManager.detectAndRedactSecrets(currentText)
                }
                val ideaScan = withContext(Dispatchers.Default) {
                    VaultAndEnterpriseManager.detectAndRedactSecrets(project.rawIdea)
                }
                val totalFound = promptScan.foundCount + (if (project.currentPromptText != null) ideaScan.foundCount else 0)
                if (totalFound == 0) {
                    showFeedback("✓ لم يتم رصد أي مفاتيح API أو كلمات مرور مكشوفة في \"${project.name}\"")
                    return@launch
                }
                repository.recordPromptVersion(
                    projectId = project.id,
                    previousText = currentText,
                    reason = "قبل إخفاء الأسرار المكشوفة (Secret Redactor)"
                )
                repository.saveProject(
                    project.copy(
                        rawIdea = ideaScan.redactedText,
                        currentPromptText = promptScan.redactedText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val typesSummary = (promptScan.detectedTypes + ideaScan.detectedTypes).distinct().joinToString("، ")
                showFeedback("🛡️ تم إخفاء $totalFound أسرار مكشوفة ($typesSummary) واستبدالها بـ BuildConfig.SECRET_KEY")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تنفيذ فحص وإخفاء الأسرار حالياً.")
            }
        }
    }

    fun exportPdf(project: ProjectEntity) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ توليد ملف PDF العربي المنسّق لمشروع \"${project.name}\"..."
            try {
                val file = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.generateNativeRtlPdf(appContext, project)
                }
                refreshDeviceTelemetry()
                showFeedback("✓ تم حفظ PDF العربي المنسّق: ${file.name}", exportedFile = file to "application/pdf")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر توليد ملف PDF العربي — تحقق من المساحة التخزينية.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun exportStarterZip(
        project: ProjectEntity,
        config: BlueprintAndScaffoldManager.ScaffoldExportConfig = BlueprintAndScaffoldManager.ScaffoldExportConfig()
    ) {
        viewModelScope.launch {
            val bundleLabel = if (config.includeExecutiveBundlePdf) "الحزمة التنفيذية الشاملة (PDF + كود)" else "أرشيف كود ${project.targetPlatform}"
            _busyStatusMessage.value = "جارٍ تجميع $bundleLabel لمشروع \"${project.name}\"..."
            try {
                val file = withContext(Dispatchers.IO) {
                    BlueprintAndScaffoldManager.generateStarterBoilerplateZip(
                        context = appContext,
                        project = project,
                        serverUrl = _currentServerUrl.value,
                        config = config
                    )
                }
                refreshDeviceTelemetry()
                showFeedback(
                    "✓ تم توليد $bundleLabel: ${file.name}",
                    exportedFile = file to "application/zip"
                )
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر توليد أرشيف الكود ZIP — تحقق من المساحة التخزينية.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun exportMarkdown(project: ProjectEntity, customContent: String?) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تصدير وثيقة Markdown..."
            try {
                val file = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportMarkdownSpec(appContext, project, customContent)
                }
                refreshDeviceTelemetry()
                showFeedback("✓ تم تصدير وثيقة Markdown: ${file.name}", exportedFile = file to "text/markdown")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تصدير ملف Markdown.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun exportArchitecturePng(project: ProjectEntity) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ رسم وتصدير المخطط المعماري عالي الدقة (PNG)..."
            try {
                val file = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportArchitectureDiagramPng(appContext, project)
                }
                refreshDeviceTelemetry()
                showFeedback("🖼️ تم تصدير المخطط المعماري عالي الدقة: ${file.name}", exportedFile = file to "image/png")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تصدير صورة المخطط المعماري PNG.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun importProjectSpecFromUri(uri: Uri) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ استيراد المواصفات واكتشاف المنصة وفحص الأسرار تلقائياً..."
            try {
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.importProjectSpecFromUri(appContext, uri)
                }
                val imported = result.project
                if (imported == null || result.errorMessage != null) {
                    showFeedback("⚠️ ${result.errorMessage ?: "تعذر قراءة ملف المواصفات المختار."}")
                } else {
                    repository.saveProject(imported)
                    val secretNote = if (result.redactedSecretsCount > 0) {
                        " • 🛡️ أُخفي ${result.redactedSecretsCount} أسرار تلقائياً"
                    } else ""
                    showFeedback("📥 تم استيراد \"${imported.name}\" (${imported.targetPlatform})$secretNote")
                }
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر استيراد ملف المواصفات.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun batchBoostAllProjects() {
        viewModelScope.launch {
            val activeList = projects.value
            if (activeList.isEmpty()) {
                showFeedback("ℹ️ لا توجد مشاريع نشطة للتحسين.")
                return@launch
            }
            _busyStatusMessage.value = "جارٍ تحسين وتحصين جميع المشاريع الناقصة (Quality + OWASP)..."
            try {
                var boostedCount = 0
                activeList.forEach { proj ->
                    val currentText = proj.currentPromptText ?: proj.rawIdea
                    val qualityScore = withContext(Dispatchers.Default) {
                        PromptEngineeringEngine.evaluatePromptQuality(currentText).totalScore
                    }
                    val securityScore = withContext(Dispatchers.Default) {
                        VaultAndEnterpriseManager.calculateSecurityScore(
                            VaultAndEnterpriseManager.auditProjectSecurity(proj)
                        )
                    }
                    if (qualityScore < 85 || securityScore < 100) {
                        repository.recordPromptVersion(
                            projectId = proj.id,
                            previousText = currentText,
                            reason = "قبل التحسين والتحصين الجماعي (Batch Boost)"
                        )
                        val updatedPrompt = withContext(Dispatchers.Default) {
                            val refined = if (qualityScore < 85) {
                                PromptEngineeringEngine.autoRefinePrompt(proj)
                            } else {
                                currentText
                            }
                            val tempProj = proj.copy(currentPromptText = refined)
                            val missingOwasp = VaultAndEnterpriseManager.buildMissingOwaspHardeningBlock(tempProj)
                            if (missingOwasp.isNotBlank()) "$refined\n\n$missingOwasp" else refined
                        }
                        repository.saveProject(
                            proj.copy(
                                currentPromptText = updatedPrompt.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        boostedCount++
                    }
                }
                if (boostedCount > 0) {
                    showFeedback("🚀 تم تحسين وتحصين $boostedCount مشاريع دفعة واحدة (مع حفظ إصدارات سابقة في Room)")
                } else {
                    showFeedback("✓ جميع مشاريعك بالفعل محصّنة (OWASP 100%) وبجودة ممتازة (+85%)")
                }
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر إتمام التحسين الجماعي للمشاريع.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun updateProjectPrompt(project: ProjectEntity, updatedPrompt: String, feedbackMsg: String) {
        viewModelScope.launch {
            try {
                val reasonLabel = when {
                    feedbackMsg.contains("السلسلة") -> "قبل اعتماد السلسلة الهندسية (PRD)"
                    feedbackMsg.contains("OWASP") -> "قبل تحصين OWASP الأمني"
                    feedbackMsg.contains("تحسين") -> "قبل التحسين التلقائي"
                    else -> "قبل تعديل يدوي"
                }
                repository.recordPromptVersion(
                    projectId = project.id,
                    previousText = project.currentPromptText ?: project.rawIdea,
                    reason = reasonLabel
                )
                repository.saveProject(
                    project.copy(
                        currentPromptText = updatedPrompt.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showFeedback(feedbackMsg)
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تحديث البرومبت في قاعدة البيانات.")
            }
        }
    }

    fun togglePinProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.saveProject(
                project.copy(
                    isPinned = !project.isPinned,
                    updatedAt = System.currentTimeMillis()
                )
            )
            showFeedback(if (!project.isPinned) "⭐ تم تثبيت المشروع في الأعلى" else "تم إلغاء تثبيت المشروع")
        }
    }

    fun archiveProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.setArchived(project.id, archived = true)
            showFeedback(
                msg = "📦 تم نقل \"${project.name}\" إلى الأرشيف",
                undoArchivedProject = project
            )
        }
    }

    fun restoreArchivedProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.setArchived(project.id, archived = false)
            showFeedback("✓ تمت استعادة المشروع من الأرشيف: ${project.name}")
        }
    }

    fun duplicateProject(project: ProjectEntity) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val cloned = project.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${project.name} (نسخة)",
                    isPinned = false,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now
                )
                repository.saveProject(cloned)
                showFeedback("✓ تم استنساخ المشروع بنجاح: ${cloned.name}")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر استنساخ المشروع حالياً.")
            }
        }
    }

    fun autoRefineProject(project: ProjectEntity) {
        viewModelScope.launch {
            try {
                repository.recordPromptVersion(
                    projectId = project.id,
                    previousText = project.currentPromptText ?: project.rawIdea,
                    reason = "قبل التحسين التلقائي الجراحي"
                )
                val refined = withContext(Dispatchers.Default) {
                    PromptEngineeringEngine.autoRefinePrompt(project)
                }
                repository.saveProject(
                    project.copy(
                        currentPromptText = refined,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showFeedback("✨ تم استكمال البنود الناقصة وتحسين برومبت \"${project.name}\" (${project.targetPlatform})")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تنفيذ التحسين التلقائي حالياً.")
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteById(project.id)
            showFeedback("🗑️ تم حذف المشروع نهائياً: ${project.name}")
        }
    }

    fun analyzeWireframeUri(project: ProjectEntity, uri: Uri) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ استخراج الألوان الفعلية وهيكل المخطط البصري..."
            try {
                repository.recordPromptVersion(
                    projectId = project.id,
                    previousText = project.currentPromptText ?: project.rawIdea,
                    reason = "قبل إلحاق تحليل المخطط البصري"
                )
                val visionSpec = withContext(Dispatchers.IO) {
                    PromptEngineeringEngine.analyzeWireframeImage(
                        appContext,
                        uri,
                        project.name,
                        project.targetPlatform
                    )
                }
                val merged = buildString {
                    appendLine(project.currentPromptText ?: project.rawIdea)
                    appendLine("\n---\n")
                    appendLine(visionSpec)
                }
                repository.saveProject(
                    project.copy(
                        currentPromptText = merged,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showFeedback("✓ تم استخراج لوحة الألوان الفعلية وإلحاق مواصفات UI/UX بالمشروع")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر قراءة وتحليل الصورة المختارة.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun backupAllProjectsInternal(portablePassword: String? = null) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تشفير وحفظ النسخة الاحتياطية الشاملة v4 (المشاريع + الإصدارات + القوالب)..."
            try {
                val snapshot = repository.getAllOnce()
                val versions = repository.getAllVersionsOnce()
                val customBps = repository.getAllCustomBlueprintsOnce()
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportAllProjectsBackupJson(
                        context = appContext,
                        projects = snapshot,
                        portablePassword = portablePassword,
                        promptVersions = versions,
                        customBlueprints = customBps
                    )
                }
                val modeTag = when {
                    result.isPortablePasswordProtected -> "مشفّرة محمولة PBKDF2"
                    result.isEncrypted -> "مشفّرة AES-256"
                    else -> "JSON"
                }
                showFeedback(
                    "🔒 تم حفظ النسخة الشاملة v4 ($modeTag • SHA:${result.sha256Short}): ${snapshot.size} مشاريع • ${versions.size} إصدارات • ${customBps.size} قوالب"
                )
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر حفظ النسخة الاحتياطية المشفّرة.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun exportBackupToSafUri(uri: Uri, portablePassword: String? = null) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تصدير النسخة الشاملة v4 إلى المسار المختار..."
            try {
                val snapshot = repository.getAllOnce()
                val versions = repository.getAllVersionsOnce()
                val customBps = repository.getAllCustomBlueprintsOnce()
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportBackupToUri(
                        context = appContext,
                        uri = uri,
                        projects = snapshot,
                        portablePassword = portablePassword,
                        promptVersions = versions,
                        customBlueprints = customBps
                    )
                }
                val modeTag = if (result.isPortablePasswordProtected) "محمولة عابرة للأجهزة بكلمة مرور" else "مشفّرة AES-256 للجهاز"
                showFeedback(
                    "📤 تم تصدير النسخة الشاملة v4 ($modeTag • SHA:${result.sha256Short}): ${snapshot.size} مشاريع • ${versions.size} إصدارات • ${customBps.size} قوالب"
                )
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر تصدير ملف النسخة الاحتياطية إلى المسار المختار.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun restoreBackupInternal(portablePassword: String? = null) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ فك تشفير وتدقيق النسخة الاحتياطية..."
            try {
                val restored = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.restoreProjectsFromBackupJson(appContext, portablePassword)
                }
                applyRestoreResult(restored)
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر استعادة النسخة الاحتياطية.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun restoreBackupFromSafUri(uri: Uri, portablePassword: String? = null, onPasswordRequired: () -> Unit = {}) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ استيراد وتدقيق ملف النسخة الاحتياطية..."
            try {
                val restored = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.restoreBackupFromUri(appContext, uri, portablePassword)
                }
                if (restored.requiresPassword) {
                    showFeedback("🔑 ${restored.errorMessage ?: "النسخة محمية بكلمة مرور"}")
                    onPasswordRequired()
                } else {
                    applyRestoreResult(restored)
                }
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر استيراد ملف النسخة الاحتياطية المختار.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    private suspend fun applyRestoreResult(restored: NativeShareAndPdfHelper.BackupRestoreResult) {
        if (restored.errorMessage != null) {
            showFeedback("⚠️ ${restored.errorMessage}")
        } else if (restored.projects.isNotEmpty() || restored.customBlueprints.isNotEmpty()) {
            val ecoMerge = repository.smartMergeRestoredEcosystem(
                restoredProjects = restored.projects,
                restoredVersions = restored.promptVersions,
                restoredBlueprints = restored.customBlueprints
            )
            val m = ecoMerge.projectMerge
            showFeedback(
                "✓ دمج شامل v4 (SHA-256): أُضيف ${m.addedCount} • حُدّث ${m.updatedCount} • حُمي ${m.skippedOlderCount} • إصدارات ${ecoMerge.restoredVersionsCount} • قوالب ${ecoMerge.restoredBlueprintsCount}"
            )
        } else {
            showFeedback("ℹ️ لا توجد نسخة احتياطية محفوظة مسبقاً للاستعادة")
        }
    }

    fun createNewProject(name: String, idea: String, targetPlatform: String) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تحليل الفكرة وتوليد المواصفات الهندسية لمنصة $targetPlatform..."
            try {
                val generatedPrompt = cloudApiService.analyzeAndGeneratePrompt(
                    idea = idea.ifBlank { name },
                    targetPlatform = targetPlatform
                ).getOrNull()

                repository.saveProject(
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
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    override fun onCleared() {
        cancelAllActiveDownloads()
        super.onCleared()
    }
}
