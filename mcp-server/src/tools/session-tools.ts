/**
 * Session Management Tools for Constellation Engine MCP Server
 */

import * as fs from 'fs';
import * as path from 'path';
import { z } from 'zod';
import {
  isWorktree,
  isMainRepo,
  getWorktreeInfo,
  getBranchInfo,
  getRepoStatus,
  listWorktrees,
  getRecentCommits,
  getUncommittedChanges,
  getRebaseStatus,
  getChangesSinceBranch,
  getOpenPR,
  extractAgentNumber,
} from '../utils/git.js';
import { findMainRepoRoot } from '../utils/paths.js';
import { readAgentQueue } from '../utils/parsers.js';
import type {
  AgentContext,
  WorktreeVerification,
  SessionResumeInfo,
  AgentQueue,
  HandoffData,
  HandoffResult,
} from '../types/index.js';

// Tool schemas
export const GetAgentContextSchema = z.object({});

export const VerifyWorktreeSchema = z.object({});

export const ResumeSessionSchema = z.object({
  agentNumber: z.number().min(1).max(10).optional(),
});

export const ReadQueueSchema = z.object({
  agentNumber: z.number().min(1).max(10),
});

export const HandoffSessionSchema = z.object({
  agentNumber: z.number().min(1).max(10),
  issueNumber: z.number().min(1),
  status: z.enum(['in_progress', 'blocked', 'ready_for_review']),
  summary: z.string().min(1),
  nextSteps: z.array(z.string()).min(1),
  blockers: z.array(z.string()).optional(),
});

/**
 * Get current agent's working context
 */
export async function getAgentContext(): Promise<AgentContext> {
  const worktree = await getWorktreeInfo();
  const branch = await getBranchInfo();
  const repoStatus = await getRepoStatus();

  // Try to detect agent number from branch or worktree path
  let agentNumber = extractAgentNumber(branch.name);
  if (!agentNumber) {
    agentNumber = extractAgentNumber(worktree.path);
  }

  return {
    agentNumber,
    worktree,
    branch,
    repoStatus,
  };
}

/**
 * Verify current directory is a valid worktree (not main repo)
 */
export async function verifyWorktree(): Promise<WorktreeVerification> {
  const currentPath = process.cwd();
  const isValidWorktree = await isWorktree();
  const isMain = await isMainRepo();

  // Get available worktrees
  const availableWorktrees = await listWorktrees();

  let recommendation: string | null = null;

  if (isMain) {
    recommendation =
      'You are in the main repository. Create or switch to a worktree before making changes. ' +
      'Use: git worktree add ../constellation-agent-N -b agent-N/issue-X-description master';
  } else if (!isValidWorktree) {
    recommendation =
      'Current directory is not a valid git worktree. ' +
      'Navigate to an existing worktree or create one from the main repository.';
  }

  return {
    isValidWorktree,
    isMainRepo: isMain,
    currentPath,
    recommendation,
    availableWorktrees,
  };
}

/**
 * Get all info needed to resume work on an agent's session
 */
export async function resumeSession(
  agentNumber?: number
): Promise<SessionResumeInfo> {
  const branch = await getBranchInfo();

  // Auto-detect agent number if not provided
  let resolvedAgentNumber: number | null = agentNumber ?? null;
  if (resolvedAgentNumber === null) {
    resolvedAgentNumber = extractAgentNumber(branch.name);
  }
  if (resolvedAgentNumber === null) {
    const worktree = await getWorktreeInfo();
    resolvedAgentNumber = extractAgentNumber(worktree.path);
  }

  // Default to 1 if we can't detect
  if (!resolvedAgentNumber) {
    resolvedAgentNumber = 1;
  }

  const [lastCommits, uncommittedChanges, rebaseStatus, changesSinceBranch] =
    await Promise.all([
      getRecentCommits(5),
      getUncommittedChanges(),
      getRebaseStatus(),
      getChangesSinceBranch(),
    ]);

  const openPR = await getOpenPR(branch.name);

  return {
    agentNumber: resolvedAgentNumber,
    currentBranch: branch.name,
    issueNumber: branch.issueNumber,
    lastCommits,
    uncommittedChanges,
    rebaseStatus,
    changesSinceBranch,
    openPR,
  };
}

/**
 * Read agent's work queue from QUEUE.md
 */
export async function readQueue(agentNumber: number): Promise<AgentQueue | null> {
  const mainRepo = await findMainRepoRoot();

  if (!mainRepo) {
    return null;
  }

  return readAgentQueue(agentNumber, mainRepo);
}

