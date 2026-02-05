import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageSuspended(): JSX.Element {
  const [activeTab, setActiveTab] = useState<'pipeline' | 'timeline' | 'state'>('pipeline');

  const pipelineCode = `# customer-onboarding.cst
# Long-lived pipeline that spans days/weeks

type Customer = { id: String, email: String, plan: String }

in customer: Customer

# Day 1: Initial setup
welcome = SendWelcomeEmail(customer.email)
account = CreateAccount(customer)

# SUSPEND: Wait for email verification (async, may take hours/days)
verification = AwaitEmailVerification(customer.id) with suspend: true

# Day 2-3: After verification
profile = CreateUserProfile(account, verification.data)
trial = StartFreeTrial(customer.plan) with suspend_until: "2024-02-01"

# SUSPEND: Wait for trial period or upgrade event
upgrade_or_expire = AwaitFirstOf(
  UpgradeEvent(customer.id),
  TrialExpiry(trial.end_date)
) with suspend: true

# Day 14+: Based on outcome
final_status = branch {
  "converted"  when upgrade_or_expire.type == "upgrade",
  "churned"    when upgrade_or_expire.type == "expiry",
  "pending"    otherwise
}

followup = SendFollowup(customer.email, final_status)

out { customer, status: final_status, timeline: [welcome, verification, trial] }`;

  const timelineCode = `┌─────────────────────────────────────────────────────────────┐
│                    Pipeline Execution Timeline               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Day 1    ●──────●──────◐ SUSPENDED                         │
│           │      │      │                                   │
│         Start  Welcome  Awaiting verification...            │
│                                                             │
│  Day 2              ●──────●──────◐ SUSPENDED               │
│                     │      │      │                         │
│                 Verified Profile  Trial started             │
│                                   (suspend_until: Day 14)   │
│                                                             │
│  Day 14                          ●──────●──────●            │
│                                  │      │      │            │
│                              Resumed  Branch  Complete      │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  Total wall-clock time: 14 days                             │
│  Compute time: ~200ms across 3 sessions                     │
│  State checkpoints: 3                                       │
└─────────────────────────────────────────────────────────────┘`;

  const stateCode = `// Pipeline state is automatically persisted
// Resume from any checkpoint after server restart

{
  "execution_id": "exec_abc123",
  "pipeline": "customer-onboarding",
  "status": "suspended",
  "suspended_at": "node:verification",
  "resume_condition": {
    "type": "external_event",
    "event": "email_verified",
    "customer_id": "cust_456"
  },
  "checkpoint": {
    "welcome": { "sent_at": "2024-01-15T10:00:00Z", "message_id": "msg_789" },
    "account": { "account_id": "acc_012", "created_at": "2024-01-15T10:00:01Z" }
  },
  "inputs": {
    "customer": { "id": "cust_456", "email": "user@example.com", "plan": "pro" }
  },
  "created_at": "2024-01-15T10:00:00Z",
  "updated_at": "2024-01-15T10:00:02Z"
}

// Resume via API:
// POST /executions/exec_abc123/resume
// { "event": "email_verified", "data": { "verified_at": "..." } }`;

  return (
    <section className={styles.section}>
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>Long-Running Workflows</span>
          <h2 className={styles.title}>Suspended Pipelines with State</h2>
          <p className={styles.subtitle}>
            Build workflows that span hours, days, or weeks. Pipelines suspend at async boundaries,
            persist their state, and resume exactly where they left off—even after server restarts.
          </p>
        </div>

        {/* Tabs */}
        <div className={styles.tabs}>
          <button
            className={`${styles.tab} ${activeTab === 'pipeline' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('pipeline')}
          >
            Pipeline Definition
          </button>
          <button
            className={`${styles.tab} ${activeTab === 'timeline' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('timeline')}
          >
            Execution Timeline
          </button>
          <button
            className={`${styles.tab} ${activeTab === 'state' ? styles.tabActive : ''}`}
            onClick={() => setActiveTab('state')}
          >
            Persisted State
          </button>
        </div>

        {/* Content */}
        <div className={styles.codePanel}>
          {activeTab === 'pipeline' && (
            <CodeBlock language="python" title="customer-onboarding.cst">
              {pipelineCode}
            </CodeBlock>
          )}
          {activeTab === 'timeline' && (
            <div className={styles.timeline}>
              <pre>{timelineCode}</pre>
            </div>
          )}
          {activeTab === 'state' && (
            <CodeBlock language="json" title="Checkpoint State">
              {stateCode}
            </CodeBlock>
          )}
        </div>

        {/* Features */}
        <div className={styles.features}>
          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <line x1="9" y1="3" x2="9" y2="21"/>
              </svg>
            </div>
            <h3>Durable Execution</h3>
            <p>State persisted to your storage backend. Survive server restarts, deployments, and failures.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </div>
            <h3>Time-Based Suspension</h3>
            <p>Use <code>suspend_until</code> for scheduled delays—trial periods, cooling-off windows, SLAs.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
            </div>
            <h3>Event-Driven Resume</h3>
            <p>Pipelines wake on external events—webhooks, user actions, third-party callbacks.</p>
          </div>

          <div className={styles.feature}>
            <div className={styles.featureIcon}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="23 4 23 10 17 10"/>
                <polyline points="1 20 1 14 7 14"/>
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
              </svg>
            </div>
            <h3>Replay & Debug</h3>
            <p>Inspect any checkpoint. Replay from any state. Debug long workflows without rerunning everything.</p>
          </div>
        </div>

        {/* Use cases */}
        <div className={styles.useCases}>
          <h3>Perfect for</h3>
          <div className={styles.useCaseList}>
            <span className={styles.useCase}>Customer Onboarding</span>
            <span className={styles.useCase}>Approval Workflows</span>
            <span className={styles.useCase}>Multi-Step Verification</span>
            <span className={styles.useCase}>Trial → Conversion</span>
            <span className={styles.useCase}>Order Fulfillment</span>
            <span className={styles.useCase}>Scheduled Reports</span>
          </div>
        </div>
      </div>
    </section>
  );
}
