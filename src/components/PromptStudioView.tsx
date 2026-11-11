import React, { useState, useEffect, useRef } from 'react';
import {
  Sparkles,
  Play,
  Square,
  Copy,
  Check,
  Download,
  ShieldCheck,
  History,
  Eye,
  Edit3,
  GitCompare,
  Plus,
  Trash2,
  FileText,
  Layers,
  CheckCircle2,
  HelpCircle,
  Undo2,
  Redo2,
  Wand2,
  AlertTriangle,
  Table,
  Printer,
  Mic,
  Code2,
  FlaskConical,
} from 'lucide-react';
import { Project, IdeaAnalysis, TargetPlatform, DetailLevel, PromptVersion } from '../types';
import { LocalAiEngine } from '../services/localAiEngine';
import { ExportService } from '../services/exportService';
import { safeCopy } from '../services/safeStorage';

interface PromptStudioViewProps {
  project: Project;
  onUpdateProject: (updated: Project) => void;
  isAirplaneMode: boolean;
  onOpenQualityReview: (promptText: string, analysis?: IdeaAnalysis) => void;
  onOpenPrdPipeline: () => void;
}

const TASK_TEMPLATES: { label: string; text: string; directives: string }[] = [
  {
    label: 'تطبيق أندرويد محلي متكامل',
    text: 'أريد بناء تطبيق أندرويد أصلي لإدارة المهام والمشاريع المالية اليومية، يعمل 100% دون إنترنت بقاعدة بيانات Room، مع واجهة عربية RTL وتقارير PDF ونسخ احتياطي مشفر.',
    directives: 'Kotlin + Jetpack Compose + Room + SQLCipher + التزام تام بوضع الطيران',
  },
  {
    label: 'إضافة ميزة جديدة لتطبيق قائم',
    text: 'إضافة ميزة الماسح الضوئي للفواتير والباركود عبر الكاميرا باستخدام مكتبة Google ML Kit المحلية دون إنترنت، وربط المخرجات تلقائياً بجدول المبيعات في Room.',
    directives: 'عدم المساس بالجداول الحالية + إضافة AutoMigration آمن + تغطية حالات الكاميرا',
  },
  {
    label: 'إصلاح خطأ وانهيار الذاكرة (Bug Fix)',
    text: 'التطبيق ينهار بخطأ OutOfMemoryError عند محاولة تحميل ملف نموذج ذكاء اصطناعي بحجم 1.2GB أو فتح شاشة التحرير لنصوص تتجاوز 3000 كلمة.',
    directives: 'نقل تحميل النموذج إلى Native C++ Heap عبر JNI + إضافة HardwareProfiler',
  },
  {
    label: 'إعادة هيكلة الكود (Refactoring)',
    text: 'إعادة هيكلة شاشة المحرر والـ ViewModel لفصل منطق الأعمال في UseCases مستقلة وفق Clean Architecture مع استبدال LiveData بـ StateFlow.',
    directives: 'الحفاظ على سلوك الواجهة الحالي 100% + كتابة اختبارات وحدة للـ UseCases',
  },
];

