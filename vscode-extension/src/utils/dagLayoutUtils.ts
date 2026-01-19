/**
 * DAG Layout Utility Functions
 *
 * Pure functions for DAG layout computation and rendering.
 * Extracted from DagVisualizerPanel for testability.
 */

export interface DagNode {
  id: string;
  type: 'module' | 'data';
  role: 'input' | 'output' | 'operation' | 'intermediate';
  name: string;
  width: number;
  height: number;
  x?: number;
  y?: number;
  cType?: string;
  consumes?: { [key: string]: string };
  produces?: { [key: string]: string };
}

export interface DagStructure {
  modules: { [uuid: string]: { name: string; consumes: { [key: string]: string }; produces: { [key: string]: string } } };
  data: { [uuid: string]: { name: string; cType: string } };
  inEdges: [string, string][];
  outEdges: [string, string][];
  declaredOutputs: string[];
}

export interface LayoutResult {
  nodes: { [id: string]: DagNode };
  edges: [string, string][];
  bounds: { minX: number; maxX: number; minY: number; maxY: number };
}

export type LayoutDirection = 'TB' | 'LR';

/**
 * Simplify node labels by stripping common prefixes and truncating.
 */
export function simplifyLabel(name: string | undefined, maxLen: number): string {
  if (!name) return '';

  let simplified = name;

  // Remove UUID-like prefix (alphanumeric followed by underscore)
  const uuidMatch = simplified.match(/^[a-f0-9]{8,}_(.+)$/i);
  if (uuidMatch) {
    simplified = uuidMatch[1];
  }

  // If name has dots (namespace), take the last part
  if (simplified.includes('.')) {
    const parts = simplified.split('.');
    simplified = parts[parts.length - 1];
  }

  // Truncate if too long
  if (simplified.length > maxLen) {
    return simplified.substring(0, maxLen - 1) + '…';
  }

  return simplified;
}

/**
 * Build tooltip text for a node.
 */
export function buildTooltip(node: { name: string; role?: string; type: string; cType?: string }): string {
  const lines: string[] = [];
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

  return lines.join('\n');
}

/**
 * Get CSS color variable for a data type.
 */
