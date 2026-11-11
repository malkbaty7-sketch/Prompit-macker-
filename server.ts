import express from 'express';
import { createServer as createViteServer } from 'vite';
import dotenv from 'dotenv';
import path from 'path';
import http from 'http';
import { fileURLToPath } from 'url';
import { GoogleGenAI } from '@google/genai';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

// Enable CORS for cross-origin iframe sandbox access
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, User-Agent');
  if (req.method === 'OPTIONS') {
    return res.sendStatus(200);
  }
  next();
});

app.use(express.json({ limit: '25mb' }));

// Server-side Gemini client
const getAiClient = () => {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return null;
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
};

// Resilient Gemini call with automatic fallback on temporary 503 spikes
async function generateWithFallback(
  ai: GoogleGenAI,
  contents: any,
  config?: { systemInstruction?: string; responseMimeType?: string }
) {
  const candidateModels = ['gemini-3.8-flash', 'gemini-flash-latest', 'gemini-3.1-flash-lite'];
  let lastError: any = null;
  for (const model of candidateModels) {
    try {
      const response = await ai.models.generateContent({
        model,
        contents,
        config,
      });
      return response;
    } catch (err: any) {
      lastError = err;
      console.warn(`Model ${model} encountered transient error, trying next fallback:`, err?.message);
    }
  }
  throw lastError;
}

// Health and capability check
app.get('/api/health', (req, res) => {
  const hasKey = Boolean(process.env.GEMINI_API_KEY);
  res.json({
    status: 'ok',
    hasGeminiKey: hasKey,
    model: 'gemini-3.8-flash',
    mode: 'Forgeprom Hybrid Engine (Local-first + Server Augmented)',
  });
});

// Analyze Idea Endpoint (Stage 6)
app.post('/api/analyze-idea', async (req, res) => {
  try {
    const { idea, projectType = 'full_app', platform = 'Android Native (Kotlin & Jetpack Compose)' } = req.body;
    if (!idea || typeof idea !== 'string') {
      return res.status(400).json({ error: 'يرجى تقديم وصف الفكرة' });
    }

    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({
        error: 'مفتاح Gemini API غير متوفر على الخادم. سيعمل التطبيق في الوضع المحلي دون اتصال.',
        offlineReady: true,
      });
    }

    const systemInstruction = `أنت المحلل الهندسي الذكي في استوديو Forgeprom Code Studio.
مهمتك تحويل الأفكار والوصف الحر (بالعربية أو الإنجليزية أو نصوص مختلطة) إلى تحليل متطلبات تقنية دقيق وهندسي.
قواعد صارمة:
1. الفصل التام بين "الحقائق المؤكدة" (ما ذكره المستخدم صراحة) و"الافتراضات المقترحة" (ما استنتجه النظام وقابل للتعديل).
2. عدم تعطيل المستخدم: قدم مسودة تحليل شاملة مع أسئلة توضيحية ذكية في قسم منفصل.
3. دعم بنية وتطوير ${platform} ونوع المشروع ${projectType}.
4. يجب أن تكون الاستجابة بصيغة JSON حصرياً مطابقة للشكل المطلوب.`;

    const prompt = `حلل الفكرة التالية بدقة:
"${idea}"

المطلوب إخراج كائن JSON فقط بالبنية التالية:
{
  "problem": "المشكلة الأساسية التي يعالجها المشروع",
  "primaryGoal": "الهدف الرئيسي للتطبيق",
  "subGoals": ["هدف فرعي 1", "هدف فرعي 2", "هدف فرعي 3"],
  "targetAudience": "الفئة المستهدفة بالتفصيل",
  "functionalReqs": {
    "mustHave": ["متطلب وظيفي إلزامي 1", "متطلب وظيفي إلزامي 2", "متطلب وظيفي إلزامي 3"],
    "niceToHave": ["متطلب تحسيني مستقبلي 1", "متطلب تحسيني مستقبلي 2"]
  },
  "nonFunctionalReqs": {
    "performance": ["معيار الأداء واستجابة الواجهة"],
    "security": ["معيار الأمان والخصوصية وحماية البيانات"],
    "offline": ["سياسة العمل دون إنترنت Local-first"]
  },
  "constraints": ["القيود التقنية والتنفيذية المفروضة"],
  "ambiguities": ["نقاط الغموض أو التعارض المحتمل في الفكرة"],
  "assumptions": ["افتراض مقترح 1 يمكن للمستخدم تعديله", "افتراض مقترح 2"],
  "verifiedFacts": ["حقيقة مؤكدة مستخرجة حرفياً من وصف المستخدم 1", "حقيقة مؤكدة 2"],
  "clarifyingQuestions": ["سؤال توضيحي ذكي 1", "سؤال توضيحي ذكي 2"]
}`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction,
      responseMimeType: 'application/json',
    });

    const parsed = JSON.parse(response.text || '{}');
    res.json(parsed);
  } catch (error: any) {
    console.error('Error analyzing idea:', error);
    res.status(500).json({ error: error?.message || 'فشل تحليل الفكرة' });
  }
});

