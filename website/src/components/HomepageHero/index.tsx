import Link from '@docusaurus/Link';
import styles from './styles.module.css';

// Static constellation node positions (percentage-based)
const nodes = [
  {cx: 10, cy: 20}, {cx: 25, cy: 15}, {cx: 40, cy: 25},
  {cx: 55, cy: 10}, {cx: 70, cy: 20}, {cx: 85, cy: 15},
  {cx: 15, cy: 50}, {cx: 30, cy: 60}, {cx: 50, cy: 45},
  {cx: 65, cy: 55}, {cx: 80, cy: 50}, {cx: 90, cy: 40},
  {cx: 20, cy: 80}, {cx: 45, cy: 75}, {cx: 75, cy: 85},
];

// Edges connecting nodes (index pairs)
const edges = [
  [0, 1], [1, 2], [2, 3], [3, 4], [4, 5],
  [0, 6], [1, 8], [2, 8], [4, 10], [5, 11],
  [6, 7], [7, 8], [8, 9], [9, 10], [10, 11],
  [6, 12], [7, 13], [9, 14], [12, 13], [13, 14],
];

export default function HomepageHero(): JSX.Element {
  return (
    <header className={styles.hero}>
      <div className={styles.heroBackground} />
      <svg className={styles.constellation} viewBox="0 0 100 100" preserveAspectRatio="none">
        {edges.map(([from, to], i) => (
          <line
            key={`edge-${i}`}
            className={styles.edge}
            x1={`${nodes[from].cx}%`}
            y1={`${nodes[from].cy}%`}
            x2={`${nodes[to].cx}%`}
            y2={`${nodes[to].cy}%`}
          />
        ))}
        {nodes.map((node, i) => (
          <circle
            key={`node-${i}`}
            className={styles.node}
            cx={`${node.cx}%`}
            cy={`${node.cy}%`}
            r="3"
          />
        ))}
      </svg>
      <div className={styles.content}>
        <h1 className={styles.title}>Constellation Engine</h1>
        <p className={styles.tagline}>
          Type-safe ML pipeline orchestration for Scala
        </p>
        <div className={styles.buttons}>
          <Link className={styles.primaryButton} to="/docs/getting-started/tutorial">
            Get Started
          </Link>
          <Link
            className={styles.secondaryButton}
            href="https://github.com/VledicFranco/constellation-engine"
          >
            View on GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}
