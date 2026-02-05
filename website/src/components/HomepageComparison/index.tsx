import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

interface ComparisonExample {
  id: string;
  title: string;
  subtitle: string;
  keyPoints: string[];
  // Traditional approach - everything mixed together
  traditional: string;
  // Constellation approach - separated concerns
  pipeline: string;        // .cst DSL
  implementations: string; // Scala module implementations
  // DAG visualization data
  dag: DagNode[];
}

interface DagNode {
  id: string;
  label: string;
  type: 'input' | 'module' | 'output' | 'computed';
  x: number;
  y: number;
  dependsOn?: string[];
}

const examples: ComparisonExample[] = [
  {
    id: 'api-composition',
    title: 'API Composition (BFF)',
    subtitle: 'Aggregate multiple backend services into a single response',
    keyPoints: [
      'Pipeline logic is declarative and auditable',
      'Module implementations are reusable across pipelines',
      'Dependencies visualized as a DAG for debugging',
    ],
    traditional: `// Traditional: Pipeline logic mixed with implementation details
class OrderEnrichmentService(
  orderService: OrderService,
  customerService: CustomerService,
  inventoryService: InventoryService,
  shippingService: ShippingService,
  pricingService: PricingService
) {
  def enrichOrder(orderId: String): IO[EnrichedOrder] = {
    for {
      order <- orderService.getOrder(orderId)

      // Parallelization logic embedded in business code
      (customer, inventory, shipping) <- (
        customerService.getCustomer(order.customerId)
          .handleErrorWith(_ => IO.pure(defaultCustomer)),
        inventoryService.checkStock(order.items)
          .timeout(5.seconds)
          .handleErrorWith(_ => IO.pure(unknownStock)),
        shippingService.estimate(order.address)
          .timeout(3.seconds)
          .handleErrorWith(_ => IO.pure(defaultShipping))
      ).parMapN((c, i, s) => (c, i, s))

      // Retry logic scattered throughout
      pricing <- retryWithBackoff(
        pricingService.calculate(order, customer.tier),
        maxRetries = 3, initialDelay = 100.millis
      )

      // Manual field mapping - typos not caught at compile time
      enriched = EnrichedOrder(
        id = order.id,
        customerName = customer.name,
        items = order.items,
        stock = inventory.available,
        shipping = shipping.estimate,
        total = pricing.total,
        discount = pricing.discount
      )
    } yield enriched
  }
}`,
    pipeline: `# order-enrichment.cst
# Declarative pipeline - defines WHAT to compute, not HOW

in orderId: String

# Each line declares a data dependency
# Runtime automatically parallelizes independent calls
order      = GetOrder(orderId)
customer   = GetCustomer(order.customerId)   with fallback: defaultCustomer
inventory  = CheckStock(order.items)         with timeout: 5s, fallback: unknownStock
shipping   = EstimateShipping(order.address) with timeout: 3s, fallback: defaultShipping
pricing    = CalculatePricing(order, customer.tier) with retry: 3

# Type-safe merge - compiler validates all fields exist
enriched = order + customer + inventory + shipping + pricing

out enriched[id, name, items, available, estimate, total, discount]`,
    implementations: `// modules/OrderModules.scala
// Pure business logic - no orchestration concerns

val getOrder = ModuleBuilder
  .metadata("GetOrder", "Fetch order by ID")
  .implementation[OrderId, Order] { id =>
    orderRepository.findById(id.value)
  }.build

val getCustomer = ModuleBuilder
  .metadata("GetCustomer", "Fetch customer profile")
  .implementation[CustomerId, Customer] { id =>
    customerRepository.findById(id.value)
  }.build

val checkStock = ModuleBuilder
  .metadata("CheckStock", "Check inventory levels")
  .implementation[Items, StockStatus] { items =>
    inventoryService.checkAvailability(items.value)
  }.build

val estimateShipping = ModuleBuilder
  .metadata("EstimateShipping", "Calculate shipping estimate")
  .implementation[Address, ShippingEstimate] { addr =>
    shippingCalculator.estimate(addr.value)
  }.build

val calculatePricing = ModuleBuilder
  .metadata("CalculatePricing", "Compute final pricing")
  .implementation[PricingInput, Pricing] { input =>
    pricingEngine.calculate(input.order, input.tier)
  }.build`,
    dag: [
      { id: 'orderId', label: 'orderId', type: 'input', x: 250, y: 20 },
      { id: 'order', label: 'GetOrder', type: 'module', x: 250, y: 80, dependsOn: ['orderId'] },
      { id: 'customer', label: 'GetCustomer', type: 'module', x: 100, y: 150, dependsOn: ['order'] },
      { id: 'inventory', label: 'CheckStock', type: 'module', x: 250, y: 150, dependsOn: ['order'] },
      { id: 'shipping', label: 'EstimateShipping', type: 'module', x: 400, y: 150, dependsOn: ['order'] },
      { id: 'pricing', label: 'CalculatePricing', type: 'module', x: 175, y: 220, dependsOn: ['order', 'customer'] },
      { id: 'enriched', label: 'merge +', type: 'computed', x: 250, y: 290, dependsOn: ['order', 'customer', 'inventory', 'shipping', 'pricing'] },
      { id: 'out', label: 'output', type: 'output', x: 250, y: 350, dependsOn: ['enriched'] },
    ],
  },
  {
    id: 'resilient-pipeline',
    title: 'Resilient Data Pipeline',
    subtitle: 'Production-ready data flow with caching, retries, and fallbacks',
    keyPoints: [
      'Resilience policies declared inline, not buried in code',
      'Fallback chains visible in pipeline definition',
      'Each module testable in isolation',
    ],
    traditional: `// Traditional: Resilience logic intertwined with business logic
class ResilientUserService(
  cache: Cache[String, UserProfile],
  circuitBreaker: CircuitBreaker,
  primaryApi: ProfileApi,
  secondaryApi: ProfileApi,
  prefsService: PreferencesService,
  activityService: ActivityService
) {
  def enrichUser(userId: String): IO[EnrichedUser] = {
    cache.get(userId).flatMap {
      case Some(cached) => IO.pure(cached)
      case None =>
        circuitBreaker.protect(
          retry(primaryApi.fetchProfile(userId),
            RetryPolicy.exponentialBackoff(100.millis, maxRetries = 3))
        ).handleErrorWith { _ =>
          retry(secondaryApi.fetchProfile(userId),
            RetryPolicy.fixed(2, 200.millis)
          ).handleErrorWith { _ =>
            cache.getStale(userId).getOrElse(defaultProfile)
          }
        }.flatTap(p => cache.set(userId, p, ttl = 5.minutes))
    }.flatMap { profile =>
      (
        prefsService.get(userId).handleErrorWith(_ => IO.pure(defaultPrefs)),
        activityService.getRecent(userId).handleErrorWith(_ => IO.pure(Nil))
      ).parMapN { (prefs, activity) =>
        EnrichedUser(profile, prefs, activity, Instant.now())
      }
    }
  }
}`,
    pipeline: `# user-enrichment.cst
# Resilience policies are declarative and visible

in userId: String

# Primary source with full resilience stack
profile = FetchProfile(userId) with {
  cache: 5m,
  retry: 3,
  backoff: exponential,
  circuit_breaker: true,
  fallback: secondaryProfile
}

# Fallback chain is explicit in the pipeline
secondaryProfile = FetchProfileSecondary(userId) with {
  retry: 2,
  fallback: defaultProfile
}

# Parallel enrichment - runtime handles coordination
preferences = GetPreferences(userId) with fallback: defaultPrefs
activity = GetRecentActivity(userId) with fallback: []

out profile + preferences + { recentActivity: activity }`,
    implementations: `// modules/UserModules.scala
// Clean implementations - resilience handled by runtime

val fetchProfile = ModuleBuilder
  .metadata("FetchProfile", "Fetch from primary API")
  .implementation[UserId, UserProfile] { id =>
    primaryApi.getProfile(id.value)
  }.build

val fetchProfileSecondary = ModuleBuilder
  .metadata("FetchProfileSecondary", "Fetch from backup API")
  .implementation[UserId, UserProfile] { id =>
    secondaryApi.getProfile(id.value)
  }.build

val getPreferences = ModuleBuilder
  .metadata("GetPreferences", "Load user preferences")
  .implementation[UserId, Preferences] { id =>
    preferencesStore.load(id.value)
  }.build

val getRecentActivity = ModuleBuilder
  .metadata("GetRecentActivity", "Fetch recent actions")
  .implementation[UserId, List[Activity]] { id =>
    activityLog.getRecent(id.value, limit = 10)
  }.build`,
    dag: [
      { id: 'userId', label: 'userId', type: 'input', x: 200, y: 20 },
      { id: 'profile', label: 'FetchProfile', type: 'module', x: 100, y: 100, dependsOn: ['userId'] },
      { id: 'secondary', label: 'FetchProfileSecondary', type: 'module', x: 100, y: 170, dependsOn: ['userId'] },
      { id: 'prefs', label: 'GetPreferences', type: 'module', x: 250, y: 100, dependsOn: ['userId'] },
      { id: 'activity', label: 'GetRecentActivity', type: 'module', x: 350, y: 100, dependsOn: ['userId'] },
      { id: 'merged', label: 'merge +', type: 'computed', x: 200, y: 240, dependsOn: ['profile', 'prefs', 'activity'] },
      { id: 'out', label: 'output', type: 'output', x: 200, y: 300, dependsOn: ['merged'] },
    ],
  },
  {
    id: 'ml-inference',
    title: 'ML Inference Pipeline',
    subtitle: 'Feature extraction, model scoring, and result aggregation',
    keyPoints: [
      'Feature engineering steps clearly visible',
      'Model scoring isolated and swappable',
      'Branching logic declarative, not procedural',
    ],
    traditional: `// Traditional: Feature engineering mixed with orchestration
class LeadScoringPipeline(
  companyEnrichment: CompanyEnrichment,
  behaviorAnalysis: BehaviorAnalysis,
  mlService: MLService
) {
  def scoreLeads(leads: List[Lead]): IO[List[ScoredLead]] = {
    leads.parTraverse { lead =>
      for {
        companyFeatures <- companyEnrichment.enrich(lead.company)
          .handleErrorWith(_ => IO.pure(defaultCompanyFeatures))

        behaviorFeatures <- behaviorAnalysis.analyze(lead.visitorId)
          .timeout(2.seconds)
          .handleErrorWith(_ => IO.pure(defaultBehaviorFeatures))

        // Manual feature vector construction
        featureVector = FeatureVector(
          companySize = companyFeatures.employeeCount.toDouble,
          industry = oneHotEncode(companyFeatures.industry),
          pageViews = behaviorFeatures.pageViews.toDouble,
          timeOnSite = behaviorFeatures.avgTimeOnSite,
          downloadedContent = if (behaviorFeatures.downloads > 0) 1.0 else 0.0
        )

        modelScore <- mlService.predict(featureVector)
          .timeout(500.millis)
          .handleErrorWith(_ => IO.pure(0.5))

        tier = modelScore match {
          case s if s >= 0.8 => "hot"
          case s if s >= 0.5 => "warm"
          case _ => "cold"
        }
      } yield ScoredLead(lead.id, lead.email, modelScore, tier, featureVector)
    }
  }
}`,
    pipeline: `# lead-scoring.cst
# ML pipeline with clear feature flow

type Lead = { id: String, email: String, company: String, visitorId: String }

in leads: Candidates<Lead>

# Feature extraction - runs in parallel per lead
companyFeatures = EnrichCompany(leads.company) with {
  fallback: defaultCompanyFeatures
}
behaviorFeatures = AnalyzeBehavior(leads.visitorId) with {
  timeout: 2s,
  fallback: defaultBehavior
}

# Merge features for scoring
features = companyFeatures + behaviorFeatures

# Model inference
rawScore = PredictScore(features) with timeout: 500ms, fallback: 0.5

# Declarative branching - easier to understand and modify
tier = branch {
  "hot"  when rawScore >= 0.8,
  "warm" when rawScore >= 0.5,
  "cold" otherwise
}

out leads + { score: rawScore, tier: tier, features: features }`,
    implementations: `// modules/MLModules.scala
// Each ML step is a focused, testable module

val enrichCompany = ModuleBuilder
  .metadata("EnrichCompany", "Enrich company data")
  .implementation[CompanyName, CompanyFeatures] { name =>
    companyDataProvider.enrich(name.value)
  }.build

val analyzeBehavior = ModuleBuilder
  .metadata("AnalyzeBehavior", "Analyze visitor behavior")
  .implementation[VisitorId, BehaviorFeatures] { id =>
    behaviorTracker.analyze(id.value)
  }.build

val predictScore = ModuleBuilder
  .metadata("PredictScore", "Run ML model inference")
  .implementation[Features, Score] { features =>
    mlModel.predict(features.toVector)
  }.build

// branch expressions are handled by the runtime
// no Scala implementation needed`,
    dag: [
      { id: 'leads', label: 'leads', type: 'input', x: 200, y: 20 },
      { id: 'company', label: 'EnrichCompany', type: 'module', x: 100, y: 90, dependsOn: ['leads'] },
      { id: 'behavior', label: 'AnalyzeBehavior', type: 'module', x: 300, y: 90, dependsOn: ['leads'] },
      { id: 'features', label: 'merge +', type: 'computed', x: 200, y: 160, dependsOn: ['company', 'behavior'] },
      { id: 'score', label: 'PredictScore', type: 'module', x: 200, y: 230, dependsOn: ['features'] },
      { id: 'tier', label: 'branch', type: 'computed', x: 200, y: 300, dependsOn: ['score'] },
      { id: 'out', label: 'output', type: 'output', x: 200, y: 370, dependsOn: ['leads', 'score', 'tier', 'features'] },
    ],
  },
  {
    id: 'data-enrichment',
    title: 'Batch Data Enrichment',
    subtitle: 'Enrich records from multiple sources with fan-out/fan-in',
    keyPoints: [
      'Candidates<T> enables automatic batch processing',
      'Computed fields clearly derived from sources',
      'Easy to add new enrichment sources',
    ],
    traditional: `// Traditional: Batch processing with manual coordination
class CustomerEnrichmentJob(
  customerDb: CustomerRepository,
  orderHistory: OrderHistoryService,
  supportTickets: SupportTicketService,
  segmentation: SegmentationService
) {
  def enrichCustomers(customerIds: List[String]): IO[List[EnrichedCustomer]] = {
    customerIds.grouped(100).toList.flatTraverse { batch =>
      for {
        customers <- batch.parTraverse(id =>
          customerDb.findById(id).handleErrorWith(_ => IO.pure(None))
        ).map(_.flatten)

        enriched <- customers.parTraverse { customer =>
          (
            orderHistory.getOrders(customer.id)
              .map(orders => orders.map(_.total).sum)
              .handleErrorWith(_ => IO.pure(0.0)),
            supportTickets.getTickets(customer.id)
              .map(_.length)
              .handleErrorWith(_ => IO.pure(0)),
            segmentation.classify(customer)
              .handleErrorWith(_ => IO.pure("unknown"))
          ).parMapN { (totalSpent, ticketCount, segment) =>
            EnrichedCustomer(
              customer.id, customer.name, customer.email,
              totalSpent, ticketCount, segment,
              isHighValue = totalSpent > 10000 && ticketCount < 3
            )
          }
        }
      } yield enriched
    }
  }
}`,
    pipeline: `# customer-enrichment.cst
# Batch enrichment with automatic fan-out

in customerIds: Candidates<String>

# Automatic fan-out: each module runs per customer
customers = FetchCustomers(customerIds)

# Parallel enrichment sources
orders     = GetOrderHistory(customers.id)     with fallback: []
tickets    = GetSupportTickets(customers.id)   with fallback: []
segment    = ClassifyCustomer(customers)       with fallback: "unknown"

# Computed fields - derived from enrichment data
totalSpent  = sum(orders[].total)
ticketCount = length(tickets)
isHighValue = totalSpent > 10000 and ticketCount < 3

# Final output merges all sources
out customers + { totalSpent, ticketCount, segment, isHighValue }`,
    implementations: `// modules/CustomerModules.scala
// Focused modules - each does one thing well

val fetchCustomers = ModuleBuilder
  .metadata("FetchCustomers", "Bulk fetch customers")
  .implementation[CustomerIds, List[Customer]] { ids =>
    customerRepository.findByIds(ids.value)
  }.build

val getOrderHistory = ModuleBuilder
  .metadata("GetOrderHistory", "Fetch order history")
  .implementation[CustomerId, List[Order]] { id =>
    orderRepository.findByCustomer(id.value)
  }.build

val getSupportTickets = ModuleBuilder
  .metadata("GetSupportTickets", "Fetch support tickets")
  .implementation[CustomerId, List[Ticket]] { id =>
    ticketRepository.findByCustomer(id.value)
  }.build

val classifyCustomer = ModuleBuilder
  .metadata("ClassifyCustomer", "Segment classification")
  .implementation[Customer, Segment] { customer =>
    segmentationModel.classify(customer)
  }.build

// sum, length computed by runtime - no module needed`,
    dag: [
      { id: 'ids', label: 'customerIds', type: 'input', x: 200, y: 20 },
      { id: 'customers', label: 'FetchCustomers', type: 'module', x: 200, y: 80, dependsOn: ['ids'] },
      { id: 'orders', label: 'GetOrderHistory', type: 'module', x: 80, y: 150, dependsOn: ['customers'] },
      { id: 'tickets', label: 'GetSupportTickets', type: 'module', x: 200, y: 150, dependsOn: ['customers'] },
      { id: 'segment', label: 'ClassifyCustomer', type: 'module', x: 320, y: 150, dependsOn: ['customers'] },
      { id: 'totalSpent', label: 'sum()', type: 'computed', x: 80, y: 220, dependsOn: ['orders'] },
      { id: 'ticketCount', label: 'length()', type: 'computed', x: 200, y: 220, dependsOn: ['tickets'] },
      { id: 'isHighValue', label: 'computed', type: 'computed', x: 140, y: 290, dependsOn: ['totalSpent', 'ticketCount'] },
      { id: 'out', label: 'output', type: 'output', x: 200, y: 360, dependsOn: ['customers', 'totalSpent', 'ticketCount', 'segment', 'isHighValue'] },
    ],
  },
];

