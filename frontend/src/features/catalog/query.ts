import type { QueryParams } from "@/lib/api/client"
import type { CatalogFilters } from "@/lib/validation/catalog"

/** Serializes filters into the public backend query contract. */
export function catalogFiltersToQuery(filters: CatalogFilters): QueryParams {
  return {
    q: filters.q,
    category: filters.category,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
    variantSize: filters.variantSize,
    color: filters.color,
    inStock: filters.inStock,
    onSale: filters.onSale,
    sort: filters.sort,
    page: filters.page,
    size: filters.size,
  }
}

/**
 * Builds a shareable catalog URL. Defaults are omitted so the common case stays
 * clean, while every active filter is preserved across sorting and pagination.
 */
export function catalogHref(
  basePath: string,
  filters: CatalogFilters,
  overrides: Partial<CatalogFilters> = {},
): string {
  const next: CatalogFilters = { ...filters, ...overrides }
  const params = new URLSearchParams()

  if (next.q) params.set("q", next.q)
  if (next.category) params.set("category", next.category)
  if (next.minPrice !== undefined) params.set("minPrice", String(next.minPrice))
  if (next.maxPrice !== undefined) params.set("maxPrice", String(next.maxPrice))
  if (next.variantSize) params.set("variantSize", next.variantSize)
  if (next.color) params.set("color", next.color)
  if (next.inStock !== undefined) params.set("inStock", String(next.inStock))
  if (next.onSale !== undefined) params.set("onSale", String(next.onSale))
  if (next.sort !== "newest") params.set("sort", next.sort)
  if (next.page > 0) params.set("page", String(next.page))
  if (next.size !== 20) params.set("size", String(next.size))

  const query = params.toString()
  return query ? `${basePath}?${query}` : basePath
}

export function sortLabel(value: CatalogFilters["sort"]): string {
  switch (value) {
    case "price_asc":
      return "Price: low to high"
    case "price_desc":
      return "Price: high to low"
    case "name_asc":
      return "Name: A-Z"
    case "popularity":
      return "Popular"
    case "newest":
    default:
      return "Newest"
  }
}
