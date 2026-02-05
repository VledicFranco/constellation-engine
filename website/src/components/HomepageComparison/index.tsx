import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

interface ComparisonExample {
  id: string;
  title: string;
  subtitle: string;
  benefits: string[];
  scala: string;
  constellation: string;
  scalaLines: number;
  constellationLines: number;
}

const examples: ComparisonExample[] = [
  {
    id: 'api-composition',
    title: 'API Composition (BFF)',
    subtitle: 'Aggregate multiple backend services with automatic parallelization',
    benefits: [
      'Automatic parallel execution of independent calls',
      'Compile-time field validation',
      'Built-in resilience (retry, fallback, cache)',
    ],
    scalaLines: 45,
    constellationLines: 12,
    scala: `// Scala: Manual parallel composition with error handling
def enrichOrder(orderId: String): IO[EnrichedOrder] = {
  for {
    order <- orderService.getOrder(orderId)

    // Manual parallelization with parMapN
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

    // Manual retry logic
    pricing <- retryWithBackoff(
      pricingService.calculate(order, customer.tier),
      maxRetries = 3,
      initialDelay = 100.millis
    )

    // Manual field mapping (no compile-time validation!)
    enriched = EnrichedOrder(
      id = order.id,
      customerName = customer.name,  // Typo here won't be caught!
      items = order.items,
      stock = inventory.available,
      shipping = shipping.estimate,
      total = pricing.total,
      discount = pricing.discount
    )
  } yield enriched
}`,
    constellation: `# Constellation: Declarative composition with automatic parallelization
in orderId: String

order = GetOrder(orderId)
customer = GetCustomer(order.customerId)  with fallback: defaultCustomer
inventory = CheckStock(order.items)       with timeout: 5s, fallback: unknownStock
shipping = EstimateShipping(order.address) with timeout: 3s, fallback: defaultShipping
pricing = CalculatePricing(order, customer.tier) with retry: 3, backoff: exponential

# Type-safe merge + projection - compiler validates all fields exist
enriched = order + customer + inventory + shipping + pricing

out enriched[id, name, items, available, estimate, total, discount]`,
  },
  {
    id: 'resilient-pipeline',
    title: 'Resilient Data Pipeline',
    subtitle: 'Production-ready pipelines with caching, retries, and fallbacks',
    benefits: [
      'Declarative resilience patterns',
      'Automatic circuit breaking',
      'Response caching with TTL',
    ],
    scalaLines: 52,
    constellationLines: 15,
    scala: `// Scala: Manual resilience implementation
class ResilientPipeline(
  cache: Cache[String, UserProfile],
  circuitBreaker: CircuitBreaker
) {
  def enrichUser(userId: String): IO[EnrichedUser] = {
    // Manual cache check
    cache.get(userId).flatMap {
      case Some(cached) => IO.pure(cached)
      case None =>
        // Circuit breaker wrapper
        circuitBreaker.protect(
          // Retry with exponential backoff
          retry(
            primaryApi.fetchProfile(userId),
            RetryPolicy.exponentialBackoff(
              baseDelay = 100.millis,
              maxRetries = 3
            )
          )
        ).handleErrorWith { _ =>
          // Fallback to secondary
          retry(
            secondaryApi.fetchProfile(userId),
            RetryPolicy.fixed(2, 200.millis)
          ).handleErrorWith { _ =>
            // Final fallback to cache/default
            cache.getStale(userId)
              .getOrElse(defaultProfile)
          }
        }.flatTap(profile => cache.set(userId, profile, ttl = 5.minutes))
    }.flatMap { profile =>
      // More manual parallelization
      (
        preferencesService.get(userId).handleErrorWith(_ => IO.pure(defaultPrefs)),
        activityService.getRecent(userId).handleErrorWith(_ => IO.pure(Nil))
      ).parMapN { (prefs, activity) =>
        EnrichedUser(
          profile = profile,
          preferences = prefs,
          recentActivity = activity,
          enrichedAt = Instant.now()
        )
      }
    }
  }
}`,
    constellation: `# Constellation: Declarative resilience
in userId: String

# Primary with full resilience stack
profile = FetchProfile(userId) with {
  cache: 5m,
  retry: 3,
  backoff: exponential,
  circuit_breaker: true,
  fallback: secondaryProfile
}

secondaryProfile = FetchProfileSecondary(userId) with retry: 2, fallback: defaultProfile

# Parallel enrichment with individual fallbacks
preferences = GetPreferences(userId) with fallback: defaultPrefs
activity = GetRecentActivity(userId) with fallback: []

out profile + preferences + { recentActivity: activity }`,
  },
  {
    id: 'ml-inference',
    title: 'ML Inference Pipeline',
    subtitle: 'Feature extraction, model scoring, and result aggregation',
    benefits: [
      'Type-safe feature vectors',
      'Automatic batching with Candidates<T>',
      'Compile-time schema validation',
    ],
    scalaLines: 58,
    constellationLines: 18,
    scala: `// Scala: ML pipeline with manual orchestration
def scoreLeads(leads: List[Lead]): IO[List[ScoredLead]] = {
  leads.parTraverse { lead =>
    for {
      // Feature extraction - manual field access
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
        downloadedContent = if (behaviorFeatures.downloads > 0) 1.0 else 0.0,
        // Easy to miss a feature or get the order wrong!
      )

      // Model inference
      modelScore <- mlService.predict(featureVector)
        .timeout(500.millis)
        .handleErrorWith(_ => IO.pure(0.5)) // Default score

      // Manual threshold logic
      tier = modelScore match {
        case s if s >= 0.8 => "hot"
        case s if s >= 0.5 => "warm"
        case _ => "cold"
      }

      // Construct result
      scored = ScoredLead(
        id = lead.id,
        email = lead.email,
        company = lead.company,
        score = modelScore,
        tier = tier,
        features = featureVector,
        scoredAt = Instant.now()
      )
    } yield scored
  }
}`,
    constellation: `# Constellation: Type-safe ML pipeline
type Lead = { id: String, email: String, company: String, visitorId: String }

in leads: Candidates<Lead>

# Parallel feature extraction with fallbacks
companyFeatures = EnrichCompany(leads.company) with fallback: defaultCompanyFeatures
behaviorFeatures = AnalyzeBehavior(leads.visitorId) with timeout: 2s, fallback: defaultBehavior

# Type-safe feature merge - compiler validates schema
features = companyFeatures + behaviorFeatures

# Model scoring with timeout
rawScore = PredictScore(features) with timeout: 500ms, fallback: 0.5

# Declarative conditional logic
tier = branch {
  "hot"  when rawScore >= 0.8,
  "warm" when rawScore >= 0.5,
  "cold" otherwise
}

out leads + { score: rawScore, tier: tier, features: features }`,
  },
  {
    id: 'data-enrichment',
    title: 'Batch Data Enrichment',
    subtitle: 'Enrich records from multiple sources with automatic fan-out',
    benefits: [
      'Candidates<T> for automatic batching',
      'Lazy evaluation for efficiency',
      'Type-safe projections',
    ],
    scalaLines: 42,
    constellationLines: 14,
    scala: `// Scala: Manual batch enrichment
def enrichCustomers(customerIds: List[String]): IO[List[EnrichedCustomer]] = {
  // Batch fetch with manual chunking for rate limiting
  customerIds.grouped(100).toList.flatTraverse { batch =>
    for {
      // Parallel lookups within batch
      customers <- batch.parTraverse(id =>
        customerDb.findById(id).handleErrorWith(_ => IO.pure(None))
      ).map(_.flatten)

      // Secondary enrichment
      enriched <- customers.parTraverse { customer =>
        (
          // Manual parallel composition
          orderHistory.getOrders(customer.id)
            .map(orders => orders.map(_.total).sum)
            .handleErrorWith(_ => IO.pure(0.0)),

          supportTickets.getTickets(customer.id)
            .map(_.length)
            .handleErrorWith(_ => IO.pure(0)),

          segmentationService.classify(customer)
            .handleErrorWith(_ => IO.pure("unknown"))
        ).parMapN { (totalSpent, ticketCount, segment) =>
          EnrichedCustomer(
            id = customer.id,
            name = customer.name,
            email = customer.email,
            totalSpent = totalSpent,
            supportTickets = ticketCount,
            segment = segment,
            isHighValue = totalSpent > 10000 && ticketCount < 3
          )
        }
      }
    } yield enriched
  }
}`,
    constellation: `# Constellation: Declarative batch enrichment
in customerIds: Candidates<String>

# Automatic fan-out to all records
customers = FetchCustomers(customerIds)

# Parallel enrichment per record
orders = GetOrderHistory(customers.id) with fallback: []
tickets = GetSupportTickets(customers.id) with fallback: []

totalSpent = sum(orders[].total)
ticketCount = length(tickets)
segment = ClassifyCustomer(customers) with fallback: "unknown"

isHighValue = totalSpent > 10000 and ticketCount < 3

out customers + { totalSpent, ticketCount, segment, isHighValue }`,
  },
];

