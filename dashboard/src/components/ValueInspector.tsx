/**
 * Constellation Dashboard - Value Inspector Component
 *
 * A React component that displays intermediate execution values
 * with a collapsible JSON tree view. Shows when a DAG node is clicked.
 */

import { useState, useEffect, CSSProperties } from 'react';

export interface NodeValue {
  nodeId: string;
  nodeName: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  value?: unknown;
  error?: string;
  durationMs?: number;
  timestamp?: number;
}

export interface ValueInspectorProps {
  nodeValue: NodeValue | null;
  visible?: boolean;
  onClose?: () => void;
}

const styles: Record<string, CSSProperties> = {
  inspector: {
    background: 'var(--bg-secondary, #161b22)',
    borderRadius: 'var(--radius-md, 6px)',
    border: '1px solid var(--border-primary, #30363d)',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    background: 'var(--bg-tertiary, #21262d)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-sm, 8px)',
  },
  nodeName: {
    fontSize: '13px',
    fontWeight: 600,
    color: 'var(--text-primary, #f0f6fc)',
  },
  nodeId: {
    fontSize: '11px',
    color: 'var(--text-muted, #6e7681)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
  },
  closeBtn: {
    background: 'none',
    border: 'none',
    color: 'var(--text-secondary, #8b949e)',
    cursor: 'pointer',
    padding: '4px',
    borderRadius: '4px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-md, 12px)',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderBottom: '1px solid var(--border-secondary, #21262d)',
    fontSize: '12px',
  },
  statusBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    padding: '2px 8px',
    borderRadius: '10px',
    fontSize: '11px',
    fontWeight: 500,
  },
  statusPending: {
    background: 'rgba(139, 148, 158, 0.2)',
    color: 'var(--status-pending, #8b949e)',
  },
  statusRunning: {
    background: 'rgba(88, 166, 255, 0.2)',
    color: 'var(--status-running, #58a6ff)',
  },
  statusCompleted: {
    background: 'rgba(63, 185, 80, 0.2)',
    color: 'var(--status-success, #3fb950)',
  },
  statusFailed: {
    background: 'rgba(248, 81, 73, 0.2)',
    color: 'var(--status-failed, #f85149)',
  },
  duration: {
    color: 'var(--text-muted, #6e7681)',
  },
  content: {
    padding: 'var(--spacing-md, 12px)',
    maxHeight: '400px',
    overflow: 'auto',
  },
  empty: {
    textAlign: 'center',
    color: 'var(--text-muted, #6e7681)',
    fontStyle: 'italic',
    padding: 'var(--spacing-lg, 24px)',
  },
  errorMessage: {
    background: 'rgba(248, 81, 73, 0.1)',
    border: '1px solid var(--status-failed, #f85149)',
    borderRadius: 'var(--radius-sm, 4px)',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    color: 'var(--status-failed, #f85149)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
    fontSize: '12px',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
};

function formatDuration(ms?: number): string {
  if (ms === undefined) return '';
  if (ms < 1) return '<1ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function getStatusStyle(status: string): CSSProperties {
  switch (status) {
    case 'pending':
      return styles.statusPending;
    case 'running':
      return styles.statusRunning;
    case 'completed':
      return styles.statusCompleted;
    case 'failed':
      return styles.statusFailed;
    default:
      return {};
  }
}

export function ValueInspector({ nodeValue, visible = true, onClose }: ValueInspectorProps) {
  if (!visible || !nodeValue) {
    return null;
  }

  const { nodeName, nodeId, status, value, error, durationMs } = nodeValue;

  return (
    <div style={styles.inspector}>
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <span style={styles.nodeName}>{nodeName}</span>
          <span style={styles.nodeId}>{nodeId}</span>
        </div>
        <button
          style={styles.closeBtn}
          onClick={onClose}
          title="Close"
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'var(--bg-hover, #30363d)';
            e.currentTarget.style.color = 'var(--text-primary, #f0f6fc)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'none';
            e.currentTarget.style.color = 'var(--text-secondary, #8b949e)';
          }}
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      </div>

      <div style={styles.statusBar}>
        <span style={{ ...styles.statusBadge, ...getStatusStyle(status) }}>{status}</span>
        {durationMs !== undefined && <span style={styles.duration}>{formatDuration(durationMs)}</span>}
      </div>

      <div style={styles.content}>
        {status === 'pending' ? (
          <div style={styles.empty}>Waiting for execution...</div>
        ) : status === 'running' ? (
          <div style={styles.empty}>Executing...</div>
        ) : status === 'failed' && error ? (
          <div style={styles.errorMessage}>{error}</div>
        ) : value !== undefined ? (
          <JsonTreeView value={value} />
        ) : (
          <div style={styles.empty}>No value</div>
        )}
      </div>
    </div>
  );
}

interface JsonTreeViewProps {
  value: unknown;
  expandDepth?: number;
  depth?: number;
  propertyKey?: string;
}

