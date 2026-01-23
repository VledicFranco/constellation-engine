import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';
import {
  createWebviewPanel,
  disposePanel,
  getBaseStyles,
  wrapHtml,
  getHeaderHtml,
  getFileNameFromUri,
  PanelState
} from '../utils/webviewUtils';

interface DagStructure {
  modules: { [uuid: string]: ModuleNode };
  data: { [uuid: string]: DataNode };
  inEdges: [string, string][];
  outEdges: [string, string][];
  declaredOutputs: string[];
}

interface ModuleNode {
  name: string;
  consumes: { [key: string]: string };
  produces: { [key: string]: string };
}

interface DataNode {
  name: string;
  cType: string;
}

interface GetDagStructureResult {
  success: boolean;
  dag?: DagStructure;
  error?: string;
}

// ========== New DagVizIR Types (from server-side layout) ==========

type NodeKind =
  | 'Input' | 'Output' | 'Operation' | 'Literal' | 'Merge' | 'Project'
  | 'FieldAccess' | 'Conditional' | 'Guard' | 'Branch' | 'Coalesce'
  | 'HigherOrder' | 'ListLiteral' | 'BooleanOp' | 'StringInterp';

type EdgeKind = 'Data' | 'Optional' | 'Control';

type ExecutionStatus = 'Pending' | 'Running' | 'Completed' | 'Failed';

interface DagVizPosition {
  x: number;
  y: number;
}

interface DagVizBounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

interface DagVizExecutionState {
  status: ExecutionStatus;
  value?: any;
  durationMs?: number;
  error?: string;
}

interface DagVizNode {
  id: string;
  kind: NodeKind;
  label: string;
  typeSignature: string;
  position?: DagVizPosition;
  executionState?: DagVizExecutionState;
}

interface DagVizEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  kind: EdgeKind;
}

interface DagVizGroup {
  id: string;
  label: string;
  nodeIds: string[];
  collapsed: boolean;
}

interface DagVizMetadata {
  title?: string;
  layoutDirection: string;
  bounds?: DagVizBounds;
}

interface DagVisualization {
  nodes: DagVizNode[];
  edges: DagVizEdge[];
  groups: DagVizGroup[];
  metadata: DagVizMetadata;
}

interface GetDagVisualizationParams {
  uri: string;
  direction?: string;
  executionId?: string;
}

interface GetDagVisualizationResult {
  success: boolean;
  dag?: DagVisualization;
  error?: string;
}

export class DagVisualizerPanel {
  public static currentPanel: DagVisualizerPanel | undefined;
  public static readonly viewType = 'constellationDagVisualizer';

  private readonly _state: PanelState;

  public static createOrShow(
    extensionUri: vscode.Uri,
    client: LanguageClient | undefined,
    documentUri: string
  ) {
    if (DagVisualizerPanel.currentPanel) {
      DagVisualizerPanel.currentPanel._state.panel.reveal(vscode.ViewColumn.Beside);
      DagVisualizerPanel.currentPanel._state.client = client;
      DagVisualizerPanel.currentPanel._state.currentUri = documentUri;
      DagVisualizerPanel.currentPanel._refreshDag();
      return;
    }

    const panel = createWebviewPanel({
      viewType: DagVisualizerPanel.viewType,
      title: 'DAG Visualizer',
      extensionUri
    });

    DagVisualizerPanel.currentPanel = new DagVisualizerPanel(panel, extensionUri, client, documentUri);
  }

