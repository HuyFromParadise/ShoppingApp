import { defineConfig, devices } from "@playwright/test"

/**
 * End-to-end tests for the critical path (DESIGN_REFERENCE §10: "E2E |
 * Playwright | the §16 critical path once Phases 2–6 exist").
 *
 * The app is started automatically against the backend, which must already be
 * running on :8080 (see e2e/README.md). Browsing, products and categories are
 * public and need no seed; the admin and checkout specs skip unless the demo
 * seed accounts exist, so the suite is honest about what it could not exercise.
 *
 * The origin must match the backend's FRONTEND_ORIGIN. That allow-list also
 * enables credentials for the refresh cookie, so 127.0.0.1 or a different port
 * would break every authenticated call with a CORS error instead of testing the
 * application.
 */
const ORIGIN = process.env.FRONTEND_ORIGIN ?? "http://localhost:3000"
const PORT = Number(process.env.E2E_PORT ?? new URL(ORIGIN).port ?? 3000)
const BASE_URL = ORIGIN

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    // A production build of the app; the API base URL is the running backend.
    command: `npm run build && npx next start -p ${PORT}`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
    env: {
      NEXT_PUBLIC_API_BASE_URL:
        process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1",
      NEXT_PUBLIC_SITE_URL: BASE_URL,
    },
  },
})