const treeStyles: Record<string, CSSProperties> = {
  host: {
    display: 'block',
    fontFamily: "'SF Mono', 'Consolas', monospace",
    fontSize: '12px',
    lineHeight: 1.5,
  },
  treeNode: {
    position: 'relative',
  },
  treeRow: {
    display: 'flex',
    alignItems: 'flex-start',
    padding: '1px 0',
    cursor: 'default',
  },
  toggleBtn: {
    background: 'none',
    border: 'none',
    padding: 0,
    width: '16px',
    height: '16px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    color: 'var(--text-muted, #6e7681)',
    flexShrink: 0,
  },
  togglePlaceholder: {
    width: '16px',
    flexShrink: 0,
  },
  key: {
    color: 'var(--text-secondary, #8b949e)',
    marginRight: '4px',
  },
  colon: {
    color: 'var(--text-muted, #6e7681)',
    marginRight: '4px',
  },
  valueString: {
    color: 'var(--syntax-string, #a5d6ff)',
  },
  valueNumber: {
    color: 'var(--syntax-number, #79c0ff)',
  },
  valueBoolean: {
    color: 'var(--syntax-boolean, #ff7b72)',
  },
  valueNull: {
    color: 'var(--syntax-null, #6e7681)',
    fontStyle: 'italic',
  },
  bracket: {
    color: 'var(--text-muted, #6e7681)',
  },
  typeIndicator: {
    color: 'var(--text-muted, #6e7681)',
    fontSize: '10px',
    marginLeft: '4px',
  },
  children: {
    marginLeft: '16px',
  },
  ellipsis: {
    color: 'var(--text-muted, #6e7681)',
  },
};

export function JsonTreeView({ value, expandDepth = 2, depth = 0, propertyKey = '' }: JsonTreeViewProps) {
  const [expanded, setExpanded] = useState(() => expandDepth === -1 || depth < expandDepth);

  // Update expanded state when expandDepth changes
  useEffect(() => {
    setExpanded(expandDepth === -1 || depth < expandDepth);
  }, [expandDepth, depth]);

  const isExpandable = (val: unknown): boolean => {
    if (val === null || val === undefined) return false;
    if (Array.isArray(val)) return val.length > 0;
    if (typeof val === 'object') return Object.keys(val).length > 0;
    return false;
  };

  const getType = (val: unknown): string => {
    if (val === null) return 'null';
    if (val === undefined) return 'undefined';
    if (Array.isArray(val)) return 'array';
    return typeof val;
  };

  const renderPrimitive = (val: unknown) => {
    if (val === null) {
      return <span style={treeStyles.valueNull}>null</span>;
    }
    if (val === undefined) {
      return <span style={treeStyles.valueNull}>undefined</span>;
    }
    if (typeof val === 'string') {
      const displayVal = val.length > 100 ? val.substring(0, 100) + '...' : val;
      return <span style={treeStyles.valueString}>"{displayVal}"</span>;
    }
    if (typeof val === 'number') {
      return <span style={treeStyles.valueNumber}>{val}</span>;
    }
    if (typeof val === 'boolean') {
      return <span style={treeStyles.valueBoolean}>{String(val)}</span>;
    }
    return <span>{String(val)}</span>;
  };

  const renderCollapsedPreview = (val: unknown) => {
    if (Array.isArray(val)) {
      return (
        <>
          <span style={treeStyles.bracket}>[</span>
          <span style={treeStyles.ellipsis}>...</span>
          <span style={treeStyles.bracket}>]</span>
          <span style={treeStyles.typeIndicator}>{val.length} items</span>
        </>
      );
    }
    if (typeof val === 'object' && val !== null) {
      const keys = Object.keys(val);
      return (
        <>
          <span style={treeStyles.bracket}>{'{'}</span>
          <span style={treeStyles.ellipsis}>...</span>
          <span style={treeStyles.bracket}>{'}'}</span>
          <span style={treeStyles.typeIndicator}>{keys.length} keys</span>
        </>
      );
    }
    return renderPrimitive(val);
  };

  const expandableValue = isExpandable(value);
  const type = getType(value);

  const keyPart = propertyKey ? (
    <>
      <span style={treeStyles.key}>{propertyKey}</span>
      <span style={treeStyles.colon}>:</span>
    </>
  ) : null;

  // Primitives
  if (!expandableValue) {
    return (
      <div style={treeStyles.host}>
        <div style={treeStyles.treeNode}>
          <div
            style={treeStyles.treeRow}
            onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, rgba(48, 54, 61, 0.5))')}
            onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
          >
            <span style={treeStyles.togglePlaceholder}></span>
            {keyPart}
            {renderPrimitive(value)}
          </div>
        </div>
      </div>
    );
  }

  // Arrays and objects
  const entries =
    type === 'array'
      ? (value as unknown[]).map((v, i) => [String(i), v] as [string, unknown])
      : Object.entries(value as Record<string, unknown>);

  const openBracket = type === 'array' ? '[' : '{';
  const closeBracket = type === 'array' ? ']' : '}';

  return (
    <div style={treeStyles.host}>
      <div style={treeStyles.treeNode}>
        <div
          style={treeStyles.treeRow}
          onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, rgba(48, 54, 61, 0.5))')}
          onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
        >
          <button
            style={treeStyles.toggleBtn}
            onClick={() => setExpanded(!expanded)}
            onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--text-primary, #f0f6fc)')}
            onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-muted, #6e7681)')}
          >
            <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
              {expanded ? <path d="M7 10l5 5 5-5z" /> : <path d="M10 17l5-5-5-5z" />}
            </svg>
          </button>
          {keyPart}
          {expanded ? (
            <span style={treeStyles.bracket}>{openBracket}</span>
          ) : (
            renderCollapsedPreview(value)
          )}
        </div>
        {expanded && (
          <>
            <div style={treeStyles.children}>
              {entries.map(([key, childVal]) => (
                <JsonTreeView
                  key={key}
                  value={childVal}
                  propertyKey={key}
                  depth={depth + 1}
                  expandDepth={expandDepth}
                />
              ))}
            </div>
            <div
              style={treeStyles.treeRow}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, rgba(48, 54, 61, 0.5))')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              <span style={treeStyles.togglePlaceholder}></span>
              <span style={treeStyles.bracket}>{closeBracket}</span>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default ValueInspector;
