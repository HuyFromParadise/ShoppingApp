import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import { tokenStore } from "@/lib/api/token"
import type { Cart } from "@/types/cart"
import { AddToCart } from "./add-to-cart"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const cart: Cart = {
  id: "c1",
  items: [],
  itemCount: 1,
  subtotalMinor: 499000,
  currency: "VND",
}

function renderAddToCart(stockQuantity = 5) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AddToCart variantId="v1" stockQuantity={stockQuantity} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("AddToCart", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    tokenStore.set("access-token")
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse({ data: cart, message: "Success" })),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it("adds the selected quantity and confirms with a toast", async () => {
    renderAddToCart()

    fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }))
    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }))

    expect(await screen.findByText("Added to your cart")).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/cart\/items$/)
    expect(JSON.parse(String(init?.body))).toEqual({
      variantId: "v1",
      quantity: 2,
    })
  })

  it("never offers more than the per-line cap", () => {
    renderAddToCart(20)

    expect(screen.getByText("1")).toBeInTheDocument()
    for (let i = 1; i < 10; i += 1) {
      fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }))
    }

    expect(screen.getByText("10")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Increase quantity" }),
    ).toBeDisabled()
  })

  it("stops at the stock on hand", () => {
    renderAddToCart(2)

    fireEvent.click(screen.getByRole("button", { name: "Increase quantity" }))

    expect(screen.getByText("2")).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: "Increase quantity" }),
    ).toBeDisabled()
  })

  it("shows the server message when the stock ran out", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            timestamp: "2026-10-22T00:00:00Z",
            status: 409,
            code: "OUT_OF_STOCK",
            message: "Only 3 left in stock",
          },
          409,
        ),
      ),
    )
    renderAddToCart()

    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }))

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Only 3 left in stock",
    )
  })

  it("asks the visitor to sign in when the session expired", async () => {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        String(url).includes("/auth/refresh")
          ? jsonResponse({ status: 401, code: "UNAUTHENTICATED" }, 401)
          : jsonResponse(
              {
                timestamp: "2026-10-22T00:00:00Z",
                status: 401,
                code: "UNAUTHENTICATED",
                message: "Authentication required",
              },
              401,
            ),
      ),
    )
    renderAddToCart()

    fireEvent.click(screen.getByRole("button", { name: "Add to cart" }))

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sign in to add items to your cart.",
    )
  })

  it("is disabled and explained when the variant is sold out", () => {
    renderAddToCart(0)

    expect(screen.getByRole("button", { name: "Out of stock" })).toBeDisabled()
    expect(
      screen.getByRole("button", { name: "Decrease quantity" }),
    ).toBeDisabled()

    fireEvent.click(screen.getByRole("button", { name: "Out of stock" }))
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
