export type ProjectType =
  | 'full_app'
  | 'new_feature'
  | 'refactor'
  | 'bug_fix'
  | 'idea_analysis'
  | 'general_prompt';

export type TargetPlatform =
  | 'android_studio_ai'
  | 'cursor'
  | 'claude_code'
  | 'bolt'
  | 'lovable'
  | 'windsurf'
  | 'general';

export type DetailLevel = 'concise' | 'balanced' | 'comprehensive';

export interface IdeaAnalysis {
  problem: string;
  primaryGoal: string;
  subGoals: string[];
  targetAudience: string;
  functionalReqs: {
    mustHave: string[];
    niceToHave: string[];
  };
  nonFunctionalReqs: {
    performance: string[];
    security: string[];
    offline: string[];
  };
  constraints: string[];
  ambiguities: string[];
  assumptions: string[]; // الافتراضات المقترحة القابلة للتعديل
  verifiedFacts: string[]; // الحقائق المؤكدة من كلام المستخدم
  clarifyingQuestions: string[];
}

export interface PromptVersion {
  id: string;
  versionNumber: number;
  timestamp: string;
  targetPlatform: TargetPlatform;
  detailLevel: DetailLevel;
  promptText: string;
  customDirectives?: string;
  notes?: string;
}

export interface ChainedPRD {
  step1_prd?: string;
  step2_architecture?: string;
  step3_screens_stories?: string;
  step4_database_api?: string;
  step5_roadmap_migration?: string;
}

export interface QualityIssue {
  id: string;
  severity: 'high' | 'medium' | 'low';
  category: string;
  title: string;
  reason: string;
  suggestedFix: string;
  applied?: boolean;
}

export interface QualityReport {
  overallAssessment: string;
  score: number;
  issues: QualityIssue[];
  strengths: string[];
}

export interface Project {
  id: string;
  name: string;
  description: string;
  type: ProjectType;
  category: string;
  tags: string[];
  targetPlatform: TargetPlatform;
  detailLevel: DetailLevel;
  isPinned: boolean;
  isArchived: boolean;
  createdAt: string;
  updatedAt: string;
  rawIdea: string;
  analysis?: IdeaAnalysis;
  prompts: PromptVersion[];
  currentPromptIndex: number;
  chainedPrd?: ChainedPRD;
  qualityReport?: QualityReport;
  customDirectives?: string;
}

export interface LocalModel {
  id: string;
  name: string;
  family: string;
  parameters: string;
  format: 'GGUF Q4_K_M' | 'GGUF Q4_0' | 'MediaPipe';
  fileSizeMB: number;
  requiredRamMB: number;
  targetPhoneRam: '4GB' | '6GB+' | '8GB+';
  arabicQualityRating: number; // 1-5
  status: 'installed' | 'available' | 'downloading' | 'failed';
  downloadProgress?: number;
  sha256Expected: string;
  sha256Calculated?: string;
  checksumVerified?: boolean;
  description: string;
  strengths: string[];
}

export interface HardwareStatus {
  totalRamMB: number;
  availableRamMB: number;
  jvmHeapUsedMB: number;
  nativeHeapUsedMB: number;
  maxSafeModelSizeMB: number;
  cpuCores: number;
  activeThreads: number;
  thermalState: 'cool' | 'normal' | 'warm';
  isAirplaneMode: boolean; // Local-First offline enforcement
  activeModelId: string | null;
  isGenerating: boolean;
  threadCount?: number;
  isEcoMode?: boolean;
}

export interface VisionAnalysisResult {
  screenName: string;
  components: string[];
  visualHierarchy: string[];
  missingStates: string[];
  composeCodeSnippet: string;
  generatedPrompt: string;
  ocrDetectedText?: string[];
}
