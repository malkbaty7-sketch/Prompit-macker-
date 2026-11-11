import { IdeaAnalysis, QualityReport, QualityIssue, TargetPlatform, DetailLevel } from '../types';

export class LocalAiEngine {
  /**
   * Ensure any IdeaAnalysis object (from server or storage) has all required arrays initialized.
   */
  static normalizeAnalysis(raw?: Partial<IdeaAnalysis>): IdeaAnalysis {
    return {
      problem: raw?.problem || 'الحاجة إلى بناء نظام محلي موثوق وعالي الكفاءة.',
      primaryGoal: raw?.primaryGoal || 'تطوير حل تقني متكامل يعمل بكفاءة دون إنترنت.',
      subGoals: Array.isArray(raw?.subGoals) ? raw!.subGoals : [],
      targetAudience: raw?.targetAudience || 'المستخدمون وأصحاب المشاريع.',
      functionalReqs: {
        mustHave: Array.isArray(raw?.functionalReqs?.mustHave) ? raw!.functionalReqs!.mustHave : [],
        niceToHave: Array.isArray(raw?.functionalReqs?.niceToHave) ? raw!.functionalReqs!.niceToHave : [],
      },
      nonFunctionalReqs: {
        performance: Array.isArray(raw?.nonFunctionalReqs?.performance) ? raw!.nonFunctionalReqs!.performance : [],
        security: Array.isArray(raw?.nonFunctionalReqs?.security) ? raw!.nonFunctionalReqs!.security : [],
        offline: Array.isArray(raw?.nonFunctionalReqs?.offline) ? raw!.nonFunctionalReqs!.offline : [],
      },
      constraints: Array.isArray(raw?.constraints) ? raw!.constraints : [],
      ambiguities: Array.isArray(raw?.ambiguities) ? raw!.ambiguities : [],
      assumptions: Array.isArray(raw?.assumptions) ? raw!.assumptions : [],
      verifiedFacts: Array.isArray(raw?.verifiedFacts) ? raw!.verifiedFacts : [],
      clarifyingQuestions: Array.isArray(raw?.clarifyingQuestions) ? raw!.clarifyingQuestions : [],
    };
  }

