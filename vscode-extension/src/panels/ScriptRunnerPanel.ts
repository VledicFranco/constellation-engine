import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import {
  createWebviewPanel,
  disposePanel,
  getBaseStyles,
  wrapHtml,
  getHeaderHtml,
  getFileNameFromUri,
  CSS_INPUTS,
  JS_UTILS,
  PanelState
} from '../utils/webviewUtils';

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

interface ErrorInfo {
  category: 'syntax' | 'type' | 'reference' | 'runtime';
  message: string;
  line?: number;
  column?: number;
  endLine?: number;
  endColumn?: number;
  codeContext?: string;
  suggestion?: string;
}

interface GetInputSchemaResult {
  success: boolean;
  inputs?: InputField[];
  error?: string;
  errors?: ErrorInfo[];
}

interface ExecutePipelineResult {
  success: boolean;
  outputs?: { [key: string]: any };
  error?: string;
  errors?: ErrorInfo[];
  executionTimeMs?: number;
}

export class ScriptRunnerPanel {
  public static currentPanel: ScriptRunnerPanel | undefined;
  public static readonly viewType = 'constellationScriptRunner';

  private readonly _state: PanelState;

  public static createOrShow(
    extensionUri: vscode.Uri,
    client: LanguageClient | undefined,
    documentUri: string
  ) {
    if (ScriptRunnerPanel.currentPanel) {
      ScriptRunnerPanel.currentPanel._state.panel.reveal(vscode.ViewColumn.Beside);
      ScriptRunnerPanel.currentPanel._state.client = client;
      ScriptRunnerPanel.currentPanel._state.currentUri = documentUri;
      ScriptRunnerPanel.currentPanel._refreshSchema();
      return;
    }

    const panel = createWebviewPanel({
      viewType: ScriptRunnerPanel.viewType,
      title: 'Script Runner',
      extensionUri
    });

    ScriptRunnerPanel.currentPanel = new ScriptRunnerPanel(panel, extensionUri, client, documentUri);
  }

