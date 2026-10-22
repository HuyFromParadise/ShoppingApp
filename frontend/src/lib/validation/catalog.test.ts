import { describe, expect, it } from "vitest"
import { catalogFilterSchema, parseCatalogFilters } from "./catalog"
import { catalogHref } from "@/features/catalog/query"

describe("catalog filter validation", () => {
  it("applies pagination and sort defaults", () => {
    expect(catalogFilterSchema.parse({})).toEqual({
      sort: "newest",
      page: 0,
      size: 20,
    })
  })

  it("coerces supported filters from URL strings", () => {
    const filters = parseCatalogFilters(
      new URLSearchParams(
        "q=shirt&category=tops&minPrice=100000&maxPrice=500000&variantSize=M&color=Black&inStock=true&onSale=false&sort=price_asc&page=2&size=12",
      ),
    )

    expect(filters).toMatchObject({
      q: "shirt",
      category: "tops",
      minPrice: 100000,
      maxPrice: 500000,
      variantSize: "M",
      color: "Black",
      inStock: true,
      onSale: false,
      sort: "price_asc",
      page: 2,
      size: 12,
    })
  })

  it("rejects unknown keys, invalid sort values and inverted prices", () => {
    expect(() =>
      parseCatalogFilters(new URLSearchParams("unexpected=value")),
    ).toThrow()
    expect(() =>
      parseCatalogFilters(new URLSearchParams("sort=cheapest")),
    ).toThrow()
    expect(() =>
      parseCatalogFilters(
        new URLSearchParams("minPrice=500000&maxPrice=100000"),
      ),
    ).toThrow()
    expect(() =>
      parseCatalogFilters(new URLSearchParams("inStock=maybe")),
    ).toThrow()
  })

  it("builds shareable URLs and preserves filters while paging", () => {
    const filters = parseCatalogFilters(
      new URLSearchParams(
        "q=shirt&variantSize=M&color=Black&sort=price_asc&page=1&size=12",
      ),
    )

    expect(catalogHref("/shop", filters)).toBe(
      "/shop?q=shirt&variantSize=M&color=Black&sort=price_asc&page=1&size=12",
    )
    expect(catalogHref("/shop", filters, { page: 2 })).toContain(
      "page=2",
    )
    expect(catalogHref("/shop", filters, { sort: "newest", page: 0 })).toBe(
      "/shop?q=shirt&variantSize=M&color=Black&size=12",
    )
  })
})