export function getTypeColor(cType: string | undefined): string {
  if (!cType) return 'var(--type-unknown)';

  const typeLower = cType.toLowerCase();

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

/**
 * Get a short type indicator for the type.
 */
export function getTypeIndicator(cType: string | undefined): string {
  if (!cType) return '';

  const typeLower = cType.toLowerCase();

  if (typeLower === 'string' || typeLower === 'text') return 'T';
  if (typeLower === 'int' || typeLower === 'integer' || typeLower === 'long') return '#';
  if (typeLower === 'float' || typeLower === 'double' || typeLower === 'decimal') return '.#';
  if (typeLower === 'boolean' || typeLower === 'bool') return '?';
  if (typeLower.startsWith('list') || typeLower.startsWith('array')) return '[]';
  if (typeLower.startsWith('record') || typeLower.startsWith('struct')) return '{}';
  if (typeLower.startsWith('option') || typeLower.includes('?')) return '~';

  return '';
}

/**
 * Format large numbers with K/M/B suffixes.
 */
export function formatLargeNumber(num: number): string {
  const absNum = Math.abs(num);
  const sign = num < 0 ? '-' : '';

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

/**
 * Truncate a value to max length with ellipsis.
 */
export function truncateValue(value: string | undefined, maxLen: number): string {
  if (!value) return '';
  if (value.length <= maxLen) return value;
  return value.substring(0, maxLen - 3) + '...';
}

/**
 * Format JSON for preview.
 */
export function formatJsonPreview(obj: unknown, maxLen: number): string {
  if (Array.isArray(obj)) {
    if (obj.length === 0) return '[]';
    if (obj.length === 1) return '[' + formatJsonPreview(obj[0], maxLen - 2) + ']';
    return '[' + obj.length + ' items]';
  }

  if (typeof obj === 'object' && obj !== null) {
    const keys = Object.keys(obj);
    if (keys.length === 0) return '{}';
    if (keys.length === 1) {
      const key = keys[0];
      const val = formatJsonPreview((obj as Record<string, unknown>)[key], maxLen - key.length - 4);
      const result = '{' + key + ': ' + val + '}';
      if (result.length <= maxLen) return result;
    }
    return '{' + keys.length + ' fields}';
  }

  if (typeof obj === 'string') {
    return '"' + truncateValue(obj, maxLen - 2) + '"';
  }

  return String(obj);
}

/**
 * Format a value preview for display - handles various data types.
 */
export function formatValuePreview(value: unknown, maxLen: number): string {
  if (value === null || value === undefined) return 'null';
  if (value === '') return '""';

  let str = String(value);

  // Try to detect and format different types
  try {
    // Check if it's a JSON string
    if ((str.startsWith('{') || str.startsWith('[')) && (str.endsWith('}') || str.endsWith(']'))) {
      const parsed = JSON.parse(str);
      str = formatJsonPreview(parsed, maxLen);
    }
  } catch {
    // Not valid JSON, use as-is
  }

  // Format boolean values
  if (str === 'true' || str === 'false') {
    return str;
  }

  // Format numbers (detect and format large numbers)
  if (!isNaN(Number(str)) && str !== '') {
    const num = Number(str);
    if (Number.isInteger(num) && Math.abs(num) >= 1000000) {
      return formatLargeNumber(num);
    }
    if (!Number.isInteger(num)) {
      // Round floats to reasonable precision
      return num.toPrecision(6).replace(/\.?0+$/, '');
    }
    return str;
  }

  // Format strings - add quotes to show it's a string
  if (!str.startsWith('"') && !str.startsWith('[') && !str.startsWith('{')) {
    str = '"' + str + '"';
  }

  return truncateValue(str, maxLen);
}

/**
 * Create hexagon points for output nodes.
 */
export function createHexagonPoints(width: number, height: number): string {
  const inset = 12; // How much the left/right points are inset
  // Points: top-left, top-right, right-point, bottom-right, bottom-left, left-point
  const points: [number, number][] = [
    [inset, 0],                    // top-left
    [width - inset, 0],            // top-right
    [width, height / 2],           // right point
    [width - inset, height],       // bottom-right
    [inset, height],               // bottom-left
    [0, height / 2]                // left point
  ];
  return points.map(p => p[0] + ',' + p[1]).join(' ');
}

/**
 * Compute the layout for a DAG.
 */
export function computeLayout(dag: DagStructure, layoutDirection: LayoutDirection = 'TB'): LayoutResult {
  const nodes: { [id: string]: DagNode } = {};
  const nodeWidth = 160;
  const moduleHeight = 60;
  const dataHeight = 40;
  const inputHeight = 36;
  const outputHeight = 44;

  // First pass: identify which data nodes have incoming edges
  const dataNodesWithIncoming = new Set<string>();
  dag.inEdges.forEach(edge => {
    dataNodesWithIncoming.add(edge[1]);
  });
  dag.outEdges.forEach(edge => {
    dataNodesWithIncoming.add(edge[1]);
  });

  // Create a set of declared outputs for quick lookup
  const declaredOutputsSet = new Set(dag.declaredOutputs || []);

  // Create nodes for modules
  Object.keys(dag.modules).forEach(uuid => {
    const spec = dag.modules[uuid];
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
  Object.keys(dag.data).forEach(uuid => {
    const spec = dag.data[uuid];
    let role: 'input' | 'output' | 'intermediate' = 'intermediate';

    // Check if this is an input (no incoming edges)
    if (!dataNodesWithIncoming.has(uuid)) {
      role = 'input';
    }
    // Check if this is a declared output
    else if (declaredOutputsSet.has(uuid)) {
      role = 'output';
    }

    let height = dataHeight;
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

  // Handle empty DAG
  if (Object.keys(nodes).length === 0) {
    return {
      nodes: {},
      edges: [],
      bounds: { minX: 0, maxX: 100, minY: 0, maxY: 100 }
    };
  }

  // Build adjacency for topological sort
  const inDegree: { [id: string]: number } = {};
  const outgoing: { [id: string]: string[] } = {};

  Object.keys(nodes).forEach(id => {
    inDegree[id] = 0;
    outgoing[id] = [];
  });

  dag.inEdges.forEach(edge => {
    const [from, to] = edge;
    if (nodes[from] && nodes[to]) {
      outgoing[from].push(to);
      inDegree[to]++;
    }
  });

  dag.outEdges.forEach(edge => {
    const [from, to] = edge;
    if (nodes[from] && nodes[to]) {
      outgoing[from].push(to);
      inDegree[to]++;
    }
  });

  // Topological sort to determine layers
  const layers: string[][] = [];
  let remaining = Object.keys(nodes).slice();

  while (remaining.length > 0) {
    let layer = remaining.filter(id => inDegree[id] === 0);

    if (layer.length === 0) {
      // Cycle detected, just add remaining
      layer = remaining.slice();
      remaining = [];
    } else {
      remaining = remaining.filter(id => inDegree[id] > 0);

      layer.forEach(id => {
        outgoing[id].forEach(targetId => {
          inDegree[targetId]--;
        });
      });
    }

    layers.push(layer);
  }

  // Position nodes based on layout direction
  const padding = 40;
  const layerGap = 100;
  const nodeGap = 30;

  if (layoutDirection === 'TB') {
    // Top-to-bottom: layers are horizontal rows
    let y = padding;

    layers.forEach(layer => {
      let x = padding;

      layer.forEach(id => {
        const node = nodes[id];
        node.x = x + node.width / 2;
        node.y = y + node.height / 2;
        x += node.width + nodeGap;
      });

      const maxHeight = Math.max(...layer.map(id => nodes[id].height));
      y += maxHeight + layerGap;
    });
  } else {
    // Left-to-right: layers are vertical columns
    let x = padding;

    layers.forEach(layer => {
      let y = padding;

      layer.forEach(id => {
        const node = nodes[id];
        node.x = x + node.width / 2;
        node.y = y + node.height / 2;
        y += node.height + nodeGap;
      });

      const maxWidth = Math.max(...layer.map(id => nodes[id].width));
      x += maxWidth + layerGap;
    });
  }

  // Calculate bounds
  const allNodes = Object.values(nodes);
  const minX = Math.min(...allNodes.map(n => (n.x ?? 0) - n.width / 2)) - padding;
  const maxX = Math.max(...allNodes.map(n => (n.x ?? 0) + n.width / 2)) + padding;
  const minY = Math.min(...allNodes.map(n => (n.y ?? 0) - n.height / 2)) - padding;
  const maxY = Math.max(...allNodes.map(n => (n.y ?? 0) + n.height / 2)) + padding;

  return {
    nodes: nodes,
    edges: [...dag.inEdges, ...dag.outEdges],
    bounds: { minX, maxX, minY, maxY }
  };
}

/**
 * Calculate edge path for SVG rendering.
 */
export function calculateEdgePath(
  fromNode: DagNode,
  toNode: DagNode,
  layoutDirection: LayoutDirection
): string {
  if (layoutDirection === 'TB') {
    // Top-to-bottom: edges go vertically
    const startY = (fromNode.y ?? 0) + fromNode.height / 2;
    const endY = (toNode.y ?? 0) - toNode.height / 2;
    const midY = (startY + endY) / 2;

    return `M ${fromNode.x ?? 0} ${startY} C ${fromNode.x ?? 0} ${midY}, ${toNode.x ?? 0} ${midY}, ${toNode.x ?? 0} ${endY}`;
  } else {
    // Left-to-right: edges go horizontally
    const startX = (fromNode.x ?? 0) + fromNode.width / 2;
    const endX = (toNode.x ?? 0) - toNode.width / 2;
    const midX = (startX + endX) / 2;

    return `M ${startX} ${fromNode.y ?? 0} C ${midX} ${fromNode.y ?? 0}, ${midX} ${toNode.y ?? 0}, ${endX} ${toNode.y ?? 0}`;
  }
}