  /**
   * Offline Idea Analyzer: Uses rule-based NLP heuristics to parse raw Arabic or mixed text
   * into structured technical specifications, strictly separating facts from assumptions.
   */
  static analyzeIdeaOffline(ideaText: string, platform: string = 'Android (Kotlin & Jetpack Compose)'): IdeaAnalysis {
    const text = ideaText.trim();
    const sentences = text.split(/[.\n!؟?]+/).map(s => s.trim()).filter(Boolean);

    // Verified facts are taken directly from user statements
    const verifiedFacts = sentences.slice(0, 3).map(s => `ذكر المستخدم صراحة: "${s}"`);
    if (verifiedFacts.length === 0) {
      verifiedFacts.push('تم استخراج الفكرة من الوصف الأولي دون تفاصيل فرعية محددة.');
    }

    // Heuristics for goals and problem
    const primaryGoal = `تطوير تطبيق ${platform} عالي الكفاءة يحل مشكلة: ${sentences[0] || text.substring(0, 80)}`;
    const problem = sentences.length > 1
      ? `التحدي القائم يتمثل في: ${sentences[1]}، مما يستدعي بناء نظام محلي مستقل وموثوق.`
      : `الحاجة إلى أتمتة وتسهيل هذه العملية عبر حل تقني محلي وسريع على أندرويد.`;

    const subGoals = [
      'بناء واجهات تفاعلية سلسة تدعم اتجاه RTL والوضع الداكن باستخدام Jetpack Compose',
      'حفظ ومعالجة كافة البيانات محلياً عبر قاعدة بيانات Room دون اشتراط وجود اتصال شبكي',
      'إتاحة إمكانية التصدير والمشاركة السريعة بصيغ قياسية ومحمية',
    ];

    // Must-have vs Nice-to-have
    const mustHave = [
      'إدارة العمليات الأساسية وإجراءات الإدخال والتعديل والحذف (CRUD)',
      'تخزين محلي معزول مع دعم الاستعلامات السريعة والفهرسة',
      'الاستجابة الفورية للمدخلات مع التحقق من صحة البيانات (Form Validation)',
      'حفظ تلقائي للمسودات وحماية تجربة المستخدم من فقدان البيانات',
    ];

    const niceToHave = [
      'تصدير التقارير بصيغة PDF مع دعم الخطوط العربية الأصيلة',
      'مزامنة سحابية اختيارية مشفرة عند الرغبة مع معالجة التعارضات',
      'إحصائيات ورسوم بيانية محلية تلخص النشاط',
    ];

    // Explicit Assumptions that the user can edit
    const assumptions = [
      'افتراض أن التطبيق يستهدف هواتف أندرويد متوسطة المواصفات (ذاكرة عشوائية 4GB إلى 8GB)',
      'افتراض أن التخزين المحلي كافٍ ولا يتطلب مساحات خارجية استثنائية في المرحلة الأولى',
      'افتراض أن المصادقة البيومترية أو حماية البصمة اختيارية يمكن تفعيلها من الإعدادات',
    ];

    const clarifyingQuestions = [
      'هل هناك متطلبات خاصة لمزامنة البيانات مستقبلاً بين أكثر من جهاز للمستخدم نفسه؟',
      'ما هي الصيغ الخارجية التي ترغب في استيراد البيانات منها أو التصدير إليها؟',
    ];

    return {
      problem,
      primaryGoal,
      subGoals,
      targetAudience: 'المستخدمون وأصحاب المشاريع والتقنيون الباحثون عن أداء سريع ومستقل دون إنترنت.',
      functionalReqs: {
        mustHave,
        niceToHave,
      },
      nonFunctionalReqs: {
        performance: ['سرعة استجابة الشاشات أقل من 200ms مع معدل إطارات 60fps'],
        security: ['عزل البيانات المحلية وتشفير المعاملات الحساسة'],
        offline: ['تشغيل كامل لكافة الوظائف الحيوية في وضع الطيران (100% Local-First)'],
      },
      constraints: [
        'بناء التطبيق بلغة Kotlin بنسبة 100% مع Jetpack Compose',
        'الالتزام بمعمارية Clean Architecture (Data, Domain, Presentation)',
      ],
      ambiguities: [
        'لم يُحدد نوع وشكل التقارير المطلوبة بالتفصيل في الوصف الأولي.',
      ],
      assumptions,
      verifiedFacts,
      clarifyingQuestions,
    };
  }

