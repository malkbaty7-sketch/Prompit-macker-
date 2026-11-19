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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.R
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.ui.LocalStudioVisualSettings
import com.forgeprom.codestudio.core.ui.StudioVisualSettings
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine

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
    availableRamMB: Long,
    networkState: NetworkMonitor.NetworkState,
    serverStatus: CloudApiService.ServerHealthResult?,
    isCheckingServer: Boolean,
    currentServerUrl: String,
    models: List<ModelDownloadManager.GgufModelInfo>,
    blueprints: List<BlueprintAndScaffoldManager.IndustryBlueprint>,
    downloadStatusMap: Map<String, String>,
    researchResult: String,
    isResearching: Boolean,
    actionFeedback: String?,
    visualSettings: StudioVisualSettings,
    onUpdateVisualSettings: (StudioVisualSettings) -> Unit,
    onToggle3DDepth: () -> Unit,
    onToggleEyeComfort: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onCheckServer: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onDownloadModel: (ModelDownloadManager.GgufModelInfo) -> Unit,
    onRunResearch: (String) -> Unit,
    onUseBlueprint: (BlueprintAndScaffoldManager.IndustryBlueprint) -> Unit,
    onShareProject: (ProjectEntity) -> Unit,
    onExportPdf: (ProjectEntity) -> Unit,
    onExportStarterZip: (ProjectEntity) -> Unit,
    onExportMarkdown: (ProjectEntity, String?) -> Unit,
    onUpdateProjectPrompt: (ProjectEntity, String, String) -> Unit,
    onTogglePinProject: (ProjectEntity) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onAnalyzeWireframeUri: (ProjectEntity, Uri) -> Unit,
    onCopyText: (String, String) -> Unit,
    onBackupAllProjects: () -> Unit,
    onRestoreBackupProjects: () -> Unit,
    onNewProject: (String, String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    var activeTab by remember { mutableIntStateOf(0) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var selectedProjectForDialog by remember { mutableStateOf<ProjectEntity?>(null) }
    var activeStudioProject by remember { mutableStateOf<ProjectEntity?>(null) }

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

    val filteredProjects = remember(projects, searchQuery, selectedPlatformFilter) {
        projects.filter { proj ->
            val matchesQuery = searchQuery.isBlank() ||
                proj.name.contains(searchQuery, ignoreCase = true) ||
                proj.description.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedPlatformFilter) {
                "ALL" -> true
                "PINNED" -> proj.isPinned
                else -> proj.targetPlatform.equals(selectedPlatformFilter, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(0, "المشاريع", Icons.Default.FolderOpen, "nav_projects"),
            NavDestinationItem(1, "السلسلة والجودة", Icons.Default.AccountTree, "nav_studio"),
            NavDestinationItem(2, "القوالب", Icons.Default.AutoAwesomeMosaic, "nav_blueprints"),
            NavDestinationItem(3, "الأمان وFinOps", Icons.Default.Security, "nav_enterprise"),
            NavDestinationItem(4, "النماذج والبحث", Icons.Default.Memory, "nav_models")
        )
    }

    BackHandler(enabled = activeTab != 0) {
        activeTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Forgeprom Code Studio Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                            )
                            Column {
                                Text(
                                    text = "Forgeprom Code Studio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = visual.primaryText,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isOnline) "متصل بالخادم (${serverStatus?.latencyMs ?: 0}ms) + محلي"
                                    else "وضع محلي 100% (${networkState.connectionType})",
                                    fontSize = 11.sp,
                                    color = if (isOnline) Color(0xFF10B981) else visual.accentPrimary,
                                    maxLines = 1
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
                            modifier = Modifier.padding(end = 8.dp).testTag("server_status_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = "حالة الاتصال بالخادم",
                                    tint = if (isOnline) Color(0xFF34D399) else Color(0xFF93C5FD),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isCheckingServer) "جارٍ الفحص..." else if (isOnline) "متصل" else "محلي 100%",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = visual.surfaceBg,
                        contentColor = visual.primaryText
                    ) {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = activeTab == item.index,
                                onClick = { activeTab = item.index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 9.sp, maxLines = 1) },
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
                // NavigationRail جانبي تلقائي للشاشات المتوسطة والعريضة (Tablets / Foldables / Landscape)
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = visual.surfaceBg,
                        contentColor = visual.primaryText,
                        modifier = Modifier.fillMaxHeight().testTag("wide_navigation_rail")
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = activeTab == item.index,
                                onClick = { activeTab = item.index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = visual.contentPadding, vertical = 8.dp)
                ) {
                    if (!actionFeedback.isNullOrBlank()) {
                        Surface(
                            color = visual.accentPrimary.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = actionFeedback,
                                color = visual.primaryText,
                                fontSize = (11f + visual.fontDelta).sp,
                                modifier = Modifier.padding(10.dp)
                            )
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
                                            "REACT_NATIVE" to "React Native"
                                        )
                                        items(filters, key = { it.first }) { (key, label) ->
                                            FilterChip(
                                                selected = selectedPlatformFilter == key,
                                                onClick = { selectedPlatformFilter = key },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }

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
                                                                    onCopyText(project.name, project.currentPromptText ?: project.rawIdea)
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
                                                            IconButton(
                                                                onClick = { onDeleteProject(project) },
                                                                modifier = Modifier.minimumInteractiveComponentSize()
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
                                                    onShareProject = onShareProject,
                                                    onExportPdf = onExportPdf,
                                                    onExportStarterZip = onExportStarterZip,
                                                    onCopyPrompt = {
                                                        onCopyText(it.name, it.currentPromptText ?: it.rawIdea)
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
                            val chunkedBlueprints = remember(blueprints, isWideScreen) {
                                if (isWideScreen) blueprints.chunked(2) else blueprints.map { listOf(it) }
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
                                                    modifier = Modifier.padding(visual.contentPadding),
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
                                                        modifier = Modifier.heightIn(min = 38.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("استخدام القالب وإنشاء المشروع", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        if (isWideScreen && rowItems.size == 1) {
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
                            val chunkedModels = remember(models, isWideScreen) {
                                if (isWideScreen) models.chunked(2) else models.map { listOf(it) }
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
                                                            text = if (isRamSafe) "✓ متوافق (${model.requiredRamMB} MB)"
                                                            else "⚠️ يتطلب ${model.requiredRamMB} MB",
                                                            color = if (isRamSafe) Color(0xFF10B981) else Color(0xFFD97706),
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Button(
                                                            onClick = { onDownloadModel(model) },
                                                            enabled = isRamSafe,
                                                            colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                            modifier = Modifier.heightIn(min = 36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("تنزيل", fontSize = 11.sp)
                                                        }
                                                    }
                                                    if (!statusText.isNullOrBlank()) {
                                                        Text(text = statusText, color = Color(0xFF3B82F6), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        if (isWideScreen && rowModels.size == 1) {
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
                                                    onCopy = { onCopyText("نتيجة البحث", researchResult) }
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
            onSaveServerUrl = onUpdateServerUrl
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
                        onShareProject = onShareProject,
                        onExportPdf = onExportPdf,
                        onExportStarterZip = onExportStarterZip,
                        onCopyPrompt = { onCopyText(it.name, it.currentPromptText ?: it.rawIdea) }
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
}
