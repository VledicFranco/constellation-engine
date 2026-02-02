import styles from './styles.module.css';

interface UseCase {
  icon: string;
  title: string;
  description: string;
}

const useCases: UseCase[] = [
  {
    icon: '\u{1F9E0}',
    title: 'ML Inference Pipelines',
    description:
      'Compose models, feature extraction, and scoring into type-safe DAGs. The compiler validates every field flows correctly between pipeline stages.',
  },
  {
    icon: '\u{1F310}',
    title: 'API Composition (BFF)',
    description:
      'Aggregate backend services with compile-time field validation. Build Backend-for-Frontend layers where field mapping bugs are caught before deployment.',
  },
  {
    icon: '\u{1F4CA}',
    title: 'Data Enrichment',
    description:
      'Batch processing with Candidates<T>, parallel execution, and automatic dependency resolution. Enrich records from multiple sources in a single pipeline.',
  },
];

export default function HomepageUseCases(): JSX.Element {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Built For</h2>
      <div className={styles.grid}>
        {useCases.map((uc) => (
          <div key={uc.title} className={styles.card}>
            <div className={styles.icon}>{uc.icon}</div>
            <h3 className={styles.cardTitle}>{uc.title}</h3>
            <p className={styles.cardDescription}>{uc.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
