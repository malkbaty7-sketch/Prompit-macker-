import { Project, LocalModel, IdeaAnalysis } from '../types';
import { INITIAL_MODELS } from './modelCatalog';
import { safeStorage } from './safeStorage';

const STORAGE_KEY_PROJECTS = 'forgeprom_projects_v2';
const STORAGE_KEY_MODELS = 'forgeprom_models_v2';

const SAMPLE_ANALYSIS_POS: IdeaAnalysis = {
  problem: 'انقطاع شبكة الإنترنت في المتاجر والمستودعات يعطل عمليات البيع وتسجيل الفواتير، مما يؤدي لفقدان المبيعات وتأخر العمليات.',
  primaryGoal: 'بناء تطبيق نقطة بيع (POS) وإدارة مخزون أصلي لأجهزة أندرويد يعمل بنسبة 100% دون إنترنت مع مزامنة سحابية اختيارية.',
  subGoals: [
    'تسجيل المبيعات وطباعة الإيصالات عبر طابعة بلوتوث دون أي اتصال شبكي',
    'مسح الباركود باستخدام كاميرا الهاتف عبر ML Kit المحلي',
    'إدارة المخزون وتنبيهات انخفاض الكميات وقاعدة بيانات Room محلية مشفرة',
  ],
  targetAudience: 'أصحاب المتاجر الصغيرة والمتوسطة، المندوبون الميدانيون، ومسؤولو المستودعات.',
  functionalReqs: {
    mustHave: [
      'إنشاء وحفظ الفواتير محلياً في قاعدة بيانات Room',
      'مسح الباركود QR / EAN13 باستخدام مكتبة Google ML Kit Barcode Scanning',
      'طباعة الفاتورة عبر طابعة حرارية Bluetooth ESC/POS',
      'لوحة تحكم للمبيعات اليومية والأصناف الأكثر مبيعاً محلياً',
    ],
    niceToHave: [
      'تصدير تقرير المبيعات بصيغة Excel / PDF ومشاركته عبر واتساب',
      'مزامنة سحابية اختيارية مشفرة عند توفر اتصال Wi-Fi بموافقة المستخدم',
    ],
  },
  nonFunctionalReqs: {
    performance: ['استجابة الواجهة وإتمام عملية البيع في أقل من 500ms'],
    security: ['تشفير قاعدة البيانات المحلية باستخدام SQLCipher والنسخ الاحتياطي المشفر'],
    offline: ['سياسة Local-First مطلقة: التطبيق يعمل بكامل طاقته في وضع الطيران'],
  },
  constraints: [
    'عدم الاعتماد على أي خادم سحابي للوظائف الحيوية',
    'العمل على أجهزة أندرويد بدءاً من Android 8.0 (API 26) برام 4GB',
  ],
  ambiguities: [
    'طريقة تسوية التعارضات (Conflict Resolution) في حال المزامنة مع أكثر من جهاز مستقبلاً',
  ],
  assumptions: [
    'المستخدم سيستخدم طابعة حرارية قياسية تدعم بروتوكول ESC/POS عبر البلوتوث',
    'العملة الافتراضية يمكن تحديدها من إعدادات التطبيق دون الحاجة لاتصال بالإنترنت',
  ],
  verifiedFacts: [
    'المشروع يحتاج لتطبيق أندرويد أصلي يعمل محلياً بالكامل',
    'الوظائف الأساسية هي إدارة المخزون وتسجيل المبيعات السريعة',
  ],
  clarifyingQuestions: [
    'هل تحتاج إلى دعم مسح باركود المنتجات متعددة الدفعات (Batches) وتواريخ الصلاحية؟',
    'ما هي فئات الضرائب المعتمدة وطريقة تطبيقها على الفاتورة؟',
  ],
};

