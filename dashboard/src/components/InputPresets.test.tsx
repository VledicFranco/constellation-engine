/**
 * Tests for InputPresets React component
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { InputPresets } from './InputPresets.js';

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('InputPresets', () => {
  const scriptPath = '/scripts/test.cst';
  const storageKey = `constellation:presets:${scriptPath}`;

  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  it('renders presets button', () => {
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );
    expect(screen.getByText('Presets')).toBeTruthy();
  });

  it('shows dropdown when button clicked', () => {
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    expect(screen.getByText('Saved Presets')).toBeTruthy();
  });

  it('shows empty state when no presets', () => {
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    expect(screen.getByText('No saved presets')).toBeTruthy();
  });

  it('loads presets from localStorage on mount', () => {
    const presets = [
      { name: 'Test Preset', inputs: { text: 'hello' }, createdAt: Date.now() },
    ];
    localStorageMock.setItem(storageKey, JSON.stringify(presets));

    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    expect(screen.getByText('Test Preset')).toBeTruthy();
  });

  it('shows preset count badge when presets exist', () => {
    const presets = [
      { name: 'Preset 1', inputs: { text: 'a' }, createdAt: Date.now() },
      { name: 'Preset 2', inputs: { text: 'b' }, createdAt: Date.now() },
    ];
    localStorageMock.setItem(storageKey, JSON.stringify(presets));

    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    expect(screen.getByText('2')).toBeTruthy();
  });

  it('calls onLoadPreset when preset clicked', () => {
    const presets = [
      { name: 'Test Preset', inputs: { text: 'hello' }, createdAt: Date.now() },
    ];
    localStorageMock.setItem(storageKey, JSON.stringify(presets));

    const onLoadPreset = vi.fn();
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={onLoadPreset}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    fireEvent.click(screen.getByText('Test Preset'));

    expect(onLoadPreset).toHaveBeenCalledWith({ text: 'hello' });
  });

  it('shows save dialog when save button clicked', () => {
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    fireEvent.click(screen.getByText('Save Current Inputs'));

    expect(screen.getByPlaceholderText('Preset name')).toBeTruthy();
  });

  it('calls onSavePreset when save confirmed', () => {
    const onSavePreset = vi.fn();
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={onSavePreset}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    fireEvent.click(screen.getByText('Save Current Inputs'));

    const input = screen.getByPlaceholderText('Preset name');
    fireEvent.change(input, { target: { value: 'My Preset' } });
    fireEvent.click(screen.getByText('Save'));

    expect(onSavePreset).toHaveBeenCalledWith('My Preset');
  });

  it('cancels save dialog', () => {
    render(
      <InputPresets
        scriptPath={scriptPath}
        onLoadPreset={vi.fn()}
        onSavePreset={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Presets'));
    fireEvent.click(screen.getByText('Save Current Inputs'));
    fireEvent.click(screen.getByText('Cancel'));

    // Should be back to main dropdown view
    expect(screen.getByText('Save Current Inputs')).toBeTruthy();
  });
});
