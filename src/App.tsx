import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { StatusBar } from './components/StatusBar';
import { DashboardView } from './components/DashboardView';
import { ProjectListView } from './components/ProjectListView';
import { PromptStudioView } from './components/PromptStudioView';
import { ChainedPrdView } from './components/ChainedPrdView';
import { ModelCenterView } from './components/ModelCenterView';
import { VisionAnalyzerView } from './components/VisionAnalyzerView';
import { QualityReviewModal } from './components/QualityReviewModal';
import { AndroidCodeModal } from './components/AndroidCodeModal';
import { ResearchCloudView } from './components/ResearchCloudView';
import { VisualArchitectureView } from './components/VisualArchitectureView';
import { SettingsView } from './components/SettingsView';
import { QrSyncModal } from './components/QrSyncModal';
import { EnterpriseSuiteView } from './components/EnterpriseSuiteView';
import { OfflineIndicator } from './components/PWAInstallButton';

import { Project, HardwareStatus, LocalModel, IdeaAnalysis } from './types';
import { AppStorage } from './services/storage';
import { HardwareProfiler } from './services/hardwareProfiler';
import { ExportService } from './services/exportService';
import { safeStorage } from './services/safeStorage';
import { createProjectFromBlueprint, IndustryBlueprint } from './services/blueprintsCatalog';

