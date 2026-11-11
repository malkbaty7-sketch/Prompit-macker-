import React, { useState, useMemo } from 'react';
import {
  ShieldCheck,
  Calculator,
  FileJson,
  GitBranch,
  Lock,
  Download,
  Copy,
  Check,
  Plus,
  Trash2,
  Unlock,
  KeyRound,
  AlertTriangle,
  CheckCircle2,
  Upload,
} from 'lucide-react';
import { Project } from '../types';
import {
  EnterpriseSuiteService,
  ApiEndpointSpec,
} from '../services/enterpriseSuiteService';
import { ExportService } from '../services/exportService';

interface EnterpriseSuiteViewProps {
  project?: Project;
  projects: Project[];
  onSelectProject: (id: string) => void;
  onImportDecryptedProject: (project: Project) => void;
}

type SuiteModule = 'security' | 'finops' | 'openapi' | 'github' | 'vault';

export const EnterpriseSuiteView: React.FC<EnterpriseSuiteViewProps> = ({
  project,
  projects,
  onSelectProject,
  onImportDecryptedProject,
}) => {
  const activeProject = project || projects[0];
  const [activeModule, setActiveModule] = useState<SuiteModule>('security');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // 1. Security Auditor state
  const securityFindings = useMemo(
    () => (activeProject ? EnterpriseSuiteService.runSecurityAudit(activeProject) : []),
    [activeProject]
  );
  const [mitigatedIds, setMitigatedIds] = useState<Record<string, boolean>>({});

  // 2. FinOps state
  const [mau, setMau] = useState<number>(10000);
  const [dailyPrompts, setDailyPrompts] = useState<number>(5);
  const [avgTokens, setAvgTokens] = useState<number>(1800);
  const [localOffloadPercent, setLocalOffloadPercent] = useState<number>(75);

  const finops = useMemo(
    () => EnterpriseSuiteService.calculateFinOps(mau, dailyPrompts, avgTokens, localOffloadPercent),
    [mau, dailyPrompts, avgTokens, localOffloadPercent]
  );

  // 3. OpenAPI & Postman state
  const [serverUrl, setServerUrl] = useState<string>(
    'https://ais-pre-wg5ba7jzr4vldg2v6hfqek-381610229333.europe-west3.run.app'
  );
  const [customEndpoints, setCustomEndpoints] = useState<ApiEndpointSpec[]>([]);
  const [newEpMethod, setNewEpMethod] = useState<'GET' | 'POST' | 'PUT' | 'DELETE'>('POST');
  const [newEpPath, setNewEpPath] = useState<string>('/api/v1/items');
  const [newEpSummary, setNewEpSummary] = useState<string>('إضافة عنصر جديد إلى قاعدة البيانات');

  const allEndpoints = useMemo(() => {
    const defaults = activeProject ? EnterpriseSuiteService.getDefaultEndpoints(activeProject) : [];
    return [...defaults, ...customEndpoints];
  }, [activeProject, customEndpoints]);

  const openApiYaml = useMemo(
    () =>
      activeProject
        ? EnterpriseSuiteService.generateOpenApiYaml(activeProject, allEndpoints, serverUrl)
        : '',
    [activeProject, allEndpoints, serverUrl]
  );

  // 4. GitHub Publisher state
  const [githubToken, setGithubToken] = useState<string>('');
  const [githubRepoName, setGithubRepoName] = useState<string>(() =>
    activeProject
      ? activeProject.name.replace(/[\s/\\?%*:|"<>]/g, '-').toLowerCase() || 'forgeprom-project'
      : 'forgeprom-project'
  );
  const [isPrivateRepo, setIsPrivateRepo] = useState<boolean>(true);
  const [githubStatus, setGithubStatus] = useState<{
    loading: boolean;
    message: string;
    repoUrl?: string;
    isError?: boolean;
  } | null>(null);

  // 5. AES-256-GCM Encrypted Vault state
  const [vaultPassphrase, setVaultPassphrase] = useState<string>('');
  const [encryptedOutput, setEncryptedOutput] = useState<string>('');
  const [decryptInput, setDecryptInput] = useState<string>('');
  const [decryptPassphrase, setDecryptPassphrase] = useState<string>('');
  const [vaultMessage, setVaultMessage] = useState<{ text: string; isError?: boolean } | null>(null);

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleAddEndpoint = () => {
    if (!newEpPath.trim() || !newEpSummary.trim()) return;
    const ep: ApiEndpointSpec = {
      id: `ep_custom_${Date.now()}`,
      method: newEpMethod,
      path: newEpPath.startsWith('/') ? newEpPath.trim() : `/${newEpPath.trim()}`,
      summary: newEpSummary.trim(),
      authRequired: true,
      requestSample:
        newEpMethod === 'POST' || newEpMethod === 'PUT'
          ? JSON.stringify({ title: 'Sample Payload', timestamp: Date.now() }, null, 2)
          : undefined,
      responseSample: JSON.stringify({ success: true, timestamp: Date.now() }, null, 2),
    };
    setCustomEndpoints(prev => [...prev, ep]);
    setNewEpPath('/api/v1/');
    setNewEpSummary('');
  };

  const handlePublishToGitHubApi = async () => {
    if (!activeProject) return;
    if (!githubToken.trim()) {
      setGithubStatus({
        loading: false,
        isError: true,
        message: 'يرجى إدخال رمز GitHub Personal Access Token أو تحميل سكربت النشر التلقائي (deploy_to_github.sh).',
      });
      return;
    }

    setGithubStatus({ loading: true, message: 'جارٍ الاتصال بـ GitHub API وإنشاء المستودع...' });
    try {
      const headers = {
        Authorization: `Bearer ${githubToken.trim()}`,
        Accept: 'application/vnd.github+json',
        'Content-Type': 'application/json',
      };

      // 1. Create repository
      const createRes = await fetch('https://api.github.com/user/repos', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          name: githubRepoName.trim(),
          description: activeProject.description || 'Generated by Forgeprom Code Studio',
          private: isPrivateRepo,
          auto_init: true,
        }),
      });

      const repoData = await createRes.json();
      if (!createRes.ok) {
        throw new Error(repoData.message || 'تعذر إنشاء المستودع عبر GitHub API');
      }

      const fullName = repoData.full_name;
      const htmlUrl = repoData.html_url;

      // 2. Create Issues from Must-Have requirements
      const mustHave = activeProject.analysis?.functionalReqs?.mustHave || [
        'إعداد البنية المعمارية وقاعدة البيانات المحلية (Room / SQLite)',
        'تطبيق طبقة الأمان وتشفير الجلسات (OWASP Security Rules)',
      ];

      for (let i = 0; i < Math.min(mustHave.length, 5); i++) {
        await fetch(`https://api.github.com/repos/${fullName}/issues`, {
          method: 'POST',
          headers,
          body: JSON.stringify({
            title: `المتطلب الهندسي #${i + 1}: ${mustHave[i].slice(0, 75)}`,
            body: `تم توليد هذه المهمة تلقائياً بواسطة **Forgeprom Code Studio**.\n\n### المتطلب:\n${mustHave[i]}`,
            labels: ['enhancement'],
          }),
        });
      }

      setGithubStatus({
        loading: false,
        message: `تم إنشاء المستودع (${fullName}) ورفع المهام الهندسية (GitHub Issues) بنجاح!`,
        repoUrl: htmlUrl,
      });
    } catch (err) {
      setGithubStatus({
        loading: false,
        isError: true,
        message: err instanceof Error ? err.message : 'حدث خطأ أثناء الاتصال بـ GitHub API',
      });
    }
  };

  const handleEncryptProject = async () => {
    if (!activeProject) return;
    if (vaultPassphrase.length < 4) {
      setVaultMessage({ text: 'يرجى إدخال كلمة مرور أو رمز PIN لا يقل عن 4 أحرف للتشفير.', isError: true });
      return;
    }
    try {
      const cipherJson = await EnterpriseSuiteService.encryptProjectVault(activeProject, vaultPassphrase);
      setEncryptedOutput(cipherJson);
      setVaultMessage({
        text: `تم تشفير المشروع "${activeProject.name}" بنجاح بمعيار AES-256-GCM (PBKDF2 100,000 دورة).`,
      });
    } catch {
      setVaultMessage({ text: 'تعذر تشفير المشروع.', isError: true });
    }
  };

  const handleDecryptProject = async () => {
    if (!decryptInput.trim() || !decryptPassphrase) {
      setVaultMessage({ text: 'يرجى إدخال النص المشفر وكلمة المرور لفك التشفير.', isError: true });
      return;
    }
    try {
      const restored = await EnterpriseSuiteService.decryptProjectVault(decryptInput, decryptPassphrase);
      onImportDecryptedProject({
        ...restored,
        id: `proj_restored_${Date.now()}`,
        name: `${restored.name} (مستعاد من الخزنة)`,
      });
      setVaultMessage({ text: `تم فك تشفير المشروع "${restored.name}" واستيراده بنجاح!` });
      setDecryptInput('');
    } catch {
      setVaultMessage({
        text: 'فشل فك التشفير: كلمة المرور غير صحيحة أو أن ملف الخزنة تالف.',
        isError: true,
      });
    }
  };

  if (!activeProject) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 bg-slate-900 border border-slate-800 rounded-xl text-center space-y-3">
        <h2 className="text-base font-bold text-white">لا يوجد مشروع نشط</h2>
        <p className="text-xs text-slate-400">يرجى إنشاء مشروع أولاً لتشغيل أدوات الفحص الأمني وحاسبة التكاليف وتوليد عقود الـ API.</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Top Header & Project Selector */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-5 border-b border-slate-800">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
            حزمة الهندسة المؤسسية والأمان (Enterprise Suite)
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            تدقيق الأمان OWASP · حاسبة التكاليف FinOps · عقود OpenAPI & Postman · النشر إلى GitHub · خزنة التشفير AES-256
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label className="text-xs text-slate-400 whitespace-nowrap">المشروع المستهدف:</label>
          <select
            value={activeProject.id}
            onChange={e => onSelectProject(e.target.value)}
            className="bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-purple-500"
          >
            {projects.map(p => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Interactive Module Selector Tabs */}
      <div className="flex items-center gap-1.5 p-1.5 bg-slate-900 border border-slate-800 rounded-xl overflow-x-auto">
        <button
          onClick={() => setActiveModule('security')}
          className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            activeModule === 'security'
              ? 'bg-purple-700 text-white shadow-sm'
              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <ShieldCheck className="w-4 h-4" />
          <span>01. مدقق الأمان (OWASP)</span>
        </button>

        <button
          onClick={() => setActiveModule('finops')}
          className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            activeModule === 'finops'
              ? 'bg-purple-700 text-white shadow-sm'
              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <Calculator className="w-4 h-4" />
          <span>02. حاسبة التكاليف (FinOps)</span>
        </button>

        <button
          onClick={() => setActiveModule('openapi')}
          className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            activeModule === 'openapi'
              ? 'bg-purple-700 text-white shadow-sm'
              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <FileJson className="w-4 h-4" />
          <span>03. عقود الـ API (Swagger & Postman)</span>
        </button>

        <button
          onClick={() => setActiveModule('github')}
          className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            activeModule === 'github'
              ? 'bg-purple-700 text-white shadow-sm'
              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <GitBranch className="w-4 h-4" />
          <span>04. النشر إلى GitHub والمهام</span>
        </button>

        <button
          onClick={() => setActiveModule('vault')}
          className={`flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
            activeModule === 'vault'
              ? 'bg-purple-700 text-white shadow-sm'
              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
          }`}
        >
          <Lock className="w-4 h-4" />
          <span>05. الخزنة المشفرة (AES-256)</span>
        </button>
      </div>

      {/* MODULE 1: OWASP Security & Threat Modeling Auditor */}
      {activeModule === 'security' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-900 border border-slate-800 rounded-xl p-5">
            <div className="space-y-1">
              <h2 className="text-base font-bold text-white">
                تقرير الفحص الأمني ونمذجة التهديدات (OWASP API & Mobile MASVS)
              </h2>
              <p className="text-xs text-slate-400">
                فحص معماري تلقائي لمشروع &quot;{activeProject.name}&quot; مع قواعد الحماية والـ Middleware الجاهزة للنسخ.
              </p>
            </div>
            <button
              onClick={() => EnterpriseSuiteService.exportSecurityAuditMarkdown(activeProject, securityFindings)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>تصدير التقرير الأمني (.md)</span>
            </button>
          </div>

          <div className="grid grid-cols-1 gap-5">
            {securityFindings.map((finding, idx) => {
              const isResolved = mitigatedIds[finding.id] ?? finding.status === 'mitigated';
              return (
                <div
                  key={finding.id}
                  className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-slate-800">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2 text-xs text-slate-400">
                        <span className="font-mono tabular-nums">0{idx + 1}</span>
                        <span aria-hidden="true">·</span>
                        <span>{finding.owaspCategory}</span>
                        <span aria-hidden="true">·</span>
                        <span
                          className={
                            finding.severity === 'critical'
                              ? 'text-rose-400 font-semibold'
                              : 'text-amber-400 font-semibold'
                          }
                        >
                          {finding.severity === 'critical' ? 'أولوية قصوى (Critical)' : 'أولوية عالية (High)'}
                        </span>
                      </div>
                      <h3 className="text-sm sm:text-base font-bold text-white">{finding.title}</h3>
                    </div>

                    <button
                      onClick={() =>
                        setMitigatedIds(prev => ({ ...prev, [finding.id]: !isResolved }))
                      }
                      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        isResolved
                          ? 'bg-emerald-950/60 border-emerald-700/50 text-emerald-300'
                          : 'bg-amber-950/60 border-amber-700/50 text-amber-300'
                      }`}
                    >
                      {isResolved ? (
                        <>
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                          <span>تم التأمين (Mitigated)</span>
                        </>
                      ) : (
                        <>
                          <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                          <span>يتطلب التفعيل (تحديد كمكتمل)</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs leading-relaxed">
                    <div>
                      <span className="text-slate-400 font-semibold block mb-1">تحليل التهديد:</span>
                      <p className="text-slate-200">{finding.description}</p>
                    </div>
                    <div>
                      <span className="text-slate-400 font-semibold block mb-1">الضابط الهندسي الموصى به:</span>
                      <p className="text-emerald-300">{finding.remediation}</p>
                    </div>
                  </div>

                  <div className="space-y-2 pt-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono text-slate-400">{finding.codeSnippetTitle}</span>
                      <button
                        onClick={() => handleCopy(finding.codeSnippet, finding.id)}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-md transition-colors cursor-pointer"
                      >
                        {copiedId === finding.id ? (
                          <>
                            <Check className="w-3.5 h-3.5 text-emerald-400" />
                            <span>تم النسخ</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>نسخ كود الحماية</span>
                          </>
                        )}
                      </button>
                    </div>
                    <pre
                      dir="ltr"
                      className="bg-slate-950 border border-slate-800 rounded-lg p-3.5 text-[11px] font-mono text-slate-200 overflow-x-auto leading-relaxed"
                    >
                      {finding.codeSnippet}
                    </pre>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* MODULE 2: Cloud & LLM FinOps Cost Estimator */}
      {activeModule === 'finops' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
            {/* Interactive Sliders Panel */}
            <div className="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-5">
              <div className="border-b border-slate-800 pb-3">
                <h2 className="text-base font-bold text-white">محددات التشغيل وحجم الاستخدام</h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  اضبط عدد المستخدمين ومعدل استهلاك الذكاء الاصطناعي لحساب التكلفة الشهرية والوفر المالي.
                </p>
              </div>

              <div className="space-y-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-300">المستخدمون النشطون شهرياً (MAU):</span>
                    <span className="font-mono tabular-nums font-bold text-white">
                      {mau.toLocaleString('en-US')} users
                    </span>
                  </div>
                  <input
                    type="range"
                    min={500}
                    max={250000}
                    step={500}
                    value={mau}
                    onChange={e => setMau(Number(e.target.value))}
                    className="w-full accent-purple-600 cursor-pointer"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-300">متوسط طلبات الذكاء الاصطناعي يومياً لكل مستخدم:</span>
                    <span className="font-mono tabular-nums font-bold text-white">{dailyPrompts} req/day</span>
                  </div>
                  <input
                    type="range"
                    min={1}
                    max={40}
                    step={1}
                    value={dailyPrompts}
                    onChange={e => setDailyPrompts(Number(e.target.value))}
                    className="w-full accent-purple-600 cursor-pointer"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-300">متوسط حجم التوكنز للطلب الواحد (Input + Output):</span>
                    <span className="font-mono tabular-nums font-bold text-white">
                      {avgTokens.toLocaleString('en-US')} tokens
                    </span>
                  </div>
                  <input
                    type="range"
                    min={500}
                    max={8000}
                    step={250}
                    value={avgTokens}
                    onChange={e => setAvgTokens(Number(e.target.value))}
                    className="w-full accent-purple-600 cursor-pointer"
                  />
                </div>

                <div className="space-y-1.5 pt-2 border-t border-slate-800">
                  <div className="flex justify-between text-xs">
                    <span className="text-emerald-300 font-medium">
                      نسبة المعالجة المحلية على الهاتف (On-Device GGUF Offload):
                    </span>
                    <span className="font-mono tabular-nums font-bold text-emerald-400">
                      {localOffloadPercent}%
                    </span>
                  </div>
                  <input
                    type="range"
                    min={0}
                    max={100}
                    step={5}
                    value={localOffloadPercent}
                    onChange={e => setLocalOffloadPercent(Number(e.target.value))}
                    className="w-full accent-emerald-500 cursor-pointer"
                  />
                </div>
              </div>
            </div>

            {/* Savings Summary & Comparison Table */}
            <div className="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between space-y-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-base font-bold text-white">مقارنة تكاليف السحابة مقابل المحرك الهجين (GGUF)</h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    إجمالي الاستهلاك الشهري المقدر:{' '}
                    <span className="font-mono tabular-nums text-white font-semibold">
                      {finops.totalMonthlyTokensMillions.toLocaleString('en-US')}M Tokens / Month
                    </span>
                  </p>
                </div>
                <div className="text-right sm:text-left">
                  <span className="text-[11px] text-slate-400 block">الوفر السنوي مع البنية الهجينة (GGUF):</span>
                  <span className="text-xl font-bold font-mono tabular-nums text-emerald-400">
                    ${finops.annualSavingsUsd.toLocaleString('en-US')}/yr
                  </span>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-right text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400">
                      <th className="py-2.5 px-3 font-medium">المزود / النموذج</th>
                      <th className="py-2.5 px-3 font-medium text-left">تكلفة النماذج (LLM)</th>
                      <th className="py-2.5 px-3 font-medium text-left">استضافة الخادم</th>
                      <th className="py-2.5 px-3 font-medium text-left">الإجمالي الشهري</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/70">
                    {finops.providers.map(p => (
                      <tr key={p.modelName} className="hover:bg-slate-800/40 transition-colors">
                        <td className="py-3 px-3 font-medium text-white">{p.name}</td>
                        <td className="py-3 px-3 font-mono tabular-nums text-left text-slate-300">
                          ${p.monthlyLlmCostUsd.toLocaleString('en-US')}
                        </td>
                        <td className="py-3 px-3 font-mono tabular-nums text-left text-slate-400">
                          ${p.monthlyInfraCostUsd.toLocaleString('en-US')}
                        </td>
                        <td className="py-3 px-3 font-mono tabular-nums text-left font-bold text-white">
                          ${p.totalMonthlyUsd.toLocaleString('en-US')}/mo
                        </td>
                      </tr>
                    ))}
                    <tr className="bg-emerald-950/30">
                      <td className="py-3 px-3 font-bold text-emerald-300">
                        Forgeprom Hybrid (محلي {localOffloadPercent}% GGUF + سحابي {100 - localOffloadPercent}%)
                      </td>
                      <td className="py-3 px-3 font-mono tabular-nums text-left text-emerald-300">
                        $0.00 (On-Device)
                      </td>
                      <td className="py-3 px-3 font-mono tabular-nums text-left text-emerald-300">
                        منخفضة
                      </td>
                      <td className="py-3 px-3 font-mono tabular-nums text-left font-extrabold text-emerald-400">
                        ${finops.hybridLocalFirstMonthlyUsd.toLocaleString('en-US')}/mo
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODULE 3: OpenAPI 3.1 / Swagger & Postman Collection Generator */}
      {activeModule === 'openapi' && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-900 border border-slate-800 rounded-xl p-5">
            <div className="space-y-1">
              <h2 className="text-base font-bold text-white">
                مولّد عقود الـ API القياسية (OpenAPI 3.1.0 & Postman Collection v2.1)
              </h2>
              <p className="text-xs text-slate-400">
                صدّر ملفات Swagger و Postman الجاهزة للاستيراد الفوري لاختبار نقاط النهاية لمشروع &quot;{activeProject.name}&quot;.
              </p>
            </div>
            <div className="flex items-center gap-2.5 shrink-0">
              <button
                onClick={() => {
                  const safeName = activeProject.name.replace(/[\s/\\?%*:|"<>]/g, '_');
                  ExportService.downloadTextFile(
                    `${safeName}_openapi_3_1.yaml`,
                    openApiYaml,
                    'text/yaml;charset=utf-8'
                  );
                }}
                className="inline-flex items-center gap-2 px-3.5 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg transition-colors whitespace-nowrap cursor-pointer"
              >
                <Download className="w-4 h-4" />
                <span>تنزيل openapi.yaml</span>
              </button>

              <button
                onClick={() => {
                  const safeName = activeProject.name.replace(/[\s/\\?%*:|"<>]/g, '_');
                  const json = EnterpriseSuiteService.generatePostmanCollectionJson(
                    activeProject,
                    allEndpoints,
                    serverUrl
                  );
                  ExportService.downloadTextFile(
                    `${safeName}.postman_collection.json`,
                    json,
                    'application/json;charset=utf-8'
                  );
                }}
                className="inline-flex items-center gap-2 px-3.5 py-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-white text-xs font-semibold rounded-lg transition-colors whitespace-nowrap cursor-pointer"
              >
                <FileJson className="w-4 h-4 text-purple-400" />
                <span>تنزيل Postman Collection</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Endpoints List & Custom Builder */}
            <div className="lg:col-span-6 bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
              <div className="space-y-2">
                <label className="text-xs text-slate-400 block">رابط الخادم الأساسي (Base Server URL):</label>
                <input
                  type="text"
                  dir="ltr"
                  value={serverUrl}
                  onChange={e => setServerUrl(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-slate-200"
                />
              </div>

              <div className="space-y-2.5 pt-2">
                <h3 className="text-xs font-bold text-slate-300">
                  نقاط النهاية المعتمدة ({allEndpoints.length} Endpoints):
                </h3>
                <div className="space-y-2">
                  {allEndpoints.map(ep => (
                    <div
                      key={ep.id}
                      className="flex items-center justify-between gap-3 p-3 bg-slate-950 border border-slate-800 rounded-lg"
                    >
                      <div className="space-y-0.5 min-w-0">
                        <div className="flex items-center gap-2" dir="ltr">
                          <span
                            className={`font-mono text-[11px] font-bold ${
                              ep.method === 'GET'
                                ? 'text-emerald-400'
                                : ep.method === 'POST'
                                ? 'text-purple-400'
                                : ep.method === 'DELETE'
                                ? 'text-rose-400'
                                : 'text-amber-400'
                            }`}
                          >
                            {ep.method}
                          </span>
                          <span className="font-mono text-xs text-white truncate">{ep.path}</span>
                        </div>
                        <p className="text-[11px] text-slate-400 truncate">{ep.summary}</p>
                      </div>
                      {ep.id.startsWith('ep_custom_') && (
                        <button
                          onClick={() =>
                            setCustomEndpoints(prev => prev.filter(item => item.id !== ep.id))
                          }
                          className="p-1.5 text-slate-500 hover:text-rose-400 cursor-pointer"
                          title="حذف النقطة"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Add Custom Endpoint */}
              <div className="pt-3 border-t border-slate-800 space-y-3">
                <h4 className="text-xs font-semibold text-white">إضافة نقطة نهاية مخصصة (Custom Endpoint):</h4>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  <select
                    value={newEpMethod}
                    onChange={e => setNewEpMethod(e.target.value as 'GET' | 'POST' | 'PUT' | 'DELETE')}
                    className="bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-white"
                  >
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                    <option value="PUT">PUT</option>
                    <option value="DELETE">DELETE</option>
                  </select>
                  <input
                    type="text"
                    dir="ltr"
                    placeholder="/api/v1/orders"
                    value={newEpPath}
                    onChange={e => setNewEpPath(e.target.value)}
                    className="sm:col-span-2 bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-white"
                  />
                </div>
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="وصف وظيفة النقطة..."
                    value={newEpSummary}
                    onChange={e => setNewEpSummary(e.target.value)}
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white"
                  />
                  <button
                    onClick={handleAddEndpoint}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg whitespace-nowrap shrink-0 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>إضافة</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Live OpenAPI YAML Preview */}
            <div className="lg:col-span-6 bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between space-y-3">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <span className="text-xs font-mono text-slate-300">openapi.yaml (OpenAPI 3.1.0 Preview)</span>
                <button
                  onClick={() => handleCopy(openApiYaml, 'openapi-yaml')}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs text-slate-300 hover:text-white bg-slate-800 rounded-md cursor-pointer"
                >
                  {copiedId === 'openapi-yaml' ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                      <span>تم النسخ</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" />
                      <span>نسخ العقد</span>
                    </>
                  )}
                </button>
              </div>
              <pre
                dir="ltr"
                className="flex-1 bg-slate-950 border border-slate-800 rounded-lg p-4 text-[11px] font-mono text-slate-200 overflow-auto max-h-96 leading-relaxed"
              >
                {openApiYaml}
              </pre>
            </div>
          </div>
        </div>
      )}

      {/* MODULE 4: One-Click GitHub Repository & Automated Issues Publisher */}
      {activeModule === 'github' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-6 bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="border-b border-slate-800 pb-3">
              <h2 className="text-base font-bold text-white">
                النشر المباشر إلى GitHub وتوليد المهام (GitHub API)
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                أنشئ مستودعاً جديداً وحوّل متطلبات الـ PRD تلقائياً إلى GitHub Issues جاهزة للتنفيذ.
              </p>
            </div>

            <div className="space-y-3">
              <div>
                <label className="text-xs text-slate-300 block mb-1">اسم المستودع (Repository Name):</label>
                <input
                  type="text"
                  dir="ltr"
                  value={githubRepoName}
                  onChange={e => setGithubRepoName(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-white"
                />
              </div>

              <div>
                <label className="text-xs text-slate-300 block mb-1">
                  رمز الوصول الشخصي (GitHub Personal Access Token - اختياري للاتصال المباشر):
                </label>
                <input
                  type="password"
                  dir="ltr"
                  placeholder="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                  value={githubToken}
                  onChange={e => setGithubToken(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-white"
                />
                <span className="text-[11px] text-slate-400 mt-1 block">
                  لا يتم حفظ الرمز على أي خادم خارجي؛ يُستخدم مباشرة من متصفحك إلى api.github.com.
                </span>
              </div>

              <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={isPrivateRepo}
                  onChange={e => setIsPrivateRepo(e.target.checked)}
                  className="accent-purple-600"
                />
                <span>إنشاء كمستودع خاص (Private Repository)</span>
              </label>

              {githubStatus && (
                <div
                  className={`p-3.5 rounded-lg border text-xs ${
                    githubStatus.isError
                      ? 'bg-rose-950/50 border-rose-800 text-rose-200'
                      : 'bg-emerald-950/50 border-emerald-800 text-emerald-200'
                  }`}
                >
                  <p>{githubStatus.message}</p>
                  {githubStatus.repoUrl && (
                    <a
                      href={githubStatus.repoUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-block mt-2 underline font-mono text-emerald-300"
                    >
                      {githubStatus.repoUrl}
                    </a>
                  )}
                </div>
              )}

              <div className="flex flex-wrap items-center gap-3 pt-2">
                <button
                  onClick={handlePublishToGitHubApi}
                  disabled={githubStatus?.loading}
                  className="inline-flex items-center gap-2 px-4 py-2.5 bg-purple-700 hover:bg-purple-600 disabled:opacity-50 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                >
                  <GitBranch className="w-4 h-4" />
                  <span>إنشاء المستودع ورفع المهام الآن</span>
                </button>

                <button
                  onClick={() => {
                    const script = EnterpriseSuiteService.generateGitHubAutomationScript(
                      activeProject,
                      githubRepoName,
                      isPrivateRepo
                    );
                    ExportService.downloadTextFile(
                      `deploy_${githubRepoName}_github.sh`,
                      script,
                      'text/x-shellscript;charset=utf-8'
                    );
                  }}
                  className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                >
                  <Download className="w-4 h-4 text-purple-400" />
                  <span>تنزيل سكربت الأتمتة المحلي (deploy_to_github.sh)</span>
                </button>
              </div>
            </div>
          </div>

          <div className="lg:col-span-6 bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <span className="text-xs font-mono text-slate-300">deploy_to_github.sh (Git & GitHub CLI Script)</span>
              <button
                onClick={() =>
                  handleCopy(
                    EnterpriseSuiteService.generateGitHubAutomationScript(
                      activeProject,
                      githubRepoName,
                      isPrivateRepo
                    ),
                    'gh-script'
                  )
                }
                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs text-slate-300 hover:text-white bg-slate-800 rounded-md cursor-pointer"
              >
                {copiedId === 'gh-script' ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span>تم النسخ</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    <span>نسخ السكربت</span>
                  </>
                )}
              </button>
            </div>
            <pre
              dir="ltr"
              className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-[11px] font-mono text-slate-200 overflow-auto max-h-80 leading-relaxed"
            >
              {EnterpriseSuiteService.generateGitHubAutomationScript(
                activeProject,
                githubRepoName,
                isPrivateRepo
              )}
            </pre>
          </div>
        </div>
      )}

      {/* MODULE 5: Biometric & AES-256-GCM Encrypted Project Vault */}
      {activeModule === 'vault' && (
        <div className="space-y-6">
          {vaultMessage && (
            <div
              className={`p-4 rounded-xl border text-xs flex items-center justify-between ${
                vaultMessage.isError
                  ? 'bg-rose-950/60 border-rose-800 text-rose-200'
                  : 'bg-emerald-950/60 border-emerald-800 text-emerald-200'
              }`}
            >
              <span>{vaultMessage.text}</span>
              <button
                onClick={() => setVaultMessage(null)}
                className="text-slate-400 hover:text-white text-xs underline cursor-pointer"
              >
                إغلاق
              </button>
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Encrypt Active Project */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
              <div className="border-b border-slate-800 pb-3">
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <Lock className="w-4 h-4 text-purple-400" />
                  <span>تشفير المشروع بمعيار AES-256-GCM</span>
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  تشفير حقيقي عبر WebCrypto (PBKDF2 100,000 دورة + AES-GCM 256-bit) لحماية الملكية الفكرية لمشروعك.
                </p>
              </div>

              <div className="space-y-3">
                <div>
                  <label className="text-xs text-slate-300 block mb-1">
                    كلمة مرور الخزنة أو رمز القفل (Vault Passphrase):
                  </label>
                  <input
                    type="password"
                    placeholder="أدخل كلمة مرور قوية لتشفير المشروع..."
                    value={vaultPassphrase}
                    onChange={e => setVaultPassphrase(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white"
                  />
                </div>

                <div className="flex flex-wrap gap-2.5">
                  <button
                    onClick={handleEncryptProject}
                    className="inline-flex items-center gap-2 px-4 py-2 bg-purple-700 hover:bg-purple-600 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                  >
                    <KeyRound className="w-4 h-4" />
                    <span>تشفير المشروع الحالي</span>
                  </button>

                  {encryptedOutput && (
                    <button
                      onClick={() => {
                        const safeName = activeProject.name.replace(/[\s/\\?%*:|"<>]/g, '_');
                        ExportService.downloadTextFile(
                          `${safeName}.forgevault.json`,
                          encryptedOutput,
                          'application/json;charset=utf-8'
                        );
                      }}
                      className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-700 hover:bg-emerald-600 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                    >
                      <Download className="w-4 h-4" />
                      <span>تنزيل ملف الخزنة المشفر (.forgevault)</span>
                    </button>
                  )}
                </div>

                {encryptedOutput && (
                  <pre
                    dir="ltr"
                    className="bg-slate-950 border border-slate-800 rounded-lg p-3 text-[11px] font-mono text-emerald-300 overflow-auto max-h-48"
                  >
                    {encryptedOutput}
                  </pre>
                )}
              </div>
            </div>

            {/* Decrypt & Restore Vault */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
              <div className="border-b border-slate-800 pb-3">
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <Unlock className="w-4 h-4 text-emerald-400" />
                  <span>فك تشفير واستعادة مشروع من الخزنة</span>
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  الصق محتوى ملف `.forgevault.json` أو قم برفعه ثم أدخل كلمة المرور لاستعادة المشروع بالكامل.
                </p>
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="text-xs text-slate-300">البيانات المشفرة (AES-256-GCM JSON):</label>
                  <label className="inline-flex items-center gap-1.5 text-xs text-purple-400 hover:text-purple-300 cursor-pointer">
                    <Upload className="w-3.5 h-3.5" />
                    <span>رفع ملف .forgevault</span>
                    <input
                      type="file"
                      accept=".json,.forgevault"
                      className="hidden"
                      onChange={e => {
                        const file = e.target.files?.[0];
                        if (!file) return;
                        const reader = new FileReader();
                        reader.onload = ev => {
                          setDecryptInput(String(ev.target?.result || ''));
                        };
                        reader.readAsText(file);
                      }}
                    />
                  </label>
                </div>

                <textarea
                  dir="ltr"
                  rows={4}
                  placeholder='{"cipher": "AES-256-GCM", "salt": "...", "iv": "...", "payload": "..."}'
                  value={decryptInput}
                  onChange={e => setDecryptInput(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs font-mono text-slate-200"
                />

                <div>
                  <label className="text-xs text-slate-300 block mb-1">كلمة المرور لفك التشفير:</label>
                  <input
                    type="password"
                    placeholder="أدخل كلمة المرور..."
                    value={decryptPassphrase}
                    onChange={e => setDecryptPassphrase(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-white"
                  />
                </div>

                <button
                  onClick={handleDecryptProject}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-700 hover:bg-emerald-600 text-white text-xs font-semibold rounded-lg transition-colors cursor-pointer"
                >
                  <Unlock className="w-4 h-4" />
                  <span>فك التشفير واستيراد المشروع</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
