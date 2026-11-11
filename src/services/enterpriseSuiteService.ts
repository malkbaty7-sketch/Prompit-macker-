import { Project } from '../types';
import { ExportService } from './exportService';

export interface SecurityFinding {
  id: string;
  owaspCategory: string;
  title: string;
  severity: 'critical' | 'high' | 'medium';
  status: 'mitigated' | 'action_required';
  description: string;
  remediation: string;
  codeSnippetTitle: string;
  codeSnippet: string;
}

export interface ApiEndpointSpec {
  id: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  summary: string;
  authRequired: boolean;
  requestSample?: string;
  responseSample: string;
}

export interface FinOpsCalculation {
  mau: number;
  dailyPromptsPerUser: number;
  avgTokensPerPrompt: number;
  totalMonthlyTokensMillions: number;
  providers: {
    name: string;
    modelName: string;
    inputCostPer1M: number;
    outputCostPer1M: number;
    monthlyLlmCostUsd: number;
    monthlyInfraCostUsd: number;
    totalMonthlyUsd: number;
  }[];
  hybridLocalFirstMonthlyUsd: number;
  monthlySavingsUsd: number;
  annualSavingsUsd: number;
}

export class EnterpriseSuiteService {
  /**
   * 1. OWASP Top 10 & Mobile MASVS Security Auditor
   */
  static runSecurityAudit(project: Project): SecurityFinding[] {
    const isAndroid = String(project.targetPlatform || '').toLowerCase().includes('android');
    const rawText = `${project.name} ${project.description} ${project.rawIdea}`.toLowerCase();
    const hasAuth = rawText.includes('دخول') || rawText.includes('مستخدم') || rawText.includes('حساب') || rawText.includes('auth');
    const hasPayments = rawText.includes('دفع') || rawText.includes('متجر') || rawText.includes('فواتير') || rawText.includes('مال');

    return [
      {
        id: 'sec-01',
        owaspCategory: 'OWASP API1:2023 / Broken Object Level Authorization (BOLA / IDOR)',
        title: 'حماية معرّفات السجلات وعزل بيانات المستخدمين (Row-Level Security)',
        severity: 'critical',
        status: 'action_required',
        description: `يتطلب مشروع "${project.name}" التحقق الصارم من ملكية كل سجل عند طلب البيانات عبر المعرّف (ID) لمنع وصول مستخدم لبيانات مستخدم آخر.`,
        remediation: 'تطبيق التحقق من هوية المالك (Owner UID) على مستوى قاعدة البيانات والـ Middleware قبل إرجاع أو تعديل أي وثيقة.',
        codeSnippetTitle: 'PostgreSQL Row-Level Security (RLS) & Firestore Rules',
        codeSnippet: `-- 1. PostgreSQL RLS Policy
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_isolate_own_records"
ON projects
FOR ALL
USING (auth.uid() = owner_id)
WITH CHECK (auth.uid() = owner_id);

// 2. Firestore Security Rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /projects/{projectId} {
      allow read, update, delete: if request.auth != null && request.auth.uid == resource.data.ownerId;
      allow create: if request.auth != null && request.resource.data.ownerId == request.auth.uid;
    }
  }
}`,
      },
      {
        id: 'sec-02',
        owaspCategory: 'OWASP A02:2021 / Cryptographic Failures & Local Storage Hardening',
        title: isAndroid
          ? 'تشفير قاعدة بيانات Room ومفاتيح الـ API على هاتف الأندرويد (MASVS-STORAGE)'
          : 'تشفير البيانات المحلية الحساسة ومنع تسرب مفاتيح الـ API في الواجهة',
        severity: 'critical',
        status: 'mitigated',
        description: 'تخزين المسودات الهندسية أو مفاتيح الاتصال كنص صريح يعرضهاللاستخراج في حال فقدان الجهاز أو فحص الذاكرة.',
        remediation: 'استخدام AES-256-GCM مع Android KeyStore / EncryptedSharedPreferences وعزل مفاتيح السحابة في الخادم الوسيط (/api/*).',
        codeSnippetTitle: 'Android KeyStore AES-256-GCM & Server Proxy Guard',
        codeSnippet: `// Kotlin: Android KeyStore AES-256-GCM Encryption
val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
keyGenerator.init(
    KeyGenParameterSpec.Builder("forgeprom_vault_key",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(false)
        .build()
)
val secretKey = keyGenerator.generateKey()`,
      },
      {
        id: 'sec-03',
        owaspCategory: 'OWASP API4:2023 / Unrestricted Resource Consumption',
        title: 'حماية نقاط النهاية للذكاء الاصطناعي من استنزاف الموارد (Rate Limiting & Quota)',
        severity: 'high',
        status: 'action_required',
        description: 'استدعاء نقاط توليد البرومبت أو البحث السحابي دون سقف زمني قد يؤدي إلى استنزاف رصيد الـ Tokens أو هجمات حجب الخدمة (DoS).',
        remediation: 'تفعيل محدد الطلبات (Token Bucket / Sliding Window Rate Limiter) مع حد أقصى لحجم الحمولة (Payload Limit 1MB).',
        codeSnippetTitle: 'Express / Node.js Rate Limiter & Payload Shield',
        codeSnippet: `// Express Security & Rate Limiting Middleware
app.use(express.json({ limit: '512kb' }));

const rateLimitMap = new Map<string, { count: number; resetAt: number }>();
export function apiRateLimiter(req: Request, res: Response, next: NextFunction) {
  const ip = req.ip || 'unknown';
  const now = Date.now();
  const windowMs = 60_000; // 1 minute
  const maxRequests = 30;

  const entry = rateLimitMap.get(ip) || { count: 0, resetAt: now + windowMs };
  if (now > entry.resetAt) {
    entry.count = 0;
    entry.resetAt = now + windowMs;
  }
  entry.count += 1;
  rateLimitMap.set(ip, entry);

  if (entry.count > maxRequests) {
    return res.status(429).json({ error: 'Too many requests. Please retry after 60 seconds.' });
  }
  next();
}`,
      },
      {
        id: 'sec-04',
        owaspCategory: 'OWASP A03:2021 / Injection & Prompt Injection Defense',
        title: 'التحصين ضد حقن الأوامر (Prompt Injection) وتطهير المدخلات (Input Sanitization)',
        severity: hasPayments || hasAuth ? 'critical' : 'high',
        status: 'mitigated',
        description: 'إدخال نصوص خبيثة داخل وصف الفكرة أو ملفات الاستيراد قد يحاول تجاوز تعليمات النظام (System Instructions) أو تنفيذ XSS.',
        remediation: 'فصل تعليمات النظام عن مدخلات المستخدم باستخدام وسوم XML محمية والتحقق من المخطط (Strict JSON Schema Validation).',
        codeSnippetTitle: 'Prompt Delimiter Shield & Strict Schema Validation',
        codeSnippet: `export function sanitizeUserPromptInput(rawInput: string): string {
  const stripped = rawInput
    .replace(/<script\\b[^<]*(?:(?!<\\/script>)<[^<]*)*<\\/script>/gi, '')
    .slice(0, 12000);
  return \`<user_idea_sandbox>\\n\${stripped}\\n</user_idea_sandbox>\`;
}`,
      },
    ];
  }

