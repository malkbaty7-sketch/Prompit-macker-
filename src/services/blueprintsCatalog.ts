import { Project, ProjectType, TargetPlatform } from '../types';

export interface IndustryBlueprint {
  id: string;
  name: string;
  category: string;
  type: ProjectType;
  targetPlatform: TargetPlatform;
  techStackLabel: string;
  description: string;
  rawIdea: string;
  problem: string;
  primaryGoal: string;
  mustHave: string[];
  niceToHave: string[];
  constraints: string[];
  verifiedFacts: string[];
  assumptions: string[];
  prebuiltPrompt: string;
}

export const INDUSTRY_BLUEPRINTS: IndustryBlueprint[] = [
  {
    id: 'bp-marketplace',
    name: 'متجر إلكتروني متعدد التجار (Multi-Vendor Marketplace)',
    category: 'التجارة الإلكترونية',
    type: 'full_app',
    targetPlatform: 'cursor',
    techStackLabel: 'Flutter / Mobile',
    description: 'منصة تسوق متكاملة مع سلة مشتريات محلية، بوابات دفع، لوحة تحكم للتجار، وتتبع لحظي للطلبات.',
    rawIdea:
      'بناء تطبيق متجر إلكتروني متعدد البائعين باللغة العربية يدعم تصفح المنتجات وحفظ السلة بدون إنترنت، مع بوابات دفع إلكتروني، وإدارة مخزون لكل تاجر، وتتبع حالة التوصيل.',
    problem: 'صعوبة إدارة عدة متاجر وتجار في تطبيق واحد مع الحفاظ على سرعة التصفح واستمرارية سلة المشتريات عند ضعف الاتصال.',
    primaryGoal: 'تمكين العملاء من التسوق من عدة تجار في طلب موحد مع تقسيم آلي للعمولات والمستحقات.',
    mustHave: [
      'كتالوج منتجات متعدد الفئات مع بحث وفلترة سريعة وحفظ محلي (SQLite/Hive)',
      'سلة مشتريات موحدة تدعم تجميع منتجات من عدة تجار وحساب تكلفة الشحن',
      'لوحة تحكم للتاجر لإدارة المنتجات والمخزون والعروض الترويجية',
      'تكامل مع بوابات الدفع الإلكتروني والدفع عند الاستلام',
    ],
    niceToHave: ['نظام نقاط ولاء ومكافآت للعملاء', 'مقارنة المنتجات جنباً إلى جنب'],
    constraints: ['واجهة عربية RTL كاملة مع دعم الوضع الداكن', 'زمن تحميل الصفحة الرئيسية أقل من 1.2 ثانية'],
    verifiedFacts: ['تطبيق متعدد التجار', 'دعم حفظ السلة والكتالوج محلياً', 'واجهة عربية RTL'],
    assumptions: ['استخدام تقسيم آلي للطلبات الفرعية (Sub-orders) لكل تاجر'],
    prebuiltPrompt: `# البرومبت الهندسي الشامل: متجر إلكتروني متعدد التجار (Multi-Vendor Marketplace)
- **المنصة المستهدفة:** Flutter (Android & iOS) مع معمارية Clean Architecture (BLoC / Riverpod)
- **قاعدة البيانات المحلية:** Drift (SQLite) لحفظ الكتالوج وسلة المشتريات دون إنترنت (Local-First)
- **المتطلبات الأساسية:**
  1. واجهة عربية RTL أصيلة مع Material Design 3.
  2. نظام سلة مشتريات ذكي يقسم الطلب الرئيسي إلى طلبات فرعية لكل تاجر.
  3. لوحة تاجر مدمجة لإدارة المخزون وتحديث حالات الشحن.
  4. مزامنة تلقائية للسلة والطلبات المفضلة فور عودة الاتصال بالشبكة.`,
  },
  {
    id: 'bp-fleet-delivery',
    name: 'منظومة توصيل وتتبع مناديب (Fleet & Delivery Tracker)',
    category: 'اللوجستيات والتوصيل',
    type: 'full_app',
    targetPlatform: 'android_studio_ai',
    techStackLabel: 'Android Kotlin',
    description: 'تطبيق أندرويد أصلي لتتبع المناديب، جدولة المسارات، وإثبات التسليم بالباركود والتوقيع الإلكتروني.',
    rawIdea:
      'تطبيق أندرويد أصلي لمناديب التوصيل يعمل بكفاءة في المناطق ضعيفة التغطية، يخزن مسار الحركة وحالات التسليم محلياً، ويدعم مسح باركود الشحنات عبر الكاميرا والتوقيع على الشاشة.',
    problem: 'انقطاع اتصال الإنترنت لدى مناديب التوصيل أثناء التنقل مما يؤدي لضياع إثباتات التسليم وتأخر تحديث حالات الشحنات.',
    primaryGoal: 'ضمان استمرارية عمل المندوب 100% دون إنترنت مع مزامنة فورية لإثباتات التسليم والإحداثيات عند توفر الشبكة.',
    mustHave: [
      'خدمة تتبع خلفية (Foreground Service) موفرة للبطارية مع تخزين الإحداثيات في Room DB',
      'مسح باركود و QR للشحنات بدون إنترنت عبر Google ML Kit',
      'شاشة توقيع إلكتروني للعميل (Canvas Signature) والتقاط صورة إثبات التسليم',
      'طابور مزامنة ذكي (WorkManager Offline Queue) يرفع التحديثات تلقائياً',
    ],
    niceToHave: ['ترتيب ذكي للمسار حسب الأقرب جغرافياً', 'إشعارات صوتية للمندوب أثناء القيادة'],
    constraints: ['Android Native (Kotlin & Jetpack Compose)', 'حماية صارمة للبطارية والذاكرة العشوائية'],
    verifiedFacts: ['تطبيق مناديب توصيل', 'مسح باركود بالكاميرا', 'توقيع إلكتروني وإثبات تسليم أوفلاين'],
    assumptions: ['استخدام تشفير محلي لبيانات وعناوين العملاء على جهاز المندوب'],
    prebuiltPrompt: `# البرومبت الهندسي الشامل: منظومة توصيل وتتبع مناديب (Fleet & Delivery Tracker)
- **المنصة المستهدفة:** Android Native (Kotlin 2.0 + Jetpack Compose)
- **المعمارية:** Clean Architecture + MVVM + Room Database + WorkManager
- **المكونات الأساسية:**
  1. \`DeliveryForegroundService\` لتتبع المسار وحفظ النقاط الجغرافية محلياً.
  2. مدمج مع \`Google ML Kit Barcode Scanning\` لمسح بوليصات الشحن دون إنترنت.
  3. لوحة رسم \`SignaturePad\` بـ Compose Canvas لحفظ توقيع المستلم كصورة مضغوطة.
  4. \`SyncWorker\` لمزامنة الشحنات المسلمة فور توفر اتصال بالخادم.`,
  },
  {
    id: 'bp-smart-erp',
    name: 'نظام محاسبة ومستودعات وفوترة إلكترونية (Smart ERP & ZATCA POS)',
    category: 'المالية والمحاسبة',
    type: 'full_app',
    targetPlatform: 'android_studio_ai',
    techStackLabel: 'Android Kotlin',
    description: 'نظام إدارة فواتير ضريبية (QR TLV)، جرد مخزني بالباركود، قيود يومية، وتقارير مالية عربية PDF.',
    rawIdea:
      'بناء نظام محاسبي ومخزني متكامل يعمل محلياً على أجهزة التابلت والهواتف، يصدر فواتير ضريبية برمز QR المتوافق مع هيئة الزكاة والضريبة، مع إدارة مستودعات وتقارير أرباح وخسائر.',
    problem: 'حاجة المنشآت المتوسطة والصغيرة لنظام محاسبي وفوترة يعمل بسرعة فائقة دون توقف عند انقطاع الإنترنت.',
    primaryGoal: 'إصدار الفواتير الضريبية وضبط المخزون والقيود المحاسبية محلياً بدقة 100% مع تقارير PDF عربية.',
    mustHave: [
      'محرك فوترة ضريبية يولّد رمز QR بصيغة TLV Base64 محلياً',
      'إدارة أصناف ومستودعات مع تنبيهات انخفاض المخزون وجرد بالكاميرا',
      'دفتر أستاذ عام وقيود محاسبية مزدوجة آلية لكل فاتورة',
      'تصدير وطباعة الفواتير وتقارير المبيعات بصيغة PDF عربية أو عبر طابعات البلوتوث',
    ],
    niceToHave: ['دعم تعدد الفروع والمستودعات', 'استيراد وتصدير الأصناف عبر ملفات CSV/Excel'],
    constraints: ['عدم السماح بحذف الفواتير المصدرة (إصدار إشعار دائن/مدين بدلاً من ذلك)'],
    verifiedFacts: ['فوترة إلكترونية برمز QR', 'جرد مخزني ومحاسبة', 'تصدير PDF عربي'],
    assumptions: ['حساب ضريبة القيمة المضافة آلياً بنسبة قابلة للضبط (مثل 15%)'],
    prebuiltPrompt: `# البرومبت الهندسي الشامل: نظام المحاسبة والمستودعات والفوترة الضريبية (Smart ERP)
- **المنصة المستهدفة:** Android Native (Kotlin & Jetpack Compose)
- **قاعدة البيانات:** Room Database مع دعم Transactions الصارمة للقيود المحاسبية.
- **المتطلبات الهندسية:**
  1. مولّد \`ZatcaQrGenerator\` بصيغة Tag-Length-Value (TLV) المشفرة بـ Base64.
  2. نظام جرد مخزني لحظي يخصم الكميات فور اعتماد الفاتورة.
  3. محرك تقارير مالي يصدّر كشف الحساب والفواتير إلى PDF عربي باتجاه RTL.`,
  },
  {
    id: 'bp-edtech-lms',
    name: 'منصة تعليمية تفاعلية مع تحميل الدروس (Offline-First EdTech LMS)',
    category: 'التعليم والتدريب',
    type: 'full_app',
    targetPlatform: 'claude_code',
    techStackLabel: 'React / Next.js',
    description: 'منصة دورات تدريبية، اختبارات ذكية، وشهادات إتمام مع دعم العمل التقدمي دون اتصال (PWA).',
    rawIdea:
      'منصة تعليمية عربية للدورات البرمجية والمهنية تتيح للطلاب تحميل الدروس والاختبارات لمشاهدتها وحلها بدون إنترنت، مع لوحة تحكم للمدربين وتتبع تقدم الطالب.',
    problem: 'ضعف اتصال الإنترنت يمنع الطلاب من متابعة الدروس المرئية أو إكمال الاختبارات التقييمية.',
    primaryGoal: 'توفير تجربة تعلم سلسة متصلة أو غير متصلة بالإنترنت مع تقييم فوري وتتبع دقيق للإنجاز.',
    mustHave: [
      'مشغل دروس تفاعلي مع حفظ موضع التوقف وتدوين ملاحظات زمنية',
      'محرك اختبارات (Quiz Engine) يعمل محلياً ويصحح الإجابات مع شرح الحلول',
      'لوحة متابعة تقدم الطالب ونسب الإنجاز وإصدار شهادات رقمية',
      'تخزين محلي للمحتوى النصي والاختبارات عبر IndexedDB',
    ],
    niceToHave: ['منتدى نقاش لكل درس', 'تحديات برمجية تفاعلية'],
    constraints: ['واجهة عربية RTL متجاوبة بالكامل للهواتف والحواسيب'],
    verifiedFacts: ['منصة دورات واختبارات', 'دعم العمل دون إنترنت', 'تتبع تقدم الطالب'],
    assumptions: ['تقسيم كل دورة إلى وحدات (Modules) ودروس قصيرة لسهولة التحميل'],
    prebuiltPrompt: `# البرومبت الهندسي الشامل: منصة تعليمية تفاعلية (Offline-First EdTech LMS)
- **المنصة المستهدفة:** React + TypeScript + Tailwind CSS (PWA Local-First)
- **المعمارية:** معمارية معيارية مع تخزين محلي IndexedDB للمقررات والاختبارات.
- **المكونات الأساسية:**
  1. مستعرض دورات ووحدات تعليمية باتجاه RTL عربي احترافي.
  2. محرك اختبارات تفاعلي يحفظ محاولات الطالب محلياً ويزامنها عند الاتصال.
  3. مولّد شهادات إتمام رقمية قابلة للطباعة والتحقق.`,
  },
];

