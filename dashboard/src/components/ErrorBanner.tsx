/**
 * Constellation Dashboard - Error Banner Component
 *
 * A React component for displaying error messages with optional dismiss action.
 */

import { CSSProperties } from 'react';

export type Severity = 'error' | 'warning' | 'info';

export interface ErrorBannerProps {
  message: string;
  visible?: boolean;
  dismissable?: boolean;
  severity?: Severity;
  onDismiss?: () => void;
}

const severityStyles: Record<Severity, CSSProperties> = {
  error: {
    background: 'rgba(248, 81, 73, 0.1)',
    border: '1px solid var(--status-failed, #f85149)',
    color: 'var(--status-failed, #f85149)',
  },
  warning: {
    background: 'rgba(255, 166, 87, 0.1)',
    border: '1px solid var(--node-literal, #ffa657)',
    color: 'var(--node-literal, #ffa657)',
  },
  info: {
    background: 'rgba(88, 166, 255, 0.1)',
    border: '1px solid var(--accent-primary, #58a6ff)',
    color: 'var(--accent-primary, #58a6ff)',
  },
};

const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 'var(--spacing-sm, 8px)',
  padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
  borderRadius: 'var(--radius-sm, 4px)',
  fontSize: '13px',
  lineHeight: 1.4,
};

const iconStyle: CSSProperties = {
  flexShrink: 0,
  width: '16px',
  height: '16px',
};

const messageStyle: CSSProperties = {
  flex: 1,
  wordBreak: 'break-word',
};

const dismissStyle: CSSProperties = {
  flexShrink: 0,
  background: 'none',
  border: 'none',
  padding: 0,
  cursor: 'pointer',
  color: 'inherit',
  opacity: 0.7,
};

function getIcon(severity: Severity) {
  switch (severity) {
    case 'error':
      return (
        <svg style={iconStyle} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
        </svg>
      );
    case 'warning':
      return (
        <svg style={iconStyle} viewBox="0 0 24 24" fill="currentColor">
          <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" />
        </svg>
      );
    case 'info':
      return (
        <svg style={iconStyle} viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
        </svg>
      );
  }
}

export function ErrorBanner({
  message,
  visible = false,
  dismissable = true,
  severity = 'error',
  onDismiss,
}: ErrorBannerProps) {
  if (!visible) return null;

  return (
    <div style={{ ...bannerStyle, ...severityStyles[severity] }}>
      {getIcon(severity)}
      <span style={messageStyle}>{message}</span>
      {dismissable && (
        <button
          style={dismissStyle}
          onClick={onDismiss}
          aria-label="Dismiss"
          onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = '0.7')}
        >
          <svg style={{ width: '16px', height: '16px' }} viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      )}
    </div>
  );
}

export default ErrorBanner;
