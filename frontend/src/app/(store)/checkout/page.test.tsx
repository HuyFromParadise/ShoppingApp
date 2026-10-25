import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { Cart, CartItem } from "@/types/cart"
import CheckoutPage from "./page"

const push = vi.fn()

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function errorResponse(status: number, code: string, message: string): Response {
  return jsonResponse(
    { timestamp: "2026-10-25T00:00:00Z", status, code, message },
    status,
  )
}

function line(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: "i1",
    variantId: "v1",
    productId: "p1",
    productName: "Oxford Shirt",
    productSlug: "oxford-shirt",
    imageUrl: null,
    sku: "OXF-M",
    size: "M",
    color: "White",
    unitPriceMinor: 499000,
    quantity: 2,
    lineTotalMinor: 998000,
    stockQuantity: 5,
    available: true,
    ...overrides,
  }
}

function cartOf(items: CartItem[]): Cart {
  return {
    id: "c1",
    items,
    itemCount: items.reduce((total, item) => total + item.quantity, 0),
    subtotalMinor: items.reduce((total, item) => total + item.lineTotalMinor, 0),
    currency: "VND",
  }
}

const addresses = [
  {
    id: "a1",
    recipientName: "An Nguyen",
    phone: "0901234567",
    addressLine1: "12 Le Loi",
    addressLine2: null,
    ward: "Ben Thanh",
    district: "District 1",
    cityOrProvince: "Ho Chi Minh City",
    postalCode: "700000",
    isDefault: false,
  },
  {
    id: "a2",
    recipientName: "Binh Tran",
    phone: null,
    addressLine1: "9 Nguyen Hue",
    addressLine2: null,
    ward: null,
    district: "District 1",
    cityOrProvince: "Ho Chi Minh City",
    postalCode: null,
    isDefault: true,
  },
]

interface QuoteTotals {
  shippingTotalMinor: number
  grandTotalMinor: number
}

