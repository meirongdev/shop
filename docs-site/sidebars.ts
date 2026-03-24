import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  tutorialSidebar: [
    'intro',
    {
      type: 'category',
      label: '🚀 快速开始',
      collapsed: false,
      items: ['getting-started/quick-start', 'getting-started/local-deployment'],
    },
    {
      type: 'category',
      label: '🛠 技术栈',
      items: ['tech-stack/index', 'tech-stack/best-practices'],
    },
    {
      type: 'category',
      label: '🏗 架构设计',
      items: [
        'architecture/index',
        'architecture/api-gateway',
        'architecture/bff-pattern',
        'architecture/event-driven',
      ],
    },
    {
      type: 'category',
      label: '📦 服务模块',
      items: ['services/core', 'services/growth', 'services/platform'],
    },
    {
      type: 'category',
      label: '⚙️ 工程实践',
      items: ['engineering/standards', 'engineering/observability'],
    },
    'roadmap',
  ],
};

export default sidebars;
