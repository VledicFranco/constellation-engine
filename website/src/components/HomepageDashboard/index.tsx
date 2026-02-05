import styles from './styles.module.css';
import useBaseUrl from '@docusaurus/useBaseUrl';

export default function HomepageDashboard(): JSX.Element {
  const screenshotUrl = useBaseUrl('/img/dashboard-screenshot.png');

  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>Visual Development</span>
          <h2 className={styles.title}>Interactive Pipeline Dashboard</h2>
          <p className={styles.subtitle}>
            A complete web-based IDE for building, visualizing, and executing pipelines.
            See your DAG come to life as you write code, with real-time compilation feedback.
          </p>
        </div>

        {/* Screenshot */}
        <div className={styles.screenshotContainer}>
          <div className={styles.screenshotPanel}>
            <div className={styles.screenshotHeader}>
              <div className={styles.windowControls}>
                <span className={styles.windowDot} style={{ background: '#ff5f56' }}></span>
                <span className={styles.windowDot} style={{ background: '#ffbd2e' }}></span>
                <span className={styles.windowDot} style={{ background: '#27c93f' }}></span>
              </div>
              <span className={styles.urlBar}>localhost:8080/dashboard</span>
            </div>
            <img
              src={screenshotUrl}
              alt="Constellation Dashboard showing file browser, code editor, and DAG visualization"
              className={styles.screenshot}
            />
          </div>
        </div>

        {/* Three-column layout description */}
        <div className={styles.layoutDescription}>
          <div className={styles.layoutColumn}>
            <div className={styles.layoutIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <h3>File Browser</h3>
            <p>Navigate your <code>.cst</code> pipeline files with a tree view. Click to load, organize by folders.</p>
          </div>
          <div className={styles.layoutColumn}>
            <div className={styles.layoutIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="16 18 22 12 16 6"/>
                <polyline points="8 6 2 12 8 18"/>
              </svg>
            </div>
            <h3>Live Code Editor</h3>
            <p>Write constellation-lang with syntax highlighting. DAG updates as you type with instant error feedback.</p>
          </div>
          <div className={styles.layoutColumn}>
            <div className={styles.layoutIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="18" cy="5" r="3"/>
                <circle cx="6" cy="12" r="3"/>
                <circle cx="18" cy="19" r="3"/>
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
              </svg>
            </div>
            <h3>DAG Visualization</h3>
            <p>See your pipeline as a graph. Color-coded nodes for inputs, outputs, operations, and conditionals.</p>
          </div>
        </div>

        {/* Features grid */}
        <div className={styles.features}>
          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
            </div>
            <h3>One-Click Execution</h3>
            <p>Auto-generated input forms based on your pipeline's type signature. Fill values, click Run, see results.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </div>
            <h3>Execution History</h3>
            <p>Full audit trail of past runs. Filter by script, view inputs/outputs, debug failures with full context.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <line x1="3" y1="9" x2="21" y2="9"/>
                <line x1="9" y1="21" x2="9" y2="9"/>
              </svg>
            </div>
            <h3>Node Inspection</h3>
            <p>Click any node to see its type, execution status, computed value, and duration. Debug at node-level.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
            </div>
            <h3>Pipeline Management</h3>
            <p>Load, version, and deploy pipelines. View structural hashes, manage aliases, rollback versions.</p>
          </div>
        </div>

        {/* CTA */}
        <div className={styles.cta}>
          <p>
            <strong>Access the dashboard:</strong> Start the server and navigate to{' '}
            <code>http://localhost:8080/dashboard</code>. No additional setup required.
            The dashboard is bundled with the HTTP server and works out of the box.
          </p>
        </div>
      </div>
    </section>
  );
}
