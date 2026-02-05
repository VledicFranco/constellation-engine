import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageDeployment(): JSX.Element {
  const [activeTab, setActiveTab] = useState<'hot' | 'cold' | 'canary'>('hot');

  const hotColdDiagram = `
┌─────────────────────────────────────────────────────────────────────────┐
│                        Pipeline Deployment Modes                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   HOT PIPELINES (In-Memory)              COLD PIPELINES (On-Demand)     │
│   ─────────────────────────              ──────────────────────────     │
│                                                                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Server  │                            │ Server  │                    │
│   │ Start   │                            │ Start   │                    │
│   └────┬────┘                            └────┬────┘                    │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Load    │ ←── All .cst files         │ Ready   │ ←── No loading     │
│   │ & Cache │     compiled at start      │         │     at startup     │
│   └────┬────┘                            └────┬────┘                    │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Execute │ ←── ~1ms latency           │ Request │                    │
│   │ Request │     (cache hit)            │ Arrives │                    │
│   └─────────┘                            └────┬────┘                    │
│                                               │                         │
│                                               ▼                         │
│                                          ┌─────────┐                    │
│                                          │ Compile │ ←── First request  │
│                                          │ & Cache │     ~50-100ms      │
│                                          └────┬────┘                    │
│                                               │                         │
│                                               ▼                         │
│                                          ┌─────────┐                    │
│                                          │ Execute │ ←── Subsequent     │
│                                          │         │     ~1ms (cached)  │
│                                          └─────────┘                    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘`;

  const hotConfig = `# Server startup with hot loading
# All pipelines compiled and cached at boot

# Environment configuration
CONSTELLATION_CST_DIR=/pipelines      # Directory to scan
CONSTELLATION_LOADER_RECURSIVE=true   # Include subdirectories
CONSTELLATION_LOADER_FAIL_ON_ERROR=true  # Fail fast on compile errors

# Scala configuration
ConstellationServer.builder(constellation, compiler)
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Path.of("/pipelines"),
    recursive = true,
    failOnError = true,              // Server won't start if any pipeline fails
    aliasStrategy = AliasStrategy.FileName  // "order-enrichment.cst" → alias "order-enrichment"
  ))
  .run

# Result at startup:
# PipelineLoader: found 24 .cst file(s) in /pipelines
# PipelineLoader: loaded order-enrichment.cst -> hash=sha256:abc123...
# PipelineLoader: loaded user-scoring.cst -> hash=sha256:def456...
# PipelineLoader: loaded=24, failed=0, skipped=0
# Server ready - all pipelines hot in memory`;

  const coldConfig = `# On-demand compilation (cold start)
# Pipelines compiled when first requested

# No pre-loading - server starts instantly
ConstellationServer.builder(constellation, compiler)
  .withCaching(CachingLangCompiler.withDefaults(compiler))  # Cache after first compile
  .run

# First request for "order-enrichment":
POST /run
{
  "source": "in orderId: String\\norder = GetOrder(orderId)\\nout order",
  "name": "order-enrichment"
}

# Response includes compile time:
{
  "structuralHash": "sha256:abc123...",
  "compileTimeMs": 87,       # First request: ~50-100ms
  "executeTimeMs": 12,
  "cached": false
}

# Second request (cached):
{
  "structuralHash": "sha256:abc123...",
  "compileTimeMs": 0,        # Cache hit: ~0ms
  "executeTimeMs": 11,
  "cached": true
}`;

  const canaryConfig = `# Canary deployment: gradual rollout with auto-rollback
# Safe production deployments with traffic splitting

# Start a canary deployment
POST /canary/scoring-pipeline/start
{
  "oldVersion": "sha256:abc123...",      # Current production version
  "newVersion": "sha256:def456...",      # New version to test
  "config": {
    "initialWeight": 0.05,               # Start with 5% traffic
    "promotionSteps": [0.10, 0.25, 0.50, 1.0],  # Gradual increase
    "observationWindow": "5m",           # Watch each step for 5 minutes
    "errorThreshold": 0.05,              # Rollback if >5% errors
    "latencyThresholdMs": 500,           # Rollback if p99 > 500ms
    "minRequests": 100,                  # Need 100 requests before deciding
    "autoPromote": true                  # Auto-advance if healthy
  }
}

# Monitor canary status
GET /canary/scoring-pipeline
{
  "status": "Observing",
  "currentWeight": 0.25,                 # 25% traffic to new version
  "currentStep": 2,
  "metrics": {
    "oldVersion": { "requests": 7500, "errorRate": 0.02, "p99LatencyMs": 120 },
    "newVersion": { "requests": 2500, "errorRate": 0.01, "p99LatencyMs": 95 }
  }
}

# Auto-rollback triggered if thresholds exceeded:
# "status": "RolledBack", "reason": "Error rate 0.08 exceeded threshold 0.05"

# Manual controls available:
POST /canary/scoring-pipeline/promote   # Force advance to next step
POST /canary/scoring-pipeline/rollback  # Abort and revert`;

  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>Production Operations</span>
          <h2 className={styles.title}>Hot, Cold & Canary Deployments</h2>
          <p className={styles.subtitle}>
            Choose how pipelines are loaded and deployed. Pre-warm for latency-sensitive workloads,
            lazy-load for memory efficiency, and roll out changes safely with canary releases.
          </p>
        </div>

        {/* Tabs */}
        <div className={styles.tabs}>
          <button
            className={`${styles.tab} ${activeTab === 'hot' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('hot')}
          >
            <span className={styles.tabIcon}>🔥</span>
            Hot Pipelines
          </button>
          <button
            className={`${styles.tab} ${activeTab === 'cold' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('cold')}
          >
            <span className={styles.tabIcon}>❄️</span>
            Cold Pipelines
          </button>
          <button
            className={`${styles.tab} ${activeTab === 'canary' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('canary')}
          >
            <span className={styles.tabIcon}>🐦</span>
            Canary Releases
          </button>
        </div>

        {/* Content */}
        <div className={styles.content}>
          {activeTab === 'hot' && (
            <>
              <div className={styles.description}>
                <h3>Hot Pipelines: Pre-Compiled & Ready</h3>
                <p>
                  Load all pipelines at server startup. First request executes in ~1ms with no compilation overhead.
                  Ideal for <strong>latency-sensitive APIs</strong>, <strong>high-throughput services</strong>, and
                  <strong> predictable performance requirements</strong>.
                </p>
                <div className={styles.useCases}>
                  <span className={styles.useCase}>Real-time APIs</span>
                  <span className={styles.useCase}>BFF Services</span>
                  <span className={styles.useCase}>ML Inference</span>
                  <span className={styles.useCase}>Trading Systems</span>
                </div>
              </div>
              <div className={styles.codePanel}>
                <CodeBlock language="bash" title="Hot Loading Configuration">
                  {hotConfig}
                </CodeBlock>
              </div>
            </>
          )}

          {activeTab === 'cold' && (
            <>
              <div className={styles.description}>
                <h3>Cold Pipelines: On-Demand Compilation</h3>
                <p>
                  Start the server instantly with no pipeline loading. Pipelines compile on first request (~50-100ms),
                  then cache for subsequent calls. Ideal for <strong>development environments</strong>,
                  <strong> batch jobs</strong>, and <strong>memory-constrained deployments</strong>.
                </p>
                <div className={styles.useCases}>
                  <span className={styles.useCase}>Development</span>
                  <span className={styles.useCase}>Batch Processing</span>
                  <span className={styles.useCase}>Serverless</span>
                  <span className={styles.useCase}>Low-Traffic Services</span>
                </div>
              </div>
              <div className={styles.codePanel}>
                <CodeBlock language="bash" title="Cold Loading (On-Demand)">
                  {coldConfig}
                </CodeBlock>
              </div>
            </>
          )}

          {activeTab === 'canary' && (
            <>
              <div className={styles.description}>
                <h3>Canary Releases: Safe Production Rollouts</h3>
                <p>
                  Deploy new pipeline versions to a small percentage of traffic, automatically promote if healthy,
                  or rollback if error rates or latency exceed thresholds. Full observability with per-version metrics.
                </p>
                <div className={styles.canarySteps}>
                  <div className={styles.canaryStep}>
                    <span className={styles.stepNumber}>1</span>
                    <span className={styles.stepLabel}>5% traffic</span>
                  </div>
                  <div className={styles.canaryArrow}>→</div>
                  <div className={styles.canaryStep}>
                    <span className={styles.stepNumber}>2</span>
                    <span className={styles.stepLabel}>10%</span>
                  </div>
                  <div className={styles.canaryArrow}>→</div>
                  <div className={styles.canaryStep}>
                    <span className={styles.stepNumber}>3</span>
                    <span className={styles.stepLabel}>25%</span>
                  </div>
                  <div className={styles.canaryArrow}>→</div>
                  <div className={styles.canaryStep}>
                    <span className={styles.stepNumber}>4</span>
                    <span className={styles.stepLabel}>50%</span>
                  </div>
                  <div className={styles.canaryArrow}>→</div>
                  <div className={styles.canaryStep}>
                    <span className={styles.stepNumber}>5</span>
                    <span className={styles.stepLabel}>100%</span>
                  </div>
                </div>
              </div>
              <div className={styles.codePanel}>
                <CodeBlock language="bash" title="Canary Deployment API">
                  {canaryConfig}
                </CodeBlock>
              </div>
            </>
          )}
        </div>

        {/* Diagram for hot vs cold */}
        {(activeTab === 'hot' || activeTab === 'cold') && (
          <div className={styles.diagram}>
            <pre>{hotColdDiagram}</pre>
          </div>
        )}

        {/* Feature comparison table */}
        <div className={styles.comparisonTable}>
          <table>
            <thead>
              <tr>
                <th>Feature</th>
                <th>Hot Pipelines</th>
                <th>Cold Pipelines</th>
                <th>Canary Releases</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>First Request Latency</td>
                <td className={styles.good}>~1ms</td>
                <td className={styles.neutral}>~50-100ms</td>
                <td className={styles.good}>~1ms (both cached)</td>
              </tr>
              <tr>
                <td>Startup Time</td>
                <td className={styles.neutral}>Slower (compiles all)</td>
                <td className={styles.good}>Instant</td>
                <td className={styles.good}>Instant</td>
              </tr>
              <tr>
                <td>Memory Usage</td>
                <td className={styles.neutral}>Higher (all loaded)</td>
                <td className={styles.good}>Lower (on-demand)</td>
                <td className={styles.neutral}>2x during rollout</td>
              </tr>
              <tr>
                <td>Best For</td>
                <td>Production APIs</td>
                <td>Dev / Batch jobs</td>
                <td>Safe deployments</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
