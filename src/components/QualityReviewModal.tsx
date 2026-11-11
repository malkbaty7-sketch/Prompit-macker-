import React, { useState, useEffect } from 'react';
import {
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Info,
  X,
  Sparkles,
  Wand2,
  Check,
} from 'lucide-react';
import { QualityReport, QualityIssue, IdeaAnalysis } from '../types';
import { LocalAiEngine } from '../services/localAiEngine';

interface QualityReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  promptText: string;
  analysis?: IdeaAnalysis;
  onApplyFix: (fixText: string) => void;
  isAirplaneMode: boolean;
}

export const QualityReviewModal: React.FC<QualityReviewModalProps> = ({
  isOpen,
  onClose,
  promptText,
  analysis,
  onApplyFix,
  isAirplaneMode,
}) => {
  const [report, setReport] = useState<QualityReport | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [appliedFixes, setAppliedFixes] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (isOpen && promptText) {
      runAudit();
    }
  }, [isOpen, promptText]);

  const runAudit = async () => {
    setLoading(true);
    setAppliedFixes({});

    // If online, attempt server quality endpoint
    if (!isAirplaneMode) {
      try {
        const res = await fetch('/api/quality-review', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            promptText,
            analysisData: analysis,
          }),
        });
        if (res.ok) {
          const data = await res.json();
          const normalizedIssues: QualityIssue[] = Array.isArray(data.issues)
            ? data.issues.map((iss: any, i: number) => ({
                id: iss.id || `srv_issue_${i}`,
                severity: iss.severity || 'medium',
                category: iss.category || 'اكتمال المتطلبات',
                title: iss.title || 'ملاحظة هندسية',
                reason: iss.reason || '',
                suggestedFix: iss.suggestedFix || '',
              }))
            : [];
          setReport({
            overallAssessment: data.overallAssessment || 'تم إتمام المراجعة الهندسية.',
            score: typeof data.score === 'number' ? data.score : 90,
            issues: normalizedIssues,
            strengths: Array.isArray(data.strengths) ? data.strengths : [],
          });
          setLoading(false);
          return;
        }
      } catch (e) {
        console.warn('Server quality review failed, falling back to local-first rules', e);
      }
    }

    // Local deterministic rule-based auditor
    const localReport = LocalAiEngine.runQualityReviewOffline(promptText, analysis);
    setReport(localReport);
    setLoading(false);
  };

  const handleApplySingleFix = (issue: QualityIssue) => {
    onApplyFix(issue.suggestedFix);
    setAppliedFixes(prev => ({ ...prev, [issue.id]: true }));
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-xs p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-3xl w-full max-h-[85vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
          <div className="flex items-center gap-2.5">
            <ShieldAlert className="w-5 h-5 text-purple-400" />
            <div>
              <h2 className="text-base font-bold text-white">
                مراجعة الجودة والتحقق الهندسي للبرومبت
              </h2>
              <p className="text-xs text-slate-400">
                فحص آلي حتمي يكشف الغموض، التعارضات المنطقية، والنواقص قبل بدء البرمجة
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 text-xs flex-1">
          {loading ? (
            <div className="py-16 text-center space-y-3">
              <div className="w-10 h-10 border-4 border-purple-500 border-t-transparent rounded-full animate-spin mx-auto" />
              <p className="text-slate-300 font-semibold">جاري التدقيق الهندسي وفحص التوافق...</p>
            </div>
          ) : report ? (
            <>
              {/* Score & Assessment Card */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="space-y-1 text-right">
                  <span className="text-slate-400 text-[11px] block">التقييم الهندسي الإجمالي</span>
                  <p className="text-slate-200 leading-relaxed font-medium">
                    {report.overallAssessment}
                  </p>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <div className="text-center px-4 py-2 bg-slate-900 rounded-xl border border-slate-800">
                    <div className="text-2xl font-black text-purple-400 font-mono tabular-nums">
                      {report.score}/100
                    </div>
                    <div className="text-[10px] text-slate-400">مؤشر الجودة</div>
                  </div>
                </div>
              </div>

              {/* Strengths */}
              {report.strengths.length > 0 && (
                <div className="space-y-2">
                  <span className="font-bold text-emerald-400 block">نقاط القوة المعتمدة:</span>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {report.strengths.map((str, idx) => (
                      <div key={idx} className="bg-emerald-950/20 border border-emerald-900/30 p-2.5 rounded-lg text-emerald-200 flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                        <span>{str}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Issues List */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-white text-sm">
                    الملاحظات الهندسية وفرص التحسين ({report.issues.length})
                  </span>
                  <span className="text-[11px] text-slate-500">
                    كل ملاحظة مدعومة بالسبب المنطقي ونص الإصلاح
                  </span>
                </div>

                {report.issues.length === 0 ? (
                  <div className="p-8 text-center bg-slate-950/60 rounded-xl border border-slate-800 text-slate-400">
                    <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto mb-2" />
                    <p className="font-semibold text-slate-200">البرومبت متكامل هندسياً ومستوفٍ لكافة المعايير!</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {report.issues.map(issue => {
                      const isApplied = appliedFixes[issue.id];
                      return (
                        <div
                          key={issue.id}
                          className="bg-slate-950/90 border border-slate-800 rounded-xl p-4 space-y-3"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex items-start gap-2.5">
                              {issue.severity === 'high' ? (
                                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                              ) : (
                                <Info className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                              )}
                              <div>
                                <h3 className="font-bold text-white text-xs">
                                  {issue.title}
                                </h3>
                                <span className="text-[10px] text-slate-400">
                                  التصنيف: {issue.category} · الأولوية: {issue.severity === 'high' ? 'حرجة' : 'متوسطة'}
                                </span>
                              </div>
                            </div>

                            <button
                              onClick={() => handleApplySingleFix(issue)}
                              disabled={isApplied}
                              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold transition-colors cursor-pointer shrink-0 ${
                                isApplied
                                  ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-800/60 cursor-default'
                                  : 'bg-purple-700 hover:bg-purple-600 text-white shadow'
                              }`}
                            >
                              {isApplied ? (
                                <>
                                  <Check className="w-3.5 h-3.5" />
                                  <span>تم التطبيق</span>
                                </>
                              ) : (
                                <>
                                  <Wand2 className="w-3.5 h-3.5" />
                                  <span>تطبيق الإصلاح المقترح</span>
                                </>
                              )}
                            </button>
                          </div>

                          <div className="bg-slate-900/60 p-2.5 rounded-lg border border-slate-800/80 text-slate-300">
                            <span className="font-semibold text-slate-400 block mb-0.5">السبب المنطقي:</span>
                            <p>{issue.reason}</p>
                          </div>

                          <div className="bg-purple-950/20 p-2.5 rounded-lg border border-purple-900/30 font-mono text-[11px] text-purple-200">
                            <span className="font-semibold text-purple-300 block mb-0.5">النص المضاف:</span>
                            <pre className="whitespace-pre-wrap">{issue.suggestedFix}</pre>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          ) : null}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-slate-950 flex items-center justify-between text-xs">
          <span className="text-slate-500">
            فحص الجودة يراجع شروط الأمان والصياغة ولا يضمن خلو الأدوات الخارجية من العيوب.
          </span>
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-lg transition-colors cursor-pointer"
          >
            إغلاق التقرير
          </button>
        </div>
      </div>
    </div>
  );
};
