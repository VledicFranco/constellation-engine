/**
 * Constellation Dashboard - App Store
 *
 * Global application state including current view, selected script, and loading state.
 */

import { create } from 'zustand';

export type ViewType = 'scripts' | 'pipelines' | 'history';

export interface AppState {
  // Current view
  currentView: ViewType;

  // Current script path (selected in file browser)
  currentScriptPath: string | null;

  // Status bar message
  status: string;

  // Global loading state
  isLoading: boolean;
  loadingMessage: string;

  // Connection status
  isConnected: boolean;

  // Actions
  setView: (view: ViewType) => void;
  setScriptPath: (path: string | null) => void;
  setStatus: (status: string) => void;
  setLoading: (loading: boolean, message?: string) => void;
  setConnected: (connected: boolean) => void;
}

export const useAppStore = create<AppState>((set) => ({
  // Initial state
  currentView: 'scripts',
  currentScriptPath: null,
  status: 'Ready',
  isLoading: false,
  loadingMessage: 'Loading...',
  isConnected: true,

  // Actions
  setView: (view) => set({ currentView: view }),

  setScriptPath: (path) => set({ currentScriptPath: path }),

  setStatus: (status) => set({ status }),

  setLoading: (loading, message = 'Loading...') =>
    set({ isLoading: loading, loadingMessage: message }),

  setConnected: (connected) => set({ isConnected: connected }),
}));