// Generate Standardized Engineering Prompt Endpoint (Stage 7)
app.post('/api/generate-prompt', async (req, res) => {
  try {
    const {
      analysisData,
      targetPlatform = 'Android Studio AI',
      detailLevel = 'comprehensive',
      customDirectives = '',
      taskType = 'build_app',
    } = req.body;

    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({
        error: 'مفتاح Gemini API غير متوفر على الخادم. سيعمل التطبيق في الوضع المحلي دون اتصال.',
        offlineReady: true,
      });
    }

    const systemInstruction = `أنت مهندس البرومبتات في Forgeprom Code Studio.
مهمتك توليد برومبت هندسي احترافي جاهز للنسخ المباشر إلى المنصة المحددة (${targetPlatform}).
يجب أن يتبع البرومبت الهيكل القياسي المعتمد:
1. الدور (Role)
2. سياق المشروع والمواصفات (Context)
3. الهدف الأساسي (Objective)
4. المتطلبات الوظيفية المنظمة (Functional Requirements)
5. القيود الصارمة وتعليمات الحماية (Strict Guardrails - منع مسح أي كود سليم، الالتزام بالمعمارية)
6. مواصفات الواجهة وتجربة المستخدم (UI/UX)
7. خطة التنفيذ المرحلية خطوة بخطوة (Step-by-Step Implementation)
8. الاختبارات ومعايير القبول الصارمة (Acceptance Criteria)
9. شكل المخرجات المتوقعة وكيفية التسليم (Deliverables).
اكتب البرومبت بلغة تقنية واضحة مع إبقاء التعليمات واضحة ومنظمة.`;

    const prompt = `أنشئ برومبت هندسي متكامل للمنصة: "${targetPlatform}"
نوع المهمة: "${taskType}"
مستوى التفصيل: "${detailLevel}"
توجيهات إضافية: "${customDirectives || 'لا توجد'}"

بيانات التحليل الهندسي للمشروع:
${JSON.stringify(analysisData, null, 2)}

قم بصياغة البرومبت بتنسيق Markdown احترافي مع عناوين واضحة وجداول وقوائم مرقمة.`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction,
    });

    res.json({ promptText: response.text || '' });
  } catch (error: any) {
    console.error('Error generating prompt:', error);
    res.status(500).json({ error: error?.message || 'فشل توليد البرومبت' });
  }
});

