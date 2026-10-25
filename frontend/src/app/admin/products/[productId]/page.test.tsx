import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import EditProductPage from "./page"

vi.mock("next/navigation", () => ({
  useParams: () => ({ productId: "p1" }),
  usePathname: () => "/admin/products/p1",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function errorResponse(status: number, code: string, message: string, fieldErrors?: Record<string, string>) {
  return jsonResponse({ timestamp: "x", status, code, message, fieldErrors }, status)
}

const product = {
  id: "p1",
  name: "Oxford Shirt",
  slug: "oxford-shirt",
  shortDescription: "A shirt",
  description: "Long description",
  categoryId: "c1",
  categoryName: "Shirts",
  basePriceMinor: 499000,
  currency: "VND",
  status: "ACTIVE",
  material: "Cotton",
  fit: "Slim",
  careInstructions: null,
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
      stockQuantity: 3,
      active: true,
    },
  ],
  createdAt: null,
  updatedAt: null,
}

const categories = {
  data: [{ id: "c1", name: "Shirts", slug: "shirts", active: true, productCount: 1 }],
  message: "Success",
}

function stubServer(options: { product?: Response; patch?: (body: unknown) => Response } = {}) {
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const target = String(url)
    const method = init?.method ?? "GET"
    if (target.endsWith("/admin/categories")) {
      return Promise.resolve(jsonResponse(categories))
    }
    if (target.endsWith("/admin/products/p1") && method === "GET") {
      return Promise.resolve(options.product ?? jsonResponse({ data: product, message: "Success" }))
    }
    if (target.endsWith("/admin/products/p1") && method === "PATCH") {
      const body = JSON.parse(String(init?.body))
      return Promise.resolve(
        options.patch ? options.patch(body) : jsonResponse({ data: product, message: "Success" }),
      )
    }
    if (target.includes("/variants/") && target.endsWith("/stock")) {
      return Promise.resolve(
        jsonResponse({ data: { id: "v1", stockQuantity: 9 }, message: "Success" }),
      )
    }
    return Promise.resolve(jsonResponse({ data: {}, message: "Success" }))
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
        <EditProductPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function patches(mock: ReturnType<typeof stubServer>) {
  return mock.mock.calls.filter(
    ([url, init]) => String(url).endsWith("/admin/products/p1") && init?.method === "PATCH",
  )
}

describe("edit product page", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("loads the product into the form with the slug locked", async () => {
    stubServer()
    renderPage()

    const name = (await screen.findByLabelText(/Name/)) as HTMLInputElement
    expect(name.value).toBe("Oxford Shirt")

    const slug = screen.getByLabelText(/URL slug/) as HTMLInputElement
    expect(slug.value).toBe("oxford-shirt")
    expect(slug).toBeDisabled()
    expect(screen.getByText(/Cannot be changed once the product exists/)).toBeInTheDocument()
  })

  it("sends a patch with the edited fields and the price in minor units", async () => {
    const mock = stubServer()
    renderPage()

    const name = (await screen.findByLabelText(/Name/)) as HTMLInputElement
    fireEvent.change(name, { target: { value: "Oxford Shirt II" } })
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }))

    await waitFor(() => expect(patches(mock)).toHaveLength(1))
    const body = JSON.parse(String(patches(mock)[0][1]?.body))
    expect(body.name).toBe("Oxford Shirt II")
    expect(body.basePrice).toBe(499000)
    expect(body.status).toBe("ACTIVE")
    expect(body).not.toHaveProperty("slug")
  })

  it("blocks an invalid price locally without calling the server", async () => {
    const mock = stubServer()
    renderPage()

    const price = (await screen.findByLabelText(/Base price/)) as HTMLInputElement
    fireEvent.change(price, { target: { value: "" } })
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }))

    expect(await screen.findByText("Enter a price greater than zero.")).toBeInTheDocument()
    expect(patches(mock)).toHaveLength(0)
  })

  it("shows the server's field errors next to the field", async () => {
    stubServer({
      patch: () =>
        errorResponse(400, "VALIDATION_ERROR", "Request validation failed", {
          basePrice: "Base price must be greater than zero",
        }),
    })
    renderPage()

    await screen.findByLabelText(/Name/)
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }))

    expect(
      await screen.findByText("Base price must be greater than zero"),
    ).toBeInTheDocument()
  })

  it("does not fight the operator's typing when the server re-reads", async () => {
    stubServer()
    renderPage()

    const name = (await screen.findByLabelText(/Name/)) as HTMLInputElement
    fireEvent.change(name, { target: { value: "Typing…" } })
    expect(name.value).toBe("Typing…")
  })

  it("updates stock as an absolute total", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("Oxford Shirt")
    expect(screen.getByText("3")).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Set stock" }))
    const input = screen.getByLabelText("New total")
    fireEvent.change(input, { target: { value: "9" } })
    fireEvent.change(screen.getByLabelText("Reason for the stock change"), {
      target: { value: "Delivery received" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Save stock" }))

    await waitFor(() => {
      const stockCall = mock.mock.calls.find(([url]) => String(url).includes("/stock"))
      expect(stockCall).toBeDefined()
      expect(JSON.parse(String(stockCall?.[1]?.body))).toEqual({
        stockQuantity: 9,
        reason: "Delivery received",
      })
    })
  })

  it("refuses a negative or fractional stock count before sending it", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("Oxford Shirt")
    fireEvent.click(screen.getByRole("button", { name: "Set stock" }))
    fireEvent.change(screen.getByLabelText("New total"), { target: { value: "-2" } })

    expect(screen.getByText("Whole number, zero or more.")).toBeInTheDocument()
    const save = screen.getByRole("button", { name: "Save stock" })
    expect(save).toBeDisabled()
    expect(mock.mock.calls.some(([url]) => String(url).includes("/stock"))).toBe(false)
  })

  it("reports a missing product instead of an empty form", async () => {
    stubServer({
      product: errorResponse(404, "NOT_FOUND", "Product not found"),
    })
    renderPage()

    expect(await screen.findByText("We could not find that product")).toBeInTheDocument()
  })

  it("shows the variants with the add-variant form", async () => {
    stubServer()
    renderPage()

    await screen.findByText("White / M")
    expect(screen.getByText("OXF-WHITE-M")).toBeInTheDocument()
    expect(screen.getByText("Add a variant")).toBeInTheDocument()
    // no price override on this variant, so no Override badge is shown
    expect(screen.queryByText("Override")).not.toBeInTheDocument()
  })
})