export default function HomepageComparison(): JSX.Element {
  const [activeExample, setActiveExample] = useState(examples[0].id);
  const example = examples.find((e) => e.id === activeExample) || examples[0];

  const reduction = Math.round(
    ((example.scalaLines - example.constellationLines) / example.scalaLines) * 100
  );

  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>See the Difference</h2>
      <p className={styles.sectionSubtitle}>
        Compare verbose Scala orchestration code with clean, declarative Constellation pipelines
      </p>

      {/* Tab navigation */}
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

      {/* Benefits */}
      <div className={styles.benefits}>
        {example.benefits.map((benefit, i) => (
          <span key={i} className={styles.benefit}>
            ✓ {benefit}
          </span>
        ))}
      </div>

      {/* Code comparison */}
      <div className={styles.comparison}>
        <div className={styles.panel}>
          <div className={`${styles.panelHeader} ${styles.panelHeaderScala}`}>
            <span>Without Constellation (Scala)</span>
            <span className={styles.lineCount}>{example.scalaLines} lines</span>
          </div>
          <div className={styles.panelBody}>
            <CodeBlock language="scala" showLineNumbers>
              {example.scala}
            </CodeBlock>
          </div>
        </div>

        <div className={styles.arrow}>
          <div className={styles.arrowContent}>
            <span className={styles.arrowIcon}>→</span>
            <span className={styles.reduction}>{reduction}% less code</span>
          </div>
        </div>

        <div className={styles.panel}>
          <div className={`${styles.panelHeader} ${styles.panelHeaderConstellation}`}>
            <span>With Constellation</span>
            <span className={styles.lineCount}>{example.constellationLines} lines</span>
          </div>
          <div className={styles.panelBody}>
            <CodeBlock language="constellation" showLineNumbers>
              {example.constellation}
            </CodeBlock>
          </div>
        </div>
      </div>

      {/* Key insight */}
      <div className={styles.insight}>
        <strong>Key insight:</strong> Constellation lets you focus on <em>what</em> your pipeline does,
        not <em>how</em> to handle parallelization, error recovery, caching, and retries.
        The compiler validates types at build time, catching field typos before deployment.
      </div>
    </section>
  );
}
