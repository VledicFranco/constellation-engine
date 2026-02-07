import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo
} from 'vscode-languageclient/node';
import * as ws from 'ws';
import { Duplex } from 'stream';
import { PipelineRunnerPanel } from './panels/PipelineRunnerPanel';
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

  // Register pipeline runner command with webview panel
  const runPipelineCommand = vscode.commands.registerCommand(
    'constellation.runPipeline',
    async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor || editor.document.languageId !== 'constellation') {
        vscode.window.showWarningMessage('Open a Constellation file (.cst) to run');
        return;
      }

      const uri = editor.document.uri.toString();
      const tracker = PerformanceTracker.getInstance();
      const timer = tracker.startOperation(Operations.PANEL_CREATE);
      PipelineRunnerPanel.createOrShow(context.extensionUri, client, uri);
      timer.end();
    }
  );

  context.subscriptions.push(runPipelineCommand);

  // Register DAG visualizer command - opens dashboard in browser
  const dagVisualizerCommand = vscode.commands.registerCommand(
    'constellation.showDagVisualization',
    async () => {
      // Skip opening external browser during tests to avoid opening 100+ tabs
      if (context.extensionMode === vscode.ExtensionMode.Test) {
        console.log('[DAG] Skipping external browser open during test mode');
        return;
      }

      // Get server URL from configuration and construct dashboard URL
      const config = vscode.workspace.getConfiguration('constellation');
      const serverUrl = config.get<string>('server.url') || 'ws://localhost:8080/lsp';

      // Extract base URL from WebSocket URL
      const baseUrl = serverUrl.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:').replace(/\/lsp$/, '');
      const dashboardUrl = `${baseUrl}/dashboard`;

      // Open in external browser
      vscode.env.openExternal(vscode.Uri.parse(dashboardUrl));
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

  // Note: DAG visualization is now handled by the web dashboard
  // The DagVisualizerPanel has been removed in favor of the browser-based dashboard

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