  /**
   * Offline Standardized Prompt Generator
   */
  static generatePromptOffline(
    analysis: IdeaAnalysis,
    targetPlatform: TargetPlatform,
    detailLevel: DetailLevel,
    customDirectives?: string
  ): string {
    const safeAnalysis = this.normalizeAnalysis(analysis);
    const platformLabels: Record<TargetPlatform, string> = {
      android_studio_ai: 'Android Studio AI / Gemini Code Assist',
      cursor: 'Cursor AI (Claude / GPT-4)',
      claude_code: 'Claude Code CLI',
      bolt: 'Bolt.new / WebContainer',
      lovable: 'Lovable AI Engineer',
      windsurf: 'Windsurf Cascade',
      general: 'مساعد برمجي ذكي عام',
    };

    const targetLabel = platformLabels[targetPlatform] || targetPlatform;

    return `# 1. الدور والهوية البرمجية (Role & Identity)
أنت كبير مهندسي البرمجيات (Staff Software Architect) المتخصص في بناء تطبيقات Android الأصيلة والموجه لمنصة: **${targetLabel}**.

# 2. سياق المشروع والمواصفات (Context)
- **المشكلة:** ${safeAnalysis.problem}
- **الهدف الرئيسي:** ${safeAnalysis.primaryGoal}
- **الفئة المستهدفة:** ${safeAnalysis.targetAudience}
- **المكدس التقني المعتمد:** Kotlin 100% + Jetpack Compose + Material 3 + Room Database + Hilt DI + Coroutines & Flow.

# 3. المتطلبات الوظيفية المنظمة (Functional Requirements)
## المتطلبات الإلزامية (Must-have):
${safeAnalysis.functionalReqs.mustHave.map((r, i) => `${i + 1}. ${r}`).join('\n')}

## المتطلبات التحسينية (Nice-to-have):
${safeAnalysis.functionalReqs.niceToHave.map((r) => `- ${r}`).join('\n')}

# 4. القيود الصارمة وتعليمات الحماية (Strict Guardrails)
- **سياسة عدم الاتصال (Offline-First):** يجب أن تعمل كافة الميزات الأساسية بكفاءة تامة في وضع الطيران (Airplane Mode) دون أي اعتماد على خوادم خارجية.
- **سلامة الكود القائم:** يُمنع منعاً باتاً مسح أو إعادة تسمية أي دالة أو واجهة مستخدم قائمة دون كتابة ترحيل صريح.
- **سلامة الذاكرة:** إدارة الذاكرة بصرامة ومنع تسريب الذاكرة (Memory Leaks) عبر Coroutines Cancellation.
- **التوجيهات المخصصة:** ${customDirectives || 'الالتزام باتجاه RTL ودعم النصوص العربية والمختلطة بسلاسة.'}

# 5. خطة التنفيذ المرحلية خطوة بخطوة (Step-by-Step Implementation)
1. **الطبقة الأساسية (Core & Data):** إنشاء كيانات Room DAOs وقاعدة البيانات مع خطة ترحيل آمنة.
2. **طبقة النطاق (Domain Layer):** بناء نماذج الأعمال وحالات الاستخدام (UseCases) وواجهات المستودعات (Repository Interfaces).
3. **طبقة العرض (Presentation):** بناء شاشات Jetpack Compose مع نمط MVI/MVVM وإدارة الحالة عبر StateFlow.
4. **الربط والتحقق:** اختبار تدفق البيانات والحفظ التلقائي في وضع الطيران.

# 6. معايير القبول الصارمة (Acceptance Criteria)
- [ ] التطبيق يترجم بنجاح (\`./gradlew assembleDebug\`) دون أي أخطاء أو تحذيرات مهملة.
- [ ] عمل الشاشات بنسبة 100% دون اتصال بالإنترنت.
- [ ] دعم اتجاه اليمين إلى اليسار (RTL) بشكل مثالي للنصوص العربية والأرقام.
- [ ] خلو التطبيق من التجمد والتقطيع أثناء العمليات الثقيلة.

# 7. شكل التسليم المطلوب (Deliverables)
قدم الأكواد كاملة ومكتملة دون استخدام تعليقات الاختصار (مثل \`// ... rest of code\`).`;
  }

  /**
   * Stage 7: Optimize Existing Weak Prompt Offline (أداة تحسين برومبت قائم)
   */
  static optimizePromptOffline(
    rawPrompt: string,
    targetPlatform: TargetPlatform = 'android_studio_ai'
  ): { optimizedPrompt: string; improvementsMade: string[] } {
    const cleanInput = rawPrompt.trim();
    const optimizedPrompt = `# 1. الدور (Role)
أنت مهندس برمجيات أندرويد أول (Senior Android Architect) متخصص في Kotlin وJetpack Compose ومعمارية Clean Architecture، وتعمل ضمن بيئة (${targetPlatform}).

# 2. الهدف والمقصود الأساسي للمستخدم (Core Objective)
تنفيذ الطلب التالي بدقة هندسية كاملة دون تحريف:
> "${cleanInput}"

# 3. المتطلبات الوظيفية المستنبطة (Functional Requirements)
1. تنفيذ الوظيفة المطلوبة أعلاه بكود Kotlin نقي ومتوافق مع Android SDK 26+.
2. بناء واجهة مستخدم متجاوبة باستخدام Jetpack Compose (Material 3) تدعم اتجاه RTL والوضع الداكن.
3. حفظ الحالة محلياً عبر ViewModel وStateFlow وقاعدة بيانات Room عند الحاجة.
4. تغطية كافة حالات الشاشة: (التحميل Loading، الفراغ Empty، الخطأ Error، والنجاح Success).

# 4. القيود الصارمة وتعليمات الحماية (Strict Guardrails)
- **حماية الكود القائم:** يُمنع حذف أو تعطيل أي ميزة أو دالة قائمة في المشروع.
- **العمل المحلي (Local-First):** لا تفترض وجود اتصال بالإنترنت للوظائف الأساسية.
- **إدارة الذاكرة:** منع تسريب الذاكرة (Memory Leaks) واستخدام \`viewModelScope\` لكافة العمليات غير المتزامنة.
- **منع الاختصار المخل:** اكتب الأكواد كاملة ولا تستخدم \`// TODO\` أو \`// rest of code\`.

# 5. خطة التنفيذ المرحلية (Step-by-Step Plan)
1. تحديد الكيانات ونماذج البيانات (Domain & Data Models).
2. بناء منطق الأعمال أو التعديل المطلوب في طبقة UseCase / ViewModel.
3. بناء أو تحديث مكونات الواجهة \`@Composable\` مع معالجة النصوص المختلطة (Bidi Text).
4. التحقق من سلامة البناء والتجميع.

# 6. معايير القبول (Acceptance Criteria)
- [ ] تحقيق مقصود المستخدم الأصلي بالكامل دون نقص.
- [ ] نجاح البناء (\`./gradlew assembleDebug\`) دون أخطاء تجميع.
- [ ] عمل الميزة بسلاسة في وضع الطيران (Airplane Mode).`;

    const improvementsMade = [
      'إضافة تحديد الدور الهندسي والبيئة المستهدفة لضبط جودة الكود الناتج',
      'الحفاظ الحرفي على مقصود المستخدم الأصلي داخل قسم الهدف الأساسي',
      'إضافة تعليمات الحماية الإلزامية لمنع مسح الوظائف الحالية أو اختصار الكود',
      'إضافة تغطية حالات الشاشة الأربع (Loading, Empty, Error, Success)',
      'إرفاق خطة تنفيذ مرحلية ومعايير قبول قابلة للتحقق الآلي',
    ];

    return { optimizedPrompt, improvementsMade };
  }