  /**
   * 2. Cloud & LLM FinOps Cost Estimator
   */
  static calculateFinOps(
    mau: number,
    dailyPromptsPerUser: number,
    avgTokensPerPrompt: number,
    localOffloadPercent: number = 75
  ): FinOpsCalculation {
    const monthlyPrompts = mau * dailyPromptsPerUser * 30;
    const totalMonthlyTokens = monthlyPrompts * avgTokensPerPrompt;
    const totalMonthlyTokensMillions = totalMonthlyTokens / 1_000_000;

    // Assume 60% input tokens, 40% output tokens
    const inputTokensM = totalMonthlyTokensMillions * 0.6;
    const outputTokensM = totalMonthlyTokensMillions * 0.4;

    // Base infrastructure cost scales smoothly with MAU
    const baseInfraUsd = Math.max(12, Math.round((mau / 1000) * 8.5));

    const rawProviders = [
      {
        name: 'Google Gemini 2.5 Flash',
        modelName: 'gemini-2.5-flash',
        inputCostPer1M: 0.15,
        outputCostPer1M: 0.60,
      },
      {
        name: 'Google Gemini 2.5 Pro',
        modelName: 'gemini-2.5-pro',
        inputCostPer1M: 1.25,
        outputCostPer1M: 5.00,
      },
      {
        name: 'OpenAI GPT-4o',
        modelName: 'gpt-4o',
        inputCostPer1M: 2.50,
        outputCostPer1M: 10.00,
      },
      {
        name: 'Anthropic Claude 3.5 Sonnet',
        modelName: 'claude-3-5-sonnet',
        inputCostPer1M: 3.00,
        outputCostPer1M: 15.00,
      },
    ];

    const providers = rawProviders.map(p => {
      const llmCost = inputTokensM * p.inputCostPer1M + outputTokensM * p.outputCostPer1M;
      return {
        ...p,
        monthlyLlmCostUsd: Number(llmCost.toFixed(2)),
        monthlyInfraCostUsd: baseInfraUsd,
        totalMonthlyUsd: Number((llmCost + baseInfraUsd).toFixed(2)),
      };
    });

    // Compare against Gemini 2.5 Pro standard cloud setup when offloading localOffloadPercent% to on-device GGUF
    const cloudReference = providers[1].totalMonthlyUsd;
    const cloudFraction = (100 - localOffloadPercent) / 100;
    const hybridLocalFirstMonthlyUsd = Number(
      (providers[0].monthlyLlmCostUsd * cloudFraction + baseInfraUsd * 0.5).toFixed(2)
    );
    const monthlySavingsUsd = Math.max(0, Number((cloudReference - hybridLocalFirstMonthlyUsd).toFixed(2)));
    const annualSavingsUsd = Number((monthlySavingsUsd * 12).toFixed(2));

    return {
      mau,
      dailyPromptsPerUser,
      avgTokensPerPrompt,
      totalMonthlyTokensMillions: Number(totalMonthlyTokensMillions.toFixed(2)),
      providers,
      hybridLocalFirstMonthlyUsd,
      monthlySavingsUsd,
      annualSavingsUsd,
    };
  }

