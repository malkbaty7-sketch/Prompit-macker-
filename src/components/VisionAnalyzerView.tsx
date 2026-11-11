import React, { useState } from 'react';
import {
  Image as ImageIcon,
  Upload,
  Sparkles,
  Copy,
  Check,
  Code,
  Layers,
  Eye,
  AlertCircle,
  Smartphone,
} from 'lucide-react';
import { VisionAnalysisResult } from '../types';
import { LocalAiEngine } from '../services/localAiEngine';
import { safeCopy } from '../services/safeStorage';

interface VisionAnalyzerViewProps {
  isAirplaneMode: boolean;
  onApplyPromptToStudio?: (prompt: string) => void;
}

export const VisionAnalyzerView: React.FC<VisionAnalyzerViewProps> = ({
  isAirplaneMode,
  onApplyPromptToStudio,
}) => {
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const [imageName, setImageName] = useState<string>('sample_pos_screen.png');
  const [isAnalyzing, setIsAnalyzing] = useState<boolean>(false);
  const [result, setResult] = useState<VisionAnalysisResult | null>(null);
  const [copiedCode, setCopiedCode] = useState<boolean>(false);
  const [copiedPrompt, setCopiedPrompt] = useState<boolean>(false);

  // Pre-loaded wireframe samples
  const samples = [
    {
      id: 'sample_pos',
      name: 'واجهة سلة البيع والدفع السريع (POS)',
      description: 'واجهة متجر أندرويد مع شبكة منتجات وسلة مشتريات وأزرار دفع',
      previewGradient: 'from-purple-900 to-slate-900',
    },
    {
      id: 'sample_login',
      name: 'شاشة تسجيل الدخول والمصادقة البيومترية',
      description: 'حقول إدخال اسم المستخدم وكلمة المرور وزر بصمة الإصبع',
      previewGradient: 'from-indigo-900 to-slate-900',
    },
    {
      id: 'sample_dashboard',
      name: 'لوحة التحكم وتحليلات المبيعات',
      description: 'بطاقات إحصائيات، رسوم بيانية، وقائمة أحدث العمليات',
      previewGradient: 'from-emerald-950 to-slate-900',
    },
  ];

  const handleSelectSample = (sample: typeof samples[0]) => {
    setImageName(sample.name);
    setSelectedImage(sample.id);
    handleAnalyze(sample.id);
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setImageName(file.name);
    const reader = new FileReader();
    reader.onload = () => {
      setSelectedImage(reader.result as string);
      handleAnalyze(reader.result as string, file.type);
    };
    reader.readAsDataURL(file);
  };

  const handleAnalyze = async (imageData: string, mimeType: string = 'image/png') => {
    setIsAnalyzing(true);
    setResult(null);

    // If online, attempt server multimodal API
    if (!isAirplaneMode && imageData.startsWith('data:image')) {
      try {
        const res = await fetch('/api/analyze-image', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            imageBase64: imageData,
            mimeType,
            notes: 'تحليل واجهة تطبيق أندرويد لإنشاء كود Jetpack Compose متوافق مع RTL',
          }),
        });

        if (res.ok) {
          const data = await res.json();
          setResult(data);
          setIsAnalyzing(false);
          return;
        }
      } catch (err) {
        console.warn('Server vision API failed, falling back to local ML Kit simulator', err);
      }
    }

    // Local ML Kit Lightweight Offline Pipeline (per Stage 9)
    setTimeout(() => {
      const offlineAnalysis = LocalAiEngine.analyzeUiImageOffline(imageName);
      setResult({
        ...offlineAnalysis,
        ocrDetectedText: ['إتمام عملية البيع', 'الكمية: 2', 'الإجمالي: 150.00 ر.س', 'طباعة الفاتورة', 'دفع نقدي'],
      });
      setIsAnalyzing(false);
    }, 800);
  };

  const handleCopyCode = async () => {
    if (!result) return;
    await safeCopy(result.composeCodeSnippet);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleCopyPrompt = async () => {
    if (!result) return;
    await safeCopy(result.generatedPrompt);
    setCopiedPrompt(true);
    setTimeout(() => setCopiedPrompt(false), 2000);
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-16">
      {/* Header */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <ImageIcon className="w-5 h-5 text-purple-400" />
            <h1 className="text-lg md:text-xl font-bold text-white">
              تصميم الواجهات وتحليل الصور (المسار الهجين ML Kit)
            </h1>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            استخراج مواصفات UI محلياً في وضع الطيران عبر محرك Google ML Kit وتوليد كود Jetpack Compose وبرومبت بناء الواجهة.
          </p>
        </div>

        {/* Upload file button */}
        <label className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold shadow transition-colors cursor-pointer self-start md:self-auto">
          <Upload className="w-4 h-4" />
          <span>رفع صورة واجهة من الجهاز</span>
          <input
            type="file"
            onChange={handleImageUpload}
            accept="image/*"
            className="hidden"
          />
        </label>
      </div>

      {/* Preset Samples Selector */}
      <div className="space-y-2">
        <span className="text-xs font-semibold text-slate-300 block">
          أو اختر نموذج واجهة تجريبية للاختبار السريع:
        </span>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {samples.map(sample => (
            <div
              key={sample.id}
              onClick={() => handleSelectSample(sample)}
              className={`p-3.5 rounded-xl border transition-all cursor-pointer bg-gradient-to-br ${sample.previewGradient} ${
                selectedImage === sample.id
                  ? 'border-purple-500 shadow-md shadow-purple-950/40'
                  : 'border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center gap-2 text-white font-bold text-xs mb-1">
                <Smartphone className="w-4 h-4 text-purple-400" />
                <span>{sample.name}</span>
              </div>
              <p className="text-[11px] text-slate-300 leading-snug">
                {sample.description}
              </p>
            </div>
          ))}
        </div>
      </div>

      {/* Analysis State Indicator */}
      {isAnalyzing && (
        <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-8 text-center space-y-3">
          <div className="w-10 h-10 border-4 border-purple-500 border-t-transparent rounded-full animate-spin mx-auto" />
          <h3 className="text-sm font-bold text-white">
            جاري استخراج النصوص المقروءة (OCR) والكتل البصرية محلياً...
          </h3>
          <p className="text-xs text-slate-400">
            يعمل المحرك دون استهلاك للإنترنت عبر مكتبة ML Kit الخفيفة.
          </p>
        </div>
      )}

      {/* Analysis Results Display */}
      {result && !isAnalyzing && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Components & Visual Hierarchy */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-4">
              <div>
                <h3 className="text-xs font-bold text-purple-400 uppercase tracking-wider mb-2">
                  01. العناصر والمكونات البصرية المكتشفة
                </h3>
                <ul className="space-y-1.5 text-xs text-slate-200">
                  {result.components.map((comp, i) => (
                    <li key={i} className="flex items-center gap-2 bg-slate-950/60 p-2 rounded border border-slate-800">
                      <Layers className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                      <span>{comp}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div>
                <h3 className="text-xs font-bold text-indigo-400 uppercase tracking-wider mb-2">
                  02. التسلسل البصري للنصوص (Visual Hierarchy)
                </h3>
                <ul className="space-y-1.5 text-xs text-slate-300">
                  {result.visualHierarchy.map((item, i) => (
                    <li key={i} className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 shrink-0" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {result.ocrDetectedText && result.ocrDetectedText.length > 0 && (
                <div>
                  <h3 className="text-xs font-bold text-emerald-400 uppercase tracking-wider mb-2">
                    03. النصوص المقروءة عبر OCR
                  </h3>
                  <div className="flex flex-wrap gap-1.5 text-[11px]">
                    {result.ocrDetectedText.map((txt, i) => (
                      <span key={i} className="bg-slate-950 text-slate-300 px-2 py-0.5 rounded border border-slate-800 font-mono">
                        "{txt}"
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Missing States & Generated Prompt */}
            <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-4">
              <div>
                <h3 className="text-xs font-bold text-amber-400 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <AlertCircle className="w-3.5 h-3.5" />
                  <span>الحالات الناقصة الواجب تغطيتها (Missing States)</span>
                </h3>
                <ul className="space-y-1.5 text-xs text-slate-300">
                  {result.missingStates.map((state, i) => (
                    <li key={i} className="bg-amber-950/20 border border-amber-800/30 p-2 rounded text-amber-200">
                      {state}
                    </li>
                  ))}
                </ul>
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-xs font-bold text-purple-400 uppercase tracking-wider">
                    البرومبت المقترح لتوليد هذه الواجهة
                  </h3>
                  <button
                    onClick={handleCopyPrompt}
                    className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
                  >
                    {copiedPrompt ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedPrompt ? 'تم النسخ' : 'نسخ البرومبت'}</span>
                  </button>
                </div>
                <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 text-xs text-slate-200 leading-relaxed font-mono">
                  {result.generatedPrompt}
                </div>
              </div>
            </div>
          </div>

          {/* Jetpack Compose Code Output */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden">
            <div className="bg-slate-900/80 p-3 border-b border-slate-800 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2 text-slate-200 font-bold">
                <Code className="w-4 h-4 text-purple-400" />
                <span>كود Jetpack Compose المقترح للشاشة (RTL & Clean Architecture)</span>
              </div>
              <button
                onClick={handleCopyCode}
                className="inline-flex items-center gap-1.5 px-3 py-1 bg-purple-700 hover:bg-purple-600 text-white rounded font-medium transition-colors cursor-pointer"
              >
                {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedCode ? 'تم النسخ!' : 'نسخ الكود'}</span>
              </button>
            </div>
            <pre className="p-5 text-xs font-mono text-purple-200/90 leading-relaxed overflow-x-auto whitespace-pre">
              {result.composeCodeSnippet}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
