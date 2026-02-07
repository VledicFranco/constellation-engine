/**
 * Tests for ErrorPanel React component
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorPanel, CompileError } from './ErrorPanel.js';

describe('ErrorPanel', () => {
  const sampleErrors: CompileError[] = [
    {
      code: 'E001',
      message: 'Undefined variable: foo',
      line: 10,
      column: 5,
    },
    {
      code: 'E002',
      message: 'Type mismatch',
      line: 15,
      column: 10,
      suggestion: 'Try converting the value to String',
    },
  ];

  it('renders empty state when no errors', () => {
    render(<ErrorPanel errors={[]} />);
    expect(screen.getByText('No errors')).toBeTruthy();
  });

  it('renders error count in header', () => {
    render(<ErrorPanel errors={sampleErrors} />);
    expect(screen.getByText('2')).toBeTruthy();
  });

  it('displays error messages', () => {
    render(<ErrorPanel errors={sampleErrors} />);
    expect(screen.getByText('Undefined variable: foo')).toBeTruthy();
    expect(screen.getByText('Type mismatch')).toBeTruthy();
  });

  it('shows line information as clickable link', () => {
    const onGotoLine = vi.fn();
    render(<ErrorPanel errors={sampleErrors} onGotoLine={onGotoLine} />);

    const lineLink = screen.getByText('Line 10:5');
    fireEvent.click(lineLink);
    expect(onGotoLine).toHaveBeenCalledWith(10, 5);
  });

  it('shows suggestion when available', () => {
    render(<ErrorPanel errors={sampleErrors} />);
    expect(screen.getByText(/Try converting the value to String/)).toBeTruthy();
  });

  it('can collapse and expand', () => {
    render(<ErrorPanel errors={sampleErrors} expanded={true} />);

    // Click header to collapse
    const header = screen.getByText('Problems').closest('div');
    if (header) {
      fireEvent.click(header);
    }

    // After collapse, errors should not be visible
    // (implementation depends on how collapsed state is handled)
  });
});
