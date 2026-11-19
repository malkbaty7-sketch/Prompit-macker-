package com.forgeprom.codestudio.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
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
    actionFeedback: String?,
    lastExportedFile: Pair<File, String>?,
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
    onDeleteProject: (ProjectEntity) -> Unit,
    onAnalyzeWireframeUri: (ProjectEntity, Uri) -> Unit,
    onCopyText: (String, String, Boolean) -> Unit,
    onBackupAllProjects: () -> Unit,
    onRestoreBackupProjects: () -> Unit,
    onNewProject: (String, String, String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var activeTab by remember { mutableIntStateOf(0) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var selectedProjectForDialog by remember { mutableStateOf<ProjectEntity?>(null) }
    var activeStudioProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectPendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectToEditPrompt by remember { mutableStateOf<ProjectEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPlatformFilter by remember { mutableStateOf("ALL") }
    var researchQuery by remember { mutableStateOf("") }

    val isOnline = serverStatus?.isConnected == true && !networkState.isAirplaneMode
    val currentActiveProject = activeStudioProject?.let { sel ->
        projects.find { it.id == sel.id }
    } ?: projects.firstOrNull()

    val averageQualityScore = remember(projects) {
        if (projects.isEmpty()) 90
        else projects.map {
            PromptEngineeringEngine.evaluatePromptQuality(it.currentPromptText ?: it.rawIdea).totalScore
        }.average().toInt()
    }

    val filteredProjects = remember(projects, archivedProjects, searchQuery, selectedPlatformFilter) {
        val sourceList = if (selectedPlatformFilter == "ARCHIVED") archivedProjects else projects
        sourceList.filter { proj ->
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
                            onClick = { showAppearanceSheet = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_appearance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "تخصيص المظهر والشاشة",
                                tint = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary
                            )
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
                                onClick = { activeTab = item.index },
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
                if (activeTab == 0) {
                    FloatingActionButton(
                        onClick = { showNewProjectDialog = true },
                        containerColor = visual.accentPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("new_project_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "مشروع جديد")
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
                    if (!actionFeedback.isNullOrBlank()) {
                        Surface(
                            color = visual.accentPrimary.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = actionFeedback,
                                    color = visual.primaryText,
                                    fontSize = (11f + visual.fontDelta).sp,
                                    modifier = Modifier.weight(1f)
                                )
                                lastExportedFile?.let { (file, mime) ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { onShareExportedFile(file, mime) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 34.dp).testTag("share_exported_file_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "مشاركة الملف المُصدّر", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مشاركة الملف", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    StudioVisualControlBar(
                        visualSettings = visualSettings,
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

                    when (activeTab) {
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
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                                        modifier = Modifier.fillMaxSize().testTag("projects_list")
                                    ) {
                                        items(filteredProjects, key = { it.id }) { project ->
                                            val score = remember(project.currentPromptText, project.rawIdea) {
                                                PromptEngineeringEngine.evaluatePromptQuality(
                                                    project.currentPromptText ?: project.rawIdea
                                                ).totalScore
                                            }
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
                                                modifier = Modifier.fillMaxWidth()
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
                                                                    fontSize = (13f + visual.fontDelta).sp
                                                                )
                                                                Surface(
                                                                    color = visual.accentPrimary.copy(alpha = 0.2f),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    modifier = Modifier.padding(top = 3.dp)
                                                                ) {
                                                                    Text(
                                                                        text = project.targetPlatform,
                                                                        color = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                                                        fontSize = 9.sp,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (selectedPlatformFilter == "ARCHIVED") {
                                                                IconButton(
                                                                    onClick = { onRestoreArchivedProject(project) },
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
                                                                    onClick = { onTogglePinProject(project) },
                                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (project.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                                                        contentDescription = "تثبيت المشروع",
                                                                        tint = if (project.isPinned) Color(0xFFFBBF24) else visual.secondaryText,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        onCopyText(project.name, project.currentPromptText ?: project.rawIdea, false)
                                                                    },
                                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.ContentCopy,
                                                                        contentDescription = "نسخ البرومبت",
                                                                        tint = if (visual.isDarkTheme) visual.accentSecondary else visual.accentPrimary,
                                                                        modifier = Modifier.size(18.dp)
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
                                                        maxLines = 2
                                                    )
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
                                    Text(
                                        text = "مركز النماذج المحلية (GGUF Q4_K_M)",
                                        fontWeight = FontWeight.Bold,
                                        color = visual.primaryText,
                                        fontSize = (15f + visual.fontDelta).sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
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
