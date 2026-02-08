import { useState } from 'react';
import Link from '@docusaurus/Link';
import styles from './styles.module.css';

// Static constellation node positions — kept to edges/corners, away from center text
const nodes = [
  {cx: 40, cy: 30},   {cx: 120, cy: 60},  {cx: 210, cy: 20},
  {cx: 330, cy: 50},  {cx: 430, cy: 25},  {cx: 560, cy: 45},
  {cx: 680, cy: 20},  {cx: 780, cy: 55},  {cx: 870, cy: 30},
  {cx: 960, cy: 60},  {cx: 70, cy: 350},  {cx: 180, cy: 380},
  {cx: 320, cy: 360}, {cx: 500, cy: 390}, {cx: 680, cy: 370},
  {cx: 820, cy: 350}, {cx: 930, cy: 380},
];

// Edges connecting nearby nodes
const edges = [
  [0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 7], [7, 8], [8, 9],
  [0, 10], [1, 11], [3, 12], [5, 14], [7, 15], [9, 16],
  [10, 11], [11, 12], [12, 13], [13, 14], [14, 15], [15, 16],
];

const modules = [
  { name: 'constellation-core', desc: 'Core types & module system', required: true },
  { name: 'constellation-runtime', desc: 'Pipeline execution engine', required: true },
  { name: 'constellation-lang-compiler', desc: 'DSL compiler for .cst files', required: false },
  { name: 'constellation-lang-stdlib', desc: 'Standard library functions', required: false },
  { name: 'constellation-http-api', desc: 'HTTP server & dashboard', required: false },
  { name: 'constellation-lang-lsp', desc: 'IDE language server', required: false },
];

export default function HomepageHero(): JSX.Element {
  const [copied, setCopied] = useState<string | null>(null);
  const version = '0.5.0';

  const handleCopy = async (moduleName: string) => {
    const command = `"io.constellation" %% "${moduleName}" % "${version}"`;
    await navigator.clipboard.writeText(command);
    setCopied(moduleName);
    setTimeout(() => setCopied(null), 2000);
  };

  const handleCopyAll = async () => {
    const allDeps = modules
      .map(m => `"io.constellation" %% "${m.name}" % "${version}"`)
      .join(',\n  ');
    await navigator.clipboard.writeText(allDeps);
    setCopied('all');
    setTimeout(() => setCopied(null), 2000);
  };

  return (
    <header className={styles.hero}>
      <div className={styles.heroBackground} />
      <svg className={styles.constellation} viewBox="0 0 1000 420" preserveAspectRatio="xMidYMid slice">
        {edges.map(([from, to], i) => (
          <line
            key={`edge-${i}`}
            className={styles.edge}
            x1={nodes[from].cx}
            y1={nodes[from].cy}
            x2={nodes[to].cx}
            y2={nodes[to].cy}
          />
        ))}
        {nodes.map((node, i) => (
          <circle
            key={`node-${i}`}
            className={styles.node}
            cx={node.cx}
            cy={node.cy}
            r="3"
          />
        ))}
      </svg>
      <div className={styles.content}>
        <h1 className={styles.title}>Constellation Engine</h1>
        <p className={styles.tagline}>
          Type-safe pipeline orchestration for Scala
        </p>
        <div className={styles.buttons}>
          <Link className={styles.primaryButton} to="/docs/getting-started/tutorial">
            Get Started
          </Link>
          <Link
            className={styles.secondaryButton}
            href="https://github.com/VledicFranco/constellation-engine"
          >
            View on GitHub
          </Link>
        </div>
        <div className={styles.installSection}>
          <div className={styles.installHeader}>
            <span className={styles.installTitle}>Add to your build.sbt</span>
            <button className={styles.copyAllButton} onClick={handleCopyAll}>
              {copied === 'all' ? 'Copied!' : 'Copy all'}
            </button>
          </div>
          <div className={styles.modulesList}>
            {modules.map((mod) => (
              <div
                key={mod.name}
                className={styles.moduleRow}
                onClick={() => handleCopy(mod.name)}
              >
                <div className={styles.moduleInfo}>
                  <code className={styles.moduleName}>{mod.name}</code>
                  <span className={styles.moduleDesc}>{mod.desc}</span>
                </div>
                <div className={styles.moduleActions}>
                  {mod.required ? (
                    <span className={styles.requiredBadge}>required</span>
                  ) : (
                    <span className={styles.optionalBadge}>optional</span>
                  )}
                  <button className={styles.copyButton} aria-label="Copy to clipboard">
                    {copied === mod.name ? (
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    ) : (
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                      </svg>
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </header>
  );
}
