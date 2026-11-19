package com.forgeprom.codestudio.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.service.ForegroundGenerationService
import com.forgeprom.codestudio.core.ux.PromptSnapshot
import com.forgeprom.codestudio.core.ux.PromptVersionHistoryStore
import com.forgeprom.codestudio.data.local.db.AppDatabase
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * وحدة إدارة الحالة والخدمات الرئيسية (StudioViewModel)
 * تحفظ كافة حالات التطبيق والعمليات الجارية عبر تغييرات التوجيه (Rotation) والهواتف القابلة للطي وتعدد النوافذ،
 * وتنسّق بين المستودع المحلي Room والخدمات الخلفية وتنزيل النماذج والبحث الهجين والنسخ الاحتياطي.
 */
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val repository = ProjectRepository(database.projectDao())
    private val hardwareProfiler = HardwareProfiler(appContext)
    private val networkMonitor = NetworkMonitor(appContext)
    private val modelDownloadManager = ModelDownloadManager(appContext, hardwareProfiler)
    private val cloudApiService = CloudApiService(appContext)

    val models: List<ModelDownloadManager.GgufModelInfo> = modelDownloadManager.getRecommendedModels()
    val blueprints: List<BlueprintAndScaffoldManager.IndustryBlueprint> = BlueprintAndScaffoldManager.getReadyBlueprints()

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

    private val _historyRefreshCounter = MutableStateFlow(0)
    val historyRefreshCounter: StateFlow<Int> = _historyRefreshCounter.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<UxFeedbackEvent?>(null)
    val feedbackEvent: StateFlow<UxFeedbackEvent?> = _feedbackEvent.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.seedInitialProjectsIfEmpty()
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
            val snapshot = withContext(Dispatchers.IO) {
                val hw = hardwareProfiler.getFullHardwareReport()
                val installed = models.filter { modelDownloadManager.isModelDownloaded(it) }
                    .map { it.id }
                    .toSet()
                val partials = models.associate { it.id to modelDownloadManager.getPartialDownloadedMB(it) }
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

    fun getProjectHistory(projectId: String): List<PromptSnapshot> =
        PromptVersionHistoryStore.getSnapshots(appContext, projectId)

    fun restorePromptSnapshot(project: ProjectEntity, snapshot: PromptSnapshot) {
        viewModelScope.launch {
            try {
                PromptVersionHistoryStore.recordSnapshot(
                    context = appContext,
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
                _historyRefreshCounter.update { it + 1 }
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

    fun exportStarterZip(project: ProjectEntity) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تجميع أرشيف كود ${project.targetPlatform} لمشروع \"${project.name}\"..."
            try {
                val file = withContext(Dispatchers.IO) {
                    BlueprintAndScaffoldManager.generateStarterBoilerplateZip(
                        appContext,
                        project,
                        _currentServerUrl.value
                    )
                }
                refreshDeviceTelemetry()
                showFeedback(
                    "✓ تم توليد أرشيف كود (${project.targetPlatform}): ${file.name}",
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

    fun updateProjectPrompt(project: ProjectEntity, updatedPrompt: String, feedbackMsg: String) {
        viewModelScope.launch {
            try {
                val reasonLabel = when {
                    feedbackMsg.contains("السلسلة") -> "قبل اعتماد السلسلة الهندسية (PRD)"
                    feedbackMsg.contains("OWASP") -> "قبل تحصين OWASP الأمني"
                    feedbackMsg.contains("تحسين") -> "قبل التحسين التلقائي"
                    else -> "قبل تعديل يدوي"
                }
                PromptVersionHistoryStore.recordSnapshot(
                    context = appContext,
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
                _historyRefreshCounter.update { it + 1 }
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
                PromptVersionHistoryStore.recordSnapshot(
                    context = appContext,
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
                _historyRefreshCounter.update { it + 1 }
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
                PromptVersionHistoryStore.recordSnapshot(
                    context = appContext,
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
                _historyRefreshCounter.update { it + 1 }
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
            _busyStatusMessage.value = "جارٍ تشفير وحفظ النسخة الاحتياطية (AES-256)..."
            try {
                val snapshot = repository.getAllOnce()
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportAllProjectsBackupJson(appContext, snapshot, portablePassword)
                }
                val modeTag = when {
                    result.isPortablePasswordProtected -> "مشفّرة محمولة PBKDF2"
                    result.isEncrypted -> "مشفّرة AES-256"
                    else -> "JSON"
                }
                showFeedback("🔒 تم حفظ نسخة احتياطية ($modeTag • SHA:${result.sha256Short}) لـ ${snapshot.size} مشاريع")
            } catch (_: Exception) {
                showFeedback("⚠️ تعذر حفظ النسخة الاحتياطية المشفّرة.")
            } finally {
                _busyStatusMessage.value = null
            }
        }
    }

    fun exportBackupToSafUri(uri: Uri, portablePassword: String? = null) {
        viewModelScope.launch {
            _busyStatusMessage.value = "جارٍ تصدير النسخة الاحتياطية إلى المسار المختار..."
            try {
                val snapshot = repository.getAllOnce()
                val result = withContext(Dispatchers.IO) {
                    NativeShareAndPdfHelper.exportBackupToUri(appContext, uri, snapshot, portablePassword)
                }
                val modeTag = if (result.isPortablePasswordProtected) "محمولة عابرة للأجهزة بكلمة مرور" else "مشفّرة AES-256 للجهاز"
                showFeedback("📤 تم تصدير ${snapshot.size} مشاريع بنجاح ($modeTag • SHA:${result.sha256Short})")
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
        } else if (restored.projects.isNotEmpty()) {
            val merge = repository.smartMergeRestoredProjects(restored.projects)
            showFeedback(
                "✓ دمج ذكي آمن (SHA-256): أُضيف ${merge.addedCount} • حُدّث ${merge.updatedCount} • حُمي ${merge.skippedOlderCount} أحدث محلياً"
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
