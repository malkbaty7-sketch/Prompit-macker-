import React, { useState } from 'react';
import { Search, ExternalLink, ShieldCheck, Cloud, Lock, BookOpen, Copy, Check, Sparkles, AlertCircle } from 'lucide-react';
import { safeCopy } from '../services/safeStorage';

interface ResearchTopic {
  title: string;
  docUrl: string;
  summary: string;
  codeSnippet?: string;
  recommendation?: string;
}

interface SearchResponse {
  isOfflineMode: boolean;
  source: string;
  query: string;
  summary: string;
  topics: ResearchTopic[];
  citations: string[];
}

interface ResearchCloudViewProps {
  isAirplaneMode: boolean;
}

export const ResearchCloudView: React.FC<ResearchCloudViewProps> = ({ isAirplaneMode }) => {
  const [query, setQuery] = useState('');
  const [selectedTopic, setSelectedTopic] = useState<'all' | 'architecture' | 'ai_engine' | 'storage' | 'compose'>('all');
  const [allowCloudConsent, setAllowCloudConsent] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Preset search queries for native Android development
  const presets = [
    { label: 'إدارة الذاكرة مع llama.cpp في C++', q: 'إدارة ذاكرة llama.cpp في C++ JNI ومنع OutOfMemory على أندرويد' },
    { label: 'تنزيل النماذج عبر WorkManager', q: 'استخدام WorkManager لتنزيل الملفات الكبيرة في الخلفية مع استئناف التحميل' },
    { label: 'دعم RTL ونصوص Bidi في Compose', q: 'دعم اتجاه اليمين لليسار RTL والنصوص البرمجية المختلطة في Jetpack Compose' },
    { label: 'ترحيل Room Database التلقائي', q: 'Room AutoMigration وتصدير المخطط exportSchema في Kotlin' },
    { label: 'خدمة ForegroundService للتوليد', q: 'تشغيل عمليات الاستدلال الطويلة في ForegroundService مع شريط إشعار' },
  ];

  const handleSearch = async (searchQuery: string = query) => {
    if (!searchQuery.trim()) return;
    setIsLoading(true);
    setErrorMessage(null);

    const useCloud = allowCloudConsent && !isAirplaneMode;

    try {
      const res = await fetch('/api/technical-search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: searchQuery.trim(),
          topic: selectedTopic,
          allowCloud: useCloud,
        }),
      });

      if (!res.ok) {
        throw new Error(`خطأ في استجابة الخادم: ${res.status}`);
      }

      const data: SearchResponse = await res.json();
      setResults(data);
    } catch (err: any) {
      console.error('Search error:', err);
      setErrorMessage(err?.message || 'تعذر إتمام البحث. يرجى المحاولة لاحقاً.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopyCode = async (snippet: string, index: number) => {
    await safeCopy(snippet);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      {/* 1. Header & Vision Banner */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 backdrop-blur-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <BookOpen className="w-5 h-5 text-purple-400" />
              <h1 className="text-xl font-bold text-white">البحث التقني والتوثيق المرجعي (المرحلة 11)</h1>
            </div>
            <p className="text-xs text-slate-400">
              استعلام التوثيق الرسمي لـ Android Jetpack، وKotlin، وRoom، وllama.cpp مع التمييز الصارم بين الحقائق والاقتراحات.
            </p>
          </div>

          {/* Cloud Consent Gate (بوابة الموافقة الصريحة) */}
          <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 flex items-center justify-between gap-4 shrink-0">
            <div className="flex items-center gap-2.5">
              {allowCloudConsent && !isAirplaneMode ? (
                <div className="w-8 h-8 rounded-md bg-blue-950/60 border border-blue-700/40 flex items-center justify-center text-blue-400">
                  <Cloud className="w-4 h-4" />
                </div>
              ) : (
                <div className="w-8 h-8 rounded-md bg-emerald-950/60 border border-emerald-700/40 flex items-center justify-center text-emerald-400">
                  <Lock className="w-4 h-4" />
                </div>
              )}
              <div>
                <div className="text-xs font-semibold text-slate-200">
                  {allowCloudConsent && !isAirplaneMode ? 'الوضع السحابي الموثق ☁️' : 'الوضع المحلي المعزول 🔒'}
                </div>
                <div className="text-[11px] text-slate-400">
                  {isAirplaneMode
                    ? 'وضع الطيران نشط · محلي 100%'
                    : allowCloudConsent
                    ? 'بحث مباشر في المستندات'
                    : 'لا تخرج أي بيانات للإنترنت'}
                </div>
              </div>
            </div>

            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={allowCloudConsent && !isAirplaneMode}
                disabled={isAirplaneMode}
                onChange={e => setAllowCloudConsent(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:right-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-purple-600 peer-disabled:opacity-50"></div>
            </label>
          </div>
        </div>

        {/* Search Input Bar */}
        <div className="mt-6 flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute right-3.5 top-3.5 w-4 h-4 text-slate-500" />
            <input
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              placeholder="اكتب استعلام البحث التقني (مثال: إدارة ذاكرة JNI في llama.cpp أو Room AutoMigration)..."
              className="w-full pl-4 pr-10 py-3 bg-slate-950 border border-slate-800 rounded-lg text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500"
            />
          </div>
          <button
            onClick={() => handleSearch()}
            disabled={isLoading || !query.trim()}
            className="px-6 py-3 bg-purple-700 hover:bg-purple-600 active:bg-purple-800 disabled:opacity-50 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer shrink-0"
          >
            {isLoading ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                <span>جاري البحث...</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4" />
                <span>بحث تقني</span>
              </>
            )}
          </button>
        </div>

        {/* Quick Presets */}
        <div className="mt-4 flex items-center gap-2 overflow-x-auto pb-1 text-xs text-slate-400">
          <span className="shrink-0 text-slate-500 font-medium">اقتراحات شائعة:</span>
          {presets.map((preset, idx) => (
            <button
              key={idx}
              onClick={() => {
                setQuery(preset.q);
                handleSearch(preset.q);
              }}
              className="px-2.5 py-1 rounded bg-slate-800/80 hover:bg-purple-900/40 hover:text-purple-300 border border-slate-700/60 whitespace-nowrap transition-colors"
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {/* Error message banner */}
      {errorMessage && (
        <div className="p-4 bg-red-950/40 border border-red-800/60 rounded-xl text-xs text-red-300 flex items-center gap-3">
          <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Results Section */}
      {results && (
        <div className="space-y-6">
          {/* Metadata Card */}
          <div className="bg-slate-900/40 border border-slate-800 rounded-xl p-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-slate-400 mb-3 border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2 text-slate-300">
                <ShieldCheck className="w-4 h-4 text-purple-400" />
                <span className="font-semibold text-white">المصدر:</span>
                <span>{results.source}</span>
              </div>
              <div className="text-slate-400">
                {results.isOfflineMode ? 'استعلام محلي آمن 100%' : 'استعلام موثق عبر السحابة'}
              </div>
            </div>
            <p className="text-sm text-slate-300 leading-relaxed">{results.summary}</p>
          </div>

          {/* Topics Grid */}
          <div className="grid grid-cols-1 gap-5">
            {results.topics.map((topic, idx) => (
              <div
                key={idx}
                className="bg-slate-900/60 border border-slate-800 hover:border-slate-700/80 rounded-xl p-5 space-y-3 transition-colors"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-purple-500"></span>
                    {topic.title}
                  </h3>
                  {topic.docUrl && (
                    <a
                      href={topic.docUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-purple-400 hover:text-purple-300 transition-colors"
                    >
                      <span>التوثيق الرسمي</span>
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  )}
                </div>

                <p className="text-xs text-slate-300 leading-relaxed">{topic.summary}</p>

                {topic.recommendation && (
                  <div className="p-2.5 bg-purple-950/30 border border-purple-800/40 rounded-lg text-xs text-purple-200">
                    <span className="font-semibold text-purple-300">توصية معتمدة: </span>
                    {topic.recommendation}
                  </div>
                )}

                {topic.codeSnippet && (
                  <div className="relative mt-2">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-slate-950 border border-slate-800 rounded-t-lg text-[11px] text-slate-400">
                      <span className="font-mono">Kotlin / C++ Snippet</span>
                      <button
                        onClick={() => handleCopyCode(topic.codeSnippet!, idx)}
                        className="inline-flex items-center gap-1 text-slate-300 hover:text-white transition-colors"
                      >
                        {copiedIndex === idx ? (
                          <>
                            <Check className="w-3.5 h-3.5 text-emerald-400" />
                            <span className="text-emerald-400">تم النسخ</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>نسخ الكود</span>
                          </>
                        )}
                      </button>
                    </div>
                    <pre className="p-3 bg-slate-950 border-x border-b border-slate-800 rounded-b-lg text-xs font-mono text-slate-200 overflow-x-auto text-left dir-ltr">
                      <code>{topic.codeSnippet}</code>
                    </pre>
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Citations List */}
          {results.citations && results.citations.length > 0 && (
            <div className="bg-slate-900/30 border border-slate-800 rounded-xl p-4 text-xs text-slate-400">
              <div className="font-semibold text-slate-300 mb-2">المراجع والتوثيقات الرسمية المعنية:</div>
              <ul className="list-disc list-inside space-y-1 text-slate-400">
                {results.citations.map((cite, i) => (
                  <li key={i}>{cite}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Initial Empty State */}
      {!results && !isLoading && (
        <div className="p-12 text-center border border-dashed border-slate-800 rounded-xl bg-slate-900/20">
          <BookOpen className="w-10 h-10 text-slate-600 mx-auto mb-3" />
          <h3 className="text-sm font-semibold text-slate-300 mb-1">ابحث في معايير وتوثيق أندرويد وClean Architecture</h3>
          <p className="text-xs text-slate-500 max-w-md mx-auto">
            اختر أحد الاقتراحات أعلاه أو اكتب استعلامك للبحث محلياً دون أي انترنت، أو فعّل بوابة السحابة للاستعلام اللحظي.
          </p>
        </div>
      )}
    </div>
  );
};