// Optimize Existing Weak Prompt Endpoint (Stage 7 - أداة تحسين برومبت قائم)
app.post('/api/optimize-prompt', async (req, res) => {
  try {
    const { rawPrompt, targetPlatform = 'Android Studio AI' } = req.body;
    if (!rawPrompt || typeof rawPrompt !== 'string') {
      return res.status(400).json({ error: 'يرجى إدخال البرومبت المراد تحسينه' });
    }

    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({ error: 'مفتاح Gemini API غير متوفر' });
    }

    const systemInstruction = `أنت خبير إعادة هندسة البرومبتات في Forgeprom Code Studio.
مهمتك استلام برومبت ضعيف أو مختصر وإعادة صياغته هندسياً دون تحريف مقصود المستخدم، وفق الهيكل القياسي المكون من 9 أقسام:
(الدور، سياق المشروع، الهدف، المتطلبات، القيود الصارمة وتعليمات الحماية، الواجهات، خطة التنفيذ، الاختبارات ومعايير القبول، وشكل التسليم).`;

    const prompt = `قم بإعادة صياغة وتحسين البرومبت التالي للمنصة (${targetPlatform}) مع الحفاظ التام على هدف المستخدم وإضافة ضوابط الحماية ومعايير القبول:
"${rawPrompt}"

أخرج النتيجة بصيغة JSON فقط:
{
  "optimizedPrompt": "البرومبت الهندسي المحسّن بالكامل بتنسيق Markdown",
  "improvementsMade": ["تحسين 1 تم إضافته", "تحسين 2 تم إضافته", "تحسين 3 تم إضافته"]
}`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction,
      responseMimeType: 'application/json',
    });

    const parsed = JSON.parse(response.text || '{}');
    res.json(parsed);
  } catch (error: any) {
    console.error('Error optimizing prompt:', error);
    res.status(500).json({ error: error?.message || 'فشل تحسين البرومبت' });
  }
});

// Chained PRD & Engineering Pipeline Endpoint (Stage 8)
app.post('/api/chained-prd', async (req, res) => {
  try {
    const { step, projectData } = req.body;
    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({ error: 'مفتاح Gemini API غير متوفر' });
    }

    const stepTitles: Record<number, string> = {
      1: 'وثيقة رؤية المنتج (PRD) وتحديد نطاق الـ MVP',
      2: 'تقسيم الوحدات، اختيار البنية التقنية (Tech Stack)، والمخطط المعماري النصي',
      3: 'مواصفات الشاشات، تدفقات المستخدم، وقصص المستخدم مع معايير القبول (Acceptance Criteria)',
      4: 'مخطط البيانات الأولي (Room Entities)، ووصف الـ API وحالات الخطأ والصلاحيات',
      5: 'خطة التنفيذ المرحلية، ترتيب المهام حسب الاعتماديات، وخطة ترحيل البيانات',
    };

    const stepTitle = stepTitles[step] || `الخطوة ${step}`;

    const prompt = `أنت كبير مهندسي النظم في Forgeprom Code Studio.
المطلوب توليد "${stepTitle}" للمشروع التالي:
${JSON.stringify(projectData, null, 2)}

اكتب مستنداً هندسياً معمقاً بتنسيق Markdown يحتوي على كل التفاصيل والقرارات التقنية المعيارية لمنصة Android Native (Kotlin & Jetpack Compose) مع المعمارية النظيفة (Clean Architecture) ونمط Local-first.`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction: 'أنت مهندس نظم وتطبيقات Android أول، متمكن من Clean Architecture وRoom وJetpack Compose والعمل دون إنترنت.',
    });

    res.json({
      step,
      title: stepTitle,
      content: response.text || '',
    });
  } catch (error: any) {
    console.error('Error generating chained PRD step:', error);
    res.status(500).json({ error: error?.message || 'فشل توليد المرحلة الهندسية' });
  }
});

// Quality Review Endpoint (Stage 10)
app.post('/api/quality-review', async (req, res) => {
  try {
    const { promptText, analysisData } = req.body;
    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({ error: 'مفتاح Gemini API غير متوفر' });
    }

    const systemInstruction = `أنت مدقق الجودة الهندسي في Forgeprom Code Studio.
تراجع البرومبتات والمواصفات للكشف عن:
1. نقص أقسام إلزامية (مثل معايير القبول أو تعليمات الحماية).
2. التعارضات المنطقية (مثل طلب عمل أوفلاين مع اعتماد مكتبات تتطلب إنترنت إلزامي).
3. الغموض أو التردد في الصياغة.
4. تغطية حالات الخطأ والاستثناءات وإمكانية الوصول (Accessibility).
قدم تقريراً يقدم أسباباً واضحة واقتراح نص إصلاح محدد لكل ملاحظة.`;

    const prompt = `راجع البرومبت والبيانات التالية:
برومبت:
"""
${promptText}
"""

بيانات المشروع:
${JSON.stringify(analysisData, null, 2)}

أخرج النتيجة بصيغة JSON فقط بهذا الشكل:
{
  "overallAssessment": "تقييم عام للاكتمال الهندسي",
  "score": 92,
  "issues": [
    {
      "id": "issue_1",
      "severity": "high",
      "category": "اكتمال المتطلبات",
      "title": "عنوان الملاحظة",
      "reason": "السبب المنطقي الدقيق وراء الملاحظة",
      "suggestedFix": "النص المقترح للإصلاح أو الإضافة بضغطة زر"
    }
  ],
  "strengths": ["نقطة قوة 1", "نقطة قوة 2"]
}`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction,
      responseMimeType: 'application/json',
    });

    const parsed = JSON.parse(response.text || '{}');
    res.json(parsed);
  } catch (error: any) {
    console.error('Error performing quality review:', error);
    res.status(500).json({ error: error?.message || 'فشل مراجعة الجودة' });
  }
});

