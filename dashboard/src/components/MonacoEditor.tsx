/**
 * Constellation Dashboard - Monaco Editor Component
 *
 * A React component that wraps Monaco Editor with Constellation language support.
 * Lazy loads Monaco for optimal bundle size and connects to the LSP WebSocket.
 */

import { useState, useEffect, useRef, useCallback, CSSProperties } from 'react';

export interface MonacoEditorProps {
  value: string;
  onChange?: (value: string) => void;
  onSave?: (value: string) => void;
  language?: string;
  theme?: string;
  readOnly?: boolean;
  height?: string | number;
  onEditorReady?: (editor: unknown) => void;
}

const styles: Record<string, CSSProperties> = {
  container: {
    width: '100%',
    height: '100%',
    position: 'relative',
  },
  loading: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    color: 'var(--text-muted, #6e7681)',
    fontSize: '14px',
    gap: '8px',
  },
  spinner: {
    width: '20px',
    height: '20px',
    border: '2px solid var(--border-primary, #30363d)',
    borderTopColor: 'var(--accent-primary, #58a6ff)',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  editorContainer: {
    width: '100%',
    height: '100%',
  },
  error: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    color: 'var(--status-failed, #f85149)',
    fontSize: '14px',
    padding: '20px',
    textAlign: 'center',
  },
};

// Constellation language definition for Monaco
const constellationLanguageConfig = {
  comments: {
    lineComment: '#',
  },
  brackets: [
    ['{', '}'],
    ['[', ']'],
    ['(', ')'],
  ],
  autoClosingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
  surroundingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
};

const constellationTokensProvider = {
  tokenizer: {
    root: [
      // Comments
      [/#.*$/, 'comment'],

      // Keywords
      [/\b(in|out|when|module|import|from|as|true|false|null)\b/, 'keyword'],

      // Type annotations
      [/:\s*(String|Int|Float|Boolean|List|Map|Any|Option)\b/, 'type'],

      // Module calls (PascalCase identifiers followed by parentheses)
      [/[A-Z][a-zA-Z0-9_]*(?=\s*\()/, 'function'],

      // Variable assignments
      [/[a-z_][a-zA-Z0-9_]*(?=\s*=)/, 'variable'],

      // Operators
      [/[=+\-*/<>!&|?:]+/, 'operator'],

      // Strings
      [/"([^"\\]|\\.)*"/, 'string'],
      [/'([^'\\]|\\.)*'/, 'string'],

      // Numbers
      [/\b\d+\.?\d*\b/, 'number'],

      // Identifiers
      [/[a-zA-Z_][a-zA-Z0-9_]*/, 'identifier'],

      // Brackets
      [/[{}()\[\]]/, 'delimiter.bracket'],

      // Punctuation
      [/[,.]/, 'delimiter'],
    ],
  },
};

// Dark theme matching dashboard
const constellationTheme = {
  base: 'vs-dark' as const,
  inherit: true,
  rules: [
    { token: 'comment', foreground: '6e7681', fontStyle: 'italic' },
    { token: 'keyword', foreground: 'ff7b72' },
    { token: 'type', foreground: '79c0ff' },
    { token: 'function', foreground: 'd2a8ff' },
    { token: 'variable', foreground: 'ffa657' },
    { token: 'string', foreground: 'a5d6ff' },
    { token: 'number', foreground: '79c0ff' },
    { token: 'operator', foreground: 'ff7b72' },
    { token: 'identifier', foreground: 'c9d1d9' },
    { token: 'delimiter.bracket', foreground: '8b949e' },
    { token: 'delimiter', foreground: '8b949e' },
  ],
  colors: {
    'editor.background': '#0d1117',
    'editor.foreground': '#c9d1d9',
    'editor.lineHighlightBackground': '#161b22',
    'editor.selectionBackground': '#264f78',
    'editorCursor.foreground': '#58a6ff',
    'editorLineNumber.foreground': '#6e7681',
    'editorLineNumber.activeForeground': '#c9d1d9',
    'editor.inactiveSelectionBackground': '#264f7855',
  },
};

