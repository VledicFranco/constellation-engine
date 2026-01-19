/**
 * Git command wrappers
 */

import { execAsync } from './process.js';
import type {
  WorktreeInfo,
  BranchInfo,
  RepoStatus,
  CommitInfo,
  WorktreeListItem,
  UncommittedChanges,
  RebaseStatus,
  ChangesSummary,
  OpenPR,
} from '../types/index.js';

/**
 * Check if directory is a git worktree (not main repo)
 */
export async function isWorktree(cwd?: string): Promise<boolean> {
  const result = await execAsync('git rev-parse --git-dir', { cwd });
  if (result.exitCode !== 0) return false;

  const gitDir = result.stdout.trim();
  return gitDir.includes('worktrees');
}

/**
 * Check if directory is the main git repository
 */
export async function isMainRepo(cwd?: string): Promise<boolean> {
  const result = await execAsync('git rev-parse --git-dir', { cwd });
  if (result.exitCode !== 0) return false;

  const gitDir = result.stdout.trim();
  return gitDir === '.git' || gitDir.endsWith('.git');
}

/**
 * Get worktree information for current directory
 */
export async function getWorktreeInfo(cwd?: string): Promise<WorktreeInfo> {
  const gitDirResult = await execAsync('git rev-parse --git-dir', { cwd });
  const topLevelResult = await execAsync('git rev-parse --show-toplevel', { cwd });

  if (gitDirResult.exitCode !== 0 || topLevelResult.exitCode !== 0) {
    return {
      path: cwd ?? process.cwd(),
      isValid: false,
      gitDir: '',
    };
  }

  return {
    path: topLevelResult.stdout.trim(),
    isValid: true,
    gitDir: gitDirResult.stdout.trim(),
  };
}

/**
 * Get current branch information
 */
export async function getBranchInfo(cwd?: string): Promise<BranchInfo> {
  // Get current branch name
  const branchResult = await execAsync('git branch --show-current', { cwd });
  const branchName = branchResult.stdout.trim();

  // Parse issue number from branch name (agent-N/issue-NUM-description)
  const issueMatch = branchName.match(/agent-\d+\/issue-(\d+)/);
  const issueNumber = issueMatch ? parseInt(issueMatch[1], 10) : null;

  // Check if it's a feature branch
  const isFeatureBranch = /^agent-\d+\//.test(branchName);

  // Get ahead/behind count relative to origin/master
  let ahead = 0;
  let behind = 0;

  const countResult = await execAsync(
    'git rev-list --left-right --count origin/master...HEAD',
    { cwd }
  );

  if (countResult.exitCode === 0) {
    const parts = countResult.stdout.trim().split(/\s+/);
    if (parts.length === 2) {
      behind = parseInt(parts[0], 10) || 0;
      ahead = parseInt(parts[1], 10) || 0;
    }
  }

  return {
    name: branchName,
    isFeatureBranch,
    issueNumber,
    aheadBehind: { ahead, behind },
  };
}

/**
 * Get repository status (staged, modified, untracked files)
 */
export async function getRepoStatus(cwd?: string): Promise<RepoStatus> {
  const result = await execAsync('git status --porcelain', { cwd });

  const stagedFiles: string[] = [];
  const modifiedFiles: string[] = [];
  const untrackedFiles: string[] = [];

  if (result.exitCode === 0 && result.stdout.trim()) {
    const lines = result.stdout.trim().split('\n');

    for (const line of lines) {
      if (line.length < 3) continue;

      const index = line[0];
      const worktree = line[1];
      const file = line.slice(3);

      // Staged changes (index column)
      if (index !== ' ' && index !== '?') {
        stagedFiles.push(file);
      }

      // Worktree changes (worktree column)
      if (worktree === 'M' || worktree === 'D') {
        modifiedFiles.push(file);
      }

      // Untracked files
      if (index === '?' && worktree === '?') {
        untrackedFiles.push(file);
      }
    }
  }

  return {
    hasUncommittedChanges:
      stagedFiles.length > 0 ||
      modifiedFiles.length > 0 ||
      untrackedFiles.length > 0,
    stagedFiles,
    modifiedFiles,
    untrackedFiles,
  };
}

/**
 * Get list of recent commits
 */
export async function getRecentCommits(
  count: number = 5,
  cwd?: string
): Promise<CommitInfo[]> {
  const result = await execAsync(
    `git log -${count} --format="%H|%s|%aI"`,
    { cwd }
  );

  if (result.exitCode !== 0) return [];

  const commits: CommitInfo[] = [];
  const lines = result.stdout.trim().split('\n');

  for (const line of lines) {
    if (!line.trim()) continue;
    const parts = line.split('|');
    if (parts.length >= 3) {
      commits.push({
        sha: parts[0],
        message: parts[1],
        date: parts[2],
      });
    }
  }

  return commits;
}

/**
 * Get list of all worktrees
 */
