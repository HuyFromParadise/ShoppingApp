import { defineConfig } from "vitest/config"
import { fileURLToPath } from "node:url"

export default defineConfig({
  esbuild: {
    jsx: "automatic",
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    restoreMocks: true,
    clearMocks: true,
    css: false,
    // The Playwright specs are driven by `npm run test:e2e`, not by Vitest; they
    // import @playwright/test and would fail as unit suites.
    exclude: ["**/node_modules/**", "**/.next/**", "e2e/**"],
  },
})
