import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo
} from 'vscode-languageclient/node';
import * as ws from 'ws';

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

        // Create duplex stream from WebSocket
        const stream: StreamInfo = {
          writer: socket,
          reader: socket
        };

        resolve(stream);
      });

      socket.on('error', (error) => {
        console.error('WebSocket connection error:', error);
        vscode.window.showErrorMessage(
          `Failed to connect to Constellation Language Server at ${serverUrl}. ` +
          'Make sure the server is running.'
        );
        reject(error);
      });

      socket.on('close', () => {
        console.log('Disconnected from Constellation Language Server');
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
