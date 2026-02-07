/**
 * Constellation Dashboard - Performance Profile View Component
 *
 * A React component that displays execution profiling data as a waterfall chart.
 * Shows timing breakdown for each node in the DAG execution.
 */

import { useState, useMemo, CSSProperties } from 'react';

export interface NodeProfile {
  nodeId: string;
  nodeName: string;
  startTime: number; // ms from execution start
  endTime: number; // ms from execution start
  durationMs: number;
  status: 'completed' | 'failed';
  category?: string;
}

export interface ExecutionProfile {
  executionId: string;
  dagName: string;
  startTime: number; // Unix timestamp
  totalDurationMs: number;
  nodes: NodeProfile[];
}

export interface ProfileViewProps {
  profile: ExecutionProfile | null;
  visible?: boolean;
  onClose?: () => void;
  onNodeClick?: (nodeId: string) => void;
}

const styles: Record<string, CSSProperties> = {
  container: {
    background: 'var(--bg-secondary, #161b22)',
    borderRadius: 'var(--radius-md, 6px)',
    border: '1px solid var(--border-primary, #30363d)',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    background: 'var(--bg-tertiary, #21262d)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  title: {
    fontSize: '13px',
    fontWeight: 600,
    color: 'var(--text-primary, #f0f6fc)',
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
  summary: {
    display: 'flex',
    gap: 'var(--spacing-lg, 24px)',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderBottom: '1px solid var(--border-secondary, #21262d)',
    fontSize: '12px',
  },
  summaryItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
  },
  summaryLabel: {
    color: 'var(--text-muted, #6e7681)',
  },
  summaryValue: {
    color: 'var(--text-primary, #f0f6fc)',
    fontWeight: 500,
    fontFamily: "'SF Mono', 'Consolas', monospace",
  },
  content: {
    flex: 1,
    overflow: 'auto',
    padding: 'var(--spacing-md, 12px)',
  },
  waterfall: {
    position: 'relative',
    minWidth: '100%',
  },
  timeline: {
    display: 'flex',
    justifyContent: 'space-between',
    marginBottom: '8px',
    paddingLeft: '140px',
    fontSize: '10px',
    color: 'var(--text-muted, #6e7681)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
  },
  row: {
    display: 'flex',
    alignItems: 'center',
    height: '28px',
    marginBottom: '4px',
  },
  nodeName: {
    width: '140px',
    flexShrink: 0,
    fontSize: '12px',
    color: 'var(--text-secondary, #8b949e)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    paddingRight: '8px',
  },
  barContainer: {
    flex: 1,
    height: '20px',
    position: 'relative',
    background: 'var(--bg-primary, #0d1117)',
    borderRadius: '3px',
  },
  bar: {
    position: 'absolute',
    height: '100%',
    borderRadius: '3px',
    cursor: 'pointer',
    transition: 'opacity 0.15s',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    paddingRight: '6px',
    fontSize: '10px',
    fontFamily: "'SF Mono', 'Consolas', monospace",
    color: 'white',
    overflow: 'hidden',
    whiteSpace: 'nowrap',
  },
  barCompleted: {
    background: 'linear-gradient(90deg, var(--status-success, #3fb950) 0%, #2ea043 100%)',
  },
  barFailed: {
    background: 'linear-gradient(90deg, var(--status-failed, #f85149) 0%, #da3633 100%)',
  },
  empty: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '200px',
    color: 'var(--text-muted, #6e7681)',
    fontSize: '14px',
  },
  emptyIcon: {
    width: '48px',
    height: '48px',
    marginBottom: '12px',
    opacity: 0.5,
  },
  sortControls: {
    display: 'flex',
    gap: '8px',
    padding: '8px 12px',
    borderBottom: '1px solid var(--border-secondary, #21262d)',
    fontSize: '11px',
  },
  sortBtn: {
    background: 'var(--bg-tertiary, #21262d)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: '4px',
    padding: '4px 8px',
    color: 'var(--text-secondary, #8b949e)',
    cursor: 'pointer',
    fontSize: '11px',
  },
  sortBtnActive: {
    background: 'var(--accent-primary, #58a6ff)',
    borderColor: 'var(--accent-primary, #58a6ff)',
    color: 'white',
  },
};

type SortMode = 'timeline' | 'duration' | 'name';

