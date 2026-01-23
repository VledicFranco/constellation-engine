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

        // Simple duplex stream wrapper - pass data through directly
        // vscode-languageclient handles LSP framing internally
        const stream = new Duplex({
          write(chunk: any, encoding: string, callback: (error?: Error | null) => void) {
            const data = chunk.toString();
            // Send directly - vscode-languageclient sends complete messages
            socket.send(data, (error) => {
              callback(error || undefined);
            });
          },
          read() {
            // Data is pushed when received via 'message' event
          }
        });

        socket.on('message', (data: ws.Data) => {
          stream.push(data.toString());
        });

        socket.on('close', () => {
          console.log('Disconnected from Constellation Language Server');
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

        // Send custom request to execute pipeline
        const result = await client?.sendRequest('constellation/executePipeline', {
          uri,
          inputs
        });

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
      ScriptRunnerPanel.createOrShow(context.extensionUri, client, uri);
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
      DagVisualizerPanel.createOrShow(context.extensionUri, client, uri);
    }
  );

  context.subscriptions.push(dagVisualizerCommand);

  // Auto-refresh DAG visualizer on document change (debounced to avoid lag)
  // Only schedule refreshes if the panel is actually open
  const refreshTimers = new Map<string, NodeJS.Timeout>();
  const DEBOUNCE_MS = 750; // Wait 750ms after last keystroke before refreshing

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
  if (!client) {
    return undefined;
  }
  return client.stop();
}