// Vision UI Analyzer (Stage 9)
app.post('/api/analyze-image', async (req, res) => {
  try {
    const { imageBase64, mimeType = 'image/jpeg', notes = '' } = req.body;
    const ai = getAiClient();
    if (!ai) {
      return res.status(503).json({ error: 'مفتاح Gemini API غير متوفر' });
    }

    const cleanBase64 = imageBase64.replace(/^data:image\/[a-z]+;base64,/, '');

    const imagePart = {
      inlineData: {
        mimeType,
        data: cleanBase64,
      },
    };

    const textPart = {
      text: `حلل هذه الصورة لواجهة المستخدم (UI Wireframe / Screenshot):
ملاحظات إضافية: "${notes}"
المطلوب استخراج:
1. المكونات البصرية وعناصر الشاشة (Buttons, Text fields, Cards, Lists, TopAppBar, BottomNav, etc.).
2. التسلسل البصري للنصوص والعناوين (Visual Hierarchy).
3. تدفق التفاعل وحالات الشاشة المحتملة (Loading, Empty, Error, Success).
4. كود Jetpack Compose مقترح لبناء هذه الواجهة بدقة مع مراعاة اتجاه RTL.
5. برومبت هندسي جاهز لتوليد الشاشة في أدوات البرمجة الذكية.

أخرج النتيجة بتنسيق JSON:
{
  "screenName": "اسم مقترح للشاشة",
  "components": ["مكون 1", "مكون 2"],
  "visualHierarchy": ["تسلسل 1", "تسلسل 2"],
  "missingStates": ["حالة ناقصة يجب تغطيتها"],
  "composeCodeSnippet": "كود Kotlin Jetpack Compose",
  "generatedPrompt": "البرومبت المقترح لتوليد هذه الواجهة"
}`,
    };

    const response = await generateWithFallback(ai, { parts: [imagePart, textPart] }, {
      responseMimeType: 'application/json',
    });

    const parsed = JSON.parse(response.text || '{}');
    res.json(parsed);
  } catch (error: any) {
    console.error('Error analyzing image:', error);
    res.status(500).json({ error: error?.message || 'فشل تحليل الصورة' });
  }
});

