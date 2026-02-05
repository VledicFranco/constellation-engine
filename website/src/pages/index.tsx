import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageHero from '@site/src/components/HomepageHero';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageCodeExample from '@site/src/components/HomepageCodeExample';
import HomepageComparison from '@site/src/components/HomepageComparison';
import HomepageUseCases from '@site/src/components/HomepageUseCases';
import HomepageArchitecture from '@site/src/components/HomepageArchitecture';
import HomepageStats from '@site/src/components/HomepageStats';
import HomepageLSP from '@site/src/components/HomepageLSP';
import HomepageDashboard from '@site/src/components/HomepageDashboard';
import HomepageSuspended from '@site/src/components/HomepageSuspended';
import HomepageDeployment from '@site/src/components/HomepageDeployment';
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
        <HomepageLSP />
        <HomepageDashboard />
        <HomepageSuspended />
        <HomepageDeployment />
        <HomepageComparison />
      </main>
    </Layout>
  );
}
