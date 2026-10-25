import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import AdminInventoryPage from "./page"

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/inventory",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const productList = {
  data: [
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
      totalStock: 3,
      primaryImageUrl: null,
      createdAt: null,
      updatedAt: null,
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  message: "Success",
}

const detail = {
  id: "p1",
  name: "Oxford Shirt",
  slug: "oxford-shirt",
  categoryId: "c1",
  categoryName: "Shirts",
  basePriceMinor: 499000,
  currency: "VND",
  status: "ACTIVE",
  featured: false,
  images: [],
  variants: [
    {
      id: "v1",
      sku: "OXF-WHITE-M",
      size: "M",
      color: "White",
      priceOverrideMinor: null,
      effectivePriceMinor: 499000,
      stockQuantity: 0,
      active: true,
    },
    {
      id: "v2",
      sku: "OXF-BLACK-L",
      size: "L",
      color: "Black",
      priceOverrideMinor: null,
      effectivePriceMinor: 499000,
      stockQuantity: 12,
      active: false,
    },
  ],
}

function stubServer(options: { list?: Response; stock?: Response } = {}) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const target = String(url)
    const method = init?.method ?? "GET"
    if (method === "PATCH" && target.endsWith("/stock")) {
      return Promise.resolve(
        options.stock ?? jsonResponse({ data: { id: "v1", stockQuantity: 8 }, message: "Success" }),
      )
    }
    if (target.endsWith("/admin/products/p1")) {
      return Promise.resolve(jsonResponse({ data: detail, message: "Success" }))
    }
    if (target.includes("/admin/products")) {
      return Promise.resolve(options.list ?? jsonResponse(productList))
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
      <ToastProvider>
        <AdminInventoryPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("admin inventory page", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists every variant, worst stock first", async () => {
    stubServer()
    renderPage()

    const table = await screen.findByRole("table", { name: "Variant stock" })
    await waitFor(() => expect(within(table).getByText("OXF-WHITE-M")).toBeInTheDocument())

    const rows = within(table).getAllByRole("row").slice(1)
    // the out-of-stock variant is first
    expect(within(rows[0]).getByText("OXF-WHITE-M")).toBeInTheDocument()
    expect(within(rows[1]).getByText("OXF-BLACK-L")).toBeInTheDocument()
    // inactive variants are still listed, marked as such
    expect(within(rows[1]).getByText("Inactive")).toBeInTheDocument()
  })

  it("summarises how many variants are low or out", async () => {
    stubServer()
    renderPage()

    await screen.findByText("OXF-WHITE-M")
    // "Low" counts stock <= 5 (one), "Out" counts zero (one)
    const low = screen.getByText("Low").parentElement as HTMLElement
    expect(within(low).getByText("1")).toBeInTheDocument()
    const out = screen.getByText("Out").parentElement as HTMLElement
    expect(within(out).getByText("1")).toBeInTheDocument()
  })

  it("filters by text and by low stock", async () => {
    stubServer()
    renderPage()

    const table = await screen.findByRole("table", { name: "Variant stock" })
    await waitFor(() => expect(within(table).getByText("OXF-WHITE-M")).toBeInTheDocument())

    fireEvent.change(screen.getByPlaceholderText("Product, SKU, colour or size"), {
      target: { value: "black" },
    })
    expect(within(table).queryByText("OXF-WHITE-M")).not.toBeInTheDocument()
    expect(within(table).getByText("OXF-BLACK-L")).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText("Product, SKU, colour or size"), {
      target: { value: "" },
    })
    fireEvent.click(screen.getByRole("checkbox", { name: /Only low stock/ }))
    expect(within(table).getByText("OXF-WHITE-M")).toBeInTheDocument()
    expect(within(table).queryByText("OXF-BLACK-L")).not.toBeInTheDocument()
  })

  it("sets an absolute stock total with a reason", async () => {
    const mock = stubServer()
    renderPage()

    const table = await screen.findByRole("table", { name: "Variant stock" })
    await waitFor(() => expect(within(table).getByText("OXF-WHITE-M")).toBeInTheDocument())

    const buttons = within(table).getAllByRole("button", { name: "Set stock" })
    fireEvent.click(buttons[0])
    fireEvent.change(screen.getByLabelText("New total"), { target: { value: "8" } })
    fireEvent.change(screen.getByLabelText("Reason for the stock change"), {
      target: { value: "Recount" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Save stock" }))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "PATCH")
      expect(call).toBeDefined()
      expect(String(call?.[0])).toContain("/admin/variants/v1/stock")
      // an absolute total, never a delta
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({
        stockQuantity: 8,
        reason: "Recount",
      })
    })
    expect(await screen.findByText("Stock updated")).toBeInTheDocument()
  })

  it("shows the server's refusal and keeps the editor open", async () => {
    stubServer({
      stock: jsonResponse(
        { timestamp: "x", status: 400, code: "VALIDATION_ERROR", message: "Stock cannot be negative" },
        400,
      ),
    })
    renderPage()

    const table = await screen.findByRole("table", { name: "Variant stock" })
    await waitFor(() => expect(within(table).getByText("OXF-WHITE-M")).toBeInTheDocument())

    fireEvent.click(within(table).getAllByRole("button", { name: "Set stock" })[0])
    fireEvent.change(screen.getByLabelText("New total"), { target: { value: "4" } })
    fireEvent.click(screen.getByRole("button", { name: "Save stock" }))

    expect(await screen.findByText("Stock cannot be negative")).toBeInTheDocument()
    expect(screen.getByLabelText("New total")).toBeInTheDocument()
  })

  it("offers a retry when the product list fails", async () => {
    stubServer({
      list: jsonResponse({ timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" }, 500),
    })
    renderPage()

    expect(await screen.findByText("We could not load inventory")).toBeInTheDocument()
  })
})
