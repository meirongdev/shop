/**
 * Buyer KMP app tests: authentication, guest mode, and all major routes.
 *
 * The buyer app is a KMP/Compose WASM app served via kmp-web-proxy.mjs.
 * It exposes an e2e marker element (#buyer-app-e2e) with:
 *   data-route   – current route name
 *   data-status  – "ready" | "authenticating" | "error" | "booting"
 *   data-user    – username of logged-in buyer (empty if not authenticated)
 *
 * Start the buyer proxy before running these tests:
 *   node scripts/kmp-web-proxy.mjs <dist-dir> <gateway-url> 18182
 */
import { test, expect, type Page } from '@playwright/test';

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://127.0.0.1:18080';
const BUYER_USERNAME = process.env.BUYER_USERNAME || 'buyer.demo';
const BUYER_PASSWORD = process.env.BUYER_PASSWORD || 'password';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Obtain a JWT for the buyer by calling auth-server directly. */
async function fetchBuyerToken(page: Page): Promise<{ token: string; principalId: string; username: string }> {
  const resp = await page.request.post(`${GATEWAY_URL}/auth/v1/token/login`, {
    data: { username: BUYER_USERNAME, password: BUYER_PASSWORD, portal: 'buyer' },
  });
  expect(resp.status()).toBe(200);
  const body = await resp.json();
  return { token: body.data.accessToken, principalId: body.data.principalId, username: body.data.username };
}

/** Wait for the e2e status marker to reach a target status. */
async function waitForE2eStatus(page: Page, status: string, timeout = 30000): Promise<void> {
  await expect(page.locator('#buyer-app-e2e')).toHaveAttribute('data-status', status, { timeout });
}

/** Wait for the marker to leave the 'booting' / 'authenticating' transient states. */
async function waitForE2eReady(page: Page, timeout = 30000): Promise<void> {
  await page.waitForFunction(
    () => {
      const el = document.querySelector('#buyer-app-e2e');
      if (!el) return false;
      const s = el.getAttribute('data-status') ?? '';
      return s !== '' && s !== 'booting' && s !== 'authenticating';
    },
    { timeout },
  );
}

/** Navigate to the buyer app with e2e mode and injected session. */
async function openBuyerRoute(
  page: Page,
  route: string,
  token: string,
  principalId: string,
  username: string,
): Promise<void> {
  const params = new URLSearchParams({
    e2e: '1',
    e2eRoute: route,
    e2eAccessToken: token,
    e2eUsername: username,
    e2ePrincipalId: principalId,
    e2eDisplayName: 'Buyer Demo',
  });
  await page.goto(`/?${params.toString()}`);
  await page.waitForSelector('#buyer-app-e2e', { state: 'attached', timeout: 30000 });
  await waitForE2eStatus(page, 'ready');
}

// ---------------------------------------------------------------------------
// Auth: buyer auth screen
// ---------------------------------------------------------------------------

test.describe('Buyer app - auth screen', () => {
  test('auth screen renders in e2e mode', async ({ page }) => {
    await page.goto('/?e2e=1&e2eRoute=auth');
    await page.waitForSelector('#buyer-app-e2e', { state: 'attached', timeout: 30000 });
    const marker = page.locator('#buyer-app-e2e');
    await waitForE2eReady(page, 30000);
    const status = await marker.getAttribute('data-status');
    expect(['booting', 'authenticating', 'ready', 'error']).toContain(status);
  });

  test('auto-login with buyer.demo eventually reaches a terminal state', async ({ page }) => {
    await page.goto('/?e2e=1&e2eRoute=marketplace&e2eAutoLogin=1');
    await page.waitForSelector('#buyer-app-e2e', { state: 'attached', timeout: 30000 });
    await waitForE2eReady(page, 45000);
    const marker = page.locator('#buyer-app-e2e');
    const status = await marker.getAttribute('data-status');
    expect(['ready', 'error']).toContain(status);
  });

  test('guest login eventually reaches a terminal state', async ({ page }) => {
    await page.goto('/?e2e=1&e2eRoute=marketplace&e2eGuestLogin=1');
    await page.waitForSelector('#buyer-app-e2e', { state: 'attached', timeout: 30000 });
    await waitForE2eReady(page, 45000);
    const marker = page.locator('#buyer-app-e2e');
    const status = await marker.getAttribute('data-status');
    expect(['ready', 'error']).toContain(status);
  });
});

// ---------------------------------------------------------------------------
// Buyer authenticated routes
// ---------------------------------------------------------------------------

const BUYER_ROUTES = ['marketplace', 'cart', 'orders', 'wallet', 'promotions', 'profile'];

test.describe('Buyer app - authenticated routes', () => {
  let token: string;
  let principalId: string;
  let username: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    const creds = await fetchBuyerToken(page);
    token = creds.token;
    principalId = creds.principalId;
    username = creds.username;
    await page.close();
  });

  for (const route of BUYER_ROUTES) {
    test(`${route} route loads and reaches ready status`, async ({ page }) => {
      await openBuyerRoute(page, route, token, principalId, username);
      const marker = page.locator('#buyer-app-e2e');
      await expect(marker).toHaveAttribute('data-status', 'ready');
      await expect(marker).toHaveAttribute('data-route', route);
      await expect(marker).toHaveAttribute('data-user', username);
    });
  }

  test('marketplace route shows no error state', async ({ page }) => {
    await openBuyerRoute(page, 'marketplace', token, principalId, username);
    const marker = page.locator('#buyer-app-e2e');
    const errMsg = await marker.getAttribute('data-message');
    expect(errMsg ?? '').toBe('');
  });
});

// ---------------------------------------------------------------------------
// Buyer KMP app via gateway — verify the k8s-deployed nginx SPA shell
// ---------------------------------------------------------------------------

test.describe('Buyer app SPA shell via gateway', () => {
  test('buyer app index is served from gateway', async ({ request }) => {
    const resp = await request.get(`${GATEWAY_URL}/buyer-app/`);
    expect(resp.status()).toBe(200);
    const body = await resp.text();
    expect(body).toContain('<!DOCTYPE html');
    expect(body).toContain('buyer-app.js');
  });

  test('buyer app JS bundle is served', async ({ request }) => {
    const resp = await request.get(`${GATEWAY_URL}/buyer-app/buyer-app.js`);
    expect(resp.status()).toBe(200);
  });

  test('buyer app SPA fallback serves index.html for unknown routes', async ({ request }) => {
    const resp = await request.get(`${GATEWAY_URL}/buyer-app/some/deep/route`);
    expect(resp.status()).toBe(200);
    const body = await resp.text();
    expect(body).toContain('<!DOCTYPE html');
  });
});
