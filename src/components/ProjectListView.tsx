import React, { useState } from 'react';
import {
  Search,
  Plus,
  Pin,
  Copy,
  Trash2,
  Archive,
  Download,
  FileCode,
  Tag,
  Check,
} from 'lucide-react';
import { Project, ProjectType, TargetPlatform } from '../types';

interface ProjectListViewProps {
  projects: Project[];
  onOpenProject: (projectId: string) => void;
  onCreateProject: (project: Partial<Project>) => void;
  onDuplicateProject: (projectId: string) => void;
  onDeleteProject: (projectId: string) => void;
  onTogglePinProject: (projectId: string) => void;
  onToggleArchiveProject: (projectId: string) => void;
  onExportProject: (project: Project) => void;
}

export const ProjectListView: React.FC<ProjectListViewProps> = ({
  projects,
  onOpenProject,
  onCreateProject,
  onDuplicateProject,
  onDeleteProject,
  onTogglePinProject,
  onToggleArchiveProject,
  onExportProject,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState<string>('all');
  const [showArchived, setShowArchived] = useState(false);
  const [showNewModal, setShowNewModal] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  // New Project Form State
  const [newProjectName, setNewProjectName] = useState('');
  const [newProjectDesc, setNewProjectDesc] = useState('');
  const [newProjectType, setNewProjectType] = useState<ProjectType>('full_app');
  const [newProjectPlatform, setNewProjectPlatform] = useState<TargetPlatform>('android_studio_ai');
  const [newProjectIdea, setNewProjectIdea] = useState('');

  // Filter projects
  const filteredProjects = projects.filter(p => {
    if (!showArchived && p.isArchived) return false;
    if (showArchived && !p.isArchived) return false;
    if (selectedType !== 'all' && p.type !== selectedType) return false;

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchName = p.name.toLowerCase().includes(q);
      const matchDesc = p.description.toLowerCase().includes(q);
      const matchTags = p.tags.some(t => t.toLowerCase().includes(q));
      if (!matchName && !matchDesc && !matchTags) return false;
    }
    return true;
  });

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newProjectName.trim()) return;

    onCreateProject({
      name: newProjectName.trim(),
      description: newProjectDesc.trim() || 'مشروع هندسي جديد في Forgeprom Studio',
      type: newProjectType,
      targetPlatform: newProjectPlatform,
      rawIdea: newProjectIdea.trim(),
      tags: ['Android', 'Kotlin', 'RTL', newProjectType],
      category: 'عام',
      isPinned: false,
      isArchived: false,
      prompts: [],
      currentPromptIndex: 0,
    });

    // Reset and close
    setNewProjectName('');
    setNewProjectDesc('');
    setNewProjectIdea('');
    setShowNewModal(false);
  };

  const projectTypeLabels: Record<ProjectType, string> = {
    full_app: 'تطبيق كامل',
    new_feature: 'ميزة جديدة',
    refactor: 'إعادة هيكلة',
    bug_fix: 'إصلاح خطأ',
    idea_analysis: 'تحليل فكرة',
    general_prompt: 'برومبت عام',
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Top Controls: Title, Search, Filter tabs, New Project button */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl md:text-2xl font-bold text-white tracking-tight">
            إدارة المشاريع وقاعدة البيانات المحلية (Room DB)
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            جميع المشاريع محفوظة محلياً على الجهاز وتُستعاد تلقائياً عند فتح التطبيق.
          </p>
        </div>

        <button
          onClick={() => setShowNewModal(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 active:bg-purple-800 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>مشروع جديد</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-slate-900/60 p-3 rounded-xl border border-slate-800">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="بحث بالاسم أو الوصف أو الوسوم..."
            className="w-full bg-slate-950 border border-slate-800 text-slate-200 text-xs rounded-lg pr-9 pl-4 py-2 focus:outline-none focus:border-purple-500 transition-colors"
          />
        </div>

        {/* Filter buttons (Interactive buttons permitted per frontend constitution) */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 text-xs">
          <button
            onClick={() => setSelectedType('all')}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap cursor-pointer ${
              selectedType === 'all'
                ? 'bg-purple-900/50 text-purple-200 border border-purple-500/40'
                : 'text-slate-400 hover:text-white bg-slate-950/60 border border-transparent'
            }`}
          >
            الكل ({projects.length})
          </button>
          <button
            onClick={() => setSelectedType('full_app')}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap cursor-pointer ${
              selectedType === 'full_app'
                ? 'bg-purple-900/50 text-purple-200 border border-purple-500/40'
                : 'text-slate-400 hover:text-white bg-slate-950/60 border border-transparent'
            }`}
          >
            تطبيق كامل
          </button>
          <button
            onClick={() => setSelectedType('new_feature')}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap cursor-pointer ${
              selectedType === 'new_feature'
                ? 'bg-purple-900/50 text-purple-200 border border-purple-500/40'
                : 'text-slate-400 hover:text-white bg-slate-950/60 border border-transparent'
            }`}
          >
            ميزة جديدة
          </button>
          <button
            onClick={() => setSelectedType('idea_analysis')}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap cursor-pointer ${
              selectedType === 'idea_analysis'
                ? 'bg-purple-900/50 text-purple-200 border border-purple-500/40'
                : 'text-slate-400 hover:text-white bg-slate-950/60 border border-transparent'
            }`}
          >
            تحليل فكرة
          </button>

          <button
            onClick={() => setShowArchived(!showArchived)}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap cursor-pointer ml-auto ${
              showArchived
                ? 'bg-amber-900/40 text-amber-300 border border-amber-600/40'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            {showArchived ? 'المشاريع المؤرشفة' : 'الأرشيف'}
          </button>
        </div>
      </div>

      {/* Projects Grid */}
      {filteredProjects.length === 0 ? (
        <div className="bg-slate-900/30 border border-slate-800/80 rounded-2xl p-12 text-center space-y-3">
          <FileCode className="w-10 h-10 text-slate-600 mx-auto" />
          <h3 className="text-base font-semibold text-slate-300">لا توجد مشاريع مطابقة</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            لم نجد أي مشروع يطابق شروط البحث الحالية. يمكنك إنشاء مشروع جديد أو مسح الفلاتر.
          </p>
          <button
            onClick={() => setShowNewModal(true)}
            className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>إنشاء أول مشروع الآن</span>
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredProjects.map(project => {
            const currentPrompt = project.prompts[project.currentPromptIndex] || project.prompts[0];
            return (
              <div
                key={project.id}
                className="bg-slate-900/80 border border-slate-800 hover:border-slate-700 rounded-xl p-5 flex flex-col justify-between space-y-4 transition-all"
              >
                <div className="space-y-3">
                  {/* Top line with Pin and title */}
                  <div className="flex items-start justify-between gap-2">
                    <h2
                      onClick={() => onOpenProject(project.id)}
                      className="text-base font-bold text-white hover:text-purple-300 transition-colors cursor-pointer line-clamp-1"
                    >
                      {project.name}
                    </h2>

                    <button
                      onClick={() => onTogglePinProject(project.id)}
                      className={`p-1 rounded transition-colors cursor-pointer ${
                        project.isPinned
                          ? 'text-purple-400 bg-purple-950/60'
                          : 'text-slate-500 hover:text-slate-300'
                      }`}
                      title={project.isPinned ? 'إلغاء التثبيت' : 'تثبيت المشروع في الأعلى'}
                    >
                      <Pin className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {/* Zero-Pill Text Metadata with typographic separators */}
                  <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
                    <span>{projectTypeLabels[project.type] || project.type}</span>
                    <span aria-hidden="true">·</span>
                    <span>{project.targetPlatform || 'Android'}</span>
                    <span aria-hidden="true">·</span>
                    <span>تحديث {project.updatedAt ? new Date(project.updatedAt).toLocaleDateString('ar-SA') : 'الآن'}</span>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed line-clamp-3">
                    {project.description}
                  </p>

                  {/* Tags */}
                  {Array.isArray(project.tags) && project.tags.length > 0 && (
                    <div className="flex items-center gap-1.5 flex-wrap text-[11px] text-slate-400">
                      <Tag className="w-3 h-3 text-purple-400" />
                      {project.tags.map(t => (
                        <span key={t} className="text-slate-400">#{t}</span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Bottom Card Bar with Actions */}
                <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs">
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => onDuplicateProject(project.id)}
                      className="p-1.5 text-slate-400 hover:text-white rounded hover:bg-slate-800 transition-colors cursor-pointer"
                      title="نسخ المشروع (Duplicate)"
                    >
                      <Copy className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => onExportProject(project)}
                      className="p-1.5 text-slate-400 hover:text-white rounded hover:bg-slate-800 transition-colors cursor-pointer"
                      title="تصدير حزمة ZIP للمشروع"
                    >
                      <Download className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => onToggleArchiveProject(project.id)}
                      className="p-1.5 text-slate-400 hover:text-white rounded hover:bg-slate-800 transition-colors cursor-pointer"
                      title={project.isArchived ? 'استعادة من الأرشيف' : 'أرشفة'}
                    >
                      <Archive className="w-3.5 h-3.5" />
                    </button>
                    {confirmDeleteId === project.id ? (
                      <div className="flex items-center gap-1 bg-rose-950/80 px-2 py-0.5 rounded border border-rose-800 text-[11px]">
                        <span className="text-rose-200">حذف؟</span>
                        <button
                          onClick={() => {
                            onDeleteProject(project.id);
                            setConfirmDeleteId(null);
                          }}
                          className="text-rose-300 font-bold hover:text-white"
                        >
                          نعم
                        </button>
                        <span className="text-slate-500">|</span>
                        <button
                          onClick={() => setConfirmDeleteId(null)}
                          className="text-slate-400 hover:text-white"
                        >
                          لا
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setConfirmDeleteId(project.id)}
                        className="p-1.5 text-slate-400 hover:text-rose-400 rounded hover:bg-slate-800 transition-colors cursor-pointer"
                        title="حذف المشروع"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>

                  <button
                    onClick={() => onOpenProject(project.id)}
                    className="px-3 py-1 bg-purple-700 hover:bg-purple-600 text-white rounded font-medium text-xs transition-colors cursor-pointer"
                  >
                    فتح المحرر
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* New Project Modal */}
      {showNewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800">
              <h2 className="text-base font-bold text-white">إنشاء مشروع هندسي جديد</h2>
              <button
                onClick={() => setShowNewModal(false)}
                className="text-slate-400 hover:text-white text-xs cursor-pointer"
              >
                إغلاق
              </button>
            </div>

            <form onSubmit={handleCreateSubmit} className="space-y-4 text-xs">
              <div>
                <label className="block text-slate-300 font-medium mb-1">اسم المشروع *</label>
                <input
                  type="text"
                  required
                  value={newProjectName}
                  onChange={e => setNewProjectName(e.target.value)}
                  placeholder="مثال: تطبيق الفواتير السريعة ForgeInvoice"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-purple-500"
                />
              </div>

              <div>
                <label className="block text-slate-300 font-medium mb-1">وصف مختصر</label>
                <input
                  type="text"
                  value={newProjectDesc}
                  onChange={e => setNewProjectDesc(e.target.value)}
                  placeholder="وصف الهدف الأساسي للمشروع..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-purple-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-300 font-medium mb-1">نوع المشروع</label>
                  <select
                    value={newProjectType}
                    onChange={e => setNewProjectType(e.target.value as ProjectType)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-purple-500"
                  >
                    <option value="full_app">تطبيق كامل (Full App)</option>
                    <option value="new_feature">ميزة جديدة (New Feature)</option>
                    <option value="refactor">إعادة هيكلة (Refactor)</option>
                    <option value="bug_fix">إصلاح مشكلة (Bug Fix)</option>
                    <option value="idea_analysis">تحليل فكرة فقط</option>
                  </select>
                </div>

                <div>
                  <label className="block text-slate-300 font-medium mb-1">المنصة المستهدفة</label>
                  <select
                    value={newProjectPlatform}
                    onChange={e => setNewProjectPlatform(e.target.value as TargetPlatform)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-purple-500"
                  >
                    <option value="android_studio_ai">Android Studio AI</option>
                    <option value="cursor">Cursor AI</option>
                    <option value="claude_code">Claude Code</option>
                    <option value="bolt">Bolt.new</option>
                    <option value="lovable">Lovable</option>
                    <option value="windsurf">Windsurf</option>
                    <option value="general">عام</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-slate-300 font-medium mb-1">
                  الوصف الحر للفكرة (يمكن إدخاله الآن أو لاحقاً في المحلل)
                </label>
                <textarea
                  rows={4}
                  value={newProjectIdea}
                  onChange={e => setNewProjectIdea(e.target.value)}
                  placeholder="اكتب فكرتك بعفوية باللغة العربية: ما الذي تريده أن يفعله التطبيق؟ من المستخدم المستهدف؟..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3 text-white focus:outline-none focus:border-purple-500 resize-none"
                />
              </div>

              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowNewModal(false)}
                  className="px-4 py-2 text-slate-400 hover:text-white rounded-lg cursor-pointer"
                >
                  إلغاء
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-purple-700 hover:bg-purple-600 text-white font-semibold rounded-lg transition-colors cursor-pointer"
                >
                  إنشاء المشروع
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
