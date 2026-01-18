/**
 * Shared TypeScript types for the Constellation Engine MCP Server
 */

// Module names that can be tested/built
export type ModuleName =
  | 'all'
  | 'core'
  | 'runtime'
  | 'parser'
  | 'compiler'
  | 'lsp'
  | 'http'
  | 'stdlib';

// Test result types
export interface TestSummary {
  passed: number;
  failed: number;
  skipped: number;
}

export interface FailedTest {
  name: string;
  suite: string;
  message: string;
  location?: string;
}

export interface TestResult {
  success: boolean;
  exitCode: number;
  stdout: string;
  stderr: string;
  duration: number;
  summary: TestSummary;
  failedTests?: FailedTest[];
}

export interface CachedTestResult {
  available: boolean;
  lastRunTimestamp?: string;
  summary?: TestSummary;
  failedTests?: FailedTest[];
}

// Build result types
export interface BuildError {
  file: string;
  line: number;
  column: number;
  message: string;
  severity: 'error' | 'warning';
}

export interface BuildResult {
  success: boolean;
  exitCode: number;
  errors: BuildError[];
  warnings: number;
  duration: number;
}

// Git types
export interface WorktreeInfo {
  path: string;
  isValid: boolean;
  gitDir: string;
}

export interface BranchInfo {
  name: string;
  isFeatureBranch: boolean;
  issueNumber: number | null;
  aheadBehind: {
    ahead: number;
    behind: number;
  };
}

export interface RepoStatus {
  hasUncommittedChanges: boolean;
  stagedFiles: string[];
  modifiedFiles: string[];
  untrackedFiles: string[];
}

export interface AgentContext {
  agentNumber: number | null;
  worktree: WorktreeInfo;
  branch: BranchInfo;
  repoStatus: RepoStatus;
}

export interface WorktreeListItem {
  path: string;
  branch: string;
  commit: string;
}

export interface WorktreeVerification {
  isValidWorktree: boolean;
  isMainRepo: boolean;
  currentPath: string;
  recommendation: string | null;
  availableWorktrees: WorktreeListItem[];
}

// Session types
export interface CommitInfo {
  sha: string;
  message: string;
  date: string;
}

export interface UncommittedChanges {
  staged: string[];
  unstaged: string[];
  untracked: string[];
}

export interface RebaseStatus {
  needsRebase: boolean;
  commitsAhead: number;
  commitsBehind: number;
}

export interface ChangesSummary {
  filesChanged: number;
  insertions: number;
  deletions: number;
}

export interface OpenPR {
  number: number;
  title: string;
  url: string;
}

export interface SessionResumeInfo {
  agentNumber: number;
  currentBranch: string;
  issueNumber: number | null;
  lastCommits: CommitInfo[];
  uncommittedChanges: UncommittedChanges;
  rebaseStatus: RebaseStatus;
  changesSinceBranch: ChangesSummary;
  openPR: OpenPR | null;
}

// Queue types
export interface QueueIssue {
  priority: number;
  issueNumber: number;
  title: string;
  status: 'pending' | 'in_progress' | 'blocked' | 'ready_for_review';
  branch?: string;
}

export interface CompletedIssue {
  issueNumber: number;
  title: string;
  pr?: number;
}

export interface AgentQueue {
  track: string;
  focus: string;
  assignedIssues: QueueIssue[];
  completedIssues: CompletedIssue[];
  notes: string;
  dependencies: string;
}

// Handoff types
export type HandoffStatus = 'in_progress' | 'blocked' | 'ready_for_review';

export interface HandoffData {
  agentNumber: number;
  issueNumber: number;
  status: HandoffStatus;
  summary: string;
  nextSteps: string[];
  blockers?: string[];
}

export interface HandoffResult {
  saved: boolean;
  handoffPath: string;
  timestamp: string;
}

// Affected tests types
export interface AffectedTestsResult {
  affectedModules: ModuleName[];
  success: boolean;
  results: Array<{
    module: ModuleName;
    result: TestResult;
  }>;
}

// Process execution types
export interface ExecResult {
  stdout: string;
  stderr: string;
  exitCode: number;
}