  /**
   * 3. OpenAPI 3.1 & Postman Collection Generator
   */
  static getDefaultEndpoints(project: Project): ApiEndpointSpec[] {
    return [
      {
        id: 'ep-health',
        method: 'GET',
        path: '/api/v1/health',
        summary: 'فحص جاهزية الخادم وزمن الاستجابة (Health & Latency Check)',
        authRequired: false,
        responseSample: JSON.stringify(
          { status: 'ok', service: project.name, mode: 'hybrid-local-first', timestamp: new Date().toISOString() },
          null,
          2
        ),
      },
      {
        id: 'ep-auth',
        method: 'POST',
        path: '/api/v1/auth/session',
        summary: 'إصدار رمز الجلسة الآمن (JWT Bearer Token Issuance)',
        authRequired: false,
        requestSample: JSON.stringify({ email: 'engineer@forgeprom.dev', deviceFingerprint: 'android-arm64-v8a' }, null, 2),
        responseSample: JSON.stringify({ accessToken: 'eyJhbGciOiJIUzI1NiIs...', expiresIn: 3600 }, null, 2),
      },
      {
        id: 'ep-projects-list',
        method: 'GET',
        path: '/api/v1/projects',
        summary: `استرجاع قائمة السجلات والمشاريع في ${project.name}`,
        authRequired: true,
        responseSample: JSON.stringify(
          {
            items: [{ id: project.id, name: project.name, platform: project.targetPlatform, updatedAt: project.updatedAt }],
            total: 1,
          },
          null,
          2
        ),
      },
      {
        id: 'ep-projects-create',
        method: 'POST',
        path: '/api/v1/projects',
        summary: 'إنشاء مشروع جديد وتحليل المتطلبات الهندسية',
        authRequired: true,
        requestSample: JSON.stringify(
          { name: project.name, description: project.description, targetPlatform: project.targetPlatform },
          null,
          2
        ),
        responseSample: JSON.stringify({ id: 'proj_new_01', status: 'created', synced: true }, null, 2),
      },
      {
        id: 'ep-sync-delta',
        method: 'POST',
        path: '/api/v1/sync/delta',
        summary: 'مزامنة التعديلات المحلية من الهاتف إلى الخادم السحابي (Offline-First Delta Sync)',
        authRequired: true,
        requestSample: JSON.stringify({ lastSyncTimestamp: Date.now() - 86400000, changes: [] }, null, 2),
        responseSample: JSON.stringify({ syncedCount: 1, conflictsResolved: 0, serverTimestamp: Date.now() }, null, 2),
      },
    ];
  }

