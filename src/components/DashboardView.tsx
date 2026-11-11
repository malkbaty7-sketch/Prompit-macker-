import React from 'react';
import {
  Sparkles,
  FileCode,
  Layers,
  Image as ImageIcon,
  ShieldAlert,
  ArrowUpLeft,
  Pin,
  Cpu,
  CheckCircle2,
  HardDrive,
  Download,
  BookOpen,
  Settings as SettingsIcon,
  QrCode,
  Code2,
  LayoutGrid,
} from 'lucide-react';
import { Project, HardwareStatus, LocalModel } from '../types';
import { INDUSTRY_BLUEPRINTS, IndustryBlueprint } from '../services/blueprintsCatalog';
import { ExportService } from '../services/exportService';

interface DashboardViewProps {
  projects: Project[];
  hardware: HardwareStatus;
  activeModel?: LocalModel;
  onOpenProject: (projectId: string) => void;
  onNewPrompt: () => void;
  onSelectTab: (tab: string) => void;
  onExportProject: (project: Project) => void;
  onCreateFromBlueprint?: (bp: IndustryBlueprint) => void;
  onOpenQrSync?: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  projects,
  hardware,
  activeModel,
  onOpenProject,
  onNewPrompt,
  onSelectTab,
  onExportProject,
  onCreateFromBlueprint,
  onOpenQrSync,
}) => {
  const recentProjects = projects.slice(0, 4);

  return (
    <div className="space-y-8 max-w-7xl mx-auto pb-12">
      {/* Hero Section */}
      <div className="bg-gradient-to-b from-slate-900 to-slate-950 border border-slate-800 rounded-2xl p-6 md:p-8 relative overflow-hidden">
        <div className="absolute top-0 left-0 w-96 h-96 bg-purple-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 max-w-3xl space-y-4">
          <div className="flex items-center gap-2 text-xs text-purple-400 font-medium">
            <span>الإصدار 2.0 المعتمد</span>
            <span aria-hidden="true">·</span>
            <span>Local-First بنسبة 100%</span>
            <span aria-hidden="true">·</span>
            <span>Native Android (Kotlin & Jetpack Compose)</span>
          </div>

          <h1 className="text-2xl md:text-3xl lg:text-4xl font-extrabold text-white tracking-tight leading-snug">
            حوّل وصف فكرتك العفوي إلى مواصفات برمجية دقيقة وهياكل مشاريع جاهزة للتشغيل
          </h1>

          <p className="text-sm md:text-base text-slate-300 leading-relaxed max-w-2xl">
            استوديو هندسي مستقل يعمل محلياً بالكامل دون إنترنت أو بوضع هجين مع الخادم السحابي، مجهز بمكتبة قوالب صناعية، ومولد هياكل أكواد كاملة (Codebase Scaffolding)، ومزامنة فورية عبر رمز QR.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <button
              onClick={onNewPrompt}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-purple-700 hover:bg-purple-600 active:bg-purple-800 text-white text-sm font-semibold rounded-lg shadow-lg shadow-purple-900/40 transition-colors cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>بدء تحليل فكرة جديدة</span>
            </button>

            <button
              onClick={() => onSelectTab('prd')}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 text-sm font-medium rounded-lg transition-colors cursor-pointer"
            >
              <Layers className="w-4 h-4 text-purple-400" />
              <span>هندسة تطبيق بنظام التجزئة (PRD)</span>
            </button>

            {onOpenQrSync && (
              <button
                onClick={onOpenQrSync}
                className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-purple-200 border border-purple-500/40 text-sm font-medium rounded-lg transition-colors cursor-pointer"
              >
                <QrCode className="w-4 h-4 text-purple-400" />
                <span>مزامنة فورية مع الهاتف (QR Sync)</span>
              </button>
            )}

            <button
              onClick={() => onSelectTab('enterprise')}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-emerald-300 border border-emerald-700/50 text-sm font-medium rounded-lg transition-colors cursor-pointer"
            >
              <ShieldAlert className="w-4 h-4 text-emerald-400" />
              <span>حزمة الأمان والمؤسسات (OWASP & FinOps)</span>
            </button>

            <button
              onClick={() => onSelectTab('models')}
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 text-sm font-medium rounded-lg transition-colors cursor-pointer"
            >
              <HardDrive className="w-4 h-4 text-emerald-400" />
              <span>مركز النماذج والتنزيل</span>
            </button>
          </div>
        </div>
      </div>

      {/* Quick Launch Action Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div
          onClick={onNewPrompt}
          className="bg-slate-900/70 border border-slate-800 hover:border-purple-600/50 rounded-xl p-5 transition-all hover:shadow-md hover:shadow-purple-950/40 cursor-pointer group"
        >
          <div className="w-10 h-10 rounded-lg bg-purple-950/80 border border-purple-800/60 flex items-center justify-center text-purple-400 mb-4 group-hover:scale-105 transition-transform">
            <Sparkles className="w-5 h-5" />
          </div>
          <h2 className="text-base font-semibold text-white mb-1.5 flex items-center justify-between">
            <span>محلل الأفكار والإملاء الصوتي</span>
            <ArrowUpLeft className="w-4 h-4 text-slate-500 group-hover:text-purple-400 transition-colors" />
          </h2>
          <p className="text-xs text-slate-400 leading-relaxed">
            استخراج المتطلبات وفصل الحقائق المؤكدة عن الافتراضات مع دعم الإملاء الصوتي بالعربية ومختبر البرومبت.
          </p>
        </div>

        <div
          onClick={() => onSelectTab('prd')}
          className="bg-slate-900/70 border border-slate-800 hover:border-indigo-600/50 rounded-xl p-5 transition-all hover:shadow-md hover:shadow-indigo-950/40 cursor-pointer group"
        >
          <div className="w-10 h-10 rounded-lg bg-indigo-950/80 border border-indigo-800/60 flex items-center justify-center text-indigo-400 mb-4 group-hover:scale-105 transition-transform">
            <Layers className="w-5 h-5" />
          </div>
          <h2 className="text-base font-semibold text-white mb-1.5 flex items-center justify-between">
            <span>التجزئة المتسلسلة (PRD)</span>
            <ArrowUpLeft className="w-4 h-4 text-slate-500 group-hover:text-indigo-400 transition-colors" />
          </h2>
          <p className="text-xs text-slate-400 leading-relaxed">
            توليد وثيقة PRD والمخطط المعماري النصي وشاشات Compose ومخطط Room في 5 خطوات.
          </p>
        </div>

        <div
          onClick={() => onSelectTab('vision')}
          className="bg-slate-900/70 border border-slate-800 hover:border-emerald-600/50 rounded-xl p-5 transition-all hover:shadow-md hover:shadow-emerald-950/40 cursor-pointer group"
        >
          <div className="w-10 h-10 rounded-lg bg-emerald-950/80 border border-emerald-800/60 flex items-center justify-center text-emerald-400 mb-4 group-hover:scale-105 transition-transform">
            <ImageIcon className="w-5 h-5" />
          </div>
          <h2 className="text-base font-semibold text-white mb-1.5 flex items-center justify-between">
            <span>تحليل الواجهات (Vision)</span>
            <ArrowUpLeft className="w-4 h-4 text-slate-500 group-hover:text-emerald-400 transition-colors" />
          </h2>
          <p className="text-xs text-slate-400 leading-relaxed">
            استخراج مواصفات UI محلياً عبر تقنية ML Kit وتوليد كود Jetpack Compose فوري.
          </p>
        </div>

        <div
          onClick={() => {
            if (projects.length > 0) onOpenProject(projects[0].id);
          }}
          className="bg-slate-900/70 border border-slate-800 hover:border-amber-600/50 rounded-xl p-5 transition-all hover:shadow-md hover:shadow-amber-950/40 cursor-pointer group"
        >
          <div className="w-10 h-10 rounded-lg bg-amber-950/80 border border-amber-800/60 flex items-center justify-center text-amber-400 mb-4 group-hover:scale-105 transition-transform">
            <ShieldAlert className="w-5 h-5" />
          </div>
          <h2 className="text-base font-semibold text-white mb-1.5 flex items-center justify-between">
            <span>مراجعة الجودة ومقارنة الإصدارات</span>
            <ArrowUpLeft className="w-4 h-4 text-slate-500 group-hover:text-amber-400 transition-colors" />
          </h2>
          <p className="text-xs text-slate-400 leading-relaxed">
            فحص حتمي لاكتمال الشروط ومقارنة A/B بين إصدارات البرومبت جنباً إلى جنب.
          </p>
        </div>
      </div>

      {/* Upgrade 4: Ready-Made Industry Blueprints Library */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <LayoutGrid className="w-5 h-5 text-purple-400" />
            <h2 className="text-lg font-bold text-white">مكتبة القوالب الهندسية الجاهزة (Industry Blueprints)</h2>
          </div>
          <span className="text-xs text-slate-400">انطلق بمشروعك فوراً من قالب معماري مكتمل</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {INDUSTRY_BLUEPRINTS.map(bp => (
            <div
              key={bp.id}
              className="bg-slate-900/80 border border-slate-800 hover:border-purple-500/40 rounded-xl p-5 flex flex-col justify-between space-y-4 transition-all"
            >
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-[11px] text-purple-400 font-medium">
                  <span>{bp.category}</span>
                  <span aria-hidden="true">·</span>
                  <span className="font-mono">{bp.targetPlatform}</span>
                </div>
                <h3 className="text-sm font-bold text-white leading-snug">{bp.name}</h3>
                <p className="text-xs text-slate-400 leading-relaxed line-clamp-3">{bp.description}</p>
              </div>

              <button
                onClick={() => onCreateFromBlueprint && onCreateFromBlueprint(bp)}
                className="w-full py-2 px-3 bg-purple-950/60 hover:bg-purple-700 text-purple-200 hover:text-white border border-purple-500/30 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
              >
                استخدام القالب وإنشاء المشروع
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Pinned & Recent Projects Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-white">المشاريع المثبتة والنشطة</h2>
            <span className="text-xs text-slate-500 font-mono">({projects.length})</span>
          </div>

          <button
            onClick={() => onSelectTab('projects')}
            className="text-xs font-medium text-purple-400 hover:text-purple-300 transition-colors cursor-pointer"
          >
            عرض جميع المشاريع &larr;
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {recentProjects.map(project => {
            const prompts = Array.isArray(project.prompts) ? project.prompts : [];
            const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
            return (
              <div
                key={project.id}
                className="bg-slate-900/80 border border-slate-800 hover:border-slate-700 rounded-xl p-5 space-y-3 transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      {project.isPinned && <Pin className="w-3.5 h-3.5 text-purple-400 fill-purple-400/20" />}
                      <h3
                        onClick={() => onOpenProject(project.id)}
                        className="text-base font-semibold text-white hover:text-purple-300 transition-colors cursor-pointer"
                      >
                        {project.name}
                      </h3>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-400">
                      <span>{project.targetPlatform || 'Android'}</span>
                      <span aria-hidden="true">·</span>
                      <span>تحديث {project.updatedAt || 'الآن'}</span>
                    </div>
                  </div>
                </div>

                <p className="text-xs text-slate-300 leading-relaxed line-clamp-2">{project.description}</p>

                <div className="pt-2 border-t border-slate-800/80 flex flex-wrap items-center justify-between gap-2 text-xs">
                  <div className="flex items-center gap-2 text-slate-400">
                    <FileCode className="w-3.5 h-3.5 text-purple-400" />
                    <span>الإصدار {currentPrompt ? `v${currentPrompt.versionNumber}` : 'مسودة'}</span>
                    {project.chainedPrd && (
                      <>
                        <span aria-hidden="true">·</span>
                        <span className="text-emerald-400">PRD مكتمل</span>
                      </>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => ExportService.exportStarterCodebaseZip(project)}
                      className="px-2.5 py-1 text-purple-200 hover:text-white bg-purple-950/60 hover:bg-purple-800 border border-purple-500/30 rounded text-xs transition-colors cursor-pointer flex items-center gap-1"
                      title="توليد وتنزيل هيكل كود المشروع القابل للتشغيل (Starter Codebase ZIP)"
                    >
                      <Code2 className="w-3.5 h-3.5" />
                      <span>كود المشروع (ZIP)</span>
                    </button>

                    <button
                      onClick={() => onExportProject(project)}
                      className="px-2.5 py-1 text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded text-xs transition-colors cursor-pointer"
                      title="تصدير حزمة وثائق المشروع"
                    >
                      <Download className="w-3.5 h-3.5 inline ml-1" />
                      الوثائق
                    </button>

                    <button
                      onClick={() => onOpenProject(project.id)}
                      className="px-3 py-1 text-white bg-purple-700 hover:bg-purple-600 rounded text-xs font-medium transition-colors cursor-pointer"
                    >
                      فتح المشروع
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Hardware Profile & Memory Guard Overview */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-1">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Cpu className="w-4 h-4 text-purple-400" />
              <span>فاحص العتاد وإدارة الذاكرة الصارمة (HardwareProfiler)</span>
            </h2>
            <p className="text-xs text-slate-400">
              معادلة الأمان المعتمدة: <code className="bg-slate-800 text-purple-300 px-2 py-0.5 rounded font-mono">حجم ملف النموذج × 1.25 &le; الذاكرة العشوائية المتاحة</code> لمنع انهيار OOM.
            </p>
          </div>

          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-400">حالة النموذج النشط:</span>
            <span className="text-emerald-400 font-semibold">{activeModel ? activeModel.name : 'غير محمل'}</span>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-2">
          <div className="bg-slate-950/80 border border-slate-800/80 rounded-lg p-3">
            <div className="text-[11px] text-slate-400 mb-1">الذاكرة الكلية للجهاز</div>
            <div className="text-lg font-bold text-white font-mono tabular-nums">
              {(hardware.totalRamMB / 1024).toFixed(1)} GB
            </div>
            <div className="text-[10px] text-slate-500">معدل الهواتف المتوسطة المستهدفة</div>
          </div>

          <div className="bg-slate-950/80 border border-slate-800/80 rounded-lg p-3">
            <div className="text-[11px] text-slate-400 mb-1">الرام المتاح فعلياً</div>
            <div className="text-lg font-bold text-emerald-400 font-mono tabular-nums">
              {(hardware.availableRamMB / 1024).toFixed(2)} GB
            </div>
            <div className="text-[10px] text-emerald-500/80">جاهز للتحميل بأمان</div>
          </div>

          <div className="bg-slate-950/80 border border-slate-800/80 rounded-lg p-3">
            <div className="text-[11px] text-slate-400 mb-1">الذاكرة الأصلية (Native Heap)</div>
            <div className="text-lg font-bold text-indigo-400 font-mono tabular-nums">
              {hardware.nativeHeapUsedMB} MB
            </div>
            <div className="text-[10px] text-slate-500">مخصص لـ llama.cpp عبر JNI</div>
          </div>

          <div className="bg-slate-950/80 border border-slate-800/80 rounded-lg p-3">
            <div className="text-[11px] text-slate-400 mb-1">ذاكرة JVM المعزولة</div>
            <div className="text-lg font-bold text-slate-300 font-mono tabular-nums">
              {hardware.jvmHeapUsedMB} MB
            </div>
            <div className="text-[10px] text-slate-500">مخصص لواجهة Compose فقط</div>
          </div>
        </div>
      </div>

      {/* Quick Access Utility Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <button
          onClick={() => onSelectTab('research')}
          className="p-3 bg-slate-900/50 hover:bg-slate-900 border border-slate-800 hover:border-purple-600/40 rounded-xl flex items-center justify-between text-right transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-2.5">
            <BookOpen className="w-4 h-4 text-purple-400" />
            <div>
              <div className="text-xs font-semibold text-white">البحث التقني والتوثيق</div>
              <div className="text-[11px] text-slate-400">مستندات Android وRoom الرسمية</div>
            </div>
          </div>
          <ArrowUpLeft className="w-3.5 h-3.5 text-slate-500" />
        </button>

        <button
          onClick={() => onSelectTab('diagrams')}
          className="p-3 bg-slate-900/50 hover:bg-slate-900 border border-slate-800 hover:border-indigo-600/40 rounded-xl flex items-center justify-between text-right transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-2.5">
            <Layers className="w-4 h-4 text-indigo-400" />
            <div>
              <div className="text-xs font-semibold text-white">المخططات المعمارية والنظم</div>
              <div className="text-[11px] text-slate-400">Clean Architecture وRoom ER</div>
            </div>
          </div>
          <ArrowUpLeft className="w-3.5 h-3.5 text-slate-500" />
        </button>

        <button
          onClick={() => onSelectTab('settings')}
          className="p-3 bg-slate-900/50 hover:bg-slate-900 border border-slate-800 hover:border-emerald-600/40 rounded-xl flex items-center justify-between text-right transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-2.5">
            <SettingsIcon className="w-4 h-4 text-emerald-400" />
            <div>
              <div className="text-xs font-semibold text-white">إعدادات الأداء والخادم السحابي</div>
              <div className="text-[11px] text-slate-400">خيوط المعالج والنسخ الاحتياطي</div>
            </div>
          </div>
          <ArrowUpLeft className="w-3.5 h-3.5 text-slate-500" />
        </button>
      </div>

      {/* Engineering Policy Assurance Checklist */}
      <div className="bg-slate-900/40 border border-slate-800/60 rounded-xl p-5 space-y-3">
        <h3 className="text-sm font-semibold text-slate-200">
          ضمانات المعمارية الصارمة للتطبيق (Constitution Compliance)
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 text-xs text-slate-300">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>يعمل 100% في وضع الطيران دون خوادم</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>حفظ تلقائي معزول بقاعدة Room</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>تحقق إلزامي من بصمة SHA-256 للنماذج</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>فصل قاطع بين الحقائق والافتراضات المقترحة</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>تصدير ملفات TXT وMD وPDF وStarter Codebase ZIP</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>تفريغ الذاكرة التلقائي عند الخمول وضغط النظام</span>
          </div>
        </div>
      </div>
    </div>
  );
};
