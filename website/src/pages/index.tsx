import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageHero from '@site/src/components/HomepageHero';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageCodeExample from '@site/src/components/HomepageCodeExample';
import HomepageUseCases from '@site/src/components/HomepageUseCases';
import HomepageArchitecture from '@site/src/components/HomepageArchitecture';
import HomepageStats from '@site/src/components/HomepageStats';
import styles from './index.module.css';

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Type-safe pipeline orchestration for Scala"
    >
      <main className={styles.main}>
        <HomepageHero />
        <HomepageFeatures />
        <HomepageCodeExample />
        <HomepageUseCases />
        <HomepageArchitecture />
        <HomepageStats />
      </main>
    </Layout>
  );
}
