/**
 * Constellation Dashboard - Modules Store
 *
 * Manages the module catalog for the module browser feature.
 * Loads modules from /api/v1/modules and supports filtering/searching.
 */

import { create } from 'zustand';

export interface ModuleInfo {
  name: string;
  description: string;
  version: string;
  category?: string;
  inputs: Record<string, string>;
  outputs: Record<string, string>;
  examples?: string[];
}

export interface ModulesState {
  // All available modules
  modules: ModuleInfo[];

  // Loading state
  isLoading: boolean;

  // Error message
  error: string | null;

  // Search/filter query
  searchQuery: string;

  // Selected category filter
  selectedCategory: string | null;

  // Currently selected module (for detail view)
  selectedModule: ModuleInfo | null;

  // Computed: filtered modules based on search and category
  filteredModules: () => ModuleInfo[];

  // Computed: unique categories from modules
  categories: () => string[];

  // Actions
  setModules: (modules: ModuleInfo[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  setSearchQuery: (query: string) => void;
  setSelectedCategory: (category: string | null) => void;
  setSelectedModule: (module: ModuleInfo | null) => void;
  loadModules: () => Promise<void>;
}

export const useModulesStore = create<ModulesState>((set, get) => ({
  // Initial state
  modules: [],
  isLoading: false,
  error: null,
  searchQuery: '',
  selectedCategory: null,
  selectedModule: null,

  // Computed: filtered modules
  filteredModules: () => {
    const { modules, searchQuery, selectedCategory } = get();
    let filtered = modules;

    // Filter by category
    if (selectedCategory) {
      filtered = filtered.filter((m) => m.category === selectedCategory);
    }

    // Filter by search query
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(
        (m) =>
          m.name.toLowerCase().includes(query) ||
          m.description.toLowerCase().includes(query)
      );
    }

    return filtered;
  },

  // Computed: unique categories
  categories: () => {
    const { modules } = get();
    const cats = new Set<string>();
    for (const m of modules) {
      if (m.category) {
        cats.add(m.category);
      }
    }
    return Array.from(cats).sort();
  },

  // Actions
  setModules: (modules) => set({ modules }),

  setLoading: (loading) => set({ isLoading: loading }),

  setError: (error) => set({ error }),

  setSearchQuery: (query) => set({ searchQuery: query }),

  setSelectedCategory: (category) => set({ selectedCategory: category }),

  setSelectedModule: (module) => set({ selectedModule: module }),

  loadModules: async () => {
    set({ isLoading: true, error: null });

    try {
      const response = await fetch('/api/v1/modules');
      if (!response.ok) {
        throw new Error(`Failed to load modules: ${response.statusText}`);
      }

      const data = await response.json();
      const modules: ModuleInfo[] = data.modules || data || [];
      set({ modules, isLoading: false });
    } catch (error) {
      set({
        error: (error as Error).message,
        isLoading: false,
      });
    }
  },
}));
