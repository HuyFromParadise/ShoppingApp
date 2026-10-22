import { describe, expect, it } from "vitest"
import { z } from "zod"
import {
  pageSizeSchema,
  paginationSchema,
  parseSearchParams,
  toSearchParamsRecord,
} from "./pagination"

describe("pagination validation", () => {
  it("applies defaults when params are absent", () => {
    expect(paginationSchema.parse({})).toEqual({ page: 0, size: 20 })
  })

  it("coerces numeric strings from the URL", () => {
    expect(paginationSchema.parse({ page: "2", size: "12" })).toEqual({
      page: 2,
      size: 12,
    })
  })

  it("rejects out-of-range and non-integer sizes", () => {
    expect(pageSizeSchema.safeParse("49").success).toBe(false)
    expect(pageSizeSchema.safeParse("0").success).toBe(false)
    expect(pageSizeSchema.safeParse("1.5").success).toBe(false)
  })

  it("parses URLSearchParams and rejects unknown keys for strict schemas", () => {
    const strict = paginationSchema.strict()
    const params = new URLSearchParams("page=1&size=8")
    expect(parseSearchParams(strict, params)).toEqual({ page: 1, size: 8 })

    const withUnknown = new URLSearchParams("page=1&size=8&unexpected=x")
    expect(() => parseSearchParams(strict, withUnknown)).toThrow(z.ZodError)
  })

  it("normalizes Next.js repeated search params", () => {
    expect(
      toSearchParamsRecord({ page: ["1", "3"], empty: undefined, size: "10" }),
    ).toEqual({ page: "3", size: "10" })
  })
})
