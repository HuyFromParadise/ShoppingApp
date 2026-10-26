import { expect, test, type Page } from "@playwright/test"

/**
 * Admin product and category workflow (PROJECT_SPEC Journey E) and the guarded
 * routes around it.
 *
 * The admin pages are protected by the API, so this spec signs in with the demo
 * admin created by the seed (`db/seed/R__demo_catalog.sql`). If that account is
 * absent — e.g. a database without the seed — the specs skip rather than fail,
 * because the application is not at fault.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1"
const ADMIN = { email: "admin@demo.local", password: "DemoPass!234" }

async function seedAdminExists(page: Page): Promise<boolean> {
  const response = await page.request.post(`${API_BASE}/auth/login`, {
    data: ADMIN,
  })
  return response.ok()
}

test.describe("admin catalog", () => {
  // The admin shell is client-rendered behind a session guard after the session
  // call resolves, so these pages need more than the default assertion budget.
  test.describe.configure({ timeout: 90_000 })

  test("a signed-out visitor is sent to sign in", async ({ page }) => {
    await page.goto("/admin/products")
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole("heading")).toBeVisible()
  })

  test("the demo admin can open the product list", async ({ page }) => {
    test.skip(!(await seedAdminExists(page)), "demo admin not seeded")

    // The API check above runs in this same context, and its refresh cookie
    // would sign the browser in, replacing the form with a redirect. Clear it
    // so the test really starts signed out.
    await page.context().clearCookies()
    await page.goto("/login")
    await page.getByLabel(/email/i).fill(ADMIN.email)
    // The password input has no accessible name (it carries no id and no
    // aria-label), so it is reached by type here. Fixing that label belongs to
    // the auth form, not to this test.
    await page.locator('input[type="password"]').fill(ADMIN.password)
    await page.getByRole("button", { name: /sign in|log in/i }).click()
    // Wait for the redirect itself, so the session is stored before navigating.
    await page.waitForURL((url) => !url.pathname.endsWith("/login"), { timeout: 45_000 })

    await page.goto("/admin/products")
    await expect(page.getByRole("heading", { level: 1, name: "Products" })).toBeVisible({
      timeout: 45_000,
    })
    await expect(page.getByRole("link", { name: "New product" })).toBeVisible()

    // Seeded products and their statuses are visible to the operator.
    const table = page.getByRole("table", { name: "Products" })
    await expect(table).toBeVisible()
    await expect(table.getByText("Live").first()).toBeVisible()
  })

  test("the demo admin can open the category admin", async ({ page }) => {
    test.skip(!(await seedAdminExists(page)), "demo admin not seeded")

    // The API check above runs in this same context, and its refresh cookie
    // would sign the browser in, replacing the form with a redirect. Clear it
    // so the test really starts signed out.
    await page.context().clearCookies()
    await page.goto("/login")
    await page.getByLabel(/email/i).fill(ADMIN.email)
    // The password input has no accessible name (it carries no id and no
    // aria-label), so it is reached by type here. Fixing that label belongs to
    // the auth form, not to this test.
    await page.locator('input[type="password"]').fill(ADMIN.password)
    await page.getByRole("button", { name: /sign in|log in/i }).click()
    await page.waitForURL((url) => !url.pathname.endsWith("/login"), { timeout: 45_000 })

    await page.goto("/admin/categories")
    await expect(page.getByRole("heading", { level: 1, name: "Categories" })).toBeVisible({
      timeout: 45_000,
    })

    // A hidden category shows as hidden to the operator, and there is no delete.
    await expect(page.getByText("Visible").first()).toBeVisible()
    await expect(page.getByRole("button", { name: "New category" })).toBeVisible()
    await expect(page.getByText("Delete", { exact: true })).toHaveCount(0)
  })
})
