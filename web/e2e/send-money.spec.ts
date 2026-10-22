import { test, expect } from "@playwright/test";

/**
 * End-to-end: log in, open two fresh accounts, send money between them, and confirm the
 * balance actually moved. Then replays the exact same transfer request (same Idempotency-Key)
 * and confirms the UI proves it did NOT charge twice -- the same transfer id comes back and
 * the balance does not move a second time.
 */
test("login, send money, and prove a retry does not double-charge", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").fill("alice");
  await page.getByLabel("Password").fill("password123");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/accounts$/);

  // The "Your accounts" list only shows accounts whose ownerName matches the logged-in user
  // (alice), so both test accounts must be opened as "alice" -- distinguished by a balance
  // amount unlikely to collide with any pre-existing account from other test runs.
  async function openAccount(openingBalance: string) {
    await page.getByLabel("Owner name").fill("alice");
    await page.getByLabel("Currency").fill("USD");
    await page.getByLabel("Opening balance").fill(openingBalance);
    await page.getByRole("button", { name: /open account/i }).click();
    const link = page.getByRole("link").filter({ hasText: `${openingBalance} USD` });
    await expect(link).toBeVisible();
    const href = await link.getAttribute("href");
    return href!.split("/accounts/")[1];
  }

  const fromId = await openAccount("1831.19");
  const toId = await openAccount("642.07");

  // Send money once, then replay the exact same request before navigating anywhere else --
  // the "last submission" state that makes the retry button work lives only in this page.
  await page.goto("/send");
  await page.getByRole("combobox").selectOption({ value: fromId });
  await page.getByPlaceholder("destination account id").fill(toId);
  await page.getByPlaceholder("25.00").fill("40.00");
  await page.getByRole("button", { name: "Send" }).click();

  const firstResult = page.locator("li", { hasText: "New transfer" }).first();
  await expect(firstResult).toContainText("COMPLETED");
  const transferId = await firstResult.locator("code").textContent();

  const retryButton = page.getByRole("button", { name: /retry same request/i });
  await expect(retryButton).toBeVisible();
  await retryButton.click();

  const replayNote = page.getByText("Same transfer id as before -- this was a replay, not a new charge.");
  await expect(replayNote).toBeVisible();
  const retriedResult = page.locator("li", { hasText: "Retry (same Idempotency-Key)" }).first();
  await expect(retriedResult.locator("code")).toHaveText(transferId ?? "");

  // Balance should reflect exactly one 40.00 debit (1831.19 - 40.00 = 1791.19), not two.
  await page.goto(`/accounts/${fromId}`);
  await expect(page.getByText("1791.19")).toBeVisible();
});
