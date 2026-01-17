import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

interface InputField {
  name: string;
  type: TypeDescriptor;
  line: number;
}

interface TypeDescriptor {
  kind: 'primitive' | 'list' | 'record' | 'map' | 'parameterized' | 'ref';
  name?: string;
  elementType?: TypeDescriptor;
  fields?: RecordField[];
  keyType?: TypeDescriptor;
  valueType?: TypeDescriptor;
  params?: TypeDescriptor[];
}

interface RecordField {
  name: string;
  type: TypeDescriptor;
}

interface GetInputSchemaResult {
  success: boolean;
  inputs?: InputField[];
  error?: string;
}

interface ExecutePipelineResult {
  success: boolean;
  outputs?: { [key: string]: any };
  error?: string;
  executionTimeMs?: number;
}

export class ScriptRunnerPanel {
  public static currentPanel: ScriptRunnerPanel | undefined;
  public static readonly viewType = 'constellationScriptRunner';

  private readonly _panel: vscode.WebviewPanel;
  private readonly _extensionUri: vscode.Uri;
  private _client: LanguageClient | undefined;
  private _currentUri: string | undefined;
  private _disposables: vscode.Disposable[] = [];

  public static createOrShow(
    extensionUri: vscode.Uri,
    client: LanguageClient | undefined,
    documentUri: string
  ) {
    const column = vscode.ViewColumn.Beside;

    if (ScriptRunnerPanel.currentPanel) {
      ScriptRunnerPanel.currentPanel._panel.reveal(column);
      ScriptRunnerPanel.currentPanel._client = client;
      ScriptRunnerPanel.currentPanel._currentUri = documentUri;
      ScriptRunnerPanel.currentPanel._refreshSchema();
      return;
    }

    const panel = vscode.window.createWebviewPanel(
      ScriptRunnerPanel.viewType,
      'Script Runner',
      column,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'src', 'webview')]
      }
    );

    ScriptRunnerPanel.currentPanel = new ScriptRunnerPanel(panel, extensionUri, client, documentUri);
  }

  private constructor(
    panel: vscode.WebviewPanel,
    extensionUri: vscode.Uri,
    client: LanguageClient | undefined,
    documentUri: string
  ) {
    this._panel = panel;
    this._extensionUri = extensionUri;
    this._client = client;
    this._currentUri = documentUri;

    this._update();

    this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

    this._panel.webview.onDidReceiveMessage(
      async (message) => {
        console.log('[ScriptRunner] Received message from webview:', message.command);
        switch (message.command) {
          case 'ready':
            console.log('[ScriptRunner] Webview ready, loading schema...');
            await this._refreshSchema();
            break;
          case 'refresh':
            await this._refreshSchema();
            break;
          case 'execute':
            await this._executePipeline(message.inputs);
            break;
        }
      },
      null,
      this._disposables
    );
  }

  private async _refreshSchema() {
    console.log('[ScriptRunner] _refreshSchema called, uri:', this._currentUri);
    if (!this._client || !this._currentUri) {
      console.log('[ScriptRunner] No client or URI');
      this._panel.webview.postMessage({
        type: 'schemaError',
        error: 'Language server not connected'
      });
      return;
    }

    try {
      console.log('[ScriptRunner] Sending getInputSchema request...');
      const result = await this._client.sendRequest<GetInputSchemaResult>(
        'constellation/getInputSchema',
        { uri: this._currentUri }
      );
      console.log('[ScriptRunner] Got result:', JSON.stringify(result));

      if (result.success && result.inputs) {
        const fileName = this._currentUri.split('/').pop() || 'script.cst';
        console.log('[ScriptRunner] Posting schema to webview, inputs:', result.inputs.length);
        this._panel.webview.postMessage({
          type: 'schema',
          inputs: result.inputs,
          fileName: fileName
        });
      } else {
        console.log('[ScriptRunner] Schema error:', result.error);
        this._panel.webview.postMessage({
          type: 'schemaError',
          error: result.error || 'Failed to get input schema'
        });
      }
    } catch (error: any) {
      console.log('[ScriptRunner] Exception:', error);
      this._panel.webview.postMessage({
        type: 'schemaError',
        error: error.message || 'Failed to get input schema'
      });
    }
  }

  private async _executePipeline(inputs: { [key: string]: any }) {
    if (!this._client || !this._currentUri) {
      this._panel.webview.postMessage({
        type: 'executeError',
        error: 'Language server not connected'
      });
      return;
    }

    this._panel.webview.postMessage({ type: 'executing' });

    const startTime = Date.now();

    try {
      const result = await this._client.sendRequest<ExecutePipelineResult>(
        'constellation/executePipeline',
        { uri: this._currentUri, inputs }
      );

      const executionTime = Date.now() - startTime;

      if (result.success) {
        this._panel.webview.postMessage({
          type: 'executeResult',
          outputs: result.outputs || {},
          executionTimeMs: result.executionTimeMs || executionTime
        });
      } else {
        this._panel.webview.postMessage({
          type: 'executeError',
          error: result.error || 'Execution failed'
        });
      }
    } catch (error: any) {
      this._panel.webview.postMessage({
        type: 'executeError',
        error: error.message || 'Execution failed'
      });
    }
  }

  public dispose() {
    ScriptRunnerPanel.currentPanel = undefined;

    this._panel.dispose();

    while (this._disposables.length) {
      const disposable = this._disposables.pop();
      if (disposable) {
        disposable.dispose();
      }
    }
  }

  private _update() {
    this._panel.webview.html = this._getHtmlContent();
  }

  private _getHtmlContent(): string {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">
  <title>Script Runner</title>
  <style>
    :root {
      --spacing-xs: 4px;
      --spacing-sm: 8px;
      --spacing-md: 12px;
      --spacing-lg: 16px;
      --spacing-xl: 20px;
      --radius-sm: 4px;
      --radius-md: 6px;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif);
      font-size: var(--vscode-font-size, 13px);
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      line-height: 1.5;
      padding: var(--spacing-lg);
    }

    .header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding-bottom: var(--spacing-md);
      margin-bottom: var(--spacing-lg);
      border-bottom: 1px solid var(--vscode-panel-border, rgba(128, 128, 128, 0.35));
    }

    .header-left { display: flex; align-items: center; gap: var(--spacing-sm); }
    .header-icon { font-size: 20px; opacity: 0.9; }
    .header-content h1 { font-size: 14px; font-weight: 600; margin: 0; }
    .file-name {
      font-size: 12px;
      color: var(--vscode-descriptionForeground);
      margin-top: 2px;
      font-family: var(--vscode-editor-font-family, monospace);
    }

    .refresh-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      background: transparent;
      border: none;
      border-radius: var(--radius-sm);
      color: var(--vscode-foreground);
      cursor: pointer;
      opacity: 0.7;
      font-size: 16px;
    }
    .refresh-btn:hover { opacity: 1; background: var(--vscode-toolbar-hoverBackground, rgba(90, 93, 94, 0.31)); }

    .section-title {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--vscode-descriptionForeground);
      margin-bottom: var(--spacing-md);
    }

    .inputs-card {
      background: var(--vscode-input-background);
      border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
      border-radius: var(--radius-md);
      padding: var(--spacing-lg);
      margin-bottom: var(--spacing-lg);
    }

    .input-group { margin-bottom: var(--spacing-lg); }
    .input-group:last-child { margin-bottom: 0; }

    .input-label {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: var(--spacing-sm);
    }
    .input-name { font-weight: 500; }
    .type-badge {
      font-size: 11px;
      font-family: var(--vscode-editor-font-family, monospace);
      color: var(--vscode-textLink-foreground, #3794ff);
      background: rgba(55, 148, 255, 0.15);
      padding: 2px 6px;
      border-radius: 3px;
    }

    input[type="text"], input[type="number"] {
      width: 100%;
      padding: var(--spacing-sm) var(--spacing-md);
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
      border-radius: var(--radius-sm);
      font-family: var(--vscode-editor-font-family, monospace);
      font-size: 13px;
    }
    input:focus {
      outline: none;
      border-color: var(--vscode-focusBorder, #007fd4);
      box-shadow: 0 0 0 1px var(--vscode-focusBorder, #007fd4);
    }

    .checkbox-wrapper {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      cursor: pointer;
      padding: var(--spacing-sm) 0;
    }
    .checkbox-wrapper input[type="checkbox"] {
      width: 18px;
      height: 18px;
      cursor: pointer;
    }

    .run-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--spacing-sm);
      width: 100%;
      padding: var(--spacing-md) var(--spacing-lg);
      background: var(--vscode-button-background, #0e639c);
      color: var(--vscode-button-foreground, #fff);
      border: none;
      border-radius: var(--radius-sm);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      margin-bottom: var(--spacing-xl);
    }
    .run-btn:hover:not(:disabled) { background: var(--vscode-button-hoverBackground, #1177bb); }
    .run-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .output-section { display: none; }
    .output-section.visible { display: block; }

    .output-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: var(--spacing-md);
    }
    .execution-time {
      font-size: 11px;
      font-family: var(--vscode-editor-font-family, monospace);
      color: var(--vscode-descriptionForeground);
      background: var(--vscode-badge-background, rgba(77, 77, 77, 0.7));
      padding: 2px 8px;
      border-radius: 10px;
    }

    .output-box {
      background: var(--vscode-textCodeBlock-background, rgba(10, 10, 10, 0.4));
      border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
      border-radius: var(--radius-md);
      padding: var(--spacing-lg);
      font-family: var(--vscode-editor-font-family, monospace);
      font-size: 12px;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 400px;
      overflow-y: auto;
    }
    .output-box.success { border-left: 3px solid var(--vscode-terminal-ansiGreen, #4ec9b0); }

    .error-box {
      background: var(--vscode-inputValidation-errorBackground, rgba(255, 0, 0, 0.1));
      border: 1px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
      border-left: 3px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
      border-radius: var(--radius-md);
      padding: var(--spacing-lg);
      color: var(--vscode-errorForeground, #f14c4c);
      font-size: 13px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: var(--spacing-xl);
      color: var(--vscode-descriptionForeground);
    }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid currentColor;
      border-radius: 50%;
      border-top-color: transparent;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .no-inputs {
      color: var(--vscode-descriptionForeground);
      font-style: italic;
      text-align: center;
      padding: var(--spacing-lg);
    }

    .list-container {
      border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
      border-radius: var(--radius-sm);
      padding: var(--spacing-md);
      background: rgba(0, 0, 0, 0.1);
    }
    .list-items { display: flex; flex-direction: column; gap: var(--spacing-sm); }
    .list-item { display: flex; gap: var(--spacing-sm); align-items: center; }
    .list-item input { flex: 1; }
    .list-item .remove-btn {
      width: 24px;
      height: 24px;
      background: transparent;
      border: none;
      color: var(--vscode-errorForeground, #f14c4c);
      cursor: pointer;
      opacity: 0.6;
      font-size: 14px;
    }
    .list-item .remove-btn:hover { opacity: 1; }
    .add-item-btn {
      margin-top: var(--spacing-sm);
      padding: var(--spacing-xs) var(--spacing-sm);
      background: transparent;
      border: none;
      color: var(--vscode-textLink-foreground, #3794ff);
      cursor: pointer;
      font-size: 12px;
    }
    .add-item-btn:hover { text-decoration: underline; }

    .record-container {
      border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
      border-radius: var(--radius-sm);
      padding: var(--spacing-md);
      background: rgba(0, 0, 0, 0.1);
      margin-top: var(--spacing-xs);
    }
    .record-field { margin-bottom: var(--spacing-md); }
    .record-field:last-child { margin-bottom: 0; }
  </style>
</head>
<body>
  <header class="header">
    <div class="header-left">
      <span class="header-icon">▶</span>
      <div class="header-content">
        <h1>Script Runner</h1>
        <div class="file-name" id="fileName">Loading...</div>
      </div>
    </div>
    <button class="refresh-btn" id="refreshBtn" title="Refresh Schema">↻</button>
  </header>

  <section>
    <div class="section-title">Inputs</div>
    <div class="inputs-card" id="inputsCard">
      <div class="loading-container">
        <div class="spinner"></div>
        <div style="margin-top: 8px; font-size: 12px;">Loading schema...</div>
      </div>
    </div>
  </section>

  <button class="run-btn" id="runBtn" disabled>
    <span>▶</span>
    <span>Run Script</span>
  </button>

  <section class="output-section" id="outputSection">
    <div class="output-header">
      <div class="section-title">Output</div>
      <span class="execution-time" id="executionTime"></span>
    </div>
    <div id="outputContainer"></div>
  </section>

  <script>
(function() {
  var vscode = acquireVsCodeApi();
  var currentSchema = [];
  var isExecuting = false;

  var inputsCard = document.getElementById('inputsCard');
  var outputSection = document.getElementById('outputSection');
  var outputContainer = document.getElementById('outputContainer');
  var executionTime = document.getElementById('executionTime');
  var runBtn = document.getElementById('runBtn');
  var refreshBtn = document.getElementById('refreshBtn');
  var fileNameEl = document.getElementById('fileName');

  refreshBtn.onclick = function() {
    vscode.postMessage({ command: 'refresh' });
  };

  runBtn.onclick = function() {
    if (isExecuting) return;
    var inputs = collectInputs();
    vscode.postMessage({ command: 'execute', inputs: inputs });
  };

  window.addEventListener('message', function(event) {
    var message = event.data;

    if (message.type === 'schema') {
      currentSchema = message.inputs || [];
      fileNameEl.textContent = message.fileName || 'script.cst';
      renderInputs(currentSchema);
      runBtn.disabled = false;
    } else if (message.type === 'schemaError') {
      inputsCard.innerHTML = '<div class="error-box">' + escapeHtml(message.error) + '</div>';
      runBtn.disabled = true;
    } else if (message.type === 'executing') {
      isExecuting = true;
      runBtn.disabled = true;
      runBtn.innerHTML = '<span class="spinner" style="width:14px;height:14px;border-width:2px;"></span><span>Running...</span>';
    } else if (message.type === 'executeResult') {
      isExecuting = false;
      runBtn.disabled = false;
      runBtn.innerHTML = '<span>▶</span><span>Run Script</span>';
      outputSection.classList.add('visible');
      executionTime.textContent = message.executionTimeMs + 'ms';
      outputContainer.innerHTML = '<div class="output-box success">' + escapeHtml(JSON.stringify(message.outputs, null, 2)) + '</div>';
    } else if (message.type === 'executeError') {
      isExecuting = false;
      runBtn.disabled = false;
      runBtn.innerHTML = '<span>▶</span><span>Run Script</span>';
      outputSection.classList.add('visible');
      executionTime.textContent = '';
      outputContainer.innerHTML = '<div class="error-box">' + escapeHtml(message.error) + '</div>';
    }
  });

  function renderInputs(inputs) {
    if (!inputs || inputs.length === 0) {
      inputsCard.innerHTML = '<div class="no-inputs">No inputs defined in this script.</div>';
      return;
    }
    var html = '';
    for (var i = 0; i < inputs.length; i++) {
      html += renderInputGroup(inputs[i].name, inputs[i].type, inputs[i].name);
    }
    inputsCard.innerHTML = html;
  }

  function renderInputGroup(name, type, path) {
    var typeStr = formatType(type);
    var inputHtml = '';

    if (type.kind === 'primitive') {
      inputHtml = renderPrimitiveInput(type.name, path);
    } else if (type.kind === 'list') {
      inputHtml = renderListInput(type, path);
    } else if (type.kind === 'record') {
      inputHtml = renderRecordInput(type, path);
    } else {
      inputHtml = '<input type="text" data-path="' + path + '" data-type="json" placeholder="Enter JSON value">';
    }

    return '<div class="input-group">' +
      '<div class="input-label">' +
        '<span class="input-name">' + escapeHtml(name) + '</span>' +
        '<span class="type-badge">' + escapeHtml(typeStr) + '</span>' +
      '</div>' +
      inputHtml +
    '</div>';
  }

  function renderPrimitiveInput(typeName, path) {
    if (typeName === 'String') {
      return '<input type="text" data-path="' + path + '" data-type="string" placeholder="Enter text">';
    } else if (typeName === 'Int') {
      return '<input type="number" data-path="' + path + '" data-type="int" step="1" placeholder="0">';
    } else if (typeName === 'Float') {
      return '<input type="number" data-path="' + path + '" data-type="float" step="any" placeholder="0.0">';
    } else if (typeName === 'Boolean') {
      return '<label class="checkbox-wrapper"><input type="checkbox" data-path="' + path + '" data-type="boolean"><span>Enabled</span></label>';
    } else {
      return '<input type="text" data-path="' + path + '" data-type="string" placeholder="Enter value">';
    }
  }

  function renderListInput(type, path) {
    var elementType = type.elementType || { kind: 'primitive', name: 'String' };
    var elementTypeJson = JSON.stringify(elementType).replace(/"/g, '&quot;');
    return '<div class="list-container" data-path="' + path + '" data-type="list" data-element-type="' + elementTypeJson + '">' +
      '<div class="list-items"></div>' +
      '<button type="button" class="add-item-btn" onclick="addListItem(this.parentNode)">+ Add item</button>' +
    '</div>';
  }

  function renderRecordInput(type, path) {
    var html = '<div class="record-container">';
    var fields = type.fields || [];
    for (var i = 0; i < fields.length; i++) {
      var field = fields[i];
      var fieldPath = path + '.' + field.name;
      html += '<div class="record-field">' + renderInputGroup(field.name, field.type, fieldPath) + '</div>';
    }
    html += '</div>';
    return html;
  }

  function formatType(type) {
    if (type.kind === 'primitive') return type.name;
    if (type.kind === 'list') return 'List<' + formatType(type.elementType) + '>';
    if (type.kind === 'record') return 'Record';
    if (type.kind === 'ref') return type.name;
    return 'Any';
  }

  window.addListItem = function(listContainer) {
    var itemsContainer = listContainer.querySelector('.list-items');
    var elementTypeJson = listContainer.getAttribute('data-element-type');
    var elementType = JSON.parse(elementTypeJson.replace(/&quot;/g, '"'));
    var path = listContainer.getAttribute('data-path');
    var index = itemsContainer.children.length;

    var itemDiv = document.createElement('div');
    itemDiv.className = 'list-item';

    var inputHtml = '';
    if (elementType.kind === 'primitive') {
      if (elementType.name === 'String') {
        inputHtml = '<input type="text" data-path="' + path + '[' + index + ']" data-type="string" placeholder="Item ' + (index + 1) + '">';
      } else if (elementType.name === 'Int') {
        inputHtml = '<input type="number" data-path="' + path + '[' + index + ']" data-type="int" step="1" placeholder="0">';
      } else if (elementType.name === 'Float') {
        inputHtml = '<input type="number" data-path="' + path + '[' + index + ']" data-type="float" step="any" placeholder="0.0">';
      } else if (elementType.name === 'Boolean') {
        inputHtml = '<input type="checkbox" data-path="' + path + '[' + index + ']" data-type="boolean">';
      } else {
        inputHtml = '<input type="text" data-path="' + path + '[' + index + ']" data-type="string">';
      }
    } else {
      inputHtml = '<input type="text" data-path="' + path + '[' + index + ']" data-type="json" placeholder="JSON value">';
    }

    itemDiv.innerHTML = inputHtml + '<button type="button" class="remove-btn" onclick="removeListItem(this)">×</button>';
    itemsContainer.appendChild(itemDiv);
  };

  window.removeListItem = function(button) {
    button.parentNode.remove();
  };

  function collectInputs() {
    var inputs = {};
    var elements = inputsCard.querySelectorAll('[data-path]');

    for (var i = 0; i < elements.length; i++) {
      var el = elements[i];
      if (el.getAttribute('data-type') === 'list') continue;

      var path = el.getAttribute('data-path');
      var type = el.getAttribute('data-type');
      var value;

      if (type === 'boolean') {
        value = el.checked;
      } else if (type === 'int') {
        value = parseInt(el.value, 10) || 0;
      } else if (type === 'float') {
        value = parseFloat(el.value) || 0.0;
      } else if (type === 'json') {
        try { value = JSON.parse(el.value || 'null'); } catch (e) { value = el.value; }
      } else {
        value = el.value;
      }

      setNestedValue(inputs, path, value);
    }

    var listContainers = inputsCard.querySelectorAll('[data-type="list"]');
    for (var j = 0; j < listContainers.length; j++) {
      var container = listContainers[j];
      var listPath = container.getAttribute('data-path');
      var items = container.querySelectorAll('.list-item [data-path]');
      var values = [];

      for (var k = 0; k < items.length; k++) {
        var item = items[k];
        var itemType = item.getAttribute('data-type');
        var itemValue;

        if (itemType === 'boolean') {
          itemValue = item.checked;
        } else if (itemType === 'int') {
          itemValue = parseInt(item.value, 10) || 0;
        } else if (itemType === 'float') {
          itemValue = parseFloat(item.value) || 0.0;
        } else if (itemType === 'json') {
          try { itemValue = JSON.parse(item.value || 'null'); } catch (e) { itemValue = item.value; }
        } else {
          itemValue = item.value;
        }
        values.push(itemValue);
      }

      setNestedValue(inputs, listPath, values);
    }

    return inputs;
  }

  function setNestedValue(obj, path, value) {
    // Use string methods instead of regex to avoid template literal escaping issues
    var parts = path.split('[').join('.').split(']').join('').split('.');
    var current = obj;

    for (var i = 0; i < parts.length - 1; i++) {
      var part = parts[i];
      var nextPart = parts[i + 1];
      var isNextArray = !isNaN(parseInt(nextPart, 10));

      if (!(part in current)) {
        current[part] = isNextArray ? [] : {};
      }
      current = current[part];
    }

    var lastPart = parts[parts.length - 1];
    current[lastPart] = value;
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  vscode.postMessage({ command: 'ready' });
})();
  </script>
</body>
</html>`;
  }
}
