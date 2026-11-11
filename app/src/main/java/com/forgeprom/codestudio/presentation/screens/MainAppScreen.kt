package com.forgeprom.codestudio.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.R
import com.forgeprom.codestudio.core.network.NetworkMonitor
import com.forgeprom.codestudio.core.ui.ObsidianDarkBg
import com.forgeprom.codestudio.core.ui.ObsidianDarkSurface
import com.forgeprom.codestudio.core.ui.PurpleBrandAccent
import com.forgeprom.codestudio.core.ui.PurpleBrandPrimary
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.remote.CloudApiService
import com.forgeprom.codestudio.data.remote.ModelDownloadManager
import com.forgeprom.codestudio.domain.blueprint.BlueprintAndScaffoldManager

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
    onCheckServer: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onDownloadModel: (ModelDownloadManager.GgufModelInfo) -> Unit,
    onRunResearch: (String) -> Unit,
    onUseBlueprint: (BlueprintAndScaffoldManager.IndustryBlueprint) -> Unit,
    onShareProject: (ProjectEntity) -> Unit,
    onExportPdf: (ProjectEntity) -> Unit,
    onExportStarterZip: (ProjectEntity) -> Unit,
    onNewProject: (String, String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var selectedProjectForDetails by remember { mutableStateOf<ProjectEntity?>(null) }

    var newProjectName by remember { mutableStateOf("") }
    var newProjectIdea by remember { mutableStateOf("") }
    var serverUrlInput by remember(currentServerUrl) { mutableStateOf(currentServerUrl) }
    var researchQuery by remember { mutableStateOf("") }

    val isOnline = serverStatus?.isConnected == true && !networkState.isAirplaneMode

    Scaffold(
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
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Column {
                            Text(
                                text = "Forgeprom Code Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isOnline) "متصل بالخادم (${serverStatus?.latencyMs ?: 0}ms) + محلي"
                                else "وضع محلي 100% (${networkState.connectionType})",
                                fontSize = 11.sp,
                                color = if (isOnline) Color(0xFF34D399) else Color(0xFF93C5FD)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianDarkSurface
                ),
                actions = {
                    Surface(
                        onClick = { showServerDialog = true },
                        color = if (isOnline) Color(0xFF065F46).copy(alpha = 0.5f) else Color(0xFF1E3A8A).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "حالة الاتصال بالخادم",
                                tint = if (isOnline) Color(0xFF34D399) else Color(0xFF60A5FA),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCheckingServer) "جارٍ الفحص..." else if (isOnline) "متصل بالخادم" else "محلي 100%",
                                color = if (isOnline) Color(0xFFA7F3D0) else Color(0xFF93C5FD),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianDarkSurface,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text("المشاريع", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.AutoAwesomeMosaic, contentDescription = null) },
                    label = { Text("القوالب الجاهزة", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                    label = { Text("النماذج GGUF", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    label = { Text("الخادم والبحث", fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { showNewProjectDialog = true },
                    containerColor = PurpleBrandPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "مشروع جديد")
                }
            }
        },
        containerColor = ObsidianDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!actionFeedback.isNullOrBlank()) {
                Surface(
                    color = PurpleBrandPrimary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = actionFeedback,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Hardware & Memory Safety Status Card (3D Extruded Display Panel)
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                border = BorderStroke(1.dp, Color(0xFF284157)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "فاحص العتاد والأمان (HardwareProfiler 3D)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "الرام المتاح: $availableRamMB MB | الشبكة: ${networkState.connectionType}",
                            color = Color(0xFF54D9CC),
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onCheckServer,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص الخادم", fontSize = 10.sp)
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    // TAB 0: PROJECTS & PROMPT STUDIO
                    Text(
                        text = "المشاريع الهندسية النشطة (${projects.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(projects, key = { it.id }) { project ->
                            Card(
                                onClick = {
                                    selectedProjectForDetails = project
                                    onSelectProject(project)
                                },
                                colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp, pressedElevation = 3.dp),
                                border = BorderStroke(1.dp, Color(0xFF284157)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = project.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Surface(
                                            color = PurpleBrandPrimary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = project.targetPlatform,
                                                color = PurpleBrandAccent,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = project.description,
                                        color = Color(0xFFA0AEC0),
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: INDUSTRY BLUEPRINTS LIBRARY
                    Text(
                        text = "مكتبة القوالب الهندسية الجاهزة (Industry Blueprints)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(blueprints, key = { it.id }) { bp ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = bp.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                        Text(text = bp.category, color = PurpleBrandAccent, fontSize = 10.sp)
                                    }
                                    Text(text = bp.description, color = Color(0xFFA0AEC0), fontSize = 11.sp)
                                    Button(
                                        onClick = {
                                            onUseBlueprint(bp)
                                            activeTab = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("استخدام القالب وإنشاء المشروع", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: LOCAL GGUF MODELS & SHA-256 VERIFICATION
                    Text(
                        text = "مركز النماذج المحلية (GGUF Q4_K_M)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(models, key = { it.id }) { model ->
                            val isRamSafe = availableRamMB >= model.requiredRamMB
                            val statusText = downloadStatusMap[model.id]
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = model.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                        Text(text = "${model.fileSizeMB} MB (${model.quantization})", color = PurpleBrandAccent, fontSize = 11.sp)
                                    }
                                    Text(text = model.arabicSupportLevel, color = Color(0xFFA0AEC0), fontSize = 11.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isRamSafe) "✓ متوافق مع ذاكرة هاتفك (يتطلب ${model.requiredRamMB} MB)"
                                            else "⚠️ يتطلب ${model.requiredRamMB} MB رام حر",
                                            color = if (isRamSafe) Color(0xFF34D399) else Color(0xFFFBBF24),
                                            fontSize = 11.sp
                                        )
                                        Button(
                                            onClick = { onDownloadModel(model) },
                                            enabled = isRamSafe,
                                            colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تنزيل وفحص SHA-256", fontSize = 11.sp)
                                        }
                                    }
                                    if (!statusText.isNullOrBlank()) {
                                        Text(text = statusText, color = Color(0xFF93C5FD), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: CLOUD SERVER CONNECTION & TECHNICAL RESEARCH
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "إعدادات الخادم السحابي (Cloud Server URL)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                OutlinedTextField(
                                    value = serverUrlInput,
                                    onValueChange = { serverUrlInput = it },
                                    label = { Text("رابط الخادم") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onUpdateServerUrl(serverUrlInput) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary)
                                    ) {
                                        Text("حفظ وفحص الاتصال", fontSize = 12.sp)
                                    }
                                    if (serverStatus != null) {
                                        Text(
                                            text = serverStatus.message,
                                            color = if (serverStatus.isConnected) Color(0xFF34D399) else Color(0xFF93C5FD),
                                            fontSize = 11.sp,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "البحث التقني والاستشاري الهجين (Cloud + Local Fallback)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary)
                                ) {
                                    Text(if (isResearching) "جارٍ البحث..." else "بدء البحث التقني", fontSize = 12.sp)
                                }
                                if (researchResult.isNotBlank()) {
                                    Text(text = researchResult, color = Color(0xFFD6BCFA), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("إنشاء مشروع وتحليل هندسي جديد", color = Color.White, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("اسم المشروع") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProjectIdea,
                        onValueChange = { newProjectIdea = it },
                        label = { Text("وصف الفكرة الحرة باللغة الطبيعية") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (isOnline) "سيتم تحليل الفكرة عبر الخادم السحابي المتصل وحفظها محلياً."
                        else "سيتم تحليل الفكرة وتوليد البرومبت عبر المحرك المحلي (بدون إنترنت).",
                        color = if (isOnline) Color(0xFF34D399) else Color(0xFF93C5FD),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            onNewProject(newProjectName, newProjectIdea)
                            newProjectName = ""
                            newProjectIdea = ""
                            showNewProjectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary)
                ) {
                    Text("تحليل وحفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            containerColor = ObsidianDarkSurface
        )
    }

    // Server Configuration Dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("إعدادات الاتصال بالخادم السحابي", color = Color.White, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "يعمل التطبيق 100% بدون إنترنت، ويرتبط تلقائياً بالخادم السحابي عند توفر الشبكة:",
                        color = Color.LightGray,
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
                        onUpdateServerUrl(serverUrlInput)
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary)
                ) {
                    Text("حفظ وفحص الاتصال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("إغلاق", color = Color.Gray)
                }
            },
            containerColor = ObsidianDarkSurface
        )
    }

    // Project Prompt Details & Native Actions Dialog
    selectedProjectForDetails?.let { proj ->
        AlertDialog(
            onDismissRequest = { selectedProjectForDetails = null },
            title = { Text(proj.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "الوصف: ${proj.description}", color = Color.LightGray, fontSize = 12.sp)
                    HorizontalDivider(color = Color.DarkGray)
                    Text(
                        text = proj.currentPromptText ?: "البرومبت الهندسي جاهز للتوليد والتصدير.",
                        color = Color(0xFFD6BCFA),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onShareProject(proj) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { onExportPdf(proj) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF عربي", fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { onExportStarterZip(proj) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("كود ZIP", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedProjectForDetails = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrandPrimary)
                ) {
                    Text("إغلاق")
                }
            },
            containerColor = ObsidianDarkSurface
        )
    }
}
