import { defineConfig, devices } from "@playwright/test";

/**
 * Assumes ledger-service and notification-service are already running and reachable at the
 * URLs configured in .env.local (see README for how to start them). This config only starts
 * the Next.js app itself; it does not spin up the Java backend.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000/login",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
