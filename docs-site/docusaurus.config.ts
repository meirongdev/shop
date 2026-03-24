import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Shop Platform',
  tagline: '云原生微服务电商技术验证平台',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://meirongdev.github.io',
  baseUrl: '/shop/',

  organizationName: 'meirongdev',
  projectName: 'shop',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },

  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {from: '/local-deployment', to: '/getting-started/local-deployment'},
          {from: '/observability', to: '/engineering/observability'},
          {from: '/engineering-standards', to: '/engineering/standards'},
          {from: '/tech-stack-best-practices', to: '/tech-stack/best-practices'},
          {from: '/modules/auth-server', to: '/services/core'},
          {from: '/modules/api-gateway', to: '/architecture/api-gateway'},
          {from: '/modules/bff-portals', to: '/architecture/bff-pattern'},
          {from: '/modules/domain-services', to: '/services/core'},
        ],
      },
    ],
  ],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: '/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Shop Platform',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: '文档',
        },
        {
          href: 'https://github.com/meirongdev/shop',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '开始',
          items: [
            {label: '项目概览', to: '/'},
            {label: '快速开始', to: '/getting-started/quick-start'},
            {label: '本地部署', to: '/getting-started/local-deployment'},
          ],
        },
        {
          title: '架构',
          items: [
            {label: '架构概览', to: '/architecture'},
            {label: 'API Gateway', to: '/architecture/api-gateway'},
            {label: '事件驱动', to: '/architecture/event-driven'},
          ],
        },
        {
          title: '参考',
          items: [
            {label: '技术栈', to: '/tech-stack'},
            {label: '服务模块', to: '/services/core'},
            {label: 'Roadmap', to: '/roadmap'},
          ],
        },
      ],
      copyright: `Copyright ${new Date().getFullYear()} Shop Platform. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'kotlin', 'yaml', 'bash', 'sql'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
