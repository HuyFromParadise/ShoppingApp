import { render, screen, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import CategoriesPage, { metadata } from "./page"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const categories = [
  { id: "c1", name: "Áo", slug: "ao", description: "Shirts and knitwear", parentId: null, sortOrder: 1 },
  { id: "c2", name: "Quần", slug: "quan", description: null, parentId: null, sortOrder: 2 },
  { id: "c3", name: "Áo sơ mi", slug: "ao-so-mi", description: null, parentId: "c1", sortOrder: 1 },
  { id: "c4", name: "Áo len", slug: "ao-len", description: null, parentId: "c1", sortOrder: 2 },
  // Its parent is hidden, so the API returns it without the parent: it must
  // still be reachable rather than vanish from the index.
  { id: "c5", name: "Mũ", slug: "mu", description: null, parentId: "hidden-parent", sortOrder: 3 },
]

function stubServer(body: unknown, status = 200) {
  vi.stubGlobal(
    "fetch",
    vi.fn((_url: string, _init?: RequestInit) => Promise.resolve(jsonResponse(body, status))),
  )
}

describe("categories page", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("declares a canonical URL and a title", () => {
    expect(metadata.title).toBe("Categories")
    expect(metadata.alternates).toEqual({ canonical: "/categories" })
  })

  it("lists parents with their children nested underneath", async () => {
    stubServer({ data: categories, message: "Success" })
    render(await CategoriesPage())

    const ao = screen.getByRole("link", { name: "Áo" }).closest("li") as HTMLElement
    expect(within(ao).getByRole("link", { name: "Áo sơ mi" })).toHaveAttribute(
      "href",
      "/categories/ao-so-mi",
    )
    expect(within(ao).getByRole("link", { name: "Áo len" })).toBeInTheDocument()
    expect(within(ao).getByText("Shirts and knitwear")).toBeInTheDocument()

    const quan = screen.getByRole("link", { name: "Quần" }).closest("li") as HTMLElement
    expect(within(quan).queryByRole("list")).toBeNull()
  })

  it("keeps an orphaned child visible instead of dropping it", async () => {
    stubServer({ data: categories, message: "Success" })
    render(await CategoriesPage())

    expect(screen.getByRole("link", { name: "Mũ" })).toHaveAttribute("href", "/categories/mu")
  })

  it("orders by sort order, not by the API order", async () => {
    stubServer({
      data: [
        { id: "c2", name: "Quần", slug: "quan", description: null, parentId: null, sortOrder: 2 },
        { id: "c1", name: "Áo", slug: "ao", description: null, parentId: null, sortOrder: 1 },
      ],
      message: "Success",
    })
    render(await CategoriesPage())

    const items = screen.getAllByRole("link", { name: /^(Áo|Quần)$/ })
    expect(items[0]).toHaveTextContent("Áo")
    expect(items[1]).toHaveTextContent("Quần")
  })

  it("offers a retry when the catalog is down", async () => {
    stubServer(
      { timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" },
      500,
    )
    render(await CategoriesPage())

    expect(screen.getByText("Categories unavailable")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Try again" })).toHaveAttribute(
      "href",
      "/categories",
    )
  })
})
