import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageDeployment(): JSX.Element {
  const [activeTab, setActiveTab] = useState<'hot' | 'cold' | 'canary'>('hot');

  const hotColdDiagram = `
┌─────────────────────────────────────────────────────────────────────────┐
│                        Pipeline Execution Patterns                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   HOT PIPELINES (Compile + Run)          COLD PIPELINES (Store + Exec)  │
│   ─────────────────────────────          ────────────────────────────   │
│                                                                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Client  │                            │ Client  │                    │
│   │ Request │                            │ Request │                    │
│   └────┬────┘                            └────┬────┘                    │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ POST    │ ←── Source code +          │ POST    │ ←── Reference +    │
│   │ /run    │     inputs in one call     │/execute │     inputs only    │
│   └────┬────┘                            └────┬────┘                    │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Compile │ ←── Every request          │ Lookup  │ ←── By name or     │
│   │ Source  │     compiles fresh         │ Stored  │     structural hash│
│   └────┬────┘                            └────┬────┘                    │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌─────────┐                            ┌─────────┐                    │
│   │ Execute │ ←── ~50-100ms total        │ Execute │ ←── ~1ms           │
│   │ & Return│     (compile + run)        │ & Return│     (no compile)   │
│   └─────────┘                            └─────────┘                    │
│                                                                         │
│   Best for: Ad-hoc, dev, one-time        Best for: Production, APIs    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘`;

  const hotConfig = `# Hot Pipeline: Compile + Execute in one request
# Send source code directly, get results immediately

# Single request does everything:
POST /run
{
  "source": "in orderId: String\\norder = GetOrder(orderId)\\nout order",
  "inputs": { "orderId": "ORD-12345" }
}

# Response:
{
  "outputs": { "order": { "id": "ORD-12345", "total": 99.99 } },
  "structuralHash": "sha256:abc123...",
  "compileTimeMs": 87,
  "executeTimeMs": 12
}

# Ideal for:
# - Ad-hoc queries and exploration
# - Development and testing
# - One-time transformations
# - Dynamic pipelines generated at runtime`;

  const coldConfig = `# Cold Pipeline: Store + Execute by Reference
# Compile once, run many times by name or hash

# Step 1: Compile and store the pipeline
POST /compile
{
  "source": "in orderId: String\\norder = GetOrder(orderId)\\nout order",
  "name": "order-enrichment"
}
# Response: { "structuralHash": "sha256:abc123...", "alias": "order-enrichment" }

# Step 2: Execute by reference (no compilation!)
POST /execute
{
  "ref": "order-enrichment",
  "inputs": { "orderId": "ORD-12345" }
}

# Or execute by hash for immutable references:
POST /execute
{
  "ref": "sha256:abc123...",
  "inputs": { "orderId": "ORD-12345" }
}

# Benefits:
# - Sub-millisecond execution (no compile overhead)
# - Version control via structural hashes
# - Update aliases without changing client code
# - Pre-load at server startup for zero cold-start latency`;

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
            Choose your execution pattern. Use hot pipelines for ad-hoc development workflows,
            cold pipelines for production APIs with sub-millisecond latency, and canary releases for safe rollouts.
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
                <h3>Hot Pipelines: Compile + Run in One Request</h3>
                <p>
                  Send source code directly to the <code>/run</code> endpoint. The server compiles and executes in a single step.
                  Ideal for <strong>ad-hoc queries</strong>, <strong>development</strong>, <strong>exploration</strong>, and
                  <strong> dynamically generated pipelines</strong>.
                </p>
                <div className={styles.useCases}>
                  <span className={styles.useCase}>Development</span>
                  <span className={styles.useCase}>Ad-hoc Queries</span>
                  <span className={styles.useCase}>Dynamic Pipelines</span>
                  <span className={styles.useCase}>One-time Jobs</span>
                </div>
              </div>
              <div className={styles.codePanel}>
                <CodeBlock language="bash" title="Hot Pipeline: /run Endpoint">
                  {hotConfig}
                </CodeBlock>
              </div>
            </>
          )}

          {activeTab === 'cold' && (
            <>
              <div className={styles.description}>
                <h3>Cold Pipelines: Store + Execute by Reference</h3>
                <p>
                  Compile once with <code>/compile</code>, then execute many times by name or hash via <code>/execute</code>.
                  Sub-millisecond execution with no compile overhead. Ideal for <strong>production APIs</strong>,
                  <strong> high-throughput services</strong>, and <strong>versioned deployments</strong>.
                </p>
                <div className={styles.useCases}>
                  <span className={styles.useCase}>Production APIs</span>
                  <span className={styles.useCase}>High Throughput</span>
                  <span className={styles.useCase}>ML Inference</span>
                  <span className={styles.useCase}>Trading Systems</span>
                </div>
              </div>
              <div className={styles.codePanel}>
                <CodeBlock language="bash" title="Cold Pipeline: /compile + /execute">
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
                <td>API Pattern</td>
                <td><code>/run</code> (compile + execute)</td>
                <td><code>/compile</code> + <code>/execute</code></td>
                <td>Traffic splitting</td>
              </tr>
              <tr>
                <td>Execution Latency</td>
                <td className={styles.neutral}>~50-100ms (includes compile)</td>
                <td className={styles.good}>~1ms (pre-compiled)</td>
                <td className={styles.good}>~1ms (both cached)</td>
              </tr>
              <tr>
                <td>Reusability</td>
                <td className={styles.neutral}>One-time use</td>
                <td className={styles.good}>Compile once, run many</td>
                <td className={styles.good}>Versioned deployments</td>
              </tr>
              <tr>
                <td>Best For</td>
                <td>Dev / Ad-hoc / Dynamic</td>
                <td>Production APIs</td>
                <td>Safe deployments</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
