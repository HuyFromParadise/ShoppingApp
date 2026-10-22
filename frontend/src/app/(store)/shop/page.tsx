import type { Metadata } from "next"
import Link from "next/link"
import { Breadcrumbs } from "@/components/layout/breadcrumbs"
import { CatalogResults } from "@/components/catalog/catalog-results"
import { ErrorState } from "@/components/ui/error-state"
import { buttonVariants } from "@/components/ui/button"
import { ApiError } from "@/lib/api/client"
import {
  listCategories,
  listProducts,
} from "@/lib/api/endpoints/catalog"
import { safeParseCatalogFilters } from "@/lib/validation/catalog"
import type { Category, ProductSummary } from "@/types/catalog"
import type { Paginated } from "@/lib/api/types"

export const metadata: Metadata = {
  title: "Shop",
  description: "Browse the full considered wardrobe collection.",
}

interface ShopPageProps {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

export default async function ShopPage({ searchParams }: ShopPageProps) {
  const parsed = safeParseCatalogFilters(await searchParams)
  if (!parsed.success) {
    return (
      <div className="container-store py-12 sm:py-16">
        <Breadcrumbs items={[{ label: "Home", href: "/" }, { label: "Shop" }]} />
        <div className="mt-8">
          <ErrorState
            title="Invalid catalog filters"
            description={
              parsed.error.issues[0]?.message ??
              "Check the filters in the address bar and try again."
            }
            action={
              <Link href="/shop" className={buttonVariants({ variant: "secondary" })}>
                Reset filters
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const filters = parsed.data
  const [productsResult, categoriesResult] = await Promise.allSettled([
    listProducts(filters),
    listCategories(),
  ])

  if (productsResult.status === "rejected") {
    return (
      <div className="container-store py-12 sm:py-16">
        <Breadcrumbs items={[{ label: "Home", href: "/" }, { label: "Shop" }]} />
        <div className="mt-8">
          <ErrorState
            title="Catalog unavailable"
            description={
              productsResult.reason instanceof ApiError
                ? productsResult.reason.message
                : "We could not load the collection. Please try again shortly."
            }
            action={
              <Link href="/shop" className={buttonVariants({ variant: "secondary" })}>
                Try again
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  const categories: Category[] =
    categoriesResult.status === "fulfilled" ? categoriesResult.value : []
  const page: Paginated<ProductSummary> = productsResult.value
  const activeCategory =
    filters.category !== undefined
      ? categories.find((category) => category.slug === filters.category)
      : undefined

  const breadcrumbs = [
    { label: "Home", href: "/" },
    { label: "Shop", href: "/shop" },
    ...(activeCategory
      ? [{ label: activeCategory.name, href: `/categories/${activeCategory.slug}` }]
      : []),
  ]

  return (
    <div className="container-store py-10 sm:py-14">
      <Breadcrumbs items={breadcrumbs} />

      <div className="mt-8 flex flex-col gap-3 border-b border-line pb-8 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-medium uppercase tracking-label text-muted">
            The collection
          </p>
          <h1 className="mt-3 text-3xl font-semibold tracking-display text-ink sm:text-4xl">
            {filters.q ? `Results for “${filters.q}”` : "Shop all"}
          </h1>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted">
            Quiet, considered pieces chosen for everyday wear. Filter by fit,
            colour, price and availability.
          </p>
        </div>
        {filters.onSale ? (
          <p className="text-sm text-accent">Showing sale pieces only</p>
        ) : null}
      </div>

      <div className="mt-10">
        <CatalogResults
          basePath="/shop"
          filters={filters}
          page={page}
          categories={categories}
        />
      </div>
    </div>
  )
}
