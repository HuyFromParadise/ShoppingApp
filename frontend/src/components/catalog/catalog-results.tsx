import Link from "next/link"
import { ProductGrid } from "@/components/product/product-grid"
import { buttonVariants } from "@/components/ui/button"
import type { Paginated } from "@/lib/api/types"
import type { Category, ProductSummary } from "@/types/catalog"
import {
  hasActiveCatalogFilters,
  type CatalogFilters,
} from "@/lib/validation/catalog"
import { CatalogFilterDrawer } from "./catalog-filter-drawer"
import { CatalogFilterPanel } from "./catalog-filter-panel"
import { CatalogSearch } from "./catalog-search"
import { Pagination } from "./pagination"
import { SortSelect } from "./sort-select"

export interface CatalogResultsProps {
  basePath: string
  filters: CatalogFilters
  page: Paginated<ProductSummary>
  categories: Category[]
  lockedCategory?: string
}

export function CatalogResults({
  basePath,
  filters,
  page,
  categories,
  lockedCategory,
}: CatalogResultsProps) {
  const hasFilters = hasActiveCatalogFilters(filters)

  return (
    <div className="grid gap-10 lg:grid-cols-[14rem_minmax(0,1fr)] lg:gap-12">
      <aside className="hidden lg:block">
        <CatalogFilterPanel
          basePath={basePath}
          filters={filters}
          categories={categories}
          lockedCategory={lockedCategory}
          idPrefix="desktop-catalog"
        />
      </aside>

      <div>
        <div className="flex flex-col gap-4 border-b border-line pb-5 sm:flex-row sm:items-center sm:justify-between">
          <CatalogSearch
            basePath={basePath}
            filters={filters}
            className="max-w-md"
          />
          <div className="flex items-center justify-between gap-4 sm:justify-end">
            <CatalogFilterDrawer
              basePath={basePath}
              filters={filters}
              categories={categories}
              lockedCategory={lockedCategory}
            />
            <SortSelect basePath={basePath} filters={filters} />
          </div>
        </div>

        <p className="mt-5 text-sm text-muted" aria-live="polite">
          {page.totalElements === 0
            ? "No products"
            : `${page.totalElements} product${page.totalElements === 1 ? "" : "s"}`}
        </p>

        <ProductGrid
          products={page.items}
          className="mt-6"
          emptyAction={
            hasFilters ? (
              <Link
                href={basePath}
                className={buttonVariants({ variant: "secondary", size: "sm" })}
              >
                Clear filters
              </Link>
            ) : undefined
          }
        />

        <Pagination
          basePath={basePath}
          filters={filters}
          page={page.page}
          totalPages={page.totalPages}
          totalElements={page.totalElements}
          size={page.size}
          className="mt-12"
        />
      </div>
    </div>
  )
}
