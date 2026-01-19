/**
 * Child process execution utilities
 */

import { exec, spawn } from 'child_process';
import { promisify } from 'util';
import type { ExecResult } from '../types/index.js';

const execPromise = promisify(exec);

export interface ExecOptions {
  cwd?: string;
  timeout?: number;
  maxBuffer?: number;
  env?: NodeJS.ProcessEnv;
}

const DEFAULT_TIMEOUT = 300000; // 5 minutes
const DEFAULT_MAX_BUFFER = 50 * 1024 * 1024; // 50MB

/**
 * Execute a command and return stdout, stderr, and exit code
 */
export async function execAsync(
  command: string,
  options: ExecOptions = {}
): Promise<ExecResult> {
  const {
    cwd = process.cwd(),
    timeout = DEFAULT_TIMEOUT,
    maxBuffer = DEFAULT_MAX_BUFFER,
    env = process.env,
  } = options;

  try {
    const { stdout, stderr } = await execPromise(command, {
      cwd,
      timeout,
      maxBuffer,
      env,
      shell: process.platform === 'win32' ? 'powershell.exe' : '/bin/sh',
    });

    return {
      stdout: stdout.toString(),
      stderr: stderr.toString(),
      exitCode: 0,
    };
  } catch (error: unknown) {
    const execError = error as {
      stdout?: string | Buffer;
      stderr?: string | Buffer;
      code?: number;
      killed?: boolean;
      signal?: string;
    };

    // Command failed but we still have output
    return {
      stdout: execError.stdout?.toString() ?? '',
      stderr: execError.stderr?.toString() ?? '',
      exitCode: execError.code ?? 1,
    };
  }
}

/**
 * Execute a long-running command with streaming output
 * Useful for build/test commands
 */
export function spawnAsync(
  command: string,
  args: string[],
  options: ExecOptions = {}
): Promise<ExecResult> {
  const {
    cwd = process.cwd(),
    timeout = DEFAULT_TIMEOUT,
    env = process.env,
  } = options;

  return new Promise((resolve) => {
    const proc = spawn(command, args, {
      cwd,
      env,
      shell: true,
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';
    let timeoutId: NodeJS.Timeout | undefined;

    if (timeout > 0) {
      timeoutId = setTimeout(() => {
        proc.kill('SIGTERM');
        stderr += '\n[Process killed due to timeout]';
      }, timeout);
    }

    proc.stdout?.on('data', (data: Buffer) => {
      stdout += data.toString();
    });

    proc.stderr?.on('data', (data: Buffer) => {
      stderr += data.toString();
    });

    proc.on('close', (code) => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      resolve({
        stdout,
        stderr,
        exitCode: code ?? 1,
      });
    });

    proc.on('error', (err) => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      resolve({
        stdout,
        stderr: stderr + '\n' + err.message,
        exitCode: 1,
      });
    });
  });
}

/**
 * Measure execution time of an async function
 */
export async function withTiming<T>(
  fn: () => Promise<T>
): Promise<{ result: T; duration: number }> {
  const start = Date.now();
  const result = await fn();
  const duration = Date.now() - start;
  return { result, duration };
}
