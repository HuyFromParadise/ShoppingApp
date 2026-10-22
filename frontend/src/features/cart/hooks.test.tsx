import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook } from "@testing-library/react"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { tokenStore } from "@/lib/api/token"
import type { Cart } from "@/types/cart"
import {
  CART_KEYS,
  useAddCartItem,
  useClearCart,
  useRemoveCartItem,
  useUpdateCartItem,
} from "./hooks"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const cart: Cart = {
  id: "c1",
  items: [],
  itemCount: 0,
  subtotalMinor: 0,
  currency: "VND",
}

function newClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

/** Fresh client per test, so the invalidation spy sees the hook's own client. */
function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }
}

describe("cart hooks", () => {
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

  it("invalidates the cart after adding an item", async () => {
    const queryClient = newClient()
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useAddCartItem(), {
      wrapper: wrapperFor(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ variantId: "v1", quantity: 2 })
    })

    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_KEYS.cart })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/cart\/items$/)
    expect(init?.method).toBe("POST")
    expect(JSON.parse(String(init?.body))).toEqual({
      variantId: "v1",
      quantity: 2,
    })
    // the access token travels with the authenticated call
    expect((init?.headers as Record<string, string>).Authorization).toBe(
      "Bearer access-token",
    )
  })

  it("invalidates the cart after changing a quantity", async () => {
    const queryClient = newClient()
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useUpdateCartItem(), {
      wrapper: wrapperFor(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({ itemId: "i1", quantity: 3 })
    })

    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_KEYS.cart })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/cart\/items\/i1$/)
    expect(init?.method).toBe("PATCH")
    expect(JSON.parse(String(init?.body))).toEqual({ quantity: 3 })
  })

  it("invalidates the cart after removing an item", async () => {
    const queryClient = newClient()
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useRemoveCartItem(), {
      wrapper: wrapperFor(queryClient),
    })

    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(new Response(null, { status: 204 })),
    )

    await act(async () => {
      await result.current.mutateAsync("i1")
    })

    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_KEYS.cart })
    expect(fetchMock.mock.calls[0][1]?.method).toBe("DELETE")
  })

  it("invalidates the cart after clearing it", async () => {
    const queryClient = newClient()
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useClearCart(), {
      wrapper: wrapperFor(queryClient),
    })

    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(new Response(null, { status: 204 })),
    )

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_KEYS.cart })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/cart$/)
    expect(init?.method).toBe("DELETE")
  })
})
