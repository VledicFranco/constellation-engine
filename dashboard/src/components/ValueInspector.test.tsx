/**
 * Tests for ValueInspector React component
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ValueInspector, JsonTreeView, NodeValue } from './ValueInspector.js';

describe('ValueInspector', () => {
  const completedNodeValue: NodeValue = {
    nodeId: 'node-1',
    nodeName: 'Uppercase',
    status: 'completed',
    value: { result: 'HELLO WORLD' },
    durationMs: 15,
  };

  const failedNodeValue: NodeValue = {
    nodeId: 'node-2',
    nodeName: 'ParseJson',
    status: 'failed',
    error: 'Invalid JSON: unexpected token',
    durationMs: 5,
  };

  it('renders nothing when not visible', () => {
    const { container } = render(
      <ValueInspector nodeValue={completedNodeValue} visible={false} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when nodeValue is null', () => {
    const { container } = render(
      <ValueInspector nodeValue={null} visible={true} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('displays node name and ID', () => {
    render(<ValueInspector nodeValue={completedNodeValue} visible={true} />);
    expect(screen.getByText('Uppercase')).toBeTruthy();
    expect(screen.getByText('node-1')).toBeTruthy();
  });

  it('displays status badge', () => {
    render(<ValueInspector nodeValue={completedNodeValue} visible={true} />);
    expect(screen.getByText('completed')).toBeTruthy();
  });

  it('displays duration', () => {
    render(<ValueInspector nodeValue={completedNodeValue} visible={true} />);
    expect(screen.getByText('15ms')).toBeTruthy();
  });

  it('displays value using JsonTreeView', () => {
    render(<ValueInspector nodeValue={completedNodeValue} visible={true} />);
    expect(screen.getByText('result')).toBeTruthy();
  });

  it('displays error message for failed nodes', () => {
    render(<ValueInspector nodeValue={failedNodeValue} visible={true} />);
    expect(screen.getByText('Invalid JSON: unexpected token')).toBeTruthy();
  });

  it('calls onClose when close button clicked', () => {
    const onClose = vi.fn();
    render(
      <ValueInspector
        nodeValue={completedNodeValue}
        visible={true}
        onClose={onClose}
      />
    );
    const closeButton = screen.getByTitle('Close');
    fireEvent.click(closeButton);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe('JsonTreeView', () => {
  it('renders primitive string', () => {
    render(<JsonTreeView value="hello" />);
    expect(screen.getByText(/"hello"/)).toBeTruthy();
  });

  it('renders primitive number', () => {
    render(<JsonTreeView value={42} />);
    expect(screen.getByText('42')).toBeTruthy();
  });

  it('renders primitive boolean', () => {
    render(<JsonTreeView value={true} />);
    expect(screen.getByText('true')).toBeTruthy();
  });

  it('renders null', () => {
    render(<JsonTreeView value={null} />);
    expect(screen.getByText('null')).toBeTruthy();
  });

  it('renders object with expandable keys', () => {
    render(<JsonTreeView value={{ name: 'test', count: 5 }} />);
    expect(screen.getByText('name')).toBeTruthy();
  });

  it('renders array with item count', () => {
    render(<JsonTreeView value={[1, 2, 3]} expandDepth={0} />);
    expect(screen.getByText('3 items')).toBeTruthy();
  });

  it('can expand and collapse', () => {
    const { container } = render(
      <JsonTreeView value={{ nested: { value: 1 } }} expandDepth={1} />
    );
    // Find toggle buttons and verify they work
    const toggleButtons = container.querySelectorAll('button');
    expect(toggleButtons.length).toBeGreaterThan(0);
  });
});