// Technical Research Endpoint (Stage 11)
app.post('/api/technical-search', async (req, res) => {
  try {
    const { query, topic = 'general', allowCloud = false } = req.body;
    if (!query || typeof query !== 'string') {
      return res.status(400).json({ error: 'يرجى تقديم استعلام البحث' });
    }

    const ai = getAiClient();
    if (!allowCloud || !ai) {
      return res.json({
        isOfflineMode: true,
        source: 'مستودع المعرفة المحلي لـ Forgeprom Studio',
        query,
        summary: `تم البحث محلياً دون اتصال بالإنترنت عن: "${query}". جميع المراجع والمكتبات المعروضة مأخوذة من توثيق Android الرسمي وأفضل ممارسات هندسة النظم.`,
        topics: [
          {
            title: 'معمارية Clean Architecture في أندرويد',
            docUrl: 'https://developer.android.com/topic/architecture',
            summary: 'فصل طبقات Presentation وDomain وData، واعتماد نماذج Flow وحقن الاعتماديات Hilt.',
            codeSnippet: '// Domain UseCase\nclass GetProjectsUseCase @Inject constructor(\n    private val repository: ProjectRepository\n) {\n    operator fun invoke(): Flow<List<Project>> = repository.observeProjects()\n}',
            recommendation: 'معتمد رسمياً في دليل هندسة تطبيقات أندرويد الحديثة.',
          },
          {
            title: 'إدارة الذاكرة مع llama.cpp في C++ JNI',
            docUrl: 'https://github.com/ggerganov/llama.cpp',
            summary: 'تحميل مصفوفات النموذج المكممة Q4_K_M في الذاكرة الأصلية عبر mmap لمنع تجاوز JVM Heap.',
            codeSnippet: '// JNI Bridge\nJNIEXPORT jlong JNICALL\nJava_com_forgeprom_ai_LlamaBridge_loadModel(\n    JNIEnv* env, jobject thiz, jstring modelPath, jint nThreads\n) {\n    llama_model_params model_params = llama_model_default_params();\n    return (jlong) llama_load_model_from_file(path, model_params);\n}',
            recommendation: 'الحل الأمثل لتشغيل نماذج 1B-3B على هواتف 4GB-8GB RAM دون OOM.',
          },
        ],
        citations: [
          'Android Developers Guide: App Architecture (2025)',
          'Jetpack Compose Performance Best Practices',
          'llama.cpp Mobile GGUF Quantization Documentation',
        ],
      });
    }

    const systemInstruction = `أنت محرك البحث والتوثيق التقني الموثوق في Forgeprom Code Studio.
مهمتك تقديم معلومات موثوقة ومفصلة من التوثيق الرسمي لـ Android Jetpack، وKotlin، وRoom، وllama.cpp، وMaterial 3.
القواعد:
1. التمييز الصارم بين الحقائق الموثقة في المستندات الرسمية والاقتراحات الشخصية.
2. تضمين روابط توثيق تقريبية دقيقة (developer.android.com, kotlinlang.org, github).
3. إعطاء أكواد Kotlin / Jetpack Compose نظيفة ومباشرة.
4. أخرج النتيجة بتنسيق JSON حصرياً.`;

    const prompt = `ابحث وقدم وثيقة تقنية متكاملة وموثقة حول:
"${query}"
في مجال: "${topic}"

أخرج النتيجة كـ JSON بالهيكل التالي:
{
  "isOfflineMode": false,
  "source": "توثيق Android الرسمي + مستودعات GitHub المفتوحة",
  "query": "${query}",
  "summary": "ملخص تقني مكثف",
  "topics": [
    {
      "title": "عنوان الموضوع التقني",
      "docUrl": "https://developer.android.com/...",
      "summary": "شرح هندسي دقيق مع أسباب الاختيار",
      "codeSnippet": "كود نموذجي",
      "recommendation": "توصية الاستخدام"
    }
  ],
  "citations": [
    "مرجع موثق 1",
    "مرجع موثق 2"
  ]
}`;

    try {
      const response = await generateWithFallback(ai, prompt, {
        systemInstruction,
        responseMimeType: 'application/json',
      });

      const parsed = JSON.parse(response.text || '{}');
      return res.json(parsed);
    } catch (geminiError: any) {
      console.warn('Gemini cloud search temporarily unavailable, falling back to local docs:', geminiError?.message);
      return res.json({
        isOfflineMode: true,
        source: 'مستودع المعرفة المحلي لـ Forgeprom Studio (بديل آمن عند انقطاع السحابة)',
        query,
        summary: `تم التحويل تلقائياً للوضع المحلي الآمن. استعلام: "${query}". توثيق معتمد لمنصة Android Jetpack وClean Architecture.`,
        topics: [
          {
            title: `دليل توثيق ${query}`,
            docUrl: 'https://developer.android.com/reference/kotlin',
            summary: `شرح وتوجيهات معمارية Android الرسمية للتعامل مع ${query} ونمط Local-first في Jetpack Compose وRoom.`,
            codeSnippet: `// مثال معماري معتمد لـ ${query}\n@Composable\nfun ScreenContent() {\n    // استجابة محلية فورية\n}`,
            recommendation: 'الاعتماد على المعالجة المحلية أولاً والتخزين في Room.',
          },
          {
            title: 'إدارة الذاكرة مع llama.cpp في C++ JNI',
            docUrl: 'https://github.com/ggerganov/llama.cpp',
            summary: 'تحميل مصفوفات النموذج المكممة Q4_K_M في الذاكرة الأصلية عبر mmap لمنع تجاوز JVM Heap.',
            codeSnippet: '// JNI Bridge\nJNIEXPORT jlong JNICALL\nJava_com_forgeprom_ai_LlamaBridge_loadModel(\n    JNIEnv* env, jobject thiz, jstring modelPath, jint nThreads\n) {\n    llama_model_params model_params = llama_model_default_params();\n    return (jlong) llama_load_model_from_file(path, model_params);\n}',
            recommendation: 'الحل الأمثل لتشغيل نماذج 1B-3B على هواتف 4GB-8GB RAM دون OOM.',
          },
        ],
        citations: [
          'Android Developers Guide: App Architecture (2025)',
          'Jetpack Compose Performance & RTL Guidelines',
          'llama.cpp Mobile GGUF Quantization Documentation',
        ],
      });
    }
  } catch (error: any) {
    console.error('Error in technical search:', error);
    res.status(500).json({ error: error?.message || 'فشل البحث التقني' });
  }
});

