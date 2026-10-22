import { cache } from "react"
import { api } from "@/lib/api/client"
import type { Paginated } from "@/lib/api/types"
import { ENDPOINTS } from "@/lib/api/endpoints"
import { catalogFiltersToQuery } from "@/features/catalog/query"
import type { CatalogFilters } from "@/lib/validation/catalog"
import type {
  Category,
  ProductDetail,
  ProductSummary,
} from "@/types/catalog"

export function listCategories(): Promise<Category[]> {
  return api.get<Category[]>(ENDPOINTS.categories)
}

export function listProducts(
  filters: CatalogFilters,
): Promise<Paginated<ProductSummary>> {
  return api.getPage<ProductSummary>(ENDPOINTS.products, {
    query: catalogFiltersToQuery(filters),
  })
}

export function listCategoryProducts(
  slug: string,
  filters: CatalogFilters,
): Promise<Paginated<ProductSummary>> {
  return api.getPage<ProductSummary>(ENDPOINTS.categoryProducts(slug), {
    query: catalogFiltersToQuery({ ...filters, category: undefined }),
  })
}

/** Memoized per server request so metadata and the page share one fetch. */
export const getProduct = cache((slug: string): Promise<ProductDetail> => {
  return api.get<ProductDetail>(ENDPOINTS.product(slug))
})
