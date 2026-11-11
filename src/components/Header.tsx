import React from 'react';
import { Plus, Sun, Moon } from 'lucide-react';

interface HeaderProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
  onNewPrompt: () => void;
  darkMode: boolean;
  onToggleDarkMode: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentTab,
  onSelectTab,
  onNewPrompt,
  darkMode,
  onToggleDarkMode,
}) => {
  const navItems = [
    { id: 'dashboard', label: 'الرئيسية' },
    { id: 'projects', label: 'المشاريع' },
    { id: 'studio', label: 'استوديو البرومبت' },
    { id: 'prd', label: 'هندسة النظم (PRD)' },
    { id: 'models', label: 'مركز النماذج' },
    { id: 'vision', label: 'تحليل الواجهات' },
    { id: 'research', label: 'البحث التقني' },
    { id: 'diagrams', label: 'المخططات والإطلاق' },
    { id: 'enterprise', label: 'الأمان والمؤسسات' },
    { id: 'settings', label: 'الإعدادات' },
  ];

  return (
    <header className="border-b border-slate-800 bg-slate-950/90 backdrop-blur-md sticky top-0 z-40">
      <div className="flex items-center justify-between gap-4 px-4 sm:px-6 py-3">
        {/* Zone 1: Brand wordmark with optimized metallic glass app icon */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => onSelectTab('dashboard')}
            className="flex items-center gap-3 group cursor-pointer text-right"
          >
            <img
              src="/app-icon.png"
              alt="Forgeprom Code Studio"
              className="w-10 h-10 rounded-xl object-cover shadow-md shadow-black/60 border border-slate-700/80 group-hover:border-purple-500/60 transition-all shrink-0"
            />
            <span className="text-base sm:text-lg font-bold tracking-tight text-white group-hover:text-purple-300 transition-colors whitespace-nowrap">
              Forgeprom Code Studio
            </span>
          </button>
        </div>

        {/* Zone 2: Clean single-line text navigation links (visible on all viewports with overflow scroll) */}
        <nav className="flex items-center gap-4 sm:gap-5 text-xs font-medium text-slate-300 overflow-x-auto py-1 max-w-full">
          {navItems.map(item => (
            <button
              key={item.id}
              onClick={() => onSelectTab(item.id)}
              className={`hover:text-purple-400 transition-colors whitespace-nowrap shrink-0 py-1 cursor-pointer ${
                currentTab === item.id ? 'text-purple-400 font-semibold border-b-2 border-purple-500' : ''
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>

        {/* Zone 3: 1 primary action & theme toggle */}
        <div className="flex items-center gap-2.5 shrink-0">
          <button
            onClick={onToggleDarkMode}
            className="p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-900 border border-slate-800 transition-colors cursor-pointer"
            title={darkMode ? 'التحويل للوضع الفاتح' : 'التحويل للوضع الداكن'}
            aria-label="Toggle theme"
          >
            {darkMode ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>
          <button
            onClick={onNewPrompt}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-white bg-purple-700 hover:bg-purple-600 active:bg-purple-800 rounded-lg shadow-sm shadow-purple-900/30 transition-colors whitespace-nowrap shrink-0 border border-purple-500/30 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">إنشاء برومبت جديد</span>
          </button>
        </div>
      </div>
    </header>
  );
};
