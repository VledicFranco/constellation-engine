import styles from './styles.module.css';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

const features: Feature[] = [
  {
    icon: '\u{1F6E1}',
    title: 'Type Safety',
    description:
      'Compile-time type checking catches field typos and type mismatches before your pipeline runs. No more runtime surprises from field mapping errors.',
  },
  {
    icon: '\u{1F4DD}',
    title: 'Declarative DSL',
    description:
      'constellation-lang is a readable, hot-reloadable DSL that separates pipeline logic from implementation. Change behavior without recompiling Scala.',
  },
  {
    icon: '\u{26A1}',
    title: 'Automatic Parallelization',
    description:
      'Independent branches in your DAG run concurrently on Cats Effect fibers. The engine handles scheduling and dependency resolution automatically.',
  },
  {
    icon: '\u{1F504}',
    title: 'Resilience Built In',
    description:
      'Retry, timeout, fallback, cache, and throttle are declarative "with" clauses on any module call. No boilerplate resilience code.',
  },
  {
    icon: '\u{1F4BB}',
    title: 'IDE Support',
    description:
      'VSCode extension with autocomplete, inline errors, hover types, and DAG visualization. Full Language Server Protocol support.',
  },
  {
    icon: '\u{1F680}',
    title: 'Production Ready',
    description:
      'Docker, Kubernetes, auth, CORS, rate limiting, health checks, and SPI for custom metrics and tracing. Deploy with confidence.',
  },
];

export default function HomepageFeatures(): JSX.Element {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Why Constellation?</h2>
      <div className={styles.grid}>
        {features.map((feature) => (
          <div key={feature.title} className={styles.card}>
            <div className={styles.icon}>{feature.icon}</div>
            <h3 className={styles.cardTitle}>{feature.title}</h3>
            <p className={styles.cardDescription}>{feature.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
