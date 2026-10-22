import Link from "next/link"
import { CatalogSearch } from "@/components/catalog/catalog-search"
import { CategoryNavigation } from "@/components/catalog/category-nav"
import { ProductGrid } from "@/components/product/product-grid"
import { buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import {
  listCategories,
  listProducts,
} from "@/lib/api/endpoints/catalog"
import { catalogFilterSchema } from "@/lib/validation/catalog"
import type { Category, ProductSummary } from "@/types/catalog"
import type { Paginated } from "@/lib/api/types"

const BASE_FILTERS = catalogFilterSchema.parse({})

export default async function HomePage() {
  const [categoriesResult, favouritesResult, newArrivalsResult] =
    await Promise.allSettled([
      listCategories(),
      listProducts({ ...BASE_FILTERS, sort: "popularity", size: 4 }),
      listProducts({ ...BASE_FILTERS, sort: "newest", size: 8 }),
    ])

  const categories: Category[] =
    categoriesResult.status === "fulfilled" ? categoriesResult.value : []
  const favourites: Paginated<ProductSummary> | null =
    favouritesResult.status === "fulfilled" ? favouritesResult.value : null
  const newArrivals: Paginated<ProductSummary> | null =
    newArrivalsResult.status === "fulfilled" ? newArrivalsResult.value : null

  const catalogFailed = !favourites && !newArrivals
  const hasProducts =
    (favourites?.items.length ?? 0) > 0 || (newArrivals?.items.length ?? 0) > 0

  return (
    <>
      <section className="border-b border-line bg-surface">
        <div className="container-store py-20 sm:py-28">
          <p className="text-xs font-medium uppercase tracking-label text-muted">
            Considered wardrobe
          </p>
          <h1 className="mt-5 max-w-3xl text-4xl font-semibold tracking-display text-ink sm:text-5xl lg:text-6xl">
            Clothes made to be worn, not noticed.
          </h1>
          <p className="mt-5 max-w-xl text-base leading-relaxed text-muted">
            An editorial edit of everyday pieces: honest materials, quiet
            colours, and a fit that lasts beyond the season.
          </p>

          <div className="mt-8 max-w-xl">
            <CatalogSearch
              basePath="/shop"
              filters={BASE_FILTERS}
              placeholder="Search shirts, trousers, knitwear..."
            />
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-4">
            <Link href="/shop" className={buttonVariants({ size: "lg" })}>
              Shop the collection
            </Link>
            <Link
              href="/shop?onSale=true"
              className={buttonVariants({ variant: "secondary", size: "lg" })}
            >
              View sale
            </Link>
          </div>
        </div>
      </section>

      {categories.length > 0 ? (
        <section className="container-store py-16">
          <div className="flex items-end justify-between gap-6 border-b border-line pb-4">
            <h2 className="text-2xl font-semibold tracking-display text-ink">
              Shop by category
            </h2>
            <Link
              href="/shop"
              className="text-sm text-muted underline underline-offset-4 transition-colors hover:text-ink"
            >
              View all
            </Link>
          </div>
          <CategoryNavigation categories={categories} className="mt-6" />
        </section>
      ) : null}

      {catalogFailed ? (
        <section className="container-store py-16">
          <ErrorState
            title="Catalog unavailable"
            description="We could not load the collection. The storefront will be back shortly."
            action={
              <Link href="/" className={buttonVariants({ variant: "secondary" })}>
                Try again
              </Link>
            }
          />
        </section>
      ) : null}

      {!catalogFailed && !hasProducts ? (
        <section className="container-store py-16">
          <EmptyState
            title="The collection is being prepared"
            description="New pieces will appear here as soon as they are published."
            action={
              <Link href="/shop" className={buttonVariants({ variant: "secondary" })}>
                Browse the shop
              </Link>
            }
          />
        </section>
      ) : null}

      {favourites && favourites.items.length > 0 ? (
        <section className="container-store py-16">
          <div className="flex items-end justify-between gap-6 border-b border-line pb-4">
            <div>
              <p className="text-xs font-medium uppercase tracking-label text-muted">
                Selected pieces
              </p>
              <h2 className="mt-2 text-2xl font-semibold tracking-display text-ink">
                Considered favourites
              </h2>
            </div>
            <Link
              href="/shop?sort=popularity"
              className="text-sm text-muted underline underline-offset-4 transition-colors hover:text-ink"
            >
              View all
            </Link>
          </div>
          <ProductGrid
            products={favourites.items}
            className="mt-8 lg:grid-cols-4"
          />
        </section>
      ) : null}

      <section className="border-y border-line bg-surface">
        <div className="container-store flex flex-col gap-6 py-12 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-label text-accent">
              Last pieces
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-display text-ink">
              The sale edit
            </h2>
            <p className="mt-2 max-w-lg text-sm leading-relaxed text-muted">
              Reduced pieces from the current collection, while availability
              lasts.
            </p>
          </div>
          <Link
            href="/shop?onSale=true"
            className={buttonVariants({ variant: "secondary", size: "lg" })}
          >
            Shop sale
          </Link>
        </div>
      </section>

      {newArrivals && newArrivals.items.length > 0 ? (
        <section className="container-store py-16">
          <div className="flex items-end justify-between gap-6 border-b border-line pb-4">
            <div>
              <p className="text-xs font-medium uppercase tracking-label text-muted">
                Just arrived
              </p>
              <h2 className="mt-2 text-2xl font-semibold tracking-display text-ink">
                New arrivals
              </h2>
            </div>
            <Link
              href="/shop?sort=newest"
              className="text-sm text-muted underline underline-offset-4 transition-colors hover:text-ink"
            >
              View all
            </Link>
          </div>
          <ProductGrid
            products={newArrivals.items}
            className="mt-8 lg:grid-cols-4"
          />
        </section>
      ) : null}
    </>
  )
}