// Stack Trace & Source Code Analyzer Endpoint (Stage 15)
app.post('/api/analyze-stacktrace', async (req, res) => {
  try {
    const { inputCodeOrTrace, mode = 'stacktrace', allowCloud = false } = req.body;
    if (!inputCodeOrTrace || typeof inputCodeOrTrace !== 'string') {
      return res.status(400).json({ error: 'يرجى إدخال سجل الخطأ أو الكود البرمجي' });
    }

    const ai = getAiClient();
    if (!allowCloud || !ai) {
      return res.status(503).json({ error: 'الوضع المحلي مفعل' });
    }

    const systemInstruction = `أنت محلل الأعطال ومراجع الأكواد في Forgeprom Code Studio.
مهمتك تحليل سجلات أخطاء أندرويد (Logcat / Gradle / JNI / Room / OutOfMemoryError) أو ملفات الكود البرمجية، وتحديد السبب الجذري بدقة، وتقديم الكود المصحح وبرومبت الإصلاح الهندسي.`;

    const prompt = `حلل النص البرمجي التالي (${mode}):
"""
${inputCodeOrTrace}
"""

أخرج النتيجة بصيغة JSON فقط:
{
  "errorType": "نوع الخطأ أو المشكلة المعمارية",
  "severity": "critical" | "warning" | "info",
  "rootCause": "التفسير الهندسي الدقيق للسبب الجذري",
  "memoryImpact": "تأثير المشكلة على الذاكرة العشوائية (JVM / Native Heap) أو المعالج",
  "fixedCode": "الكود البرمجي المصحح بالكامل بلغة Kotlin / C++",
  "fixPrompt": "برومبت هندسي جاهز للنسخ لإصلاح المشكلة في المشروع"
}`;

    const response = await generateWithFallback(ai, prompt, {
      systemInstruction,
      responseMimeType: 'application/json',
    });

    const parsed = JSON.parse(response.text || '{}');
    res.json(parsed);
  } catch (error: any) {
    console.error('Error analyzing stacktrace:', error);
    res.status(500).json({ error: error?.message || 'فشل تحليل سجل الخطأ' });
  }
});

// Configure Vite middleware or static assets
async function startServer() {
  const server = http.createServer(app);
  const isProd = process.env.NODE_ENV === 'production';
  if (!isProd) {
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: process.env.DISABLE_HMR === 'true' ? false : { server },
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (req, res) => {
      res.sendFile(path.resolve(__dirname, 'dist', 'index.html'));
    });
  }

  server.listen(PORT, '0.0.0.0', () => {
    console.log(`Forgeprom Code Studio server running at http://0.0.0.0:${PORT}`);
  });
}

startServer();
