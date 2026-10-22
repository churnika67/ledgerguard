import { defineConfig, devices } from "@playwright/test";

/**
 * Assumes ledger-service is already running and reachable at environment.ts's apiBaseUrl.
 * This config only starts the Angular dev server itself.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:4200",
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "npx ng serve --port 4200",
    url: "http://localhost:4200/login",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
