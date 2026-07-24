import { test, expect } from "@playwright/test";

/**
 * Smoke E2E against a running stack (docker-compose or local dev).
 * Run: npx playwright test --config=playwright.config.ts
 */
test.describe("InvoiceGenie AR console smoke", () => {
  test("loads dashboard shell", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText(/InvoiceGenie/i).first()).toBeVisible();
  });

  test("navigates to invoices", async ({ page }) => {
    await page.goto("/invoices");
    await expect(page.getByText(/Invoices/i).first()).toBeVisible();
  });

  test("settings page renders", async ({ page }) => {
    await page.goto("/settings");
    await expect(page.getByText(/Settings|Tenant|Health/i).first()).toBeVisible();
  });
});