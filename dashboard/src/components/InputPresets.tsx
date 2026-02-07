/**
 * Constellation Dashboard - Input Presets Component
 *
 * A React component that saves and loads input presets to LocalStorage.
 * Presets are scoped per script path.
 */

import { useState, useEffect, useCallback, useRef, CSSProperties } from 'react';
import { createPortal } from 'react-dom';

export interface InputPreset {
  name: string;
  inputs: Record<string, unknown>;
  createdAt: number;
}

export interface InputPresetsProps {
  scriptPath: string;
  onLoadPreset: (inputs: Record<string, unknown>) => void;
  onSavePreset: (name: string) => void;
}

const styles: Record<string, CSSProperties> = {
  container: {
    display: 'inline-block',
    position: 'relative',
  },
  presetsBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-xs, 4px)',
    padding: 'var(--spacing-xs, 4px) var(--spacing-sm, 8px)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: 'var(--radius-sm, 4px)',
    background: 'var(--bg-tertiary, #21262d)',
    color: 'var(--text-secondary, #8b949e)',
    fontSize: '12px',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  count: {
    background: 'var(--bg-hover, #30363d)',
    padding: '0 6px',
    borderRadius: '10px',
    fontSize: '10px',
  },
  dropdown: {
    position: 'absolute',
    top: '100%',
    left: 0,
    marginTop: '4px',
    minWidth: '200px',
    maxWidth: '300px',
    background: 'var(--bg-secondary, #161b22)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: 'var(--radius-md, 6px)',
    boxShadow: 'var(--shadow-md, 0 4px 8px rgba(0, 0, 0, 0.4))',
    zIndex: 100,
    overflow: 'hidden',
  },
  dropdownHeader: {
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    fontSize: '11px',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    color: 'var(--text-muted, #6e7681)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  presetList: {
    maxHeight: '200px',
    overflowY: 'auto',
  },
  presetItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    cursor: 'pointer',
    transition: 'background 0.15s ease',
  },
  presetName: {
    fontSize: '13px',
    color: 'var(--text-primary, #f0f6fc)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  presetDelete: {
    background: 'none',
    border: 'none',
    padding: '2px',
    cursor: 'pointer',
    color: 'var(--text-muted, #6e7681)',
    opacity: 0,
    transition: 'opacity 0.15s ease',
  },
  emptyState: {
    padding: 'var(--spacing-md, 12px)',
    textAlign: 'center',
    color: 'var(--text-muted, #6e7681)',
    fontSize: '12px',
  },
  saveAction: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-xs, 4px)',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderTop: '1px solid var(--border-primary, #30363d)',
    cursor: 'pointer',
    color: 'var(--accent-primary, #58a6ff)',
    fontSize: '13px',
    transition: 'background 0.15s ease',
  },
  saveDialog: {
    padding: 'var(--spacing-md, 12px)',
    borderTop: '1px solid var(--border-primary, #30363d)',
  },
  saveInput: {
    width: '100%',
    padding: 'var(--spacing-sm, 8px)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: 'var(--radius-sm, 4px)',
    background: 'var(--bg-tertiary, #21262d)',
    color: 'var(--text-primary, #f0f6fc)',
    fontSize: '13px',
    marginBottom: 'var(--spacing-sm, 8px)',
    boxSizing: 'border-box',
  },
  saveDialogActions: {
    display: 'flex',
    gap: 'var(--spacing-sm, 8px)',
    justifyContent: 'flex-end',
  },
  btnCancel: {
    padding: 'var(--spacing-xs, 4px) var(--spacing-sm, 8px)',
    borderRadius: 'var(--radius-sm, 4px)',
    fontSize: '12px',
    cursor: 'pointer',
    background: 'var(--bg-tertiary, #21262d)',
    border: '1px solid var(--border-primary, #30363d)',
    color: 'var(--text-secondary, #8b949e)',
  },
  btnSave: {
    padding: 'var(--spacing-xs, 4px) var(--spacing-sm, 8px)',
    borderRadius: 'var(--radius-sm, 4px)',
    fontSize: '12px',
    cursor: 'pointer',
    background: 'var(--accent-highlight, #1f6feb)',
    border: '1px solid var(--accent-highlight, #1f6feb)',
    color: 'var(--text-primary, #f0f6fc)',
  },
};

function getStorageKey(scriptPath: string): string {
  return `constellation:presets:${scriptPath}`;
}

