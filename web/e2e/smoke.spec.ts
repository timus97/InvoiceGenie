import { test, expect } from "@playwright/test";

/**
 * Auth + console smoke against a running stack (Quarkus + Next.js).
 * Run: npm run test:e2e
 */
test.describe("Authentication", () => {
  test("unauthenticated users are redirected to login", async ({ page }) => {
    await page.goto("/invoices");
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("heading", { name: /Sign in/i })).toBeVisible();
  });

  test("login with email/password and reach dashboard", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel(/^Email$/i).fill("admin@invoicegenie.local");
    await page.getByLabel(/Password/i).fill("Admin123!");
    await page.getByRole("button", { name: /^Sign in$/i }).click();
    await expect(page).toHaveURL(/\/(?!login)/, { timeout: 15_000 });
    await expect(page.getByText(/InvoiceGenie/i).first()).toBeVisible();
  });

  test("login does not expose JWT/API key in body or Set-Cookie", async ({
    page,
  }) => {
    const loginResponse = page.waitForResponse(
      (r) =>
        r.url().includes("/api/auth/login") && r.request().method() === "POST",
    );
    await page.goto("/login");
    await page.getByLabel(/^Email$/i).fill("admin@invoicegenie.local");
    await page.getByLabel(/Password/i).fill("Admin123!");
    await page.getByRole("button", { name: /^Sign in$/i }).click();
    const res = await loginResponse;
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    expect(json.accessToken).toBeUndefined();
    expect(json.refreshToken).toBeUndefined();
    expect(json.apiKey).toBeUndefined();
    expect(json.password).toBeUndefined();
    expect(json.tenantId).toBeTruthy();
    expect(json.email || json.subject).toContain("admin@invoicegenie.local");

    const setCookie = res.headers()["set-cookie"] ?? "";
    expect(setCookie).toMatch(/ig_sid=/);
    expect(setCookie).not.toMatch(/eyJ[A-Za-z0-9_-]+\./);
    expect(setCookie).not.toMatch(/ig_at=/);

    const leaks: string[] = [];
    page.on("request", (req) => {
      if (req.url().includes("/api/v1/")) {
        const auth = req.headers()["authorization"];
        const apiKey = req.headers()["x-api-key"];
        if (auth) leaks.push(`authorization=${auth}`);
        if (apiKey) leaks.push(`x-api-key=${apiKey}`);
      }
    });
    await page.goto("/customers");
    await page.waitForTimeout(1500);
    expect(leaks).toEqual([]);
  });

  test("logout returns to login", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel(/^Email$/i).fill("admin@invoicegenie.local");
    await page.getByLabel(/Password/i).fill("Admin123!");
    await page.getByRole("button", { name: /^Sign in$/i }).click();
    await expect(page).toHaveURL(/\/(?!login)/, { timeout: 15_000 });
    await page.getByRole("button", { name: /Sign out/i }).click();
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });
});

test.describe("InvoiceGenie AR console smoke (authenticated)", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel(/^Email$/i).fill("admin@invoicegenie.local");
    await page.getByLabel(/Password/i).fill("Admin123!");
    await page.getByRole("button", { name: /^Sign in$/i }).click();
    await expect(page).toHaveURL(/\/(?!login)/, { timeout: 15_000 });
  });

  test("loads dashboard shell", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText(/InvoiceGenie/i).first()).toBeVisible();
  });

  test("navigates to invoices", async ({ page }) => {
    await page.goto("/invoices");
    await expect(page.getByText(/Invoices/i).first()).toBeVisible();
  });

  test("settings page renders session", async ({ page }) => {
    await page.goto("/settings");
    await expect(page.getByText(/Settings|Session/i).first()).toBeVisible();
    await expect(page.getByText(/admin/i).first()).toBeVisible();
  });

  test("notifications page loads metrics shell", async ({ page }) => {
    await page.goto("/notifications");
    await expect(page.getByText(/Notifications/i).first()).toBeVisible();
    // Metric tiles are always present (zeros if API missing)
    await expect(page.getByText(/^SENT$/i).first()).toBeVisible();
    await expect(page.getByText(/^FAILED$/i).first()).toBeVisible();
  });
});

test.describe("Public pages", () => {
  test("login page loads", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: /Sign in/i })).toBeVisible();
  });

  test("unsubscribe page loads without auth", async ({ page }) => {
    await page.goto("/unsubscribe");
    await expect(
      page.getByRole("heading", { name: /Notification preferences/i }),
    ).toBeVisible();
    await expect(page.getByText(/missing a token/i)).toBeVisible();
  });
});