export async function listWorktrees(cwd?: string): Promise<WorktreeListItem[]> {
  const result = await execAsync('git worktree list --porcelain', { cwd });

  if (result.exitCode !== 0) return [];

  const worktrees: WorktreeListItem[] = [];
  let current: Partial<WorktreeListItem> = {};

  const lines = result.stdout.trim().split('\n');
  for (const line of lines) {
    if (line.startsWith('worktree ')) {
      current.path = line.slice(9);
    } else if (line.startsWith('HEAD ')) {
      current.commit = line.slice(5);
    } else if (line.startsWith('branch ')) {
      current.branch = line.slice(7).replace('refs/heads/', '');
    } else if (line === '') {
      if (current.path && current.commit) {
        worktrees.push({
          path: current.path,
          branch: current.branch ?? '(detached)',
          commit: current.commit,
        });
      }
      current = {};
    }
  }

  // Don't forget last entry
  if (current.path && current.commit) {
    worktrees.push({
      path: current.path,
      branch: current.branch ?? '(detached)',
      commit: current.commit,
    });
  }

  return worktrees;
}

/**
 * Get uncommitted changes (staged, unstaged, untracked)
 */
export async function getUncommittedChanges(
  cwd?: string
): Promise<UncommittedChanges> {
  const status = await getRepoStatus(cwd);
  return {
    staged: status.stagedFiles,
    unstaged: status.modifiedFiles,
    untracked: status.untrackedFiles,
  };
}

/**
 * Get rebase status relative to origin/master
 */
export async function getRebaseStatus(cwd?: string): Promise<RebaseStatus> {
  // First fetch to get latest
  await execAsync('git fetch origin', { cwd });

  const result = await execAsync(
    'git rev-list --left-right --count origin/master...HEAD',
    { cwd }
  );

  if (result.exitCode !== 0) {
    return { needsRebase: false, commitsAhead: 0, commitsBehind: 0 };
  }

  const parts = result.stdout.trim().split(/\s+/);
  const commitsBehind = parseInt(parts[0], 10) || 0;
  const commitsAhead = parseInt(parts[1], 10) || 0;

  return {
    needsRebase: commitsBehind > 0,
    commitsAhead,
    commitsBehind,
  };
}

/**
 * Get changes since branching from master
 */
export async function getChangesSinceBranch(
  cwd?: string
): Promise<ChangesSummary> {
  const result = await execAsync(
    'git diff origin/master...HEAD --stat',
    { cwd }
  );

  if (result.exitCode !== 0) {
    return { filesChanged: 0, insertions: 0, deletions: 0 };
  }

  // Parse last line which looks like: "X files changed, Y insertions(+), Z deletions(-)"
  const lines = result.stdout.trim().split('\n');
  const lastLine = lines[lines.length - 1];

  const filesMatch = lastLine.match(/(\d+)\s+files?\s+changed/);
  const insertMatch = lastLine.match(/(\d+)\s+insertions?\(\+\)/);
  const deleteMatch = lastLine.match(/(\d+)\s+deletions?\(-\)/);

  return {
    filesChanged: filesMatch ? parseInt(filesMatch[1], 10) : 0,
    insertions: insertMatch ? parseInt(insertMatch[1], 10) : 0,
    deletions: deleteMatch ? parseInt(deleteMatch[1], 10) : 0,
  };
}

/**
 * Get list of files changed compared to a base branch
 */
export async function getChangedFiles(
  baseBranch: string = 'master',
  cwd?: string
): Promise<string[]> {
  const result = await execAsync(
    `git diff --name-only origin/${baseBranch}...HEAD`,
    { cwd }
  );

  if (result.exitCode !== 0) return [];

  return result.stdout
    .trim()
    .split('\n')
    .filter((f) => f.length > 0);
}

/**
 * Check for open PR using GitHub CLI
 */
export async function getOpenPR(
  branch: string,
  cwd?: string
): Promise<OpenPR | null> {
  const result = await execAsync(
    `gh pr list --head "${branch}" --json number,title,url --limit 1`,
    { cwd }
  );

  if (result.exitCode !== 0) return null;

  try {
    const prs = JSON.parse(result.stdout);
    if (prs.length > 0) {
      return {
        number: prs[0].number,
        title: prs[0].title,
        url: prs[0].url,
      };
    }
  } catch {
    // JSON parse failed or gh not available
  }

  return null;
}

/**
 * Extract agent number from branch name or worktree path
 */
export function extractAgentNumber(branchOrPath: string): number | null {
  // Try branch pattern: agent-N/...
  const branchMatch = branchOrPath.match(/agent-(\d+)\//);
  if (branchMatch) {
    return parseInt(branchMatch[1], 10);
  }

  // Try worktree path pattern: constellation-agent-N
  const pathMatch = branchOrPath.match(/constellation-agent-(\d+)/);
  if (pathMatch) {
    return parseInt(pathMatch[1], 10);
  }

  return null;
}
