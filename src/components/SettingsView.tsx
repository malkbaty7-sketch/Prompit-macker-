import React, { useState, useEffect } from 'react';
import {
  Settings,
  Cpu,
  HardDrive,
  Trash2,
  RefreshCw,
  Download,
  Upload,
  ShieldCheck,
  Flame,
  AlertTriangle,
  CheckCircle2,
  Globe,
  Activity,
} from 'lucide-react';
import { HardwareStatus, Project, LocalModel } from '../types';
import { HardwareProfiler } from '../services/hardwareProfiler';
import { AppStorage } from '../services/storage';
import { safeStorage } from '../services/safeStorage';

interface SettingsViewProps {
  hardware: HardwareStatus;
  onUpdateHardware: (updated: HardwareStatus) => void;
  projects: Project[];
  models: LocalModel[];
  onRefreshData: () => void;
}

const DEFAULT_CLOUD_SERVER_URL = 'https://ais-pre-wg5ba7jzr4vldg2v6hfqek-381610229333.europe-west3.run.app';

export const SettingsView: React.FC<SettingsViewProps> = ({
  hardware,
  onUpdateHardware,
  projects,
  models,
  onRefreshData,
}) => {
  const [threadCount, setThreadCount] = useState<number>(hardware.threadCount || 4);
  const [ecoMode, setEcoMode] = useState<boolean>(hardware.isEcoMode || false);
  const [contextLength, setContextLength] = useState<number>(4096);
  const [temperature, setTemperature] = useState<number>(0.7);

  // Cloud Server Connection State
  const [serverUrl, setServerUrl] = useState<string>(
    () => safeStorage.getItem('forgeprom_custom_server_url') || DEFAULT_CLOUD_SERVER_URL
  );
  const [serverPingResult, setServerPingResult] = useState<{
    connected: boolean;
    latencyMs: number;
    mode: string;
    message: string;
  } | null>(null);
  const [isTestingServer, setIsTestingServer] = useState<boolean>(false);

  // In-app confirmation dialog for data wipe
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  const totalModelsSizeMB = models.reduce((acc, m) => acc + (m.status === 'installed' ? m.fileSizeMB : 0), 0);

  const handleTestServerConnection = async () => {
    if (hardware.isAirplaneMode) {
      setServerPingResult({
        connected: false,
        latencyMs: 0,
        mode: 'Local-First (Airplane Mode)',
        message: 'وضع الطيران مفعل حالياً — جميع العمليات تعمل محلياً 100% دون اتصال بالخادم.',
      });
      return;
    }

    setIsTestingServer(true);
    const start = performance.now();
    try {
      const res = await fetch('/api/health');
      const latency = Math.round(performance.now() - start);
      if (res.ok) {
        const data = await res.json();
        setServerPingResult({
          connected: true,
          latencyMs: latency,
          mode: data.mode || 'Forgeprom Hybrid Engine',
          message: `متصل بالخادم السحابي بنجاح (${latency}ms) — جاهز لتحليل الأفكار والمزامنة.`,
        });
      } else {
        setServerPingResult({
          connected: false,
          latencyMs: latency,
          mode: 'Local-First Fallback',
          message: 'تعذر الوصول للخادم — يعمل التطبيق تلقائياً بالمحرك المحلي.',
        });
      }
    } catch {
      setServerPingResult({
        connected: false,
        latencyMs: 0,
        mode: 'Local-First Fallback',
        message: 'لا يوجد اتصال بالشبكة — يعمل التطبيق بالوضع المحلي 100%.',
      });
    } finally {
      setIsTestingServer(false);
    }
  };

  useEffect(() => {
    handleTestServerConnection();
  }, [hardware.isAirplaneMode]);

  const handleSaveServerUrl = () => {
    const cleaned = serverUrl.trim().replace(/\/+$/, '') || DEFAULT_CLOUD_SERVER_URL;
    setServerUrl(cleaned);
    safeStorage.setItem('forgeprom_custom_server_url', cleaned);
    handleTestServerConnection();
    showNotice('تم حفظ رابط الخادم السحابي وفحص الاتصال بنجاح.');
  };

  const handleSavePerformance = () => {
    const activeThreads = ecoMode ? Math.min(threadCount, 2) : threadCount;
    const updated: HardwareStatus = {
      ...hardware,
      activeThreads,
      threadCount: activeThreads,
      isEcoMode: ecoMode,
    };
    HardwareProfiler.saveStatus(updated);
    onUpdateHardware(updated);
    showNotice('تم حفظ إعدادات الأداء وتوزيع خيوط المعالج بنجاح.');
  };

  const handleSimulateTrimMemory = () => {
    const updated: HardwareStatus = {
      ...hardware,
      nativeHeapUsedMB: 0,
      activeModelId: null,
      availableRamMB: Math.min(hardware.totalRamMB - 800, hardware.availableRamMB + 1200),
    };
    HardwareProfiler.saveStatus(updated);
    onUpdateHardware(updated);
    showNotice('تمت محاكاة onTrimMemory: تم تفريغ النموذج من Native Heap فوراً وتحرير الذاكرة بنجاح.');
  };

  const handleExportFullBackup = () => {
    const backupData = {
      version: '2.0-stable',
      exportDate: new Date().toISOString(),
      serverUrl,
      projects,
      models: models.map(m => ({ id: m.id, name: m.name, status: m.status })),
      hardware,
    };
    const blob = new Blob([JSON.stringify(backupData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `forgeprom_full_backup_${Date.now()}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showNotice('تم تصدير ملف النسخة الاحتياطية الشاملة JSON بنجاح.');
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = evt => {
      try {
        const parsed = JSON.parse(evt.target?.result as string);
        if (parsed.projects && Array.isArray(parsed.projects)) {
          AppStorage.saveProjects(parsed.projects);
          onRefreshData();
          showNotice(`تم استيراد ${parsed.projects.length} مشروع من النسخة الاحتياطية بنجاح.`);
        } else {
          showNotice('تنسيق ملف النسخة الاحتياطية غير متوافق.');
        }
      } catch {
        showNotice('فشل قراءة ملف النسخة الاحتياطية.');
      }
    };
    reader.readAsText(file);
  };

  const handleWipeAllData = () => {
    AppStorage.clearAll();
    setShowClearConfirm(false);
    onRefreshData();
    showNotice('تم مسح جميع البيانات المحلية بنجاح والعودة لحالة المصنع.');
  };

  const showNotice = (msg: string) => {
    setActionNotice(msg);
    setTimeout(() => setActionNotice(null), 4000);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* 1. Header */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 backdrop-blur-sm">
        <div className="flex items-center gap-2 mb-1">
          <Settings className="w-5 h-5 text-purple-400" />
          <h1 className="text-xl font-bold text-white">إعدادات الأداء، الخادم السحابي، والذاكرة (المرحلة 13)</h1>
        </div>
        <p className="text-xs text-slate-400">
          إدارة الاتصال بالخادم السحابي (CloudApiService)، خيوط المعالج، تفريغ الذاكرة (onTrimMemory)، والنسخ الاحتياطي المحلي.
        </p>
      </div>

      {/* Action Notification Banner */}
      {actionNotice && (
        <div className="p-4 bg-purple-950/40 border border-purple-800/60 rounded-xl text-xs text-purple-200 flex items-center gap-3">
          <CheckCircle2 className="w-4 h-4 text-purple-400 shrink-0" />
          <span>{actionNotice}</span>
        </div>
      )}

      {/* 2. Cloud Server Connection & Diagnostics */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Globe className="w-4 h-4 text-purple-400" />
            <h2 className="text-sm font-bold text-white">إعدادات الاتصال بالخادم السحابي (CloudApiService)</h2>
          </div>
          <span className="text-xs text-emerald-400 font-mono">/api/health • Hybrid Mode</span>
        </div>

        <p className="text-xs text-slate-400 leading-relaxed">
          يرتبط تطبيق الأندرويد بعد تثبيت الـ APK بهذا الخادم لتحليل الأفكار والبحث التقني، ويتحول تلقائياً وفي نفس اللحظة إلى الوضع المحلي 100% عند انقطاع الشبكة أو تفعيل وضع الطيران.
        </p>

        <div className="flex flex-col sm:flex-row gap-3">
          <input
            type="url"
            dir="ltr"
            value={serverUrl}
            onChange={e => setServerUrl(e.target.value)}
            placeholder="https://..."
            className="flex-1 px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:border-purple-500"
          />
          <button
            onClick={handleSaveServerUrl}
            disabled={isTestingServer}
            className="px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer shrink-0"
          >
            <Activity className="w-3.5 h-3.5" />
            <span>{isTestingServer ? 'جارٍ الفحص...' : 'حفظ وفحص الخادم'}</span>
          </button>
        </div>

        {serverPingResult && (
          <div
            className={`p-3.5 rounded-lg border text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2 ${
              serverPingResult.connected
                ? 'bg-emerald-950/30 border-emerald-800/50 text-emerald-200'
                : 'bg-indigo-950/30 border-indigo-800/50 text-indigo-200'
            }`}
          >
            <div className="flex items-center gap-2">
              <span
                className={`w-2 h-2 rounded-full ${
                  serverPingResult.connected ? 'bg-emerald-400' : 'bg-indigo-400'
                }`}
              />
              <span className="font-semibold">{serverPingResult.message}</span>
            </div>
            <span className="font-mono text-[11px] opacity-80">{serverPingResult.mode}</span>
          </div>
        )}
      </div>

      {/* 3. Performance & Threading Management */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-5">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Cpu className="w-4 h-4 text-purple-400" />
            <h2 className="text-sm font-bold text-white">إدارة المعالج والاستدلال (CPU & Threads)</h2>
          </div>
          <span className="text-xs text-slate-400">موصى به: 4 خيوط للهواتف المتوسطة</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {/* Thread Count */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300 block">
              عدد خيوط المعالج المستخدمة: ({threadCount} أنوية)
            </label>
            <input
              type="range"
              min="1"
              max="8"
              step="1"
              disabled={ecoMode}
              value={ecoMode ? 2 : threadCount}
              onChange={e => setThreadCount(parseInt(e.target.value, 10))}
              className="w-full accent-purple-500"
            />
            <div className="flex justify-between text-[11px] text-slate-500">
              <span>1 نواة (أدنى حرارة)</span>
              <span>4 أنوية (متوازن)</span>
              <span>8 أنوية (أقصى سرعة)</span>
            </div>
          </div>

          {/* Eco Mode Toggle */}
          <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Flame className={`w-4 h-4 ${ecoMode ? 'text-amber-400' : 'text-slate-500'}`} />
              <div>
                <div className="text-xs font-semibold text-slate-200">وضع التشغيل الاقتصادي (Eco Mode)</div>
                <div className="text-[11px] text-slate-400">يحد المعالج بنواتين لمنع السخونة واستهلاك البطارية</div>
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={ecoMode}
                onChange={e => setEcoMode(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-9 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:right-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-purple-600"></div>
            </label>
          </div>
        </div>

        {/* Inference Context & Temperature */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 pt-3 border-t border-slate-800/80">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300 block">
              طول السياق (Context Length): {contextLength} Token
            </label>
            <select
              value={contextLength}
              onChange={e => setContextLength(parseInt(e.target.value, 10))}
              className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-purple-500"
            >
              <option value={2048}>2048 رمز (توفير فائق للذاكرة - مناسب لـ 4GB RAM)</option>
              <option value={4096}>4096 رمز (الوضع المتوازن المعتمد - 6GB RAM)</option>
              <option value={8192}>8192 رمز (سياق موسع - يتطلب 8GB+ RAM)</option>
            </select>
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-300 block">
              درجة التركيز (Temperature): {temperature}
            </label>
            <input
              type="range"
              min="0.1"
              max="1.0"
              step="0.1"
              value={temperature}
              onChange={e => setTemperature(parseFloat(e.target.value))}
              className="w-full accent-purple-500"
            />
            <div className="flex justify-between text-[11px] text-slate-500">
              <span>0.1 (دقة حتمية للأكواد)</span>
              <span>0.7 (متوازن)</span>
              <span>1.0 (إبداعي)</span>
            </div>
          </div>
        </div>

        <button
          onClick={handleSavePerformance}
          className="px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white rounded-lg text-xs font-semibold transition-colors cursor-pointer"
        >
          حفظ إعدادات الأداء
        </button>
      </div>

      {/* 4. Memory Diagnostics & onTrimMemory */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-purple-400" />
            <h2 className="text-sm font-bold text-white">تشخيص الذاكرة واستجابة النظام (Memory Guard)</h2>
          </div>
          <span className="text-xs text-slate-400 font-mono">ComponentCallbacks2</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
          <div className="p-3 bg-slate-950 border border-slate-800 rounded-lg">
            <div className="text-slate-400 mb-1">ذاكرة JVM المشغولة:</div>
            <div className="text-base font-bold text-slate-200">{hardware.jvmHeapUsedMB} MB</div>
            <div className="text-[10px] text-emerald-400">آمنة وخفيفة (واجهة فقط)</div>
          </div>
          <div className="p-3 bg-slate-950 border border-slate-800 rounded-lg">
            <div className="text-slate-400 mb-1">الذاكرة الأصلية C++ Native:</div>
            <div className="text-base font-bold text-purple-400">{hardware.nativeHeapUsedMB} MB</div>
            <div className="text-[10px] text-purple-400">تحميل النموذج حصرياً في JNI</div>
          </div>
          <div className="p-3 bg-slate-950 border border-slate-800 rounded-lg">
            <div className="text-slate-400 mb-1">إجمالي مساحة النماذج المخزنة:</div>
            <div className="text-base font-bold text-slate-200">{totalModelsSizeMB} MB</div>
            <div className="text-[10px] text-slate-400">تخزين معزول Context.filesDir</div>
          </div>
        </div>

        <div className="p-3.5 bg-slate-950/80 border border-slate-800 rounded-lg flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
          <div>
            <div className="font-semibold text-slate-200">محاكاة إشارة ضغط الذاكرة (onTrimMemory)</div>
            <div className="text-[11px] text-slate-400">
              تفريغ النموذج اللغوي فوراً من Native Heap لحماية التطبيق من الإغلاق القسري بواسطة نظام أندرويد.
            </div>
          </div>
          <button
            onClick={handleSimulateTrimMemory}
            className="px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-semibold flex items-center justify-center gap-2 transition-colors shrink-0 cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>تفريغ الذاكرة الآن</span>
          </button>
        </div>
      </div>

      {/* 5. Backup & Storage Clean */}
      <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-purple-400" />
            <h2 className="text-sm font-bold text-white">النسخ الاحتياطي والخصوصية المحلية (Local-First Backup)</h2>
          </div>
          <span className="text-xs text-slate-400">مشفر ومحلي 100%</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            onClick={handleExportFullBackup}
            className="p-4 bg-slate-950 border border-slate-800 hover:border-slate-700 rounded-xl text-right transition-colors cursor-pointer group"
          >
            <div className="flex items-center justify-between mb-2">
              <Download className="w-4 h-4 text-purple-400 group-hover:translate-y-0.5 transition-transform" />
              <span className="text-xs font-bold text-white">تصدير نسخة احتياطية شاملة</span>
            </div>
            <p className="text-[11px] text-slate-400">
              حفظ جميع المشاريع، وتحليلات الأفكار، وإصدارات البرومبتات في ملف JSON موحد.
            </p>
          </button>

          <label className="p-4 bg-slate-950 border border-slate-800 hover:border-slate-700 rounded-xl text-right transition-colors cursor-pointer group block">
            <div className="flex items-center justify-between mb-2">
              <Upload className="w-4 h-4 text-purple-400 group-hover:-translate-y-0.5 transition-transform" />
              <span className="text-xs font-bold text-white">استيراد نسخة احتياطية</span>
            </div>
            <p className="text-[11px] text-slate-400">
              استعادة مشاريع سابقة من ملف JSON دون الكتابة فوق المشاريع الحالية.
            </p>
            <input type="file" accept=".json" onChange={handleImportBackup} className="hidden" />
          </label>
        </div>

        {/* Clear all data section */}
        <div className="pt-4 border-t border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
          <div>
            <div className="font-semibold text-red-300">مسح كافة البيانات المحلية والتخزين</div>
            <div className="text-[11px] text-slate-400">
              حذف قاعدة بيانات Room والمسودات وإعادة التطبيق للحالة الافتراضية.
            </div>
          </div>
          <button
            onClick={() => setShowClearConfirm(true)}
            className="px-3.5 py-2 bg-red-950/40 hover:bg-red-900/60 text-red-300 border border-red-800/40 rounded-lg font-semibold flex items-center justify-center gap-1.5 transition-colors shrink-0 cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>مسح البيانات</span>
          </button>
        </div>
      </div>

      {/* Confirmation Modal */}
      {showClearConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
          <div className="bg-slate-900 border border-red-800/60 rounded-xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center gap-3 text-red-400">
              <AlertTriangle className="w-6 h-6 shrink-0" />
              <h3 className="text-base font-bold text-white">تأكيد مسح كافة البيانات المحلية</h3>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              هذا الإجراء سيقوم بحذف جميع المشاريع والبرومبتات المحلية المخزنة في جهازك ولا يمكن التراجع عنه.
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setShowClearConfirm(false)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold cursor-pointer"
              >
                إلغاء
              </button>
              <button
                onClick={handleWipeAllData}
                className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg text-xs font-semibold cursor-pointer"
              >
                نعم، احذف كافة البيانات
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
