/**
 * Constellation Dashboard - Editor Store
 *
 * Manages code editor state including source content, compilation status,
 * errors, and input parameters derived from the DAG.
 */

import { create } from 'zustand';

export interface InputParam {
  name: string;
  paramType: string;
  required: boolean;
  defaultValue?: unknown;
}

export interface CompileError {
  code?: string;
  message: string;
  line?: number;
  column?: number;
  sourceContext?: string[];
  suggestion?: string;
}

export interface EditorState {
  // Current source code
  source: string;

  // Whether source has unsaved changes
  isDirty: boolean;

  // Compilation status
  compileStatus: 'idle' | 'compiling' | 'success' | 'error';

  // Compilation errors
  errors: CompileError[];

  // Input parameters extracted from DAG
  inputs: InputParam[];

  // Current input values (for form)
  inputValues: Record<string, unknown>;

  // DAG visualization IR (last successful compile)
  dagVizIR: unknown | null;

  // Actions
  setSource: (source: string) => void;
  setDirty: (isDirty: boolean) => void;
  setCompileStatus: (status: EditorState['compileStatus']) => void;
  setErrors: (errors: CompileError[]) => void;
  setInputs: (inputs: InputParam[]) => void;
  setInputValue: (name: string, value: unknown) => void;
  setAllInputValues: (values: Record<string, unknown>) => void;
  setDagVizIR: (dagVizIR: unknown | null) => void;
  reset: () => void;
}

export const useEditorStore = create<EditorState>((set) => ({
  // Initial state
  source: '',
  isDirty: false,
  compileStatus: 'idle',
  errors: [],
  inputs: [],
  inputValues: {},
  dagVizIR: null,

  // Actions
  setSource: (source) => set({ source, isDirty: true }),

  setDirty: (isDirty) => set({ isDirty }),

  setCompileStatus: (status) => set({ compileStatus: status }),

  setErrors: (errors) => set({ errors }),

  setInputs: (inputs) =>
    set((prev) => {
      // Preserve existing input values for matching names
      const newInputValues: Record<string, unknown> = {};
      for (const input of inputs) {
        if (input.name in prev.inputValues) {
          newInputValues[input.name] = prev.inputValues[input.name];
        } else if (input.defaultValue !== undefined) {
          newInputValues[input.name] = input.defaultValue;
        }
      }
      return { inputs, inputValues: newInputValues };
    }),

  setInputValue: (name, value) =>
    set((prev) => ({
      inputValues: {
        ...prev.inputValues,
        [name]: value,
      },
    })),

  setAllInputValues: (values) => set({ inputValues: values }),

  setDagVizIR: (dagVizIR) => set({ dagVizIR }),

  reset: () =>
    set({
      source: '',
      isDirty: false,
      compileStatus: 'idle',
      errors: [],
      inputs: [],
      inputValues: {},
      dagVizIR: null,
    }),
}));
