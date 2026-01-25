import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo
} from 'vscode-languageclient/node';
import * as ws from 'ws';
import { Duplex } from 'stream';
import { ScriptRunnerPanel } from './panels/ScriptRunnerPanel';
import { DagVisualizerPanel } from './panels/DagVisualizerPanel';
import { PerformanceTracker, Operations } from './utils/performanceTracker';

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
  console.log('Constellation Language Support is now active');

  // Get server URL from configuration
  const config = vscode.workspace.getConfiguration('constellation');
  const serverUrl = config.get<string>('server.url') || 'ws://localhost:8080/lsp';

  // Create WebSocket client options
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'constellation' }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.cst')
    }
  };

  // Create WebSocket connection to LSP server
  const createConnection = (): Promise<StreamInfo> => {
    return new Promise((resolve, reject) => {
      const socket = new ws.WebSocket(serverUrl);

      socket.on('open', () => {
        console.log('Connected to Constellation Language Server');

        // Queue for messages received while paused due to backpressure
        const messageQueue: string[] = [];
        let isPaused = false;

        // Duplex stream wrapper with proper backpressure handling
        // Use smaller highWaterMark to trigger backpressure earlier and prevent OOM
        const stream = new Duplex({
          highWaterMark: 256 * 1024, // 256KB buffer limit (reduced from 1MB)
          write(chunk: any, encoding: string, callback: (error?: Error | null) => void) {
            const data = chunk.toString();
            // Send directly - vscode-languageclient sends complete messages
            socket.send(data, (error) => {
              callback(error || undefined);
            });
          },
          read() {
            // When the consumer is ready for more data, drain the queue
            isPaused = false;
            while (messageQueue.length > 0 && !isPaused) {
              const msg = messageQueue.shift()!;
              // push() returns false if internal buffer is full
              if (!this.push(msg)) {
                isPaused = true;
                break;
              }
            }
          }
        });

        socket.on('message', (data: ws.Data) => {
          const msg = data.toString();

          if (isPaused) {
            // Buffer is full, queue the message
            // Limit queue size to prevent unbounded memory growth
            if (messageQueue.length < 100) {
              messageQueue.push(msg);
            } else {
              console.warn('LSP message queue full, dropping message');
            }
          } else {
            // Try to push directly, queue if buffer full
            if (!stream.push(msg)) {
              isPaused = true;
            }
          }
        });

        socket.on('close', () => {
          console.log('Disconnected from Constellation Language Server');
          // Drain any remaining queued messages
          while (messageQueue.length > 0) {
            stream.push(messageQueue.shift()!);
          }
          stream.push(null);
        });

        socket.on('error', (error) => {
          console.error('WebSocket error:', error);
          stream.destroy(error);
        });

        resolve({ writer: stream, reader: stream });
      });

      socket.on('error', (error) => {
        console.error('WebSocket connection error:', error);
        vscode.window.showErrorMessage(
          `Failed to connect to Constellation Language Server at ${serverUrl}. ` +
          'Make sure the server is running.'
        );
        reject(error);
      });
    });
  };

  // Create the language client
  client = new LanguageClient(
    'constellationLanguageServer',
    'Constellation Language Server',
    createConnection,
    clientOptions
  );

  // Register custom command to execute pipelines
  const executeCommand = vscode.commands.registerCommand(
    'constellation.executePipeline',
    async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== 'constellation') {
        vscode.window.showWarningMessage('Open a Constellation file (.cst) to execute');
        return;
      }

      const uri = editor.document.uri.toString();

      // Prompt for inputs (simplified - in real implementation, would parse required inputs)
      const inputsJson = await vscode.window.showInputBox({
        prompt: 'Enter input values as JSON (e.g., {"text": "hello"})',
        value: '{}'
      });

      if (!inputsJson) {
        return;
      }

      try {
        const inputs = JSON.parse(inputsJson);

        // Send custom request to execute pipeline with performance tracking
        const tracker = PerformanceTracker.getInstance();
        const timer = tracker.startOperation(Operations.LSP_EXECUTE);
        const result = await client?.sendRequest('constellation/executePipeline', {
          uri,
          inputs
        });
        timer.end();

        if (result && (result as any).success) {
          vscode.window.showInformationMessage('Pipeline executed successfully');
          console.log('Pipeline result:', result);
        } else {
          vscode.window.showErrorMessage(
            `Pipeline execution failed: ${(result as any).error || 'Unknown error'}`
          );
        }
      } catch (error) {
        vscode.window.showErrorMessage(`Invalid JSON: ${error}`);
      }
    }
  );

  context.subscriptions.push(executeCommand);

  // Register script runner command with webview panel
  const runScriptCommand = vscode.commands.registerCommand(
    'constellation.runScript',
    async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== 'constellation') {
        vscode.window.showWarningMessage('Open a Constellation file (.cst) to run');
        return;
      }

      const uri = editor.document.uri.toString();
      const tracker = PerformanceTracker.getInstance();
      const timer = tracker.startOperation(Operations.PANEL_CREATE);
      ScriptRunnerPanel.createOrShow(context.extensionUri, client, uri);
      timer.end();
    }
  );

  context.subscriptions.push(runScriptCommand);

  // Register DAG visualizer command
  const dagVisualizerCommand = vscode.commands.registerCommand(
    'constellation.showDagVisualization',
    async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== 'constellation') {
        vscode.window.showWarningMessage('Open a Constellation file (.cst) to visualize');
        return;
      }

      const uri = editor.document.uri.toString();
      const tracker = PerformanceTracker.getInstance();
      const timer = tracker.startOperation(Operations.PANEL_CREATE);
      DagVisualizerPanel.createOrShow(context.extensionUri, client, uri);
      timer.end();
    }
  );

  context.subscriptions.push(dagVisualizerCommand);

  // Register command to show performance statistics
  const showPerfStatsCommand = vscode.commands.registerCommand(
    'constellation.showPerformanceStats',
    () => {
      const tracker = PerformanceTracker.getInstance();
      const stats = tracker.getAllStats();
      if (Object.keys(stats).length === 0) {
        vscode.window.showInformationMessage('No performance data collected yet.');
        return;
      }
      // Log to console and show summary
      tracker.logStats();
      const summary = Object.entries(stats)
        .map(([op, s]) => `${op}: avg=${s.avg.toFixed(0)}ms, p95=${s.p95.toFixed(0)}ms`)
        .join('\n');
      vscode.window.showInformationMessage(`Performance Stats:\n${summary}`);
    }
  );

  context.subscriptions.push(showPerfStatsCommand);

  // Auto-refresh DAG visualizer on document change (debounced to avoid lag)
  // Only schedule refreshes if the panel is actually open
  const refreshTimers = new Map<string, NodeJS.Timeout>();
  const DEBOUNCE_MS = 750; // Wait 750ms after last keystroke before refreshing

  // Clean up timers on deactivate
  context.subscriptions.push({
    dispose: () => {
      refreshTimers.forEach((timer) => clearTimeout(timer));
      refreshTimers.clear();
    }
  });

  const documentChangeListener = vscode.workspace.onDidChangeTextDocument((e) => {
    // Only process if panel is open and it's a constellation file
    if (e.document.languageId === 'constellation' && DagVisualizerPanel.isPanelOpen()) {
      const uri = e.document.uri.toString();

      // Clear any pending refresh for this document
      const existingTimer = refreshTimers.get(uri);
      if (existingTimer) {
        clearTimeout(existingTimer);
      }

      // Schedule a new refresh after debounce delay
      const timer = setTimeout(() => {
        refreshTimers.delete(uri);
        DagVisualizerPanel.refresh(uri);
      }, DEBOUNCE_MS);

      refreshTimers.set(uri, timer);
    }
  });

  context.subscriptions.push(documentChangeListener);

  // Start the client
  client.start().catch((error) => {
    console.error('Failed to start language client:', error);
    vscode.window.showErrorMessage('Failed to start Constellation Language Server');
  });
}

export function deactivate(): Thenable<void> | undefined {
  // Log performance stats on deactivation
  const tracker = PerformanceTracker.getInstance();
  tracker.logStats();

  if (!client) {
    return undefined;
  }
  return client.stop();
}
