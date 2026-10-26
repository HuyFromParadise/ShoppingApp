import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { WishlistEntry } from "@/types/p1"
import WishlistPage from "./page"

let sessionStatus: "loading" | "authenticated" | "unauthenticated" = "authenticated"

vi.mock("@/features/auth/session", () => ({
  useSession: () => ({ status: sessionStatus, user: null }),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function entry(overrides: Partial<WishlistEntry> = {}): WishlistEntry {
  return {
    productId: "p1",
    productName: "Oxford Shirt",
    productSlug: "oxford-shirt",
    currency: "VND",
    priceMinor: 499000,
    compareAtPriceMinor: null,
    inStock: true,
    purchasable: true,
    imageUrl: null,
    savedAt: "2026-10-25T00:00:00Z",
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <WishlistPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function stubServer(items: WishlistEntry[] | Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(
        items instanceof Response ? items : jsonResponse({ data: items, message: "Success" }),
      ),
    ),
  )
}

describe("wishlist page", () => {
  beforeEach(() => {
    sessionStatus = "authenticated"
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("asks a signed-out visitor to sign in", () => {
    sessionStatus = "unauthenticated"
    renderPage()
    expect(screen.getByText("Sign in to see your wishlist")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      "/login?next=%2Fwishlist",
    )
  })

  it("shows an empty state with a way to shop", async () => {
    stubServer([])
    renderPage()
    expect(await screen.findByText("Nothing saved yet")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Browse the shop" })).toHaveAttribute(
      "href",
      "/shop",
    )
  })

  it("lists saved products with their availability in words", async () => {
    stubServer([
      entry(),
      entry({ productId: "p2", productName: "Wool Coat", productSlug: "wool-coat", inStock: false }),
      entry({
        productId: "p3",
        productName: "Retired Tee",
        productSlug: "retired-tee",
        purchasable: false,
      }),
    ])
    renderPage()

    await screen.findByRole("heading", { name: "Saved pieces" })
    expect(screen.getByText("In stock")).toBeInTheDocument()
    expect(screen.getByText("Currently out of stock")).toBeInTheDocument()
    expect(screen.getByText("No longer sold")).toBeInTheDocument()
    // an archived item offers no way to buy it
    const retired = screen.getAllByText("Retired Tee")[0].closest("li") as HTMLElement
    expect(retired.querySelector('a[href="/products/retired-tee"]')).not.toBeNull()
    expect(screen.getAllByRole("link", { name: "Choose options" })).toHaveLength(1)
    expect(screen.getByRole("link", { name: "View product" })).toHaveAttribute(
      "href",
      "/products/wool-coat",
    )
    expect(screen.getByText("3 items")).toBeInTheDocument()
  })

  it("reports a failed load with a retry", async () => {
    stubServer(
      jsonResponse({ timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" }, 500),
    )
    renderPage()
    expect(await screen.findByText("We could not load your wishlist")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument()
  })
})
