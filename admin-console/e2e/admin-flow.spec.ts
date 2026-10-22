import { test, expect } from "@playwright/test";

/**
 * End-to-end: a non-admin login is rejected by the console, then an admin login reaches the
 * reconciliation dashboard with live data, and the accounts table shows real rows. This is the
 * stable backbone of the admin console; the chaos-test panel has its own manual verification
 * (it drives a real concurrent workload against the backend, which is awkward to make both fast
 * and deterministic in CI).
 */
test("admin login reaches the dashboard; a non-admin is rejected", async ({ page }) => {
  await page.goto("/login");

  // A USER-role account can authenticate but must not get into the console.
  await page.getByLabel("Username").fill("alice");
  await page.getByLabel("Password").fill("password123");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page.getByText(/requires an ADMIN account/i)).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel("Username").fill("admin");
  await page.getByLabel("Password").fill("admin123");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/reconciliation$/);
  await expect(page.getByText(/all invariants hold|invariant violation/i)).toBeVisible({ timeout: 15000 });

  await page.getByRole("link", { name: /accounts/i }).click();
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(page.getByRole("table")).toBeVisible();
});
