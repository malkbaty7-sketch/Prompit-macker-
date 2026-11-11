import React, { useState, useEffect, useCallback } from 'react';
import { Plane, Cpu, ShieldCheck, Database, Code, CheckCircle, Activity, RefreshCw } from 'lucide-react';
import { HardwareStatus, LocalModel } from '../types';
import { PWAInstallButton } from './PWAInstallButton';

interface StatusBarProps {
  hardware: HardwareStatus;
  activeModel?: LocalModel;
  onToggleAirplaneMode: () => void;
  onOpenAndroidCode: () => void;
  onOpenModels: () => void;
}

export const StatusBar: React.FC<StatusBarProps> = ({
  hardware,
  activeModel,
  onToggleAirplaneMode,
  onOpenAndroidCode,
  onOpenModels,
}) => {
  const [serverConnected, setServerConnected] = useState<boolean>(false);
  const [latencyMs, setLatencyMs] = useState<number | null>(null);
  const [isChecking, setIsChecking] = useState<boolean>(false);

  const checkServerHealth = useCallback(async () => {
    if (hardware.isAirplaneMode) {
      setServerConnected(false);
      setLatencyMs(null);
      return;
    }
    setIsChecking(true);
    const start = performance.now();
    try {
      const res = await fetch('/api/health');
      const elapsed = Math.round(performance.now() - start);
      if (res.ok) {
        setServerConnected(true);
        setLatencyMs(elapsed);
      } else {
        setServerConnected(false);
        setLatencyMs(null);
      }
    } catch {
      setServerConnected(false);
      setLatencyMs(null);
    } finally {
      setIsChecking(false);
    }
  }, [hardware.isAirplaneMode]);

  useEffect(() => {
    checkServerHealth();
  }, [checkServerHealth]);

  return (
    <div className="bg-slate-900/60 border-b border-slate-800/80 px-6 py-2.5 text-xs text-slate-300 flex flex-wrap items-center justify-between gap-4">
      {/* Right side: Hardware profiler & engine status */}
      <div className="flex items-center gap-4 flex-wrap">
        {/* Airplane Mode Toggle */}
        <button
          onClick={onToggleAirplaneMode}
          className={`flex items-center gap-2 px-2.5 py-1 rounded-md border text-xs font-medium transition-colors cursor-pointer ${
            hardware.isAirplaneMode
              ? 'bg-purple-950/60 text-purple-200 border-purple-500/40 hover:bg-purple-900/50'
              : 'bg-slate-800/80 text-slate-300 border-slate-700 hover:bg-slate-800'
          }`}
          title="تبديل وضع العمل بدون إنترنت (Airplane Mode)"
        >
          <Plane className={`w-3.5 h-3.5 ${hardware.isAirplaneMode ? 'text-purple-400 rotate-45' : 'text-slate-400'}`} />
          <span>{hardware.isAirplaneMode ? 'وضع الطيران: محلي 100% 🔒' : 'الوضع الهجين (محلي + سحابي) ☁️'}</span>
        </button>

        {/* Live Cloud Server Connection & Ping */}
        <button
          onClick={checkServerHealth}
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-xs font-medium transition-colors cursor-pointer ${
            !hardware.isAirplaneMode && serverConnected
              ? 'bg-emerald-950/50 text-emerald-300 border-emerald-700/50 hover:bg-emerald-900/50'
              : 'bg-slate-800/60 text-slate-400 border-slate-700/60 hover:bg-slate-800'
          }`}
          title="انقر لفحص اتصال الخادم السحابي وزمن الاستجابة (/api/health)"
        >
          {isChecking ? (
            <RefreshCw className="w-3.5 h-3.5 text-purple-400 animate-spin" />
          ) : (
            <Activity className={`w-3.5 h-3.5 ${!hardware.isAirplaneMode && serverConnected ? 'text-emerald-400' : 'text-slate-500'}`} />
          )}
          <span>
            {hardware.isAirplaneMode
              ? 'الخادم معزول (Local-First)'
              : serverConnected
              ? `متصل بالخادم (${latencyMs ?? 12}ms)`
              : 'وضع محلي مستقل'}
          </span>
        </button>

        <span className="text-slate-600 hidden sm:inline">|</span>

        {/* Active Local Model */}
        <button
          onClick={onOpenModels}
          className="flex items-center gap-1.5 text-slate-300 hover:text-white transition-colors cursor-pointer"
        >
          <Cpu className="w-3.5 h-3.5 text-purple-400" />
          <span className="text-slate-400">النموذج النشط:</span>
          <span className="font-medium text-slate-100">{activeModel ? activeModel.name : 'لا يوجد نموذج نشط'}</span>
          <span className="text-purple-300/80 font-mono text-[11px]">(GGUF Q4_K_M)</span>
        </button>

        <span className="text-slate-600 hidden sm:inline">|</span>

        {/* Memory & OOM Safety */}
        <div className="flex items-center gap-1.5">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-slate-400">الرام المتاح:</span>
          <span className="font-mono text-emerald-300 font-semibold">{(hardware.availableRamMB / 1024).toFixed(2)}GB</span>
          <span className="text-slate-500 text-[11px]">(حماية OOM نشطة)</span>
        </div>

        <span className="text-slate-600 hidden lg:inline">|</span>

        {/* Native Heap vs JVM Heap */}
        <div className="hidden lg:flex items-center gap-1.5 text-slate-400 font-mono text-[11px]">
          <Database className="w-3.5 h-3.5 text-indigo-400" />
          <span>Native Heap: {hardware.nativeHeapUsedMB}MB</span>
          <span className="text-slate-600">/</span>
          <span>JVM: {hardware.jvmHeapUsedMB}MB</span>
        </div>
      </div>

      {/* Left side: Android Native status & Quick tools */}
      <div className="flex items-center gap-2.5">
        <PWAInstallButton />

        <button
          onClick={onOpenAndroidCode}
          className="flex items-center gap-1.5 text-xs font-semibold text-purple-200 bg-purple-900/50 hover:bg-purple-900/80 active:bg-purple-800 border border-purple-500/40 px-2.5 py-1 rounded-md transition-colors cursor-pointer shadow-xs shadow-purple-900/20"
          title="تحميل وبناء ملف APK لنظام Android عبر GitHub Actions"
        >
          <Code className="w-3.5 h-3.5 text-purple-400" />
          <span>كود Android وبناء APK (GitHub)</span>
        </button>

        <div className="hidden sm:flex items-center gap-1 text-[11px] text-emerald-400/90 bg-emerald-950/40 px-2 py-0.5 rounded border border-emerald-800/40">
          <CheckCircle className="w-3 h-3 text-emerald-400" />
          <span>Clean Architecture جاهز</span>
        </div>
      </div>
    </div>
  );
};