  /**
   * Rule-based Quality Review Engine (Stage 10)
   */
  static runQualityReviewOffline(promptText: string, _analysis?: IdeaAnalysis): QualityReport {
    const issues: QualityIssue[] = [];
    const text = promptText.toLowerCase();

    // 1. Check for acceptance criteria
    if (!promptText.includes('معايير القبول') && !text.includes('acceptance criteria')) {
      issues.push({
        id: 'issue_acceptance_criteria',
        severity: 'high',
        category: 'معايير القبول',
        title: 'غياب قسم معايير القبول الصريحة (Acceptance Criteria)',
        reason: 'البرومبت يفتقر إلى معايير نجاح محددة وقابلة للاختبار الآلي، مما قد يدفع النموذج الذكي للتوقف قبل إتمام الشروط.',
        suggestedFix: `\n\n# معايير القبول الإلزامية:\n- التطبيق يعمل 100% دون إنترنت في وضع الطيران.\n- نجاح التجميع بـ ./gradlew assembleDebug دون تحذيرات.\n- الحفظ التلقائي للمسودات بنجاح.`,
      });
    }

    // 2. Check for offline protection guardrails
    if (!promptText.includes('وضع الطيران') && !promptText.includes('offline') && !promptText.includes('دون إنترنت')) {
      issues.push({
        id: 'issue_offline_guardrail',
        severity: 'high',
        category: 'الأمان والأوفلاين',
        title: 'عدم التأكيد على قيد العمل بدون إنترنت (Local-First)',
        reason: 'لم يتم إلزام المساعد البرمجي بحظر الاعتماد على السحابة، وقد يقوم بتضمين استدعاءات API خارجية تكسر التطبيق عند انقطاع الشبكة.',
        suggestedFix: `\n\n# قيد الحماية المحلي (Local-First):\nيجب أن تعمل جميع العمليات دون أي اعتماد على خوادم خارجية واختبار ذلك في وضع الطيران.`,
      });
    }

    // 3. Check for destructive edit warnings
    if (!promptText.includes('يُمنع') && !promptText.includes('حظر') && !promptText.includes('سلامة الكود')) {
      issues.push({
        id: 'issue_safety_guardrails',
        severity: 'medium',
        category: 'الأمان وحماية الكود',
        title: 'غياب تعليمات الحماية من مسح الكود القائم',
        reason: 'نماذج الذكاء الاصطناعي قد تقوم بحذف وحدات قائمة أو استبدالها بتعليقات اختصار ما لم يتم تحذيرها صراحة.',
        suggestedFix: `\n\n# تعليمات حماية الكود:\nيُمنع مسح أو استبدال الأكواد القائمة بـ "// rest of code"، ويجب تقديم الكود كاملاً وسليماً.`,
      });
    }

    // 4. Check for RTL and Arabic support
    if (!promptText.includes('RTL') && !promptText.includes('عربي') && !promptText.includes('العربية')) {
      issues.push({
        id: 'issue_rtl_support',
        severity: 'medium',
        category: 'اكتمال المتطلبات',
        title: 'عدم النص على دعم اتجاه RTL والنصوص العربية',
        reason: 'قد يولد النموذج واجهات تفترض اتجاه LTR وتنهار بوجود كلمات إنجليزية داخل الجمل العربية.',
        suggestedFix: `\n\n# مواصفات الواجهة:\nدعم اتجاه RTL افتراضياً والتعامل السليم مع النصوص المختلطة (Bidi Text).`,
      });
    }

    // Calculate score
    const penalties = issues.reduce((acc, iss) => acc + (iss.severity === 'high' ? 15 : iss.severity === 'medium' ? 8 : 4), 0);
    const score = Math.max(50, 100 - penalties);

    const strengths = [
      'الهيكل العام واضح ويتبع التقسيم الهندسي المنطقي',
      'تحديد صريح لدور ومسؤولية المهندس الذكي',
      'تحديد حزمة الأدوات ومكتبات أندرويد المستهدفة',
    ];

    const overallAssessment = issues.length === 0
      ? 'برومبت متكامل ومستوفٍ لكافة المعايير الهندسية والأمنية وجاهز للتنفيذ الفوري.'
      : `البرومبت جيد ومترابط، ولكنه يحتاج إلى إضافة ${issues.length} تحسينات حاسمة لضمان أعلى دقة برمجية وتفادي أخطاء النماذج.`;

    return {
      overallAssessment,
      score,
      issues,
      strengths,
    };
  }