  static generateOpenApiYaml(project: Project, endpoints: ApiEndpointSpec[], serverUrl: string): string {
    const pathsYaml = endpoints
      .map(ep => {
        const methodLower = ep.method.toLowerCase();
        const securityBlock = ep.authRequired ? `      security:\n        - bearerAuth: []\n` : '';
        const requestBodyBlock =
          ep.method === 'POST' || ep.method === 'PUT'
            ? `      requestBody:\n        required: true\n        content:\n          application/json:\n            schema:\n              type: object\n`
            : '';
        return `  ${ep.path}:
    ${methodLower}:
      summary: "${ep.summary.replace(/"/g, '\\"')}"
      operationId: "${ep.id.replace(/[^a-zA-Z0-9]/g, '_')}"
${securityBlock}${requestBodyBlock}      responses:
        '200':
          description: "Successful operation"
          content:
            application/json:
              schema:
                type: object`;
      })
      .join('\n');

    return `openapi: 3.1.0
info:
  title: "${project.name} — REST API Contract"
  description: "${(project.description || 'Generated by Forgeprom Code Studio').replace(/"/g, '\\"')}"
  version: "2.5.0"
servers:
  - url: "${serverUrl}"
    description: "Primary Hybrid Cloud Server"
  - url: "http://localhost:3000"
    description: "Local Development Server"
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
paths:
${pathsYaml}
`;
  }

  static generatePostmanCollectionJson(project: Project, endpoints: ApiEndpointSpec[], serverUrl: string): string {
    const collection = {
      info: {
        name: `${project.name} — Forgeprom API Collection`,
        description: project.description,
        schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
      },
      variable: [
        { key: 'baseUrl', value: serverUrl, type: 'string' },
        { key: 'accessToken', value: 'YOUR_JWT_TOKEN', type: 'string' },
      ],
      item: endpoints.map(ep => ({
        name: `${ep.method} ${ep.path} — ${ep.summary}`,
        request: {
          method: ep.method,
          header: [
            { key: 'Content-Type', value: 'application/json' },
            ...(ep.authRequired ? [{ key: 'Authorization', value: 'Bearer {{accessToken}}' }] : []),
          ],
          url: {
            raw: `{{baseUrl}}${ep.path}`,
            host: ['{{baseUrl}}'],
            path: ep.path.split('/').filter(Boolean),
          },
          ...(ep.requestSample
            ? {
                body: {
                  mode: 'raw',
                  raw: ep.requestSample,
                },
              }
            : {}),
        },
      })),
    };
    return JSON.stringify(collection, null, 2);
  }

  /**
   * 4. GitHub CLI & REST Automation Script Generator
   */
  static generateGitHubAutomationScript(project: Project, repoName: string, isPrivate: boolean): string {
    const mustHave = project.analysis?.functionalReqs?.mustHave || [
      'إعداد هيكل المشروع الأساسي وقاعدة البيانات المحلية',
      'ربط واجهة المستخدم مع الخادم السحابي والوضع المحلي',
      'إضافة اختبارات الأمان والتحقق من المدخلات',
    ];

    const issueCommands = mustHave
      .map(
        (req, idx) =>
          `gh issue create --title "المتطلب الهندسي #${idx + 1}: ${req.replace(/"/g, '\\"').slice(0, 70)}" --body "تم توليد هذه المهمة تلقائياً من وثيقة PRD لمشروع ${project.name}.\\n\\nالتفاصيل: ${req.replace(/"/g, '\\"')}" --label "enhancement"`
      )
      .join('\n');

    return `#!/usr/bin/env bash
# ==============================================================================
# Forgeprom Code Studio — Automated GitHub Repository & Issues Publisher
# Project: ${project.name}
# ==============================================================================
set -e

REPO_NAME="${repoName}"
VISIBILITY="${isPrivate ? '--private' : '--public'}"

echo "1. Initializing Git repository..."
git init
git add .
git commit -m "feat: initial engineering scaffold & PRD for ${project.name}" || true

echo "2. Creating GitHub repository ($REPO_NAME)..."
gh repo create "$REPO_NAME" $VISIBILITY --source=. --remote=origin --push

echo "3. Creating automated GitHub Issues from PRD Requirements..."
${issueCommands}

echo "Done! Repository and Engineering Issues published successfully."
`;
  }

