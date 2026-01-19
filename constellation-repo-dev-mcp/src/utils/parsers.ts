/**
 * Output parsing utilities
 */

import * as fs from 'fs';
import * as path from 'path';
import type {
  TestSummary,
  FailedTest,
  BuildError,
  QueueIssue,
  AgentQueue,
} from '../types/index.js';

/**
 * Parse ScalaTest output to extract test summary and failures
 */
export function parseScalaTestOutput(output: string): {
  summary: TestSummary;
  failedTests: FailedTest[];
} {
  const failedTests: FailedTest[] = [];
  let passed = 0;
  let failed = 0;
  let skipped = 0;

  const lines = output.split('\n');

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Match test run summary lines like "Tests: succeeded 10, failed 2, canceled 0, ignored 1"
    const summaryMatch = line.match(
      /Tests:\s*succeeded\s+(\d+),\s*failed\s+(\d+),?\s*(?:canceled\s+\d+,?)?\s*(?:ignored\s+(\d+))?/i
    );
    if (summaryMatch) {
      passed += parseInt(summaryMatch[1], 10) || 0;
      failed += parseInt(summaryMatch[2], 10) || 0;
      skipped += parseInt(summaryMatch[3], 10) || 0;
      continue;
    }

    // Alternative summary format: "Run completed. Total number of tests run: X"
    const totalMatch = line.match(/Total number of tests run:\s*(\d+)/);
    if (totalMatch) {
      // Already parsed above, just continue
      continue;
    }

    // Match failed test indicators like "*** FAILED ***"
    if (line.includes('*** FAILED ***')) {
      // Look backwards for test name
      for (let j = i - 1; j >= Math.max(0, i - 10); j--) {
        const prevLine = lines[j];
        // Match "- test name" pattern
        const testMatch = prevLine.match(/^\s*-\s*(.+?)\s*(?:\*\*\*.*)?$/);
        if (testMatch) {
          // Look for suite name (usually a few lines up with "in" or class name)
          let suite = 'Unknown Suite';
          for (let k = j - 1; k >= Math.max(0, j - 5); k--) {
            const suiteLine = lines[k];
            const suiteMatch = suiteLine.match(
              /^\s*(?:in\s+)?([A-Z][A-Za-z0-9]+(?:Spec|Test|Suite))\s*:?\s*$/
            );
            if (suiteMatch) {
              suite = suiteMatch[1];
              break;
            }
          }

          // Look for error message
          let message = '';
          for (let k = i + 1; k < Math.min(lines.length, i + 5); k++) {
            const msgLine = lines[k].trim();
            if (msgLine && !msgLine.startsWith('at ')) {
              message = msgLine;
              break;
            }
          }

          failedTests.push({
            name: testMatch[1].trim(),
            suite,
            message: message || 'Test failed',
          });
          break;
        }
      }
    }

    // Match sbt test summary format
    const sbtSummaryMatch = line.match(
      /All tests passed|(\d+) tests? failed/i
    );
    if (sbtSummaryMatch) {
      if (line.includes('All tests passed')) {
        // Count is already tracked above
      } else if (sbtSummaryMatch[1]) {
        failed = Math.max(failed, parseInt(sbtSummaryMatch[1], 10));
      }
    }
  }

  return {
    summary: { passed, failed, skipped },
    failedTests,
  };
}

/**
 * Parse Scala compiler output to extract errors and warnings
 */
export function parseScalacOutput(output: string): BuildError[] {
  const errors: BuildError[] = [];

  // Match patterns like: [error] /path/to/file.scala:42:10: error message
  const errorPattern =
    /\[(error|warn)\]\s+([^:]+):(\d+):(\d+):\s*(.+)/g;

  const matchedLocations = new Set<string>();

  let match: RegExpExecArray | null;
  while ((match = errorPattern.exec(output)) !== null) {
    const locationKey = `${match[2]}:${match[3]}`;
    matchedLocations.add(locationKey);
    errors.push({
      severity: match[1] === 'error' ? 'error' : 'warning',
      file: match[2],
      line: parseInt(match[3], 10),
      column: parseInt(match[4], 10),
      message: match[5].trim(),
    });
  }

  // Also match simpler format: [error] file.scala:42: message
  // But only if not already matched by the more specific pattern
  const simplePattern = /\[(error|warn)\]\s+([^:]+):(\d+):\s*(.+)/g;
  let simpleMatch: RegExpExecArray | null;
  while ((simpleMatch = simplePattern.exec(output)) !== null) {
    const locationKey = `${simpleMatch[2]}:${simpleMatch[3]}`;
    // Skip if already matched by more specific pattern
    if (matchedLocations.has(locationKey)) {
      continue;
    }
    matchedLocations.add(locationKey);
    errors.push({
      severity: simpleMatch[1] === 'error' ? 'error' : 'warning',
      file: simpleMatch[2],
      line: parseInt(simpleMatch[3], 10),
      column: 0,
      message: simpleMatch[4].trim(),
    });
  }

  return errors;
}