  /**
   * Offline UI Image Layout Analyzer (ML Kit Simulation - Stage 9)
   */
  static analyzeUiImageOffline(_imageName: string = 'screen_preview.png'): {
    screenName: string;
    components: string[];
    visualHierarchy: string[];
    missingStates: string[];
    composeCodeSnippet: string;
    generatedPrompt: string;
  } {
    return {
      screenName: 'شاشة تفاصيل الطلبات وسلة المبيعات (SaleCheckoutScreen)',
      components: [
        'TopAppBar مع زر رجوع وعنوان الشاشة وزر إتمام الطلب',
        'LazyColumn يعرض بطاقات المنتجات مع عدادات الكمية (+ / -)',
        'بطاقة ملخص الحساب (المجموع، الضريبة، الإجمالي الصافي)',
        'شريط أزرار سفلية ثابتة: [طباعة الفاتورة] و [إتمام الدفع النقد]',
      ],
      visualHierarchy: [
        'المستوى 1: إجمالي الفاتورة البارز بأرقام عريضة (HeadlineMedium)',
        'المستوى 2: أسماء المنتجات وأسعارها المفردة (TitleSmall)',
        'المستوى 3: تفاصيل الضرائب والخصومات بلون رمادي هادئ (BodySmall)',
      ],
      missingStates: [
        'حالة السلة الفارغة (EmptyState): تحتاج إلى رسم توضيحي وزر "بدء إضافة منتجات"',
        'حالة جاري الطباعة (PrintingState): مؤشر تقدم دائري أثناء إرسال أوامر البلوتوث',
        'حالة فشل الاتصال بالطابعة (ErrorDialog): رسالة تحذير مع خيار إعادة المحاولة',
      ],
      composeCodeSnippet: `@Composable
fun SaleCheckoutScreen(
    onNavigateBack: () -> Unit,
    onCompleteSale: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إتمام عملية البيع", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onCompleteSale,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("إصدار الفاتورة وحفظها")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("قائمة المنتجات المحددة", style = MaterialTheme.typography.titleSmall)
            // محتوى القائمة
        }
    }
}`,
      generatedPrompt: `قم ببناء شاشة Jetpack Compose باسم SaleCheckoutScreen لدعم اتجاه RTL بالكامل، وتضمين Scaffold مع TopAppBar وLazyColumn للمنتجات، وبطاقة ملخص إجمالي الحساب، وتغطية حالات السلة الفارغة والطباعة وفشل الاتصال.`,
    };
  }

