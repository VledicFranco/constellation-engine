import * as assert from 'assert';
import * as vscode from 'vscode';
import * as path from 'path';
import { PerformanceTracker, Operations } from '../../../utils/performanceTracker';

/**
 * End-to-end performance tests for the VS Code extension.
 *
 * These tests verify that key user operations complete within acceptable
 * time thresholds. They use the PerformanceTracker to measure timing.
 *
 * Note: These tests require a running LSP server for full functionality.
 * Without a server, some tests will be skipped.
 */
suite('E2E Performance Tests', function() {
  this.timeout(120000); // 2 minute timeout for performance tests

  const fixturesPath = path.join(__dirname, '..', '..', '..', '..', 'src', 'test', 'fixtures');
  const tracker = PerformanceTracker.getInstance();

  // Performance thresholds (in milliseconds)
  const THRESHOLDS = {
    DOCUMENT_OPEN: 2000,      // Max time to open document and get language features
    PANEL_CREATE: 3000,       // Max time to create and render a panel
    COMMAND_EXECUTE: 5000,    // Max time to execute a command
    RAPID_TYPING: 100,        // Max time per keystroke during rapid typing
  };

  suiteSetup(() => {
    // Reset tracker before tests
    tracker.reset();
  });

  suiteTeardown(async () => {
    // Log performance statistics
    console.log('\n[PERFORMANCE TEST RESULTS]');
    tracker.logStats();

    // Close all editors
    await vscode.commands.executeCommand('workbench.action.closeAllEditors');
  });

  suite('Document Open Performance', () => {
    test('Opening a .cst file should complete quickly', async () => {
      const testFilePath = path.join(fixturesPath, 'simple.cst');

      const timer = tracker.startOperation('test.documentOpen');
      const document = await vscode.workspace.openTextDocument(testFilePath);
      await vscode.window.showTextDocument(document);
      const elapsed = timer.end();

      console.log(`[PERF] Document open: ${elapsed.toFixed(1)}ms`);

      assert.strictEqual(document.languageId, 'constellation');
      assert.ok(elapsed < THRESHOLDS.DOCUMENT_OPEN,
        `Document open took ${elapsed}ms, threshold is ${THRESHOLDS.DOCUMENT_OPEN}ms`);
    });

    test('Opening a large .cst file should complete within threshold', async () => {
      const testFilePath = path.join(fixturesPath, 'multi-step.cst');

      const timer = tracker.startOperation('test.largeDocumentOpen');
      const document = await vscode.workspace.openTextDocument(testFilePath);
      await vscode.window.showTextDocument(document);
      const elapsed = timer.end();

      console.log(`[PERF] Large document open: ${elapsed.toFixed(1)}ms`);

      assert.strictEqual(document.languageId, 'constellation');
      assert.ok(elapsed < THRESHOLDS.DOCUMENT_OPEN * 1.5,
        `Large document open took ${elapsed}ms`);
    });
  });

  suite('Command Execution Performance', () => {
    test('Run Script command should respond quickly', async () => {
      const testFilePath = path.join(fixturesPath, 'simple.cst');
      const document = await vscode.workspace.openTextDocument(testFilePath);
      await vscode.window.showTextDocument(document);

      // Wait for extension to stabilize
      await new Promise(resolve => setTimeout(resolve, 500));

      const timer = tracker.startOperation('test.runScriptCommand');
      try {
        await vscode.commands.executeCommand('constellation.runScript');
      } catch {
        // Command may fail if server not running, that's OK
      }
      const elapsed = timer.end();

      console.log(`[PERF] Run Script command: ${elapsed.toFixed(1)}ms`);

      // Command should respond quickly even if it fails
      assert.ok(elapsed < THRESHOLDS.COMMAND_EXECUTE,
        `Run Script command took ${elapsed}ms, threshold is ${THRESHOLDS.COMMAND_EXECUTE}ms`);
    });

    test('Show DAG Visualization command should respond quickly', async () => {
      const testFilePath = path.join(fixturesPath, 'multi-step.cst');
      const document = await vscode.workspace.openTextDocument(testFilePath);
      await vscode.window.showTextDocument(document);

      // Wait for extension to stabilize
      await new Promise(resolve => setTimeout(resolve, 500));

      const timer = tracker.startOperation('test.dagCommand');
      try {
        await vscode.commands.executeCommand('constellation.showDagVisualization');
      } catch {
        // Command may fail if server not running, that's OK
      }
      const elapsed = timer.end();

      console.log(`[PERF] DAG Visualization command: ${elapsed.toFixed(1)}ms`);

      assert.ok(elapsed < THRESHOLDS.COMMAND_EXECUTE,
        `DAG command took ${elapsed}ms, threshold is ${THRESHOLDS.COMMAND_EXECUTE}ms`);
    });
  });

  suite('Typing Performance', () => {
    test('Rapid typing should not block the editor', async () => {
      // Create a new unsaved document for typing test
      const document = await vscode.workspace.openTextDocument({
        content: 'in text: String\n',
        language: 'constellation',
      });
      const editor = await vscode.window.showTextDocument(document);

      // Wait for initial setup
      await new Promise(resolve => setTimeout(resolve, 500));

      const typedText = 'result = Uppercase(text)\nout result';
      const timings: number[] = [];

      // Simulate rapid typing by inserting characters with timing
      for (let i = 0; i < typedText.length; i++) {
        const timer = tracker.startOperation('test.keystroke');
        await editor.edit(editBuilder => {
          editBuilder.insert(
            new vscode.Position(editor.document.lineCount - 1,
              editor.document.lineAt(editor.document.lineCount - 1).text.length),
            typedText[i]
          );
        });
        timings.push(timer.end());
      }

      const avgTime = timings.reduce((a, b) => a + b, 0) / timings.length;
      const maxTime = Math.max(...timings);

      console.log(`[PERF] Typing: avg=${avgTime.toFixed(1)}ms, max=${maxTime.toFixed(1)}ms`);

      // Average keystroke should be fast
      assert.ok(avgTime < THRESHOLDS.RAPID_TYPING,
        `Average keystroke time ${avgTime}ms exceeds threshold ${THRESHOLDS.RAPID_TYPING}ms`);
    });
  });

  suite('Multiple Operations Performance', () => {
    test('Opening multiple files sequentially should not degrade', async () => {
      const files = ['simple.cst', 'multi-step.cst'];
      const openTimes: number[] = [];

      for (const file of files) {
        const testFilePath = path.join(fixturesPath, file);

        const timer = tracker.startOperation('test.sequentialOpen');
        const document = await vscode.workspace.openTextDocument(testFilePath);
        await vscode.window.showTextDocument(document);
        openTimes.push(timer.end());

        // Small delay between opens
        await new Promise(resolve => setTimeout(resolve, 100));
      }

      console.log(`[PERF] Sequential opens: ${openTimes.map(t => t.toFixed(0)).join('ms, ')}ms`);

      // Later opens should not be significantly slower than first
      const firstTime = openTimes[0];
      const lastTime = openTimes[openTimes.length - 1];
      const degradation = lastTime / firstTime;

      assert.ok(degradation < 3.0,
        `Performance degraded ${degradation.toFixed(1)}x between first and last open`);
    });
  });

  suite('Performance Statistics', () => {
    test('PerformanceTracker should collect statistics correctly', () => {
      // Record some test operations
      for (let i = 0; i < 10; i++) {
        tracker.recordLatency('test.statsTest', 50 + Math.random() * 50);
      }

      const stats = tracker.getStats('test.statsTest');

      assert.ok(stats !== null, 'Stats should be available');
      assert.strictEqual(stats!.count, 10, 'Should have 10 samples');
      assert.ok(stats!.avg >= 50 && stats!.avg <= 100, 'Avg should be in expected range');
      assert.ok(stats!.p95 >= stats!.avg, 'P95 should be >= avg');
      assert.ok(stats!.max >= stats!.p95, 'Max should be >= p95');
    });

    test('getAllStats should return all tracked operations', () => {
      const allStats = tracker.getAllStats();

      // Should have stats from previous tests
      assert.ok(Object.keys(allStats).length > 0, 'Should have tracked operations');

      // Print summary
      console.log('[PERF] All tracked operations:');
      for (const [op, stats] of Object.entries(allStats)) {
        console.log(`  ${op}: avg=${stats.avg.toFixed(1)}ms, count=${stats.count}`);
      }
    });
  });
});
