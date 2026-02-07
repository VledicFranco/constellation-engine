/**
 * Constellation Dashboard - Module Browser Component
 *
 * A React component for browsing and searching the module catalog.
 * Allows inserting module calls into the code editor.
 */

import { useState, useMemo, CSSProperties } from 'react';

export interface ModuleInfo {
  name: string;
  description: string;
  version: string;
  category?: string;
  inputs: Record<string, string>;
  outputs: Record<string, string>;
  examples?: string[];
}

export interface ModuleBrowserProps {
  modules: ModuleInfo[];
  visible?: boolean;
  onClose?: () => void;
  onInsertModule?: (code: string, module: ModuleInfo) => void;
}

const styles: Record<string, CSSProperties> = {
  browser: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
    background: 'var(--bg-secondary, #161b22)',
    borderRadius: 'var(--radius-md, 6px)',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  headerTitle: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-sm, 8px)',
    fontSize: '12px',
    fontWeight: 600,
    color: 'var(--text-secondary, #8b949e)',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
  },
  closeBtn: {
    background: 'none',
    border: 'none',
    padding: '4px',
    cursor: 'pointer',
    color: 'var(--text-muted, #6e7681)',
    borderRadius: 'var(--radius-sm, 4px)',
    transition: 'all 0.15s ease',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchContainer: {
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  searchInput: {
    width: '100%',
    padding: 'var(--spacing-sm, 8px)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: 'var(--radius-sm, 4px)',
    background: 'var(--bg-tertiary, #21262d)',
    color: 'var(--text-primary, #f0f6fc)',
    fontSize: '13px',
    boxSizing: 'border-box',
  },
  categories: {
    display: 'flex',
    gap: 'var(--spacing-xs, 4px)',
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    overflowX: 'auto',
    borderBottom: '1px solid var(--border-primary, #30363d)',
  },
  categoryBtn: {
    padding: 'var(--spacing-xs, 4px) var(--spacing-sm, 8px)',
    border: '1px solid var(--border-primary, #30363d)',
    borderRadius: 'var(--radius-sm, 4px)',
    background: 'var(--bg-tertiary, #21262d)',
    color: 'var(--text-secondary, #8b949e)',
    fontSize: '11px',
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    transition: 'all 0.15s ease',
  },
  categoryBtnActive: {
    background: 'var(--accent-primary, #58a6ff)',
    borderColor: 'var(--accent-primary, #58a6ff)',
    color: 'var(--bg-primary, #0d1117)',
  },
  content: {
    flex: 1,
    display: 'flex',
    overflow: 'hidden',
  },
  moduleList: {
    flex: 1,
    overflowY: 'auto',
    padding: 'var(--spacing-xs, 4px)',
  },
  moduleDetail: {
    width: '280px',
    borderLeft: '1px solid var(--border-primary, #30363d)',
    overflowY: 'auto',
  },
  emptyState: {
    padding: 'var(--spacing-lg, 24px)',
    textAlign: 'center',
    color: 'var(--text-muted, #6e7681)',
  },
};

export function ModuleBrowser({ modules, visible = true, onClose, onInsertModule }: ModuleBrowserProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedModule, setSelectedModule] = useState<ModuleInfo | null>(null);

  const categories = useMemo(() => {
    const cats = new Set<string>();
    for (const m of modules) {
      if (m.category) {
        cats.add(m.category);
      }
    }
    return Array.from(cats).sort();
  }, [modules]);

  const filteredModules = useMemo(() => {
    let filtered = modules;

    if (selectedCategory) {
      filtered = filtered.filter((m) => m.category === selectedCategory);
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(
        (m) => m.name.toLowerCase().includes(query) || m.description.toLowerCase().includes(query)
      );
    }

    return filtered;
  }, [modules, selectedCategory, searchQuery]);

  const handleCategoryClick = (category: string | null) => {
    setSelectedCategory(selectedCategory === category ? null : category);
  };

  const handleInsert = (code: string, module: ModuleInfo) => {
    onInsertModule?.(code, module);
  };

  if (!visible) return null;

  return (
    <div style={styles.browser}>
      <div style={styles.header}>
        <span style={styles.headerTitle}>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z" />
          </svg>
          Module Browser
        </span>
        <button
          style={styles.closeBtn}
          onClick={onClose}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'var(--bg-hover, #30363d)';
            e.currentTarget.style.color = 'var(--text-primary, #f0f6fc)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'none';
            e.currentTarget.style.color = 'var(--text-muted, #6e7681)';
          }}
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      </div>

      <div style={styles.searchContainer}>
        <input
          type="text"
          style={styles.searchInput}
          placeholder="Search modules..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      {categories.length > 0 && (
        <div style={styles.categories}>
          <button
            style={{
              ...styles.categoryBtn,
              ...(selectedCategory === null ? styles.categoryBtnActive : {}),
            }}
            onClick={() => handleCategoryClick(null)}
          >
            All
          </button>
          {categories.map((cat) => (
            <button
              key={cat}
              style={{
                ...styles.categoryBtn,
                ...(selectedCategory === cat ? styles.categoryBtnActive : {}),
              }}
              onClick={() => handleCategoryClick(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      )}

      <div style={styles.content}>
        <div style={styles.moduleList}>
          {filteredModules.length === 0 ? (
            <div style={styles.emptyState}>
              {modules.length === 0 ? 'No modules available' : 'No modules match your search'}
            </div>
          ) : (
            filteredModules.map((module) => (
              <ModuleCard
                key={module.name}
                module={module}
                selected={selectedModule?.name === module.name}
                onClick={() => setSelectedModule(module)}
              />
            ))
          )}
        </div>

        {selectedModule && (
          <div style={styles.moduleDetail}>
            <ModuleDetail module={selectedModule} onInsert={handleInsert} />
          </div>
        )}
      </div>
    </div>
  );
}

interface ModuleCardProps {
  module: ModuleInfo;
  selected?: boolean;
  onClick?: () => void;
}

const cardStyles: Record<string, CSSProperties> = {
  card: {
    padding: 'var(--spacing-sm, 8px) var(--spacing-md, 12px)',
    borderRadius: 'var(--radius-sm, 4px)',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    marginBottom: 'var(--spacing-xs, 4px)',
  },
  cardSelected: {
    background: 'var(--bg-active, rgba(56, 139, 253, 0.1))',
    borderLeft: '2px solid var(--accent-primary, #58a6ff)',
  },
  name: {
    fontSize: '13px',
    fontWeight: 600,
    color: 'var(--node-operation, #d2a8ff)',
  },
  description: {
    fontSize: '12px',
    color: 'var(--text-muted, #6e7681)',
    marginTop: '2px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  meta: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--spacing-sm, 8px)',
    marginTop: '4px',
    fontSize: '11px',
    color: 'var(--text-muted, #6e7681)',
  },
  category: {
    background: 'var(--bg-tertiary, #21262d)',
    padding: '1px 6px',
    borderRadius: '10px',
  },
};

function ModuleCard({ module, selected, onClick }: ModuleCardProps) {
  return (
    <div
      style={{
        ...cardStyles.card,
        ...(selected ? cardStyles.cardSelected : {}),
      }}
      onClick={onClick}
      onMouseEnter={(e) => {
        if (!selected) e.currentTarget.style.background = 'var(--bg-hover, #30363d)';
      }}
      onMouseLeave={(e) => {
        if (!selected) e.currentTarget.style.background = 'transparent';
      }}
    >
      <div style={cardStyles.name}>{module.name}</div>
      <div style={cardStyles.description}>{module.description}</div>
      <div style={cardStyles.meta}>
        <span>v{module.version}</span>
        {module.category && <span style={cardStyles.category}>{module.category}</span>}
      </div>
    </div>
  );
}

interface ModuleDetailProps {
  module: ModuleInfo;
  onInsert?: (code: string, module: ModuleInfo) => void;
}

const detailStyles: Record<string, CSSProperties> = {
  detail: {
    padding: 'var(--spacing-md, 12px)',
  },
  name: {
    fontSize: '16px',
    fontWeight: 600,
    color: 'var(--node-operation, #d2a8ff)',
    marginBottom: 'var(--spacing-xs, 4px)',
  },
  version: {
    fontSize: '12px',
    color: 'var(--text-muted, #6e7681)',
    marginBottom: 'var(--spacing-md, 12px)',
  },
  description: {
    fontSize: '13px',
    color: 'var(--text-primary, #f0f6fc)',
    lineHeight: 1.5,
    marginBottom: 'var(--spacing-md, 12px)',
  },
  section: {
    marginBottom: 'var(--spacing-md, 12px)',
  },
  sectionTitle: {
    fontSize: '11px',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    color: 'var(--text-muted, #6e7681)',
    marginBottom: 'var(--spacing-sm, 8px)',
  },
  params: {
    background: 'var(--bg-tertiary, #21262d)',
    borderRadius: 'var(--radius-sm, 4px)',
    padding: 'var(--spacing-sm, 8px)',
  },
  param: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: 'var(--spacing-xs, 4px) 0',
    fontSize: '12px',
  },
  paramName: {
    color: 'var(--text-primary, #f0f6fc)',
    fontWeight: 500,
  },
  paramType: {
    color: 'var(--text-muted, #6e7681)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
  },
  example: {
    background: 'var(--bg-tertiary, #21262d)',
    borderRadius: 'var(--radius-sm, 4px)',
    padding: 'var(--spacing-sm, 8px)',
    fontFamily: "'SF Mono', 'Consolas', monospace",
    fontSize: '12px',
    color: 'var(--text-primary, #f0f6fc)',
    whiteSpace: 'pre-wrap',
    marginBottom: 'var(--spacing-sm, 8px)',
  },
  insertBtn: {
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 'var(--spacing-xs, 4px)',
    padding: 'var(--spacing-sm, 8px)',
    border: 'none',
    borderRadius: 'var(--radius-sm, 4px)',
    background: 'var(--accent-highlight, #1f6feb)',
    color: 'var(--text-primary, #f0f6fc)',
    fontSize: '13px',
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
};

function ModuleDetail({ module, onInsert }: ModuleDetailProps) {
  const inputEntries = Object.entries(module.inputs);
  const outputEntries = Object.entries(module.outputs);

  const handleInsert = () => {
    const inputNames = Object.keys(module.inputs);
    const args = inputNames.map((name) => name).join(', ');
    const code = `result = ${module.name}(${args})`;
    onInsert?.(code, module);
  };

  return (
    <div style={detailStyles.detail}>
      <div style={detailStyles.name}>{module.name}</div>
      <div style={detailStyles.version}>v{module.version}</div>
      <div style={detailStyles.description}>{module.description}</div>

      {inputEntries.length > 0 && (
        <div style={detailStyles.section}>
          <div style={detailStyles.sectionTitle}>Inputs</div>
          <div style={detailStyles.params}>
            {inputEntries.map(([name, type]) => (
              <div key={name} style={detailStyles.param}>
                <span style={detailStyles.paramName}>{name}</span>
                <span style={detailStyles.paramType}>{type}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {outputEntries.length > 0 && (
        <div style={detailStyles.section}>
          <div style={detailStyles.sectionTitle}>Outputs</div>
          <div style={detailStyles.params}>
            {outputEntries.map(([name, type]) => (
              <div key={name} style={detailStyles.param}>
                <span style={detailStyles.paramName}>{name}</span>
                <span style={detailStyles.paramType}>{type}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {module.examples && module.examples.length > 0 && (
        <div style={detailStyles.section}>
          <div style={detailStyles.sectionTitle}>Examples</div>
          {module.examples.map((example, index) => (
            <div key={index} style={detailStyles.example}>
              {example}
            </div>
          ))}
        </div>
      )}

      <button
        style={detailStyles.insertBtn}
        onClick={handleInsert}
        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--accent-primary, #58a6ff)')}
        onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--accent-highlight, #1f6feb)')}
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
          <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
        </svg>
        Insert into Editor
      </button>
    </div>
  );
}

export default ModuleBrowser;
