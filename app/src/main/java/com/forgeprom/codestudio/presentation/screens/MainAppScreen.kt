package com.forgeprom.codestudio.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.R
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.ui.LocalAdaptiveScreenSpec
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.core.ui.StudioVisualSettings
import com.forgeprom.codestudio.core.ui.rememberAdaptiveScreenSpec
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import java.io.File

private data class NavDestinationItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private enum class ProjectSortMode(val labelAr: String) {
    RECENT("الأحدث"),
    QUALITY("الأعلى جودة"),
    ALPHABETICAL("الأبجدية")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    projects: List<ProjectEntity>,
    archivedProjects: List<ProjectEntity>,
    availableRamMB: Long,
    networkState: NetworkMonitor.NetworkState,
    serverStatus: CloudApiService.ServerHealthResult?,
    isCheckingServer: Boolean,
    currentServerUrl: String,
    models: List<ModelDownloadManager.GgufModelInfo>,
    blueprints: List<BlueprintAndScaffoldManager.IndustryBlueprint>,
    downloadStatusMap: Map<String, String>,
    downloadedModelIds: Set<String>,
    activeDownloadingIds: Set<String>,
    researchResult: String,
    isResearching: Boolean,
    feedbackEvent: UxFeedbackEvent?,
    onConsumeFeedback: () -> Unit,
    isFocusMode: Boolean,
    onToggleFocusMode: () -> Unit,
    showOnboarding: Boolean,
    onCompleteOnboarding: () -> Unit,
    onReplayOnboarding: () -> Unit,
    visualSettings: StudioVisualSettings,
    onUpdateVisualSettings: (StudioVisualSettings) -> Unit,
    onToggle3DDepth: () -> Unit,
    onToggleEyeComfort: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onCheckServer: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onResetServerUrl: () -> Unit,
    onDownloadModel: (ModelDownloadManager.GgufModelInfo) -> Unit,
    onCancelModelDownload: (ModelDownloadManager.GgufModelInfo) -> Unit,
    onDeleteDownloadedModel: (ModelDownloadManager.GgufModelInfo) -> Unit,
    onRunResearch: (String) -> Unit,
    onUseBlueprint: (BlueprintAndScaffoldManager.IndustryBlueprint) -> Unit,
    onShareProject: (ProjectEntity) -> Unit,
    onShareExportedFile: (File, String) -> Unit,
    onExportPdf: (ProjectEntity) -> Unit,
    onExportStarterZip: (ProjectEntity) -> Unit,
    onExportMarkdown: (ProjectEntity, String?) -> Unit,
    onUpdateProjectPrompt: (ProjectEntity, String, String) -> Unit,
    onTogglePinProject: (ProjectEntity) -> Unit,
    onArchiveProject: (ProjectEntity) -> Unit,
    onRestoreArchivedProject: (ProjectEntity) -> Unit,
    onDuplicateProject: (ProjectEntity) -> Unit,
    onAutoRefineProject: (ProjectEntity) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onAnalyzeWireframeUri: (ProjectEntity, Uri) -> Unit,
    onCopyText: (String, String, Boolean) -> Unit,
    onBackupAllProjects: () -> Unit,
    onRestoreBackupProjects: () -> Unit,
    onNewProject: (String, String, String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableIntStateOf(0) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var selectedProjectForDialog by remember { mutableStateOf<ProjectEntity?>(null) }
    var activeStudioProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectPendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectToEditPrompt by remember { mutableStateOf<ProjectEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPlatformFilter by remember { mutableStateOf("ALL") }
    var sortMode by remember { mutableStateOf(ProjectSortMode.RECENT) }
    var researchQuery by remember { mutableStateOf("") }

    // عرض إشعار Snackbar عائم ذكي مع زر تراجع أو مشاركة فورية دون تحريك عناصر الشاشة
    LaunchedEffect(feedbackEvent?.id) {
        val event = feedbackEvent ?: return@LaunchedEffect
        val actionLabel = when {
            event.undoArchivedProject != null -> "تراجع"
            event.exportedFile != null -> "مشاركة"
            else -> null
        }
        val duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = actionLabel,
            withDismissAction = true,
            duration = duration
        )
        if (result == SnackbarResult.ActionPerformed) {
            event.undoArchivedProject?.let { onRestoreArchivedProject(it) }
            event.exportedFile?.let { (file, mime) -> onShareExportedFile(file, mime) }
        }
        onConsumeFeedback()
    }

    val isOnline = serverStatus?.isConnected == true && !networkState.isAirplaneMode
    val currentActiveProject = activeStudioProject?.let { sel ->
        projects.find { it.id == sel.id }
    } ?: projects.firstOrNull()

    val projectScoresMap = remember(projects, archivedProjects) {
        (projects + archivedProjects).associate { proj ->
            proj.id to PromptEngineeringEngine.evaluatePromptQuality(
                proj.currentPromptText ?: proj.rawIdea
            ).totalScore
        }
    }

    val averageQualityScore = remember(projects, projectScoresMap) {
        if (projects.isEmpty()) 90
        else projects.map { projectScoresMap[it.id] ?: 80 }.average().toInt()
    }

    val filteredProjects = remember(projects, archivedProjects, searchQuery, selectedPlatformFilter, sortMode, projectScoresMap) {
        val sourceList = if (selectedPlatformFilter == "ARCHIVED") archivedProjects else projects
        val filtered = sourceList.filter { proj ->
            val matchesQuery = searchQuery.isBlank() ||
                proj.name.contains(searchQuery, ignoreCase = true) ||
                proj.description.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedPlatformFilter) {
                "ALL", "ARCHIVED" -> true
                "PINNED" -> proj.isPinned
                else -> proj.targetPlatform.equals(selectedPlatformFilter, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
        when (sortMode) {
            ProjectSortMode.RECENT -> filtered.sortedWith(
                compareByDescending<ProjectEntity> { it.isPinned }.thenByDescending { it.updatedAt }
            )
            ProjectSortMode.QUALITY -> filtered.sortedWith(
                compareByDescending<ProjectEntity> { it.isPinned }.thenByDescending { projectScoresMap[it.id] ?: 0 }
            )
            ProjectSortMode.ALPHABETICAL -> filtered.sortedWith(
                compareByDescending<ProjectEntity> { it.isPinned }.thenBy { it.name.lowercase() }
            )
        }
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(0, "المشاريع", Icons.Default.FolderOpen, "nav_projects"),
            NavDestinationItem(1, "الاستوديو", Icons.Default.AccountTree, "nav_studio"),
            NavDestinationItem(2, "القوالب", Icons.Default.AutoAwesomeMosaic, "nav_blueprints"),
            NavDestinationItem(3, "الأمان", Icons.Default.Security, "nav_enterprise"),
            NavDestinationItem(4, "النماذج", Icons.Default.Memory, "nav_models")
        )
    }

    BackHandler(enabled = activeTab != 0) {
        activeTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val adaptive = rememberAdaptiveScreenSpec(maxWidth = maxWidth, maxHeight = maxHeight)
        val isWideScreen = adaptive.useDualPane

        CompositionLocalProvider(LocalAdaptiveScreenSpec provides adaptive) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.testTag("ux_snackbar_host")
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (visual.isDarkTheme) Color(0xFF1E293B) else Color(0xFF0F172A),
                        contentColor = Color.White,
                        actionColor = visual.accentSecondary,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (adaptive.isUltraCompact) 6.dp else 10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Forgeprom Code Studio Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(if (adaptive.isUltraCompact) 30.dp else 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (adaptive.isUltraCompact) "Forgeprom" else "Forgeprom Code Studio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (adaptive.isUltraCompact) 13.sp else 15.sp,
                                    color = visual.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isOnline) "متصل (${serverStatus?.latencyMs ?: 0}ms) + محلي"
                                    else "محلي 100% (${networkState.connectionType})",
                                    fontSize = if (adaptive.isUltraCompact) 10.sp else 11.sp,
                                    color = if (isOnline) Color(0xFF10B981) else visual.accentPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = visual.surfaceBg),
                    actions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCommandPalette = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_command_palette_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "البحث الشامل والأوامر السريعة",
                                tint = visual.primaryText
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleFocusMode()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_focus_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isFocusMode) Icons.Default.FullscreenExit else Icons.Default.CenterFocusStrong,
                                contentDescription = if (isFocusMode) "إيقاف وضع التركيز" else "تفعيل وضع التركيز",
                                tint = if (isFocusMode) visual.accentPrimary else visual.secondaryText
                            )
                        }
                        if (!adaptive.isUltraCompact) {
                            IconButton(
                                onClick = { showAppearanceSheet = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_appearance_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "تخصيص المظهر والشاشة",
                                    tint = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary
                                )
                            }
                        }
                        Surface(
                            onClick = { showServerDialog = true },
                            color = if (isOnline) Color(0xFF065F46).copy(alpha = 0.85f) else Color(0xFF1E3A8A).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 6.dp).testTag("server_status_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    horizontal = if (adaptive.isUltraCompact) 7.dp else 10.dp,
                                    vertical = 6.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = "حالة الاتصال بالخادم",
                                    tint = if (isOnline) Color(0xFF34D399) else Color(0xFF93C5FD),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (!adaptive.isUltraCompact) {
                                    Text(
                                        text = if (isCheckingServer) "فحص..." else if (isOnline) "متصل" else "محلي",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!adaptive.useNavigationRail) {
                    NavigationBar(
                        containerColor = visual.surfaceBg,
                        contentColor = visual.primaryText
                    ) {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = activeTab == item.index,
                                onClick = {
                                    if (activeTab != item.index) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activeTab = item.index
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontSize = if (adaptive.isUltraCompact) 9.sp else 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = !adaptive.isUltraCompact,
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                // 7. زر إجراء عائم سياقي يتكيف مع التبويب النشط وحجم الشاشة
                val useCompactFab = adaptive.isUltraCompact || adaptive.isShortLandscape
                when (activeTab) {
                    0 -> {
                        if (useCompactFab) {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showNewProjectDialog = true
                                },
                                containerColor = visual.accentPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("new_project_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "مشروع جديد")
                            }
                        } else {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showNewProjectDialog = true
                                },
                                containerColor = visual.accentPrimary,
                                contentColor = Color.White,
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                text = { Text("مشروع جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("new_project_fab")
                            )
                        }
                    }
                    1 -> {
                        currentActiveProject?.let { proj ->
                            if (useCompactFab) {
                                FloatingActionButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAutoRefineProject(proj)
                                    },
                                    containerColor = visual.accentPrimary,
                                    contentColor = Color.White,
                                    modifier = Modifier.testTag("studio_quick_refine_fab")
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = "تحسين تلقائي سريع")
                                }
                            } else {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAutoRefineProject(proj)
                                    },
                                    containerColor = visual.accentPrimary,
                                    contentColor = Color.White,
                                    icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                    text = { Text("تحسين ذكي", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("studio_quick_refine_fab")
                                )
                            }
                        }
                    }
                    3 -> {
                        if (useCompactFab) {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBackupAllProjects()
                                },
                                containerColor = visual.accentPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.testTag("security_quick_backup_fab")
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = "نسخة احتياطية مشفّرة")
                            }
                        } else {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBackupAllProjects()
                                },
                                containerColor = visual.accentPrimary,
                                contentColor = Color.White,
                                icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                text = { Text("نسخة مشفّرة", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("security_quick_backup_fab")
                            )
                        }
                    }
                }
            },
            containerColor = visual.containerBg
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // NavigationRail جانبي تلقائي وقابل للتمرير للتابلت، القابلة للطي، والهواتف بالوضع الأفقي
                if (adaptive.useNavigationRail) {
                    NavigationRail(
                        containerColor = visual.surfaceBg,
                        contentColor = visual.primaryText,
                        modifier = Modifier.fillMaxHeight().testTag("wide_navigation_rail")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            navItems.forEach { item ->
                                NavigationRailItem(
                                    selected = activeTab == item.index,
                                    onClick = { activeTab = item.index },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                                    alwaysShowLabel = !adaptive.isShortLandscape,
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = adaptive.maxContentWidth)
                        .padding(horizontal = adaptive.horizontalOuterPadding, vertical = if (adaptive.isShortLandscape) 4.dp else 8.dp)
                ) {
                    // 1. وضع التركيز (Focus Mode) يطوي شريط المظهر ولوحة العتاد بنعومة لتوسيع مساحة العمل
                    AnimatedVisibility(
                        visible = !isFocusMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            StudioVisualControlBar(
                                visualSettings = visualSettings,
                                isFocusMode = isFocusMode,
                                onToggleFocusMode = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleFocusMode()
                                },
                                onToggle3DDepth = onToggle3DDepth,
                                onToggleEyeComfort = onToggleEyeComfort,
                                onToggleDarkTheme = onToggleDarkTheme,
                                onOpenAppearanceStudio = { showAppearanceSheet = true }
                            )

                            HardwareTelemetryHud3D(
                                availableRamMB = availableRamMB,
                                connectionType = networkState.connectionType,
                                projectsCount = projects.size,
                                averageQualityScore = averageQualityScore,
                                isWideScreen = isWideScreen,
                                onCheckServer = onCheckServer
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "main_tab_transition"
                    ) { currentTab ->
                    when (currentTab) {
                        0 -> {
                            val projectsListPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                Column(modifier = paneModifier) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("ابحث في المشاريع بالاسم أو الوصف...", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", modifier = Modifier.size(18.dp)) },
                                        trailingIcon = if (searchQuery.isNotEmpty()) {
                                            {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "مسح البحث", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        } else null,
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("project_search_input")
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    ) {
                                        val filters = listOf(
                                            "ALL" to "الكل (${projects.size})",
                                            "PINNED" to "المثبتة ⭐",
                                            "ANDROID_KOTLIN" to "Android Kotlin",
                                            "FLUTTER" to "Flutter",
                                            "REACT_NATIVE" to "React Native",
                                            "WEB" to "Web",
                                            "ARCHIVED" to "الأرشيف (${archivedProjects.size})"
                                        )
                                        items(filters, key = { it.first }) { (key, label) ->
                                            FilterChip(
                                                selected = selectedPlatformFilter == key,
                                                onClick = { selectedPlatformFilter = key },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }

                                    // 6. شريط الفرز الذكي وتلميح السحب السريع
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "فرز المشاريع",
                                            tint = visual.secondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        ProjectSortMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = sortMode == mode,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    sortMode = mode
                                                },
                                                label = { Text(mode.labelAr, fontSize = 10.sp, maxLines = 1) },
                                                modifier = Modifier.testTag("sort_mode_${mode.name}")
                                            )
                                        }
                                    }

                                    if (filteredProjects.isEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                            border = visual.cardBorder,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("projects_empty_state")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOff,
                                                    contentDescription = "لا توجد مشاريع مطابقة",
                                                    tint = visual.secondaryText,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Text(
                                                    text = if (projects.isEmpty()) "لا توجد مشاريع محفوظة بعد"
                                                    else "لا توجد مشاريع تطابق البحث أو الفلتر الحالي",
                                                    color = visual.primaryText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (13f + visual.fontDelta).sp
                                                )
                                                if (projects.isEmpty()) {
                                                    Button(
                                                        onClick = { showNewProjectDialog = true },
                                                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("إنشاء مشروع جديد", fontSize = 12.sp)
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = {
                                                            searchQuery = ""
                                                            selectedPlatformFilter = "ALL"
                                                        }
                                                    ) {
                                                        Text("مسح البحث وإظهار الكل", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                                        contentPadding = PaddingValues(bottom = 84.dp),
                                        modifier = Modifier.fillMaxSize().testTag("projects_list")
                                    ) {
                                        items(filteredProjects, key = { it.id }) { project ->
                                            val score = projectScoresMap[project.id] ?: 80
                                            val isArchivedView = selectedPlatformFilter == "ARCHIVED"
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { swipeValue ->
                                                    when (swipeValue) {
                                                        SwipeToDismissBoxValue.StartToEnd -> {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            if (isArchivedView) {
                                                                onRestoreArchivedProject(project)
                                                            } else {
                                                                onTogglePinProject(project)
                                                            }
                                                            false
                                                        }
                                                        SwipeToDismissBoxValue.EndToStart -> {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            if (isArchivedView) {
                                                                projectPendingDelete = project
                                                            } else {
                                                                onArchiveProject(project)
                                                            }
                                                            false
                                                        }
                                                        SwipeToDismissBoxValue.Settled -> false
                                                    }
                                                }
                                            )

                                            SwipeToDismissBox(
                                                state = dismissState,
                                                backgroundContent = {
                                                    val direction = dismissState.dismissDirection
                                                    val bgColor = when (direction) {
                                                        SwipeToDismissBoxValue.StartToEnd ->
                                                            if (isArchivedView) Color(0xFF065F46) else Color(0xFFB45309)
                                                        SwipeToDismissBoxValue.EndToStart ->
                                                            if (isArchivedView) Color(0xFF991B1B) else Color(0xFF1E3A8A)
                                                        else -> Color.Transparent
                                                    }
                                                    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                                        Alignment.CenterStart
                                                    } else {
                                                        Alignment.CenterEnd
                                                    }
                                                    val swipeLabel = when (direction) {
                                                        SwipeToDismissBoxValue.StartToEnd ->
                                                            if (isArchivedView) "استعادة" else if (project.isPinned) "إلغاء التثبيت" else "تثبيت ⭐"
                                                        SwipeToDismissBoxValue.EndToStart ->
                                                            if (isArchivedView) "حذف نهائي" else "أرشفة آمنة"
                                                        else -> ""
                                                    }
                                                    val swipeIcon = when (direction) {
                                                        SwipeToDismissBoxValue.StartToEnd ->
                                                            if (isArchivedView) Icons.Default.Unarchive else Icons.Default.Star
                                                        SwipeToDismissBoxValue.EndToStart ->
                                                            if (isArchivedView) Icons.Default.DeleteForever else Icons.Default.Archive
                                                        else -> Icons.Default.Archive
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(bgColor)
                                                            .padding(horizontal = 18.dp),
                                                        contentAlignment = alignment
                                                    ) {
                                                        if (swipeLabel.isNotEmpty()) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Icon(swipeIcon, contentDescription = swipeLabel, tint = Color.White, modifier = Modifier.size(20.dp))
                                                                Text(swipeLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            ) {
                                            Card(
                                                onClick = {
                                                    activeStudioProject = project
                                                    if (!isWideScreen) {
                                                        selectedProjectForDialog = project
                                                    }
                                                    onSelectProject(project)
                                                },
                                                colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                                elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                                                border = visual.cardBorder,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("project_card_${project.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(visual.contentPadding)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            ProjectQualityRing(score = score)
                                                            Column {
                                                                Text(
                                                                    text = project.name,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = visual.primaryText,
                                                                    fontSize = (13f + visual.fontDelta).sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                    modifier = Modifier.padding(top = 3.dp)
                                                                ) {
                                                                    Surface(
                                                                        color = visual.accentPrimary.copy(alpha = 0.2f),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = project.targetPlatform,
                                                                            color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                    Text(
                                                                        text = formatRelativeTimeAr(project.updatedAt),
                                                                        color = visual.secondaryText,
                                                                        fontSize = 9.sp,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (isArchivedView) {
                                                                IconButton(
                                                                    onClick = {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        onRestoreArchivedProject(project)
                                                                    },
                                                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("restore_project_btn_${project.id}")
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Unarchive,
                                                                        contentDescription = "استعادة من الأرشيف",
                                                                        tint = Color(0xFF10B981),
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            } else {
                                                                IconButton(
                                                                    onClick = {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        onTogglePinProject(project)
                                                                    },
                                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (project.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                                                        contentDescription = "تثبيت المشروع",
                                                                        tint = if (project.isPinned) Color(0xFFFBBF24) else visual.secondaryText,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = { projectPendingDelete = project },
                                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_project_btn_${project.id}")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DeleteOutline,
                                                                    contentDescription = "حذف المشروع",
                                                                    tint = Color(0xFFEF4444),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = project.description,
                                                        color = visual.secondaryText,
                                                        fontSize = (11f + visual.fontDelta).sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!isArchivedView) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        // شريط إجراءات سريعة مباشرة بلمسة واحدة
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .horizontalScroll(rememberScrollState()),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            AssistChip(
                                                                onClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    activeStudioProject = project
                                                                    activeTab = 1
                                                                },
                                                                label = { Text("الاستوديو", fontSize = 10.sp, maxLines = 1) },
                                                                leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                            )
                                                            AssistChip(
                                                                onClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    onCopyText(project.name, project.currentPromptText ?: project.rawIdea, false)
                                                                },
                                                                label = { Text("نسخ", fontSize = 10.sp, maxLines = 1) },
                                                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                            )
                                                            AssistChip(
                                                                onClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    onDuplicateProject(project)
                                                                },
                                                                label = { Text("استنساخ", fontSize = 10.sp, maxLines = 1) },
                                                                leadingIcon = { Icon(Icons.Default.FileCopy, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                            )
                                                            AssistChip(
                                                                onClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    onExportPdf(project)
                                                                },
                                                                label = { Text("PDF", fontSize = 10.sp, maxLines = 1) },
                                                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            }
                                        }
                                    }
                                    }
                                }
                            }

                            if (isWideScreen) {
                                // التخطيط القانوني List-Detail للتابلت والأجهزة القابلة للطي
                                Row(
                                    modifier = Modifier.fillMaxSize().testTag("projects_list_detail_layout"),
                                    horizontalArrangement = Arrangement.spacedBy(visual.itemSpacing)
                                ) {
                                    projectsListPane(Modifier.weight(1f).fillMaxHeight())
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                                        border = visual.cardBorder,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1.1f).fillMaxHeight()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(visual.contentPadding)
                                        ) {
                                            if (currentActiveProject != null) {
                                                ProjectDetailPanelContent(
                                                    project = currentActiveProject,
                                                    onOpenInStudio = {
                                                        activeStudioProject = it
                                                        activeTab = 1
                                                    },
                                                    onEditPrompt = { projectToEditPrompt = it },
                                                    onDuplicateProject = onDuplicateProject,
                                                    onArchiveProject = onArchiveProject,
                                                    onShareProject = onShareProject,
                                                    onExportPdf = onExportPdf,
                                                    onExportStarterZip = onExportStarterZip,
                                                    onCopyPrompt = {
                                                        onCopyText(it.name, it.currentPromptText ?: it.rawIdea, false)
                                                    }
                                                )
                                            } else {
                                                Text("اختر مشروعاً لعرض تفاصيله.", color = visual.secondaryText)
                                            }
                                        }
                                    }
                                }
                            } else {
                                projectsListPane(Modifier.fillMaxSize())
                            }
                        }

                        1 -> {
                            ChainedStudioWorkspace(
                                projects = projects,
                                selectedProject = currentActiveProject,
                                serverUrl = currentServerUrl,
                                isWideScreen = isWideScreen,
                                onSelectProject = { proj -> activeStudioProject = proj },
                                onEditPrompt = { projectToEditPrompt = it },
                                onUpdateProjectPrompt = onUpdateProjectPrompt,
                                onAnalyzeWireframeUri = onAnalyzeWireframeUri,
                                onCopyText = onCopyText,
                                onExportMarkdown = { proj, content -> onExportMarkdown(proj, content) }
                            )
                        }

                        2 -> {
                            Text(
                                text = "مكتبة القوالب الهندسية الجاهزة (${blueprints.size} قوالب)",
                                fontWeight = FontWeight.Bold,
                                color = visual.primaryText,
                                fontSize = (15f + visual.fontDelta).sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            val bpColumns = adaptive.gridColumns
                            val chunkedBlueprints = remember(blueprints, bpColumns) {
                                blueprints.chunked(bpColumns)
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(chunkedBlueprints) { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(visual.itemSpacing)
                                    ) {
                                        rowItems.forEach { bp ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                                elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                                                border = visual.cardBorder,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(adaptive.adaptiveCardPadding(visual.contentPadding)),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = bp.title,
                                                            fontWeight = FontWeight.Bold,
                                                            color = visual.primaryText,
                                                            fontSize = (13f + visual.fontDelta).sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = bp.category,
                                                            color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = bp.description,
                                                        color = visual.secondaryText,
                                                        fontSize = (11f + visual.fontDelta).sp
                                                    )
                                                    Button(
                                                        onClick = {
                                                            onUseBlueprint(bp)
                                                            activeTab = 0
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                                                        modifier = Modifier.heightIn(min = 42.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("استخدام القالب وإنشاء المشروع", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                        repeat(bpColumns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            EnterpriseSuiteSection(
                                activeProject = currentActiveProject,
                                isWideScreen = isWideScreen,
                                onAppendSecurityToProject = { proj, secText ->
                                    val merged = (proj.currentPromptText ?: proj.rawIdea) + "\n" + secText
                                    onUpdateProjectPrompt(proj, merged, "✓ تم حقن ضوابط OWASP الأمنية في برومبت المشروع")
                                },
                                onCopyText = onCopyText,
                                onBackupAllProjects = onBackupAllProjects,
                                onRestoreBackupProjects = onRestoreBackupProjects
                            )
                        }

                        4 -> {
                            val modelColumns = adaptive.gridColumns
                            val chunkedModels = remember(models, modelColumns) {
                                models.chunked(modelColumns)
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(visual.itemSpacing),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "مركز النماذج المحلية (GGUF Q4_K_M)",
                                            fontWeight = FontWeight.Bold,
                                            color = visual.primaryText,
                                            fontSize = (15f + visual.fontDelta).sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ContextualHelpIconButton(
                                            title = "ما هي نماذج GGUF Q4_K_M ومتطلبات RAM؟",
                                            explanation = "ملفات GGUF هي نماذج ذكاء اصطناعي مضغوطة بتقنية 4-bit لتعمل مباشرة على هاتفك دون إنترنت وبخصوصية تامة. يفحص التطبيق ذاكرة RAM المتاحة تلقائياً لمنع تنزيل نموذج أثقل من قدرات جهازك.",
                                            testTag = "help_gguf_models_button",
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                items(chunkedModels) { rowModels ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(visual.itemSpacing)
                                    ) {
                                        rowModels.forEach { model ->
                                            val isRamSafe = availableRamMB >= model.requiredRamMB
                                            val statusText = downloadStatusMap[model.id]
                                            val isDownloaded = model.id in downloadedModelIds
                                            val isDownloading = model.id in activeDownloadingIds
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                                elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                                                border = visual.cardBorder,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(visual.contentPadding),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = model.name,
                                                            fontWeight = FontWeight.Bold,
                                                            color = visual.primaryText,
                                                            fontSize = (13f + visual.fontDelta).sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = "${model.fileSizeMB} MB (${model.quantization})",
                                                            color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = model.arabicSupportLevel,
                                                        color = visual.secondaryText,
                                                        fontSize = (11f + visual.fontDelta).sp
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isDownloaded -> "✓ مُنزّل وجاهز محلياً"
                                                                isRamSafe -> "✓ متوافق (${model.requiredRamMB} MB)"
                                                                else -> "⚠️ يتطلب ${model.requiredRamMB} MB"
                                                            },
                                                            color = when {
                                                                isDownloaded -> Color(0xFF10B981)
                                                                isRamSafe -> Color(0xFF10B981)
                                                                else -> Color(0xFFD97706)
                                                            },
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        when {
                                                            isDownloading -> {
                                                                OutlinedButton(
                                                                    onClick = { onCancelModelDownload(model) },
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                    modifier = Modifier.heightIn(min = 38.dp).testTag("cancel_model_${model.id}")
                                                                ) {
                                                                    Icon(Icons.Default.Close, contentDescription = "إلغاء التنزيل", modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("إلغاء", fontSize = 11.sp, color = Color(0xFFEF4444))
                                                                }
                                                            }
                                                            isDownloaded -> {
                                                                OutlinedButton(
                                                                    onClick = { onDeleteDownloadedModel(model) },
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                    modifier = Modifier.heightIn(min = 38.dp).testTag("delete_model_${model.id}")
                                                                ) {
                                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف النموذج", modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("حذف الملف", fontSize = 11.sp, color = Color(0xFFEF4444))
                                                                }
                                                            }
                                                            else -> {
                                                                Button(
                                                                    onClick = { onDownloadModel(model) },
                                                                    enabled = isRamSafe,
                                                                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                    modifier = Modifier.heightIn(min = 38.dp).testTag("download_model_${model.id}")
                                                                ) {
                                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("تنزيل", fontSize = 11.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (!statusText.isNullOrBlank()) {
                                                        Text(text = statusText, color = Color(0xFF3B82F6), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        repeat(modelColumns - rowModels.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = visual.surfaceBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = visual.cardElevation),
                                        border = visual.cardBorder,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(visual.contentPadding),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "البحث التقني والاستشاري الهجين (Cloud + Local Fallback)",
                                                fontWeight = FontWeight.Bold,
                                                color = visual.primaryText,
                                                fontSize = (14f + visual.fontDelta).sp
                                            )
                                            OutlinedTextField(
                                                value = researchQuery,
                                                onValueChange = { researchQuery = it },
                                                label = { Text("ابحث عن مكتبة أندرويد أو نمط معماري...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Button(
                                                onClick = { if (researchQuery.isNotBlank()) onRunResearch(researchQuery) },
                                                colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                                                modifier = Modifier.heightIn(min = 42.dp)
                                            ) {
                                                Text(if (isResearching) "جارٍ البحث..." else "بدء البحث التقني", fontSize = 12.sp)
                                            }
                                            if (researchResult.isNotBlank()) {
                                                IdeCodeTerminalViewer(
                                                    title = "Research Output",
                                                    codeText = researchResult,
                                                    onCopy = { onCopyText("نتيجة البحث", researchResult, false) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                }
            }
        }

        if (showOnboarding) {
            FirstLaunchOnboardingDialog(
                onComplete = onCompleteOnboarding,
                onCreateFirstProject = { showNewProjectDialog = true }
            )
        }

        if (showCommandPalette) {
            GlobalCommandPaletteDialog(
                projects = projects,
                blueprints = blueprints,
                models = models,
                isFocusMode = isFocusMode,
                onDismiss = { showCommandPalette = false },
                onSelectProject = { proj ->
                    activeStudioProject = proj
                    activeTab = 1
                },
                onUseBlueprint = { bp ->
                    onUseBlueprint(bp)
                    activeTab = 0
                },
                onJumpToModels = { activeTab = 4 },
                onNewProject = { showNewProjectDialog = true },
                onOpenAppearance = { showAppearanceSheet = true },
                onToggleFocusMode = onToggleFocusMode,
                onRunBackup = onBackupAllProjects,
                onCheckServer = onCheckServer,
                onReplayOnboarding = onReplayOnboarding
            )
        }

        if (showNewProjectDialog) {
            NewProjectDialog(
                isOnline = isOnline,
                onDismiss = { showNewProjectDialog = false },
                onCreateProject = onNewProject
            )
        }

        if (showServerDialog) {
            ServerConfigDialog(
                currentServerUrl = currentServerUrl,
                onDismiss = { showServerDialog = false },
                onSaveServerUrl = onUpdateServerUrl,
                onResetDefaultUrl = onResetServerUrl
            )
        }

        if (showAppearanceSheet) {
            AppearanceStudioBottomSheet(
                visualSettings = visualSettings,
                onUpdateSettings = onUpdateVisualSettings,
                onDismiss = { showAppearanceSheet = false }
            )
        }

        selectedProjectForDialog?.let { proj ->
            AlertDialog(
                onDismissRequest = { selectedProjectForDialog = null },
                text = {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ProjectDetailPanelContent(
                            project = proj,
                            onOpenInStudio = {
                                activeStudioProject = it
                                selectedProjectForDialog = null
                                activeTab = 1
                            },
                            onEditPrompt = {
                                selectedProjectForDialog = null
                                projectToEditPrompt = it
                            },
                            onDuplicateProject = {
                                selectedProjectForDialog = null
                                onDuplicateProject(it)
                            },
                            onArchiveProject = {
                                selectedProjectForDialog = null
                                onArchiveProject(it)
                            },
                            onShareProject = onShareProject,
                            onExportPdf = onExportPdf,
                            onExportStarterZip = onExportStarterZip,
                            onCopyPrompt = { onCopyText(it.name, it.currentPromptText ?: it.rawIdea, false) }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedProjectForDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary)
                    ) {
                        Text("إغلاق")
                    }
                },
                containerColor = visual.surfaceBg
            )
        }

        projectToEditPrompt?.let { proj ->
            EditPromptDialog(
                project = proj,
                onDismiss = { projectToEditPrompt = null },
                onSavePrompt = { updatedText ->
                    onUpdateProjectPrompt(proj, updatedText, "✓ تم حفظ تعديلات البرومبت والمواصفات بنجاح")
                }
            )
        }

        projectPendingDelete?.let { proj ->
            DeleteProjectConfirmDialog(
                project = proj,
                onConfirm = {
                    if (selectedProjectForDialog?.id == proj.id) {
                        selectedProjectForDialog = null
                    }
                    if (activeStudioProject?.id == proj.id) {
                        activeStudioProject = null
                    }
                    onDeleteProject(proj)
                    projectPendingDelete = null
                },
                onDismiss = { projectPendingDelete = null }
            )
        }
        }
    }
}