function formatDuration(ms: number): string {
  if (ms < 1) return '<1ms';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function formatTimestamp(ts: number): string {
  const date = new Date(ts);
  return date.toLocaleTimeString();
}

export function ProfileView({ profile, visible = true, onClose, onNodeClick }: ProfileViewProps) {
  const [sortMode, setSortMode] = useState<SortMode>('timeline');
  const [hoveredNode, setHoveredNode] = useState<string | null>(null);

  const sortedNodes = useMemo(() => {
    if (!profile) return [];

    const nodes = [...profile.nodes];

    switch (sortMode) {
      case 'timeline':
        return nodes.sort((a, b) => a.startTime - b.startTime);
      case 'duration':
        return nodes.sort((a, b) => b.durationMs - a.durationMs);
      case 'name':
        return nodes.sort((a, b) => a.nodeName.localeCompare(b.nodeName));
      default:
        return nodes;
    }
  }, [profile, sortMode]);

  const timelineMarks = useMemo(() => {
    if (!profile || profile.totalDurationMs === 0) return [];

    const total = profile.totalDurationMs;
    const marks = [0, total * 0.25, total * 0.5, total * 0.75, total];

    return marks.map((ms) => formatDuration(ms));
  }, [profile]);

  if (!visible) {
    return null;
  }

  if (!profile) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <span style={styles.title}>Performance Profile</span>
          {onClose && (
            <button
              style={styles.closeBtn}
              onClick={onClose}
              title="Close"
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'var(--bg-hover, #30363d)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'none';
              }}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          )}
        </div>
        <div style={styles.empty}>
          <svg style={styles.emptyIcon} viewBox="0 0 24 24" fill="currentColor">
            <path d="M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z" />
          </svg>
          <div>No execution profile available</div>
          <div style={{ fontSize: '12px', marginTop: '4px' }}>Run a pipeline to see timing data</div>
        </div>
      </div>
    );
  }

  const total = profile.totalDurationMs || 1; // Avoid division by zero

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <span style={styles.title}>Performance Profile</span>
        {onClose && (
          <button
            style={styles.closeBtn}
            onClick={onClose}
            title="Close"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'var(--bg-hover, #30363d)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'none';
            }}
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        )}
      </div>

      <div style={styles.summary}>
        <div style={styles.summaryItem}>
          <span style={styles.summaryLabel}>DAG:</span>
          <span style={styles.summaryValue}>{profile.dagName}</span>
        </div>
        <div style={styles.summaryItem}>
          <span style={styles.summaryLabel}>Total:</span>
          <span style={styles.summaryValue}>{formatDuration(profile.totalDurationMs)}</span>
        </div>
        <div style={styles.summaryItem}>
          <span style={styles.summaryLabel}>Nodes:</span>
          <span style={styles.summaryValue}>{profile.nodes.length}</span>
        </div>
        <div style={styles.summaryItem}>
          <span style={styles.summaryLabel}>Started:</span>
          <span style={styles.summaryValue}>{formatTimestamp(profile.startTime)}</span>
        </div>
      </div>

      <div style={styles.sortControls}>
        <span style={{ color: 'var(--text-muted, #6e7681)' }}>Sort by:</span>
        <button
          style={{ ...styles.sortBtn, ...(sortMode === 'timeline' ? styles.sortBtnActive : {}) }}
          onClick={() => setSortMode('timeline')}
        >
          Timeline
        </button>
        <button
          style={{ ...styles.sortBtn, ...(sortMode === 'duration' ? styles.sortBtnActive : {}) }}
          onClick={() => setSortMode('duration')}
        >
          Duration
        </button>
        <button
          style={{ ...styles.sortBtn, ...(sortMode === 'name' ? styles.sortBtnActive : {}) }}
          onClick={() => setSortMode('name')}
        >
          Name
        </button>
      </div>

      <div style={styles.content}>
        <div style={styles.waterfall}>
          {/* Timeline markers */}
          <div style={styles.timeline}>
            {timelineMarks.map((mark, i) => (
              <span key={i}>{mark}</span>
            ))}
          </div>

          {/* Node bars */}
          {sortedNodes.map((node) => {
            const left = (node.startTime / total) * 100;
            const width = Math.max((node.durationMs / total) * 100, 1); // Min 1% width for visibility

            return (
              <div key={node.nodeId} style={styles.row}>
                <div style={styles.nodeName} title={node.nodeName}>
                  {node.nodeName}
                </div>
                <div style={styles.barContainer}>
                  <div
                    style={{
                      ...styles.bar,
                      ...(node.status === 'completed' ? styles.barCompleted : styles.barFailed),
                      left: `${left}%`,
                      width: `${width}%`,
                      opacity: hoveredNode && hoveredNode !== node.nodeId ? 0.5 : 1,
                    }}
                    onClick={() => onNodeClick?.(node.nodeId)}
                    onMouseEnter={() => setHoveredNode(node.nodeId)}
                    onMouseLeave={() => setHoveredNode(null)}
                    title={`${node.nodeName}: ${formatDuration(node.durationMs)}`}
                  >
                    {width > 8 && formatDuration(node.durationMs)}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default ProfileView;
