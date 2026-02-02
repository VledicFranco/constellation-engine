import styles from './styles.module.css';

interface Stat {
  value: string;
  label: string;
  detail: string;
}

const stats: Stat[] = [
  {
    value: '~0.15ms',
    label: 'Per-Node Overhead',
    detail: 'At 50+ modules, your services are the bottleneck',
  },
  {
    value: '0.06ms',
    label: 'p50 Latency',
    detail: 'Sustained across 10,000 executions',
  },
  {
    value: 'Stable',
    label: 'Heap at ~95 MB',
    detail: 'No growth or degradation over 10K+ runs',
  },
];

export default function HomepageStats(): JSX.Element {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Performance</h2>
      <div className={styles.grid}>
        {stats.map((stat) => (
          <div key={stat.label} className={styles.stat}>
            <div className={styles.value}>{stat.value}</div>
            <div className={styles.label}>{stat.label}</div>
            <p className={styles.detail}>{stat.detail}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