const SAMPLE_PROJECTS: Project[] = [
  {
    id: 'proj_offline_pos_001',
    name: 'تطبيق نقطة البيع والمخزون بدون إنترنت (ForgePOS)',
    description: 'نظام نقاط بيع وإدارة مخزون أصلي لأندرويد (Kotlin + Compose + Room) يعمل 100% في وضع الطيران مع طباعة بلوتوث ومسح باركود.',
    type: 'full_app',
    category: 'التجارة والأعمال',
    tags: ['Android', 'Kotlin', 'Compose', 'Room', 'Offline-First', 'MLKit'],
    targetPlatform: 'android_studio_ai',
    detailLevel: 'comprehensive',
    isPinned: true,
    isArchived: false,
    createdAt: '2026-11-08T09:30:00Z',
    updatedAt: '2026-11-09T08:15:00Z',
    rawIdea: 'أريد تطبيق نقاط بيع سريع للمحلات الصغيرة يسجل المبيعات ويطبع الفواتير بباركود ويعمل في وضع الطيران تماماً دون نت مع قاعدة بيانات Room وإدارة المخزون.',
    analysis: SAMPLE_ANALYSIS_POS,
    prompts: [
      {
        id: 'prompt_v1',
        versionNumber: 1,
        timestamp: '2026-11-09T08:15:00Z',
        targetPlatform: 'android_studio_ai',
        detailLevel: 'comprehensive',
        promptText: `# الدور وسياق المشروع
أنت كبير مهندسي تطبيقات أندرويد (Senior Android Engineer). مهمتك بناء تطبيق Android أصلي بالكامل باسم **ForgePOS** باستخدام **Kotlin** و **Jetpack Compose** وفق معمارية **Clean Architecture (MVVM/MVI)**.

# الهدف الأساسي
تطبيق نقاط بيع (POS) وإدارة مخزون محلي أولاً (Local-First)، يعمل بنسبة 100% دون اتصال بالإنترنت (في وضع الطيران)، مع دعم كامل لاتجاه RTL واللغة العربية، ومسح الباركود، وطباعة الإيصالات.

# المتطلبات الوظيفية الصارمة
1. **قاعدة البيانات المحلية (Room):**
   - جدول المنتجات \`ProductEntity\` (id, barcode, name, price, stockQuantity, minAlertStock, category).
   - جدول الفواتير \`InvoiceEntity\` (id, invoiceNumber, timestamp, totalAmount, discount, paymentMethod).
   - جدول بنود الفاتورة \`InvoiceItemEntity\` (id, invoiceId, productId, quantity, unitPrice).
2. **سلة البيع السريعة:**
   - واجهة شاشة البيع بـ Jetpack Compose مع شبكة المنتجات وسلة مشتريات ديناميكية.
   - مسح الباركود السريع عبر Google ML Kit Barcode Scanning.
3. **الطباعة الحرارية البلوتوث:**
   - خدمة تواصل Bluetooth SPP لإرسال أوامر ESC/POS وتوليد الفاتورة العربية بصيغة صورة Bitmap تجنباً لتشوه الحروف العربية في الطابعات المدمجة.

# القيود الصارمة وتعليمات الحماية
- يُمنع إدراج أي استدعاء شبكي في المسار الحرج للبيع (Critical Path).
- ممنوع مسح أو تعديل أي كود قائم في طبقة الـ Data دون كتابة ترحيل آمن (Room Migration).
- معالجة حالات نفاذ المخزون والمدفوعات المجزأة بدقة.

# معايير القبول (Acceptance Criteria)
- التطبيق يفتح ويعمل بالكامل في وضع الطيران.
- عملية تسجيل الفاتورة تستغرق أقل من 300ms.
- تمرير جميع اختبارات الوحدة للمخزون وحساب إجماليات الفواتير.`,
        customDirectives: 'التأكيد على معمارية Clean Architecture وتشفير قاعدة البيانات بـ SQLCipher.',
      },
    ],
    currentPromptIndex: 0,
    chainedPrd: {
      step1_prd: `## وثيقة رؤية المنتج (PRD) — ForgePOS
### 1. ملخص المنتج
ForgePOS هو نظام نقاط بيع مستقل وسريع للمتاجر الحرة، مصمم لمعالجة معضلة انقطاع الشبكة عبر الاعتماد الكامل على التخزين والمعالجة المحلية بنسبة 100%.

### 2. نطاق الـ MVP
- شاشة سلة البيع المباشر مع بحث ومسح بالباركود عبر Google ML Kit.
- إصدار الفاتورة وحفظها في قاعدة بيانات Room مع خيار الطباعة الحرارية عبر البلوتوث.
- شاشة إدارة المخزون وتنبيهات انخفاض الكميات.
- تصدير تقارير المبيعات بصيغ قياسية ومحمية (TXT / CSV / PDF).`,
      step2_architecture: `## المعمارية البرمجية وتقسيم الحزم (Clean Architecture)
- \`com.forgepos.app.core\`: الثوابت، الموزعات (Dispatchers)، وأدوات فحص العتاد والذاكرة.
- \`com.forgepos.app.data.local\`: قاعدة بيانات Room، الـ DAOs، الكيانات، وتشفير SQLCipher.
- \`com.forgepos.app.domain\`: نماذج النطاق وحالات الاستخدام (CreateInvoiceUseCase, ScanBarcodeUseCase, UpdateStockUseCase).
- \`com.forgepos.app.presentation\`: شاشات Jetpack Compose (Dashboard, Checkout, Inventory) وViewModel مع StateFlow.`,
      step3_screens_stories: `## مواصفات الشاشات وتدفقات المستخدم (Compose UI & Stories)
### 1. شاشة سلة المبيعات (SaleCheckoutScreen):
- شبكة المنتجات مع عدادات الكميات وبطاقة ملخص الحساب الصافي.
- تدفق المعاملة: اختيار الأصناف -> مسح الباركود -> الضغط على [إصدار وحفظ الفاتورة] -> إرسال أمر الطباعة.

### 2. قصص المستخدم ومعايير القبول:
- **ككاشير:** أريد تسجيل الفاتورة في أقل من ثانيتين بدون إنترنت حتى لا يتعطل الطابور.
  - *معيار القبول:* إتمام الحفظ في Room والتحقق من صحة الفاتورة في أقل من 250ms في وضع الطيران.`,
      step4_database_api: `## مخطط البيانات المحلي (Room Database Schema)
\`\`\`kotlin
@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String,
    @ColumnInfo(name = "total_amount") val totalAmount: Double,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoice_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "quantity") val quantity: Int
)
\`\`\``,
      step5_roadmap_migration: `## خطة التنفيذ المرحلية والاعتماديات (Roadmap & Migration)
### المرحلة 1: التأسيس والمعمارية (الأسبوع 1)
- إعداد بنية Clean Architecture، حقن Hilt، ونظام الألوان وRTL في Compose.
### المرحلة 2: قاعدة بيانات Room وطبقة البيانات (الأسبوع 2)
- كتابة الـ Entities والـ DAOs مع اختبارات SQLite محلية وتفعيل exportSchema = true.
### المرحلة 3: شاشات البيع والطباعة الحرارية (الأسبوع 3)
- شاشة سلة البيع، ربط ML Kit للباركود، وتوليد تذاكر الطباعة البلوتوث.
### المرحلة 4: اختبار الصمود في وضع الطيران والتصدير (الأسبوع 4)
- تشغيل التطبيق في وضع الطيران التام واختبار التصدير متعدد الصيغ وتجميع حزمة Release.`,
    },
  },
  {
    id: 'proj_clinic_manager_002',
    name: 'تطبيق إدارة العيادات والمواعيد الطبية (ForgeClinic)',
    description: 'نظام إدارة عيادات تخصصية وسجلات مرضى ومواعيد أوفلاين 100% لأندرويد مع تشفير البيانات وتوليد الوصفات الطبية PDF.',
    type: 'full_app',
    category: 'الرعاية الصحية والطب',
    tags: ['Android', 'Kotlin', 'Compose', 'Room', 'SQLCipher', 'Local-First'],
    targetPlatform: 'android_studio_ai',
    detailLevel: 'comprehensive',
    isPinned: false,
    isArchived: false,
    createdAt: '2026-11-10T12:00:00Z',
    updatedAt: '2026-11-11T05:00:00Z',
    rawIdea: 'أريد بناء تطبيق إدارة عيادات ومواعيد مرضى لأجهزة أندرويد، يعمل بدون إنترنت نهائياً لحفظ سجلات المرضى والوصفات الطبية في قاعدة بيانات Room مشفرة، مع طباعة التذاكر عبر البلوتوث، وتصدير السجلات بصيغة PDF مشفرة.',
    analysis: {
      problem: 'انقطاع الشبكة وضعف التغطية في المراكز الصحية والعيادات يهدد سرية السجلات ويعطل استقبال الحالات الطارئة.',
      primaryGoal: 'بناء تطبيق أندرويد أصلي لإدارة العيادات وسجلات المرضى والمواعيد يعمل محلياً بالكامل (Local-First) دون اشتراط وجود إنترنت.',
      subGoals: [
        'تسجيل المرضى وإدارة ملفاتهم الطبية السابقة والتشخيصات اليومية',
        'جدولة المواعيد والتذكيرات المحلية وتتبع حالات الانتظار',
        'توليد وتصدير الوصفات الطبية بصيغة PDF عربية رسمية مع التشفير',
      ],
      targetAudience: 'الأطباء، مساعدو العيادات، والمراكز الطبية الميدانية.',
      functionalReqs: {
        mustHave: [
          'ملف مريض إلكتروني محلي مشفر بقاعدة بيانات Room و SQLCipher',
          'جدول مواعيد تفاعلي يفرز الحالات حسب الوقت وحالة الكشف',
          'محرر وصفات طبية يتيح اختيار الأدوية والجرعات والتكرار',
          'طباعة تذكرة الكشف أو الوصفة عبر طابعة حرارية بلوتوث',
        ],
        niceToHave: [
          'تصدير سجل المريض الكامل بملف PDF مشفر بكلمة مرور',
          'نسخ احتياطي محلي مشفر على بطاقة SD أو الذاكرة الخارجية',
        ],
      },
      nonFunctionalReqs: {
        performance: ['فتح ملف المريض واستعراض السجل في أقل من 150ms'],
        security: ['تشفير ملف قاعدة البيانات بمعيار AES-256 عبر SQLCipher'],
        offline: ['عمل كافة الشاشات الحيوية في وضع الطيران بنسبة 100%'],
      },
      constraints: [
        'الاعتماد الحصري على لغة Kotlin 100% و Jetpack Compose',
        'حظر أي تسريب للبيانات الطبية إلى خوادم خارجية دون موافقة صريحة',
      ],
      ambiguities: [
        'لم يُحدد نوع طابعات البلوتوث المفضلة في العيادة (ESC/POS 58mm أم 80mm).',
      ],
      assumptions: [
        'افتراض أن الطبيب يستخدم هاتفاً متوسطاً أو لوحياً (Tablet) برام 6GB-8GB',
        'افتراض أن الوصفات الطبية تعتمد صيغة موحدة باللغة العربية والإنجليزية',
      ],
      verifiedFacts: [
        'ذكر المستخدم صراحة: العمل بدون إنترنت نهائياً لحفظ سجلات المرضى',
        'ذكر المستخدم صراحة: استخدام قاعدة بيانات Room مشفرة وتصدير PDF وطباعة بلوتوث',
      ],
      clarifyingQuestions: [
        'هل ترغب في دعم إرفاق صور الأشعة والتحاليل من كاميرا الهاتف بالملف الطبي؟',
      ],
    },
    prompts: [
      {
        id: 'clinic_prompt_v1',
        versionNumber: 1,
        timestamp: '2026-11-11T05:00:00Z',
        targetPlatform: 'android_studio_ai',
        detailLevel: 'comprehensive',
        promptText: `# الدور والهوية البرمجية (Role & Identity)
أنت كبير مهندسي برمجيات أندرويد (Staff Android Engineer) موجه لمنصة Android Studio AI لبناء نظام **ForgeClinic** الطبي.

# سياق المشروع والمواصفات (Context)
- **المشكلة:** الحفاظ على استمرارية العمل الطبي وسرية المرضى دون اعتماد على السحابة.
- **الهدف الرئيسي:** نظام إدارة عيادات متكامل يعمل 100% في وضع الطيران.
- **المكدس التقني:** Kotlin 100% + Jetpack Compose + Room + SQLCipher + Hilt DI + Coroutines.

# المتطلبات الوظيفية الصارمة (Functional Requirements)
1. **قاعدة البيانات الطبية المشفرة (Room + SQLCipher):**
   - كيان المريض \`PatientEntity\` (id, fullName, nationalId, phone, birthDate, bloodType, chronicDiseases).
   - كيان الموعد \`AppointmentEntity\` (id, patientId, appointmentTime, status, notes).
   - كيان الزيارة والوصفة \`VisitEntity\` (id, patientId, diagnosis, prescriptionJson, visitDate).
2. **شاشات Compose التفاعلية:**
   - شاشة استقبال المريض وتعديل السجل مع التحقق الفوري من المدخلات.
   - شاشة جدول المواعيد اليومية مع تحديث الحالة (في الانتظار، جاري الكشف، مكتمل).
   - محرر الوصفات الطبية مع خيار التصدير والطباعة.

# القيود الصارمة وتعليمات الحماية (Strict Guardrails)
- **سياسة عدم الاتصال (Offline-First):** حظر أي استدعاء خارجي، وضمان عمل النظام بالكامل في وضع الطيران.
- **الأمان والخصوصية:** تشفير قاعدة البيانات ومنع ظهور السجلات الطبية في سجلات Logcat.
- **سلامة الكود:** كتابة الأكواد كاملة ومنع استخدام تعليقات الاختصار "// rest of code".

# معايير القبول (Acceptance Criteria)
- [ ] التطبيق يترجم بنجاح (\`./gradlew assembleDebug\`) دون أخطاء.
- [ ] فتح السجل الطبي والبحث يتم في أقل من 200ms.
- [ ] دعم اتجاه RTL للنصوص العربية والمختلطة بدقة.`,
        customDirectives: 'التركيز على سرية البيانات الطبية واستخدام Clean Architecture.',
      },
    ],
    currentPromptIndex: 0,
    chainedPrd: {
      step1_prd: `## وثيقة رؤية المنتج (PRD) — ForgeClinic
### 1. ملخص المنتج
ForgeClinic هو نظام إدارة عيادات تخصصية وسجلات مرضى مصمم للعمل المحلي المعزول بنسبة 100% في وضع الطيران لحماية أسرار المرضى وضمان استمرارية التشخيص في أي ظرف.

### 2. نطاق الـ MVP
- تسجيل وإدارة ملفات المرضى وتاريخهم المرضي.
- حجز وإدارة مواعيد العيادة اليومية.
- إصدار الوصفات الطبية وطباعتها حرارياً وتصديرها كـ PDF مشفر.`,
      step2_architecture: `## المعمارية التقنية وتقسيم الحزم
- \`com.forgeclinic.app.core\`: أدوات التشفير AES-256، وموزعات خيوط المعالجة، والسمات.
- \`com.forgeclinic.app.data\`: قاعدة بيانات Room المشفرة بـ SQLCipher، وDAOs المواعيد والمرضى.
- \`com.forgeclinic.app.domain\`: حالات الاستخدام (RegisterPatientUseCase, BookAppointmentUseCase, IssuePrescriptionUseCase).
- \`com.forgeclinic.app.presentation\`: شاشات Compose (PatientsList, AppointmentsAgenda, MedicalRecord) وViewModel.`,
      step3_screens_stories: `## مواصفات الشاشات وتدفقات المستخدم
### 1. شاشة المواعيد والسجلات (PatientsAgendaScreen):
- قائمة المواعيد اليومية مع بطاقات ملونة حسب حالة المريض ومؤقت الكشف.
### 2. قصة المستخدم ومعيار القبول:
- **كالطبيب:** أريد استعراض التشخيصات والوصفات السابقة للمريض فور فتح ملفه وبدون انتظار اتصال بالشبكة.
  - *معيار القبول:* فتح السجل واستخراج الأدوية السابقة في أقل من 150ms في وضع الطيران.`,
      step4_database_api: `## مخطط البيانات المحلي (Room Database Schema)
\`\`\`kotlin
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "blood_type") val bloodType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
\`\`\``,
      step5_roadmap_migration: `## خطة التنفيذ المرحلية (Roadmap)
- **الأسبوع 1:** إعداد البنية المشفرة بقاعدة بيانات Room و SQLCipher مع تفعيل exportSchema.
- **الأسبوع 2:** بناء طبقة Domain ومستودع البيانات السريرية.
- **الأسبوع 3:** بناء واجهات Jetpack Compose الطبية وتنسيق الوصفات RTL.
- **الأسبوع 4:** اختبار وضع الطيران وتصدير تقارير الـ PDF واختبار ترحيل البيانات.`,
    },
  },
];

