import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

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

export class DagVisualizerPanel {
  public static currentPanel: DagVisualizerPanel | undefined;
  public static readonly viewType = 'constellationDagVisualizer';

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

    if (DagVisualizerPanel.currentPanel) {
      DagVisualizerPanel.currentPanel._panel.reveal(column);
      DagVisualizerPanel.currentPanel._client = client;
      DagVisualizerPanel.currentPanel._currentUri = documentUri;
      DagVisualizerPanel.currentPanel._refreshDag();
      return;
    }

    const panel = vscode.window.createWebviewPanel(
      DagVisualizerPanel.viewType,
      'DAG Visualizer',
      column,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'src', 'webview')]
      }
    );

    DagVisualizerPanel.currentPanel = new DagVisualizerPanel(panel, extensionUri, client, documentUri);
  }

  public static refresh(documentUri: string) {
    if (DagVisualizerPanel.currentPanel && DagVisualizerPanel.currentPanel._currentUri === documentUri) {
      DagVisualizerPanel.currentPanel._refreshDag();
    }
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
        switch (message.command) {
          case 'ready':
            await this._refreshDag();
            break;
          case 'refresh':
            await this._refreshDag();
            break;
          case 'nodeClick':
            // Future: handle node click for details panel
            console.log('Node clicked:', message.nodeId, message.nodeType);
            break;
        }
      },
      null,
      this._disposables
    );
  }

  private async _refreshDag() {
    if (!this._client || !this._currentUri) {
      this._panel.webview.postMessage({
        type: 'error',
        message: 'Language server not connected'
      });
      return;
    }

    this._panel.webview.postMessage({ type: 'loading', loading: true });

    try {
      const result = await this._client.sendRequest<GetDagStructureResult>(
        'constellation/getDagStructure',
        { uri: this._currentUri }
      );

      if (result.success && result.dag) {
        const fileName = this._currentUri.split('/').pop() || 'script.cst';
        this._panel.webview.postMessage({
          type: 'dagData',
          dag: result.dag,
          fileName: fileName
        });
      } else {
        this._panel.webview.postMessage({
          type: 'error',
          message: result.error || 'Failed to get DAG structure'
        });
      }
    } catch (error: any) {
      this._panel.webview.postMessage({
        type: 'error',
        message: error.message || 'Failed to get DAG structure'
      });
    }
  }

  public dispose() {
    DagVisualizerPanel.currentPanel = undefined;

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
  <title>DAG Visualizer</title>
  <style>
${this._getCssContent()}
  </style>
</head>
<body>
  <header class="header">
    <div class="header-left">
      <span class="header-icon">&#9670;</span>
      <div class="header-content">
        <h1>DAG Visualizer</h1>
        <div class="file-name" id="fileName">Loading...</div>
      </div>
    </div>
    <div class="header-actions">
      <button class="icon-btn" id="refreshBtn" title="Refresh">&#8635;</button>
    </div>
  </header>

  <div class="container" id="container">
    <div class="loading-container" id="loadingContainer">
      <div class="spinner"></div>
      <div style="margin-top: 8px; font-size: 12px;">Loading DAG...</div>
    </div>
    <svg id="dagSvg" class="dag-svg"></svg>
  </div>

  <div class="error-container" id="errorContainer" style="display: none;">
    <div class="error-box" id="errorMessage"></div>
  </div>

  <script>
${this._getJsContent()}
  </script>
</body>
</html>`;
  }

  private _getCssContent(): string {
    return `
    :root {
      --spacing-xs: 4px;
      --spacing-sm: 8px;
      --spacing-md: 12px;
      --spacing-lg: 16px;
      --radius-sm: 4px;
      --radius-md: 6px;

      --node-data-border: var(--vscode-charts-blue, #3794ff);
      --node-module-border: var(--vscode-charts-orange, #d18616);
      --edge-color: var(--vscode-charts-yellow, #cca700);
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif);
      font-size: var(--vscode-font-size, 13px);
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      line-height: 1.5;
      overflow: hidden;
      height: 100vh;
      display: flex;
      flex-direction: column;
    }

    .header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: var(--spacing-md) var(--spacing-lg);
      border-bottom: 1px solid var(--vscode-panel-border, rgba(128, 128, 128, 0.35));
      flex-shrink: 0;
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
      display: flex;
      flex-direction: column;
      align-items: center;
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

    .error-container {
      padding: var(--spacing-lg);
    }

    .error-box {
      background: var(--vscode-inputValidation-errorBackground, rgba(255, 0, 0, 0.1));
      border: 1px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
      border-left: 3px solid var(--vscode-inputValidation-errorBorder, #f14c4c);
      border-radius: var(--radius-md);
      padding: var(--spacing-lg);
      color: var(--vscode-errorForeground, #f14c4c);
      font-size: 13px;
    }

    .dag-node {
      cursor: pointer;
    }

    .dag-node rect, .dag-node ellipse {
      fill: var(--vscode-editor-background);
      stroke-width: 2;
      transition: stroke-width 0.15s ease;
    }

    .dag-node:hover rect, .dag-node:hover ellipse {
      stroke-width: 3;
    }

    .dag-node-data rect {
      stroke: var(--node-data-border);
      rx: 8;
      ry: 8;
    }

    .dag-node-module rect {
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
      fill: var(--vscode-descriptionForeground);
    }

    .dag-edge path {
      fill: none;
      stroke: var(--edge-color);
      stroke-width: 1.5;
      opacity: 0.6;
      transition: opacity 0.15s ease, stroke-width 0.15s ease;
    }

    .dag-edge:hover path {
      opacity: 1;
      stroke-width: 2.5;
    }

    .dag-edge polygon {
      fill: var(--edge-color);
      opacity: 0.6;
    }

    .dag-edge:hover polygon {
      opacity: 1;
    }
    `;
  }

  private _getJsContent(): string {
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

  var currentDag = null;
  var viewBox = { x: 0, y: 0, width: 800, height: 600 };
  var isPanning = false;
  var startPan = { x: 0, y: 0 };
  var scale = 1;

  refreshBtn.onclick = function() {
    vscode.postMessage({ command: 'refresh' });
  };

  window.addEventListener('message', function(event) {
    var message = event.data;

    if (message.type === 'loading') {
      loadingContainer.style.display = message.loading ? 'flex' : 'none';
      errorContainer.style.display = 'none';
    } else if (message.type === 'dagData') {
      loadingContainer.style.display = 'none';
      errorContainer.style.display = 'none';
      fileNameEl.textContent = message.fileName || 'script.cst';
      currentDag = message.dag;
      renderDag(message.dag);
    } else if (message.type === 'error') {
      loadingContainer.style.display = 'none';
      errorContainer.style.display = 'block';
      errorMessage.textContent = message.message;
    }
  });

  function renderDag(dag) {
    var layout = computeLayout(dag);
    renderSvg(layout, dag);
    setupPanZoom();
  }

  function computeLayout(dag) {
    var nodes = {};
    var nodeWidth = 160;
    var moduleHeight = 60;
    var dataHeight = 40;

    // Create nodes for modules
    Object.keys(dag.modules).forEach(function(uuid) {
      var spec = dag.modules[uuid];
      nodes[uuid] = {
        id: uuid,
        type: 'module',
        name: spec.name,
        width: nodeWidth,
        height: moduleHeight,
        consumes: spec.consumes,
        produces: spec.produces
      };
    });

    // Create nodes for data
    Object.keys(dag.data).forEach(function(uuid) {
      var spec = dag.data[uuid];
      nodes[uuid] = {
        id: uuid,
        type: 'data',
        name: spec.name,
        cType: spec.cType,
        width: nodeWidth,
        height: dataHeight
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

    // Position nodes
    var padding = 40;
    var layerGap = 100;
    var nodeGap = 30;
    var y = padding;

    layers.forEach(function(layer) {
      var totalWidth = layer.reduce(function(sum, id) {
        return sum + nodes[id].width;
      }, 0) + (layer.length - 1) * nodeGap;

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
    dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);

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
      var fromNode = layout.nodes[edge[0]];
      var toNode = layout.nodes[edge[1]];
      if (!fromNode || !toNode) return;

      var edgeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      edgeGroup.setAttribute('class', 'dag-edge');

      var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');

      // Calculate edge path with curve
      var startY = fromNode.y + fromNode.height / 2;
      var endY = toNode.y - toNode.height / 2;
      var midY = (startY + endY) / 2;

      var d = 'M ' + fromNode.x + ' ' + startY +
              ' C ' + fromNode.x + ' ' + midY +
              ', ' + toNode.x + ' ' + midY +
              ', ' + toNode.x + ' ' + endY;

      path.setAttribute('d', d);
      path.setAttribute('marker-end', 'url(#arrowhead)');
      edgeGroup.appendChild(path);
      edgesGroup.appendChild(edgeGroup);
    });

    dagSvg.appendChild(edgesGroup);

    // Render nodes
    var nodesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    nodesGroup.setAttribute('class', 'nodes-group');

    Object.keys(layout.nodes).forEach(function(id) {
      var node = layout.nodes[id];
      var nodeGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      nodeGroup.setAttribute('class', 'dag-node dag-node-' + node.type);
      nodeGroup.setAttribute('transform', 'translate(' + (node.x - node.width/2) + ',' + (node.y - node.height/2) + ')');

      var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
      rect.setAttribute('width', node.width);
      rect.setAttribute('height', node.height);
      nodeGroup.appendChild(rect);

      var text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', node.width / 2);
      text.setAttribute('y', node.height / 2);
      text.textContent = node.name;
      nodeGroup.appendChild(text);

      if (node.type === 'data' && node.cType) {
        var typeText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        typeText.setAttribute('class', 'node-type');
        typeText.setAttribute('x', node.width / 2);
        typeText.setAttribute('y', node.height / 2 + 14);
        typeText.textContent = node.cType;
        nodeGroup.appendChild(typeText);
      }

      nodeGroup.onclick = function() {
        vscode.postMessage({ command: 'nodeClick', nodeId: id, nodeType: node.type });
      };

      nodesGroup.appendChild(nodeGroup);
    });

    dagSvg.appendChild(nodesGroup);
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

      // Limit zoom
      if (newWidth < 100 || newWidth > 5000) return;

      viewBox.x = svgX - (mouseX / rect.width) * newWidth;
      viewBox.y = svgY - (mouseY / rect.height) * newHeight;
      viewBox.width = newWidth;
      viewBox.height = newHeight;

      dagSvg.setAttribute('viewBox', viewBox.x + ' ' + viewBox.y + ' ' + viewBox.width + ' ' + viewBox.height);
    };
  }

  vscode.postMessage({ command: 'ready' });
})();
    `;
  }
}
