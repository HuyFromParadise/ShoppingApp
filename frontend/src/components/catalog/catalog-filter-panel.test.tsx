import { fireEvent, render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { catalogFilterSchema } from "@/lib/validation/catalog"
import type { Category } from "@/types/catalog"
import { CatalogFilterPanel } from "./catalog-filter-panel"

const routerPush = vi.hoisted(() => vi.fn())

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}))

const categories: Category[] = [
  {
    id: "c1",
    name: "Tops",
    slug: "tops",
    description: "Shirts and knitwear",
    parentId: null,
    sortOrder: 1,
  },
  {
    id: "c2",
    name: "Trousers",
    slug: "trousers",
    description: null,
    parentId: null,
    sortOrder: 2,
  },
]

const currentFilters = catalogFilterSchema.parse({
  q: "shirt",
  sort: "price_asc",
  size: "12",
})

describe("CatalogFilterPanel", () => {
  beforeEach(() => {
    routerPush.mockClear()
  })

  it("serializes changed filters to the catalog URL and resets the page", () => {
    render(
      <CatalogFilterPanel
        basePath="/shop"
        filters={currentFilters}
        categories={categories}
      />,
    )

    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "tops" },
    })
    fireEvent.change(screen.getByLabelText("Size"), {
      target: { value: "M" },
    })
    fireEvent.change(screen.getByLabelText("Colour"), {
      target: { value: "Black" },
    })
    fireEvent.change(screen.getByLabelText("Minimum price"), {
      target: { value: "100000" },
    })
    fireEvent.change(screen.getByLabelText("Maximum price"), {
      target: { value: "500000" },
    })
    fireEvent.click(screen.getByLabelText("In stock only"))
    fireEvent.click(screen.getByLabelText("On sale"))
    fireEvent.click(screen.getByRole("button", { name: "Apply filters" }))

    expect(routerPush).toHaveBeenCalledWith(
      "/shop?q=shirt&category=tops&minPrice=100000&maxPrice=500000&variantSize=M&color=Black&inStock=true&onSale=true&sort=price_asc&size=12",
    )
  })

  it("clears every filter without leaving the catalog", () => {
    render(
      <CatalogFilterPanel
        basePath="/shop"
        filters={currentFilters}
        categories={categories}
      />,
    )

    fireEvent.click(screen.getByRole("button", { name: "Clear all" }))
    expect(routerPush).toHaveBeenCalledWith("/shop")
  })

  it("hides the category picker when the category is fixed by the route", () => {
    render(
      <CatalogFilterPanel
        basePath="/categories/tops"
        filters={currentFilters}
        categories={categories}
        lockedCategory="tops"
      />,
    )

    expect(screen.queryByLabelText("Category")).not.toBeInTheDocument()
  })
})