export class AppStorage {
  static getProjects(): Project[] {
    const raw = safeStorage.getItem(STORAGE_KEY_PROJECTS);
    if (!raw) {
      this.saveProjects(SAMPLE_PROJECTS);
      return SAMPLE_PROJECTS;
    }
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed) || parsed.length === 0) {
        this.saveProjects(SAMPLE_PROJECTS);
        return SAMPLE_PROJECTS;
      }
      // Ensure default showcase projects (like ForgeClinic) are present
      SAMPLE_PROJECTS.forEach(sample => {
        if (!parsed.some((p: any) => p.id === sample.id)) {
          parsed.push(sample);
        }
      });
      return parsed.map((p: any) => ({
        ...p,
        id: p.id || `proj_${Date.now()}`,
        name: p.name || 'مشروع بدون عنوان',
        description: p.description || '',
        type: p.type || 'full_app',
        category: p.category || 'عام',
        tags: Array.isArray(p.tags) ? p.tags : [],
        targetPlatform: p.targetPlatform || 'android_studio_ai',
        detailLevel: p.detailLevel || 'comprehensive',
        isPinned: Boolean(p.isPinned),
        isArchived: Boolean(p.isArchived),
        createdAt: p.createdAt || new Date().toISOString(),
        updatedAt: p.updatedAt || new Date().toISOString(),
        rawIdea: p.rawIdea || '',
        prompts: Array.isArray(p.prompts) ? p.prompts : [],
        currentPromptIndex: typeof p.currentPromptIndex === 'number' ? p.currentPromptIndex : 0,
      }));
    } catch {
      return SAMPLE_PROJECTS;
    }
  }

  static saveProjects(projects: Project[]): void {
    safeStorage.setItem(STORAGE_KEY_PROJECTS, JSON.stringify(projects));
  }

  static getProject(id: string): Project | undefined {
    const projects = this.getProjects();
    return projects.find(p => p.id === id);
  }

  static saveProject(project: Project): void {
    const projects = this.getProjects();
    const index = projects.findIndex(p => p.id === project.id);
    if (index >= 0) {
      projects[index] = { ...project, updatedAt: new Date().toISOString() };
    } else {
      projects.unshift({ ...project, updatedAt: new Date().toISOString() });
    }
    this.saveProjects(projects);
  }

  static deleteProject(id: string): void {
    const projects = this.getProjects().filter(p => p.id !== id);
    this.saveProjects(projects);
  }

  static duplicateProject(id: string): Project | undefined {
    const project = this.getProject(id);
    if (!project) return undefined;

    const duplicated: Project = {
      ...project,
      id: `proj_${Date.now()}`,
      name: `${project.name} (نسخة)`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      isPinned: false,
    };

    this.saveProject(duplicated);
    return duplicated;
  }

  static getModels(): LocalModel[] {
    const raw = safeStorage.getItem(STORAGE_KEY_MODELS);
    if (!raw) {
      this.saveModels(INITIAL_MODELS);
      return INITIAL_MODELS;
    }
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed) || parsed.length === 0) {
        this.saveModels(INITIAL_MODELS);
        return INITIAL_MODELS;
      }
      return parsed.map((m: any) => ({
        ...m,
        id: m.id || `model_${Date.now()}`,
        name: m.name || 'Local Model',
        family: m.family || 'GGUF',
        parameters: m.parameters || '1.5B',
        format: m.format || 'GGUF Q4_K_M',
        fileSizeMB: typeof m.fileSizeMB === 'number' ? m.fileSizeMB : 986,
        requiredRamMB: typeof m.requiredRamMB === 'number' ? m.requiredRamMB : 1235,
        targetPhoneRam: m.targetPhoneRam || '4GB',
        arabicQualityRating: typeof m.arabicQualityRating === 'number' ? m.arabicQualityRating : 4,
        status: m.status || 'available',
        sha256Expected: m.sha256Expected || 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        description: m.description || '',
        strengths: Array.isArray(m.strengths) ? m.strengths : [],
      }));
    } catch {
      return INITIAL_MODELS;
    }
  }

  static saveModels(models: LocalModel[]): void {
    safeStorage.setItem(STORAGE_KEY_MODELS, JSON.stringify(models));
  }

  static updateModel(updated: LocalModel): void {
    const models = this.getModels();
    const idx = models.findIndex(m => m.id === updated.id);
    if (idx >= 0) {
      models[idx] = updated;
    } else {
      models.push(updated);
    }
    this.saveModels(models);
  }

  static exportAllData(): string {
    const data = {
      version: '2.0.0',
      exportedAt: new Date().toISOString(),
      projects: this.getProjects(),
      models: this.getModels(),
    };
    return JSON.stringify(data, null, 2);
  }

  static importData(jsonString: string): boolean {
    try {
      const data = JSON.parse(jsonString);
      if (Array.isArray(data.projects)) {
        this.saveProjects(data.projects);
      }
      if (Array.isArray(data.models)) {
        this.saveModels(data.models);
      }
      return true;
    } catch (e) {
      console.error('Failed to import data', e);
      return false;
    }
  }

  static clearAll(): void {
    safeStorage.removeItem(STORAGE_KEY_PROJECTS);
    safeStorage.removeItem(STORAGE_KEY_MODELS);
  }
}
