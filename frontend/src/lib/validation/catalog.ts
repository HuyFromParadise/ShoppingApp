import { z } from "zod"
import {
  pageSchema,
  pageSizeSchema,
  toSearchParamsRecord,
} from "./pagination"

export const CATALOG_SORT_KEYS = [
  "newest",
  "price_asc",
  "price_desc",
  "name_asc",
  "popularity",
] as const

export type CatalogSort = (typeof CATALOG_SORT_KEYS)[number]

function emptyToUndefined(value: unknown): unknown {
  return value === "" || value === undefined || value === null
    ? undefined
    : value
}

function optionalText(max: number, message: string) {
  return z.preprocess(
    emptyToUndefined,
    z.string().trim().max(max, message).optional(),
  )
}

function optionalNonNegativeInteger(message: string) {
  return z.preprocess(
    emptyToUndefined,
    z.coerce.number().int(message).min(0, message).optional(),
  )
}

function optionalBoolean(message: string) {
  return z.preprocess((value) => {
    if (value === "" || value === undefined || value === null) return undefined
    if (value === "true") return true
    if (value === "false") return false
    return value
  }, z.boolean({ invalid_type_error: message }).optional())
}

/**
 * Strict catalog filter contract for URL search params.
 *
 * `size` is the page size. The apparel size filter is `variantSize`, matching
 * the public backend contract.
 */
export const catalogFilterSchema = z
  .object({
    q: optionalText(100, "Search text must be at most 100 characters"),
    category: optionalText(200, "Category must be at most 200 characters"),
    minPrice: optionalNonNegativeInteger(
      "Minimum price must be a non-negative integer",
    ),
    maxPrice: optionalNonNegativeInteger(
      "Maximum price must be a non-negative integer",
    ),
    variantSize: optionalText(32, "Size must be at most 32 characters"),
    color: optionalText(64, "Colour must be at most 64 characters"),
    inStock: optionalBoolean("In-stock filter must be true or false"),
    onSale: optionalBoolean("Sale filter must be true or false"),
    sort: z.enum(CATALOG_SORT_KEYS).default("newest"),
    page: pageSchema,
    size: pageSizeSchema,
  })
  .strict()
  .superRefine((value, context) => {
    if (
      value.minPrice !== undefined &&
      value.maxPrice !== undefined &&
      value.minPrice > value.maxPrice
    ) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["minPrice"],
        message: "Minimum price must be less than or equal to maximum price",
      })
    }
  })

export type CatalogFilters = z.infer<typeof catalogFilterSchema>

export function parseCatalogFilters(
  input: URLSearchParams | Record<string, string | string[] | undefined>,
): CatalogFilters {
  return catalogFilterSchema.parse(toSearchParamsRecord(input))
}

export function safeParseCatalogFilters(
  input: URLSearchParams | Record<string, string | string[] | undefined>,
) {
  return catalogFilterSchema.safeParse(toSearchParamsRecord(input))
}

export function hasActiveCatalogFilters(filters: CatalogFilters): boolean {
  return Boolean(
    filters.q ||
      filters.category ||
      filters.minPrice !== undefined ||
      filters.maxPrice !== undefined ||
      filters.variantSize ||
      filters.color ||
      filters.inStock !== undefined ||
      filters.onSale !== undefined,
  )
}
