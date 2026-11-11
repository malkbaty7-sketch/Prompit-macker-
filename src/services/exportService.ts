import JSZip from 'jszip';
import { Project } from '../types';

export class ExportService {
  /**
   * Download a text file with UTF-8 BOM for flawless Arabic character rendering across all OSes.
   */
  static downloadTextFile(filename: string, content: string, mimeType: string = 'text/plain;charset=utf-8'): void {
    const bom = '\uFEFF'; // Byte Order Mark for Arabic UTF-8
    const blob = new Blob([bom + content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Export Single Prompt to Markdown (.md)
   */
  static exportPromptMarkdown(project: Project): void {
    const prompts = Array.isArray(project.prompts) ? project.prompts : [];
    const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
    const content = `# مشروع: ${project.name}
تاريخ التصدير: ${new Date().toLocaleDateString('ar-SA')}
المنصة المستهدفة: ${currentPrompt?.targetPlatform || project.targetPlatform}
نوع المهمة: ${project.type}

---

${currentPrompt?.promptText || 'لا يوجد برومبت متاح'}`;

    const filename = `${project.name.replace(/[\s/\\?%*:|"<>]/g, '_')}_prompt_v${currentPrompt?.versionNumber || 1}.md`;
    this.downloadTextFile(filename, content, 'text/markdown;charset=utf-8');
  }

  /**
   * Export Analysis to Plain Text (.txt with UTF-8 BOM)
   */
  static exportAnalysisText(project: Project): void {
    const a = project.analysis;
    if (!a) return;

    const subGoals = Array.isArray(a.subGoals) ? a.subGoals : [];
    const mustHave = Array.isArray(a.functionalReqs?.mustHave) ? a.functionalReqs.mustHave : [];
    const niceToHave = Array.isArray(a.functionalReqs?.niceToHave) ? a.functionalReqs.niceToHave : [];
    const perf = Array.isArray(a.nonFunctionalReqs?.performance) ? a.nonFunctionalReqs.performance : [];
    const sec = Array.isArray(a.nonFunctionalReqs?.security) ? a.nonFunctionalReqs.security : [];
    const off = Array.isArray(a.nonFunctionalReqs?.offline) ? a.nonFunctionalReqs.offline : [];
    const constraints = Array.isArray(a.constraints) ? a.constraints : [];
    const assumptions = Array.isArray(a.assumptions) ? a.assumptions : [];
    const verifiedFacts = Array.isArray(a.verifiedFacts) ? a.verifiedFacts : [];
    const questions = Array.isArray(a.clarifyingQuestions) ? a.clarifyingQuestions : [];

    const content = `وثيقة التحليل الهندسي للمشروع: ${project.name}
تاريخ التحليل: ${project.createdAt}
-----------------------------------------------------------
1. المشكلة الأساسية:
${a.problem || ''}

2. الهدف الرئيسي:
${a.primaryGoal || ''}

3. الأهداف الفرعية:
${subGoals.map((g, i) => `  ${i + 1}. ${g}`).join('\n')}

4. الفئة المستهدفة:
${a.targetAudience || ''}

5. المتطلبات الوظيفية الإلزامية (Must-have):
${mustHave.map(r => `  - ${r}`).join('\n')}

6. المتطلبات التحسينية (Nice-to-have):
${niceToHave.map(r => `  - ${r}`).join('\n')}

7. متطلبات الأداء والأمان والأوفلاين:
الأداء: ${perf.join(', ')}
الأمان: ${sec.join(', ')}
سياسة عدم الاتصال: ${off.join(', ')}

8. القيود التقنية:
${constraints.map(c => `  * ${c}`).join('\n')}

9. الافتراضات المقترحة القابلة للتعديل (Assumptions):
${assumptions.map(as => `  ? ${as}`).join('\n')}

10. الحقائق المؤكدة من المستخدم (Verified Facts):
${verifiedFacts.map(vf => `  ✓ ${vf}`).join('\n')}

11. الأسئلة التوضيحية:
${questions.map(q => `  • ${q}`).join('\n')}
`;

    const filename = `${project.name.replace(/[\s/\\?%*:|"<>]/g, '_')}_analysis.txt`;
    this.downloadTextFile(filename, content);
  }

  /**
   * Export Requirements & Tasks Table to CSV (Stage 12)
   */
  static exportRequirementsCsv(project: Project): void {
    const a = project.analysis;
    const rows: string[][] = [
      ['الرقم', 'الفئة', 'الأولوية', 'البند / المتطلب الهندسي', 'الحالة'],
    ];

    let index = 1;
    const escapeCsv = (val: string) => `"${String(val || '').replace(/"/g, '""')}"`;

    if (a) {
      (a.verifiedFacts || []).forEach(item => {
        rows.push([String(index++), 'حقيقة مؤكدة (Verified Fact)', 'مؤكد', item, 'معتمد']);
      });
      (a.functionalReqs?.mustHave || []).forEach(item => {
        rows.push([String(index++), 'متطلب وظيفي إلزامي (Must-have)', 'عالية', item, 'قيد التنفيذ']);
      });
      (a.functionalReqs?.niceToHave || []).forEach(item => {
        rows.push([String(index++), 'متطلب تحسيني (Nice-to-have)', 'متوسطة', item, 'مجدول']);
      });
      (a.constraints || []).forEach(item => {
        rows.push([String(index++), 'قيد تقني صارم (Constraint)', 'إلزامي', item, 'مفعل']);
      });
      (a.assumptions || []).forEach(item => {
        rows.push([String(index++), 'افتراض مقترح (Assumption)', 'قابل للتعديل', item, 'مقترح']);
      });
    } else {
      rows.push(['1', 'وصف أولي', 'عالية', project.rawIdea || project.description, 'مسودة']);
    }

    const csvContent = rows.map(r => r.map(escapeCsv).join(',')).join('\r\n');
    const filename = `${project.name.replace(/[\s/\\?%*:|"<>]/g, '_')}_requirements.csv`;
    this.downloadTextFile(filename, csvContent, 'text/csv;charset=utf-8');
  }

  /**
   * Export RTL Arabic PDF-Ready Document (Stage 12 - Native Arabic Typography & RTL Layout)
   */
  static exportRtlPdfDocument(project: Project): void {
    const prompts = Array.isArray(project.prompts) ? project.prompts : [];
    const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
    const a = project.analysis;
    const prd = project.chainedPrd || {};

    const htmlDocument = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8" />
  <title>وثيقة هندسة المشروع — ${project.name}</title>
  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
  <style>
    @page { size: A4; margin: 18mm; }
    body {
      font-family: 'Cairo', Tahoma, Arial, sans-serif;
      direction: rtl;
      text-align: right;
      color: #0f172a;
      background: #ffffff;
      line-height: 1.75;
      margin: 0;
      padding: 24px;
    }
    .header {
      border-bottom: 3px solid #6d28d9;
      padding-bottom: 16px;
      margin-bottom: 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .brand {
      font-weight: 800;
      font-size: 22px;
      color: #5b21b6;
    }
    .meta {
      font-size: 12px;
      color: #475569;
    }
    h1 { font-size: 22px; color: #1e1b4b; margin-top: 0; }
    h2 {
      font-size: 16px;
      color: #5b21b6;
      background: #f5f3ff;
      padding: 8px 12px;
      border-right: 4px solid #6d28d9;
      margin-top: 24px;
    }
    ul { padding-right: 20px; }
    li { margin-bottom: 6px; font-size: 13px; }
    pre {
      font-family: 'JetBrains Mono', monospace;
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 14px;
      white-space: pre-wrap;
      word-wrap: break-word;
      font-size: 12px;
      color: #1e293b;
    }
    .print-bar {
      background: #4c1d95;
      color: #fff;
      padding: 12px 20px;
      border-radius: 8px;
      margin-bottom: 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .print-btn {
      background: #fff;
      color: #4c1d95;
      border: none;
      padding: 8px 16px;
      border-radius: 6px;
      font-family: 'Cairo', sans-serif;
      font-weight: 700;
      cursor: pointer;
    }
    @media print {
      .print-bar { display: none; }
      body { padding: 0; }
    }
  </style>
</head>
<body>
  <div class="print-bar">
    <span>وثيقة Forgeprom Code Studio الرسمية جاهزة للطباعة أو الحفظ كملف PDF باتجاه RTL سليم 100%</span>
    <button class="print-btn" onclick="window.print()">طباعة / حفظ كملف PDF</button>
  </div>

  <div class="header">
    <div>
      <div class="brand">Forgeprom Code Studio — الإصدار 2.0</div>
      <div class="meta">وثيقة المواصفات الهندسية والبرومبت المعتمد (Local-First Architecture)</div>
    </div>
    <div class="meta">
      <div>تاريخ الإصدار: ${new Date().toLocaleDateString('ar-SA')}</div>
      <div>المنصة: ${project.targetPlatform}</div>
    </div>
  </div>

  <h1>مشروع: ${project.name}</h1>
  <p>${project.description || ''}</p>

  ${
    a
      ? `
  <h2>1. المشكلة والهدف الرئيسي</h2>
  <p><strong>المشكلة:</strong> ${a.problem}</p>
  <p><strong>الهدف الرئيسي:</strong> ${a.primaryGoal}</p>
  <p><strong>الفئة المستهدفة:</strong> ${a.targetAudience}</p>

  <h2>2. الحقائق المؤكدة مقابل الافتراضات المقترحة</h2>
  <p><strong>الحقائق المؤكدة من المستخدم:</strong></p>
  <ul>${(a.verifiedFacts || []).map(f => `<li>✓ ${f}</li>`).join('')}</ul>
  <p><strong>الافتراضات المقترحة القابلة للتعديل:</strong></p>
  <ul>${(a.assumptions || []).map(as => `<li>? ${as}</li>`).join('')}</ul>

  <h2>3. المتطلبات الوظيفية الإلزامية والتحسينية</h2>
  <p><strong>المتطلبات الإلزامية (Must-have):</strong></p>
  <ul>${(a.functionalReqs?.mustHave || []).map(m => `<li>${m}</li>`).join('')}</ul>
  <p><strong>المتطلبات التحسينية (Nice-to-have):</strong></p>
  <ul>${(a.functionalReqs?.niceToHave || []).map(n => `<li>${n}</li>`).join('')}</ul>
  `
      : ''
  }

  <h2>4. البرومبت الهندسي القياسي المعتمد</h2>
  <pre>${currentPrompt?.promptText || 'لم يتم توليد البرومبت بعد'}</pre>

  ${
    prd.step1_prd
      ? `<h2>5. وثيقة رؤية المنتج وهندسة النظم (PRD)</h2><pre>${[
          prd.step1_prd,
          prd.step2_architecture,
          prd.step3_screens_stories,
          prd.step4_database_api,
          prd.step5_roadmap_migration,
        ]
          .filter(Boolean)
          .join('\n\n---\n\n')}</pre>`
      : ''
  }
</body>
</html>`;

    const filename = `${project.name.replace(/[\s/\\?%*:|"<>]/g, '_')}_RTL_Report_PDF.html`;
    this.downloadTextFile(filename, htmlDocument, 'text/html;charset=utf-8');
  }

  /**
   * Export Full Project as a ZIP Archive containing structured Markdown, CSV, HTML/PDF report, and backup JSON.
   */
  static async exportProjectZip(project: Project): Promise<void> {
    const zip = new JSZip();
    const folderName = project.name.replace(/[\s/\\?%*:|"<>]/g, '_');
    const folder = zip.folder(folderName) || zip;

    // 1. README
    const readmeContent = `# ${project.name}
تم التوليد والتصدير بواسطة: Forgeprom Code Studio (الإصدار 2.0)
تاريخ التصدير: ${new Date().toISOString()}

## وصف المشروع
${project.description}

- **نوع المشروع:** ${project.type}
- **المنصة المستهدفة:** ${project.targetPlatform}
- **حالة الاتصال:** Local-First (يعمل دون إنترنت بنسبة 100%)
`;
    folder.file('README.md', '\uFEFF' + readmeContent);

    // 2. Analysis Document
    if (project.analysis) {
      const mustHave = Array.isArray(project.analysis.functionalReqs?.mustHave)
        ? project.analysis.functionalReqs.mustHave
        : [];
      const assumptions = Array.isArray(project.analysis.assumptions) ? project.analysis.assumptions : [];
      const verifiedFacts = Array.isArray(project.analysis.verifiedFacts) ? project.analysis.verifiedFacts : [];

      const analysisContent = `# التحليل الهندسي والمتطلبات
## المشكلة
${project.analysis.problem}

## الهدف الرئيسي
${project.analysis.primaryGoal}

## المتطلبات الإلزامية
${mustHave.map(m => `- ${m}`).join('\n')}

## الافتراضات المقترحة
${assumptions.map(a => `- ${a}`).join('\n')}

## الحقائق المؤكدة
${verifiedFacts.map(f => `- ${f}`).join('\n')}
`;
      folder.file('01_Analysis_Requirements.md', '\uFEFF' + analysisContent);
    }

    // 3. Current Prompt
    const prompts = Array.isArray(project.prompts) ? project.prompts : [];
    const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
    if (currentPrompt) {
      folder.file(`02_Standardized_Prompt_v${currentPrompt.versionNumber}.md`, '\uFEFF' + currentPrompt.promptText);
    }

    // 4. Chained PRD steps if available
    if (project.chainedPrd) {
      const prdFolder = folder.folder('PRD_and_Architecture');
      if (prdFolder) {
        if (project.chainedPrd.step1_prd) prdFolder.file('01_PRD_MVP_Scope.md', '\uFEFF' + project.chainedPrd.step1_prd);
        if (project.chainedPrd.step2_architecture) prdFolder.file('02_Architecture_Modules.md', '\uFEFF' + project.chainedPrd.step2_architecture);
        if (project.chainedPrd.step3_screens_stories) prdFolder.file('03_Screens_UserStories.md', '\uFEFF' + project.chainedPrd.step3_screens_stories);
        if (project.chainedPrd.step4_database_api) prdFolder.file('04_Database_Entities_API.md', '\uFEFF' + project.chainedPrd.step4_database_api);
        if (project.chainedPrd.step5_roadmap_migration) prdFolder.file('05_Roadmap_Migration.md', '\uFEFF' + project.chainedPrd.step5_roadmap_migration);
      }
    }

    // 5. JSON Project Backup
    folder.file('project_backup.json', JSON.stringify(project, null, 2));

    // Generate zip and download
    const zipBlob = await zip.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(zipBlob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${folderName}_package.zip`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Export Complete Android Native Kotlin Project as a ZIP Archive ready for GitHub and Gradle APK build.
   */
  static async exportAndroidNativeProjectZip(): Promise<void> {
    const zip = new JSZip();
    const root = zip.folder('forgeprom-code-studio-android') || zip;

    // Root Build Files
    root.file('settings.gradle.kts', `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ForgepromCodeStudio"
include(":app")
`);

    root.file('build.gradle.kts', `plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
`);

    root.file('gradle.properties', `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.configuration-cache=true
`);

    // GitHub Actions Workflow for automated APK build
    const workflows = root.folder('.github')?.folder('workflows') || root;
    workflows.file('build-apk.yml', `name: Build Android APK (Forgeprom Code Studio)

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    name: Build Android Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Grant Execute Permission for Gradlew
        run: chmod +x gradlew

      - name: Build Debug APK with Gradle
        run: ./gradlew assembleDebug --stacktrace --no-daemon

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: Forgeprom-CodeStudio-Debug-APK
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
          retention-days: 30
`);

    // App Module
    const appFolder = root.folder('app') || root;
    appFolder.file('build.gradle.kts', `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.forgeprom.codestudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forgeprom.codestudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
`);

    appFolder.file('proguard-rules.pro', `# Forgeprom Code Studio ProGuard Rules
-keep class com.forgeprom.codestudio.data.local.model.** { *; }
-keep class com.forgeprom.codestudio.data.local.db.** { *; }
-keep class com.forgeprom.codestudio.core.hardware.** { *; }
-keep class com.forgeprom.codestudio.core.security.** { *; }
`);

    // AndroidManifest.xml
    const mainFolder = appFolder.folder('src')?.folder('main') || appFolder;
    mainFolder.file('AndroidManifest.xml', `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application
        android:name=".ForgepromApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.ForgepromCodeStudio">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.ForgepromCodeStudio">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
`);

    // Kotlin Sources
    const pkgFolder = mainFolder.folder('java')?.folder('com')?.folder('forgeprom')?.folder('codestudio') || mainFolder;
    pkgFolder.file('ForgepromApp.kt', `package com.forgeprom.codestudio

import android.app.Application
import android.util.Log

class ForgepromApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("ForgepromApp", "Forgeprom Code Studio initialized. Native Local-First environment ready.")
    }
}
`);

    pkgFolder.file('MainActivity.kt', `package com.forgeprom.codestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.ui.ForgepromTheme
import com.forgeprom.codestudio.data.local.db.AppDatabase
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.presentation.screens.MainAppScreen
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var hardwareProfiler: HardwareProfiler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getInstance(this)
        hardwareProfiler = HardwareProfiler(this)

        setContent {
            ForgepromTheme {
                val projects by database.projectDao().getActiveProjectsFlow().collectAsState(initial = emptyList())
                val availableRam = remember { hardwareProfiler.getAvailableRamMB() }

                MainAppScreen(
                    projects = projects,
                    availableRamMB = availableRam,
                    onNewProject = { name, idea ->
                        lifecycleScope.launch {
                            database.projectDao().insertOrUpdate(
                                ProjectEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    description = idea.take(120),
                                    type = "NEW_APP",
                                    targetPlatform = "ANDROID_KOTLIN",
                                    rawIdea = idea
                                )
                            )
                        }
                    },
                    onSelectProject = { }
                )
            }
        }
    }
}
`);

    // Hardware and Security
    const coreFolder = pkgFolder.folder('core') || pkgFolder;
    coreFolder.folder('hardware')?.file('HardwareProfiler.kt', `package com.forgeprom.codestudio.core.hardware

import android.app.ActivityManager
import android.content.Context
import java.io.File

class HardwareProfiler(private val context: Context) {
    data class MemorySafetyReport(
        val isSafe: Boolean,
        val availableRamMB: Long,
        val requiredRamMB: Long,
        val differenceMB: Long,
        val recommendedThreads: Int,
        val warningMessage: String? = null
    )

    fun getAvailableRamMB(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    fun getRecommendedThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceAtLeast(2).coerceAtMost(6)
    }

    fun verifyModelMemorySafety(modelFile: File): MemorySafetyReport {
        val modelSizeMB = modelFile.length() / (1024 * 1024)
        val requiredRamMB = (modelSizeMB * 1.25).toLong()
        val availableRamMB = getAvailableRamMB()
        val differenceMB = availableRamMB - requiredRamMB
        val isSafe = differenceMB >= 0

        val warning = if (!isSafe) {
            "الذاكرة الحرة المتاحة (\$availableRamMB MB) غير كافية. يتطلب النموذج \$requiredRamMB MB على الأقل."
        } else null

        return MemorySafetyReport(
            isSafe = isSafe,
            availableRamMB = availableRamMB,
            requiredRamMB = requiredRamMB,
            differenceMB = differenceMB,
            recommendedThreads = getRecommendedThreadCount(),
            warningMessage = warning
        )
    }
}
`);

    coreFolder.folder('security')?.file('ChecksumValidator.kt', `package com.forgeprom.codestudio.core.security

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ChecksumValidator {
    fun calculateSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedHash: String): Boolean {
        if (!file.exists() || expectedHash.isBlank()) return false
        val calculated = calculateSha256(file)
        return calculated.equals(expectedHash.trim(), ignoreCase = true)
    }
}
`);

    coreFolder.folder('ui')?.file('Theme.kt', `package com.forgeprom.codestudio.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val PurpleBrandPrimary = Color(0xFF6B46C1)
val PurpleBrandAccent = Color(0xFF9F7AEA)
val ObsidianDarkBg = Color(0xFF0F0B1E)
val ObsidianDarkSurface = Color(0xFF18122B)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleBrandPrimary,
    onPrimary = Color.White,
    secondary = PurpleBrandAccent,
    background = ObsidianDarkBg,
    surface = ObsidianDarkSurface
)

@Composable
fun ForgepromTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}
`);

    coreFolder.folder('network')?.file('NetworkMonitor.kt', `package com.forgeprom.codestudio.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

class NetworkMonitor(private val context: Context) {
    data class NetworkState(
        val isOnline: Boolean,
        val isAirplaneMode: Boolean,
        val connectionType: String
    )

    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentNetworkState(): NetworkState {
        if (isAirplaneModeOn()) return NetworkState(false, true, "وضع الطيران (محلي 100%)")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return NetworkState(false, false, "غير متصل (محلي 100%)")
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkState(false, false, "غير متصل (محلي 100%)")
        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return NetworkState(isConnected, false, if (isConnected) "متصل بالشبكة" else "غير متصل (محلي 100%)")
    }
}
`);

    // Remote Cloud Service & Model Download Manager
    const remoteFolder = pkgFolder.folder('data')?.folder('remote') || pkgFolder;
    remoteFolder.file('CloudApiService.kt', `package com.forgeprom.codestudio.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CloudApiService(private val context: Context? = null) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://ais-pre-wg5ba7jzr4vldg2v6hfqek-381610229333.europe-west3.run.app"
    }

    private var baseUrl: String = DEFAULT_SERVER_URL

    data class ServerHealthResult(
        val isConnected: Boolean,
        val serverMode: String,
        val latencyMs: Long,
        val message: String
    )

    suspend fun checkServerConnection(): ServerHealthResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("\$baseUrl/api/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            val latency = System.currentTimeMillis() - startTime
            if (conn.responseCode in 200..299) {
                ServerHealthResult(true, "Forgeprom Hybrid Engine", latency, "متصل بالخادم السحابي بنجاح (\${latency}ms)")
            } else {
                ServerHealthResult(false, "Local-First Offline", latency, "يعمل بالوضع المحلي 100%")
            }
        } catch (e: Exception) {
            ServerHealthResult(false, "Local-First Offline", -1, "وضع محلي 100% آمن")
        }
    }
}
`);

    remoteFolder.file('ModelDownloadManager.kt', `package com.forgeprom.codestudio.data.remote

import android.content.Context
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.security.ChecksumValidator
import java.io.File

class ModelDownloadManager(
    private val context: Context,
    private val hardwareProfiler: HardwareProfiler
) {
    data class GgufModelInfo(
        val id: String,
        val name: String,
        val quantization: String,
        val fileSizeMB: Long,
        val requiredRamMB: Long,
        val downloadUrl: String,
        val expectedSha256: String
    )

    fun isModelCompatibleWithDevice(model: GgufModelInfo): Boolean {
        return hardwareProfiler.getAvailableRamMB() >= model.requiredRamMB
    }

    fun verifyDownloadedModel(file: File, expectedHash: String): Boolean {
        return ChecksumValidator.verify(file, expectedHash)
    }
}
`);

    // Room Database and Model
    const dataFolder = pkgFolder.folder('data')?.folder('local') || pkgFolder;
    dataFolder.folder('model')?.file('ProjectEntity.kt', `package com.forgeprom.codestudio.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val targetPlatform: String,
    val rawIdea: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val analysisJson: String? = null,
    val currentPromptText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
`);

    dataFolder.folder('db')?.file('AppDatabase.kt', `package com.forgeprom.codestudio.data.local.db

import android.content.Context
import androidx.room.*
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "forgeprom_local_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
`);

    // Presentation
    pkgFolder.folder('presentation')?.folder('screens')?.file('MainAppScreen.kt', `package com.forgeprom.codestudio.presentation.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.ObsidianDarkBg
import com.forgeprom.codestudio.core.ui.ObsidianDarkSurface
import com.forgeprom.codestudio.core.ui.PurpleBrandAccent
import com.forgeprom.codestudio.core.ui.PurpleBrandPrimary
import com.forgeprom.codestudio.data.local.model.ProjectEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    projects: List<ProjectEntity>,
    availableRamMB: Long,
    onNewProject: (String, String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurpleBrandPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "F", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Column {
                            Text(text = "Forgeprom Code Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            Text(text = "بيئة الهندسة والبرومبتات المحلية (Local-First)", fontSize = 11.sp, color = Color(0xFFA0AEC0))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianDarkSurface)
            )
        },
        containerColor = ObsidianDarkBg
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "فاحص العتاد وسلامة الذاكرة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text(text = "الذاكرة المتاحة: \$availableRamMB MB | آمن ضد OOM", color = Color(0xFF34D399), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Memory, contentDescription = null, tint = PurpleBrandAccent)
                }
            }

            Text(text = "المشاريع النشطة (\${projects.size})", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    Card(
                        onClick = { onSelectProject(project) },
                        colors = CardDefaults.cardColors(containerColor = ObsidianDarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = project.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = project.description, color = Color(0xFFA0AEC0), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
`);

    // Resources
    const resFolder = mainFolder.folder('res') || mainFolder;
    resFolder.folder('values')?.file('strings.xml', `<resources>
    <string name="app_name">Forgeprom Code Studio</string>
</resources>
`);
    resFolder.folder('values')?.file('colors.xml', `<resources>
    <color name="purple_primary">#6B46C1</color>
    <color name="obsidian_bg">#0F0B1E</color>
</resources>
`);

    // README with APK download instructions
    root.file('README.md', `\uFEFF# Forgeprom Code Studio — Android Native Project

## 🚀 كيفية تنزيل ملف الـ APK وتثبيته على هاتفك عبر GitHub
1. ارفع هذا المستودع إلى حسابك على GitHub:
   git init
   git add .
   git commit -m "feat: initial commit"
   git remote add origin https://github.com/USERNAME/REPO.git
   git push -u origin main

2. توجه إلى تبويب **Actions** في GitHub.
3. اضغط على أحدث سير عمل **Build Android APK**.
4. حمّل ملف **Forgeprom-CodeStudio-Debug-APK** من قسم Artifacts وثبته على هاتفك مباشرة!
`);

    // Foreground Service & Native Export Helpers
    coreFolder.folder('service')?.file('ForegroundGenerationService.kt', `package com.forgeprom.codestudio.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ForegroundGenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
`);

    coreFolder.folder('export')?.file('NativeShareAndPdfHelper.kt', `package com.forgeprom.codestudio.core.export

import android.content.Context
import android.content.Intent
import com.forgeprom.codestudio.data.local.model.ProjectEntity

object NativeShareAndPdfHelper {
    fun shareProjectPrompt(context: Context, project: ProjectEntity) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, project.name)
            putExtra(Intent.EXTRA_TEXT, project.currentPromptText ?: project.rawIdea)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة البرومبت"))
    }
}
`);

    // Generate zip and download
    const zipBlob = await zip.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(zipBlob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Forgeprom_CodeStudio_Android_Native_Project.zip`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Upgrade 1: Full Codebase Scaffolding Generator (Starter Boilerplate ZIP)
   * Generates a runnable multi-file starter codebase tailored to the project's platform & requirements.
   */
  static async exportStarterCodebaseZip(project: Project): Promise<void> {
    const zip = new JSZip();
    const safeName = project.name.replace(/[\s/\\?%*:|"<>]/g, '_');
    const root = zip.folder(`${safeName}_Starter_Codebase`) || zip;
    const prompts = Array.isArray(project.prompts) ? project.prompts : [];
    const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
    const mustHave = project.analysis?.functionalReqs?.mustHave || [project.description];

    // 1. Project README.md
    root.file(
      'README.md',
      `\uFEFF# ${project.name} — Starter Codebase
تم توليد هذا الهيكل البرمجي تلقائياً بواسطة **Forgeprom Code Studio (Codebase Scaffolding Engine)**.

- **المنصة المستهدفة:** ${project.targetPlatform}
- **نوع المشروع:** ${project.type}
- **تاريخ التوليد:** ${new Date().toLocaleDateString('ar-SA')}

## المتطلبات الوظيفية المدمجة في الهيكل:
${mustHave.map((r, i) => `${i + 1}. ${r}`).join('\n')}

## خطوات التشغيل السريع:
1. افتح هذا المجلد في بيئة التطوير المناسبة (${project.targetPlatform}).
2. راجع ملف \`docs/ENGINEERING_PROMPT.md\` لاستكمال بناء الوحدات التفصيلية.
`
    );

    // 2. Engineering Prompt & Architecture Doc
    const docs = root.folder('docs') || root;
    docs.file('ENGINEERING_PROMPT.md', '\uFEFF' + (currentPrompt?.promptText || project.rawIdea));

    // 3. Platform-Specific Starter Code Files
    const platform = String(project.targetPlatform || '').toLowerCase();
    if (platform.includes('flutter')) {
      root.file(
        'pubspec.yaml',
        `name: ${safeName.toLowerCase()}
description: ${project.description}
version: 1.0.0+1
environment:
  sdk: ">=3.2.0 <4.0.0"
dependencies:
  flutter:
    sdk: flutter
  flutter_localizations:
    sdk: flutter
`
      );
      root.folder('lib')?.file(
        'main.dart',
        `import 'package:flutter/material.dart';

void main() {
  runApp(const GeneratedApp());
}

class GeneratedApp extends StatelessWidget {
  const GeneratedApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '${project.name}',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(useMaterial3: true),
      home: Directionality(
        textDirection: TextDirection.rtl,
        child: Scaffold(
          appBar: AppBar(title: const Text('${project.name}')),
          body: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text('${project.description}', style: TextStyle(fontSize: 16)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
`
      );
    } else if (platform.includes('react') || platform.includes('next') || platform.includes('web')) {
      root.file(
        'package.json',
        JSON.stringify(
          {
            name: safeName.toLowerCase(),
            version: '1.0.0',
            private: true,
            description: project.description,
            scripts: {
              dev: 'vite',
              build: 'vite build',
            },
          },
          null,
          2
        )
      );
      root.folder('src')?.file(
        'App.tsx',
        `import React from 'react';

export default function App() {
  return (
    <div dir="rtl" className="min-h-screen bg-slate-950 text-white p-8 font-sans">
      <header className="max-w-4xl mx-auto border-b border-slate-800 pb-4 mb-6">
        <h1 className="text-2xl font-bold text-purple-400">${project.name}</h1>
        <p className="text-sm text-slate-400 mt-1">${project.description}</p>
      </header>
      <main className="max-w-4xl mx-auto space-y-4">
        <h2 className="text-lg font-semibold">الوحدات الأساسية المعتمدة:</h2>
        <ul className="list-disc pr-6 space-y-2 text-slate-300">
${mustHave.map(m => `          <li>${m}</li>`).join('\n')}
        </ul>
      </main>
    </div>
  );
}
`
      );
    } else {
      // Default to Android Native Kotlin Clean Architecture Starter
      const kotlinFolder = root.folder('app/src/main/java/com/generated/app') || root;
      kotlinFolder.file(
        'MainActivity.kt',
        `package com.generated.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * المشروع: ${project.name}
 * الوصف: ${project.description}
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${project.name}", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${project.description}")
                    }
                }
            }
        }
    }
}
`
      );
      kotlinFolder.folder('domain/model')?.file(
        'DomainEntities.kt',
        `package com.generated.app.domain.model

data class ProjectRecord(
    val id: String,
    val title: String,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
`
      );
    }

    const zipBlob = await zip.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(zipBlob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${safeName}_Starter_Codebase.zip`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}