// Simple DAG visualization component
function DagVisualization({ nodes }: { nodes: DagNode[] }) {
  const width = 500;
  const height = 400;

  // Build edges from dependsOn
  const edges: { from: DagNode; to: DagNode }[] = [];
  nodes.forEach(node => {
    if (node.dependsOn) {
      node.dependsOn.forEach(depId => {
        const fromNode = nodes.find(n => n.id === depId);
        if (fromNode) {
          edges.push({ from: fromNode, to: node });
        }
      });
    }
  });

  const getNodeColor = (type: DagNode['type']) => {
    switch (type) {
      case 'input': return '#6366f1';
      case 'module': return '#2aa198';
      case 'computed': return '#d97706';
      case 'output': return '#dc2626';
      default: return '#666';
    }
  };

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className={styles.dagSvg}>
      <defs>
        <marker
          id="arrowhead"
          markerWidth="10"
          markerHeight="7"
          refX="9"
          refY="3.5"
          orient="auto"
        >
          <polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8" />
        </marker>
      </defs>

      {/* Edges */}
      {edges.map((edge, i) => (
        <line
          key={i}
          x1={edge.from.x}
          y1={edge.from.y + 15}
          x2={edge.to.x}
          y2={edge.to.y - 15}
          stroke="#94a3b8"
          strokeWidth="1.5"
          markerEnd="url(#arrowhead)"
        />
      ))}

      {/* Nodes */}
      {nodes.map(node => (
        <g key={node.id}>
          <rect
            x={node.x - 55}
            y={node.y - 12}
            width={110}
            height={24}
            rx={4}
            fill={getNodeColor(node.type)}
            opacity={0.9}
          />
          <text
            x={node.x}
            y={node.y + 4}
            textAnchor="middle"
            fill="white"
            fontSize="11"
            fontFamily="system-ui, -apple-system, sans-serif"
            fontWeight="500"
          >
            {node.label}
          </text>
        </g>
      ))}

      {/* Legend */}
      <g transform="translate(10, 360)">
        <rect x="0" y="0" width="10" height="10" fill="#6366f1" rx="2" />
        <text x="15" y="9" fontSize="9" fill="#64748b">Input</text>
        <rect x="50" y="0" width="10" height="10" fill="#2aa198" rx="2" />
        <text x="65" y="9" fontSize="9" fill="#64748b">Module</text>
        <rect x="115" y="0" width="10" height="10" fill="#d97706" rx="2" />
        <text x="130" y="9" fontSize="9" fill="#64748b">Computed</text>
        <rect x="190" y="0" width="10" height="10" fill="#dc2626" rx="2" />
        <text x="205" y="9" fontSize="9" fill="#64748b">Output</text>
      </g>
    </svg>
  );
}

