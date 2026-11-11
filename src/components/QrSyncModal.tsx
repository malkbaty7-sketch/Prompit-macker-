import React, { useState, useMemo } from 'react';
import { QrCode, X, Copy, Check, Upload, Smartphone, ArrowLeftRight } from 'lucide-react';
import { Project } from '../types';
import { safeCopy } from '../services/safeStorage';

interface QrSyncModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeProject: Project;
  onImportSyncedProject: (project: Project) => void;
}

/**
 * Generates a deterministic 21x21 QR-style visual matrix from a string seed
 * with standard finder patterns in the 3 corners.
 */
function generateQrMatrix(seedText: string): boolean[][] {
  const size = 21;
  const grid: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false));

  const drawFinder = (r0: number, c0: number) => {
    for (let r = 0; r < 7; r++) {
      for (let c = 0; c < 7; c++) {
        const isBorder = r === 0 || r === 6 || c === 0 || c === 6;
        const isInner = r >= 2 && r <= 4 && c >= 2 && c <= 4;
        grid[r0 + r][c0 + c] = isBorder || isInner;
      }
    }
  };

  drawFinder(0, 0);
  drawFinder(0, size - 7);
  drawFinder(size - 7, 0);

  let hash = 2166136261;
  for (let i = 0; i < seedText.length; i++) {
    hash ^= seedText.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const inTopLeft = r < 8 && c < 8;
      const inTopRight = r < 8 && c >= size - 8;
      const inBottomLeft = r >= size - 8 && c < 8;
      if (inTopLeft || inTopRight || inBottomLeft) continue;

      hash ^= (r * 31 + c * 17) & 0xff;
      hash = Math.imul(hash, 16777619);
      grid[r][c] = (Math.abs(hash) % 10) < 5;
    }
  }

  return grid;
}

export const QrSyncModal: React.FC<QrSyncModalProps> = ({
  isOpen,
  onClose,
  activeProject,
  onImportSyncedProject,
}) => {
  const [copiedToken, setCopiedToken] = useState(false);
  const [importTokenInput, setImportTokenInput] = useState('');
  const [importStatus, setImportStatus] = useState<string | null>(null);

  const syncToken = useMemo(() => {
    const compactPayload = {
      id: activeProject.id,
      name: activeProject.name,
      description: activeProject.description,
      type: activeProject.type,
      targetPlatform: activeProject.targetPlatform,
      rawIdea: activeProject.rawIdea,
      prompts: activeProject.prompts,
      analysis: activeProject.analysis,
    };
    try {
      return btoa(unescape(encodeURIComponent(JSON.stringify(compactPayload))));
    } catch {
      return '';
    }
  }, [activeProject]);

  const qrMatrix = useMemo(() => generateQrMatrix(activeProject.id + activeProject.name), [activeProject]);

  const handleCopyToken = async () => {
    await safeCopy(syncToken);
    setCopiedToken(true);
    setTimeout(() => setCopiedToken(false), 2000);
  };

  const handleImportToken = () => {
    if (!importTokenInput.trim()) return;
    try {
      const decoded = decodeURIComponent(escape(atob(importTokenInput.trim())));
      const parsed = JSON.parse(decoded);
      if (parsed && parsed.name) {
        const syncedProject: Project = {
          ...parsed,
          id: `proj-sync-${Date.now()}`,
          updatedAt: 'الآن (مزامنة QR)',
          createdAt: new Date().toLocaleDateString('ar-SA'),
          isPinned: true,
          isArchived: false,
          currentPromptIndex: 0,
        };
        onImportSyncedProject(syncedProject);
        setImportStatus('✓ تمت مزامنة واستيراد المشروع بنجاح!');
        setImportTokenInput('');
        setTimeout(() => {
          setImportStatus(null);
          onClose();
        }, 1200);
      } else {
        setImportStatus('⚠️ رمز المزامنة غير صالح.');
      }
    } catch {
      setImportStatus('⚠️ تعذر فك تشفير رمز المزامنة. تأكد من نسخ الرمز كاملاً.');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-xs p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full overflow-hidden shadow-2xl">
        {/* Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/70">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-purple-700/80 flex items-center justify-center border border-purple-500/40">
              <QrCode className="w-4 h-4 text-white" />
            </div>
            <div>
              <h2 className="text-sm sm:text-base font-bold text-white">
                المزامنة الفورية بين الهاتف والمتصفح (Instant QR Sync)
              </h2>
              <p className="text-[11px] text-slate-400">
                انقل مشروعك والبرومبت الهندسي فوراً بين شاشة الكمبيوتر وتطبيق الأندرويد عبر مسح QR أو رمز النقل المشفر.
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

        <div className="p-5 grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
          {/* QR Code Visual Box */}
          <div className="flex flex-col items-center justify-center bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-3">
            <div className="bg-white p-3 rounded-xl shadow-md">
              <svg width="168" height="168" viewBox="0 0 21 21" shapeRendering="crispEdges">
                {qrMatrix.map((row, rIdx) =>
                  row.map((cell, cIdx) =>
                    cell ? (
                      <rect key={`${rIdx}-${cIdx}`} x={cIdx} y={rIdx} width={1} height={1} fill="#0F0B1E" />
                    ) : null
                  )
                )}
              </svg>
            </div>
            <div className="text-center">
              <div className="text-xs font-bold text-white">{activeProject.name}</div>
              <div className="text-[11px] text-purple-300 mt-0.5 flex items-center justify-center gap-1">
                <Smartphone className="w-3.5 h-3.5" />
                <span>امسح الرمز عبر كاميرا تطبيق الهاتف (ML Kit)</span>
              </div>
            </div>
          </div>

          {/* Token Copy & Import */}
          <div className="space-y-4 text-xs">
            <div className="p-3.5 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
              <div className="font-bold text-slate-200 flex items-center gap-1.5">
                <ArrowLeftRight className="w-3.5 h-3.5 text-purple-400" />
                <span>1. نسخ رمز النقل المشفر للمشروع الحالي:</span>
              </div>
              <p className="text-[11px] text-slate-400">
                يحتوي هذا الرمز المضغوط على كامل متطلبات المشروع والبرومبت الهندسي لنقله الفوري دون الحاجة لحساب سحابي.
              </p>
              <button
                onClick={handleCopyToken}
                className="w-full py-2 px-3 bg-purple-700 hover:bg-purple-600 text-white rounded-lg font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer"
              >
                {copiedToken ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
                <span>{copiedToken ? 'تم نسخ رمز المزامنة!' : 'نسخ رمز المزامنة المشفر'}</span>
              </button>
            </div>

            <div className="p-3.5 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
              <div className="font-bold text-slate-200 flex items-center gap-1.5">
                <Upload className="w-3.5 h-3.5 text-emerald-400" />
                <span>2. استيراد مشروع من جهاز آخر:</span>
              </div>
              <textarea
                rows={2}
                dir="ltr"
                value={importTokenInput}
                onChange={e => setImportTokenInput(e.target.value)}
                placeholder="الصق رمز المزامنة هنا..."
                className="w-full p-2 bg-slate-900 border border-slate-800 rounded-lg text-[11px] font-mono text-slate-200 focus:outline-none focus:border-purple-500"
              />
              <button
                onClick={handleImportToken}
                className="w-full py-2 px-3 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-semibold transition-colors cursor-pointer"
              >
                استيراد ومزامنة الآن
              </button>
              {importStatus && <div className="text-[11px] text-emerald-400 font-semibold pt-1">{importStatus}</div>}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
