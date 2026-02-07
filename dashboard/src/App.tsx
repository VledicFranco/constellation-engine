/**
 * Constellation Dashboard - React Application Shell
 *
 * Main React component that integrates the new IDE features.
 * Uses React portals to render into specific DOM locations.
 */

import { useState, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { ErrorBanner } from './components/ErrorBanner.js';
import { ErrorPanel, CompileError } from './components/ErrorPanel.js';
import { InputPresets } from './components/InputPresets.js';
import { ModuleBrowser, ModuleInfo } from './components/ModuleBrowser.js';
import { ValueInspector, NodeValue } from './components/ValueInspector.js';

export interface AppProps {
  // Callbacks to integrate with vanilla JS dashboard
  onInsertCode?: (code: string) => void;
  onGotoLine?: (line: number, column?: number) => void;
  onLoadPreset?: (inputs: Record<string, unknown>) => void;
  onSavePreset?: (name: string) => void;
}

export function App({ onInsertCode, onGotoLine, onLoadPreset, onSavePreset }: AppProps) {
  // Global error state
  const [globalError, setGlobalError] = useState<string | null>(null);

  // Module browser state
  const [modules, setModules] = useState<ModuleInfo[]>([]);
  const [showModuleBrowser, setShowModuleBrowser] = useState(false);

  // Error panel state
  const [compileErrors, setCompileErrors] = useState<CompileError[]>([]);

  // Value inspector state
  const [selectedNodeValue, setSelectedNodeValue] = useState<NodeValue | null>(null);
  const [showValueInspector, setShowValueInspector] = useState(false);

  // Current script path for presets
  const [currentScriptPath, setCurrentScriptPath] = useState<string>('');

  // Mount points
  const [errorBannerMount, setErrorBannerMount] = useState<HTMLElement | null>(null);
  const [inputPresetsMount, setInputPresetsMount] = useState<HTMLElement | null>(null);
  const [errorPanelMount, setErrorPanelMount] = useState<HTMLElement | null>(null);

  // Find mount points on load
  useEffect(() => {
    setErrorBannerMount(document.getElementById('error-banner-mount'));
    setInputPresetsMount(document.getElementById('input-presets-mount'));
    setErrorPanelMount(document.getElementById('error-panel-mount'));
  }, []);

  // Load modules on mount
  useEffect(() => {
    loadModules();
  }, []);

  // Set up module browser button handler
  useEffect(() => {
    const btn = document.getElementById('module-browser-btn');
    if (btn) {
      const handler = () => setShowModuleBrowser(true);
      btn.addEventListener('click', handler);
      return () => btn.removeEventListener('click', handler);
    }
  }, []);

  // Expose React state setters to vanilla JS via window
  useEffect(() => {
    const reactBridge = {
      setGlobalError,
      setCompileErrors: (errors: CompileError[]) => {
        setCompileErrors(errors);
      },
      setCompileErrorsFromStrings: (errors: string[]) => {
        // Convert string errors to CompileError format
        const compileErrors: CompileError[] = errors.map((msg, index) => {
          // Try to parse line:col from error message
          const match = msg.match(/line (\d+)(?::(\d+))?/i);
          return {
            code: `E${String(index + 1).padStart(3, '0')}`,
            message: msg,
            line: match ? parseInt(match[1], 10) : undefined,
            column: match && match[2] ? parseInt(match[2], 10) : undefined,
          };
        });
        setCompileErrors(compileErrors);
      },
      clearCompileErrors: () => setCompileErrors([]),
      setSelectedNodeValue: (value: NodeValue | null) => {
        setSelectedNodeValue(value);
        setShowValueInspector(value !== null);
      },
      setCurrentScriptPath,
      showModuleBrowser: () => setShowModuleBrowser(true),
      hideModuleBrowser: () => setShowModuleBrowser(false),
      toggleModuleBrowser: () => setShowModuleBrowser((prev) => !prev),
    };

    (window as Window & { reactBridge?: typeof reactBridge }).reactBridge = reactBridge;

    return () => {
      delete (window as Window & { reactBridge?: typeof reactBridge }).reactBridge;
    };
  }, []);

  const loadModules = async () => {
    try {
      const response = await fetch('/api/v1/modules');
      if (response.ok) {
        const data = await response.json();
        // Transform API response to ModuleInfo format
        const moduleList: ModuleInfo[] = (data.modules || []).map(
          (m: {
            name: string;
            description?: string;
            version?: string;
            category?: string;
            inputs?: Record<string, string>;
            outputs?: Record<string, string>;
            examples?: string[];
          }) => ({
            name: m.name,
            description: m.description || '',
            version: m.version || '1.0',
            category: m.category,
            inputs: m.inputs || {},
            outputs: m.outputs || {},
            examples: m.examples || [],
          })
        );
        setModules(moduleList);
      }
    } catch (error) {
      console.error('Failed to load modules:', error);
    }
  };

  const handleDismissError = useCallback(() => {
    setGlobalError(null);
  }, []);

  const handleInsertModule = useCallback(
    (code: string, _module: ModuleInfo) => {
      onInsertCode?.(code);
      setShowModuleBrowser(false);
    },
    [onInsertCode]
  );

  const handleGotoLine = useCallback(
    (line: number, column?: number) => {
      onGotoLine?.(line, column);
    },
    [onGotoLine]
  );

  const handleLoadPreset = useCallback(
    (inputs: Record<string, unknown>) => {
      onLoadPreset?.(inputs);
    },
    [onLoadPreset]
  );

  const handleSavePreset = useCallback(
    (name: string) => {
      onSavePreset?.(name);
    },
    [onSavePreset]
  );

  const handleCloseValueInspector = useCallback(() => {
    setShowValueInspector(false);
    setSelectedNodeValue(null);
  }, []);

  return (
    <>
      {/* Global Error Banner - Portal into header area */}
      {errorBannerMount &&
        createPortal(
          <ErrorBanner
            message={globalError || ''}
            visible={globalError !== null}
            severity="error"
            dismissable
            onDismiss={handleDismissError}
          />,
          errorBannerMount
        )}

      {/* Input Presets - Portal into inputs panel header */}
      {inputPresetsMount &&
        currentScriptPath &&
        createPortal(
          <InputPresets
            scriptPath={currentScriptPath}
            onLoadPreset={handleLoadPreset}
            onSavePreset={handleSavePreset}
          />,
          inputPresetsMount
        )}

      {/* Error Panel - Portal into editor area */}
      {errorPanelMount &&
        compileErrors.length > 0 &&
        createPortal(
          <ErrorPanel errors={compileErrors} expanded={true} onGotoLine={handleGotoLine} />,
          errorPanelMount
        )}

      {/* Module Browser Overlay - Full screen modal */}
      {showModuleBrowser && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.6)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              setShowModuleBrowser(false);
            }
          }}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '900px',
              height: '80vh',
              background: 'var(--bg-primary, #0d1117)',
              borderRadius: 'var(--radius-lg, 8px)',
              border: '1px solid var(--border-primary, #30363d)',
              boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
              overflow: 'hidden',
            }}
          >
            <ModuleBrowser
              modules={modules}
              visible={true}
              onClose={() => setShowModuleBrowser(false)}
              onInsertModule={handleInsertModule}
            />
          </div>
        </div>
      )}

      {/* Value Inspector - Fixed position overlay */}
      {showValueInspector && selectedNodeValue && (
        <div
          style={{
            position: 'fixed',
            top: '80px',
            right: '20px',
            width: '380px',
            zIndex: 500,
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.3)',
            borderRadius: 'var(--radius-md, 6px)',
          }}
        >
          <ValueInspector
            nodeValue={selectedNodeValue}
            visible={true}
            onClose={handleCloseValueInspector}
          />
        </div>
      )}
    </>
  );
}

export default App;
