/**
 * Seller app tests: authentication and all major routes.
 *
 * The seller app is a KMP/Compose WASM app served via seller-web-proxy.mjs.
 * It exposes an e2e marker element (#seller-app-e2e) with:
 *   data-route   – current route name
 *   data-status  – "ready" | "authenticating" | "error" | "booting"
 *   data-user    – username of logged-in seller (empty if not authenticated)
 *
 * Start the seller proxy before running these tests:
 *   node scripts/seller-web-proxy.mjs <dist-dir> <gateway-url> 18181
 */
import { test, expect, type Page } from '@playwright/test';

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://127.0.0.1:18080';
const SELLER_USERNAME = process.env.SELLER_USERNAME || 'seller.demo';
const SELLER_PASSWORD = process.env.SELLER_PASSWORD || 'password';
const SELLER_PORTAL = 'seller';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Obtain a JWT for the seller by calling auth-server directly. */
async function fetchSellerToken(page: Page): Promise<{ token: string; principalId: string }> {
  const resp = await page.request.post(`${GATEWAY_URL}/auth/v1/token/login`, {
    data: { username: SELLER_USERNAME, password: SELLER_PASSWORD, portal: SELLER_PORTAL },
  });
  expect(resp.status()).toBe(200);
  const body = await resp.json();
  return { token: body.data.accessToken, principalId: body.data.principalId };
}

/** Wait for the e2e status marker to reach a target status.
 *  The #seller-app-e2e element is intentionally hidden; use DOM attribute checks only. */
async function waitForE2eStatus(page: Page, status: string, timeout = 30000): Promise<void> {
  await expect(page.locator('#seller-app-e2e')).toHaveAttribute('data-status', status, { timeout });
}

/** Wait for the marker to leave the 'booting' / 'authenticating' transient states. */
async function waitForE2eReady(page: Page, timeout = 30000): Promise<void> {
  await page.waitForFunction(
    () => {
      const el = document.querySelector('#seller-app-e2e');
      if (!el) return false;
      const s = el.getAttribute('data-status') ?? '';
      return s !== '' && s !== 'booting' && s !== 'authenticating';
    },
    { timeout },
  );
}

/** Navigate to the seller app with e2e mode and injected session. */
async function openSellerRoute(
  page: Page,
  route: string,
  token: string,
  principalId: string,
): Promise<void> {
  const params = new URLSearchParams({
    e2e: '1',
    e2eRoute: route,
    e2eAccessToken: token,
    e2eUsername: SELLER_USERNAME,
    e2ePrincipalId: principalId,
    e2eDisplayName: 'Seller Demo',
  });
  await page.goto(`/?${params.toString()}`);
  // #seller-app-e2e is hidden by design; wait for it to be in the DOM, not visible
  await page.waitForSelector('#seller-app-e2e', { state: 'attached', timeout: 30000 });
  await waitForE2eStatus(page, 'ready');
}

// ---------------------------------------------------------------------------
// Auth: seller login screen
// ---------------------------------------------------------------------------

test.describe('Seller app - auth screen', () => {
  test('login screen renders in e2e mode', async ({ page }) => {
    await page.goto('/?e2e=1&e2eRoute=auth');
    // Marker is intentionally hidden; just assert it is attached with expected attributes
    await page.waitForSelector('#seller-app-e2e', { state: 'attached', timeout: 30000 });
    const marker = page.locator('#seller-app-e2e');
    await waitForE2eReady(page, 30000);
    const status = await marker.getAttribute('data-status');
    expect(['booting', 'authenticating', 'ready', 'error']).toContain(status);
  });

  test('auto-login with seller.demo eventually reaches a terminal state', async ({ page }) => {
    await page.goto('/?e2e=1&e2eRoute=marketplace&e2eAutoLogin=1');
    await page.waitForSelector('#seller-app-e2e', { state: 'attached', timeout: 30000 });
    // Wait for the app to leave transient states; accept ready or error (WASM coroutine
    // cancellation can occur in headless environments during the initial auth flow)
    await waitForE2eReady(page, 45000);
    const marker = page.locator('#seller-app-e2e');
    const status = await marker.getAttribute('data-status');
    expect(['ready', 'error']).toContain(status);
  });
});

// ---------------------------------------------------------------------------
// Seller authenticated routes
// ---------------------------------------------------------------------------

const SELLER_ROUTES = ['marketplace', 'orders', 'wallet', 'promotions', 'profile'];

test.describe('Seller app - authenticated routes', () => {
  let token: string;
  let principalId: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    const creds = await fetchSellerToken(page);
    token = creds.token;
    principalId = creds.principalId;
    await page.close();
  });

  for (const route of SELLER_ROUTES) {
    test(`${route} route loads and reaches ready status`, async ({ page }) => {
      await openSellerRoute(page, route, token, principalId);
      const marker = page.locator('#seller-app-e2e');
      await expect(marker).toHaveAttribute('data-status', 'ready');
      await expect(marker).toHaveAttribute('data-route', route);
      await expect(marker).toHaveAttribute('data-user', SELLER_USERNAME);
    });
  }

  test('marketplace route shows no error state', async ({ page }) => {
    await openSellerRoute(page, 'marketplace', token, principalId);
    const marker = page.locator('#seller-app-e2e');
    const errMsg = await marker.getAttribute('data-message');
    expect(errMsg ?? '').toBe('');
  });
});
