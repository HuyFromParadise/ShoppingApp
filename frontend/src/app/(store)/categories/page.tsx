import type { Metadata } from "next"
import Link from "next/link"
import { Breadcrumbs } from "@/components/layout/breadcrumbs"
import { ErrorState } from "@/components/ui/error-state"
import { buttonVariants } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import { listCategories } from "@/lib/api/endpoints/catalog"
import type { Category } from "@/types/catalog"

export const metadata: Metadata = {
  title: "Categories",
  description: "Browse the collection by category.",
  alternates: { canonical: "/categories" },
}

/**
 * Category index (project step: products and categories).
 *
 * <p>The public API lists only active categories, so hidden ones never appear
 * here. Parents are shown with their children nested, which is how the catalog
 * is organised rather than a flat tag list.
 */
export default async function CategoriesPage() {
  let categories: Category[]
  try {
    categories = await listCategories()
  } catch (error) {
    return (
      <div className="container-store py-12 sm:py-16">
        <Breadcrumbs items={[{ label: "Home", href: "/" }, { label: "Categories" }]} />
        <div className="mt-8">
          <ErrorState
            title="Categories unavailable"
            description={
              error instanceof ApiError
                ? error.message
                : "We could not load the categories. Please try again shortly."
            }
            action={
              <Link href="/categories" className={buttonVariants({ variant: "secondary" })}>
                Try again
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const byId = new Map(categories.map((category) => [category.id, category]))
  const children = new Map<string, Category[]>()
  const roots: Category[] = []
  for (const category of categories) {
    // A parent that is hidden is not in this list, so an orphaned child is shown
    // at the top level rather than disappearing.
    if (category.parentId && byId.has(category.parentId)) {
      const siblings = children.get(category.parentId) ?? []
      siblings.push(category)
      children.set(category.parentId, siblings)
    } else {
      roots.push(category)
    }
  }
  const bySortOrder = (a: Category, b: Category) =>
    a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)

  return (
    <div className="container-store py-10 sm:py-14">
      <Breadcrumbs items={[{ label: "Home", href: "/" }, { label: "Categories" }]} />

      <div className="mt-8 border-b border-line pb-8">
        <p className="text-xs font-medium uppercase tracking-label text-muted">Browse</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-display text-ink sm:text-4xl">
          Categories
        </h1>
        <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted">
          Every part of the collection, grouped the way it is stocked.
        </p>
      </div>

      {categories.length === 0 ? (
        <p className="mt-10 text-sm text-muted">
          No categories are published yet. <Link href="/shop" className="text-ink underline underline-offset-4">Browse everything</Link>.
        </p>
      ) : (
        <ul className="mt-10 grid gap-x-10 gap-y-8 sm:grid-cols-2 lg:grid-cols-3">
          {roots.sort(bySortOrder).map((root) => {
            const kids = (children.get(root.id) ?? []).sort(bySortOrder)
            return (
              <li key={root.id}>
                <Link
                  href={`/categories/${encodeURIComponent(root.slug)}`}
                  className="text-lg font-medium text-ink underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-paper"
                >
                  {root.name}
                </Link>
                {root.description ? (
                  <p className="mt-1 text-sm text-muted">{root.description}</p>
                ) : null}
                {kids.length > 0 ? (
                  <ul className="mt-3 space-y-1 border-l border-line pl-4">
                    {kids.map((child) => (
                      <li key={child.id}>
                        <Link
                          href={`/categories/${encodeURIComponent(child.slug)}`}
                          className="text-sm text-muted underline-offset-4 hover:text-ink hover:underline"
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
