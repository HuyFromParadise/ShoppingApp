"use client"

import { useRouter } from "next/navigation"
import { CATALOG_SORT_KEYS, type CatalogFilters } from "@/lib/validation/catalog"
import { catalogHref, sortLabel } from "@/features/catalog/query"

export interface SortSelectProps {
  basePath: string
  filters: CatalogFilters
}

export function SortSelect({ basePath, filters }: SortSelectProps) {
  const router = useRouter()

  return (
    <div className="flex items-center gap-2">
      <label
        htmlFor="catalog-sort"
        className="whitespace-nowrap text-sm text-muted"
      >
        Sort by
      </label>
      <select
        id="catalog-sort"
        value={filters.sort}
        onChange={(event) => {
          const sort = event.target.value as CatalogFilters["sort"]
          router.push(catalogHref(basePath, filters, { sort, page: 0 }))
        }}
        className="h-10 rounded border border-line bg-surface px-3 text-sm text-ink focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
      >
        {CATALOG_SORT_KEYS.map((key) => (
          <option key={key} value={key}>
            {sortLabel(key)}
          </option>
        ))}
      </select>
    </div>
  )
}