export function createProjectFromBlueprint(bp: IndustryBlueprint): Project {
  const nowIso = new Date().toISOString();
  return {
    id: `proj-bp-${Date.now()}`,
    name: bp.name,
    description: bp.description,
    type: bp.type,
    category: bp.category,
    tags: [bp.category, bp.techStackLabel, 'Blueprint'],
    targetPlatform: bp.targetPlatform,
    detailLevel: 'comprehensive',
    rawIdea: bp.rawIdea,
    isPinned: true,
    isArchived: false,
    createdAt: nowIso,
    updatedAt: nowIso,
    analysis: {
      problem: bp.problem,
      primaryGoal: bp.primaryGoal,
      subGoals: bp.mustHave.slice(0, 3),
      targetAudience: `قطاع ${bp.category} والمستخدمون النهائيون`,
      functionalReqs: {
        mustHave: bp.mustHave,
        niceToHave: bp.niceToHave,
      },
      nonFunctionalReqs: {
        performance: ['استجابة فورية للواجهة بأقل من 100ms', 'استهلاك ذاكرة منضبط'],
        security: ['تشفير البيانات الحساسة محلياً', 'التحقق الصارم من المدخلات'],
        offline: ['العمل الكامل دون إنترنت (Local-First) مع مزامنة تلقائية عند توفر الشبكة'],
      },
      constraints: bp.constraints,
      ambiguities: [],
      assumptions: bp.assumptions,
      verifiedFacts: bp.verifiedFacts,
      clarifyingQuestions: ['هل ترغب في إضافة تكامل مع خدمة إشعارات خارجية في المرحلة الثانية؟'],
    },
    prompts: [
      {
        id: `v1-${Date.now()}`,
        versionNumber: 1,
        timestamp: nowIso,
        promptText: bp.prebuiltPrompt,
        targetPlatform: bp.targetPlatform,
        detailLevel: 'comprehensive',
        notes: 'النسخة القياسية المعتمدة من القالب الهندسي',
      },
    ],
    currentPromptIndex: 0,
  };
}
