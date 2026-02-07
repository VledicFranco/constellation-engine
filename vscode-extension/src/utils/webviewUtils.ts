import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

/**
 * Options for creating a webview panel
 */
export interface PanelOptions {
  viewType: string;
  title: string;
  extensionUri: vscode.Uri;
  column?: vscode.ViewColumn;
  webviewOptions?: vscode.WebviewPanelOptions & vscode.WebviewOptions;
}

/**
 * Common panel state shared across all webview panels
 */
export interface PanelState {
  panel: vscode.WebviewPanel;
  extensionUri: vscode.Uri;
  client: LanguageClient | undefined;
  currentUri: string | undefined;
  disposables: vscode.Disposable[];
}

/**
 * Creates a webview panel with common default options
 */
export function createWebviewPanel(options: PanelOptions): vscode.WebviewPanel {
  const column = options.column ?? vscode.ViewColumn.Beside;

  const defaultWebviewOptions: vscode.WebviewPanelOptions & vscode.WebviewOptions = {
    enableScripts: true,
    retainContextWhenHidden: true,
    localResourceRoots: [vscode.Uri.joinPath(options.extensionUri, 'src', 'webview')]
  };

  return vscode.window.createWebviewPanel(
    options.viewType,
    options.title,
    column,
    { ...defaultWebviewOptions, ...options.webviewOptions }
  );
}

/**
 * Disposes all resources for a panel
 */
export function disposePanel(state: PanelState): void {
  state.panel.dispose();

  while (state.disposables.length) {
    const disposable = state.disposables.pop();
    if (disposable) {
      disposable.dispose();
    }
  }
}

/**
 * Common CSS variables used across all panels
 */
export const CSS_VARIABLES = `
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 12px;
  --spacing-lg: 16px;
  --spacing-xl: 20px;
  --radius-sm: 4px;
  --radius-md: 6px;
`;

/**
 * Common CSS reset and base styles
 */
export const CSS_RESET = `
  * { box-sizing: border-box; margin: 0; padding: 0; }

  body {
    font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif);
    font-size: var(--vscode-font-size, 13px);
    color: var(--vscode-foreground);
    background: var(--vscode-editor-background);
    line-height: 1.5;
  }
`;

/**
 * Common header styles for panels
 */
export const CSS_HEADER = `
  .header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: var(--spacing-md) var(--spacing-lg);
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

  .header-actions { display: flex; gap: var(--spacing-xs); }
`;

/**
 * Common button styles
 */
export const CSS_BUTTONS = `
  .icon-btn {
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
  .icon-btn:hover { opacity: 1; background: var(--vscode-toolbar-hoverBackground, rgba(90, 93, 94, 0.31)); }

  .primary-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-md) var(--spacing-lg);
    background: var(--vscode-button-background, #0e639c);
    color: var(--vscode-button-foreground, #fff);
    border: none;
    border-radius: var(--radius-sm);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
  }
  .primary-btn:hover:not(:disabled) { background: var(--vscode-button-hoverBackground, #1177bb); }
  .primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
`;

/**
 * Common loading/spinner styles
 */
export const CSS_LOADING = `
  .loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: var(--spacing-xl);
    color: var(--vscode-descriptionForeground);
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 2px solid currentColor;
    border-radius: 50%;
    border-top-color: transparent;
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
`;

/**
 * Common error box styles
 */
export const CSS_ERROR = `
  .error-box {
    background: var(--vscode-inputValidation-errorBackground, rgba(255, 0, 0, 0.1));
    border: 1px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
    border-left: 3px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
    border-radius: var(--radius-md);
    padding: var(--spacing-lg);
    color: var(--vscode-errorForeground, #f14c4c);
    font-size: 13px;
  }
`;

/**
 * Common input styles
 */
export const CSS_INPUTS = `
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
`;

/**
 * Get all common base styles combined
 */
export function getBaseStyles(additionalVariables: string = ''): string {
  return `
    :root {
      ${CSS_VARIABLES}
      ${additionalVariables}
    }
    ${CSS_RESET}
    ${CSS_HEADER}
    ${CSS_BUTTONS}
    ${CSS_LOADING}
    ${CSS_ERROR}
  `;
}

/**
 * Content Security Policy for webviews
 */
export const CSP_META = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';">`;

/**
 * Standard HTML head content
 */
export function getHtmlHead(title: string, styles: string): string {
  return `
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    ${CSP_META}
    <title>${escapeHtml(title)}</title>
    <style>
${styles}
    </style>
  `;
}

/**
 * Wrap content in a complete HTML document
 */
export function wrapHtml(options: {
  title: string;
  styles: string;
  body: string;
  scripts: string;
}): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
${getHtmlHead(options.title, options.styles)}
</head>
<body>
${options.body}
<script>
${options.scripts}
</script>
</body>
</html>`;
}

/**
 * Generate a standard panel header HTML
 */
export function getHeaderHtml(options: {
  icon: string;
  title: string;
  fileNameId?: string;
  actions?: string;
}): string {
  const fileNameEl = options.fileNameId
    ? `<div class="file-name" id="${options.fileNameId}">Loading...</div>`
    : '';

  const actionsEl = options.actions
    ? `<div class="header-actions">${options.actions}</div>`
    : '';

  return `
    <header class="header">
      <div class="header-left">
        <span class="header-icon">${options.icon}</span>
        <div class="header-content">
          <h1>${escapeHtml(options.title)}</h1>
          ${fileNameEl}
        </div>
      </div>
      ${actionsEl}
    </header>
  `;
}

/**
 * Common JavaScript utilities for webview scripts
 */
export const JS_UTILS = `
  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
`;

/**
 * Escape HTML special characters for safe rendering
 */
export function escapeHtml(text: string): string {
  const escapeMap: { [key: string]: string } = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  };
  return text.replace(/[&<>"']/g, char => escapeMap[char]);
}

/**
 * Extract filename from a URI string
 */
export function getFileNameFromUri(uri: string): string {
  return uri.split('/').pop() || 'pipeline.cst';
}