function quoteFor(method: string, subtotal: number): QuoteTotals {
  const shipping = method === "EXPRESS" ? 60000 : 30000
  return { shippingTotalMinor: shipping, grandTotalMinor: subtotal + shipping }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <CheckoutPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** A fake server: cart, addresses, quote, and order creation. */
function stubServer(options: {
  cart: Cart
  quoteError?: Response
  orderError?: Response
}) {
  let quoteCalls = 0
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const target = String(url)
    const method = init?.method ?? "GET"

    if (target.endsWith("/cart")) {
      return Promise.resolve(jsonResponse({ data: options.cart, message: "Success" }))
    }
    if (target.endsWith("/account/addresses")) {
      return Promise.resolve(jsonResponse({ data: addresses, message: "Success" }))
    }
    if (target.endsWith("/checkout/quote") && method === "POST") {
      quoteCalls += 1
      if (options.quoteError) {
        return Promise.resolve(options.quoteError.clone())
      }
      const body = JSON.parse(String(init?.body)) as { shippingMethod: string }
      const totals = quoteFor(body.shippingMethod, options.cart.subtotalMinor)
      return Promise.resolve(
        jsonResponse({
          data: {
            currency: "VND",
            itemCount: options.cart.itemCount,
            subtotalMinor: options.cart.subtotalMinor,
            discountTotalMinor: 0,
            shippingTotalMinor: totals.shippingTotalMinor,
            grandTotalMinor: totals.grandTotalMinor,
            shippingMethodCode: body.shippingMethod,
            shippingMethodName:
              body.shippingMethod === "EXPRESS"
                ? "Express delivery"
                : "Standard delivery",
          },
          message: "Success",
        }),
      )
    }
    if (target.endsWith("/orders") && method === "POST") {
      if (options.orderError) {
        return Promise.resolve(options.orderError.clone())
      }
      return Promise.resolve(
        jsonResponse(
          { data: { id: "o1", orderNumber: "ORD-1" }, message: "Success" },
          201,
        ),
      )
    }
    return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return {
    mock,
    quoteCalls: () => quoteCalls,
  }
}

function orderPosts(mock: Mock<[string, RequestInit?], Promise<Response>>) {
  return mock.mock.calls.filter(
    ([url, init]) => String(url).endsWith("/orders") && init?.method === "POST",
  )
}

describe("checkout page", () => {
  beforeEach(() => {
    push.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("preselects the default address and shows the server's total", async () => {
    stubServer({ cart: cartOf([line()]) })
    renderPage()

    const address = await screen.findByRole("radio", { name: /Binh Tran/ })
    expect(address).toBeChecked()
    expect(await screen.findByText(/You will pay 1\.028\.000/)).toBeInTheDocument()
    // the chosen option is selected; the summary names it too, so assert on the control
    expect(screen.getByRole("radio", { name: /Standard delivery/ })).toBeChecked()
  })

  it("re-quotes when the shipping option changes", async () => {
    const server = stubServer({ cart: cartOf([line()]) })
    renderPage()

    await screen.findByText(/You will pay 1\.028\.000/)
    expect(server.quoteCalls()).toBe(1)

    fireEvent.click(screen.getByRole("radio", { name: /Express delivery/ }))

    expect(await screen.findByText(/You will pay 1\.058\.000/)).toBeInTheDocument()
    expect(server.quoteCalls()).toBe(2)
    expect(screen.getByRole("radio", { name: /Express delivery/ })).toBeChecked()
  })

  it("places the order with the chosen address and then opens its page", async () => {
    const server = stubServer({ cart: cartOf([line()]) })
    renderPage()

    await screen.findByText(/You will pay 1\.028\.000/)
    fireEvent.click(screen.getByRole("button", { name: "Place order" }))

    await waitFor(() => expect(push).toHaveBeenCalledWith("/account/orders/o1?placed=1"))
    const [url, init] = orderPosts(server.mock)[0]
    expect(url).toMatch(/\/orders$/)
    const body = JSON.parse(String(init?.body)) as Record<string, unknown>
    expect(body).toEqual({ addressId: "a2", shippingMethod: "STANDARD" })
    const key = (init?.headers as Record<string, string>)["Idempotency-Key"]
    expect(key).toMatch(/^[A-Za-z0-9_-]{8,80}$/)
  })

  it("keeps one idempotency key while the same order is retried", async () => {
    const server = stubServer({
      cart: cartOf([line()]),
      orderError: errorResponse(500, "INTERNAL_ERROR", "An unexpected error occurred"),
    })
    renderPage()

    await screen.findByText(/You will pay 1\.028\.000/)
    fireEvent.click(screen.getByRole("button", { name: "Place order" }))
    await waitFor(() => expect(orderPosts(server.mock)).toHaveLength(1))

    fireEvent.click(screen.getByRole("button", { name: "Place order" }))
    await waitFor(() => expect(orderPosts(server.mock)).toHaveLength(2))

    const keys = orderPosts(server.mock).map(
      ([, init]) => (init?.headers as Record<string, string>)["Idempotency-Key"],
    )
    expect(keys[0]).toBe(keys[1])
    expect(push).not.toHaveBeenCalled()
    expect(
      (await screen.findAllByText("We could not place your order")).length,
    ).toBeGreaterThan(0)
    // the generic 500 is explained in the storefront's terms, not as an outage
    expect(screen.getAllByText(/Please try again in a moment\./).length).toBeGreaterThan(0)
  })

  it("starts a fresh key after a stock rejection", async () => {
    const server = stubServer({
      cart: cartOf([line()]),
      orderError: errorResponse(409, "OUT_OF_STOCK", "Not enough stock to complete the order"),
    })
    renderPage()

    await screen.findByText(/You will pay 1\.028\.000/)
    fireEvent.click(screen.getByRole("button", { name: "Place order" }))
    await waitFor(() => expect(orderPosts(server.mock)).toHaveLength(1))
    fireEvent.click(screen.getByRole("button", { name: "Place order" }))
    await waitFor(() => expect(orderPosts(server.mock)).toHaveLength(2))

    const keys = orderPosts(server.mock).map(
      ([, init]) => (init?.headers as Record<string, string>)["Idempotency-Key"],
    )
    expect(keys[0]).not.toBe(keys[1])
  })

  it("explains an out-of-stock quote instead of showing a total", async () => {
    stubServer({
      cart: cartOf([line()]),
      quoteError: errorResponse(409, "OUT_OF_STOCK", "Only 1 left in stock"),
    })
    renderPage()

    expect(
      await screen.findByText("An item is no longer available in that quantity"),
    ).toBeInTheDocument()
    expect(screen.getByText(/Only 1 left in stock/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Place order" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Recalculate" })).toBeInTheDocument()
  })

  it("blocks placing an order with an unavailable line", async () => {
    stubServer({ cart: cartOf([line({ available: false, stockQuantity: 0 })]) })
    renderPage()

    expect(await screen.findByText(/No longer available/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Place order" })).toBeDisabled()
  })

  it("sends the customer to add an address when they have none", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        Promise.resolve(
          String(url).endsWith("/cart")
            ? jsonResponse({ data: cartOf([line()]), message: "Success" })
            : jsonResponse({ data: [], message: "Success" }),
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText("Add a delivery address to continue.")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Add an address" })).toHaveAttribute(
      "href",
      "/account/addresses",
    )
    expect(screen.getByRole("button", { name: "Place order" })).toBeDisabled()
  })

  it("shows the empty state instead of an order form for an empty cart", async () => {
    stubServer({ cart: cartOf([]) })
    renderPage()

    expect(await screen.findByText("Your cart is empty.")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Place order" })).not.toBeInTheDocument()
  })

  it("includes the delivery note in the order request", async () => {
    const server = stubServer({ cart: cartOf([line()]) })
    renderPage()

    await screen.findByText(/You will pay 1\.028\.000/)
    fireEvent.change(screen.getByLabelText(/Delivery note/), {
      target: { value: "  Leave at reception  " },
    })
    fireEvent.click(screen.getByRole("button", { name: "Place order" }))

    await waitFor(() => expect(orderPosts(server.mock)).toHaveLength(1))
    const body = JSON.parse(String(orderPosts(server.mock)[0][1]?.body)) as Record<
      string,
      unknown
    >
    expect(body.customerNote).toBe("Leave at reception")
  })
})
