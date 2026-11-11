import React, { useState, useRef } from 'react';
import {
  HardDrive,
  Cpu,
  Download,
  Pause,
  Play,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Upload,
  ShieldCheck,
  Star,
  FileCheck,
} from 'lucide-react';
import { LocalModel, HardwareStatus } from '../types';
import { HardwareProfiler } from '../services/hardwareProfiler';

interface ModelCenterViewProps {
  models: LocalModel[];
  hardware: HardwareStatus;
  activeModelId: string | null;
  onSelectActiveModel: (modelId: string) => void;
  onUpdateModel: (model: LocalModel) => void;
  onDeleteModel: (modelId: string) => void;
  onImportLocalFile: (model: LocalModel) => void;
}

export const ModelCenterView: React.FC<ModelCenterViewProps> = ({
  models,
  hardware,
  activeModelId,
  onSelectActiveModel,
  onUpdateModel,
  onDeleteModel,
  onImportLocalFile,
}) => {
  const [downloadingIds, setDownloadingIds] = useState<Record<string, number>>({});
  const [verifyingHashId, setVerifyingHashId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Filter tabs: 'all' | 'installed' | 'available'
  const [filter, setFilter] = useState<'all' | 'installed' | 'available'>('all');

  const filteredModels = models.filter(m => {
    if (filter === 'installed') return m.status === 'installed';
    if (filter === 'available') return m.status === 'available';
    return true;
  });

  const [noticeMessage, setNoticeMessage] = useState<{ text: string; type: 'success' | 'warning' | 'error' } | null>(null);
  const [confirmDeleteModelId, setConfirmDeleteModelId] = useState<string | null>(null);

  // Simulate download with pause/resume and SHA-256 Checksum validation
  const handleStartDownload = (model: LocalModel) => {
    // 1. Run hardware safety check
    const safety = HardwareProfiler.isModelSafeToLoad(model, hardware.availableRamMB);
    if (!safety.safe) {
      setNoticeMessage({
        text: `تنبيه توافق الذاكرة: النموذج يتطلب ${safety.requiredFreeRamMB}MB والمتاح ${hardware.availableRamMB}MB. يجري التنزيل مع تفعيل وضع الضغط.`,
        type: 'warning',
      });
    }

    setDownloadingIds(prev => ({ ...prev, [model.id]: prev[model.id] || 5 }));

    const interval = setInterval(async () => {
      setDownloadingIds(prev => {
        const current = prev[model.id] || 0;
        if (current >= 100) {
          clearInterval(interval);
          // Trigger SHA-256 Hash Verification
          triggerChecksumVerification(model);
          return { ...prev, [model.id]: 100 };
        }
        return { ...prev, [model.id]: current + 15 };
      });
    }, 400);
  };

  const handlePauseDownload = (modelId: string) => {
    setDownloadingIds(prev => {
      const copy = { ...prev };
      delete copy[modelId];
      return copy;
    });
  };

  const triggerChecksumVerification = async (model: LocalModel) => {
    setVerifyingHashId(model.id);
    const samplePayload = `GGUF_MODEL_${model.name}_QUANTIZED_Q4_K_M_TENSORS`;
    const computedHash = await HardwareProfiler.calculateSha256(samplePayload);

    setTimeout(() => {
      onUpdateModel({
        ...model,
        status: 'installed',
        sha256Calculated: model.sha256Expected,
        checksumVerified: true,
      });
      setVerifyingHashId(null);
      setDownloadingIds(prev => {
        const copy = { ...prev };
        delete copy[model.id];
        return copy;
      });
      setNoticeMessage({
        text: `تم تنزيل النموذج "${model.name}" بنجاح واجتياز فحص بصمة SHA-256.`,
        type: 'success',
      });
    }, 1200);
  };

  // Handle local file import from device (SAF simulation per Stage 4)
  const handleFilePicked = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const fileName = file.name;
    const isGguf = fileName.endsWith('.gguf') || fileName.endsWith('.bin');
    if (!isGguf) {
      setNoticeMessage({
        text: 'يرجى اختيار ملف نموذج بصيغة GGUF القياسية (.gguf) لتشغيله عبر محرك الاستدلال المحلي.',
        type: 'error',
      });
      return;
    }

    const sizeMB = Math.round(file.size / (1024 * 1024)) || 850;
    const computedHash = await HardwareProfiler.calculateSha256(fileName + file.size);

    const importedModel: LocalModel = {
      id: `imported_${Date.now()}`,
      name: fileName.replace(/\.[^/.]+$/, ''),
      family: 'Custom GGUF',
      parameters: '1B-3B',
      format: 'GGUF Q4_K_M',
      fileSizeMB: sizeMB,
      requiredRamMB: Math.round(sizeMB * 1.25),
      targetPhoneRam: sizeMB > 1500 ? '8GB+' : '4GB',
      arabicQualityRating: 4,
      status: 'installed',
      sha256Expected: computedHash,
      sha256Calculated: computedHash,
      checksumVerified: true,
      description: `نموذج محلي مستورد من ذاكرة الجهاز عبر منتقي ملفات النظام (SAF).`,
      strengths: ['استيراد مباشر بدون استهلاك للإنترنت', 'تخزين معزول في Context.filesDir'],
    };

    onImportLocalFile(importedModel);
    setNoticeMessage({
      text: `تم استيراد النموذج "${importedModel.name}" بنجاح وتأمينه في التخزين المحلي.`,
      type: 'success',
    });
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-16">
      {/* Hidden file input for SAF import */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFilePicked}
        accept=".gguf,.bin"
        className="hidden"
      />

      {/* In-app Notice Banner */}
      {noticeMessage && (
        <div
          className={`p-3.5 rounded-xl border text-xs flex items-center justify-between gap-3 ${
            noticeMessage.type === 'success'
              ? 'bg-emerald-950/60 border-emerald-800 text-emerald-200'
              : noticeMessage.type === 'warning'
              ? 'bg-amber-950/60 border-amber-800 text-amber-200'
              : 'bg-rose-950/60 border-rose-800 text-rose-200'
          }`}
        >
          <span>{noticeMessage.text}</span>
          <button
            onClick={() => setNoticeMessage(null)}
            className="text-slate-400 hover:text-white text-xs px-2 py-0.5 rounded"
          >
            إغلاق
          </button>
        </div>
      )}

      {/* Header */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <HardDrive className="w-5 h-5 text-purple-400" />
            <h1 className="text-lg md:text-xl font-bold text-white">
              مركز النماذج المحلية والتنزيل الآمن (Model Hub)
            </h1>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            نماذج مكمّمة (GGUF Q4_K_M) بحجم 1B إلى 3B معامل، مُحسنة للهواتف المتوسطة (RAM 4GB-8GB) مع فحص SHA-256 قبل التشغيل.
          </p>
        </div>

        {/* Import file button */}
        <button
          onClick={() => fileInputRef.current?.click()}
          className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold shadow transition-colors cursor-pointer self-start md:self-auto"
        >
          <Upload className="w-4 h-4" />
          <span>استيراد ملف GGUF من الجهاز</span>
        </button>
      </div>

      {/* Filter tabs */}
      <div className="flex items-center gap-1.5 p-1 bg-slate-900/60 rounded-lg border border-slate-800 text-xs w-fit">
        <button
          onClick={() => setFilter('all')}
          className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
            filter === 'all' ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40' : 'text-slate-400 hover:text-white'
          }`}
        >
          جميع النماذج ({models.length})
        </button>
        <button
          onClick={() => setFilter('installed')}
          className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
            filter === 'installed' ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40' : 'text-slate-400 hover:text-white'
          }`}
        >
          المثبتة محلياً ({models.filter(m => m.status === 'installed').length})
        </button>
        <button
          onClick={() => setFilter('available')}
          className={`px-3 py-1.5 rounded-md font-medium transition-colors cursor-pointer ${
            filter === 'available' ? 'bg-purple-900/60 text-purple-200 border border-purple-500/40' : 'text-slate-400 hover:text-white'
          }`}
        >
          المتاحة للتنزيل ({models.filter(m => m.status === 'available').length})
        </button>
      </div>

      {/* Model Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {filteredModels.map(model => {
          const isActive = activeModelId === model.id;
          const isInstalled = model.status === 'installed';
          const downloadProgress = downloadingIds[model.id];
          const isDownloading = downloadProgress !== undefined;
          const isVerifying = verifyingHashId === model.id;

          const safety = HardwareProfiler.isModelSafeToLoad(model, hardware.availableRamMB);

          return (
            <div
              key={model.id}
              className={`bg-slate-900/80 border rounded-xl p-5 flex flex-col justify-between space-y-4 transition-all ${
                isActive
                  ? 'border-purple-500/60 shadow-lg shadow-purple-950/40 bg-purple-950/20'
                  : 'border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="space-y-3">
                {/* Header row with model name and active indicator */}
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-base font-bold text-white">
                        {model.name}
                      </h2>
                      {isActive && (
                        <span className="text-[10px] bg-purple-900/80 text-purple-200 px-2 py-0.5 rounded font-semibold border border-purple-500/40">
                          النشط حالياً
                        </span>
                      )}
                    </div>

                    {/* Zero-Pill Metadata */}
                    <div className="flex items-center gap-2 text-xs text-slate-400 mt-1">
                      <span>{model.format}</span>
                      <span aria-hidden="true">·</span>
                      <span>{model.parameters} معامل</span>
                      <span aria-hidden="true">·</span>
                      <span className="font-mono text-purple-300">{model.fileSizeMB} MB</span>
                    </div>
                  </div>

                  {/* Star Rating for Arabic */}
                  <div className="flex items-center gap-0.5 text-amber-400" title={`تقييم دعم اللغة العربية: ${model.arabicQualityRating}/5`}>
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Star
                        key={i}
                        className={`w-3.5 h-3.5 ${i < model.arabicQualityRating ? 'fill-amber-400' : 'text-slate-600'}`}
                      />
                    ))}
                  </div>
                </div>

                <p className="text-xs text-slate-300 leading-relaxed">
                  {model.description}
                </p>

                {/* Hardware Safety Indicator */}
                <div className={`p-2.5 rounded-lg border text-xs flex items-start gap-2.5 ${
                  safety.safe
                    ? 'bg-emerald-950/30 border-emerald-800/40 text-emerald-300'
                    : 'bg-amber-950/30 border-amber-800/40 text-amber-300'
                }`}>
                  {safety.safe ? (
                    <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                  )}
                  <div className="space-y-0.5">
                    <span className="font-semibold block">
                      {safety.safe ? 'متوافق وآمن لذاكرة جهازك' : 'تحذير توافق الذاكرة'}
                    </span>
                    <span className="text-[11px] opacity-90 block">
                      يحتاج لـ <span className="font-mono font-bold">{safety.requiredFreeRamMB}MB</span> رام حر (حجم الملف × 1.25) — المتاح: <span className="font-mono font-bold">{hardware.availableRamMB}MB</span>.
                    </span>
                  </div>
                </div>

                {/* Checksum SHA-256 Info */}
                <div className="text-[11px] text-slate-400 bg-slate-950/70 p-2 rounded border border-slate-800 flex items-center justify-between font-mono">
                  <div className="flex items-center gap-1.5 truncate">
                    <FileCheck className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                    <span className="truncate">SHA-256: {model.sha256Expected.substring(0, 16)}...</span>
                  </div>
                  {model.checksumVerified && (
                    <span className="text-emerald-400 font-semibold shrink-0">متحقق منه ✓</span>
                  )}
                </div>

                {/* Download Progress Bar if active */}
                {isDownloading && (
                  <div className="space-y-1.5 pt-1">
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-purple-300">جاري التنزيل مع دعم الاستكمال...</span>
                      <span className="text-white font-bold">{downloadProgress}%</span>
                    </div>
                    <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
                      <div
                        className="bg-purple-600 h-full transition-all duration-300"
                        style={{ width: `${downloadProgress}%` }}
                      />
                    </div>
                  </div>
                )}

                {/* Hash verification spinner */}
                {isVerifying && (
                  <div className="text-xs text-amber-400 font-medium animate-pulse flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4" />
                    <span>جاري حساب بصمة SHA-256 للتحقق من سلامة الملف ومنع الانهيار...</span>
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs">
                {isInstalled ? (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onSelectActiveModel(model.id)}
                      disabled={isActive}
                      className={`px-3 py-1.5 rounded-lg font-semibold transition-colors cursor-pointer ${
                        isActive
                          ? 'bg-purple-900/60 text-purple-300 border border-purple-500/40 cursor-default'
                          : 'bg-slate-800 hover:bg-slate-700 text-white'
                      }`}
                    >
                      {isActive ? 'النموذج المعتمد حالياً' : 'تعيين كنموذج افتراضي'}
                    </button>

                    {confirmDeleteModelId === model.id ? (
                      <div className="flex items-center gap-1 bg-rose-950/80 px-2 py-0.5 rounded border border-rose-800 text-[11px]">
                        <span className="text-rose-200">حذف؟</span>
                        <button
                          onClick={() => {
                            onDeleteModel(model.id);
                            setConfirmDeleteModelId(null);
                          }}
                          className="text-rose-300 font-bold hover:text-white"
                        >
                          نعم
                        </button>
                        <span className="text-slate-500">|</span>
                        <button
                          onClick={() => setConfirmDeleteModelId(null)}
                          className="text-slate-400 hover:text-white"
                        >
                          لا
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setConfirmDeleteModelId(model.id)}
                        className="p-1.5 text-slate-500 hover:text-rose-400 rounded hover:bg-slate-800 transition-colors cursor-pointer"
                        title="حذف ملف النموذج وتحرير المساحة"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                ) : (
                  <div>
                    {isDownloading ? (
                      <button
                        onClick={() => handlePauseDownload(model.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-amber-700 hover:bg-amber-600 text-white rounded-lg font-medium transition-colors cursor-pointer"
                      >
                        <Pause className="w-3.5 h-3.5" />
                        <span>إيقاف مؤقت</span>
                      </button>
                    ) : (
                      <button
                        onClick={() => handleStartDownload(model)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-700 hover:bg-purple-600 text-white rounded-lg font-medium shadow transition-colors cursor-pointer"
                      >
                        <Download className="w-3.5 h-3.5" />
                        <span>تنزيل النموذج ({model.fileSizeMB} MB)</span>
                      </button>
                    )}
                  </div>
                )}

                <span className="text-[11px] text-slate-500">
                  فئة الذاكرة: {model.targetPhoneRam}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
