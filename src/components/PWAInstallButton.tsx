import React, { useState } from 'react';
import { Download, Smartphone, X, CheckCircle2, WifiOff } from 'lucide-react';
import { usePWAInstall, useOnlineStatus } from '../hooks/usePWAInstall';

export const PWAInstallButton: React.FC = () => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showGuideModal, setShowGuideModal] = useState(false);

  if (isInstalled) {
    return null;
  }

  return (
    <>
      <button
        onClick={async () => {
          if (isInstallable) {
            await install();
          } else {
            setShowGuideModal(true);
          }
        }}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-purple-200 bg-purple-950/60 hover:bg-purple-900/70 rounded-lg border border-purple-700/40 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
        title="تثبيت الاستوديو كتطبيق مستقل (PWA)"
      >
        <Download className="w-3.5 h-3.5 text-purple-400" />
        <span>تثبيت التطبيق</span>
      </button>

      {showGuideModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4" dir="rtl">
          <div className="w-full max-w-md rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2.5">
                <img src="/app-icon.png" alt="Forgeprom" className="w-9 h-9 rounded-lg object-cover border border-slate-700" />
                <div>
                  <h3 className="text-sm font-bold text-white">تثبيت Forgeprom Code Studio</h3>
                  <p className="text-[11px] text-slate-400">يعمل كتطبيق سطح مكتب وهاتف دون إنترنت (PWA & APK)</p>
                </div>
              </div>
              <button
                onClick={() => setShowGuideModal(false)}
                className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {isIOS ? (
              <div className="space-y-3 text-xs text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-800">
                <p className="font-semibold text-white">خطوات التثبيت على iPhone / iPad (متصفح Safari):</p>
                <ol className="list-decimal list-inside space-y-1.5 text-slate-300">
                  <li>اضغط على زر <strong>المشاركة (Share)</strong> في شريط Safari السفلي.</li>
                  <li>مرر للأسفل واختر <strong>إضافة إلى الشاشة الرئيسية (Add to Home Screen)</strong>.</li>
                  <li>اضغط <strong>إضافة (Add)</strong> ليظهر التطبيق بأيقونته الرسمية ويعمل دون إنترنت.</li>
                </ol>
              </div>
            ) : (
              <div className="space-y-3 text-xs text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-800">
                <p className="font-semibold text-white flex items-center gap-1.5">
                  <Smartphone className="w-4 h-4 text-purple-400" />
                  خيارات التثبيت المباشر:
                </p>
                <ul className="space-y-2 text-slate-300">
                  <li className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                    <span><strong>على الكمبيوتر أو المتصفح:</strong> اضغط على أيقونة التثبيت في شريط العنوان العلوي للمتصفح (Chrome / Edge) لتثبيته كتطبيق مستقل.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-purple-400 shrink-0 mt-0.5" />
                    <span><strong>كملف أندرويد أصلي (APK):</strong> يمكنك تنزيل كود مشروع الأندرويد الكامل أو بناؤه تلقائياً عبر GitHub Actions من شريط الحالة العلوي.</span>
                  </li>
                </ul>
              </div>
            )}

            <button
              onClick={() => setShowGuideModal(false)}
              className="w-full py-2.5 rounded-lg bg-purple-700 hover:bg-purple-600 text-xs font-semibold text-white transition-colors cursor-pointer"
            >
              حسناً، فهمت
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div className="fixed bottom-4 left-4 z-50 flex items-center gap-2 rounded-lg bg-amber-950/90 border border-amber-700/60 px-3.5 py-2 text-xs font-medium text-amber-200 shadow-lg">
      <WifiOff className="w-3.5 h-3.5 text-amber-400 shrink-0" />
      <span>وضع عدم الاتصال — يتم استخدام البيانات المخزنة محلياً (Offline Cache)</span>
    </div>
  );
};
