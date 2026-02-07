/**
 * Tests for ModuleBrowser React component
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ModuleBrowser, ModuleInfo } from './ModuleBrowser.js';

describe('ModuleBrowser', () => {
  const sampleModules: ModuleInfo[] = [
    {
      name: 'Uppercase',
      description: 'Converts text to uppercase',
      version: '1.0',
      category: 'Text',
      inputs: { text: 'String' },
      outputs: { result: 'String' },
      examples: ['result = Uppercase(text)'],
    },
    {
      name: 'ParseJson',
      description: 'Parses JSON string to object',
      version: '1.0',
      category: 'Data',
      inputs: { json: 'String' },
      outputs: { value: 'Any' },
    },
    {
      name: 'WordCount',
      description: 'Counts words in text',
      version: '1.0',
      category: 'Text',
      inputs: { text: 'String' },
      outputs: { count: 'Int' },
    },
  ];

  it('renders nothing when not visible', () => {
    const { container } = render(
      <ModuleBrowser modules={sampleModules} visible={false} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders module list', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);
    expect(screen.getByText('Uppercase')).toBeTruthy();
    expect(screen.getByText('ParseJson')).toBeTruthy();
    expect(screen.getByText('WordCount')).toBeTruthy();
  });

  it('displays category buttons', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);
    expect(screen.getByText('All')).toBeTruthy();
    // Use getAllByText since 'Text' and 'Data' appear in both buttons and module cards
    expect(screen.getAllByText('Text').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Data').length).toBeGreaterThan(0);
  });

  it('filters by category', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);

    // Click on Text category button (the first one, which is a button)
    const textButtons = screen.getAllByText('Text');
    const textCategoryButton = textButtons.find(
      (el) => el.tagName === 'BUTTON'
    );
    if (textCategoryButton) {
      fireEvent.click(textCategoryButton);
    }

    // Text modules should be visible
    expect(screen.getByText('Uppercase')).toBeTruthy();
    expect(screen.getByText('WordCount')).toBeTruthy();
  });

  it('filters by search query', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);

    const searchInput = screen.getByPlaceholderText('Search modules...');
    fireEvent.change(searchInput, { target: { value: 'json' } });

    // Only ParseJson should match
    expect(screen.getByText('ParseJson')).toBeTruthy();
  });

  it('shows module details when clicked', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);

    // Click on Uppercase module - get the first one (in the list, not the detail panel)
    const uppercaseElements = screen.getAllByText('Uppercase');
    fireEvent.click(uppercaseElements[0]);

    // Module details should appear - use getAllByText since description appears twice
    expect(screen.getAllByText('Converts text to uppercase').length).toBeGreaterThan(0);
    expect(screen.getByText('Inputs')).toBeTruthy();
    expect(screen.getByText('Outputs')).toBeTruthy();
  });

  it('calls onInsertModule when insert button clicked', () => {
    const onInsertModule = vi.fn();
    render(
      <ModuleBrowser
        modules={sampleModules}
        visible={true}
        onInsertModule={onInsertModule}
      />
    );

    // Select a module first
    fireEvent.click(screen.getByText('Uppercase'));

    // Click insert button
    fireEvent.click(screen.getByText('Insert into Editor'));

    expect(onInsertModule).toHaveBeenCalledWith(
      'result = Uppercase(text)',
      expect.objectContaining({ name: 'Uppercase' })
    );
  });

  it('shows empty state when no modules match search', () => {
    render(<ModuleBrowser modules={sampleModules} visible={true} />);

    const searchInput = screen.getByPlaceholderText('Search modules...');
    fireEvent.change(searchInput, { target: { value: 'nonexistent' } });

    expect(screen.getByText('No modules match your search')).toBeTruthy();
  });

  it('shows empty state when no modules available', () => {
    render(<ModuleBrowser modules={[]} visible={true} />);
    expect(screen.getByText('No modules available')).toBeTruthy();
  });

  it('calls onClose when close button clicked', () => {
    const onClose = vi.fn();
    render(
      <ModuleBrowser
        modules={sampleModules}
        visible={true}
        onClose={onClose}
      />
    );

    // Find and click close button
    const closeButtons = screen.getAllByRole('button');
    const closeButton = closeButtons.find(btn =>
      btn.querySelector('svg path[d*="19 6.41"]')
    );
    if (closeButton) {
      fireEvent.click(closeButton);
      expect(onClose).toHaveBeenCalledTimes(1);
    }
  });
});
