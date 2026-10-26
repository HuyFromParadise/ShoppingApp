import { expect, test } from "@playwright/test"

/**
 * Catalog critical path (PROJECT_SPEC Journeys A and B): land on the shop, find
 * a product, open it, and reach the category index. These read the public API,
 * so they need no login and no seed data beyond what the catalog holds.
 */

test.describe("browse the catalog", () => {
  test("the home page renders and links into the shop", async ({ page }) => {
    await page.goto("/")
    await expect(page).toHaveTitle(/Clothing/i)

    // One main landmark and exactly one h1: the heading structure is a contract.
    await expect(page.locator("main")).toHaveCount(1)
    await expect(page.locator("main h1")).toHaveCount(1)

    await page.getByRole("link", { name: "Shop", exact: true }).first().click()
    await expect(page).toHaveURL(/\/shop$/)
  })

  test("the shop lists products and opens one", async ({ page }) => {
    await page.goto("/shop")
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible()

    // Every product card links to a product page; the first one is enough to
    // prove the list renders real data rather than a skeleton.
    const productLinks = page.locator('a[href^="/products/"]')
    await expect(productLinks.first()).toBeVisible()

    const href = await productLinks.first().getAttribute("href")
    expect(href).toBeTruthy()
    await productLinks.first().click()

    await expect(page).toHaveURL(new RegExp(`${href}$`))
    await expect(page.locator("main h1")).toBeVisible()
    // Structured data for search engines is part of the product page contract.
    const jsonLd = await page.locator('script[type="application/ld+json"]').allTextContents()
    expect(jsonLd.join("")).toContain('"@type":"Product"')
  })

  test("the category index lists categories and opens one", async ({ page }) => {
    await page.goto("/categories")
    await expect(page.getByRole("heading", { level: 1, name: "Categories" })).toBeVisible()

    const categoryLink = page.locator('a[href^="/categories/"]').first()
    if ((await categoryLink.count()) === 0) {
      test.skip(true, "No active categories in the catalog")
    }
    await categoryLink.click()
    await expect(page).toHaveURL(/\/categories\/.+/)
    await expect(page.locator("main h1")).toBeVisible()
  })

  test("an unknown product is a not-found page, not a crash", async ({ page }) => {
    const response = await page.goto("/products/this-product-does-not-exist")
    expect(response?.status()).toBeLessThan(500)
  })

  test("the sitemap and robots are served", async ({ request }) => {
    const robots = await request.get("/robots.txt")
    expect(robots.status()).toBe(200)
    expect(await robots.text()).toContain("Sitemap:")

    const sitemap = await request.get("/sitemap.xml")
    expect(sitemap.status()).toBe(200)
    expect(await sitemap.text()).toContain("<urlset")
  })
})