  /**
   * Stage 15: Offline Stack Trace & Source Code Analyzer
   */
  static analyzeStackTraceOffline(input: string): {
    errorType: string;
    severity: 'critical' | 'warning' | 'info';
    rootCause: string;
    memoryImpact: string;
    fixedCode: string;
    fixPrompt: string;
  } {
    const lower = input.toLowerCase();
    if (lower.includes('outofmemoryerror') || lower.includes('oom') || lower.includes('heap')) {
      return {
        errorType: 'java.lang.OutOfMemoryError (تجاوز سعة ذاكرة آلة جافا JVM Heap)',
        severity: 'critical',
        rootCause: 'محاولة قراءة أو تحميل مصفوفات ملف نموذج الذكاء الاصطناعي (ByteArray) داخل ذاكرة JVM بدلاً من تمرير مسار الملف مباشرة إلى طبقة C++ الأصلية عبر JNI mmap.',
        memoryImpact: 'يستهلك كامل حصة التطبيق في JVM Heap (عادة 256MB-512MB) ويؤدي لقتل التطبيق فوراً بواسطة نظام أندرويد.',
        fixedCode: `// الحل الصحيح: فحص الذاكرة أولاً ثم التحميل في Native C++ Heap فقط
suspend fun safeLoadModel(modelFile: File, profiler: HardwareProfiler): Result<Long> = withContext(Dispatchers.IO) {
    val safety = profiler.verifyModelMemorySafety(modelFile)
    if (!safety.isSafe) {
        return@withContext Result.failure(IllegalStateException(safety.warningMessage))
    }
    // تمرير المسار النصي فقط إلى JNI دون قراءة الملف في JVM
    val nativeHandle = LlamaBridge.loadModelNative(modelFile.absolutePath, threads = 4)
    Result.success(nativeHandle)
}`,
        fixPrompt: `أصلح خطأ OutOfMemoryError عبر إزالة أي قراءة لملف النموذج بـ File.readBytes() في Kotlin، واستبدلها بفحص الذاكرة عبر HardwareProfiler(modelSize * 1.25 <= availRam) ثم تمرير modelFile.absolutePath مباشرة لدالة JNI الأصلية في C++.`,
      };
    }

    if (lower.includes('room') || lower.includes('migration') || lower.includes('illegalstateexception')) {
      return {
        errorType: 'RoomDatabase IllegalStateException (تعارض مخطط قاعدة البيانات)',
        severity: 'critical',
        rootCause: 'تم تعديل إحدى كيانات قاعدة البيانات (@Entity) دون رفع رقم الإصدار أو توفير خطة ترحيل آمنة (AutoMigration / Migration)، مما يمنع فتح قاعدة البيانات.',
        memoryImpact: 'لا يوجد تأثير مباشر على الـ RAM، لكنه يمنع إقلاع طبقة التخزين المحلية.',
        fixedCode: `@Database(
    entities = [ProjectEntity::class, PromptVersionEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}`,
        fixPrompt: `قم بإصلاح خطأ ترحيل قاعدة بيانات Room عبر تفعيل exportSchema = true وإضافة AutoMigration بين الإصدار القديم والجديد مع الحفاظ الكامل على بيانات المستخدم المحلية دون استخدام fallbackToDestructiveMigration.`,
      };
    }

    return {
      errorType: 'تنبيه معماري / استثناء تشغيلي في خيوط المعالجة (Main Thread / JNI)',
      severity: 'warning',
      rootCause: 'تشغيل عملية ثقيلة (استدلال أو فحص بصمة SHA-256 أو قراءة ملف) على الخيط الرئيسي للواجهة (Main UI Thread) مما يسبب تقطّع الإطارات أو خطأ ANR.',
      memoryImpact: 'حجب خيط الرسم في Jetpack Compose وارتفاع حرارة المعالج.',
      fixedCode: `// نقل العمليات الثقيلة إلى Dispatchers.IO أو Dispatchers.Default داخل ForegroundService
viewModelScope.launch(Dispatchers.Default) {
    val hash = ChecksumValidator.calculateSha256(modelFile)
    withContext(Dispatchers.Main) {
        _uiState.update { it.copy(checksumVerified = hash == expectedHash) }
    }
}`,
      fixPrompt: `قم بنقل كافة عمليات حساب بصمة SHA-256 واستدلال النموذج اللغوي من الخيط الرئيسي إلى Dispatchers.IO / Dispatchers.Default مع حماية التوليد الطويل داخل ForegroundService.`,
    };
  }
}
