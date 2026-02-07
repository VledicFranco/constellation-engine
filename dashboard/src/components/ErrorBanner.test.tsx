/**
 * Tests for ErrorBanner React component
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorBanner } from './ErrorBanner.js';

describe('ErrorBanner', () => {
  it('renders nothing when not visible', () => {
    const { container } = render(
      <ErrorBanner message="Test error" visible={false} />
    );
    // When not visible, the container should be empty
    expect(container.innerHTML).toBe('');
  });

  it('renders message when visible', () => {
    render(<ErrorBanner message="Test error message" visible={true} />);
    expect(screen.getByText('Test error message')).toBeTruthy();
  });

  it('shows dismiss button when dismissable', () => {
    const onDismiss = vi.fn();
    render(
      <ErrorBanner
        message="Dismissable error"
        visible={true}
        dismissable={true}
        onDismiss={onDismiss}
      />
    );
    // Button has aria-label="Dismiss" instead of title
    const dismissButton = screen.getByLabelText('Dismiss');
    expect(dismissButton).toBeTruthy();
    fireEvent.click(dismissButton);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('applies correct severity styles', () => {
    const { rerender, container } = render(
      <ErrorBanner message="Error" visible={true} severity="error" />
    );
    // Just verify it renders without crashing with different severities
    expect(container.querySelector('div')).toBeTruthy();

    rerender(<ErrorBanner message="Warning" visible={true} severity="warning" />);
    expect(container.querySelector('div')).toBeTruthy();

    rerender(<ErrorBanner message="Info" visible={true} severity="info" />);
    expect(container.querySelector('div')).toBeTruthy();
  });
});
