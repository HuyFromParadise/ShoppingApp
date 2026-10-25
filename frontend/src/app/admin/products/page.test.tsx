import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import AdminProductsPage from "./page"

const push = vi.fn()
const search = new URLSearchParams()

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => search,
  usePathname: () => "/admin/products",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const products = [
  {
    id: "p1",
    name: "Oxford Shirt",
    slug: "oxford-shirt",
    categoryId: "c1",
    categoryName: "Shirts",
    basePriceMinor: 499000,
    currency: "VND",
    status: "ACTIVE",
    featured: false,
    variantCount: 2,
    totalStock: 7,
    primaryImageUrl: null,
    createdAt: null,
    updatedAt: null,
  },
  {
    id: "p2",
    name: "Archived Coat",
    slug: "archived-coat",
    categoryId: "c2",
    categoryName: "Outerwear",
    basePriceMinor: 1899000,
    currency: "VND",
    status: "ARCHIVED",
    featured: false,
    variantCount: 1,
    totalStock: 0,
    primaryImageUrl: null,
    createdAt: null,
    updatedAt: null,
  },
]

function stubServer(options: { products?: Response; categories?: Response } = {}) {
  const mock = vi.fn((url: string) => {
    const target = String(url)
    if (target.includes("/admin/products")) {
      return Promise.resolve(
        options.products ??
          jsonResponse({
            data: products,
            page: 0,
            size: 20,
            totalElements: 2,
            totalPages: 1,
            message: "Success",
          }),
      )
    }
    if (target.endsWith("/admin/categories")) {
      return Promise.resolve(
        options.categories ??
          jsonResponse({
            data: [
              { id: "c1", name: "Shirts", slug: "shirts", active: true },
              { id: "c2", name: "Outerwear", slug: "outerwear", active: false },
            ],
            message: "Success",
          }),
      )
    }
    return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return mock
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminProductsPage />
    </QueryClientProvider>,
  )
}

describe("admin products page", () => {
  beforeEach(() => {
    push.mockReset()
    for (const key of Array.from(search.keys())) search.delete(key)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists products with status, price and stock", async () => {
    stubServer()
    renderPage()

    const row = await screen.findByRole("link", { name: "Oxford Shirt" })
    expect(row).toHaveAttribute("href", "/admin/products/p1")
    // statuses also appear as filter options, so scope to the table
    const table = screen.getByRole("table", { name: "Products" })
    expect(within(table).getByText("Live")).toBeInTheDocument()
    expect(within(table).getByText("Archived")).toBeInTheDocument()
    expect(screen.getByText(/499\.000/)).toBeInTheDocument()
    expect(within(table).getByText("oxford-shirt")).toBeInTheDocument()
  })

  it("reads filters from the URL and sends them to the API", async () => {
    search.set("status", "ARCHIVED")
    const mock = stubServer()
    renderPage()

    await screen.findByRole("link", { name: "Archived Coat" })
    expect(mock.mock.calls[0][0]).toContain("status=ARCHIVED")
  })

  it("applies a status filter through the URL, resetting the page", async () => {
    search.set("page", "2")
    stubServer()
    renderPage()

    await screen.findByRole("link", { name: "Oxford Shirt" })
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "DRAFT" } })

    await waitFor(() => expect(push).toHaveBeenCalled())
    const target = push.mock.calls.at(-1)?.[0] as string
    expect(target).toBe("/admin/products?status=DRAFT")
    expect(target).not.toContain("page=")
  })

  it("searches by text on submit", async () => {
    stubServer()
    renderPage()

    await screen.findByRole("link", { name: "Oxford Shirt" })
    fireEvent.change(screen.getByLabelText("Search"), { target: { value: "  coat " } })
    fireEvent.click(screen.getByRole("button", { name: "Search" }))

    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/admin/products?q=coat"),
    )
  })

  it("offers every category as a filter, including hidden ones", async () => {
    stubServer()
    renderPage()

    const select = (await screen.findByLabelText("Category")) as HTMLSelectElement
    // categories load after the products, so wait for the options to fill
    await waitFor(() => {
      const values = Array.from(select.options).map((option) => option.value)
      expect(values).toContain("c1")
      expect(values).toContain("c2")
    })
  })

  it("shows an empty state for an unfiltered list with no products", async () => {
    stubServer({
      products: jsonResponse({
        data: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        message: "Success",
      }),
    })
    renderPage()

    expect(await screen.findByText("No products yet")).toBeInTheDocument()
    const newLinks = screen.getAllByRole("link", { name: "New product" })
    expect(newLinks.every((link) => link.getAttribute("href") === "/admin/products/new")).toBe(
      true,
    )
  })

  it("explains an empty filtered result instead of offering onboarding", async () => {
    search.set("status", "DRAFT")
    stubServer({
      products: jsonResponse({
        data: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        message: "Success",
      }),
    })
    renderPage()

    expect(await screen.findByText("No products match these filters.")).toBeInTheDocument()
    expect(screen.queryByText("No products yet")).not.toBeInTheDocument()
  })

  it("offers a retry when the list fails", async () => {
    stubServer({
      products: jsonResponse(
        { timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" },
        500,
      ),
    })
    renderPage()

    expect(await screen.findByText("We could not load the products")).toBeInTheDocument()
  })
})
