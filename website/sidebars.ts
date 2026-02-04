import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/introduction',
        'getting-started/concepts',
        'getting-started/tutorial',
        'getting-started/embedding-guide',
        {
          type: 'category',
          label: 'Examples',
          items: [
            'getting-started/examples/index',
            'getting-started/examples/text-cleaning',
            'getting-started/examples/content-analysis',
            'getting-started/examples/data-statistics',
            'getting-started/examples/list-processing',
            'getting-started/examples/batch-enrichment',
            'getting-started/examples/scoring-pipeline',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Language Reference',
      items: [
        'language/index',
        'language/pipeline-structure',
        'language/types',
        'language/declarations',
        'language/expressions',
        'language/type-algebra',
        'language/orchestration-algebra',
        'language/comments',
        'language/module-options',
        'language/error-messages',
        'language/examples',
        'language/resilient-pipelines',
        {
          type: 'category',
          label: 'Module Options',
          items: [
            'language/options/retry',
            'language/options/timeout',
            'language/options/fallback',
            'language/options/cache',
            'language/options/cache-backend',
            'language/options/delay',
            'language/options/backoff',
            'language/options/throttle',
            'language/options/concurrency',
            'language/options/on-error',
            'language/options/lazy',
            'language/options/priority',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api-reference/http-api-overview',
        'api-reference/programmatic-api',
        'api-reference/stdlib',
        'api-reference/error-reference',
        'api-reference/lsp-websocket',
      ],
    },
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'architecture/technical-architecture',
        'architecture/security-model',
      ],
    },
    {
      type: 'category',
      label: 'Operations',
      items: [
        'operations/configuration',
        'operations/deployment',
        'operations/runbook',
        'operations/performance-tuning',
      ],
    },
    {
      type: 'category',
      label: 'Integrations (SPI)',
      items: [
        'integrations/metrics-provider',
        'integrations/tracer-provider',
        'integrations/execution-listener',
        'integrations/cache-backend',
        'integrations/execution-storage',
      ],
    },
    {
      type: 'category',
      label: 'Optional Modules',
      items: [
        'modules/index',
        'modules/cache-memcached',
      ],
    },
    {
      type: 'category',
      label: 'Tooling',
      items: [
        'tooling/dashboard',
        'tooling/lsp-integration',
        'tooling/troubleshooting',
      ],
    },
    {
      type: 'category',
      label: 'Resources',
      items: [
        'resources/ml-orchestration-challenges',
        'resources/migration-v030',
        'resources/contributing',
        'resources/changelog',
      ],
    },
  ],
};

export default sidebars;
