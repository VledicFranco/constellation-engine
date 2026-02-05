import styles from './styles.module.css';
import CodeBlock from '@theme/CodeBlock';
import useBaseUrl from '@docusaurus/useBaseUrl';

export default function HomepageLSP(): JSX.Element {
  const screenshotUrl = useBaseUrl('/img/vscode-lsp-demo.png');
  const scalaModuleCode = `// modules/DataModules.scala
// Define modules in Scala - LSP exposes them to .cst files

val multiplyEach = ModuleBuilder
  .metadata(
    name = "MultiplyEach",
    description = "Multiplies each number in a list by a constant multiplier",
    majorVersion = 1,
    minorVersion = 0,
    tags = Set("data", "transform")
  )
  .implementationPure[MultiplyInput, List[Int]] { input =>
    input.numbers.map(_ * input.multiplier)
  }
  .build

val filterAbove = ModuleBuilder
  .metadata(
    name = "FilterAbove",
    description = "Filters list to keep only values above threshold",
    majorVersion = 1,
    minorVersion = 0,
    tags = Set("data", "filter")
  )
  .implementationPure[FilterInput, List[Int]] { input =>
    input.numbers.filter(_ > input.threshold)
  }
  .build`;

  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>Developer Experience</span>
          <h2 className={styles.title}>IDE-Powered Pipeline Development</h2>
          <p className={styles.subtitle}>
            Write modules in Scala, compose them in <code>.cst</code> files with full IDE support.
            The LSP server connects your Scala definitions to the pipeline editor in real-time.
          </p>
        </div>

        <div className={styles.content}>
          {/* Left: Scala module definitions */}
          <div className={styles.codePanel}>
            <div className={styles.codePanelHeader}>
              <span className={styles.fileIcon}>S</span>
              <span>DataModules.scala</span>
              <span className={styles.tag}>Scala Module Definitions</span>
            </div>
            <CodeBlock language="scala">
              {scalaModuleCode}
            </CodeBlock>
          </div>

          {/* Right: Screenshot */}
          <div className={styles.screenshotPanel}>
            <div className={styles.screenshotHeader}>
              <span className={styles.vscodeLogo}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M17.583 2.322L12.29 7.616l-5.293-5.294L3.3 5.624l3.893 3.893L3.3 13.41l3.697 3.302 5.293-5.293 5.293 5.293L21.3 13.41l-3.893-3.893L21.3 5.624l-3.717-3.302z"/>
                </svg>
              </span>
              <span>VSCode + Constellation Extension</span>
            </div>
            <img
              src={screenshotUrl}
              alt="VSCode showing Constellation LSP with hover documentation and Script Runner"
              className={styles.screenshot}
            />
          </div>
        </div>

        {/* Features grid */}
        <div className={styles.features}>
          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
              </svg>
            </div>
            <h3>Rich Autocomplete</h3>
            <p>Module names, parameters, and types auto-suggested as you type. No more guessing module signatures.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <h3>Hover Documentation</h3>
            <p>See descriptions, parameter types, return types, and version info directly from your Scala code.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
            </div>
            <h3>Live Execution</h3>
            <p>Run pipelines against your deployed server directly from VSCode. See results in milliseconds.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </div>
            <h3>Real-time Diagnostics</h3>
            <p>Type errors, unknown modules, and field mismatches highlighted instantly as you edit.</p>
          </div>
        </div>

        {/* Call to action */}
        <div className={styles.cta}>
          <p>
            <strong>How it works:</strong> Start the HTTP server with your Scala modules registered.
            The LSP endpoint (<code>ws://localhost:8080/lsp</code>) streams module metadata to VSCode,
            enabling autocomplete and validation based on your actual codebase.
          </p>
        </div>
      </div>
    </section>
  );
}
