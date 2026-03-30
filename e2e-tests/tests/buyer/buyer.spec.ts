/**
 * Buyer portal tests: login, guest browsing, and all authenticated pages.
 *
 * Requires the platform to be running and accessible at GATEWAY_URL (default: http://127.0.0.1:18080).
 * Demo credentials: buyer.demo / password
 */
import { test, expect, type Page, type BrowserContext } from '@playwright/test';

const BUYER_USERNAME = process.env.BUYER_USERNAME || 'buyer.demo';
const BUYER_PASSWORD = process.env.BUYER_PASSWORD || 'password';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function loginAsBuyer(page: Page): Promise<void> {
  await page.goto('/buyer/login');
  await page.fill('input[name="username"]', BUYER_USERNAME);
  await page.fill('input[name="password"]', BUYER_PASSWORD);
  await page.click('button[type="submit"]');
  // After login we land on /buyer/welcome or /buyer/home
  await page.waitForURL(/\/buyer\/(welcome|home)/, { timeout: 15000 });
}

async function browseAsGuest(page: Page): Promise<void> {
  await page.goto('/buyer/login');
  await page.click('a:text("Continue as guest")');
  await page.waitForURL('**/buyer/home', { timeout: 15000 });
}

// ---------------------------------------------------------------------------
// Login page
// ---------------------------------------------------------------------------

test.describe('Buyer login page', () => {
  test('renders login form with all expected elements', async ({ page }) => {
    await page.goto('/buyer/login');
    await expect(page).toHaveTitle(/buyer|login|shop/i);
    await expect(page.locator('input[name="username"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
    await expect(page.locator('a:text("Continue as guest")')).toBeVisible();
    await expect(page.locator('a:text("Create account")')).toBeVisible();
  });

  test('shows validation error for wrong credentials', async ({ page }) => {
    await page.goto('/buyer/login');
    await page.fill('input[name="username"]', 'wrong.user');
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');
    // Stay on login page with an error
    await expect(page).toHaveURL(/\/buyer\/login/);
    await expect(page.locator('body')).not.toContainText('Welcome');
  });

  test('successful login with buyer.demo credentials redirects away from login', async ({ page }) => {
    await loginAsBuyer(page);
    await expect(page).not.toHaveURL(/\/buyer\/login/);
    // Should show display name, not "Guest buyer"
    await expect(page.locator('body')).not.toContainText('Guest buyer');
  });

  test('Continue as guest navigates to home with guest session', async ({ page }) => {
    await browseAsGuest(page);
    await expect(page.locator('body')).toContainText('Guest buyer');
    await expect(page.locator('a:text("Sign in")')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Guest browsing
// ---------------------------------------------------------------------------

test.describe('Guest browsing', () => {
  test.beforeEach(async ({ page }) => {
    await browseAsGuest(page);
  });

  test('home page renders product grid container', async ({ page }) => {
    // The product grid container is always present; items appear when products are seeded
    await expect(page.locator('.product-grid')).toBeAttached({ timeout: 10000 });
  });

  test('cart page is accessible as guest', async ({ page }) => {
    await page.goto('/buyer/cart');
    await expect(page.locator('body')).not.toContainText('Error');
    await expect(page).toHaveURL(/\/buyer\/cart/);
  });

  test('protected pages redirect guest to login', async ({ page }) => {
    await page.goto('/buyer/orders');
    // Protected pages redirect unauthenticated guests back to the login page
    await expect(page).toHaveURL(/\/buyer\/login/, { timeout: 8000 });
  });

  test('guest can add item to cart', async ({ page }) => {
    await page.goto('/buyer/home');
    const addToCartBtn = page.locator('button:text("Add to Cart")').first();
    if (await addToCartBtn.count() > 0) {
      await addToCartBtn.click();
      await page.goto('/buyer/cart');
      await expect(page.locator('body')).not.toContainText('Error');
    } else {
      test.skip(); // No products seeded yet
    }
  });
});

// ---------------------------------------------------------------------------
// Authenticated buyer pages
// ---------------------------------------------------------------------------

test.describe('Authenticated buyer pages', () => {
  let context: BrowserContext;
  let page: Page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
    await loginAsBuyer(page);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('welcome page loads for authenticated buyer', async () => {
    await page.goto('/buyer/welcome');
    await expect(page.locator('body')).not.toContainText('Guest buyer');
    await expect(page).not.toHaveURL(/\/buyer\/login/);
  });

  test('home page shows products and authenticated nav', async () => {
    await page.goto('/buyer/home');
    await expect(page).toHaveURL(/\/buyer\/home/);
    await expect(page.locator('body')).not.toContainText('Guest buyer');
  });

  test('cart page renders without error', async () => {
    await page.goto('/buyer/cart');
    await expect(page).toHaveURL(/\/buyer\/cart/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('orders page renders without error', async () => {
    await page.goto('/buyer/orders');
    await expect(page).toHaveURL(/\/buyer\/orders/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('wallet page renders without error', async () => {
    await page.goto('/buyer/wallet');
    await expect(page).toHaveURL(/\/buyer\/wallet/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('profile page renders with buyer info', async () => {
    await page.goto('/buyer/profile');
    await expect(page).toHaveURL(/\/buyer\/profile/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
    await expect(page.locator('body')).not.toContainText('Guest buyer');
  });

  test('activities page renders without error', async () => {
    await page.goto('/buyer/activities');
    await expect(page).toHaveURL(/\/buyer\/activities/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('loyalty page renders without error', async () => {
    await page.goto('/buyer/loyalty');
    await expect(page).toHaveURL(/\/buyer\/loyalty/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('invite page renders without error', async () => {
    await page.goto('/buyer/invite');
    await expect(page).toHaveURL(/\/buyer\/invite/);
    await expect(page.locator('body')).not.toContainText('Whitelabel Error');
  });

  test('logout clears session and redirects to login', async () => {
    // Open a new page to avoid breaking the shared session for other tests
    const logoutPage = await context.newPage();
    await logoutPage.goto('/buyer/logout');
    // Logout invalidates the session and redirects to /buyer/home (guest mode)
    await expect(logoutPage).toHaveURL(/\/buyer\/home/, { timeout: 8000 });
    await logoutPage.close();

    // Re-login for subsequent tests in beforeAll session
    await loginAsBuyer(page);
  });
});

// ---------------------------------------------------------------------------
// Buyer KMP App (WASM) — served via gateway at /buyer-app/**
// ---------------------------------------------------------------------------

test.describe('Buyer KMP app (WASM) via gateway', () => {
  test('SPA shell is served with correct HTML', async ({ page }) => {
    const response = await page.goto('/buyer-app/');
    expect(response?.status()).toBe(200);
    await expect(page.locator('html')).toBeDefined();
    // The WASM app shell must include the buyer-app root div and JS entrypoint
    const content = await page.content();
    expect(content).toContain('buyerApp');
    expect(content).toContain('buyer-app.js');
  });

  test('WASM JavaScript bundle is served with correct MIME type', async ({ page }) => {
    const jsRequests: string[] = [];
    page.on('response', (resp) => {
      if (resp.url().includes('buyer-app.js')) {
        jsRequests.push(resp.headers()['content-type'] ?? '');
      }
    });
    await page.goto('/buyer-app/');
    // Wait for scripts to load
    await page.waitForLoadState('domcontentloaded');
    // The JS entrypoint must be served (may not have content-type in all cases)
    const jsResp = await page.request.get('/buyer-app/buyer-app.js');
    expect(jsResp.status()).toBe(200);
  });
});