  /**
   * 5. Real WebCrypto AES-256-GCM Encryption & Decryption for Project Vault
   */
  private static async deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
    const enc = new TextEncoder();
    const keyMaterial = await window.crypto.subtle.importKey(
      'raw',
      enc.encode(passphrase),
      { name: 'PBKDF2' },
      false,
      ['deriveKey']
    );
    return window.crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: salt as unknown as BufferSource,
        iterations: 100000,
        hash: 'SHA-256',
      },
      keyMaterial,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  static async encryptProjectVault(project: Project, passphrase: string): Promise<string> {
    const salt = window.crypto.getRandomValues(new Uint8Array(16));
    const iv = window.crypto.getRandomValues(new Uint8Array(12));
    const key = await this.deriveKey(passphrase, salt);

    const enc = new TextEncoder();
    const plaintext = JSON.stringify({
      vaultVersion: '2.5-AES-256-GCM',
      encryptedAt: new Date().toISOString(),
      project,
    });

    const ciphertextBuf = await window.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      enc.encode(plaintext)
    );

    const toBase64 = (bytes: Uint8Array) => {
      let binary = '';
      for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
      }
      return btoa(binary);
    };

    return JSON.stringify(
      {
        cipher: 'AES-256-GCM',
        kdf: 'PBKDF2-SHA256-100000',
        projectNameHint: project.name.slice(0, 15) + '***',
        salt: toBase64(salt),
        iv: toBase64(iv),
        payload: toBase64(new Uint8Array(ciphertextBuf)),
      },
      null,
      2
    );
  }

  static async decryptProjectVault(encryptedJson: string, passphrase: string): Promise<Project> {
    const parsed = JSON.parse(encryptedJson);
    const fromBase64 = (b64: string) => {
      const binary = atob(b64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      return bytes;
    };

    const salt = fromBase64(parsed.salt);
    const iv = fromBase64(parsed.iv);
    const payload = fromBase64(parsed.payload);
    const key = await this.deriveKey(passphrase, salt);

    const decryptedBuf = await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      payload as unknown as BufferSource
    );

    const dec = new TextDecoder();
    const unwrapped = JSON.parse(dec.decode(decryptedBuf));
    return unwrapped.project as Project;
  }

  static exportSecurityAuditMarkdown(project: Project, findings: SecurityFinding[]): void {
    const content = `# تقرير التدقيق الأمني ونمذجة التهديدات (OWASP & MASVS)
المشروع: ${project.name}
المنصة المستهدفة: ${project.targetPlatform}
تاريخ الفحص: ${new Date().toLocaleDateString('ar-SA')}

---

${findings
  .map(
    (f, i) => `## ${i + 1}. ${f.title}
- **التصنيف القياسي:** ${f.owaspCategory}
- **مستوى الخطورة:** ${f.severity.toUpperCase()}
- **الوصف:** ${f.description}
- **الإجراء الهندسي الموصى به:** ${f.remediation}

### الكود والسياسات الجاهزة للتطبيق (${f.codeSnippetTitle})
\`\`\`
${f.codeSnippet}
\`\`\`
`
  )
  .join('\n---\n\n')}
`;
    const safeName = project.name.replace(/[\s/\\?%*:|"<>]/g, '_');
    ExportService.downloadTextFile(`${safeName}_OWASP_Security_Audit.md`, content, 'text/markdown;charset=utf-8');
  }
}