export function InputPresets({ scriptPath, onLoadPreset, onSavePreset }: InputPresetsProps) {
  const [presets, setPresets] = useState<InputPreset[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [newPresetName, setNewPresetName] = useState('');
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Load presets from localStorage when scriptPath changes
  useEffect(() => {
    if (!scriptPath) {
      setPresets([]);
      return;
    }

    try {
      const stored = localStorage.getItem(getStorageKey(scriptPath));
      if (stored) {
        setPresets(JSON.parse(stored));
      } else {
        setPresets([]);
      }
    } catch (error) {
      console.error('Failed to load presets:', error);
      setPresets([]);
    }
  }, [scriptPath]);

  const savePresetsToStorage = useCallback(
    (newPresets: InputPreset[]) => {
      try {
        localStorage.setItem(getStorageKey(scriptPath), JSON.stringify(newPresets));
      } catch (error) {
        console.error('Failed to save presets:', error);
      }
    },
    [scriptPath]
  );

  const handleLoadPreset = (preset: InputPreset) => {
    setShowDropdown(false);
    onLoadPreset(preset.inputs);
  };

  const handleDeletePreset = (index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    const newPresets = presets.filter((_, i) => i !== index);
    setPresets(newPresets);
    savePresetsToStorage(newPresets);
  };

  const toggleDropdown = () => {
    if (!showDropdown && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect();
      setDropdownPosition({
        top: rect.bottom + 4,
        left: rect.left,
      });
    }
    setShowDropdown(!showDropdown);
    setShowSaveDialog(false);
  };

  const openSaveDialog = () => {
    setShowSaveDialog(true);
    setNewPresetName(`Preset ${presets.length + 1}`);
  };

  const cancelSaveDialog = () => {
    setShowSaveDialog(false);
    setNewPresetName('');
  };

  const handleSave = () => {
    if (!newPresetName.trim()) return;
    onSavePreset(newPresetName);
    setShowSaveDialog(false);
    setNewPresetName('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSave();
    } else if (e.key === 'Escape') {
      cancelSaveDialog();
    }
  };

  // Public method to add a preset (called from parent)
  const addPreset = useCallback(
    (name: string, inputs: Record<string, unknown>) => {
      const preset: InputPreset = {
        name,
        inputs,
        createdAt: Date.now(),
      };
      const newPresets = [...presets, preset];
      setPresets(newPresets);
      savePresetsToStorage(newPresets);
    },
    [presets, savePresetsToStorage]
  );

  // Expose addPreset via ref or context if needed
  // For now, parent can call onSavePreset which triggers addPreset flow

  // Close dropdown when clicking outside
  useEffect(() => {
    if (!showDropdown) return;

    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      // Check if click is outside both button and dropdown
      if (buttonRef.current && !buttonRef.current.contains(target)) {
        const dropdown = document.getElementById('presets-dropdown-portal');
        if (!dropdown || !dropdown.contains(target)) {
          setShowDropdown(false);
          setShowSaveDialog(false);
        }
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showDropdown]);

  return (
    <div style={styles.container}>
      <button
        ref={buttonRef}
        style={styles.presetsBtn}
        onClick={toggleDropdown}
        onMouseEnter={(e) => {
          e.currentTarget.style.borderColor = 'var(--accent-primary, #58a6ff)';
          e.currentTarget.style.color = 'var(--text-primary, #f0f6fc)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.borderColor = 'var(--border-primary, #30363d)';
          e.currentTarget.style.color = 'var(--text-secondary, #8b949e)';
        }}
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
          <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" />
        </svg>
        Presets
        {presets.length > 0 && <span style={styles.count}>{presets.length}</span>}
      </button>

      {showDropdown &&
        createPortal(
          <div
            id="presets-dropdown-portal"
            style={{
              ...styles.dropdown,
              position: 'fixed',
              top: dropdownPosition.top,
              left: dropdownPosition.left,
            }}
          >
          <div style={styles.dropdownHeader}>Saved Presets</div>

          {presets.length === 0 ? (
            <div style={styles.emptyState}>No saved presets</div>
          ) : (
            <div style={styles.presetList}>
              {presets.map((preset, index) => (
                <div
                  key={index}
                  style={{
                    ...styles.presetItem,
                    background: hoveredIndex === index ? 'var(--bg-hover, #30363d)' : undefined,
                  }}
                  onClick={() => handleLoadPreset(preset)}
                  onMouseEnter={() => setHoveredIndex(index)}
                  onMouseLeave={() => setHoveredIndex(null)}
                >
                  <span style={styles.presetName}>{preset.name}</span>
                  <button
                    style={{
                      ...styles.presetDelete,
                      opacity: hoveredIndex === index ? 1 : 0,
                    }}
                    onClick={(e) => handleDeletePreset(index, e)}
                    title="Delete preset"
                  >
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                      <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}

          {showSaveDialog ? (
            <div style={styles.saveDialog}>
              <input
                type="text"
                placeholder="Preset name"
                value={newPresetName}
                onChange={(e) => setNewPresetName(e.target.value)}
                onKeyDown={handleKeyDown}
                style={styles.saveInput}
                autoFocus
              />
              <div style={styles.saveDialogActions}>
                <button style={styles.btnCancel} onClick={cancelSaveDialog}>
                  Cancel
                </button>
                <button
                  style={{
                    ...styles.btnSave,
                    opacity: !newPresetName.trim() ? 0.5 : 1,
                    cursor: !newPresetName.trim() ? 'not-allowed' : 'pointer',
                  }}
                  onClick={handleSave}
                  disabled={!newPresetName.trim()}
                >
                  Save
                </button>
              </div>
            </div>
          ) : (
            <div
              style={styles.saveAction}
              onClick={openSaveDialog}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-hover, #30363d)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
              </svg>
              Save Current Inputs
            </div>
          )}
        </div>,
          document.body
        )}
    </div>
  );
}

export default InputPresets;