  private constructor(
    panel: vscode.WebviewPanel,
    extensionUri: vscode.Uri,
    client: LanguageClient | undefined,
    documentUri: string
  ) {
    this._state = {
      panel,
      extensionUri,
      client,
      currentUri: documentUri,
      disposables: []
    };

    this._update();

    this._state.panel.onDidDispose(() => this.dispose(), null, this._state.disposables);

    this._state.panel.webview.onDidReceiveMessage(
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
          case 'navigateToError':
            await this._navigateToError(message.line, message.column);
            break;
        }
      },
      null,
      this._state.disposables
    );
  }

  private async _refreshSchema() {
    console.log('[ScriptRunner] _refreshSchema called, uri:', this._state.currentUri);
    if (!this._state.client || !this._state.currentUri) {
      console.log('[ScriptRunner] No client or URI');
      this._state.panel.webview.postMessage({
        type: 'schemaError',
        error: 'Language server not connected'
      });
      return;
    }

    try {
      console.log('[ScriptRunner] Sending getInputSchema request...');
      const result = await this._state.client.sendRequest<GetInputSchemaResult>(
        'constellation/getInputSchema',
        { uri: this._state.currentUri }
      );
      console.log('[ScriptRunner] Got result:', JSON.stringify(result));

      if (result.success && result.inputs) {
        const fileName = getFileNameFromUri(this._state.currentUri);
        console.log('[ScriptRunner] Posting schema to webview, inputs:', result.inputs.length);
        this._state.panel.webview.postMessage({
          type: 'schema',
          inputs: result.inputs,
          fileName: fileName
        });
      } else {
        console.log('[ScriptRunner] Schema error:', result.error);
        this._state.panel.webview.postMessage({
          type: 'schemaError',
          error: result.error || 'Failed to get input schema',
          errors: result.errors
        });
      }
    } catch (error: any) {
      console.log('[ScriptRunner] Exception:', error);
      this._state.panel.webview.postMessage({
        type: 'schemaError',
        error: error.message || 'Failed to get input schema'
      });
    }
  }

  private async _executePipeline(inputs: { [key: string]: any }) {
    if (!this._state.client || !this._state.currentUri) {
      this._state.panel.webview.postMessage({
        type: 'executeError',
        error: 'Language server not connected'
      });
      return;
    }

    this._state.panel.webview.postMessage({ type: 'executing' });

    const startTime = Date.now();

    try {
      const result = await this._state.client.sendRequest<ExecutePipelineResult>(
        'constellation/executePipeline',
        { uri: this._state.currentUri, inputs }
      );

      const executionTime = Date.now() - startTime;

      if (result.success) {
        this._state.panel.webview.postMessage({
          type: 'executeResult',
          outputs: result.outputs || {},
          executionTimeMs: result.executionTimeMs || executionTime
        });
      } else {
        this._state.panel.webview.postMessage({
          type: 'executeError',
          error: result.error || 'Execution failed',
          errors: result.errors
        });
      }
    } catch (error: any) {
      this._state.panel.webview.postMessage({
        type: 'executeError',
        error: error.message || 'Execution failed'
      });
    }
  }

  private async _navigateToError(line: number, column: number) {
    if (!this._state.currentUri) {
      return;
    }

    try {
      // Convert file:// URI to vscode.Uri
      const documentUri = vscode.Uri.parse(this._state.currentUri);
      const document = await vscode.workspace.openTextDocument(documentUri);
      const editor = await vscode.window.showTextDocument(document, vscode.ViewColumn.One);

      // Create position (0-based in VSCode)
      const position = new vscode.Position(line - 1, column - 1);
      const selection = new vscode.Selection(position, position);

      // Move cursor and reveal the line
      editor.selection = selection;
      editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenter);
    } catch (error) {
      console.error('[ScriptRunner] Failed to navigate to error:', error);
    }
  }

  public dispose() {
    ScriptRunnerPanel.currentPanel = undefined;
    disposePanel(this._state);
  }

  private _update() {
    this._state.panel.webview.html = this._getHtmlContent();
  }

  private _getHtmlContent(): string {
    const styles = getBaseStyles() + CSS_INPUTS + this._getCustomStyles();

    const headerHtml = getHeaderHtml({
      icon: '▶',
      title: 'Script Runner',
      fileNameId: 'fileName',
      actions: '<button class="icon-btn" id="refreshBtn" title="Refresh Schema">↻</button>'
    });

    const body = `
      ${headerHtml}

      <section class="content-section">
        <div class="section-title">Inputs</div>
        <div class="inputs-card" id="inputsCard">
          <div class="loading-container">
            <div class="spinner"></div>
            <div style="margin-top: 8px; font-size: 12px;">Loading schema...</div>
          </div>
        </div>
      </section>

      <button class="primary-btn run-btn" id="runBtn" disabled>
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
    `;

    return wrapHtml({
      title: 'Script Runner',
      styles,
      body,
      scripts: JS_UTILS + this._getScriptContent()
    });
  }

  private _getCustomStyles(): string {
    return `
      body { padding: var(--spacing-lg); }

      .content-section { margin-bottom: var(--spacing-lg); }

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
        width: 100%;
        margin-bottom: var(--spacing-xl);
      }

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

      /* Structured Error Display Styles */
      .error-container {
        background: var(--vscode-inputValidation-errorBackground, rgba(255, 0, 0, 0.1));
        border: 1px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
        border-left: 4px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
        border-radius: var(--radius-md);
        overflow: hidden;
      }

      .error-header {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
        padding: var(--spacing-md) var(--spacing-lg);
        background: rgba(241, 76, 76, 0.15);
        border-bottom: 1px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
      }

      .error-icon {
        font-size: 16px;
        color: var(--vscode-errorForeground, #f14c4c);
      }

      .error-category {
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        padding: 2px 8px;
        border-radius: 3px;
        background: var(--vscode-badge-background, rgba(77, 77, 77, 0.7));
        color: var(--vscode-badge-foreground, #fff);
      }
      .error-category.syntax { background: #d73a4a; }
      .error-category.type { background: #0366d6; }
      .error-category.reference { background: #6f42c1; }
      .error-category.runtime { background: #e36209; }

      .error-location {
        font-size: 12px;
        font-family: var(--vscode-editor-font-family, monospace);
        color: var(--vscode-descriptionForeground);
        margin-left: auto;
        cursor: pointer;
        padding: 2px 6px;
        border-radius: 3px;
        transition: background 0.15s;
      }
      .error-location:hover {
        background: rgba(255, 255, 255, 0.1);
        text-decoration: underline;
      }

      .error-body {
        padding: var(--spacing-lg);
      }

      .error-message {
        font-size: 13px;
        line-height: 1.5;
        color: var(--vscode-foreground);
        margin-bottom: var(--spacing-md);
      }

      .error-code-context {
        background: var(--vscode-textCodeBlock-background, rgba(10, 10, 10, 0.4));
        border: 1px solid var(--vscode-input-border, rgba(128, 128, 128, 0.35));
        border-radius: var(--radius-sm);
        padding: var(--spacing-md);
        font-family: var(--vscode-editor-font-family, monospace);
        font-size: 12px;
        line-height: 1.6;
        overflow-x: auto;
        white-space: pre;
        margin-bottom: var(--spacing-md);
      }

      .error-suggestion {
        display: flex;
        align-items: flex-start;
        gap: var(--spacing-sm);
        padding: var(--spacing-sm) var(--spacing-md);
        background: rgba(79, 193, 255, 0.1);
        border: 1px solid rgba(79, 193, 255, 0.3);
        border-radius: var(--radius-sm);
        font-size: 12px;
        color: var(--vscode-textLink-foreground, #3794ff);
      }

      .error-suggestion-icon {
        flex-shrink: 0;
        margin-top: 2px;
      }
    `;
  }

  private _getScriptContent(): string {
    return `
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
      if (message.errors && message.errors.length > 0) {
        inputsCard.innerHTML = renderStructuredErrors(message.errors);
      } else {
        inputsCard.innerHTML = '<div class="error-box">' + escapeHtml(message.error) + '</div>';
      }
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
      if (message.errors && message.errors.length > 0) {
        outputContainer.innerHTML = renderStructuredErrors(message.errors);
      } else {
        outputContainer.innerHTML = '<div class="error-box">' + escapeHtml(message.error) + '</div>';
      }
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

  function renderStructuredErrors(errors) {
    var html = '';
    for (var i = 0; i < errors.length; i++) {
      html += renderSingleError(errors[i]);
    }
    return html;
  }

  function renderSingleError(error) {
    var categoryLabels = {
      'syntax': 'Syntax Error',
      'type': 'Type Error',
      'reference': 'Reference Error',
      'runtime': 'Runtime Error'
    };

    var categoryLabel = categoryLabels[error.category] || 'Error';
    var locationText = '';
    if (error.line) {
      locationText = 'Line ' + error.line;
      if (error.column) {
        locationText += ':' + error.column;
      }
    }

    var html = '<div class="error-container">';

    // Error header with category badge and location
    html += '<div class="error-header">';
    html += '<span class="error-icon">✕</span>';
    html += '<span class="error-category ' + escapeHtml(error.category) + '">' + escapeHtml(categoryLabel) + '</span>';
    if (locationText) {
      html += '<span class="error-location" data-line="' + error.line + '" data-column="' + (error.column || 1) + '" onclick="navigateToError(this)">' + escapeHtml(locationText) + '</span>';
    }
    html += '</div>';

    // Error body with message, code context, and suggestion
    html += '<div class="error-body">';
    html += '<div class="error-message">' + escapeHtml(error.message) + '</div>';

    if (error.codeContext) {
      html += '<div class="error-code-context">' + escapeHtml(error.codeContext) + '</div>';
    }

    if (error.suggestion) {
      html += '<div class="error-suggestion">';
      html += '<span class="error-suggestion-icon">💡</span>';
      html += '<span>' + escapeHtml(error.suggestion) + '</span>';
      html += '</div>';
    }

    html += '</div>';
    html += '</div>';

    return html;
  }

  window.navigateToError = function(element) {
    var line = parseInt(element.getAttribute('data-line'), 10);
    var column = parseInt(element.getAttribute('data-column'), 10) || 1;
    vscode.postMessage({
      command: 'navigateToError',
      line: line,
      column: column
    });
  };

  vscode.postMessage({ command: 'ready' });
})();
    `;
  }
}
