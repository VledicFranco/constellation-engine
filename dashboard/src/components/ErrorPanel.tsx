/**
 * Constellation Dashboard - Error Panel Component
 *
 * A React component that displays compilation errors with source context,
 * line numbers, and suggestions. Supports goto-line functionality.
 */

import { useState, CSSProperties } from 'react';

export interface CompileError {
  code?: string;
  message: string;
  line?: number;
  column?: number;
  sourceContext?: string[];
  suggestion?: string;
}

export interface ErrorPanelProps {
  errors: CompileError[];
  expanded?: boolean;
  onGotoLine?: (line: number, column?: number) => void;
}

const styles: Record<string, CSSProperties> = {
  panel: {
    background: 'var(--bg-secondary, #161b22)',
    borderRadius: 'var(--radius-md, 6px)',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    fontSize: '12px',
    fontWeight: 600,
    color: 'var(--text-secondary, #8b949e)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
    cursor: 'pointer',
    userSelect: 'none',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-sm, 8px)',
  },
  errorCount: {
    background: 'var(--status-failed, #f85149)',
    color: 'var(--bg-primary, #0d1117)',
    padding: '0 6px',
    borderRadius: '10px',
    fontSize: '11px',
  },
  empty: {
    padding: 'var(--spacing-md, 16px)',
    textAlign: 'center',
    color: 'var(--text-muted, #6e7681)',
    fontStyle: 'italic',
  },
  errorList: {
    maxHeight: '300px',
    overflowY: 'auto',
  },
};

export function ErrorPanel({ errors, expanded: initialExpanded = true, onGotoLine }: ErrorPanelProps) {
  const [expanded, setExpanded] = useState(initialExpanded);

  return (
    <div style={styles.panel}>
      <div
        style={styles.header}
        onClick={() => setExpanded(!expanded)}
        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, #30363d)')}
        onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
      >
        <div style={styles.headerLeft}>
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="currentColor"
            style={{
              transition: 'transform 0.2s ease',
              transform: expanded ? 'rotate(0deg)' : 'rotate(-90deg)',
            }}
          >
            <path d="M7 10l5 5 5-5z" />
          </svg>
          <span>Problems</span>
          {errors.length > 0 && <span style={styles.errorCount}>{errors.length}</span>}
        </div>
      </div>

      {expanded && (
        <div>
          {errors.length === 0 ? (
            <div style={styles.empty}>No errors</div>
          ) : (
            <div style={styles.errorList}>
              {errors.map((error, index) => (
                <ErrorItem key={index} error={error} onGotoLine={onGotoLine} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

interface ErrorItemProps {
  error: CompileError;
  onGotoLine?: (line: number, column?: number) => void;
}

const itemStyles: Record<string, CSSProperties> = {
  errorItem: {
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderBottom: '1px solid var(--border-secondary, #21262d)',
    transition: 'background 0.15s ease',
  },
  errorHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 'var(--spacing-sm, 8px)',
  },
  errorIcon: {
    flexShrink: 0,
    width: '16px',
    height: '16px',
    color: 'var(--status-failed, #f85149)',
    marginTop: '2px',
  },
  errorContent: {
    flex: 1,
    minWidth: 0,
  },
  errorMessage: {
    fontSize: '13px',
    color: 'var(--text-primary, #f0f6fc)',
    lineHeight: 1.4,
    wordBreak: 'break-word',
  },
  errorLocation: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-sm, 8px)',
    marginTop: 'var(--spacing-xs, 4px)',
    fontSize: '12px',
  },
  errorLocationLink: {
    color: 'var(--accent-primary, #58a6ff)',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
  },
  errorCode: {
    color: 'var(--text-muted, #6e7681)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
  },
  sourceContext: {
    marginTop: 'var(--spacing-sm, 8px)',
    background: 'var(--bg-tertiary, #21262d)',
    borderRadius: 'var(--radius-sm, 4px)',
    padding: 'var(--spacing-sm, 8px)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
    fontSize: '12px',
    overflowX: 'auto',
  },
  contextLine: {
    display: 'flex',
    lineHeight: 1.5,
  },
  lineNumber: {
    color: 'var(--text-muted, #6e7681)',
    minWidth: '3ch',
    textAlign: 'right',
    paddingRight: 'var(--spacing-sm, 8px)',
    userSelect: 'none',
  },
  lineContent: {
    color: 'var(--text-primary, #f0f6fc)',
    whiteSpace: 'pre',
  },
  errorLineContent: {
    color: 'var(--status-failed, #f85149)',
    whiteSpace: 'pre',
  },
  caretLine: {
    paddingLeft: 'calc(3ch + var(--spacing-sm, 8px))',
    color: 'var(--status-failed, #f85149)',
    whiteSpace: 'pre',
  },
  suggestion: {
    marginTop: 'var(--spacing-sm, 8px)',
    padding: 'var(--spacing-sm, 8px)',
    background: 'rgba(88, 166, 255, 0.1)',
    border: '1px solid var(--accent-primary, #58a6ff)',
    borderRadius: 'var(--radius-sm, 4px)',
    fontSize: '12px',
    color: 'var(--accent-primary, #58a6ff)',
  },
  suggestionLabel: {
    fontWeight: 600,
    marginRight: 'var(--spacing-xs, 4px)',
  },
};

function ErrorItem({ error, onGotoLine }: ErrorItemProps) {
  const { message, line, column, code, sourceContext, suggestion } = error;

  const handleGotoLine = () => {
    if (line && onGotoLine) {
      onGotoLine(line, column);
    }
  };

  return (
    <div
      style={itemStyles.errorItem}
      onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, #30363d)')}
      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
    >
      <div style={itemStyles.errorHeader}>
        <svg style={itemStyles.errorIcon} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
        </svg>
        <div style={itemStyles.errorContent}>
          <div style={itemStyles.errorMessage}>{message}</div>
          <div style={itemStyles.errorLocation}>
            {line && (
              <span
                style={itemStyles.errorLocationLink}
                onClick={handleGotoLine}
                onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
                onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
              >
                <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                  <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z" />
                </svg>
                Line {line}
                {column && `:${column}`}
              </span>
            )}
            {code && <span style={itemStyles.errorCode}>{code}</span>}
          </div>
        </div>
      </div>

      {sourceContext && sourceContext.length > 0 && (
        <div style={itemStyles.sourceContext}>
          {sourceContext.map((contextLine, index) => {
            const lineNum = line ? line - 1 + index : index + 1;
            const isErrorLine = line && lineNum === line;
            return (
              <div key={index}>
                <div style={itemStyles.contextLine}>
                  <span style={itemStyles.lineNumber as CSSProperties}>{lineNum}</span>
                  <span style={isErrorLine ? itemStyles.errorLineContent : itemStyles.lineContent}>
                    {contextLine}
                  </span>
                </div>
                {isErrorLine && column && (
                  <div style={itemStyles.caretLine as CSSProperties}>{' '.repeat(column - 1)}^</div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {suggestion && (
        <div style={itemStyles.suggestion}>
          <span style={itemStyles.suggestionLabel}>Suggestion:</span>
          {suggestion}
        </div>
      )}
    </div>
  );
}

export default ErrorPanel;