export function MonacoEditor({
  value,
  onChange,
  onSave,
  language = 'constellation',
  theme = 'constellation-dark',
  readOnly = false,
  height = '100%',
  onEditorReady,
}: MonacoEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<unknown>(null);
  const monacoRef = useRef<typeof import('monaco-editor') | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Initialize Monaco editor
  useEffect(() => {
    let disposed = false;

    const initMonaco = async () => {
      try {
        // Dynamically import Monaco
        const monaco = await import('monaco-editor');

        if (disposed) return;

        monacoRef.current = monaco;

        // Register Constellation language
        if (!monaco.languages.getLanguages().some((lang) => lang.id === 'constellation')) {
          monaco.languages.register({ id: 'constellation' });
          monaco.languages.setLanguageConfiguration('constellation', constellationLanguageConfig);
          monaco.languages.setMonarchTokensProvider('constellation', constellationTokensProvider);
        }

        // Register theme
        monaco.editor.defineTheme('constellation-dark', constellationTheme);

        // Create editor
        if (containerRef.current) {
          const editor = monaco.editor.create(containerRef.current, {
            value,
            language,
            theme,
            readOnly,
            automaticLayout: true,
            minimap: { enabled: false },
            fontSize: 13,
            fontFamily: "'SF Mono', 'Consolas', 'Monaco', monospace",
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            tabSize: 2,
            insertSpaces: true,
            renderWhitespace: 'selection',
            padding: { top: 8, bottom: 8 },
          });

          editorRef.current = editor;

          // Handle content changes
          editor.onDidChangeModelContent(() => {
            const newValue = editor.getValue();
            onChange?.(newValue);
          });

          // Handle save shortcut (Ctrl/Cmd+S)
          editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
            onSave?.(editor.getValue());
          });

          onEditorReady?.(editor);
        }

        setLoading(false);
      } catch (err) {
        console.error('Failed to load Monaco editor:', err);
        setError('Failed to load editor. Please refresh the page.');
        setLoading(false);
      }
    };

    initMonaco();

    return () => {
      disposed = true;
      if (editorRef.current && monacoRef.current) {
        (editorRef.current as { dispose: () => void }).dispose();
      }
    };
  }, []);

  // Update value when prop changes
  useEffect(() => {
    if (editorRef.current && monacoRef.current) {
      const editor = editorRef.current as { getValue: () => string; setValue: (v: string) => void };
      if (editor.getValue() !== value) {
        editor.setValue(value);
      }
    }
  }, [value]);

  // Insert text at cursor position
  const insertText = useCallback((text: string) => {
    if (editorRef.current && monacoRef.current) {
      const editor = editorRef.current as {
        getSelection: () => { startLineNumber: number; startColumn: number } | null;
        executeEdits: (source: string, edits: unknown[]) => void;
        focus: () => void;
      };
      const selection = editor.getSelection();
      if (selection) {
        const range = {
          startLineNumber: selection.startLineNumber,
          startColumn: selection.startColumn,
          endLineNumber: selection.startLineNumber,
          endColumn: selection.startColumn,
        };
        editor.executeEdits('insert', [{ range, text }]);
        editor.focus();
      }
    }
  }, []);

  // Go to specific line/column
  const gotoLine = useCallback((line: number, column?: number) => {
    if (editorRef.current && monacoRef.current) {
      const editor = editorRef.current as {
        revealLineInCenter: (line: number) => void;
        setPosition: (pos: { lineNumber: number; column: number }) => void;
        focus: () => void;
      };
      editor.revealLineInCenter(line);
      editor.setPosition({ lineNumber: line, column: column || 1 });
      editor.focus();
    }
  }, []);

  // Expose methods via window for vanilla JS bridge
  useEffect(() => {
    const monacoMethods = {
      insertText,
      gotoLine,
      getValue: () => {
        if (editorRef.current) {
          return (editorRef.current as { getValue: () => string }).getValue();
        }
        return '';
      },
      setValue: (v: string) => {
        if (editorRef.current) {
          (editorRef.current as { setValue: (v: string) => void }).setValue(v);
        }
      },
    };

    (window as Window & { monacoEditor?: typeof monacoMethods }).monacoEditor = monacoMethods;

    return () => {
      delete (window as Window & { monacoEditor?: typeof monacoMethods }).monacoEditor;
    };
  }, [insertText, gotoLine]);

  if (error) {
    return <div style={{ ...styles.container, ...styles.error }}>{error}</div>;
  }

  return (
    <div style={{ ...styles.container, height }}>
      {loading && (
        <div style={styles.loading}>
          <div style={styles.spinner} />
          Loading editor...
        </div>
      )}
      <div
        ref={containerRef}
        style={{
          ...styles.editorContainer,
          display: loading ? 'none' : 'block',
        }}
      />
    </div>
  );
}

export default MonacoEditor;
