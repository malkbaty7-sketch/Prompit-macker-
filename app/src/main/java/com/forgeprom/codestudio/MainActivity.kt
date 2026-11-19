package com.forgeprom.codestudio

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.ui.CardVisualStyle
import com.forgeprom.codestudio.core.ui.ForgepromTheme
import com.forgeprom.codestudio.core.ui.StudioVisualSettings
import com.forgeprom.codestudio.core.ui.ThemeSurfaceMode
import com.forgeprom.codestudio.core.ui.VisualSettingsPreferences
import com.forgeprom.codestudio.core.ux.LocalOfflineTtsReader
import com.forgeprom.codestudio.core.ux.OfflineTtsReader
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.presentation.screens.MainAppScreen
import com.forgeprom.codestudio.presentation.screens.UxPreferences
import com.forgeprom.codestudio.presentation.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: StudioViewModel
    private lateinit var offlineTtsReader: OfflineTtsReader
    private var pendingShortcutAction by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShortcutAction(intent)?.let { pendingShortcutAction = it }
    }

    override fun onStop() {
        if (::offlineTtsReader.isInitialized) {
            offlineTtsReader.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::offlineTtsReader.isInitialized) {
            offlineTtsReader.shutdown()
        }
        super.onDestroy()
    }

    private fun extractShortcutAction(intent: Intent?): String? {
        return when (intent?.action) {
            "com.forgeprom.codestudio.SHORTCUT_NEW_PROJECT",
            "com.forgeprom.codestudio.SHORTCUT_STUDIO",
            "com.forgeprom.codestudio.SHORTCUT_SECURITY" -> intent.action
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[StudioViewModel::class.java]
        offlineTtsReader = OfflineTtsReader(this)
        pendingShortcutAction = extractShortcutAction(intent)

        setContent {
            var visualSettings by remember {
                mutableStateOf(VisualSettingsPreferences.load(this@MainActivity))
            }

            fun updateAndSaveVisualSettings(updated: StudioVisualSettings) {
                visualSettings = updated
                VisualSettingsPreferences.save(this@MainActivity, updated)
            }

            ForgepromTheme(visualSettings = visualSettings) {
                val projects by viewModel.projects.collectAsStateWithLifecycle()
                val archivedProjects by viewModel.archivedProjects.collectAsStateWithLifecycle()
                val networkState by viewModel.networkState.collectAsStateWithLifecycle()
                val hardwareReport by viewModel.hardwareReport.collectAsStateWithLifecycle()
                val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
                val isCheckingServer by viewModel.isCheckingServer.collectAsStateWithLifecycle()
                val currentServerUrl by viewModel.currentServerUrl.collectAsStateWithLifecycle()
                val downloadStatusMap by viewModel.downloadStatusMap.collectAsStateWithLifecycle()
                val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()
                val partialModelSizesMB by viewModel.partialModelSizesMB.collectAsStateWithLifecycle()
                val downloadedModelIds by viewModel.downloadedModelIds.collectAsStateWithLifecycle()
                val activeDownloadingIds by viewModel.activeDownloadingIds.collectAsStateWithLifecycle()
                val researchResult by viewModel.researchResult.collectAsStateWithLifecycle()
                val isResearching by viewModel.isResearching.collectAsStateWithLifecycle()
                val researchCacheCount by viewModel.researchCacheCount.collectAsStateWithLifecycle()
                val exportCacheStats by viewModel.exportCacheStats.collectAsStateWithLifecycle()
                val busyStatusMessage by viewModel.busyStatusMessage.collectAsStateWithLifecycle()
                val promptVersionsMap by viewModel.promptVersionsMap.collectAsStateWithLifecycle()
                val allBlueprints by viewModel.allBlueprints.collectAsStateWithLifecycle()
                val projectScoresMap by viewModel.projectScoresMap.collectAsStateWithLifecycle()
                val projectSecurityScoresMap by viewModel.projectSecurityScoresMap.collectAsStateWithLifecycle()
                val diagnosticsReport by viewModel.diagnosticsReport.collectAsStateWithLifecycle()
                val isRunningDiagnostics by viewModel.isRunningDiagnostics.collectAsStateWithLifecycle()
                val models by viewModel.models.collectAsStateWithLifecycle()
                val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()

                var pendingModelDownload by remember { mutableStateOf<ModelDownloadManager.GgufModelInfo?>(null) }
                var isFocusMode by remember {
                    mutableStateOf(UxPreferences.isFocusModeEnabled(this@MainActivity))
                }
                var showOnboarding by remember {
                    mutableStateOf(!UxPreferences.hasCompletedOnboarding(this@MainActivity))
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    pendingModelDownload?.let { model ->
                        pendingModelDownload = null
                        viewModel.startActualModelDownload(model)
                    }
                }

                CompositionLocalProvider(LocalOfflineTtsReader provides offlineTtsReader) {
                    MainAppScreen(
                        projects = projects,
                        archivedProjects = archivedProjects,
                        availableRamMB = hardwareReport.availableRamMB,
                        hardwareReport = hardwareReport,
                        networkState = networkState,
                        serverStatus = serverStatus,
                        isCheckingServer = isCheckingServer,
                        currentServerUrl = currentServerUrl,
                        models = models,
                        blueprints = allBlueprints,
                        downloadStatusMap = downloadStatusMap,
                        downloadProgressMap = downloadProgressMap,
                        partialModelSizesMB = partialModelSizesMB,
                        downloadedModelIds = downloadedModelIds,
                        activeDownloadingIds = activeDownloadingIds,
                        researchResult = researchResult,
                        isResearching = isResearching,
                        researchCacheCount = researchCacheCount,
                        exportCacheStats = exportCacheStats,
                        busyStatusMessage = busyStatusMessage,
                        shortcutAction = pendingShortcutAction,
                        onConsumeShortcutAction = { pendingShortcutAction = null },
                        promptVersionsMap = promptVersionsMap,
                        projectScoresMap = projectScoresMap,
                        projectSecurityScoresMap = projectSecurityScoresMap,
                        diagnosticsReport = diagnosticsReport,
                        isRunningDiagnostics = isRunningDiagnostics,
                        onRunDiagnostics = viewModel::runServiceDiagnostics,
                        onRunSelfHeal = viewModel::runSelfHeal,
                        onRestorePromptSnapshot = viewModel::restorePromptSnapshot,
                        onBatchPinProjects = viewModel::batchPinProjects,
                        onBatchArchiveProjects = viewModel::batchArchiveProjects,
                        onBatchDeleteProjects = viewModel::batchDeleteProjects,
                        onBatchBackupProjects = viewModel::batchBackupProjects,
                        onExitApp = { finish() },
                        feedbackEvent = feedbackEvent,
                        onConsumeFeedback = viewModel::consumeFeedback,
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
                        onReplayOnboarding = { showOnboarding = true },
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
                        onCheckServer = { viewModel.checkServer(forceCheck = true) },
                        onUpdateServerUrl = viewModel::updateServerUrl,
                        onResetServerUrl = viewModel::resetServerUrl,
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
                                viewModel.startActualModelDownload(model)
                            }
                        },
                        onCancelModelDownload = viewModel::cancelModelDownload,
                        onDeleteDownloadedModel = viewModel::deleteDownloadedModel,
                        onRunResearch = { query -> viewModel.runResearch(query, bypassCache = false) },
                        onForceRefreshResearch = { query -> viewModel.runResearch(query, bypassCache = true) },
                        onClearResearchCache = viewModel::clearResearchCache,
                        onClearExportCache = viewModel::clearExportCache,
                        onUseBlueprint = viewModel::useBlueprint,
                        onSaveAsBlueprint = viewModel::saveProjectAsCustomBlueprint,
                        onDeleteCustomBlueprint = viewModel::deleteCustomBlueprint,
                        onRedactProjectSecrets = viewModel::redactSecretsInProject,
                        onShareProject = { proj ->
                            if (!NativeShareAndPdfHelper.shareProjectPrompt(this@MainActivity, proj)) {
                                viewModel.showFeedback("⚠️ لا يوجد تطبيق متاح للمشاركة على هذا الجهاز.")
                            }
                        },
                        onShareExportedFile = { file, mimeType ->
                            if (!NativeShareAndPdfHelper.shareExportedFile(this@MainActivity, file, mimeType)) {
                                viewModel.showFeedback("⚠️ تعذر فتح قائمة مشاركة الملف على هذا الجهاز.")
                            }
                        },
                        onExportPdf = viewModel::exportPdf,
                        onExportStarterZip = { proj, config -> viewModel.exportStarterZip(proj, config) },
                        onExportMarkdown = viewModel::exportMarkdown,
                        onUpdateProjectPrompt = viewModel::updateProjectPrompt,
                        onTogglePinProject = viewModel::togglePinProject,
                        onArchiveProject = viewModel::archiveProject,
                        onRestoreArchivedProject = viewModel::restoreArchivedProject,
                        onDuplicateProject = viewModel::duplicateProject,
                        onAutoRefineProject = viewModel::autoRefineProject,
                        onDeleteProject = viewModel::deleteProject,
                        onAnalyzeWireframeUri = viewModel::analyzeWireframeUri,
                        onCopyText = { label, text, isSensitive ->
                            NativeShareAndPdfHelper.copyToClipboard(this@MainActivity, label, text, isSensitive)
                            viewModel.showFeedback(
                                if (isSensitive) "🔒 تم نسخ ($label) بحماية الحافظة الحساسة (مسح تلقائي بعد 45 ثانية)"
                                else "✓ تم نسخ ($label) إلى الحافظة"
                            )
                        },
                        onBackupAllProjects = { viewModel.backupAllProjectsInternal(null) },
                        onRestoreBackupProjects = { viewModel.restoreBackupInternal(null) },
                        onExportBackupToUri = { uri, password -> viewModel.exportBackupToSafUri(uri, password) },
                        onRestoreBackupFromUri = { uri, password, onPwdNeeded ->
                            viewModel.restoreBackupFromSafUri(uri, password, onPwdNeeded)
                        },
                        onImportSpecFromUri = viewModel::importProjectSpecFromUri,
                        onBatchBoostAllProjects = viewModel::batchBoostAllProjects,
                        onExportArchitecturePng = viewModel::exportArchitecturePng,
                        onVerifyModelIntegrity = viewModel::verifyModelIntegrity,
                        onAddCustomModel = viewModel::addCustomGgufModel,
                        onRemoveCustomModel = viewModel::removeCustomGgufModel,
                        estimateModelFit = viewModel::estimateModelFit,
                        onNewProject = viewModel::createNewProject,
                        onSelectProject = { }
                    )
                }
            }
        }
    }
}
