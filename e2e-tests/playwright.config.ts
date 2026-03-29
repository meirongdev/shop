import { defineConfig, devices } from '@playwright/test';

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://127.0.0.1:18080';
const SELLER_PROXY_URL = process.env.SELLER_PROXY_URL || 'http://127.0.0.1:18181';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['line']],

  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [
    {
      name: 'buyer',
      testMatch: '**/buyer/**/*.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: GATEWAY_URL,
      },
    },
    {
      name: 'seller',
      testMatch: '**/seller/**/*.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: SELLER_PROXY_URL,
      },
    },
  ],
});
