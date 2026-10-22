import { z } from "zod"

/**
 * Pagination contract shared with the backend (DESIGN_REFERENCE §5.4):
 * `page >= 0`, `size` 1–48, default 20.
 */
export const pageSchema = z.coerce.number().int().min(0).default(0)
export const pageSizeSchema = z.coerce.number().int().min(1).max(48).default(20)

export const paginationSchema = z.object({
  page: pageSchema,
  size: pageSizeSchema,
})

export type PaginationParams = z.infer<typeof paginationSchema>

/**
 * Converts Next.js `searchParams` (or a `URLSearchParams`) into a plain object
 * for Zod. Repeated keys keep the last value, matching simple filter semantics.
 */
export function toSearchParamsRecord(
  input: URLSearchParams | Record<string, string | string[] | undefined>,
): Record<string, string> {
  if (input instanceof URLSearchParams) {
    return Object.fromEntries(input.entries())
  }

  const record: Record<string, string> = {}
  for (const [key, value] of Object.entries(input)) {
    if (Array.isArray(value)) {
      if (value.length > 0) record[key] = value[value.length - 1]
    } else if (value !== undefined) {
      record[key] = value
    }
  }
  return record
}

/**
 * Parses URL search params with a schema. Unknown keys are rejected when the
 * schema is `.strict()`; callers should surface the resulting `ZodError` as a
 * 400-style page state rather than silently dropping filters.
 */
export function parseSearchParams<TSchema extends z.ZodTypeAny>(
  schema: TSchema,
  input: URLSearchParams | Record<string, string | string[] | undefined>,
): z.infer<TSchema> {
  return schema.parse(toSearchParamsRecord(input))
}
