import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Constellation Engine',
  tagline: 'Type-safe pipeline orchestration for Scala',
  favicon: 'img/logo-icon.svg',

  url: 'https://vledicfranco.github.io',
  baseUrl: '/constellation-engine/',

  organizationName: 'VledicFranco',
  projectName: 'constellation-engine',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
    format: 'md',
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },

  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/VledicFranco/constellation-engine/tree/master/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/social-preview.svg',
    colorMode: {
      defaultMode: 'dark',
      disableSwitch: false,
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Constellation Engine',
      logo: {
        alt: 'Constellation Engine Logo',
        src: 'img/logo-icon.svg',
        srcDark: 'img/logo-icon-dark.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          to: '/docs/api-reference/programmatic-api',
          label: 'API',
          position: 'left',
        },
        {
          to: '/docs/getting-started/examples/',
          label: 'Examples',
          position: 'left',
        },
        {
          href: 'https://github.com/VledicFranco/constellation-engine',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {label: 'Getting Started', to: '/docs/getting-started/tutorial'},
            {label: 'Language Reference', to: '/docs/language/'},
            {label: 'API Reference', to: '/docs/api-reference/programmatic-api'},
            {label: 'Standard Library', to: '/docs/api-reference/stdlib'},
          ],
        },
        {
          title: 'Operations',
          items: [
            {label: 'Configuration', to: '/docs/operations/configuration'},
            {label: 'Deployment', to: '/docs/operations/deployment'},
            {label: 'Performance Tuning', to: '/docs/operations/performance-tuning'},
            {label: 'Troubleshooting', to: '/docs/tooling/troubleshooting'},
          ],
        },
        {
          title: 'Community',
          items: [
            {label: 'GitHub', href: 'https://github.com/VledicFranco/constellation-engine'},
            {label: 'Issues', href: 'https://github.com/VledicFranco/constellation-engine/issues'},
            {label: 'Contributing', to: '/docs/resources/contributing'},
            {label: 'Changelog', to: '/docs/resources/changelog'},
          ],
        },
      ],
      copyright: `Copyright &copy; ${new Date().getFullYear()} Constellation Engine Contributors. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['scala', 'bash', 'json', 'yaml', 'docker'],
    },
    mermaid: {
      theme: {
        light: 'neutral',
        dark: 'dark',
      },
    },
  } satisfies Preset.ThemeConfig,

  plugins: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        hashed: true,
        language: ['en'],
        highlightSearchTermsOnTargetPage: true,
        explicitSearchResultPath: true,
      },
    ],
  ],
};

export default config;