export default function App() {
  // Navigation tab state
  const [currentTab, setCurrentTab] = useState<string>('dashboard');

  // Dark mode (default to true per design)
  const [darkMode, setDarkMode] = useState<boolean>(() => {
    const stored = safeStorage.getItem('forgeprom_dark_mode');
    return stored ? stored === 'true' : true;
  });

  // Projects and active selection
  const [projects, setProjects] = useState<Project[]>(() => AppStorage.getProjects());
  const [activeProjectId, setActiveProjectId] = useState<string>(() => {
    const stored = AppStorage.getProjects();
    return stored.length > 0 ? stored[0].id : '';
  });

  // Hardware status and models
  const [hardware, setHardware] = useState<HardwareStatus>(() => HardwareProfiler.getInitialStatus());
  const [models, setModels] = useState<LocalModel[]>(() => AppStorage.getModels());

  // Modals state
  const [isQualityModalOpen, setIsQualityModalOpen] = useState<boolean>(false);
  const [qualityPromptText, setQualityPromptText] = useState<string>('');
  const [qualityAnalysis, setQualityAnalysis] = useState<IdeaAnalysis | undefined>();
  const [isAndroidCodeModalOpen, setIsAndroidCodeModalOpen] = useState<boolean>(false);
  const [isQrSyncModalOpen, setIsQrSyncModalOpen] = useState<boolean>(false);

  // 3D Display Depth & Eye-Comfort Mode state
  const [is3DTilt, setIs3DTilt] = useState<boolean>(false);
  const [isEyeCareWarm, setIsEyeCareWarm] = useState<boolean>(false);

  // Sync dark mode class with html document
  useEffect(() => {
    safeStorage.setItem('forgeprom_dark_mode', darkMode.toString());
    if (typeof document !== 'undefined') {
      if (darkMode) {
        document.documentElement.classList.add('dark');
        document.body.classList.add('bg-slate-950', 'text-slate-100');
        document.body.classList.remove('bg-slate-50', 'text-slate-900');
      } else {
        document.documentElement.classList.remove('dark');
        document.body.classList.remove('bg-slate-950', 'text-slate-100');
        document.body.classList.add('bg-slate-50', 'text-slate-900');
      }
    }
  }, [darkMode]);

  const activeProject = (Array.isArray(projects) && projects.length > 0)
    ? (projects.find(p => p.id === activeProjectId) || projects[0])
    : undefined;
  const activeModel = (Array.isArray(models) && models.length > 0)
    ? (models.find(m => m.id === hardware.activeModelId) || models[0])
    : undefined;

  // Handlers
  const handleToggleDarkMode = () => setDarkMode(prev => !prev);

  const handleToggleAirplaneMode = () => {
    setHardware(prev => {
      const updated = { ...prev, isAirplaneMode: !prev.isAirplaneMode };
      HardwareProfiler.saveStatus(updated);
      return updated;
    });
  };

  const handleUpdateProject = (updated: Project) => {
    AppStorage.saveProject(updated);
    setProjects(AppStorage.getProjects());
  };

  const handleOpenProject = (id: string) => {
    setActiveProjectId(id);
    setCurrentTab('studio');
  };

  const handleNewPrompt = () => {
    const newProj: Project = {
      id: `proj_${Date.now()}`,
      name: `مشروع جديد #${projects.length + 1}`,
      description: 'مشروع جديد قيد التطوير في Forgeprom Code Studio',
      type: 'full_app',
      category: 'تطوير أندرويد',
      tags: ['Android', 'Kotlin', 'Compose', 'RTL'],
      targetPlatform: 'android_studio_ai',
      detailLevel: 'comprehensive',
      isPinned: false,
      isArchived: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      rawIdea: '',
      prompts: [],
      currentPromptIndex: 0,
    };
    AppStorage.saveProject(newProj);
    setProjects(AppStorage.getProjects());
    setActiveProjectId(newProj.id);
    setCurrentTab('studio');
  };

  const handleCreateProject = (data: Partial<Project>) => {
    const newProj: Project = {
      id: `proj_${Date.now()}`,
      name: data.name || 'مشروع أندرويد جديد',
      description: data.description || '',
      type: data.type || 'full_app',
      category: data.category || 'عام',
      tags: data.tags || ['Android', 'Kotlin'],
      targetPlatform: data.targetPlatform || 'android_studio_ai',
      detailLevel: data.detailLevel || 'comprehensive',
      isPinned: false,
      isArchived: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      rawIdea: data.rawIdea || '',
      prompts: [],
      currentPromptIndex: 0,
    };
    AppStorage.saveProject(newProj);
    setProjects(AppStorage.getProjects());
    setActiveProjectId(newProj.id);
    setCurrentTab('studio');
  };

  const handleDeleteProject = (id: string) => {
    AppStorage.deleteProject(id);
    const updated = AppStorage.getProjects();
    setProjects(updated);
    if (activeProjectId === id && updated.length > 0) {
      setActiveProjectId(updated[0].id);
    }
  };

  const handleDuplicateProject = (id: string) => {
    const duplicated = AppStorage.duplicateProject(id);
    if (duplicated) {
      setProjects(AppStorage.getProjects());
      setActiveProjectId(duplicated.id);
    }
  };

  const handleTogglePinProject = (id: string) => {
    const p = AppStorage.getProject(id);
    if (p) {
      handleUpdateProject({ ...p, isPinned: !p.isPinned });
    }
  };

  const handleToggleArchiveProject = (id: string) => {
    const p = AppStorage.getProject(id);
    if (p) {
      handleUpdateProject({ ...p, isArchived: !p.isArchived });
    }
  };

  const handleExportProject = async (p: Project) => {
    await ExportService.exportProjectZip(p);
  };

  const handleSelectActiveModel = (modelId: string) => {
    const model = models.find(m => m.id === modelId);
    if (!model) return;

    setHardware(prev => {
      const updated: HardwareStatus = {
        ...prev,
        activeModelId: modelId,
        nativeHeapUsedMB: model.fileSizeMB, // Simulates loading directly in Native C++ Heap!
      };
      HardwareProfiler.saveStatus(updated);
      return updated;
    });
  };

  const handleUpdateModel = (updated: LocalModel) => {
    AppStorage.updateModel(updated);
    setModels(AppStorage.getModels());
  };

  const handleDeleteModel = (modelId: string) => {
    const updated = models.map(m => m.id === modelId ? { ...m, status: 'available' as const, checksumVerified: false } : m);
    AppStorage.saveModels(updated);
    setModels(updated);
    if (hardware.activeModelId === modelId) {
      setHardware(prev => ({ ...prev, activeModelId: null, nativeHeapUsedMB: 0 }));
    }
  };

  const handleImportLocalFile = (model: LocalModel) => {
    const updated = [model, ...models];
    AppStorage.saveModels(updated);
    setModels(updated);
    handleSelectActiveModel(model.id);
  };

  const handleOpenQualityReview = (promptText: string, analysis?: IdeaAnalysis) => {
    setQualityPromptText(promptText);
    setQualityAnalysis(analysis);
    setIsQualityModalOpen(true);
  };

  const handleApplyFixToPrompt = (fixText: string) => {
    if (!activeProject) return;
    const currentPrompt = activeProject.prompts[activeProject.currentPromptIndex] || activeProject.prompts[0];
    const newText = (currentPrompt?.promptText || '') + '\n' + fixText;

    if (activeProject.prompts.length === 0) {
      handleUpdateProject({
        ...activeProject,
        prompts: [
          {
            id: `pv_${Date.now()}`,
            versionNumber: 1,
            timestamp: new Date().toISOString(),
            targetPlatform: activeProject.targetPlatform,
            detailLevel: activeProject.detailLevel,
            promptText: newText,
          },
        ],
        currentPromptIndex: 0,
      });
    } else {
      const updatedPrompts = [...activeProject.prompts];
      updatedPrompts[activeProject.currentPromptIndex] = {
        ...updatedPrompts[activeProject.currentPromptIndex],
        promptText: newText,
      };
      handleUpdateProject({
        ...activeProject,
        prompts: updatedPrompts,
      });
    }
  };

  const screenTitles: Record<string, string> = {
    dashboard: 'شاشة العرض الرئيسية (3D Studio Deck)',
    projects: 'شاشة إدارة المشاريع والمسودات',
    studio: 'شاشة هندسة البرومبت ومختبر المقارنة',
    prd: 'شاشة هندسة النظم ووثيقة PRD المتسلسلة',
    models: 'شاشة إدارة النماذج المحلية (GGUF Engine)',
    vision: 'شاشة التحليل البصري للواجهات',
    research: 'شاشة البحث التقني المتقدم',
    diagrams: 'شاشة المخططات المعمارية والجاهزية',
    enterprise: 'شاشة الأمان المؤسسي وحاسبة التكاليف (OWASP & FinOps)',
    settings: 'شاشة إعدادات العتاد والخادم الهجين',
  };

  return (
    <div
      className={`min-h-screen flex flex-col bg-slate-950 text-slate-100 font-sans selection:bg-purple-600 selection:text-white ${
        isEyeCareWarm ? 'eye-comfort-warmth' : ''
      }`}
    >
      {/* 1. Header (Adhering to Top Bar Contract) */}
      <Header
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        onNewPrompt={handleNewPrompt}
        darkMode={darkMode}
        onToggleDarkMode={handleToggleDarkMode}
      />

      {/* 2. Status & Hardware Bar */}
      <StatusBar
        hardware={hardware}
        activeModel={activeModel}
        onToggleAirplaneMode={handleToggleAirplaneMode}
        onOpenAndroidCode={() => setIsAndroidCodeModalOpen(true)}
        onOpenModels={() => setCurrentTab('models')}
      />

      {/* 3. Main 3D Extruded Workspace Stage */}
      <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 studio-3d-stage">
        <div
          className={`max-w-[1400px] mx-auto screen-3d-chassis p-5 sm:p-7 ${
            is3DTilt ? 'screen-3d-tilted' : ''
          }`}
        >
          {/* 3D Beveled Display Screen Top Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 pb-4 mb-6 border-b border-slate-800/90">
            <div className="flex items-center gap-3 text-xs text-slate-300">
              <span className="font-bold text-white text-sm">
                {screenTitles[currentTab] || 'شاشة العرض الهندسية'}
              </span>
              {activeProject && (
                <>
                  <span aria-hidden="true" className="text-slate-600">·</span>
                  <span className="text-purple-300 font-medium">{activeProject.name}</span>
                </>
              )}
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setIs3DTilt(prev => !prev)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all cursor-pointer whitespace-nowrap ${
                  is3DTilt
                    ? 'bg-purple-700 text-white border-purple-500'
                    : 'bg-slate-900 text-slate-300 border-slate-700 hover:text-white'
                }`}
                title="تبديل زاوية المنظور ثلاثي الأبعاد لشاشة العرض"
              >
                {is3DTilt ? 'منظور 3D المائل: مفعّل' : 'شاشة 3D البارزة (تفعيل الإمالة)'}
              </button>

              <button
                onClick={() => setIsEyeCareWarm(prev => !prev)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all cursor-pointer whitespace-nowrap ${
                  isEyeCareWarm
                    ? 'bg-amber-900/50 text-amber-200 border-amber-600/50'
                    : 'bg-slate-900 text-slate-300 border-slate-700 hover:text-white'
                }`}
                title="تفعيل فلتر الدفء المريح للعين للقراءة الطويلة"
              >
                {isEyeCareWarm ? 'فلتر راحة العين: دافئ' : 'فلتر راحة العين (Eye-Care)'}
              </button>
            </div>
          </div>

          {currentTab === 'dashboard' && (
          <DashboardView
            projects={projects}
            hardware={hardware}
            activeModel={activeModel}
            onOpenProject={handleOpenProject}
            onNewPrompt={handleNewPrompt}
            onSelectTab={setCurrentTab}
            onExportProject={handleExportProject}
            onCreateFromBlueprint={(bp: IndustryBlueprint) => {
              const newProj = createProjectFromBlueprint(bp);
              const updatedList = [newProj, ...projects];
              setProjects(updatedList);
              AppStorage.saveProjects(updatedList);
              setActiveProjectId(newProj.id);
              setCurrentTab('studio');
            }}
            onOpenQrSync={() => setIsQrSyncModalOpen(true)}
          />
        )}

        {currentTab === 'projects' && (
          <ProjectListView
            projects={projects}
            onOpenProject={handleOpenProject}
            onCreateProject={handleCreateProject}
            onDuplicateProject={handleDuplicateProject}
            onDeleteProject={handleDeleteProject}
            onTogglePinProject={handleTogglePinProject}
            onToggleArchiveProject={handleToggleArchiveProject}
            onExportProject={handleExportProject}
          />
        )}

        {currentTab === 'studio' && (
          activeProject ? (
            <PromptStudioView
              project={activeProject}
              onUpdateProject={handleUpdateProject}
              isAirplaneMode={hardware.isAirplaneMode}
              onOpenQualityReview={handleOpenQualityReview}
              onOpenPrdPipeline={() => setCurrentTab('prd')}
            />
          ) : (
            <div className="max-w-lg mx-auto my-16 p-8 bg-slate-900/70 border border-slate-800 rounded-2xl text-center space-y-4">
              <h2 className="text-base font-bold text-white">لا يوجد مشروع محدد حالياً</h2>
              <p className="text-xs text-slate-400">قم بإنشاء مشروع جديد لبدء تحليل الفكرة وتوليد البرومبت الهندسي.</p>
              <button
                onClick={handleNewPrompt}
                className="px-5 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold cursor-pointer"
              >
                إنشاء مشروع جديد
              </button>
            </div>
          )
        )}

        {currentTab === 'prd' && (
          activeProject ? (
            <ChainedPrdView
              project={activeProject}
              onUpdateProject={handleUpdateProject}
              isAirplaneMode={hardware.isAirplaneMode}
            />
          ) : (
            <div className="max-w-lg mx-auto my-16 p-8 bg-slate-900/70 border border-slate-800 rounded-2xl text-center space-y-4">
              <h2 className="text-base font-bold text-white">لا يوجد مشروع محدد حالياً</h2>
              <button
                onClick={handleNewPrompt}
                className="px-5 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold cursor-pointer"
              >
                إنشاء مشروع جديد
              </button>
            </div>
          )
        )}

        {currentTab === 'models' && (
          <ModelCenterView
            models={models}
            hardware={hardware}
            activeModelId={hardware.activeModelId}
            onSelectActiveModel={handleSelectActiveModel}
            onUpdateModel={handleUpdateModel}
            onDeleteModel={handleDeleteModel}
            onImportLocalFile={handleImportLocalFile}
          />
        )}

        {currentTab === 'vision' && (
          <VisionAnalyzerView
            isAirplaneMode={hardware.isAirplaneMode}
            onApplyPromptToStudio={prompt => {
              if (activeProject) {
                handleApplyFixToPrompt(prompt);
                setCurrentTab('studio');
              }
            }}
          />
        )}

        {currentTab === 'research' && (
          <ResearchCloudView isAirplaneMode={hardware.isAirplaneMode} />
        )}

        {currentTab === 'diagrams' && (
          <VisualArchitectureView
            onBackToDashboard={() => setCurrentTab('dashboard')}
            isAirplaneMode={hardware.isAirplaneMode}
          />
        )}

        {currentTab === 'enterprise' && (
          <EnterpriseSuiteView
            project={activeProject}
            projects={projects}
            onSelectProject={id => setActiveProjectId(id)}
            onImportDecryptedProject={decryptedProj => {
              const updatedList = [decryptedProj, ...projects];
              setProjects(updatedList);
              AppStorage.saveProjects(updatedList);
              setActiveProjectId(decryptedProj.id);
              setCurrentTab('studio');
            }}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView
            hardware={hardware}
            onUpdateHardware={setHardware}
            projects={projects}
            models={models}
            onRefreshData={() => {
              setProjects(AppStorage.getProjects());
              setModels(AppStorage.getModels());
            }}
          />
        )}
        </div>
      </main>

      {/* Offline PWA Banner */}
      <OfflineIndicator />

      {/* 4. Quality Review Modal */}
      <QualityReviewModal
        isOpen={isQualityModalOpen}
        onClose={() => setIsQualityModalOpen(false)}
        promptText={qualityPromptText}
        analysis={qualityAnalysis}
        onApplyFix={handleApplyFixToPrompt}
        isAirplaneMode={hardware.isAirplaneMode}
      />

      {/* 5. Android Code Explorer Modal */}
      <AndroidCodeModal
        isOpen={isAndroidCodeModalOpen}
        onClose={() => setIsAndroidCodeModalOpen(false)}
      />

      {/* 6. Instant QR Device Sync Modal */}
      {activeProject && (
        <QrSyncModal
          isOpen={isQrSyncModalOpen}
          onClose={() => setIsQrSyncModalOpen(false)}
          activeProject={activeProject}
          onImportSyncedProject={(syncedProj: Project) => {
            const updatedList = [syncedProj, ...projects];
            setProjects(updatedList);
            AppStorage.saveProjects(updatedList);
            setActiveProjectId(syncedProj.id);
            setCurrentTab('studio');
          }}
        />
      )}
    </div>
  );
}