/**
 * Parse QUEUE.md file to extract agent queue information
 */
export function parseQueueMarkdown(content: string): AgentQueue {
  const queue: AgentQueue = {
    track: '',
    focus: '',
    assignedIssues: [],
    completedIssues: [],
    notes: '',
    dependencies: '',
  };

  const lines = content.split('\n');
  let currentSection = '';
  let tableHeaderFound = false;

  for (const line of lines) {
    const trimmed = line.trim();

    // Track and Focus from header
    const trackMatch = trimmed.match(/^\*\*Track:\*\*\s*(.+)/);
    if (trackMatch) {
      queue.track = trackMatch[1];
      continue;
    }

    const focusMatch = trimmed.match(/^\*\*Focus:\*\*\s*(.+)/);
    if (focusMatch) {
      queue.focus = focusMatch[1];
      continue;
    }

    // Section headers
    if (trimmed.startsWith('## ') || trimmed.startsWith('### ')) {
      currentSection = trimmed.replace(/^#+\s*/, '').toLowerCase();
      tableHeaderFound = false;
      continue;
    }

    // Table parsing
    if (trimmed.startsWith('|')) {
      // Skip separator row
      if (trimmed.match(/^\|[\s\-:]+\|/)) {
        tableHeaderFound = true;
        continue;
      }

      // Skip header row - detect by common header words
      if (!tableHeaderFound) {
        const isHeader = /\b(Priority|Issue|Status|Branch|PR)\b/i.test(trimmed);
        if (isHeader) {
          continue;
        }
      }

      // Parse table row
      const cells = trimmed
        .split('|')
        .map((c) => c.trim())
        .filter((c) => c.length > 0);

      if (currentSection.includes('assigned') || currentSection.includes('queue')) {
        if (cells.length >= 3) {
          const priority = parseInt(cells[0], 10) || 99;
          const issueMatch = cells[1].match(/#(\d+)/);
          const issueNumber = issueMatch ? parseInt(issueMatch[1], 10) : 0;
          const title = cells[1].replace(/#\d+\s*-?\s*/, '').trim();
          const statusRaw = cells[2].toLowerCase();
          const branch = cells.length >= 4 ? cells[3] || undefined : undefined;

          let status: QueueIssue['status'] = 'pending';
          if (statusRaw.includes('progress')) status = 'in_progress';
          else if (statusRaw.includes('blocked')) status = 'blocked';
          else if (statusRaw.includes('review')) status = 'ready_for_review';

          queue.assignedIssues.push({
            priority,
            issueNumber,
            title,
            status,
            branch,
          });
        }
      } else if (currentSection.includes('completed')) {
        if (cells.length >= 2) {
          const issueMatch = cells[0].match(/#(\d+)/);
          const issueNumber = issueMatch ? parseInt(issueMatch[1], 10) : 0;
          const title = cells[0].replace(/#\d+\s*-?\s*/, '').trim();
          const prMatch = cells[1].match(/#?(\d+)/);
          const pr = prMatch ? parseInt(prMatch[1], 10) : undefined;

          queue.completedIssues.push({
            issueNumber,
            title,
            pr,
          });
        }
      }
    }

    // Notes and dependencies
    if (currentSection.includes('notes')) {
      if (trimmed && !trimmed.startsWith('#')) {
        queue.notes += (queue.notes ? '\n' : '') + trimmed;
      }
    }

    if (currentSection.includes('dependencies')) {
      if (trimmed && !trimmed.startsWith('#')) {
        queue.dependencies += (queue.dependencies ? '\n' : '') + trimmed;
      }
    }
  }

  return queue;
}

/**
 * Read and parse QUEUE.md for an agent
 */
export function readAgentQueue(
  agentNumber: number,
  mainRepoPath: string
): AgentQueue | null {
  const queuePath = path.join(
    mainRepoPath,
    'agents',
    `agent-${agentNumber}`,
    'QUEUE.md'
  );

  try {
    const content = fs.readFileSync(queuePath, 'utf-8');
    return parseQueueMarkdown(content);
  } catch {
    return null;
  }
}

/**
 * Count warnings in build output
 */
export function countWarnings(output: string): number {
  const warnPattern = /\[warn\]/gi;
  const matches = output.match(warnPattern);
  return matches ? matches.length : 0;
}
