/**
 * Tests for parsing utilities
 */

import {
  parseScalaTestOutput,
  parseScalacOutput,
  parseQueueMarkdown,
  countWarnings,
} from '../src/utils/parsers.js';

describe('parseScalaTestOutput', () => {
  it('should parse test summary line', () => {
    const output = `
[info] Tests: succeeded 10, failed 2, canceled 0, ignored 1
[info] All tests passed
    `;

    const { summary } = parseScalaTestOutput(output);

    expect(summary.passed).toBe(10);
    expect(summary.failed).toBe(2);
    expect(summary.skipped).toBe(1);
  });

  it('should handle empty output', () => {
    const { summary, failedTests } = parseScalaTestOutput('');

    expect(summary.passed).toBe(0);
    expect(summary.failed).toBe(0);
    expect(summary.skipped).toBe(0);
    expect(failedTests).toHaveLength(0);
  });
});

describe('parseScalacOutput', () => {
  it('should parse error with line and column', () => {
    const output = `
[error] /path/to/File.scala:42:10: value foo is not a member of Bar
[error]   bar.foo()
[error]       ^
    `;

    const errors = parseScalacOutput(output);

    expect(errors).toHaveLength(1);
    expect(errors[0].file).toBe('/path/to/File.scala');
    expect(errors[0].line).toBe(42);
    expect(errors[0].column).toBe(10);
    expect(errors[0].severity).toBe('error');
    expect(errors[0].message).toContain('value foo is not a member of Bar');
  });

  it('should parse warnings', () => {
    const output = `
[warn] /path/to/File.scala:10:5: deprecated method
    `;

    const errors = parseScalacOutput(output);

    expect(errors).toHaveLength(1);
    expect(errors[0].severity).toBe('warning');
  });

  it('should handle multiple errors', () => {
    const output = `
[error] /path/to/A.scala:1:1: error in A
[error] /path/to/B.scala:2:2: error in B
[warn] /path/to/C.scala:3:3: warning in C
    `;

    const errors = parseScalacOutput(output);

    expect(errors).toHaveLength(3);
    expect(errors.filter((e) => e.severity === 'error')).toHaveLength(2);
    expect(errors.filter((e) => e.severity === 'warning')).toHaveLength(1);
  });
});

describe('parseQueueMarkdown', () => {
  it('should parse basic queue format', () => {
    const content = `
# Agent 1 Queue

**Track:** Implementation
**Focus:** Core features

## Assigned Issues

| Priority | Issue | Status | Branch |
|----------|-------|--------|--------|
| 1 | #42 - Add feature X | In Progress | agent-1/issue-42-feature-x |
| 2 | #43 - Fix bug Y | Pending | |

## Completed Issues

| Issue | PR |
|-------|-----|
| #40 - Initial setup | #41 |

## Notes

Work on priority 1 first.

## Dependencies

Waiting on PR #39 to merge.
    `;

    const queue = parseQueueMarkdown(content);

    expect(queue.track).toBe('Implementation');
    expect(queue.focus).toBe('Core features');
    expect(queue.assignedIssues).toHaveLength(2);
    expect(queue.assignedIssues[0].priority).toBe(1);
    expect(queue.assignedIssues[0].issueNumber).toBe(42);
    expect(queue.assignedIssues[0].status).toBe('in_progress');
    expect(queue.assignedIssues[1].status).toBe('pending');
    expect(queue.completedIssues).toHaveLength(1);
    expect(queue.completedIssues[0].issueNumber).toBe(40);
    expect(queue.completedIssues[0].pr).toBe(41);
    expect(queue.notes).toContain('Work on priority 1 first');
    expect(queue.dependencies).toContain('Waiting on PR #39');
  });

  it('should handle empty queue', () => {
    const queue = parseQueueMarkdown('# Empty Queue\n');

    expect(queue.assignedIssues).toHaveLength(0);
    expect(queue.completedIssues).toHaveLength(0);
  });
});

describe('countWarnings', () => {
  it('should count warnings', () => {
    const output = `
[warn] warning 1
[warn] warning 2
[error] this is an error
[warn] warning 3
    `;

    expect(countWarnings(output)).toBe(3);
  });

  it('should return 0 for no warnings', () => {
    expect(countWarnings('[error] only errors')).toBe(0);
  });
});
