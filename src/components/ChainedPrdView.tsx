import React, { useState } from 'react';
import {
  Layers,
  Sparkles,
  Download,
  Copy,
  Check,
  CheckCircle2,
  FileText,
  Database,
  Smartphone,
  GitBranch,
  Calendar,
} from 'lucide-react';
import { Project, ChainedPRD } from '../types';
import { ExportService } from '../services/exportService';
import { safeCopy } from '../services/safeStorage';

interface ChainedPrdViewProps {
  project: Project;
  onUpdateProject: (updated: Project) => void;
  isAirplaneMode: boolean;
}

export const ChainedPrdView: React.FC<ChainedPrdViewProps> = ({
  project,
  onUpdateProject,
  isAirplaneMode,
}) => {
  const [activeStep, setActiveStep] = useState<number>(1);
  const [isGeneratingStep, setIsGeneratingStep] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  const prd: ChainedPRD = project.chainedPrd || {};

  const stepMeta = [
    {
      num: 1,
      title: 'وثيقة رؤية المنتج (PRD) والـ MVP',
      icon: FileText,
      key: 'step1_prd' as keyof ChainedPRD,
      desc: 'تحديد المشكلة ونطاق الإصدار الأدنى والتحديات الحرجة',
    },
    {
      num: 2,
      title: 'البنية التقنية والمعمارية النظيفة',
      icon: GitBranch,
      key: 'step2_architecture' as keyof ChainedPRD,
      desc: 'المخطط المعماري النصي وتقسيم الحزم ومكتبات أندرويد',
    },
    {
      num: 3,
      title: 'مواصفات الشاشات وتدفقات المستخدم',
      icon: Smartphone,
      key: 'step3_screens_stories' as keyof ChainedPRD,
      desc: 'قصص المستخدم ومعايير القبول وسلوك الشاشات بـ Jetpack Compose',
    },
    {
      num: 4,
      title: 'مخطط البيانات المحلي (Room Schema)',
      icon: Database,
      key: 'step4_database_api' as keyof ChainedPRD,
      desc: 'جداول Room والـ DAOs والعلاقات وسياسة التشفير بـ SQLCipher',
    },
    {
      num: 5,
      title: 'خطة التنفيذ والاعتماديات المرحلية',
      icon: Calendar,
      key: 'step5_roadmap_migration' as keyof ChainedPRD,
      desc: 'ترتيب المهام حسب الاعتماديات وخطة ترحيل البيانات',
    },
  ];

  const currentStepData = stepMeta.find(s => s.num === activeStep)!;
  const currentContent = (prd[currentStepData.key] as string) || '';

  // Generate individual step
  const handleGenerateStep = async (stepNum: number) => {
    setIsGeneratingStep(true);

    try {
      if (!isAirplaneMode) {
        const res = await fetch('/api/chained-prd', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            step: stepNum,
            projectData: {
              name: project.name,
              description: project.description,
              type: project.type,
              analysis: project.analysis,
              rawIdea: project.rawIdea,
            },
          }),
        });
        if (res.ok) {
          const data = await res.json();
          const targetMeta = stepMeta.find(s => s.num === stepNum)!;
          const updatedPrd = {
            ...prd,
            [targetMeta.key]: data.content,
          };
          onUpdateProject({
            ...project,
            chainedPrd: updatedPrd,
          });
          setIsGeneratingStep(false);
          return;
        }
      }
    } catch (e) {
      console.warn('Server PRD generation unavailable, falling back to local-first deterministic specs', e);
    }

    // Local-First Fallback Content Generator
    const targetMeta = stepMeta.find(s => s.num === stepNum)!;
    let localContent = '';

    if (stepNum === 1) {
      localContent = `# 1. وثيقة رؤية المنتج (PRD) — ${project.name}
## 1.1 ملخص المنتج
${project.description || project.rawIdea}
يهدف المشروع إلى توفير تجربة مستخدم عالية الكفاءة تعمل بنسبة 100% محلياً (Local-First) على أجهزة أندرويد دون اشتراط وجود اتصال شبكي.

## 1.2 نطاق الإصدار الأول (MVP Scope)
- شاشة البداية ولوحة التحكم السريعة.
- إدارة وتخزين البيانات محلياً في قاعدة بيانات Room.
- حفظ المسودات تلقائياً وحمايتها من الإغلاق المفاجئ.
- التصدير والمشاركة بصيغ قياسية (TXT, Markdown, PDF).

## 1.3 معايير النجاح (Success Metrics)
- فتح واستجابة التطبيق بالكامل في وضع الطيران.
- استهلاك الذاكرة العشوائية تحت حاجز 150MB أثناء التشغيل العادي.
- عدم وجود أي تجمد في واجهة المستخدم (Zero Frame Drops).`;
    } else if (stepNum === 2) {
      localContent = `# 2. البنية التقنية والمعمارية النظيفة (Clean Architecture)
## 2.1 المكدس التقني المعتمد (Tech Stack)
- **لغة البرمجة:** Kotlin 100% (Android SDK 26+).
- **واجهة المستخدم:** Jetpack Compose + Material Design 3 مع دعم كامل لـ RTL.
- **إدارة الحالة:** MVVM / MVI مع Kotlin Coroutines & StateFlow.
- **قاعدة البيانات:** Room Database مع دعم الترحيل الآلي (AutoMigration).
- **حقن الاعتماديات:** Hilt / Dagger.

## 2.2 تقسيم الحزم والمخطط المعماري النصي
\`\`\`text
com.forgeprom.app/
├── core/
│   ├── common/       # ثوابت وموزعات ومساعدات
│   ├── ui/           # مكونات التصميم والسمات المشتركة
│   └── util/         # أدوات التشفير والتحقق
├── data/
│   ├── local/        # Room Database, DAOs, Entities, DataStore
│   └── repository/   # تطبيق مستودعات البيانات
├── domain/
│   ├── model/        # نماذج الأعمال النقية (Clean Domain Models)
│   ├── repository/   # واجهات المستودعات (Interfaces)
│   └── usecase/      # حالات الاستخدام المستقلة (Use Cases)
└── presentation/
    ├── navigation/   # الرسم البياني للتنقل (Compose NavGraph)
    └── screens/      # شاشات التطبيق و ViewModels
\`\`\``;
    } else if (stepNum === 3) {
      localContent = `# 3. مواصفات الشاشات وتدفقات المستخدم
## 3.1 الشاشات الأساسية
1. **شاشة لوحة التحكم (DashboardScreen):**
   - ملخص الأنشطة والبيانات الأخيرة.
   - أزرار إجراءات سريعة.
2. **شاشة الإدخال والمحرر (EditorScreen):**
   - حقول إدخال تدعم النصوص الطويلة.
   - عداد فوري وحفظ تلقائي كل ثانيتين.
3. **شاشة الإعدادات والتخزين (SettingsScreen):**
   - إدارة التخزين المحلي، النسخ الاحتياطي، ومظهر التطبيق.

## 3.2 قصص المستخدم (User Stories)
- **كصاحب مشروع،** أريد إدخال بياناتي بدون إنترنت حتى لا يتعطل عملي في الأماكن النائية.
  - *معيار القبول:* تسجيل العملية وتأكيد الحفظ في قاعدة بيانات Room خلال أقل من 100ms.
- **كمستخدم عربي،** أريد أن تظهر جميع النصوص باتجاه اليمين إلى اليسار مع عدم تشوه الكلمات الإنجليزية المدمجة.
  - *معيار القبول:* استخدام التكوين السليم لـ CompositionLocalProvider مع LayoutDirection.Rtl.`;
    } else if (stepNum === 4) {
      localContent = `# 4. مخطط البيانات المحلي (Room Database Schema)
## 4.1 الجداول والكيانات (Entities)
\`\`\`kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
)
\`\`\`

## 4.2 كائن الوصول للبيانات (DAO)
\`\`\`kotlin
@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY created_at DESC")
    fun getAllItemsFlow(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Delete
    suspend fun deleteItem(item: ItemEntity)
}
\`\`\`

## 4.3 سياسة التشفير والأمان
تشفير ملف قاعدة البيانات اختياري باستخدام مكتبة SQLCipher عند تفعيل خيار الحماية المشددة.`;
    } else {
      localContent = `# 5. خطة التنفيذ المرحلية والاعتماديات
## المرحلة 1: التأسيس والمعمارية (الأسبوع 1)
- إنشاء المشروع، إعداد Hilt و Room ومكتبات Compose.
- إعداد نظام الألوان والسمات ودعم RTL.

## المرحلة 2: مسار البيانات والتخزين (الأسبوع 2)
- كتابة الـ Entities والـ DAOs واختبارات التكامل مع Room.
- بناء مستودع البيانات وحالات الاستخدام (UseCases).

## المرحلة 3: الواجهات وتجربة المستخدم (الأسبوع 3)
- بناء الشاشات التفاعلية وربط الـ ViewModel مع StateFlow.
- تفعيل الحفظ التلقائي ومعالجة الحالات الفارغة (Empty States).

## المرحلة 4: مراجعة الجودة واختبار وضع الطيران (الأسبوع 4)
- تشغيل التطبيق في وضع الطيران واختبار انقطاع الطاقة وحماية البيانات.
- تصدير ملفات APK و AAB موقّعة.`;
    }

    const updatedPrd = {
      ...prd,
      [targetMeta.key]: localContent,
    };
    onUpdateProject({
      ...project,
      chainedPrd: updatedPrd,
    });
    setIsGeneratingStep(false);
  };

  const handleCopyCurrentContent = async () => {
    if (!currentContent) return;
    await safeCopy(currentContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleGenerateAllSteps = async () => {
    for (const s of stepMeta) {
      if (!prd[s.key]) {
        await handleGenerateStep(s.num);
      }
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-16">
      {/* Header */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-xl p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Layers className="w-5 h-5 text-purple-400" />
            <h1 className="text-lg md:text-xl font-bold text-white">
              هندسة التطبيقات بنظام التجزئة المتسلسلة (Chained Pipeline)
            </h1>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            توليد المواصفات الهندسية ومستند PRD على مراحل متدرجة تناسب سعة النماذج اللغوية المحلية وتمنع انقطاع النص.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleGenerateAllSteps}
            disabled={isGeneratingStep}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-md transition-colors cursor-pointer"
          >
            <Sparkles className="w-4 h-4" />
            <span>توليد جميع المراحل بالتتابع</span>
          </button>

          <button
            onClick={() => ExportService.exportProjectZip(project)}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium transition-colors cursor-pointer"
            title="تصدير الحزمة الهندسية الكاملة (ZIP)"
          >
            <Download className="w-4 h-4" />
            <span>تصدير ZIP</span>
          </button>
        </div>
      </div>

      {/* Step Selector Horizontal Pipeline */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2">
        {stepMeta.map(s => {
          const Icon = s.icon;
          const isDone = Boolean(prd[s.key]);
          const isSelected = activeStep === s.num;

          return (
            <button
              key={s.num}
              onClick={() => setActiveStep(s.num)}
              className={`p-3.5 rounded-xl border text-right transition-all flex flex-col justify-between space-y-2 cursor-pointer ${
                isSelected
                  ? 'bg-purple-950/60 border-purple-500/60 shadow-md shadow-purple-950/40'
                  : 'bg-slate-900/60 border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className={`text-[11px] font-mono font-bold ${isSelected ? 'text-purple-400' : 'text-slate-500'}`}>
                  الخطوة {s.num}
                </span>
                {isDone ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                ) : (
                  <span className="w-2 h-2 rounded-full bg-slate-700" />
                )}
              </div>

              <div>
                <h3 className={`text-xs font-bold line-clamp-1 ${isSelected ? 'text-white' : 'text-slate-300'}`}>
                  {s.title}
                </h3>
                <p className="text-[10px] text-slate-500 line-clamp-1 mt-0.5">
                  {s.desc}
                </p>
              </div>
            </button>
          );
        })}
      </div>

      {/* Main Content Area for Active Step */}
      <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden">
        {/* Step Top Bar */}
        <div className="bg-slate-900/80 border-b border-slate-800 p-4 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2">
            <currentStepData.icon className="w-4 h-4 text-purple-400" />
            <h2 className="font-bold text-white text-sm">
              الخطوة {currentStepData.num}: {currentStepData.title}
            </h2>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => handleGenerateStep(currentStepData.num)}
              disabled={isGeneratingStep}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white rounded-lg font-semibold transition-colors cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>{isGeneratingStep ? 'جاري الصياغة...' : currentContent ? 'إعادة التوليد' : 'توليد هذه المرحلة'}</span>
            </button>

            {currentContent && (
              <button
                onClick={handleCopyCurrentContent}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg transition-colors cursor-pointer"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'تم النسخ!' : 'نسخ المستند'}</span>
              </button>
            )}
          </div>
        </div>

        {/* Step Document Content */}
        <div className="p-6 min-h-[420px]">
          {currentContent ? (
            <div className="prose prose-invert max-w-none text-slate-200 text-xs md:text-sm leading-relaxed space-y-2">
              {currentContent.split('\n').map((line, idx) => {
                if (line.startsWith('# ')) {
                  return <h1 key={idx} className="text-lg font-bold text-purple-300 mt-4 mb-2 pb-1 border-b border-slate-800">{line.replace('# ', '')}</h1>;
                }
                if (line.startsWith('## ')) {
                  return <h2 key={idx} className="text-base font-semibold text-white mt-3 mb-1">{line.replace('## ', '')}</h2>;
                }
                if (line.startsWith('### ')) {
                  return <h3 key={idx} className="text-sm font-semibold text-purple-400 mt-2 mb-1">{line.replace('### ', '')}</h3>;
                }
                if (line.startsWith('- ') || line.startsWith('* ')) {
                  return (
                    <li key={idx} className="mr-4 list-disc text-slate-300 py-0.5">
                      {line.replace(/^[-*] /, '')}
                    </li>
                  );
                }
                if (line.startsWith('```')) {
                  return <div key={idx} className="text-slate-500 font-mono text-[10px] my-1" />;
                }
                if (line.trim() === '') {
                  return <div key={idx} className="h-1.5" />;
                }
                return <p key={idx} className="text-slate-300 my-1">{line}</p>;
              })}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-80 text-center space-y-3">
              <currentStepData.icon className="w-12 h-12 text-slate-700" />
              <h3 className="text-base font-bold text-slate-300">لم يتم توليد هذه المرحلة بعد</h3>
              <p className="text-xs text-slate-500 max-w-md">
                اضغط على زر التوليد في الأعلى ليقوم المحرك بصياغة وثيقة {currentStepData.title} بالاعتماد على مخرجات التحليل الهندسي السابقة.
              </p>
              <button
                onClick={() => handleGenerateStep(currentStepData.num)}
                disabled={isGeneratingStep}
                className="px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg shadow transition-colors cursor-pointer"
              >
                توليد وثيقة الخطوة {currentStepData.num} الآن
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
