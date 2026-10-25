import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { Cart, CartItem } from "@/types/cart"
import CartPage from "./page"

// The guard is exercised by its own test; here the page is the subject.
vi.mock("@/features/auth/route-guards", () => ({
  RequireAuth: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function line(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: "i1",
    variantId: "v1",
    productId: "p1",
    productName: "Oxford Shirt",
    productSlug: "oxford-shirt",
    imageUrl: "/images/oxford-shirt.jpg",
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

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <CartPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("cart page", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>
  let serverCart: Cart

  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET"
      const target = String(url)

      if (method === "PATCH" && target.includes("/cart/items/")) {
        const itemId = target.split("/").pop()
        const { quantity } = JSON.parse(String(init?.body)) as {
          quantity: number
        }
        serverCart = cartOf(
          serverCart.items.map((item) =>
            item.id === itemId
              ? {
                  ...item,
                  quantity,
                  lineTotalMinor: item.unitPriceMinor * quantity,
                }
              : item,
          ),
        )
        return Promise.resolve(
          jsonResponse({ data: serverCart, message: "Success" }),
        )
      }

      if (method === "DELETE" && target.includes("/cart/items/")) {
        const itemId = target.split("/").pop()
        serverCart = cartOf(
          serverCart.items.filter((item) => item.id !== itemId),
        )
        return Promise.resolve(new Response(null, { status: 204 }))
      }

      if (method === "DELETE" && target.endsWith("/cart")) {
        serverCart = cartOf([])
        return Promise.resolve(new Response(null, { status: 204 }))
      }

      return Promise.resolve(
        jsonResponse({ data: serverCart, message: "Success" }),
      )
    })
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("lists the lines with the server's totals", async () => {
    serverCart = cartOf([
      line(),
      line({
        id: "i2",
        variantId: "v2",
        productId: "p2",
        productName: "Canvas Tote",
        productSlug: "canvas-tote",
        sku: "TOTE-01",
        size: null,
        color: "Natural",
        unitPriceMinor: 250000,
        quantity: 1,
        lineTotalMinor: 250000,
        stockQuantity: 3,
      }),
    ])
    renderPage()

    const shirt = await screen.findByRole("listitem", { name: "Oxford Shirt" })
    expect(within(shirt).getByText("White / M")).toBeInTheDocument()
    expect(within(shirt).getByText(/998\.000/)).toBeInTheDocument()

    const tote = screen.getByRole("listitem", { name: "Canvas Tote" })
    expect(within(tote).getByText("Natural")).toBeInTheDocument()

    // subtotal 998.000 + 250.000
    expect(screen.getByText(/1\.248\.000/)).toBeInTheDocument()
    expect(screen.getByText("3")).toBeInTheDocument()
  })

  it("shows the empty state when the cart has no lines", async () => {
    serverCart = cartOf([])
    renderPage()

    expect(await screen.findByText("Your cart is empty")).toBeInTheDocument()
    expect(
      screen.getByRole("link", { name: "Continue shopping" }),
    ).toBeInTheDocument()
  })

  it("reports a failed load instead of rendering an empty cart", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            timestamp: "2026-10-22T00:00:00Z",
            status: 500,
            code: "INTERNAL_ERROR",
            message: "An unexpected error occurred",
          },
          500,
        ),
      ),
    )
    renderPage()

    expect(
      await screen.findByText("We could not load your cart"),
    ).toBeInTheDocument()
  })

  it("sends the new quantity and re-reads the cart", async () => {
    serverCart = cartOf([line()])
    renderPage()

    const shirt = await screen.findByRole("listitem", { name: "Oxford Shirt" })
    fireEvent.click(
      within(shirt).getByRole("button", { name: "Increase quantity" }),
    )

    await waitFor(() => {
      const patch = fetchMock.mock.calls.find(
        ([, init]) => init?.method === "PATCH",
      )
      expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ quantity: 3 })
    })

    expect(
      await within(shirt).findByRole("button", { name: "Increase quantity" }),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(within(shirt).getByText("3")).toBeInTheDocument(),
    )
    // the server total replaced the old one: 499.000 × 3, on the line and in the subtotal
    expect(screen.getAllByText(/1\.497\.000/)).toHaveLength(2)
  })

  it("removes a line", async () => {
    serverCart = cartOf([line()])
    renderPage()

    const shirt = await screen.findByRole("listitem", { name: "Oxford Shirt" })
    fireEvent.click(within(shirt).getByRole("button", { name: /Remove/ }))

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE"),
      ).toBe(true),
    )
    expect(await screen.findByText("Your cart is empty")).toBeInTheDocument()
  })

  it("clears the whole cart after confirmation", async () => {
    serverCart = cartOf([line()])
    renderPage()

    fireEvent.click(
      await screen.findByRole("button", { name: "Clear cart" }),
    )

    expect(window.confirm).toHaveBeenCalled()
    await waitFor(() => {
      const clear = fetchMock.mock.calls.find(
        ([url, init]) =>
          init?.method === "DELETE" && String(url).endsWith("/cart"),
      )
      expect(clear).toBeDefined()
    })
    expect(await screen.findByText("Your cart is empty")).toBeInTheDocument()
  })

  it("flags a line that can no longer be bought", async () => {
    serverCart = cartOf([
      line({ available: false, stockQuantity: 0, quantity: 5 }),
    ])
    renderPage()

    const shirt = await screen.findByRole("listitem", { name: "Oxford Shirt" })
    expect(within(shirt).getByText("No longer available")).toBeInTheDocument()
    expect(
      within(shirt).getByRole("button", { name: "Increase quantity" }),
    ).toBeDisabled()
    expect(
      within(shirt).getByRole("button", { name: "Decrease quantity" }),
    ).toBeDisabled()
  })

  it("leads to checkout when every line can be bought", async () => {
    serverCart = cartOf([line()])
    renderPage()

    const checkout = await screen.findByRole("link", {
      name: "Proceed to checkout",
    })
    expect(checkout).toHaveAttribute("href", "/checkout")
    expect(checkout).not.toHaveAttribute("aria-disabled")
  })

  it("blocks checkout while a line is unavailable and says why", async () => {
    serverCart = cartOf([line({ available: false, stockQuantity: 0 })])
    renderPage()

    const checkout = await screen.findByRole("link", {
      name: "Proceed to checkout",
    })
    expect(checkout).toHaveAttribute("aria-disabled", "true")

    fireEvent.click(checkout)

    expect(
      await screen.findByText("Remove the unavailable items first"),
    ).toBeInTheDocument()
    // the click must not navigate
    expect(checkout).toHaveAttribute("href", "/checkout")
  })
})