  public static refresh(documentUri: string) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._state.currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._refreshDag();
    }
  }

  /** Notify the DAG visualizer that execution has started */
  public static notifyExecutionStart(documentUri: string) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._state.currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._state.panel.webview.postMessage({
        type: 'executionStart'
      });
    }
  }

  /** Notify the DAG visualizer that execution has completed */
  public static notifyExecutionComplete(documentUri: string, success: boolean) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._state.currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._state.panel.webview.postMessage({
        type: 'executionComplete',
        success
      });
    }
  }

  /** Update a single node's execution state */
  public static notifyNodeUpdate(documentUri: string, nodeId: string, state: string, valuePreview?: string) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._state.currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._state.panel.webview.postMessage({
        type: 'executionUpdate',
        nodeId,
        state,
        valuePreview
      });
    }
  }

  /** Batch update multiple nodes' execution states */
  public static notifyBatchUpdate(documentUri: string, updates: Array<{nodeId: string, state: string, valuePreview?: string}>) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._state.currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._state.panel.webview.postMessage({
        type: 'executionBatchUpdate',
        updates
      });
    }
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

    // Load saved layout direction preference
    this._layoutDirection = vscode.workspace.getConfiguration('constellation').get<string>('dagLayoutDirection', 'TB');

    this._update();

    this._state.panel.onDidDispose(() => this.dispose(), null, this._state.disposables);

    this._state.panel.webview.onDidReceiveMessage(
      async (message) => {
        switch (message.command) {
          case 'ready':
            // Send saved layout direction preference
            const savedDirection = vscode.workspace.getConfiguration('constellation').get<string>('dagLayoutDirection', 'TB');
            this._state.panel.webview.postMessage({ type: 'setLayoutDirection', direction: savedDirection });
            await this._refreshDag();
            break;
          case 'refresh':
            await this._refreshDag();
            break;
          case 'nodeClick':
            // Future: handle node click for details panel
            console.log('Node clicked:', message.nodeId, message.nodeType);
            break;
          case 'setLayoutDirection':
            // Update local state and persist layout direction preference
            this._layoutDirection = message.direction || 'TB';
            vscode.workspace.getConfiguration('constellation').update(
              'dagLayoutDirection',
              message.direction,
              vscode.ConfigurationTarget.Global
            );
            // Re-fetch DAG with new direction (server will re-layout)
            await this._refreshDag();
            break;
        }
      },
      null,
      this._state.disposables
    );
  }

  private _layoutDirection: string = 'TB';

  private async _refreshDag() {
    if (!this._state.client || !this._state.currentUri) {
      this._state.panel.webview.postMessage({
        type: 'error',
        message: 'Language server not connected'
      });
      return;
    }

    this._state.panel.webview.postMessage({ type: 'loading', loading: true });

    const fileName = getFileNameFromUri(this._state.currentUri);

    // Try the new getDagVisualization endpoint first (server-side layout)
    try {
      const vizResult = await this._state.client.sendRequest<GetDagVisualizationResult>(
        'constellation/getDagVisualization',
        {
          uri: this._state.currentUri,
          direction: this._layoutDirection
        } as GetDagVisualizationParams
      );

      if (vizResult.success && vizResult.dag) {
        this._state.panel.webview.postMessage({
          type: 'dagVizData',
          dag: vizResult.dag,
          fileName: fileName
        });
        return;
      }
      // If new endpoint returned error, fall through to legacy endpoint
      console.log('getDagVisualization returned error, falling back to getDagStructure:', vizResult.error);
    } catch (vizError: any) {
      // New endpoint not available or failed, fall back to legacy
      console.log('getDagVisualization failed, falling back to getDagStructure:', vizError.message);
    }

    // Fallback: Use legacy getDagStructure endpoint (client-side layout)
    try {
      const result = await this._state.client.sendRequest<GetDagStructureResult>(
        'constellation/getDagStructure',
        { uri: this._state.currentUri }
      );

      if (result.success && result.dag) {
        this._state.panel.webview.postMessage({
          type: 'dagData',
          dag: result.dag,
          fileName: fileName
        });
      } else {
        this._state.panel.webview.postMessage({
          type: 'error',
          message: result.error || 'Failed to get DAG structure'
        });
      }
    } catch (error: any) {
      this._state.panel.webview.postMessage({
        type: 'error',
        message: error.message || 'Failed to get DAG structure'
      });
    }
  }

  public dispose() {
    DagVisualizerPanel.currentPanel = undefined;
    disposePanel(this._state);
  }

  private _update() {
    this._state.panel.webview.html = this._getHtmlContent();
  }

  private _getHtmlContent(): string {
    const additionalVars = `
      --node-data-border: var(--vscode-charts-blue, #3794ff);
      --node-module-border: var(--vscode-charts-orange, #d18616);
      --edge-color: var(--vscode-charts-yellow, #cca700);
      --state-pending: var(--vscode-editorGutter-commentRangeForeground, #6e7681);
      --state-running: var(--vscode-charts-blue, #3794ff);
      --state-completed: var(--vscode-charts-green, #3fb950);
      --state-failed: var(--vscode-charts-red, #f85149);
      /* Data type colors */
      --type-string: #98c379;
      --type-int: #61afef;
      --type-float: #56b6c2;
      --type-boolean: #c678dd;
      --type-list: #e5c07b;
      --type-record: #d19a66;
      --type-optional: #7f848e;
      --type-unknown: var(--vscode-descriptionForeground, #888888);
    `;

    const styles = getBaseStyles(additionalVars) + this._getCustomStyles();

    const headerHtml = getHeaderHtml({
      icon: '◆',
      title: 'DAG Visualizer',
      fileNameId: 'fileName',
      actions: `
        <div class="layout-toggle">
          <button class="toggle-btn active" id="tbBtn" title="Top to Bottom">↓ TB</button>
          <button class="toggle-btn" id="lrBtn" title="Left to Right">→ LR</button>
        </div>
        <div class="zoom-controls">
          <button class="icon-btn small" id="zoomOutBtn" title="Zoom out">−</button>
          <span class="zoom-level" id="zoomLevel">100%</span>
          <button class="icon-btn small" id="zoomInBtn" title="Zoom in">+</button>
          <button class="icon-btn" id="fitBtn" title="Fit to view">⊡</button>
        </div>
        <div class="export-dropdown">
          <button class="icon-btn" id="exportBtn" title="Export">📷</button>
          <div class="export-menu" id="exportMenu">
            <button class="export-option" id="exportPngBtn">Export as PNG</button>
            <button class="export-option" id="exportSvgBtn">Export as SVG</button>
          </div>
        </div>
        <button class="icon-btn" id="resetBtn" title="Reset execution state">⟲</button>
        <button class="icon-btn" id="refreshBtn" title="Refresh">↻</button>
      `
    });

    const body = `
      ${headerHtml}

      <div class="search-bar">
        <input type="text" id="searchInput" placeholder="Search nodes..." />
        <button class="icon-btn small" id="clearSearchBtn" title="Clear search">✕</button>
      </div>

      <div class="container" id="container">
        <div class="loading-container" id="loadingContainer">
          <div class="spinner"></div>
          <div style="margin-top: 8px; font-size: 12px;">Loading DAG...</div>
        </div>
        <svg id="dagSvg" class="dag-svg"></svg>

        <div class="node-details-panel" id="nodeDetailsPanel" style="display: none;">
          <div class="details-header">
            <span class="details-title" id="detailsTitle">Node Details</span>
            <button class="icon-btn small" id="closeDetailsBtn" title="Close">✕</button>
          </div>
          <div class="details-content" id="detailsContent"></div>
        </div>
      </div>

      <div class="error-container" id="errorContainer" style="display: none;">
        <div class="error-box" id="errorMessage"></div>
      </div>
    `;

    return wrapHtml({
      title: 'DAG Visualizer',
      styles,
      body,
      scripts: this._getScriptContent()
    });
  }

  private _getCustomStyles(): string {
    return `
      body {
        overflow: hidden;
        height: 100vh;
        display: flex;
        flex-direction: column;
      }

      .header { flex-shrink: 0; }

      .layout-toggle {
        display: flex;
        gap: 2px;
        background: var(--vscode-input-background, rgba(60, 60, 60, 0.5));
        border-radius: var(--radius-sm);
        padding: 2px;
      }

      .toggle-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 4px 8px;
        background: transparent;
        border: none;
        border-radius: 3px;
        color: var(--vscode-foreground);
        cursor: pointer;
        opacity: 0.6;
        font-size: 11px;
        font-weight: 500;
        transition: all 0.15s ease;
      }

      .toggle-btn:hover {
        opacity: 0.9;
        background: var(--vscode-toolbar-hoverBackground, rgba(90, 93, 94, 0.31));
      }

      .toggle-btn.active {
        opacity: 1;
        background: var(--vscode-button-background, #0e639c);
        color: var(--vscode-button-foreground, #fff);
      }

      .zoom-controls {
        display: flex;
        align-items: center;
        gap: 4px;
        background: var(--vscode-input-background, rgba(60, 60, 60, 0.5));
        border-radius: var(--radius-sm);
        padding: 2px 6px;
      }

      .zoom-level {
        font-size: 11px;
        min-width: 40px;
        text-align: center;
        color: var(--vscode-foreground);
        opacity: 0.8;
      }

      .icon-btn.small {
        width: 20px;
        height: 20px;
        font-size: 14px;
        padding: 0;
      }

      .export-dropdown {
        position: relative;
      }

      .export-menu {
        display: none;
        position: absolute;
        top: 100%;
        right: 0;
        margin-top: 4px;
        background: var(--vscode-dropdown-background, #3c3c3c);
        border: 1px solid var(--vscode-dropdown-border, #454545);
        border-radius: var(--radius-sm);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
        z-index: 100;
        min-width: 140px;
      }

      .export-menu.show {
        display: block;
      }

      .export-option {
        display: block;
        width: 100%;
        padding: 8px 12px;
        background: transparent;
        border: none;
        color: var(--vscode-dropdown-foreground, #ccc);
        font-size: 12px;
        text-align: left;
        cursor: pointer;
      }

      .export-option:hover {
        background: var(--vscode-list-hoverBackground, rgba(90, 93, 94, 0.31));
      }

      .export-option:first-child {
        border-radius: var(--radius-sm) var(--radius-sm) 0 0;
      }

      .export-option:last-child {
        border-radius: 0 0 var(--radius-sm) var(--radius-sm);
      }

      .container {
        flex: 1;
        position: relative;
        overflow: hidden;
      }

      .dag-svg {
        width: 100%;
        height: 100%;
        display: block;
      }

      .loading-container {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
      }

      .error-container {
        padding: var(--spacing-lg);
      }

      .search-bar {
        display: flex;
        align-items: center;
        padding: 8px 12px;
        gap: 8px;
        background: var(--vscode-sideBar-background, rgba(30, 30, 30, 0.8));
        border-bottom: 1px solid var(--vscode-panel-border, rgba(128, 128, 128, 0.35));
      }

      .search-bar input {
        flex: 1;
        padding: 6px 10px;
        background: var(--vscode-input-background, #3c3c3c);
        border: 1px solid var(--vscode-input-border, #3c3c3c);
        border-radius: var(--radius-sm);
        color: var(--vscode-input-foreground, #cccccc);
        font-size: 12px;
      }

      .search-bar input:focus {
        outline: none;
        border-color: var(--vscode-focusBorder, #007fd4);
      }

      .search-bar input::placeholder {
        color: var(--vscode-input-placeholderForeground, #a0a0a0);
      }

      .node-details-panel {
        position: absolute;
        bottom: 12px;
        right: 12px;
        width: 280px;
        max-height: 300px;
        background: var(--vscode-editor-background, #1e1e1e);
        border: 1px solid var(--vscode-panel-border, rgba(128, 128, 128, 0.35));
        border-radius: var(--radius-md, 6px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        overflow: hidden;
        z-index: 50;
      }

      .details-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 12px;
        background: var(--vscode-sideBar-background, rgba(30, 30, 30, 0.8));
        border-bottom: 1px solid var(--vscode-panel-border, rgba(128, 128, 128, 0.35));
      }

      .details-title {
        font-size: 12px;
        font-weight: 600;
        color: var(--vscode-foreground);
      }

      .details-content {
        padding: 12px;
        font-size: 11px;
        line-height: 1.5;
        overflow-y: auto;
        max-height: 240px;
      }

      .detail-row {
        display: flex;
        margin-bottom: 8px;
      }

      .detail-label {
        width: 80px;
        color: var(--vscode-descriptionForeground, #888);
        flex-shrink: 0;
      }

      .detail-value {
        color: var(--vscode-foreground);
        word-break: break-word;
      }

      .detail-value.type-badge {
        padding: 2px 6px;
        border-radius: 3px;
        font-family: var(--vscode-editor-font-family, monospace);
        font-size: 10px;
      }

      .dag-node.search-match rect,
      .dag-node.search-match ellipse,
      .dag-node.search-match polygon {
        stroke-width: 3;
        filter: drop-shadow(0 0 6px var(--vscode-charts-blue, #3794ff));
      }

      .dag-node.search-dimmed {
        opacity: 0.3;
      }

      .dag-node {
        cursor: pointer;
      }

      .dag-node rect, .dag-node ellipse, .dag-node polygon, .dag-node path {
        fill: var(--vscode-editor-background);
        stroke-width: 2;
        transition: stroke-width 0.15s ease;
      }

      .dag-node:hover rect, .dag-node:hover ellipse, .dag-node:hover polygon, .dag-node:hover path {
        stroke-width: 3;
      }

      /* Input nodes: pill/ellipse shape with green border */
      .dag-node-input ellipse {
        stroke: var(--vscode-charts-green, #3fb950);
      }

      /* Output nodes: hexagon shape with purple border */
      .dag-node-output polygon {
        stroke: var(--vscode-charts-purple, #a371f7);
      }

      /* Intermediate data nodes: rounded rectangle with blue border */
      .dag-node-data rect {
        stroke: var(--node-data-border);
        rx: 8;
        ry: 8;
      }

      /* Module/operation nodes: rectangle with header bar */
      .dag-node-module rect.node-body {
        stroke: var(--node-module-border);
      }

      .dag-node-module rect.node-header {
        fill: var(--node-module-border);
        stroke: var(--node-module-border);
      }

      .dag-node text {
        fill: var(--vscode-foreground);
        font-family: var(--vscode-editor-font-family, monospace);
        font-size: 12px;
        text-anchor: middle;
        dominant-baseline: middle;
        pointer-events: none;
      }

      .dag-node .node-type {
        font-size: 10px;
        font-weight: 500;
      }

      .dag-node .node-role {
        font-size: 9px;
        fill: var(--vscode-descriptionForeground);
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }

      /* Type indicator badge */
      .dag-node .type-badge {
        font-size: 9px;
        font-weight: 600;
        fill: var(--vscode-editor-background);
      }

      .dag-node .type-badge-bg {
        rx: 3;
        ry: 3;
      }

      .dag-edge path {
        fill: none;
        stroke: var(--edge-color);
        stroke-width: 1.5;
        opacity: 0.5;
        transition: opacity 0.2s ease, stroke-width 0.2s ease, stroke 0.2s ease;
      }

      .dag-edge:hover path,
      .dag-edge.edge-highlight path {
        opacity: 1;
        stroke-width: 2.5;
        stroke: var(--vscode-charts-orange, #d18616);
      }

      .dag-edge polygon {
        fill: var(--edge-color);
        opacity: 0.5;
        transition: opacity 0.2s ease, fill 0.2s ease;
      }

      .dag-edge:hover polygon,
      .dag-edge.edge-highlight polygon {
        opacity: 1;
        fill: var(--vscode-charts-orange, #d18616);
      }

      /* Connected node highlighting */
      .dag-node.connected-highlight rect,
      .dag-node.connected-highlight ellipse,
      .dag-node.connected-highlight polygon {
        stroke-width: 3;
        filter: drop-shadow(0 0 4px var(--edge-color));
      }

      .dag-node.connected-highlight text {
        font-weight: bold;
      }

      /* Execution state styles - apply to all shape types */
      .dag-node.state-pending rect,
      .dag-node.state-pending ellipse,
      .dag-node.state-pending polygon {
        stroke: var(--state-pending) !important;
        opacity: 0.6;
      }

      .dag-node.state-running rect,
      .dag-node.state-running ellipse,
      .dag-node.state-running polygon {
        stroke: var(--state-running) !important;
        stroke-width: 3;
        animation: pulse 1.5s ease-in-out infinite;
      }

      .dag-node.state-completed rect,
      .dag-node.state-completed ellipse,
      .dag-node.state-completed polygon {
        stroke: var(--state-completed) !important;
      }

      .dag-node.state-failed rect,
      .dag-node.state-failed ellipse,
      .dag-node.state-failed polygon {
        stroke: var(--state-failed) !important;
      }

      @keyframes pulse {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.5; }
      }

      /* State indicator icon */
      .dag-node .state-icon {
        font-size: 14px;
        dominant-baseline: middle;
        text-anchor: end;
      }

      .dag-node.state-completed .state-icon {
        fill: var(--state-completed);
      }

      .dag-node.state-failed .state-icon {
        fill: var(--state-failed);
      }

      .dag-node.state-running .state-icon {
        fill: var(--state-running);
      }

      /* Value preview tooltip */
      .value-preview {
        font-size: 9px;
        fill: var(--vscode-descriptionForeground);
        opacity: 0.8;
      }

      /* ========== New DagVizIR Node Kind Styles ========== */

      /* Node icon styles */
      .dag-node .node-icon {
        font-size: 12px;
        text-anchor: start;
        dominant-baseline: hanging;
      }

      /* Node kind specific styles */
      .dag-node-input path,
      .dag-node-input rect {
        stroke: #22c55e;
      }

      .dag-node-output path,
      .dag-node-output rect {
        stroke: #3b82f6;
      }

      .dag-node-literal rect {
        stroke: #14b8a6;
      }

      .dag-node-merge rect {
        stroke: #a855f7;
      }

      .dag-node-project rect,
      .dag-node-field rect {
        stroke: #f97316;
      }

      .dag-node-guard rect {
        stroke: #eab308;
      }

      .dag-node-branch rect,
      .dag-node-cond rect {
        stroke: #6366f1;
      }

      .dag-node-coalesce rect {
        stroke: #a855f7;
      }

      .dag-node-higher rect {
        stroke: #ec4899;
      }

      .dag-node-list rect {
        stroke: #14b8a6;
      }

      .dag-node-bool rect {
        stroke: #c678dd;
      }

      .dag-node-string rect {
        stroke: #98c379;
      }

      /* Edge kind styles */
      .dag-edge-data path {
        stroke: var(--edge-color);
        stroke-width: 1.5;
      }

      .dag-edge-optional path {
        stroke: var(--edge-color);
        stroke-width: 1.5;
        stroke-dasharray: 5, 5;
        opacity: 0.7;
      }

      .dag-edge-control path {
        stroke: var(--vscode-charts-purple, #a371f7);
        stroke-width: 1.5;
        stroke-dasharray: 3, 3;
      }

      .edge-label {
        font-size: 9px;
        fill: var(--vscode-descriptionForeground);
        text-anchor: middle;
      }
    `;
  }

  private _getScriptContent(): string {
    return `
(function() {
  var vscode = acquireVsCodeApi();

  var container = document.getElementById('container');
  var dagSvg = document.getElementById('dagSvg');
  var loadingContainer = document.getElementById('loadingContainer');
  var errorContainer = document.getElementById('errorContainer');
  var errorMessage = document.getElementById('errorMessage');
  var fileNameEl = document.getElementById('fileName');
  var refreshBtn = document.getElementById('refreshBtn');
  var tbBtn = document.getElementById('tbBtn');
  var lrBtn = document.getElementById('lrBtn');
  var exportBtn = document.getElementById('exportBtn');
  var exportMenu = document.getElementById('exportMenu');
  var exportPngBtn = document.getElementById('exportPngBtn');
  var exportSvgBtn = document.getElementById('exportSvgBtn');
  var resetBtn = document.getElementById('resetBtn');
  var zoomInBtn = document.getElementById('zoomInBtn');
  var zoomOutBtn = document.getElementById('zoomOutBtn');
  var fitBtn = document.getElementById('fitBtn');
  var zoomLevelEl = document.getElementById('zoomLevel');
  var searchInput = document.getElementById('searchInput');
  var clearSearchBtn = document.getElementById('clearSearchBtn');
  var nodeDetailsPanel = document.getElementById('nodeDetailsPanel');
  var detailsTitle = document.getElementById('detailsTitle');
  var detailsContent = document.getElementById('detailsContent');
  var closeDetailsBtn = document.getElementById('closeDetailsBtn');

  var currentDag = null;
  var currentFileName = 'dag';
  var executionStates = {}; // nodeId -> { state: 'pending'|'running'|'completed'|'failed', valuePreview?: string }
  var layoutDirection = 'TB'; // 'TB' = top-to-bottom, 'LR' = left-to-right
  var viewBox = { x: 0, y: 0, width: 800, height: 600 };
  var originalViewBox = { x: 0, y: 0, width: 800, height: 600 }; // For fit-to-view
  var isPanning = false;
  var startPan = { x: 0, y: 0 };
  var scale = 1;

  refreshBtn.onclick = function() {
    vscode.postMessage({ command: 'refresh' });
  };

  tbBtn.onclick = function() {
    if (layoutDirection !== 'TB') {
      setLayoutDirection('TB');
    }
  };

  lrBtn.onclick = function() {
    if (layoutDirection !== 'LR') {
      setLayoutDirection('LR');
    }
  };

  // Export dropdown handling
  exportBtn.onclick = function(e) {
    e.stopPropagation();
    exportMenu.classList.toggle('show');
  };

  // Close dropdown when clicking outside
  document.addEventListener('click', function(e) {
    if (!exportMenu.contains(e.target) && e.target !== exportBtn) {
      exportMenu.classList.remove('show');
    }
  });

  exportPngBtn.onclick = function() {
    exportMenu.classList.remove('show');
    exportAsPng();
  };

  exportSvgBtn.onclick = function() {
    exportMenu.classList.remove('show');
    exportAsSvg();
  };

  resetBtn.onclick = function() {
    resetExecutionStates();
  };

  zoomInBtn.onclick = function() {
    zoomBy(0.8); // Zoom in by decreasing viewBox size
  };

  zoomOutBtn.onclick = function() {
    zoomBy(1.25); // Zoom out by increasing viewBox size
  };

  fitBtn.onclick = function() {
    fitToView();
  };

  // Search functionality
  searchInput.oninput = function() {
    var query = searchInput.value.toLowerCase().trim();
    filterNodes(query);
  };

  clearSearchBtn.onclick = function() {
    searchInput.value = '';
    filterNodes('');
  };

  // Details panel
  closeDetailsBtn.onclick = function() {
    nodeDetailsPanel.style.display = 'none';
  };

  function filterNodes(query) {
    var nodes = document.querySelectorAll('.dag-node');
    nodes.forEach(function(node) {
      var nodeId = node.getAttribute('data-node-id');
      var nodeData = currentDag ? (currentDag.data[nodeId] || currentDag.modules[nodeId]) : null;
      var name = nodeData ? nodeData.name : '';

      node.classList.remove('search-match', 'search-dimmed');

      if (query === '') {
        // No search, show all normally
        return;
      }

      if (name.toLowerCase().includes(query)) {
        node.classList.add('search-match');
      } else {
        node.classList.add('search-dimmed');
      }
    });
  }

  function showNodeDetails(nodeId) {
    if (!currentDag) return;

    var nodeData = currentDag.data[nodeId] || currentDag.modules[nodeId];
    if (!nodeData) return;

    var isModule = !!currentDag.modules[nodeId];
    var isOutput = (currentDag.declaredOutputs || []).indexOf(nodeId) !== -1;
    var hasIncoming = currentDag.inEdges.some(function(e) { return e[1] === nodeId; }) ||
                      currentDag.outEdges.some(function(e) { return e[1] === nodeId; });
    var isInput = !isModule && !hasIncoming;

    var role = isModule ? 'Operation' : (isInput ? 'Input' : (isOutput ? 'Output' : 'Data'));
    var stateInfo = executionStates[nodeId];

    detailsTitle.textContent = nodeData.name || 'Unknown';

    var html = '';
    html += '<div class="detail-row"><span class="detail-label">Role:</span><span class="detail-value">' + role + '</span></div>';

    if (!isModule && nodeData.cType) {
      var typeColor = getTypeColor(nodeData.cType);
      html += '<div class="detail-row"><span class="detail-label">Type:</span><span class="detail-value type-badge" style="background:' + typeColor + '; color: #fff;">' + nodeData.cType + '</span></div>';
    }

    if (isModule) {
      var inputs = Object.keys(nodeData.consumes || {});
      var outputs = Object.keys(nodeData.produces || {});
      if (inputs.length > 0) {
        html += '<div class="detail-row"><span class="detail-label">Inputs:</span><span class="detail-value">' + inputs.join(', ') + '</span></div>';
      }
      if (outputs.length > 0) {
        html += '<div class="detail-row"><span class="detail-label">Outputs:</span><span class="detail-value">' + outputs.join(', ') + '</span></div>';
      }
    }

    if (stateInfo) {
      html += '<div class="detail-row"><span class="detail-label">Status:</span><span class="detail-value">' + (stateInfo.state || 'unknown') + '</span></div>';
      if (stateInfo.valuePreview) {
        html += '<div class="detail-row"><span class="detail-label">Value:</span><span class="detail-value" style="font-family: monospace;">' + escapeHtml(stateInfo.valuePreview) + '</span></div>';
      }
    }

    // Show connected nodes
    var connectedFrom = [];
    var connectedTo = [];
    currentDag.inEdges.concat(currentDag.outEdges).forEach(function(edge) {
      if (edge[0] === nodeId) connectedTo.push(edge[1]);
      if (edge[1] === nodeId) connectedFrom.push(edge[0]);
    });

    if (connectedFrom.length > 0) {
      var fromNames = connectedFrom.map(function(id) {
        var d = currentDag.data[id] || currentDag.modules[id];
        return d ? d.name : id;
      });
      html += '<div class="detail-row"><span class="detail-label">From:</span><span class="detail-value">' + fromNames.join(', ') + '</span></div>';
    }
    if (connectedTo.length > 0) {
      var toNames = connectedTo.map(function(id) {
        var d = currentDag.data[id] || currentDag.modules[id];
        return d ? d.name : id;
      });
      html += '<div class="detail-row"><span class="detail-label">To:</span><span class="detail-value">' + toNames.join(', ') + '</span></div>';
    }

    detailsContent.innerHTML = html;
    nodeDetailsPanel.style.display = 'block';
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function zoomBy(factor) {
    var centerX = viewBox.x + viewBox.width / 2;
    var centerY = viewBox.y + viewBox.height / 2;

    var newWidth = viewBox.width * factor;
    var newHeight = viewBox.height * factor;

    // Limit zoom level
    var minZoom = originalViewBox.width / 10;
    var maxZoom = originalViewBox.width * 5;
    if (newWidth < minZoom || newWidth > maxZoom) return;

    viewBox.width = newWidth;
    viewBox.height = newHeight;
    viewBox.x = centerX - newWidth / 2;
    viewBox.y = centerY - newHeight / 2;

    updateViewBox();
    updateZoomLevel();
  }

  function fitToView() {
    viewBox.x = originalViewBox.x;
    viewBox.y = originalViewBox.y;
    viewBox.width = originalViewBox.width;
    viewBox.height = originalViewBox.height;
    updateViewBox();
    updateZoomLevel();
  }

  function updateViewBox() {
    dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);
  }

  function updateZoomLevel() {
    var zoomPercent = Math.round((originalViewBox.width / viewBox.width) * 100);
    zoomLevelEl.textContent = zoomPercent + '%';
  }

  function resetExecutionStates() {
    executionStates = {};
    if (currentDag) {
      renderDag(currentDag);
    }
  }

  function setNodeExecutionState(nodeId, state, valuePreview) {
    executionStates[nodeId] = { state: state, valuePreview: valuePreview };
    updateNodeAppearance(nodeId);
  }

  function setAllNodesState(state) {
    if (!currentDag) return;
    var allNodeIds = Object.keys(currentDag.modules || {}).concat(Object.keys(currentDag.data || {}));
    allNodeIds.forEach(function(nodeId) {
      executionStates[nodeId] = { state: state };
    });
    if (currentDag) {
      renderDag(currentDag);
    }
  }

  function updateNodeAppearance(nodeId) {
    var nodeGroup = document.querySelector('[data-node-id="' + nodeId + '"]');
    if (!nodeGroup) return;

    // Remove existing state classes
    nodeGroup.classList.remove('state-pending', 'state-running', 'state-completed', 'state-failed');

    var stateInfo = executionStates[nodeId];
    if (stateInfo && stateInfo.state) {
      nodeGroup.classList.add('state-' + stateInfo.state);

      // Update or add state icon
      var existingIcon = nodeGroup.querySelector('.state-icon');
      if (existingIcon) {
        existingIcon.remove();
      }

      var icon = '';
      if (stateInfo.state === 'completed') icon = '✓';
      else if (stateInfo.state === 'failed') icon = '✗';
      else if (stateInfo.state === 'running') icon = '⟳';

      if (icon) {
        var iconText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        iconText.setAttribute('class', 'state-icon');
        iconText.setAttribute('x', '12');
        iconText.setAttribute('y', '14');
        iconText.textContent = icon;
        nodeGroup.appendChild(iconText);
      }

      // Update value preview if available
      var existingPreview = nodeGroup.querySelector('.value-preview');
      if (existingPreview) {
        existingPreview.remove();
      }

      if (stateInfo.valuePreview && stateInfo.state === 'completed') {
        // Find the main shape to get dimensions
        var shape = nodeGroup.querySelector('rect, ellipse, polygon');
        var width = 160;
        var height = 40;
        if (shape) {
          if (shape.tagName === 'rect') {
            width = parseFloat(shape.getAttribute('width')) || 160;
            height = parseFloat(shape.getAttribute('height')) || 40;
          } else if (shape.tagName === 'ellipse') {
            width = (parseFloat(shape.getAttribute('rx')) || 80) * 2;
            height = (parseFloat(shape.getAttribute('ry')) || 20) * 2;
          } else if (shape.tagName === 'polygon') {
            // For polygon, estimate from points
            var points = shape.getAttribute('points');
            if (points) {
              var coords = points.split(' ').map(function(p) { return p.split(',').map(parseFloat); });
              var maxX = Math.max.apply(null, coords.map(function(c) { return c[0]; }));
              var maxY = Math.max.apply(null, coords.map(function(c) { return c[1]; }));
              width = maxX || 160;
              height = maxY || 40;
            }
          }
        }

        var previewText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        previewText.setAttribute('class', 'value-preview');
        previewText.setAttribute('x', width / 2);
        previewText.setAttribute('y', height - 6);
        previewText.textContent = formatValuePreview(stateInfo.valuePreview, 22);
        nodeGroup.appendChild(previewText);
      }
    }
  }

  function truncateValue(value, maxLen) {
    if (!value) return '';
    if (value.length <= maxLen) return value;
    return value.substring(0, maxLen - 3) + '...';
  }

  // Format a value preview for display - handles various data types
  function formatValuePreview(value, maxLen) {
    if (value === null || value === undefined) return 'null';
    if (value === '') return '""';

    var str = String(value);

    // Try to detect and format different types
    try {
      // Check if it's a JSON string
      if ((str.startsWith('{') || str.startsWith('[')) && (str.endsWith('}') || str.endsWith(']'))) {
        var parsed = JSON.parse(str);
        str = formatJsonPreview(parsed, maxLen);
      }
    } catch (e) {
      // Not valid JSON, use as-is
    }

    // Format boolean values
    if (str === 'true' || str === 'false') {
      return str;
    }

    // Format numbers (detect and format large numbers)
    if (!isNaN(Number(str)) && str !== '') {
      var num = Number(str);
      if (Number.isInteger(num) && Math.abs(num) >= 1000000) {
        return formatLargeNumber(num);
      }
      if (!Number.isInteger(num)) {
        // Round floats to reasonable precision
        return num.toPrecision(6).replace(/\\.?0+$/, '');
      }
      return str;
    }

    // Format strings - add quotes to show it's a string
    if (!str.startsWith('"') && !str.startsWith('[') && !str.startsWith('{')) {
      str = '"' + str + '"';
    }

    return truncateValue(str, maxLen);
  }

  // Format JSON for preview
  function formatJsonPreview(obj, maxLen) {
    if (Array.isArray(obj)) {
      if (obj.length === 0) return '[]';
      if (obj.length === 1) return '[' + formatJsonPreview(obj[0], maxLen - 2) + ']';
      return '[' + obj.length + ' items]';
    }

    if (typeof obj === 'object' && obj !== null) {
      var keys = Object.keys(obj);
      if (keys.length === 0) return '{}';
      if (keys.length === 1) {
        var key = keys[0];
        var val = formatJsonPreview(obj[key], maxLen - key.length - 4);
        var result = '{' + key + ': ' + val + '}';
        if (result.length <= maxLen) return result;
      }
      return '{' + keys.length + ' fields}';
    }

    if (typeof obj === 'string') {
      return '"' + truncateValue(obj, maxLen - 2) + '"';
    }

    return String(obj);
  }

  // Format large numbers with K/M/B suffixes
  function formatLargeNumber(num) {
    var absNum = Math.abs(num);
    var sign = num < 0 ? '-' : '';

    if (absNum >= 1e9) {
      return sign + (absNum / 1e9).toFixed(1) + 'B';
    }
    if (absNum >= 1e6) {
      return sign + (absNum / 1e6).toFixed(1) + 'M';
    }
    if (absNum >= 1e3) {
      return sign + (absNum / 1e3).toFixed(1) + 'K';
    }
    return String(num);
  }

  // Simplify node labels by stripping common prefixes and truncating
  function simplifyLabel(name, maxLen) {
    if (!name) return '';

    var simplified = name;

    // Strip common prefixes:
    // - UUID-like prefixes (e.g., "abc123_varName" -> "varName")
    // - Namespace prefixes (e.g., "module.submodule.Name" -> "Name")

    // Remove UUID-like prefix (alphanumeric followed by underscore)
    var uuidMatch = simplified.match(/^[a-f0-9]{8,}_(.+)$/i);
    if (uuidMatch) {
      simplified = uuidMatch[1];
    }

    // If name has dots (namespace), take the last part
    if (simplified.includes('.')) {
      var parts = simplified.split('.');
      simplified = parts[parts.length - 1];
    }

    // Truncate if too long
    if (simplified.length > maxLen) {
      return simplified.substring(0, maxLen - 1) + '…';
    }

    return simplified;
  }

  // Build tooltip text for a node
  function buildTooltip(node) {
    var lines = [];
    lines.push(node.name);

    if (node.role) {
      lines.push('Role: ' + node.role);
    }

    if (node.type === 'data' && node.cType) {
      lines.push('Type: ' + node.cType);
    }

    if (node.type === 'module') {
      lines.push('Operation');
    }

    return lines.join('\\n');
  }

  // Create hexagon points for output nodes
  function createHexagonPoints(width, height) {
    var inset = 12; // How much the left/right points are inset
    // Points: top-left, top-right, right-point, bottom-right, bottom-left, left-point
    var points = [
      [inset, 0],                    // top-left
      [width - inset, 0],            // top-right
      [width, height / 2],           // right point
      [width - inset, height],       // bottom-right
      [inset, height],               // bottom-left
      [0, height / 2]                // left point
    ];
    return points.map(function(p) { return p[0] + ',' + p[1]; }).join(' ');
  }

  // Get CSS color variable for a data type
  function getTypeColor(cType) {
    if (!cType) return 'var(--type-unknown)';

    var typeLower = cType.toLowerCase();

    // Check for primitive types
    if (typeLower === 'string' || typeLower === 'text') {
      return 'var(--type-string)';
    }
    if (typeLower === 'int' || typeLower === 'integer' || typeLower === 'long') {
      return 'var(--type-int)';
    }
    if (typeLower === 'float' || typeLower === 'double' || typeLower === 'decimal' || typeLower === 'number') {
      return 'var(--type-float)';
    }
    if (typeLower === 'boolean' || typeLower === 'bool') {
      return 'var(--type-boolean)';
    }

    // Check for container types
    if (typeLower.startsWith('list') || typeLower.startsWith('array') || typeLower.startsWith('seq')) {
      return 'var(--type-list)';
    }
    if (typeLower.startsWith('record') || typeLower.startsWith('struct') || typeLower.startsWith('object')) {
      return 'var(--type-record)';
    }
    if (typeLower.startsWith('option') || typeLower.startsWith('maybe') || typeLower.includes('?')) {
      return 'var(--type-optional)';
    }

    return 'var(--type-unknown)';
  }

  // Get a short type indicator (icon/emoji) for the type
  function getTypeIndicator(cType) {
    if (!cType) return '';

    var typeLower = cType.toLowerCase();

    if (typeLower === 'string' || typeLower === 'text') return 'T';
    if (typeLower === 'int' || typeLower === 'integer' || typeLower === 'long') return '#';
    if (typeLower === 'float' || typeLower === 'double' || typeLower === 'decimal') return '.#';
    if (typeLower === 'boolean' || typeLower === 'bool') return '?';
    if (typeLower.startsWith('list') || typeLower.startsWith('array')) return '[]';
    if (typeLower.startsWith('record') || typeLower.startsWith('struct')) return '{}';
    if (typeLower.startsWith('option') || typeLower.includes('?')) return '~';

    return '';
  }

  function exportAsSvg() {
    // Clone the SVG to avoid modifying the displayed one
    var svgClone = dagSvg.cloneNode(true);

    // Add explicit styles to the SVG for standalone rendering
    var styleElement = document.createElementNS('http://www.w3.org/2000/svg', 'style');
    styleElement.textContent = getSvgExportStyles();
    svgClone.insertBefore(styleElement, svgClone.firstChild);

    // Set explicit dimensions based on viewBox
    svgClone.setAttribute('width', viewBox.width);
    svgClone.setAttribute('height', viewBox.height);

    // Serialize to string
    var serializer = new XMLSerializer();
    var svgString = serializer.serializeToString(svgClone);

    // Add XML declaration
    svgString = '<?xml version="1.0" encoding="UTF-8"?>\\n' + svgString;

    // Create download link
    var blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = currentFileName.replace('.cst', '') + '-dag.svg';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function exportAsPng() {
    // Clone the SVG to avoid modifying the displayed one
    var svgClone = dagSvg.cloneNode(true);

    // Add explicit styles
    var styleElement = document.createElementNS('http://www.w3.org/2000/svg', 'style');
    styleElement.textContent = getSvgExportStyles();
    svgClone.insertBefore(styleElement, svgClone.firstChild);

    // Set explicit dimensions (2x for high resolution)
    var scale = 2;
    var width = viewBox.width * scale;
    var height = viewBox.height * scale;
    svgClone.setAttribute('width', width);
    svgClone.setAttribute('height', height);

    // Serialize SVG
    var serializer = new XMLSerializer();
    var svgString = serializer.serializeToString(svgClone);

    // Create image from SVG
    var img = new Image();
    img.onload = function() {
      // Create canvas
      var canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      var ctx = canvas.getContext('2d');

      // Fill with editor background color (or white as fallback)
      var bgColor = getComputedStyle(document.body).getPropertyValue('--vscode-editor-background') || '#1e1e1e';
      ctx.fillStyle = bgColor;
      ctx.fillRect(0, 0, width, height);

      // Draw SVG
      ctx.drawImage(img, 0, 0, width, height);

      // Create download link
      var link = document.createElement('a');
      link.href = canvas.toDataURL('image/png');
      link.download = currentFileName.replace('.cst', '') + '-dag.png';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    img.onerror = function() {
      console.error('Failed to load SVG for PNG export');
    };

    // Load SVG as data URL
    var svgBlob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
    img.src = URL.createObjectURL(svgBlob);
  }

  function getSvgExportStyles() {
    // Get computed colors for export
    var styles = getComputedStyle(document.documentElement);
    var editorBg = styles.getPropertyValue('--vscode-editor-background') || '#1e1e1e';
    var foreground = styles.getPropertyValue('--vscode-foreground') || '#cccccc';
    var descForeground = styles.getPropertyValue('--vscode-descriptionForeground') || '#888888';
    var dataBorder = styles.getPropertyValue('--node-data-border') || '#3794ff';
    var moduleBorder = styles.getPropertyValue('--node-module-border') || '#d18616';
    var inputBorder = styles.getPropertyValue('--vscode-charts-green') || '#3fb950';
    var outputBorder = styles.getPropertyValue('--vscode-charts-purple') || '#a371f7';
    var edgeColor = styles.getPropertyValue('--edge-color') || '#cca700';
    var statePending = styles.getPropertyValue('--state-pending') || '#6e7681';
    var stateRunning = styles.getPropertyValue('--state-running') || '#3794ff';
    var stateCompleted = styles.getPropertyValue('--state-completed') || '#3fb950';
    var stateFailed = styles.getPropertyValue('--state-failed') || '#f85149';

    // Type colors
    var typeString = styles.getPropertyValue('--type-string') || '#98c379';
    var typeInt = styles.getPropertyValue('--type-int') || '#61afef';
    var typeFloat = styles.getPropertyValue('--type-float') || '#56b6c2';
    var typeBoolean = styles.getPropertyValue('--type-boolean') || '#c678dd';
    var typeList = styles.getPropertyValue('--type-list') || '#e5c07b';
    var typeRecord = styles.getPropertyValue('--type-record') || '#d19a66';

    return '\\n' +
      '.dag-node rect, .dag-node ellipse, .dag-node polygon { fill: ' + editorBg + '; stroke-width: 2; }\\n' +
      '.dag-node-input ellipse { stroke: ' + inputBorder + '; }\\n' +
      '.dag-node-output polygon { stroke: ' + outputBorder + '; }\\n' +
      '.dag-node-data rect { stroke: ' + dataBorder + '; rx: 8; ry: 8; }\\n' +
      '.dag-node-operation rect.node-body { stroke: ' + moduleBorder + '; }\\n' +
      '.dag-node-operation rect.node-header { fill: ' + moduleBorder + '; stroke: ' + moduleBorder + '; }\\n' +
      '.dag-node text { fill: ' + foreground + '; font-family: monospace; font-size: 12px; text-anchor: middle; dominant-baseline: middle; }\\n' +
      '.dag-node .node-type { font-size: 10px; font-weight: 500; }\\n' +
      '.dag-node .type-badge { font-size: 9px; font-weight: 600; fill: ' + editorBg + '; }\\n' +
      '.dag-node .type-badge-bg { rx: 3; ry: 3; }\\n' +
      '.dag-edge path { fill: none; stroke: ' + edgeColor + '; stroke-width: 1.5; opacity: 0.8; }\\n' +
      '.dag-edge polygon { fill: ' + edgeColor + '; opacity: 0.8; }\\n' +
      '.dag-node.state-pending rect, .dag-node.state-pending ellipse, .dag-node.state-pending polygon { stroke: ' + statePending + '; opacity: 0.6; }\\n' +
      '.dag-node.state-running rect, .dag-node.state-running ellipse, .dag-node.state-running polygon { stroke: ' + stateRunning + '; stroke-width: 3; }\\n' +
      '.dag-node.state-completed rect, .dag-node.state-completed ellipse, .dag-node.state-completed polygon { stroke: ' + stateCompleted + '; }\\n' +
      '.dag-node.state-failed rect, .dag-node.state-failed ellipse, .dag-node.state-failed polygon { stroke: ' + stateFailed + '; }\\n' +
      '.dag-node .state-icon { font-size: 14px; }\\n' +
      '.dag-node.state-completed .state-icon { fill: ' + stateCompleted + '; }\\n' +
      '.dag-node.state-failed .state-icon { fill: ' + stateFailed + '; }\\n' +
      '.value-preview { font-size: 9px; fill: ' + descForeground + '; opacity: 0.8; }\\n';
  }

  var currentVizDag = null; // For new DagVizIR format
  var useNewVisualization = false; // Track which format is being used

  function setLayoutDirection(direction) {
    layoutDirection = direction;
    tbBtn.classList.toggle('active', direction === 'TB');
    lrBtn.classList.toggle('active', direction === 'LR');
    // Send to extension to re-fetch with new direction from server
    vscode.postMessage({ command: 'setLayoutDirection', direction: direction });
  }

  window.addEventListener('message', function(event) {
    var message = event.data;

    if (message.type === 'loading') {
      loadingContainer.style.display = message.loading ? 'flex' : 'none';
      errorContainer.style.display = 'none';
    } else if (message.type === 'dagVizData') {
      // New visualization format with server-provided layout
      loadingContainer.style.display = 'none';
      errorContainer.style.display = 'none';
      currentFileName = message.fileName || 'script.cst';
      fileNameEl.textContent = currentFileName;
      currentVizDag = message.dag;
      currentDag = null; // Clear old format
      useNewVisualization = true;
      // Update layout direction buttons based on server response
      var serverDirection = message.dag.metadata && message.dag.metadata.layoutDirection || 'TB';
      tbBtn.classList.toggle('active', serverDirection === 'TB');
      lrBtn.classList.toggle('active', serverDirection === 'LR');
      layoutDirection = serverDirection;
      renderVizDag(message.dag);
    } else if (message.type === 'dagData') {
      // Legacy format with client-side layout
      loadingContainer.style.display = 'none';
      errorContainer.style.display = 'none';
      currentFileName = message.fileName || 'script.cst';
      fileNameEl.textContent = currentFileName;
      currentDag = message.dag;
      currentVizDag = null; // Clear new format
      useNewVisualization = false;
      renderDag(message.dag);
    } else if (message.type === 'error') {
      loadingContainer.style.display = 'none';
      errorContainer.style.display = 'block';
      errorMessage.textContent = message.message;
    } else if (message.type === 'setLayoutDirection') {
      // Apply saved layout direction preference
      layoutDirection = message.direction || 'TB';
      tbBtn.classList.toggle('active', layoutDirection === 'TB');
      lrBtn.classList.toggle('active', layoutDirection === 'LR');
    } else if (message.type === 'executionUpdate') {
      // Single node execution state update
      setNodeExecutionState(message.nodeId, message.state, message.valuePreview);
    } else if (message.type === 'executionBatchUpdate') {
      // Batch update for multiple nodes
      if (message.updates && Array.isArray(message.updates)) {
        message.updates.forEach(function(update) {
          executionStates[update.nodeId] = { state: update.state, valuePreview: update.valuePreview };
        });
        if (currentDag) {
          renderDag(currentDag);
        }
      }
    } else if (message.type === 'executionStart') {
      // Mark all nodes as pending when execution starts
      setAllNodesState('pending');
    } else if (message.type === 'executionComplete') {
      // Mark all nodes based on success/failure
      if (message.success) {
        setAllNodesState('completed');
      } else {
        // Mark non-completed nodes as failed
        Object.keys(executionStates).forEach(function(nodeId) {
          if (executionStates[nodeId].state !== 'completed') {
            executionStates[nodeId].state = 'failed';
          }
        });
        if (currentDag) {
          renderDag(currentDag);
        }
      }
    }
  });

  function renderDag(dag) {
    var layout = computeLayout(dag);
    renderSvg(layout, dag);
    setupPanZoom();
  }

  // ========== New DagVizIR Rendering (server-provided layout) ==========

  // Node dimensions for VizIR rendering
  var vizNodeWidth = 180;
  var vizNodeHeight = 60;
  var vizInputHeight = 44;
  var vizOutputHeight = 48;

  // Map NodeKind to display properties
  function getNodeKindInfo(kind) {
    var kindMap = {
      'Input':       { role: 'input',     color: '#22c55e', icon: '▶' },
      'Output':      { role: 'output',    color: '#3b82f6', icon: '▶' },
      'Operation':   { role: 'operation', color: '#6b7280', icon: '⚙' },
      'Literal':     { role: 'literal',   color: '#14b8a6', icon: '#' },
      'Merge':       { role: 'merge',     color: '#a855f7', icon: '⊕' },
      'Project':     { role: 'project',   color: '#f97316', icon: '▣' },
      'FieldAccess': { role: 'field',     color: '#f97316', icon: '.' },
      'Conditional': { role: 'cond',      color: '#6366f1', icon: '?' },
      'Guard':       { role: 'guard',     color: '#eab308', icon: '?' },
      'Branch':      { role: 'branch',    color: '#6366f1', icon: '⑂' },
      'Coalesce':    { role: 'coalesce',  color: '#a855f7', icon: '|' },
      'HigherOrder': { role: 'higher',    color: '#ec4899', icon: 'λ' },
      'ListLiteral': { role: 'list',      color: '#14b8a6', icon: '[]' },
      'BooleanOp':   { role: 'bool',      color: '#c678dd', icon: '∧' },
      'StringInterp':{ role: 'string',    color: '#98c379', icon: '$' }
    };
    return kindMap[kind] || { role: 'data', color: '#64748b', icon: '?' };
  }

  function renderVizDag(dag) {
    dagSvg.innerHTML = '';

    // Use metadata bounds if available, otherwise compute from node positions
    var bounds = dag.metadata && dag.metadata.bounds;
    var padding = 40;

    if (!bounds) {
      // Compute bounds from node positions
      var minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      dag.nodes.forEach(function(node) {
        if (node.position) {
          minX = Math.min(minX, node.position.x - vizNodeWidth / 2);
          maxX = Math.max(maxX, node.position.x + vizNodeWidth / 2);
          minY = Math.min(minY, node.position.y - vizNodeHeight / 2);
          maxY = Math.max(maxY, node.position.y + vizNodeHeight / 2);
        }
      });
      if (minX === Infinity) { minX = 0; maxX = 800; minY = 0; maxY = 600; }
      bounds = { minX: minX - padding, maxX: maxX + padding, minY: minY - padding, maxY: maxY + padding };
    }

    var width = bounds.maxX - bounds.minX;
    var height = bounds.maxY - bounds.minY;

    viewBox = { x: bounds.minX, y: bounds.minY, width: width, height: height };
    originalViewBox = { x: bounds.minX, y: bounds.minY, width: width, height: height };
    dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);
    updateZoomLevel();

    // Create defs for arrow markers
    var defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');

    // Standard arrowhead
    var marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', 'arrowhead');
    marker.setAttribute('markerWidth', '10');
    marker.setAttribute('markerHeight', '7');
    marker.setAttribute('refX', '9');
    marker.setAttribute('refY', '3.5');
    marker.setAttribute('orient', 'auto');
    var polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
    polygon.setAttribute('points', '0 0, 10 3.5, 0 7');
    marker.appendChild(polygon);
    defs.appendChild(marker);
    dagSvg.appendChild(defs);

    // Build node lookup for edge rendering
    var nodeById = {};
    dag.nodes.forEach(function(node) {
      nodeById[node.id] = node;
    });

    // Render edges first (behind nodes)
    var edgesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    edgesGroup.setAttribute('class', 'edges-group');

    dag.edges.forEach(function(edge) {
      var fromNode = nodeById[edge.source];
      var toNode = nodeById[edge.target];
      if (!fromNode || !toNode || !fromNode.position || !toNode.position) return;

      var edgeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      var edgeClass = 'dag-edge dag-edge-' + (edge.kind || 'Data').toLowerCase();
      edgeGroup.setAttribute('class', edgeClass);
      edgeGroup.setAttribute('data-from', edge.source);
      edgeGroup.setAttribute('data-to', edge.target);

      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');

      // Calculate edge path
      var dir = dag.metadata && dag.metadata.layoutDirection || 'TB';
      var d;
      if (dir === 'TB') {
        var startY = fromNode.position.y + vizNodeHeight / 2;
        var endY = toNode.position.y - vizNodeHeight / 2;
        var midY = (startY + endY) / 2;
        d = 'M ' + fromNode.position.x + ' ' + startY +
            ' C ' + fromNode.position.x + ' ' + midY +
            ', ' + toNode.position.x + ' ' + midY +
            ', ' + toNode.position.x + ' ' + endY;
      } else {
        var startX = fromNode.position.x + vizNodeWidth / 2;
        var endX = toNode.position.x - vizNodeWidth / 2;
        var midX = (startX + endX) / 2;
        d = 'M ' + startX + ' ' + fromNode.position.y +
            ' C ' + midX + ' ' + fromNode.position.y +
            ', ' + midX + ' ' + toNode.position.y +
            ', ' + endX + ' ' + toNode.position.y;
      }

      path.setAttribute('d', d);
      path.setAttribute('marker-end', 'url(#arrowhead)');

      // Edge styling based on kind
      if (edge.kind === 'Optional') {
        path.setAttribute('stroke-dasharray', '5,5');
      }

      edgeGroup.appendChild(path);

      // Edge label if present
      if (edge.label) {
        var labelText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        labelText.setAttribute('class', 'edge-label');
        var labelX = (fromNode.position.x + toNode.position.x) / 2;
        var labelY = (fromNode.position.y + toNode.position.y) / 2 - 8;
        labelText.setAttribute('x', labelX);
        labelText.setAttribute('y', labelY);
        labelText.textContent = edge.label;
        edgeGroup.appendChild(labelText);
      }

      // Hover highlighting
      edgeGroup.addEventListener('mouseenter', function() {
        highlightConnection(edge.source, edge.target, true);
      });
      edgeGroup.addEventListener('mouseleave', function() {
        highlightConnection(edge.source, edge.target, false);
      });

      edgesGroup.appendChild(edgeGroup);
    });

    dagSvg.appendChild(edgesGroup);

    // Render nodes
    var nodesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    nodesGroup.setAttribute('class', 'nodes-group');

    dag.nodes.forEach(function(node) {
      if (!node.position) return;

      var kindInfo = getNodeKindInfo(node.kind);
      var nodeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');

      // Determine execution state class
      var stateClass = '';
      if (node.executionState) {
        stateClass = ' state-' + node.executionState.status.toLowerCase();
      } else if (executionStates[node.id]) {
        stateClass = ' state-' + executionStates[node.id].state;
      }

      var nodeClass = 'dag-node dag-node-' + kindInfo.role + stateClass;
      nodeGroup.setAttribute('class', nodeClass);
      nodeGroup.setAttribute('data-node-id', node.id);

      var nodeHeight = node.kind === 'Input' ? vizInputHeight :
                       node.kind === 'Output' ? vizOutputHeight : vizNodeHeight;

      nodeGroup.setAttribute('transform', 'translate(' + (node.position.x - vizNodeWidth/2) + ',' + (node.position.y - nodeHeight/2) + ')');

      // Create shape based on node kind
      if (node.kind === 'Input') {
        // Input: pill shape (rounded left edge)
        var inputPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        var r = nodeHeight / 2;
        var pw = vizNodeWidth;
        var ph = nodeHeight;
        // Rounded left edge, straight right
        inputPath.setAttribute('d',
          'M ' + r + ' 0 ' +
          'L ' + pw + ' 0 ' +
          'L ' + pw + ' ' + ph + ' ' +
          'L ' + r + ' ' + ph + ' ' +
          'A ' + r + ' ' + r + ' 0 0 1 ' + r + ' 0 Z'
        );
        inputPath.setAttribute('style', 'stroke: ' + kindInfo.color);
        nodeGroup.appendChild(inputPath);
      } else if (node.kind === 'Output') {
        // Output: pill shape (rounded right edge)
        var outputPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        var r = nodeHeight / 2;
        var pw = vizNodeWidth;
        var ph = nodeHeight;
        // Straight left, rounded right edge
        outputPath.setAttribute('d',
          'M 0 0 ' +
          'L ' + (pw - r) + ' 0 ' +
          'A ' + r + ' ' + r + ' 0 0 1 ' + (pw - r) + ' ' + ph + ' ' +
          'L 0 ' + ph + ' Z'
        );
        outputPath.setAttribute('style', 'stroke: ' + kindInfo.color);
        nodeGroup.appendChild(outputPath);
      } else if (node.kind === 'Operation') {
        // Operation: rectangle with header bar
        var headerHeight = 8;
        var headerRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        headerRect.setAttribute('class', 'node-header');
        headerRect.setAttribute('width', vizNodeWidth);
        headerRect.setAttribute('height', headerHeight);
        headerRect.setAttribute('rx', '4');
        headerRect.setAttribute('ry', '4');
        headerRect.setAttribute('fill', kindInfo.color);
        headerRect.setAttribute('stroke', kindInfo.color);
        nodeGroup.appendChild(headerRect);

        var bodyRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        bodyRect.setAttribute('class', 'node-body');
        bodyRect.setAttribute('y', headerHeight - 2);
        bodyRect.setAttribute('width', vizNodeWidth);
        bodyRect.setAttribute('height', nodeHeight - headerHeight + 2);
        bodyRect.setAttribute('style', 'stroke: ' + kindInfo.color);
        nodeGroup.appendChild(bodyRect);
      } else {
        // Default: rounded rectangle
        var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('width', vizNodeWidth);
        rect.setAttribute('height', nodeHeight);
        rect.setAttribute('rx', '8');
        rect.setAttribute('ry', '8');
        rect.setAttribute('style', 'stroke: ' + kindInfo.color);
        nodeGroup.appendChild(rect);
      }

      // Add tooltip
      var titleEl = document.createElementNS('http://www.w3.org/2000/svg', 'title');
      titleEl.textContent = node.label + '\\n' + node.kind + '\\nType: ' + (node.typeSignature || 'unknown');
      nodeGroup.appendChild(titleEl);

      // Node icon (top-left)
      var iconText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      iconText.setAttribute('class', 'node-icon');
      iconText.setAttribute('x', '8');
      iconText.setAttribute('y', '16');
      iconText.setAttribute('fill', kindInfo.color);
      iconText.setAttribute('font-size', '12');
      iconText.setAttribute('text-anchor', 'start');
      iconText.textContent = kindInfo.icon;
      nodeGroup.appendChild(iconText);

      // Node label
      var displayName = simplifyLabel(node.label, 20);
      var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', vizNodeWidth / 2);
      text.setAttribute('y', nodeHeight / 2 - 4);
      text.textContent = displayName;
      nodeGroup.appendChild(text);

      // Type signature (below label, smaller)
      if (node.typeSignature) {
        var typeText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        typeText.setAttribute('class', 'node-type');
        typeText.setAttribute('x', vizNodeWidth / 2);
        typeText.setAttribute('y', nodeHeight / 2 + 12);
        // Truncate long type signatures
        var displayType = node.typeSignature.length > 25 ?
          node.typeSignature.substring(0, 22) + '...' : node.typeSignature;
        typeText.textContent = displayType;
        nodeGroup.appendChild(typeText);
      }

      // Execution state indicator
      var execState = node.executionState || executionStates[node.id];
      if (execState) {
        var icon = '';
        if (execState.status === 'Completed' || execState.state === 'completed') icon = '✓';
        else if (execState.status === 'Failed' || execState.state === 'failed') icon = '✗';
        else if (execState.status === 'Running' || execState.state === 'running') icon = '⟳';

        if (icon) {
          var stateIcon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          stateIcon.setAttribute('class', 'state-icon');
          stateIcon.setAttribute('x', vizNodeWidth - 14);
          stateIcon.setAttribute('y', '14');
          stateIcon.textContent = icon;
          nodeGroup.appendChild(stateIcon);
        }

        // Value preview for completed nodes
        var value = execState.value || execState.valuePreview;
        if (value && (execState.status === 'Completed' || execState.state === 'completed')) {
          var previewText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          previewText.setAttribute('class', 'value-preview');
          previewText.setAttribute('x', vizNodeWidth / 2);
          previewText.setAttribute('y', nodeHeight - 6);
          previewText.textContent = formatValuePreview(value, 22);
          nodeGroup.appendChild(previewText);
        }
      }

      // Click handler
      nodeGroup.onclick = function() {
        showVizNodeDetails(node);
        vscode.postMessage({ command: 'nodeClick', nodeId: node.id, nodeKind: node.kind });
      };

      // Hover highlighting
      nodeGroup.addEventListener('mouseenter', function() {
        highlightNodeConnections(node.id, true);
      });
      nodeGroup.addEventListener('mouseleave', function() {
        highlightNodeConnections(node.id, false);
      });

      nodesGroup.appendChild(nodeGroup);
    });

    dagSvg.appendChild(nodesGroup);
    setupPanZoom();
  }

  function showVizNodeDetails(node) {
    detailsTitle.textContent = node.label || 'Unknown';

    var html = '';
    html += '<div class="detail-row"><span class="detail-label">Kind:</span><span class="detail-value">' + node.kind + '</span></div>';

    if (node.typeSignature) {
      html += '<div class="detail-row"><span class="detail-label">Type:</span><span class="detail-value" style="font-family: monospace; font-size: 10px;">' + escapeHtml(node.typeSignature) + '</span></div>';
    }

    var execState = node.executionState || executionStates[node.id];
    if (execState) {
      var status = execState.status || execState.state || 'unknown';
      html += '<div class="detail-row"><span class="detail-label">Status:</span><span class="detail-value">' + status + '</span></div>';

      if (execState.durationMs) {
        html += '<div class="detail-row"><span class="detail-label">Duration:</span><span class="detail-value">' + execState.durationMs + 'ms</span></div>';
      }

      var value = execState.value || execState.valuePreview;
      if (value) {
        var valueStr = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
        if (valueStr.length > 200) valueStr = valueStr.substring(0, 197) + '...';
        html += '<div class="detail-row"><span class="detail-label">Value:</span><span class="detail-value" style="font-family: monospace; white-space: pre-wrap;">' + escapeHtml(valueStr) + '</span></div>';
      }

      if (execState.error) {
        html += '<div class="detail-row"><span class="detail-label">Error:</span><span class="detail-value" style="color: var(--state-failed);">' + escapeHtml(execState.error) + '</span></div>';
      }
    }

    // Find connected nodes from edges
    if (currentVizDag) {
      var connectedFrom = [];
      var connectedTo = [];
      currentVizDag.edges.forEach(function(edge) {
        if (edge.source === node.id) {
          var targetNode = currentVizDag.nodes.find(function(n) { return n.id === edge.target; });
          if (targetNode) connectedTo.push(targetNode.label);
        }
        if (edge.target === node.id) {
          var sourceNode = currentVizDag.nodes.find(function(n) { return n.id === edge.source; });
          if (sourceNode) connectedFrom.push(sourceNode.label);
        }
      });

      if (connectedFrom.length > 0) {
        html += '<div class="detail-row"><span class="detail-label">From:</span><span class="detail-value">' + connectedFrom.join(', ') + '</span></div>';
      }
      if (connectedTo.length > 0) {
        html += '<div class="detail-row"><span class="detail-label">To:</span><span class="detail-value">' + connectedTo.join(', ') + '</span></div>';
      }
    }

    detailsContent.innerHTML = html;
    nodeDetailsPanel.style.display = 'block';
  }

  // ========== End DagVizIR Rendering ==========

  function computeLayout(dag) {
    var nodes = {};
    var nodeWidth = 160;
    var moduleHeight = 60;
    var dataHeight = 40;
    var inputHeight = 36;  // Smaller for pill shape
    var outputHeight = 44; // Slightly taller for hexagon

    // First pass: identify which data nodes have incoming edges
    var dataNodesWithIncoming = new Set();
    dag.inEdges.forEach(function(edge) {
      dataNodesWithIncoming.add(edge[1]);
    });
    dag.outEdges.forEach(function(edge) {
      dataNodesWithIncoming.add(edge[1]);
    });

    // Create a set of declared outputs for quick lookup
    var declaredOutputsSet = new Set(dag.declaredOutputs || []);

    // Create nodes for modules
    Object.keys(dag.modules).forEach(function(uuid) {
      var spec = dag.modules[uuid];
      nodes[uuid] = {
        id: uuid,
        type: 'module',
        role: 'operation',
        name: spec.name,
        width: nodeWidth,
        height: moduleHeight,
        consumes: spec.consumes,
        produces: spec.produces
      };
    });

    // Create nodes for data with role classification
    Object.keys(dag.data).forEach(function(uuid) {
      var spec = dag.data[uuid];
      var role = 'intermediate'; // default

      // Check if this is an input (no incoming edges)
      if (!dataNodesWithIncoming.has(uuid)) {
        role = 'input';
      }
      // Check if this is a declared output
      else if (declaredOutputsSet.has(uuid)) {
        role = 'output';
      }

      var height = dataHeight;
      if (role === 'input') height = inputHeight;
      if (role === 'output') height = outputHeight;

      nodes[uuid] = {
        id: uuid,
        type: 'data',
        role: role,
        name: spec.name,
        cType: spec.cType,
        width: nodeWidth,
        height: height
      };
    });

    // Build adjacency for topological sort
    var inDegree = {};
    var outgoing = {};

    Object.keys(nodes).forEach(function(id) {
      inDegree[id] = 0;
      outgoing[id] = [];
    });

    dag.inEdges.forEach(function(edge) {
      var from = edge[0], to = edge[1];
      if (nodes[from] && nodes[to]) {
        outgoing[from].push(to);
        inDegree[to]++;
      }
    });

    dag.outEdges.forEach(function(edge) {
      var from = edge[0], to = edge[1];
      if (nodes[from] && nodes[to]) {
        outgoing[from].push(to);
        inDegree[to]++;
      }
    });

    // Topological sort to determine layers
    var layers = [];
    var remaining = Object.keys(nodes).slice();

    while (remaining.length > 0) {
      var layer = remaining.filter(function(id) {
        return inDegree[id] === 0;
      });

      if (layer.length === 0) {
        // Cycle detected, just add remaining
        layer = remaining.slice();
        remaining = [];
      } else {
        remaining = remaining.filter(function(id) {
          return inDegree[id] > 0;
        });

        layer.forEach(function(id) {
          outgoing[id].forEach(function(targetId) {
            inDegree[targetId]--;
          });
        });
      }

      layers.push(layer);
    }

    // Position nodes based on layout direction
    var padding = 40;
    var layerGap = 100;
    var nodeGap = 30;

    if (layoutDirection === 'TB') {
      // Top-to-bottom: layers are horizontal rows
      var y = padding;

      layers.forEach(function(layer) {
        var x = padding;

        layer.forEach(function(id) {
          var node = nodes[id];
          node.x = x + node.width / 2;
          node.y = y + node.height / 2;
          x += node.width + nodeGap;
        });

        var maxHeight = Math.max.apply(null, layer.map(function(id) {
          return nodes[id].height;
        }));
        y += maxHeight + layerGap;
      });
    } else {
      // Left-to-right: layers are vertical columns
      var x = padding;

      layers.forEach(function(layer) {
        var y = padding;

        layer.forEach(function(id) {
          var node = nodes[id];
          node.x = x + node.width / 2;
          node.y = y + node.height / 2;
          y += node.height + nodeGap;
        });

        var maxWidth = Math.max.apply(null, layer.map(function(id) {
          return nodes[id].width;
        }));
        x += maxWidth + layerGap;
      });
    }

    // Calculate bounds
    var allNodes = Object.values(nodes);
    var minX = Math.min.apply(null, allNodes.map(function(n) { return n.x - n.width/2; })) - padding;
    var maxX = Math.max.apply(null, allNodes.map(function(n) { return n.x + n.width/2; })) + padding;
    var minY = Math.min.apply(null, allNodes.map(function(n) { return n.y - n.height/2; })) - padding;
    var maxY = Math.max.apply(null, allNodes.map(function(n) { return n.y + n.height/2; })) + padding;

    return {
      nodes: nodes,
      edges: dag.inEdges.concat(dag.outEdges),
      bounds: { minX: minX, maxX: maxX, minY: minY, maxY: maxY }
    };
  }

  function renderSvg(layout, dag) {
    dagSvg.innerHTML = '';

    var bounds = layout.bounds;
    var width = bounds.maxX - bounds.minX;
    var height = bounds.maxY - bounds.minY;

    viewBox = { x: bounds.minX, y: bounds.minY, width: width, height: height };
    originalViewBox = { x: bounds.minX, y: bounds.minY, width: width, height: height };
    dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);
    updateZoomLevel();

    // Create defs for arrow marker
    var defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    var marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', 'arrowhead');
    marker.setAttribute('markerWidth', '10');
    marker.setAttribute('markerHeight', '7');
    marker.setAttribute('refX', '9');
    marker.setAttribute('refY', '3.5');
    marker.setAttribute('orient', 'auto');
    var polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
    polygon.setAttribute('points', '0 0, 10 3.5, 0 7');
    marker.appendChild(polygon);
    defs.appendChild(marker);
    dagSvg.appendChild(defs);

    // Render edges first (behind nodes)
    var edgesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    edgesGroup.setAttribute('class', 'edges-group');

    layout.edges.forEach(function(edge) {
      var fromId = edge[0];
      var toId = edge[1];
      var fromNode = layout.nodes[fromId];
      var toNode = layout.nodes[toId];
      if (!fromNode || !toNode) return;

      var edgeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      edgeGroup.setAttribute('class', 'dag-edge');
      // Store connected node IDs for hover highlighting
      edgeGroup.setAttribute('data-from', fromId);
      edgeGroup.setAttribute('data-to', toId);

      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');

      // Calculate edge path with curve based on layout direction
      var d;
      if (layoutDirection === 'TB') {
        // Top-to-bottom: edges go vertically
        var startY = fromNode.y + fromNode.height / 2;
        var endY = toNode.y - toNode.height / 2;
        var midY = (startY + endY) / 2;

        d = 'M ' + fromNode.x + ' ' + startY +
            ' C ' + fromNode.x + ' ' + midY +
            ', ' + toNode.x + ' ' + midY +
            ', ' + toNode.x + ' ' + endY;
      } else {
        // Left-to-right: edges go horizontally
        var startX = fromNode.x + fromNode.width / 2;
        var endX = toNode.x - toNode.width / 2;
        var midX = (startX + endX) / 2;

        d = 'M ' + startX + ' ' + fromNode.y +
            ' C ' + midX + ' ' + fromNode.y +
            ', ' + midX + ' ' + toNode.y +
            ', ' + endX + ' ' + toNode.y;
      }

      path.setAttribute('d', d);
      path.setAttribute('marker-end', 'url(#arrowhead)');
      edgeGroup.appendChild(path);

      // Add hover event listeners for edge highlighting
      edgeGroup.addEventListener('mouseenter', function() {
        highlightConnection(fromId, toId, true);
      });
      edgeGroup.addEventListener('mouseleave', function() {
        highlightConnection(fromId, toId, false);
      });

      edgesGroup.appendChild(edgeGroup);
    });

    dagSvg.appendChild(edgesGroup);

    // Render nodes
    var nodesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    nodesGroup.setAttribute('class', 'nodes-group');

    Object.keys(layout.nodes).forEach(function(id) {
      var node = layout.nodes[id];
      var nodeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      var stateClass = executionStates[id] ? ' state-' + executionStates[id].state : '';

      // Determine CSS class based on role
      var roleClass = node.role || node.type;
      nodeGroup.setAttribute('class', 'dag-node dag-node-' + roleClass + stateClass);
      nodeGroup.setAttribute('data-node-id', id);
      nodeGroup.setAttribute('transform', 'translate(' + (node.x - node.width/2) + ',' + (node.y - node.height/2) + ')');

      // Create shape based on node role
      if (node.role === 'input') {
        // Input nodes: pill/ellipse shape
        var ellipse = document.createElementNS('http://www.w3.org/2000/svg', 'ellipse');
        ellipse.setAttribute('cx', node.width / 2);
        ellipse.setAttribute('cy', node.height / 2);
        ellipse.setAttribute('rx', node.width / 2);
        ellipse.setAttribute('ry', node.height / 2);
        nodeGroup.appendChild(ellipse);
      } else if (node.role === 'output') {
        // Output nodes: hexagon shape
        var hexPoints = createHexagonPoints(node.width, node.height);
        var hexagon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
        hexagon.setAttribute('points', hexPoints);
        nodeGroup.appendChild(hexagon);
      } else if (node.type === 'module') {
        // Module nodes: rectangle with colored header bar
        var headerHeight = 8;
        var headerRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        headerRect.setAttribute('class', 'node-header');
        headerRect.setAttribute('width', node.width);
        headerRect.setAttribute('height', headerHeight);
        headerRect.setAttribute('rx', '4');
        headerRect.setAttribute('ry', '4');
        nodeGroup.appendChild(headerRect);

        var bodyRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        bodyRect.setAttribute('class', 'node-body');
        bodyRect.setAttribute('y', headerHeight - 2);
        bodyRect.setAttribute('width', node.width);
        bodyRect.setAttribute('height', node.height - headerHeight + 2);
        nodeGroup.appendChild(bodyRect);
      } else {
        // Intermediate data nodes: rounded rectangle
        var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('width', node.width);
        rect.setAttribute('height', node.height);
        nodeGroup.appendChild(rect);
      }

      // Add tooltip (SVG title element)
      var titleEl = document.createElementNS('http://www.w3.org/2000/svg', 'title');
      titleEl.textContent = buildTooltip(node);
      nodeGroup.appendChild(titleEl);

      // Add node label text (simplified)
      var maxLabelLen = 18; // Max characters for label
      var displayName = simplifyLabel(node.name, maxLabelLen);

      var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', node.width / 2);
      var textY = node.height / 2;
      if (node.type === 'module') textY = node.height / 2 + 4; // Adjust for header
      text.setAttribute('y', textY);
      text.textContent = displayName;
      nodeGroup.appendChild(text);

      // Add type info for data nodes with color coding
      if (node.type === 'data' && node.cType) {
        var typeColor = getTypeColor(node.cType);
        var typeIndicator = getTypeIndicator(node.cType);

        // Add type badge (small colored indicator)
        if (typeIndicator) {
          var badgeWidth = typeIndicator.length * 7 + 6;
          var badgeX = node.width - badgeWidth - 4;
          var badgeY = 4;

          var badgeBg = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
          badgeBg.setAttribute('class', 'type-badge-bg');
          badgeBg.setAttribute('x', badgeX);
          badgeBg.setAttribute('y', badgeY);
          badgeBg.setAttribute('width', badgeWidth);
          badgeBg.setAttribute('height', 14);
          badgeBg.setAttribute('fill', typeColor);
          nodeGroup.appendChild(badgeBg);

          var badgeText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          badgeText.setAttribute('class', 'type-badge');
          badgeText.setAttribute('x', badgeX + badgeWidth / 2);
          badgeText.setAttribute('y', badgeY + 10);
          badgeText.textContent = typeIndicator;
          nodeGroup.appendChild(badgeText);
        }

        // Add type text with color
        var typeText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        typeText.setAttribute('class', 'node-type');
        typeText.setAttribute('x', node.width / 2);
        typeText.setAttribute('y', textY + 12);
        typeText.setAttribute('fill', typeColor);
        // Simplify type name if it has generics or is too long
        var displayType = simplifyLabel(node.cType, 20);
        typeText.textContent = displayType;
        nodeGroup.appendChild(typeText);
      }

      // Add execution state icon if present
      var stateInfo = executionStates[id];
      if (stateInfo && stateInfo.state) {
        var icon = '';
        if (stateInfo.state === 'completed') icon = '✓';
        else if (stateInfo.state === 'failed') icon = '✗';
        else if (stateInfo.state === 'running') icon = '⟳';

        if (icon) {
          var iconText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          iconText.setAttribute('class', 'state-icon');
          iconText.setAttribute('x', '12');
          iconText.setAttribute('y', '14');
          iconText.textContent = icon;
          nodeGroup.appendChild(iconText);
        }

        // Add value preview if available
        if (stateInfo.valuePreview && stateInfo.state === 'completed') {
          var previewText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          previewText.setAttribute('class', 'value-preview');
          previewText.setAttribute('x', node.width / 2);
          previewText.setAttribute('y', node.height - 6);
          previewText.textContent = formatValuePreview(stateInfo.valuePreview, 22);
          nodeGroup.appendChild(previewText);
        }
      }

      nodeGroup.onclick = function() {
        showNodeDetails(id);
        vscode.postMessage({ command: 'nodeClick', nodeId: id, nodeType: node.type, nodeRole: node.role });
      };

      // Add hover event listeners for highlighting connected edges
      nodeGroup.addEventListener('mouseenter', function() {
        highlightNodeConnections(id, true);
      });
      nodeGroup.addEventListener('mouseleave', function() {
        highlightNodeConnections(id, false);
      });

      nodesGroup.appendChild(nodeGroup);
    });

    dagSvg.appendChild(nodesGroup);
  }

  // Highlight connected nodes when hovering over an edge
  function highlightConnection(fromId, toId, highlight) {
    var fromNode = document.querySelector('[data-node-id="' + fromId + '"]');
    var toNode = document.querySelector('[data-node-id="' + toId + '"]');
    var edge = document.querySelector('.dag-edge[data-from="' + fromId + '"][data-to="' + toId + '"]');

    if (highlight) {
      if (fromNode) fromNode.classList.add('connected-highlight');
      if (toNode) toNode.classList.add('connected-highlight');
      if (edge) edge.classList.add('edge-highlight');
    } else {
      if (fromNode) fromNode.classList.remove('connected-highlight');
      if (toNode) toNode.classList.remove('connected-highlight');
      if (edge) edge.classList.remove('edge-highlight');
    }
  }

  // Highlight all edges connected to a node when hovering over the node
  function highlightNodeConnections(nodeId, highlight) {
    // Find all edges where this node is the source or target
    var edges = document.querySelectorAll('.dag-edge[data-from="' + nodeId + '"], .dag-edge[data-to="' + nodeId + '"]');

    edges.forEach(function(edge) {
      var fromId = edge.getAttribute('data-from');
      var toId = edge.getAttribute('data-to');
      highlightConnection(fromId, toId, highlight);
    });
  }

  function setupPanZoom() {
    dagSvg.onmousedown = function(e) {
      if (e.button === 0) {
        isPanning = true;
        startPan = { x: e.clientX, y: e.clientY };
        dagSvg.style.cursor = 'grabbing';
      }
    };

    dagSvg.onmousemove = function(e) {
      if (!isPanning) return;

      var dx = (e.clientX - startPan.x) * (viewBox.width / dagSvg.clientWidth);
      var dy = (e.clientY - startPan.y) * (viewBox.height / dagSvg.clientHeight);

      viewBox.x -= dx;
      viewBox.y -= dy;

      dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);

      startPan = { x: e.clientX, y: e.clientY };
    };

    dagSvg.onmouseup = function() {
      isPanning = false;
      dagSvg.style.cursor = 'default';
    };

    dagSvg.onmouseleave = function() {
      isPanning = false;
      dagSvg.style.cursor = 'default';
    };

    dagSvg.onwheel = function(e) {
      e.preventDefault();

      var zoomFactor = e.deltaY > 0 ? 1.1 : 0.9;

      var rect = dagSvg.getBoundingClientRect();
      var mouseX = e.clientX - rect.left;
      var mouseY = e.clientY - rect.top;

      var svgX = viewBox.x + (mouseX / rect.width) * viewBox.width;
      var svgY = viewBox.y + (mouseY / rect.height) * viewBox.height;

      var newWidth = viewBox.width * zoomFactor;
      var newHeight = viewBox.height * zoomFactor;

      // Limit zoom based on original size
      var minZoom = originalViewBox.width / 10;
      var maxZoom = originalViewBox.width * 5;
      if (newWidth < minZoom || newWidth > maxZoom) return;

      viewBox.x = svgX - (mouseX / rect.width) * newWidth;
      viewBox.y = svgY - (mouseY / rect.height) * newHeight;
      viewBox.width = newWidth;
      viewBox.height = newHeight;

      updateViewBox();
      updateZoomLevel();
    };
  }

  vscode.postMessage({ command: 'ready' });
})();
    `;
  }
}
