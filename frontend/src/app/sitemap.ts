import type { MetadataRoute } from "next"
import { listCategories, listProducts } from "@/lib/api/endpoints/catalog"
import { parseCatalogFilters } from "@/lib/validation/catalog"

const SITE_URL =
  process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/+$/, "") ?? "http://localhost:3000"

/** Products fetched for the sitemap; the catalog API caps `size` at 48. */
const PAGE_SIZE = 48
const MAX_PAGES = 20

function url(path: string): string {
  return `${SITE_URL}${path.startsWith("/") ? "" : "/"}${path}`
}

/**
 * Sitemap for the public storefront (project step: SEO). Only pages anyone may
 * see are listed: never the cart, checkout, account or admin areas.
 *
 * <p>A catalog failure must not break the sitemap, so a failed fetch yields the
 * static pages only.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date()
  const entries: MetadataRoute.Sitemap = [
    { url: url("/"), lastModified: now, changeFrequency: "daily", priority: 1 },
    { url: url("/shop"), lastModified: now, changeFrequency: "daily", priority: 0.9 },
  ]

  try {
    const categories = await listCategories()
    for (const category of categories) {
      entries.push({
        url: url(`/categories/${encodeURIComponent(category.slug)}`),
        lastModified: now,
        changeFrequency: "weekly",
        priority: 0.7,
      })
    }
  } catch {
    // Keep the static entries; a catalog outage should not take the sitemap down.
  }

  try {
    for (let page = 0; page < MAX_PAGES; page += 1) {
      // Built through the app's own filter parser, so the sitemap asks for the
      // same shape the shop page does instead of hand-rolling query params.
      const products = await listProducts(parseCatalogFilters({ page: String(page), size: String(PAGE_SIZE) }))
      for (const product of products.items) {
        entries.push({
          url: url(`/products/${encodeURIComponent(product.slug)}`),
          lastModified: product.createdAt ? new Date(product.createdAt) : now,
          changeFrequency: "weekly",
          priority: 0.8,
        })
      }
      if (page + 1 >= products.totalPages) break
    }
  } catch {
    // Same: the sitemap stays useful without the product list.
  }

  return entries
}
