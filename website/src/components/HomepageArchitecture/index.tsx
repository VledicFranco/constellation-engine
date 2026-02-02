import styles from './styles.module.css';

interface Stage {
  name: string;
  detail: string;
}

const stages: Stage[] = [
  {name: 'constellation-lang', detail: '.cst source'},
  {name: 'Parser', detail: 'AST'},
  {name: 'TypeChecker', detail: 'Semantic types'},
  {name: 'IR Generator', detail: 'Intermediate repr'},
  {name: 'DagCompiler', detail: 'Execution DAG'},
  {name: 'Runtime', detail: 'Cats Effect IO'},
];

export default function HomepageArchitecture(): JSX.Element {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Compilation Pipeline</h2>
      <p className={styles.sectionSubtitle}>
        From declarative DSL to parallel execution in six stages
      </p>
      <div className={styles.pipeline}>
        {stages.map((stage, i) => (
          <div key={stage.name} style={{display: 'flex', alignItems: 'center'}}>
            <div className={styles.stage}>
              <span className={styles.stageName}>{stage.name}</span>
              <span className={styles.stageDetail}>{stage.detail}</span>
            </div>
            {i < stages.length - 1 && (
              <span className={styles.arrow}>{'\u2192'}</span>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
