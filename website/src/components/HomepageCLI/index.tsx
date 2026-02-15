import styles from './styles.module.css';
import CodeBlock from '@theme/CodeBlock';

export default function HomepageCLI(): JSX.Element {
  const cliExamples = `# Type-check a pipeline
$ constellation compile pipeline.cst
✓ Compilation successful (hash: 7a3b8c9d...)

# Execute with inputs
$ constellation run pipeline.cst --input text="Hello, World!"
✓ Execution completed:
  result: "HELLO, WORLD!"

# Generate DAG visualization
$ constellation viz pipeline.cst | dot -Tpng > dag.png

# Check server health
$ constellation server health
✓ Server healthy (uptime: 3d 14h)`;

  const ciExample = `# .github/workflows/validate.yml
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install Constellation CLI
        run: cs install io.github.vledicfranco:constellation-lang-cli_3:0.8.1
      - name: Validate pipelines
        run: |
          for f in pipelines/*.cst; do
            constellation compile "$f" --json || exit 1
          done`;

  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>Terminal Workflows</span>
          <h2 className={styles.title}>CLI-First Pipeline Operations</h2>
          <p className={styles.subtitle}>
            Compile, run, visualize, and deploy pipelines from your terminal.
            Designed for <strong>scripting</strong>, <strong>CI/CD</strong>, and <strong>fast iteration</strong>.
          </p>
        </div>

        <div className={styles.content}>
          {/* Left: CLI examples */}
          <div className={styles.codePanel}>
            <div className={styles.codePanelHeader}>
              <span className={styles.terminalIcon}>$</span>
              <span>Terminal</span>
              <span className={styles.tag}>CLI Commands</span>
            </div>
            <CodeBlock language="bash">
              {cliExamples}
            </CodeBlock>
          </div>

          {/* Right: CI/CD example */}
          <div className={styles.codePanel}>
            <div className={styles.codePanelHeader}>
              <span className={styles.yamlIcon}>Y</span>
              <span>GitHub Actions</span>
              <span className={styles.tag}>CI/CD Integration</span>
            </div>
            <CodeBlock language="yaml">
              {ciExample}
            </CodeBlock>
          </div>
        </div>

        {/* Features grid */}
        <div className={styles.features}>
          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="4 17 10 11 4 5"/>
                <line x1="12" y1="19" x2="20" y2="19"/>
              </svg>
            </div>
            <h3>Compile & Run</h3>
            <p>Type-check pipelines and execute them with inputs from CLI flags or JSON files.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="3"/>
                <path d="M12 2v4m0 12v4m-7-7H2m20 0h-3M5.6 5.6l2.1 2.1m8.6 8.6l2.1 2.1m0-12.8l-2.1 2.1m-8.6 8.6l-2.1 2.1"/>
              </svg>
            </div>
            <h3>Server Operations</h3>
            <p>Health checks, metrics, execution management, and pipeline introspection.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
              </svg>
            </div>
            <h3>Deploy & Canary</h3>
            <p>Push pipelines, start canary releases, promote or rollback with confidence.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <line x1="9" y1="3" x2="9" y2="21"/>
              </svg>
            </div>
            <h3>JSON Output</h3>
            <p>Machine-readable output with <code>--json</code> flag. Deterministic exit codes for automation.</p>
          </div>
        </div>

        {/* Install CTA */}
        <div className={styles.cta}>
          <div className={styles.installCommand}>
            <code>cs install io.github.vledicfranco:constellation-lang-cli_3:0.8.1</code>
          </div>
          <p>
            Install via Coursier, or download the fat JAR from <a href="https://github.com/VledicFranco/constellation-engine/releases">GitHub Releases</a>.
          </p>
        </div>
      </div>
    </section>
  );
}