export default function HomepageComparison(): JSX.Element {
  const [activeExample, setActiveExample] = useState(examples[0].id);
  const [constellationTab, setConstellationTab] = useState<'pipeline' | 'implementation' | 'dag'>('pipeline');
  const example = examples.find((e) => e.id === activeExample) || examples[0];

  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Composable Pipeline Architecture</h2>
      <p className={styles.sectionSubtitle}>
        Constellation separates <strong>what</strong> your pipeline computes from <strong>how</strong> each step is implemented.
        The result: pipelines you can visualize, debug, and evolve independently from business logic.
      </p>

      {/* Use case tabs */}
      <div className={styles.tabs}>
        {examples.map((ex) => (
          <button
            key={ex.id}
            className={`${styles.tab} ${activeExample === ex.id ? styles.tabActive : ''}`}
            onClick={() => setActiveExample(ex.id)}
          >
            {ex.title}
          </button>
        ))}
      </div>

      {/* Example header */}
      <div className={styles.exampleHeader}>
        <h3 className={styles.exampleTitle}>{example.title}</h3>
        <p className={styles.exampleSubtitle}>{example.subtitle}</p>
      </div>

      {/* Key points */}
      <div className={styles.keyPoints}>
        {example.keyPoints.map((point, i) => (
          <span key={i} className={styles.keyPoint}>
            {point}
          </span>
        ))}
      </div>

      {/* Code comparison */}
      <div className={styles.comparison}>
        {/* Traditional approach */}
        <div className={styles.panel}>
          <div className={`${styles.panelHeader} ${styles.panelHeaderTraditional}`}>
            <span>Traditional Approach</span>
            <span className={styles.panelBadge}>Mixed Concerns</span>
          </div>
          <div className={styles.panelBody}>
            <CodeBlock language="scala">
              {example.traditional}
            </CodeBlock>
          </div>
        </div>

        {/* Arrow */}
        <div className={styles.arrow}>
          <div className={styles.arrowContent}>
            <span className={styles.arrowIcon}>→</span>
            <span className={styles.arrowLabel}>Separate</span>
          </div>
        </div>

        {/* Constellation approach - with tabs */}
        <div className={styles.panel}>
          <div className={`${styles.panelHeader} ${styles.panelHeaderConstellation}`}>
            <span>With Constellation</span>
            <span className={styles.panelBadge}>Separated Concerns</span>
          </div>

          {/* Inner tabs for pipeline/implementation/dag */}
          <div className={styles.innerTabs}>
            <button
              className={`${styles.innerTab} ${constellationTab === 'pipeline' ? styles.innerTabActive : ''}`}
              onClick={() => setConstellationTab('pipeline')}
            >
              Pipeline Definition
            </button>
            <button
              className={`${styles.innerTab} ${constellationTab === 'implementation' ? styles.innerTabActive : ''}`}
              onClick={() => setConstellationTab('implementation')}
            >
              Module Implementations
            </button>
            <button
              className={`${styles.innerTab} ${constellationTab === 'dag' ? styles.innerTabActive : ''}`}
              onClick={() => setConstellationTab('dag')}
            >
              Compiled DAG
            </button>
          </div>

          <div className={styles.panelBody}>
            {constellationTab === 'pipeline' && (
              <CodeBlock language="python">
                {example.pipeline}
              </CodeBlock>
            )}
            {constellationTab === 'implementation' && (
              <CodeBlock language="scala">
                {example.implementations}
              </CodeBlock>
            )}
            {constellationTab === 'dag' && (
              <div className={styles.dagContainer}>
                <DagVisualization nodes={example.dag} />
                <p className={styles.dagCaption}>
                  The pipeline compiles to a DAG. Independent nodes (same row) execute in parallel.
                  Hover over nodes in the dashboard to inspect values during debugging.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Key insight */}
      <div className={styles.insight}>
        <div className={styles.insightIcon}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 16v-4M12 8h.01" />
          </svg>
        </div>
        <div className={styles.insightContent}>
          <strong>Why this matters:</strong> When pipeline logic is separate from implementations, you can:
          <ul>
            <li>Visualize and debug the data flow without reading code</li>
            <li>Swap implementations (e.g., mock → real) without changing the pipeline</li>
            <li>Reuse the same modules across different pipelines</li>
            <li>Let the compiler catch field typos and type mismatches</li>
          </ul>
        </div>
      </div>
    </section>
  );
}
