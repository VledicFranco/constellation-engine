import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

const dslCode = `type Order = { id: String, customerId: String, items: List<Item>, total: Float }
type Customer = { name: String, tier: String }

in order: Order

customer = FetchCustomer(order.customerId)
shipping = EstimateShipping(order.id)

# Merge records - compiler validates all fields exist
enriched = order + customer + shipping

out enriched[id, name, tier, items, total]`;

const scalaCode = `case class CustomerInput(customerId: String)
case class CustomerOutput(name: String, tier: String)

val fetchCustomer = ModuleBuilder
  .metadata("FetchCustomer", "Fetch customer data", 1, 0)
  .implementation[CustomerInput, CustomerOutput] { input =>
    IO {
      val response = httpClient.get(s"/customers/\${input.customerId}")
      CustomerOutput(response.name, response.tier)
    }
  }
  .build`;

export default function HomepageCodeExample(): JSX.Element {
  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>Separate Logic from Implementation</h2>
      <p className={styles.sectionSubtitle}>
        Define pipeline structure in the DSL. Implement functions in Scala.
      </p>
      <div className={styles.grid}>
        <div className={styles.panel}>
          <div className={styles.panelHeader}>Pipeline Definition (constellation-lang)</div>
          <div className={styles.panelBody}>
            <CodeBlock language="constellation" showLineNumbers>
              {dslCode}
            </CodeBlock>
          </div>
        </div>
        <div className={styles.panel}>
          <div className={styles.panelHeader}>Module Implementation (Scala)</div>
          <div className={styles.panelBody}>
            <CodeBlock language="scala" showLineNumbers>
              {scalaCode}
            </CodeBlock>
          </div>
        </div>
      </div>
    </section>
  );
}
