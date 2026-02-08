import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageHero from '@site/src/components/HomepageHero';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HomepageCodeExample from '@site/src/components/HomepageCodeExample';
import HomepageUseCases from '@site/src/components/HomepageUseCases';
import HomepageCLI from '@site/src/components/HomepageCLI';
import HomepageDashboard from '@site/src/components/HomepageDashboard';
import HomepageLSP from '@site/src/components/HomepageLSP';
import HomepageStats from '@site/src/components/HomepageStats';
import HomepageDeployment from '@site/src/components/HomepageDeployment';
import HomepageSuspended from '@site/src/components/HomepageSuspended';
import HomepageArchitecture from '@site/src/components/HomepageArchitecture';
import HomepageComparison from '@site/src/components/HomepageComparison';
import styles from './index.module.css';

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Type-safe pipeline orchestration for Scala"
    >
      <main className={styles.main}>
        {/* 1. Hook: What is this? */}
        <HomepageHero />

        {/* 2. Value proposition: Why do you need this? */}
        <HomepageFeatures />

        {/* 3. Show don't tell: How does it work? */}
        <HomepageCodeExample />

        {/* 4. Relevance: Who is this for? */}
        <HomepageUseCases />

        {/* 5-7. Developer Experience: What's it like to use? */}
        <HomepageCLI />
        <HomepageDashboard />
        <HomepageLSP />

        {/* 8-9. Production Ready: Can I trust it? */}
        <HomepageStats />
        <HomepageDeployment />

        {/* 10. Advanced capabilities */}
        <HomepageSuspended />

        {/* 11. Technical depth */}
        <HomepageArchitecture />

        {/* 12. Comparison for decision-makers */}
        <HomepageComparison />
      </main>
    </Layout>
  );
}