/**
 * Record handoff notes for session continuity
 */
export async function handoffSession(
  data: HandoffData
): Promise<HandoffResult> {
  const mainRepo = await findMainRepoRoot();

  if (!mainRepo) {
    return {
      saved: false,
      handoffPath: '',
      timestamp: new Date().toISOString(),
    };
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const handoffDir = path.join(
    mainRepo,
    'agents',
    `agent-${data.agentNumber}`,
    'handoffs'
  );

  // Create handoffs directory if it doesn't exist
  if (!fs.existsSync(handoffDir)) {
    fs.mkdirSync(handoffDir, { recursive: true });
  }

  const handoffPath = path.join(
    handoffDir,
    `issue-${data.issueNumber}-${timestamp}.md`
  );

  const content = `# Session Handoff - Issue #${data.issueNumber}

**Agent:** ${data.agentNumber}
**Timestamp:** ${new Date().toISOString()}
**Status:** ${data.status}

## Summary

${data.summary}

## Next Steps

${data.nextSteps.map((step, i) => `${i + 1}. ${step}`).join('\n')}

${
  data.blockers && data.blockers.length > 0
    ? `## Blockers

${data.blockers.map((blocker) => `- ${blocker}`).join('\n')}
`
    : ''
}
---
*Generated by Constellation Engine MCP Server*
`;

  try {
    fs.writeFileSync(handoffPath, content, 'utf-8');
    return {
      saved: true,
      handoffPath,
      timestamp: new Date().toISOString(),
    };
  } catch {
    return {
      saved: false,
      handoffPath: '',
      timestamp: new Date().toISOString(),
    };
  }
}

// Tool definitions for MCP
export const sessionTools = [
  {
    name: 'constellation_get_agent_context',
    description:
      "Get current agent's working context including worktree info, branch details, and repo status. Auto-detects from current working directory.",
    inputSchema: {
      type: 'object' as const,
      properties: {},
      required: [] as string[],
    },
    handler: getAgentContext,
  },
  {
    name: 'constellation_verify_worktree',
    description:
      'Verify current directory is a valid worktree (not main repo). Returns recommendation if in wrong location.',
    inputSchema: {
      type: 'object' as const,
      properties: {},
      required: [] as string[],
    },
    handler: verifyWorktree,
  },
  {
    name: 'constellation_resume_session',
    description:
      "Get all info needed to resume work on an agent's session including commits, changes, rebase status, and open PR.",
    inputSchema: {
      type: 'object' as const,
      properties: {
        agentNumber: {
          type: 'number',
          description:
            'Agent number (1-10). Auto-detected from branch/worktree if not provided.',
          minimum: 1,
          maximum: 10,
        },
      },
      required: [] as string[],
    },
    handler: async (args: { agentNumber?: number }) =>
      resumeSession(args.agentNumber),
  },
  {
    name: 'constellation_read_queue',
    description:
      "Read agent's work queue from QUEUE.md file. Returns assigned issues, status, and priorities.",
    inputSchema: {
      type: 'object' as const,
      properties: {
        agentNumber: {
          type: 'number',
          description: 'Agent number (1-10)',
          minimum: 1,
          maximum: 10,
        },
      },
      required: ['agentNumber'],
    },
    handler: async (args: { agentNumber: number }) => readQueue(args.agentNumber),
  },
  {
    name: 'constellation_handoff_session',
    description:
      'Record handoff notes for session continuity. Creates a markdown file with summary, next steps, and blockers.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        agentNumber: {
          type: 'number',
          description: 'Agent number (1-10)',
          minimum: 1,
          maximum: 10,
        },
        issueNumber: {
          type: 'number',
          description: 'Issue number being worked on',
          minimum: 1,
        },
        status: {
          type: 'string',
          enum: ['in_progress', 'blocked', 'ready_for_review'],
          description: 'Current status of work',
        },
        summary: {
          type: 'string',
          description: 'Summary of current state and what was accomplished',
        },
        nextSteps: {
          type: 'array',
          items: { type: 'string' },
          description: 'List of next steps to complete the work',
        },
        blockers: {
          type: 'array',
          items: { type: 'string' },
          description: 'List of blockers (optional)',
        },
      },
      required: ['agentNumber', 'issueNumber', 'status', 'summary', 'nextSteps'],
    },
    handler: async (args: HandoffData) => handoffSession(args),
  },
];