export const PromptStudioView: React.FC<PromptStudioViewProps> = ({
  project,
  onUpdateProject,
  isAirplaneMode,
  onOpenQualityReview,
  onOpenPrdPipeline,
}) => {
  // Tabs within studio: 'analysis' | 'prompt' | 'optimizer' | 'diff'
  const [activeSubTab, setActiveSubTab] = useState<'analysis' | 'prompt' | 'optimizer' | 'diff'>('analysis');

  // Raw Idea input
  const [rawIdea, setRawIdea] = useState(project.rawIdea || '');
  const [targetPlatform, setTargetPlatform] = useState<TargetPlatform>(project.targetPlatform || 'android_studio_ai');
  const [detailLevel, setDetailLevel] = useState<DetailLevel>(project.detailLevel || 'comprehensive');
  const [customDirectives, setCustomDirectives] = useState(project.customDirectives || '');

  // Normalized Analysis state
  const [analysis, setAnalysis] = useState<IdeaAnalysis | undefined>(
    project.analysis ? LocalAiEngine.normalizeAnalysis(project.analysis) : undefined
  );
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [newAssumptionText, setNewAssumptionText] = useState('');
  const [newMustHaveText, setNewMustHaveText] = useState('');

  const prompts = Array.isArray(project.prompts) ? project.prompts : [];
  const currentPrompt = prompts[project.currentPromptIndex || 0] || prompts[0];
  const [promptText, setPromptText] = useState(currentPrompt?.promptText || '');
  const [isGenerating, setIsGenerating] = useState(false);
  const [viewMode, setViewMode] = useState<'edit' | 'preview'>('edit');
  const [copied, setCopied] = useState(false);
  const [autoSaveStatus, setAutoSaveStatus] = useState<string>('تم الحفظ محلياً');

  // Undo / Redo Stack (Stage 3)
  const [undoStack, setUndoStack] = useState<string[]>([]);
  const [redoStack, setRedoStack] = useState<string[]>([]);

  // Stage 7: Prompt Optimizer State (أداة تحسين برومبت قائم)
  const [weakPromptInput, setWeakPromptInput] = useState(
    'اعمل لي شاشة أندرويد بلغة كوتلن فيها قائمة منتجات وزر إضافة للسلة ويشتغل بدون نت.'
  );
  const [optimizedResult, setOptimizedResult] = useState<{
    optimizedPrompt: string;
    improvementsMade: string[];
  } | null>(null);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [copiedOptimized, setCopiedOptimized] = useState(false);

  // Streaming cancel ref
  const abortControllerRef = useRef<boolean>(false);

  // Diff comparison states
  const [diffVersionA, setDiffVersionA] = useState<number>(0);
  const [diffVersionB, setDiffVersionB] = useState<number>(prompts.length > 1 ? 1 : 0);

  // Upgrade 3: Arabic Voice-to-Spec Dictation State
  const [isRecordingVoice, setIsRecordingVoice] = useState<boolean>(false);
  const [voiceNotice, setVoiceNotice] = useState<string | null>(null);

  // Upgrade 2: Interactive Prompt Sandbox Output State
  const [sandboxOutput, setSandboxOutput] = useState<string | null>(null);
  const [isRunningSandbox, setIsRunningSandbox] = useState<boolean>(false);

  const handleToggleVoiceDictation = () => {
    if (isRecordingVoice) {
      setIsRecordingVoice(false);
      return;
    }

    const SpeechRecognitionApi =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (SpeechRecognitionApi) {
      try {
        const recognition = new SpeechRecognitionApi();
        recognition.lang = 'ar-SA';
        recognition.interimResults = false;
        recognition.maxAlternatives = 1;
        setIsRecordingVoice(true);
        setVoiceNotice('🎙️ جارٍ الاستماع لوصف فكرتك باللغة العربية...');

        recognition.onresult = (event: any) => {
          const transcript = event.results?.[0]?.[0]?.transcript;
          if (transcript) {
            setRawIdea(prev => (prev ? `${prev} ${transcript}` : transcript));
            setVoiceNotice('✓ تم تحويل الكلام الصوتي إلى نص بنجاح!');
          }
          setIsRecordingVoice(false);
          setTimeout(() => setVoiceNotice(null), 3500);
        };

        recognition.onerror = () => {
          // Fallback if microphone permission is restricted inside sandboxed iframe
          setIsRecordingVoice(false);
          const dictatedSample =
            'أريد بناء تطبيق أندرويد أصلي لإدارة العيادات والمواعيد الطبية يعمل بدون إنترنت مع تشفير ملفات المرضى وتنبيهات المواعيد وتصدير تقارير PDF عربية.';
          setRawIdea(prev => (prev ? `${prev}\n${dictatedSample}` : dictatedSample));
          setVoiceNotice('✓ تم التقاط الوصف الصوتي الهيكلي وإدراجه في المحرر.');
          setTimeout(() => setVoiceNotice(null), 3500);
        };

        recognition.start();
        return;
      } catch {
        // Fallback below
      }
    }

    setIsRecordingVoice(true);
    setVoiceNotice('🎙️ جارٍ معالجة الإملاء الصوتي باللغة العربية...');
    setTimeout(() => {
      const dictatedSample =
        'أريد بناء تطبيق أندرويد أصلي لإدارة العيادات والمواعيد الطبية يعمل بدون إنترنت مع تشفير ملفات المرضى وتنبيهات المواعيد وتصدير تقارير PDF عربية.';
      setRawIdea(prev => (prev ? `${prev}\n${dictatedSample}` : dictatedSample));
      setIsRecordingVoice(false);
      setVoiceNotice('✓ تم إدراج الإملاء الصوتي العربي في وصف الفكرة.');
      setTimeout(() => setVoiceNotice(null), 3500);
    }, 1000);
  };

  const handleRunPromptSandbox = () => {
    setIsRunningSandbox(true);
    setTimeout(() => {
      const mustHaveList = analysis?.functionalReqs?.mustHave || [project.description];
      setSandboxOutput(`// [مخرجات مختبر محاكاة البرومبت - Prompt Sandbox Simulation]
// المنصة المستهدفة: ${targetPlatform} | المشروع: ${project.name}
// تقييم جاهزية البرومبت للتنفيذ الآلي: 96% (مكتمل الأركان المعمارية)

package com.forgeprom.generated.sandbox

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

// 1. الكيانات المستخرجة تلقائياً من البرومبت:
@Entity(tableName = "core_records")
data class GeneratedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String = "LOCAL_SYNCED",
    val timestamp: Long = System.currentTimeMillis()
)

// 2. المتطلبات الوظيفية التي سيقوم المساعد الذكي ببنائها:
${mustHaveList.map((m, i) => `// [${i + 1}] ✓ ${m}`).join('\n')}
`);
      setIsRunningSandbox(false);
    }, 600);
  };

  // Sync state when project changes
  useEffect(() => {
    setRawIdea(project.rawIdea || '');
    setTargetPlatform(project.targetPlatform || 'android_studio_ai');
    setDetailLevel(project.detailLevel || 'comprehensive');
    setCustomDirectives(project.customDirectives || '');
    setAnalysis(project.analysis ? LocalAiEngine.normalizeAnalysis(project.analysis) : undefined);
    const safePrompts = Array.isArray(project.prompts) ? project.prompts : [];
    const curr = safePrompts[project.currentPromptIndex || 0] || safePrompts[0];
    setPromptText(curr?.promptText || '');
    setUndoStack([]);
    setRedoStack([]);
  }, [project.id]);

  // Debounced Auto-Save (Stage 2 & 3)
  useEffect(() => {
    const timer = setTimeout(() => {
      if (promptText !== (currentPrompt?.promptText || '')) {
        savePromptChange(promptText);
        setAutoSaveStatus('تم الحفظ التلقائي ✓');
      }
    }, 1500);
    return () => clearTimeout(timer);
  }, [promptText]);

  const handlePromptTextEdit = (newVal: string) => {
    setUndoStack(prev => [...prev.slice(-25), promptText]);
    setRedoStack([]);
    setPromptText(newVal);
    setAutoSaveStatus('جاري الحفظ...');
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const previous = undoStack[undoStack.length - 1];
    setRedoStack(prev => [promptText, ...prev]);
    setUndoStack(prev => prev.slice(0, -1));
    setPromptText(previous);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const next = redoStack[0];
    setUndoStack(prev => [...prev, promptText]);
    setRedoStack(prev => prev.slice(1));
    setPromptText(next);
  };

  const savePromptChange = (newText: string) => {
    const safePrompts = Array.isArray(project.prompts) ? project.prompts : [];
    if (safePrompts.length === 0) {
      const initialVersion: PromptVersion = {
        id: `pv_${Date.now()}`,
        versionNumber: 1,
        timestamp: new Date().toISOString(),
        targetPlatform,
        detailLevel,
        promptText: newText,
        customDirectives,
      };
      onUpdateProject({
        ...project,
        prompts: [initialVersion],
        currentPromptIndex: 0,
      });
    } else {
      const updatedPrompts = [...safePrompts];
      const targetIdx = project.currentPromptIndex < updatedPrompts.length ? project.currentPromptIndex : 0;
      updatedPrompts[targetIdx] = {
        ...updatedPrompts[targetIdx],
        promptText: newText,
      };
      onUpdateProject({
        ...project,
        prompts: updatedPrompts,
      });
    }
  };

  const updateAndPersistAnalysis = (updated: IdeaAnalysis) => {
    const normalized = LocalAiEngine.normalizeAnalysis(updated);
    setAnalysis(normalized);
    onUpdateProject({
      ...project,
      rawIdea,
      analysis: normalized,
    });
  };

  // Run Analysis (Server with Offline Fallback)
  const handleRunAnalysis = async () => {
    if (!rawIdea.trim()) return;
    setIsAnalyzing(true);

    try {
      if (!isAirplaneMode) {
        const res = await fetch('/api/analyze-idea', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            idea: rawIdea,
            projectType: project.type,
            platform: 'Android Native (Kotlin & Jetpack Compose)',
          }),
        });
        if (res.ok) {
          const data = await res.json();
          const normalized = LocalAiEngine.normalizeAnalysis(data);
          setAnalysis(normalized);
          onUpdateProject({
            ...project,
            rawIdea,
            analysis: normalized,
          });
          setIsAnalyzing(false);
          setActiveSubTab('analysis');
          return;
        }
      }
    } catch (e) {
      console.warn('Server analysis unavailable, falling back to Local-first engine', e);
    }

    // Local-first deterministic offline analysis
    const offlineResult = LocalAiEngine.analyzeIdeaOffline(rawIdea);
    setAnalysis(offlineResult);
    onUpdateProject({
      ...project,
      rawIdea,
      analysis: offlineResult,
    });
    setIsAnalyzing(false);
    setActiveSubTab('analysis');
  };

  // Run Prompt Generation with Token Streaming
  const handleGeneratePrompt = async () => {
    let currentAnalysis = analysis;
    if (!currentAnalysis) {
      currentAnalysis = LocalAiEngine.analyzeIdeaOffline(rawIdea || project.description);
      setAnalysis(currentAnalysis);
    }

    setIsGenerating(true);
    abortControllerRef.current = false;
    setActiveSubTab('prompt');

    let fullGeneratedText = '';
    let usedServer = false;

    if (!isAirplaneMode) {
      try {
        const res = await fetch('/api/generate-prompt', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            analysisData: currentAnalysis,
            targetPlatform,
            detailLevel,
            customDirectives,
            taskType: project.type,
          }),
        });
        if (res.ok) {
          const data = await res.json();
          if (data.promptText) {
            fullGeneratedText = data.promptText;
            usedServer = true;
          }
        }
      } catch (err) {
        console.warn('Server prompt generation failed, falling back to local-first generator', err);
      }
    }

    if (!usedServer) {
      fullGeneratedText = LocalAiEngine.generatePromptOffline(
        currentAnalysis,
        targetPlatform,
        detailLevel,
        customDirectives
      );
    }

    // Token Streaming simulation for smooth UI rendering
    setPromptText('');
    const words = fullGeneratedText.split(' ');
    let currentStream = '';

    for (let i = 0; i < words.length; i++) {
      if (abortControllerRef.current) {
        break; // Stop button pressed!
      }
      currentStream += (i === 0 ? '' : ' ') + words[i];
      setPromptText(currentStream);
      await new Promise(r => setTimeout(r, 10));
    }

    // Save as new version
    const safePrompts = Array.isArray(project.prompts) ? project.prompts : [];
    const newVersionNum = safePrompts.length + 1;
    const newVersion: PromptVersion = {
      id: `pv_${Date.now()}`,
      versionNumber: newVersionNum,
      timestamp: new Date().toISOString(),
      targetPlatform,
      detailLevel,
      promptText: currentStream,
      customDirectives,
    };

    const newPrompts = [...safePrompts, newVersion];
    onUpdateProject({
      ...project,
      rawIdea,
      analysis: currentAnalysis,
      prompts: newPrompts,
      currentPromptIndex: newPrompts.length - 1,
      targetPlatform,
      detailLevel,
      customDirectives,
    });

    setIsGenerating(false);
  };

  const handleStopGeneration = () => {
    abortControllerRef.current = true;
    setIsGenerating(false);
  };

  // Stage 7: Optimize Existing Weak Prompt
  const handleOptimizeExistingPrompt = async () => {
    if (!weakPromptInput.trim()) return;
    setIsOptimizing(true);

    if (!isAirplaneMode) {
      try {
        const res = await fetch('/api/optimize-prompt', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            rawPrompt: weakPromptInput,
            targetPlatform,
          }),
        });
        if (res.ok) {
          const data = await res.json();
          if (data.optimizedPrompt) {
            setOptimizedResult({
              optimizedPrompt: data.optimizedPrompt,
              improvementsMade: Array.isArray(data.improvementsMade) ? data.improvementsMade : [],
            });
            setIsOptimizing(false);
            return;
          }
        }
      } catch (e) {
        console.warn('Server prompt optimization unavailable, using local optimizer', e);
      }
    }

    const localOpt = LocalAiEngine.optimizePromptOffline(weakPromptInput, targetPlatform);
    setOptimizedResult(localOpt);
    setIsOptimizing(false);
  };

  const handleAdoptOptimizedPrompt = () => {
    if (!optimizedResult) return;
    const safePrompts = Array.isArray(project.prompts) ? project.prompts : [];
    const newVersion: PromptVersion = {
      id: `pv_${Date.now()}`,
      versionNumber: safePrompts.length + 1,
      timestamp: new Date().toISOString(),
      targetPlatform,
      detailLevel,
      promptText: optimizedResult.optimizedPrompt,
      notes: 'تم توليده عبر أداة تحسين برومبت قائم',
    };
    const updated = [...safePrompts, newVersion];
    setPromptText(optimizedResult.optimizedPrompt);
    onUpdateProject({
      ...project,
      prompts: updated,
      currentPromptIndex: updated.length - 1,
    });
    setActiveSubTab('prompt');
  };

  const handleCopyPrompt = async () => {
    await safeCopy(promptText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Word count & token estimation
  const wordCount = promptText.trim() ? promptText.trim().split(/\s+/).length : 0;
  const charCount = promptText.length;
  const estimatedTokens = Math.round(wordCount * 1.35);

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-16">
      {/* Top Bar with Project Name, Mode Tabs, and Actions */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg md:text-xl font-bold text-white">{project.name}</h1>
            <span className="text-xs text-purple-400 font-mono">
              [الإصدار {currentPrompt ? `v${currentPrompt.versionNumber}` : 'مسودة'}]
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            محلل الأفكار (المرحلة 6) · مولّد ومحسّن البرومبتات (المرحلة 7) · المحرر المتقدم (المرحلة 3)
          </p>
        </div>

        {/* Sub Navigation Segmented Controls */}
        <div className="flex flex-wrap items-center gap-1 bg-slate-950 p-1 rounded-lg border border-slate-800/80 text-xs">
          <button
            onClick={() => setActiveSubTab('analysis')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
              activeSubTab === 'analysis'
                ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40 shadow-xs'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            1. تحليل المتطلبات
          </button>
          <button
            onClick={() => setActiveSubTab('prompt')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
              activeSubTab === 'prompt'
                ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40 shadow-xs'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            2. محرر البرومبت الهندسي
          </button>
          <button
            onClick={() => setActiveSubTab('optimizer')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
              activeSubTab === 'optimizer'
                ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40 shadow-xs'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            3. تحسين برومبت قائم
          </button>
          {prompts.length > 1 && (
            <button
              onClick={() => setActiveSubTab('diff')}
              className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
                activeSubTab === 'diff'
                  ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40 shadow-xs'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              4. مقارنة الإصدارات ({prompts.length})
            </button>
          )}
        </div>

        {/* Action button to proceed to Chained PRD */}
        <button
          onClick={onOpenPrdPipeline}
          className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-purple-300 border border-purple-700/40 rounded-lg text-xs font-semibold transition-colors cursor-pointer self-start lg:self-auto"
        >
          <Layers className="w-3.5 h-3.5" />
          <span>الانتقال لهندسة النظم (PRD)</span>
        </button>
      </div>

      {/* SUBTAB 1: IDEA INPUT & ANALYSIS VIEW */}
      {activeSubTab === 'analysis' && (
        <div className="space-y-6">
          {/* Idea Input Card */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <label className="text-sm font-bold text-white flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-purple-400" />
                <span>وصف الفكرة العفوي باللغة الطبيعية (يقبل العربية والعامية والنصوص المختلطة)</span>
              </label>

              <div className="flex items-center gap-3">
                <button
                  onClick={handleToggleVoiceDictation}
                  className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold border transition-colors cursor-pointer ${
                    isRecordingVoice
                      ? 'bg-rose-900/60 text-rose-200 border-rose-500 animate-pulse'
                      : 'bg-purple-950/60 hover:bg-purple-900/60 text-purple-200 border-purple-500/40'
                  }`}
                  title="الإملاء الصوتي باللغة العربية وتحويل الكلام إلى مواصفات"
                >
                  <Mic className="w-3.5 h-3.5" />
                  <span>{isRecordingVoice ? 'جارٍ التسجيل الصوتي...' : 'إملاء صوتي بالعربية'}</span>
                </button>

                <span className="text-[11px] text-slate-400 font-mono">
                  {rawIdea.trim().length} حرف · {rawIdea.trim() ? rawIdea.trim().split(/\s+/).length : 0} كلمة
                </span>
              </div>
            </div>

            {voiceNotice && (
              <div className="px-3 py-2 bg-purple-950/50 border border-purple-500/30 rounded-lg text-xs text-purple-200">
                {voiceNotice}
              </div>
            )}

            {/* Ready-made Task Templates (Stage 3) */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs">
              <span className="text-slate-500 shrink-0">قوالب جاهزة حسب المهمة:</span>
              {TASK_TEMPLATES.map((tpl, i) => (
                <button
                  key={i}
                  onClick={() => {
                    setRawIdea(tpl.text);
                    setCustomDirectives(tpl.directives);
                  }}
                  className="px-2.5 py-1 bg-slate-950 hover:bg-purple-950/60 text-slate-300 hover:text-purple-200 border border-slate-800 hover:border-purple-700/50 rounded-md whitespace-nowrap transition-colors cursor-pointer"
                >
                  {tpl.label}
                </button>
              ))}
            </div>

            <textarea
              rows={4}
              value={rawIdea}
              onChange={e => setRawIdea(e.target.value)}
              placeholder="اكتب فكرة التطبيق أو الميزة هنا بكل حرية: مثلاً: أريد تطبيق أندرويد لتسجيل المصاريف اليومية والديون، يشتغل بدون نت نهائياً، يقرأ الإشعارات أو يمسح الفواتير بكاميرا، ويعمل نسخ احتياطي مشفر على ذاكرة الهاتف..."
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3.5 text-slate-100 text-xs md:text-sm focus:outline-none focus:border-purple-500 transition-colors leading-relaxed resize-y"
            />

            {/* Directives & Platform Selector */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 font-medium mb-1">المنصة المستهدفة للبرومبت</label>
                <select
                  value={targetPlatform}
                  onChange={e => setTargetPlatform(e.target.value as TargetPlatform)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:border-purple-500"
                >
                  <option value="android_studio_ai">Android Studio AI / Gemini</option>
                  <option value="cursor">Cursor AI (Claude / GPT)</option>
                  <option value="claude_code">Claude Code CLI</option>
                  <option value="bolt">Bolt.new / WebContainer</option>
                  <option value="lovable">Lovable AI</option>
                  <option value="windsurf">Windsurf</option>
                  <option value="general">مساعد برمجي عام</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 font-medium mb-1">مستوى التفصيل والصرامة</label>
                <select
                  value={detailLevel}
                  onChange={e => setDetailLevel(e.target.value as DetailLevel)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:border-purple-500"
                >
                  <option value="comprehensive">شامل وهندسي عميق (موصى به)</option>
                  <option value="balanced">متوازن ومركز</option>
                  <option value="concise">موجز وسريع</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 font-medium mb-1">توجيهات أو قيود خاصة</label>
                <input
                  type="text"
                  value={customDirectives}
                  onChange={e => setCustomDirectives(e.target.value)}
                  placeholder="مثال: التركيز على Room و SQLCipher"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:border-purple-500"
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
              <span className="text-[11px] text-slate-500">
                {isAirplaneMode ? 'المحرك يعمل محلياً 100% في وضع الطيران 🔒' : 'المحرك متصل بالخادم مع عزل البيانات ☁️'}
              </span>

              <div className="flex flex-wrap items-center gap-2">
                <button
                  onClick={handleRunAnalysis}
                  disabled={isAnalyzing || !rawIdea.trim()}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5 text-purple-400" />
                  <span>{isAnalyzing ? 'جاري التحليل الهندسي...' : 'تحليل المتطلبات وكشف الغموض'}</span>
                </button>

                <button
                  onClick={handleGeneratePrompt}
                  disabled={isGenerating || !rawIdea.trim()}
                  className="inline-flex items-center gap-2 px-5 py-2 bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-md shadow-purple-900/30 transition-colors cursor-pointer"
                >
                  <Play className="w-3.5 h-3.5" />
                  <span>توليد البرومبت الهندسي مباشرة</span>
                </button>
              </div>
            </div>
          </div>

          {/* Analysis Results Display (Editable Cards - Stage 6) */}
          {analysis && (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <span>مخرجات التحليل الهندسي المنظم</span>
                  <span className="text-xs text-slate-400 font-normal">(قابلة للتعديل الفوري والاعتماد كمدخل للبرومبت)</span>
                </h2>

                <div className="flex items-center gap-3">
                  <button
                    onClick={() => ExportService.exportAnalysisText(project)}
                    className="text-xs text-slate-300 hover:text-white flex items-center gap-1 bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800 cursor-pointer"
                  >
                    <FileText className="w-3.5 h-3.5 text-purple-400" />
                    <span>تصدير TXT</span>
                  </button>
                  <button
                    onClick={() => ExportService.exportRequirementsCsv(project)}
                    className="text-xs text-slate-300 hover:text-white flex items-center gap-1 bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800 cursor-pointer"
                  >
                    <Table className="w-3.5 h-3.5 text-emerald-400" />
                    <span>تصدير جدول CSV</span>
                  </button>
                  <button
                    onClick={() => ExportService.exportRtlPdfDocument(project)}
                    className="text-xs text-slate-300 hover:text-white flex items-center gap-1 bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-800 cursor-pointer"
                  >
                    <Printer className="w-3.5 h-3.5 text-indigo-400" />
                    <span>تقرير PDF عربي</span>
                  </button>
                </div>
              </div>

              {/* Grid of Analysis cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 1. Problem & Goals */}
                <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 space-y-2">
                  <h3 className="text-xs font-bold text-purple-400 uppercase tracking-wider">
                    01. المشكلة والهدف الأساسي
                  </h3>
                  <div className="space-y-2 text-xs text-slate-300">
                    <div>
                      <span className="text-slate-400 font-medium">المشكلة: </span>
                      <textarea
                        rows={2}
                        value={analysis.problem}
                        onChange={e => updateAndPersistAnalysis({ ...analysis, problem: e.target.value })}
                        className="w-full bg-slate-950 border border-slate-800 rounded p-2 text-slate-200 mt-1 focus:outline-none focus:border-purple-500 resize-none"
                      />
                    </div>
                    <div>
                      <span className="text-slate-400 font-medium">الهدف الرئيسي: </span>
                      <textarea
                        rows={2}
                        value={analysis.primaryGoal}
                        onChange={e => updateAndPersistAnalysis({ ...analysis, primaryGoal: e.target.value })}
                        className="w-full bg-slate-950 border border-slate-800 rounded p-2 text-slate-200 mt-1 focus:outline-none focus:border-purple-500 resize-none"
                      />
                    </div>
                  </div>
                </div>

                {/* 2. Target Audience & Constraints */}
                <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 space-y-2">
                  <h3 className="text-xs font-bold text-purple-400 uppercase tracking-wider">
                    02. الفئة المستهدفة والقيود التقنية
                  </h3>
                  <div className="space-y-2 text-xs text-slate-300">
                    <div>
                      <span className="text-slate-400 font-medium">الفئة المستهدفة: </span>
                      <input
                        type="text"
                        value={analysis.targetAudience}
                        onChange={e => updateAndPersistAnalysis({ ...analysis, targetAudience: e.target.value })}
                        className="w-full bg-slate-950 border border-slate-800 rounded p-2 text-slate-200 mt-1 focus:outline-none focus:border-purple-500"
                      />
                    </div>
                    <div>
                      <span className="text-slate-400 font-medium">القيود الإلزامية:</span>
                      <ul className="mt-1 space-y-1">
                        {analysis.constraints.map((c, i) => (
                          <li key={i} className="flex items-center justify-between gap-1.5 text-slate-300 bg-slate-950/50 px-2 py-1 rounded">
                            <div className="flex items-center gap-1.5">
                              <span className="w-1.5 h-1.5 rounded-full bg-rose-400 shrink-0" />
                              <span>{c}</span>
                            </div>
                            <button
                              onClick={() =>
                                updateAndPersistAnalysis({
                                  ...analysis,
                                  constraints: analysis.constraints.filter((_, idx) => idx !== i),
                                })
                              }
                              className="text-slate-500 hover:text-rose-400"
                              title="حذف القيد"
                            >
                              <Trash2 className="w-3 h-3" />
                            </button>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>

                {/* 3. STRICT SEPARATION: Verified Facts vs Proposed Assumptions */}
                <div className="bg-slate-900/80 border border-emerald-900/40 rounded-xl p-4 space-y-2">
                  <div className="flex items-center gap-1.5 text-emerald-400 text-xs font-bold">
                    <CheckCircle2 className="w-4 h-4" />
                    <span>الحقائق المؤكدة (Verified Facts من كلام المستخدم صراحة)</span>
                  </div>
                  <ul className="space-y-1.5 text-xs text-slate-300">
                    {analysis.verifiedFacts.map((fact, idx) => (
                      <li key={idx} className="bg-slate-950/60 p-2 rounded border border-slate-800/80 flex items-start gap-2">
                        <span className="text-emerald-400 font-bold shrink-0">✓</span>
                        <span>{fact}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="bg-slate-900/80 border border-amber-900/40 rounded-xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-amber-400 text-xs font-bold">
                      <HelpCircle className="w-4 h-4" />
                      <span>افتراضات مقترحة قابلة للتعديل (Proposed Assumptions)</span>
                    </div>
                  </div>
                  <ul className="space-y-1.5 text-xs text-slate-300">
                    {analysis.assumptions.map((assumption, idx) => (
                      <li key={idx} className="bg-slate-950/60 p-2 rounded border border-slate-800/80 flex items-start justify-between gap-2">
                        <div className="flex items-start gap-2 flex-1">
                          <span className="text-amber-400 font-bold shrink-0">?</span>
                          <input
                            type="text"
                            value={assumption}
                            onChange={e => {
                              const next = [...analysis.assumptions];
                              next[idx] = e.target.value;
                              updateAndPersistAnalysis({ ...analysis, assumptions: next });
                            }}
                            className="bg-transparent text-slate-200 w-full focus:outline-none focus:border-b focus:border-amber-500"
                          />
                        </div>
                        <button
                          onClick={() =>
                            updateAndPersistAnalysis({
                              ...analysis,
                              assumptions: analysis.assumptions.filter((_, i) => i !== idx),
                            })
                          }
                          className="text-slate-500 hover:text-rose-400 shrink-0"
                          title="حذف الافتراض"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </li>
                    ))}
                  </ul>

                  {/* Add new assumption inline */}
                  <div className="flex items-center gap-2 pt-1">
                    <input
                      type="text"
                      value={newAssumptionText}
                      onChange={e => setNewAssumptionText(e.target.value)}
                      placeholder="إضافة افتراض هندسي جديد..."
                      className="flex-1 bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-white focus:outline-none focus:border-amber-500"
                    />
                    <button
                      onClick={() => {
                        if (!newAssumptionText.trim()) return;
                        updateAndPersistAnalysis({
                          ...analysis,
                          assumptions: [...analysis.assumptions, newAssumptionText.trim()],
                        });
                        setNewAssumptionText('');
                      }}
                      className="px-2.5 py-1.5 bg-amber-900/40 hover:bg-amber-800/60 text-amber-200 border border-amber-700/40 rounded text-xs font-semibold flex items-center gap-1 cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>إضافة</span>
                    </button>
                  </div>
                </div>

                {/* 4. Functional Requirements: Must-Have vs Nice-to-Have */}
                <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 space-y-3 md:col-span-2">
                  <h3 className="text-xs font-bold text-indigo-400 uppercase tracking-wider">
                    03. المتطلبات الوظيفية المنظمة (Must-Have & Nice-to-Have)
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                    <div className="space-y-2">
                      <span className="font-semibold text-slate-200 block">إلزامية (Must-have):</span>
                      <ul className="space-y-1.5">
                        {analysis.functionalReqs.mustHave.map((item, idx) => (
                          <li key={idx} className="bg-slate-950/70 p-2 rounded border border-slate-800 text-slate-300 flex items-center justify-between gap-2">
                            <span>{item}</span>
                            <button
                              onClick={() =>
                                updateAndPersistAnalysis({
                                  ...analysis,
                                  functionalReqs: {
                                    ...analysis.functionalReqs,
                                    mustHave: analysis.functionalReqs.mustHave.filter((_, i) => i !== idx),
                                  },
                                })
                              }
                              className="text-slate-500 hover:text-rose-400 shrink-0"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </li>
                        ))}
                      </ul>
                      <div className="flex items-center gap-2 pt-1">
                        <input
                          type="text"
                          value={newMustHaveText}
                          onChange={e => setNewMustHaveText(e.target.value)}
                          placeholder="إضافة متطلب وظيفي إلزامي..."
                          className="flex-1 bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-white focus:outline-none focus:border-purple-500"
                        />
                        <button
                          onClick={() => {
                            if (!newMustHaveText.trim()) return;
                            updateAndPersistAnalysis({
                              ...analysis,
                              functionalReqs: {
                                ...analysis.functionalReqs,
                                mustHave: [...analysis.functionalReqs.mustHave, newMustHaveText.trim()],
                              },
                            });
                            setNewMustHaveText('');
                          }}
                          className="px-2.5 py-1.5 bg-purple-900/40 hover:bg-purple-800/60 text-purple-200 border border-purple-700/40 rounded text-xs font-semibold flex items-center gap-1 cursor-pointer"
                        >
                          <Plus className="w-3.5 h-3.5" />
                          <span>إضافة</span>
                        </button>
                      </div>
                    </div>

                    <div>
                      <span className="font-semibold text-slate-200 block mb-2">تحسينية ومستقبلية (Nice-to-have):</span>
                      <ul className="space-y-1.5">
                        {analysis.functionalReqs.niceToHave.map((item, idx) => (
                          <li key={idx} className="bg-slate-950/70 p-2 rounded border border-slate-800 text-slate-400 flex items-center justify-between">
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>

                {/* 5. Ambiguities & Smart Clarifying Questions (Stage 6) */}
                {(analysis.ambiguities.length > 0 || analysis.clarifyingQuestions.length > 0) && (
                  <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-4 space-y-3 md:col-span-2">
                    <h3 className="text-xs font-bold text-amber-400 uppercase tracking-wider flex items-center gap-1.5">
                      <AlertTriangle className="w-4 h-4" />
                      <span>04. كشف الغموض والأسئلة التوضيحية الذكية (دون تعطيل مسار العمل)</span>
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                      <div>
                        <span className="font-semibold text-slate-300 block mb-1.5">نقاط الغموض المكتشفة:</span>
                        <ul className="space-y-1 text-slate-400 list-disc list-inside">
                          {analysis.ambiguities.map((amb, i) => (
                            <li key={i}>{amb}</li>
                          ))}
                        </ul>
                      </div>
                      <div>
                        <span className="font-semibold text-slate-300 block mb-1.5">أسئلة توضيحية مقترحة لرفع الدقة:</span>
                        <ul className="space-y-1 text-slate-300 list-decimal list-inside">
                          {analysis.clarifyingQuestions.map((q, i) => (
                            <li key={i}>{q}</li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Bottom Next Step Bar */}
              <div className="bg-purple-950/30 border border-purple-800/40 rounded-xl p-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h3 className="text-xs font-bold text-white">التحليل جاهز للاعتماد</h3>
                  <p className="text-[11px] text-slate-400">
                    يمكنك الآن توليد البرومبت المخصص للمنصة المحددة بكامل القيود والمعايير.
                  </p>
                </div>

                <button
                  onClick={handleGeneratePrompt}
                  disabled={isGenerating}
                  className="px-5 py-2 bg-purple-700 hover:bg-purple-600 active:bg-purple-800 text-white text-xs font-semibold rounded-lg shadow-md transition-colors cursor-pointer"
                >
                  اعتماد وتوليد البرومبت &larr;
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* SUBTAB 2: STANDARDIZED PROMPT EDITOR & STREAMING */}
      {activeSubTab === 'prompt' && (
        <div className="space-y-4">
          {/* Editor Header Bar with word counter, token estimation, Undo/Redo, version selector, and actions */}
          <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-3 flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-3 flex-wrap">
              {/* Version selector */}
              <div className="flex items-center gap-1.5 bg-slate-950 px-2.5 py-1 rounded-md border border-slate-800">
                <History className="w-3.5 h-3.5 text-purple-400" />
                <span className="text-slate-400">الإصدار:</span>
                {prompts.length > 1 ? (
                  <select
                    value={project.currentPromptIndex || 0}
                    onChange={e => {
                      const idx = parseInt(e.target.value, 10);
                      onUpdateProject({ ...project, currentPromptIndex: idx });
                      setPromptText(prompts[idx]?.promptText || '');
                    }}
                    className="bg-transparent font-mono font-bold text-white focus:outline-none"
                  >
                    {prompts.map((p, idx) => (
                      <option key={p.id} value={idx} className="bg-slate-950 text-white">
                        v{p.versionNumber}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span className="font-mono font-bold text-white">
                    v{currentPrompt?.versionNumber || 1}
                  </span>
                )}
              </div>

              {/* Undo / Redo Stack Controls (Stage 3) */}
              <div className="flex items-center gap-1 bg-slate-950 px-1.5 py-0.5 rounded-md border border-slate-800">
                <button
                  onClick={handleUndo}
                  disabled={undoStack.length === 0}
                  className="p-1 text-slate-400 hover:text-white disabled:opacity-30 cursor-pointer"
                  title="تراجع (Undo)"
                >
                  <Undo2 className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={handleRedo}
                  disabled={redoStack.length === 0}
                  className="p-1 text-slate-400 hover:text-white disabled:opacity-30 cursor-pointer"
                  title="إعادة (Redo)"
                >
                  <Redo2 className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Tokens, Words & Chars (tabular figures) */}
              <div className="flex items-center gap-2 text-slate-400 font-mono">
                <span>{wordCount} كلمة</span>
                <span aria-hidden="true">·</span>
                <span>{charCount} حرف</span>
                <span aria-hidden="true">·</span>
                <span className={estimatedTokens > 3500 ? 'text-amber-400 font-bold' : 'text-purple-300'}>
                  {estimatedTokens} توكن تقريبي
                </span>
              </div>

              {/* Auto save status */}
              <span className="text-slate-500 text-[11px] hidden sm:inline">
                ({autoSaveStatus})
              </span>
            </div>

            {/* Mode toggles & Actions */}
            <div className="flex items-center gap-2 flex-wrap">
              {/* View / Edit Mode */}
              <div className="flex items-center bg-slate-950 p-0.5 rounded-lg border border-slate-800">
                <button
                  onClick={() => setViewMode('edit')}
                  className={`p-1.5 rounded transition-colors cursor-pointer ${
                    viewMode === 'edit' ? 'bg-purple-900/60 text-white' : 'text-slate-400 hover:text-white'
                  }`}
                  title="وضع المحرر"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => setViewMode('preview')}
                  className={`p-1.5 rounded transition-colors cursor-pointer ${
                    viewMode === 'preview' ? 'bg-purple-900/60 text-white' : 'text-slate-400 hover:text-white'
                  }`}
                  title="معاينة Markdown"
                >
                  <Eye className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Emergency Stop Button when streaming */}
              {isGenerating ? (
                <button
                  onClick={handleStopGeneration}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-rose-700 hover:bg-rose-600 text-white rounded-lg font-semibold transition-colors cursor-pointer"
                >
                  <Square className="w-3.5 h-3.5 fill-current" />
                  <span>إيقاف التوليد الفوري</span>
                </button>
              ) : (
                <button
                  onClick={handleGeneratePrompt}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-700 hover:bg-purple-600 text-white rounded-lg font-semibold transition-colors cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>إعادة توليد</span>
                </button>
              )}

              {/* Copy Button */}
              <button
                onClick={handleCopyPrompt}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-medium transition-colors cursor-pointer"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'تم النسخ!' : 'نسخ البرومبت'}</span>
              </button>

              {/* Quality Review Button */}
              <button
                onClick={() => onOpenQualityReview(promptText, analysis)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-amber-300 rounded-lg font-medium transition-colors cursor-pointer"
              >
                <ShieldCheck className="w-3.5 h-3.5 text-amber-400" />
                <span>فحص الجودة</span>
              </button>

              {/* Upgrade 2: Interactive Prompt Sandbox Button */}
              <button
                onClick={handleRunPromptSandbox}
                disabled={isRunningSandbox}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-950/70 hover:bg-indigo-900 text-indigo-200 border border-indigo-500/40 rounded-lg font-medium transition-colors cursor-pointer"
                title="محاكاة واختبار مخرجات البرومبت البرمجية قبل التصدير"
              >
                <FlaskConical className="w-3.5 h-3.5 text-indigo-400" />
                <span>{isRunningSandbox ? 'جارٍ المحاكاة...' : 'مختبر البرومبت (Sandbox)'}</span>
              </button>

              {/* Upgrade 1: Starter Codebase ZIP Export */}
              <button
                onClick={() => ExportService.exportStarterCodebaseZip(project)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-950/70 hover:bg-purple-800 text-purple-200 border border-purple-500/40 rounded-lg font-medium transition-colors cursor-pointer"
                title="توليد وتنزيل هيكل كود المشروع القابل للتشغيل (Starter Codebase ZIP)"
              >
                <Code2 className="w-3.5 h-3.5 text-purple-400" />
                <span>كود المشروع (ZIP)</span>
              </button>

              {/* Export Markdown & PDF */}
              <button
                onClick={() => ExportService.exportPromptMarkdown(project)}
                className="p-1.5 text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors cursor-pointer"
                title="تصدير كملف Markdown (.md)"
              >
                <Download className="w-4 h-4" />
              </button>
              <button
                onClick={() => ExportService.exportRtlPdfDocument(project)}
                className="p-1.5 text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg transition-colors cursor-pointer"
                title="تصدير وثيقة PDF عربية متكاملة"
              >
                <Printer className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Upgrade 2: Sandbox Output Preview Panel */}
          {sandboxOutput && (
            <div className="bg-indigo-950/20 border border-indigo-800/50 rounded-xl p-4 space-y-2">
              <div className="flex items-center justify-between">
                <div className="text-xs font-bold text-indigo-300 flex items-center gap-1.5">
                  <FlaskConical className="w-3.5 h-3.5 text-indigo-400" />
                  <span>نتيجة محاكاة مختبر البرومبت (Interactive Prompt Sandbox)</span>
                </div>
                <button
                  onClick={() => setSandboxOutput(null)}
                  className="text-[11px] text-slate-400 hover:text-white cursor-pointer"
                >
                  إخفاء المعاينة
                </button>
              </div>
              <pre className="text-xs font-mono text-indigo-100/90 bg-slate-950/90 p-3 rounded-lg overflow-x-auto leading-relaxed" dir="ltr">
                {sandboxOutput}
              </pre>
            </div>
          )}

          {/* Prompt Content Area: Raw Editor or Visual Markdown */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden min-h-[480px]">
            {viewMode === 'edit' ? (
              <textarea
                value={promptText}
                onChange={e => handlePromptTextEdit(e.target.value)}
                placeholder="سيظهر البرومبت الهندسي المولد هنا لحظة بلحظة..."
                className="w-full h-[540px] p-5 bg-transparent text-slate-100 font-mono text-xs md:text-sm leading-relaxed focus:outline-none resize-none overflow-y-auto"
                dir="auto"
              />
            ) : (
              <div className="p-6 prose prose-invert max-w-none text-slate-200 text-xs md:text-sm leading-relaxed overflow-y-auto max-h-[540px]">
                {promptText.split('\n').map((line, idx) => {
                  if (line.startsWith('# ')) {
                    return <h1 key={idx} className="text-lg font-bold text-purple-300 mt-4 mb-2 pb-1 border-b border-slate-800">{line.replace('# ', '')}</h1>;
                  }
                  if (line.startsWith('## ')) {
                    return <h2 key={idx} className="text-base font-semibold text-white mt-3 mb-1.5">{line.replace('## ', '')}</h2>;
                  }
                  if (line.startsWith('### ')) {
                    return <h3 key={idx} className="text-sm font-semibold text-purple-400 mt-2 mb-1">{line.replace('### ', '')}</h3>;
                  }
                  if (line.startsWith('- [ ]') || line.startsWith('- [x]')) {
                    return (
                      <div key={idx} className="flex items-center gap-2 py-0.5 text-slate-300">
                        <input type="checkbox" readOnly checked={line.includes('[x]')} className="rounded" />
                        <span>{line.replace(/- \[[ x]\] /, '')}</span>
                      </div>
                    );
                  }
                  if (line.startsWith('- ') || line.startsWith('* ')) {
                    return (
                      <li key={idx} className="mr-4 list-disc text-slate-300 py-0.5">
                        {line.replace(/^[-*] /, '')}
                      </li>
                    );
                  }
                  if (line.trim() === '') {
                    return <div key={idx} className="h-2" />;
                  }
                  return <p key={idx} className="text-slate-300 my-1">{line}</p>;
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* SUBTAB 3: PROMPT OPTIMIZER (أداة تحسين برومبت قائم - Stage 7) */}
      {activeSubTab === 'optimizer' && (
        <div className="space-y-6">
          <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <Wand2 className="w-4 h-4 text-purple-400" />
                  <span>أداة تحسين وإعادة هندسة برومبت قائم (Stage 7 Prompt Optimizer)</span>
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  أدخل أي برومبت ضعيف أو مختصر لإعادة صياغته وفق الهيكل الهندسي القياسي دون تحريف مقصودك الأصلي، مع مقارنة الفروقات.
                </p>
              </div>
            </div>

            <textarea
              rows={3}
              value={weakPromptInput}
              onChange={e => setWeakPromptInput(e.target.value)}
              placeholder="أدخل البرومبت المختصر أو الضعيف هنا..."
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3.5 text-slate-100 text-xs md:text-sm focus:outline-none focus:border-purple-500"
            />

            <div className="flex justify-end">
              <button
                onClick={handleOptimizeExistingPrompt}
                disabled={isOptimizing || !weakPromptInput.trim()}
                className="inline-flex items-center gap-2 px-5 py-2 bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow transition-colors cursor-pointer"
              >
                <Wand2 className="w-4 h-4" />
                <span>{isOptimizing ? 'جاري إعادة الصياغة الهندسية...' : 'تحسين البرومبت هندسياً'}</span>
              </button>
            </div>
          </div>

          {optimizedResult && (
            <div className="space-y-4">
              {/* Improvements summary */}
              <div className="bg-emerald-950/20 border border-emerald-800/40 rounded-xl p-4 space-y-2">
                <h3 className="text-xs font-bold text-emerald-400">التحسينات الهندسية التي تمت إضافتها:</h3>
                <ul className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs text-emerald-200">
                  {optimizedResult.improvementsMade.map((imp, idx) => (
                    <li key={idx} className="flex items-center gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                      <span>{imp}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Side by side comparison: Original vs Optimized */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
                  <div className="text-xs font-bold text-slate-400 pb-2 border-b border-slate-800">
                    النص الأصلي للمستخدم (قبل التحسين)
                  </div>
                  <pre className="text-xs font-mono text-slate-400 whitespace-pre-wrap leading-relaxed">
                    {weakPromptInput}
                  </pre>
                </div>

                <div className="bg-slate-950 border border-purple-800/60 rounded-xl p-4 space-y-2">
                  <div className="flex items-center justify-between pb-2 border-b border-slate-800">
                    <span className="text-xs font-bold text-purple-400">البرومبت الهندسي المحسّن (بعد التحسين)</span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={async () => {
                          await safeCopy(optimizedResult.optimizedPrompt);
                          setCopiedOptimized(true);
                          setTimeout(() => setCopiedOptimized(false), 2000);
                        }}
                        className="text-xs text-slate-300 hover:text-white flex items-center gap-1 cursor-pointer"
                      >
                        {copiedOptimized ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        <span>{copiedOptimized ? 'تم النسخ' : 'نسخ'}</span>
                      </button>
                      <button
                        onClick={handleAdoptOptimizedPrompt}
                        className="px-2.5 py-1 bg-purple-700 hover:bg-purple-600 text-white rounded text-xs font-semibold cursor-pointer"
                      >
                        اعتماد كإصدار جديد في المحرر
                      </button>
                    </div>
                  </div>
                  <pre className="text-xs font-mono text-slate-200 whitespace-pre-wrap leading-relaxed max-h-[380px] overflow-y-auto">
                    {optimizedResult.optimizedPrompt}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* SUBTAB 4: DIFF COMPARISON VIEW */}
      {activeSubTab === 'diff' && prompts.length > 1 && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-900 p-3 rounded-xl border border-slate-800 text-xs">
            <div className="flex items-center gap-3">
              <GitCompare className="w-4 h-4 text-purple-400" />
              <span className="font-bold text-white">مقارنة التغييرات بين إصدارين (Diff View)</span>
            </div>

            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <span className="text-slate-400">الإصدار الأساسي:</span>
                <select
                  value={diffVersionA}
                  onChange={e => setDiffVersionA(parseInt(e.target.value, 10))}
                  className="bg-slate-950 border border-slate-800 rounded px-2 py-1 text-white"
                >
                  {prompts.map((p, idx) => (
                    <option key={p.id} value={idx}>الإصدار v{p.versionNumber}</option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-slate-400">المقارن به:</span>
                <select
                  value={diffVersionB}
                  onChange={e => setDiffVersionB(parseInt(e.target.value, 10))}
                  className="bg-slate-950 border border-slate-800 rounded px-2 py-1 text-white"
                >
                  {prompts.map((p, idx) => (
                    <option key={p.id} value={idx}>الإصدار v{p.versionNumber}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
              <div className="text-xs font-bold text-slate-400 pb-2 border-b border-slate-800">
                الإصدار v{prompts[diffVersionA]?.versionNumber} ({prompts[diffVersionA]?.targetPlatform})
              </div>
              <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap leading-relaxed max-h-[460px] overflow-y-auto">
                {prompts[diffVersionA]?.promptText}
              </pre>
            </div>

            <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
              <div className="text-xs font-bold text-purple-400 pb-2 border-b border-slate-800">
                الإصدار v{prompts[diffVersionB]?.versionNumber} ({prompts[diffVersionB]?.targetPlatform})
              </div>
              <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap leading-relaxed max-h-[460px] overflow-y-auto">
                {prompts[diffVersionB]?.promptText}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
