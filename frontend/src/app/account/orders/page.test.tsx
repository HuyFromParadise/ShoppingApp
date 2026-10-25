import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import type { OrderSummary } from "@/types/orders"
import OrdersPage from "./page"

const search = new URLSearchParams()

vi.mock("next/navigation", () => ({
  useSearchParams: () => search,
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function summary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    id: "o1",
    orderNumber: "ORD-7F3K9QX2",
    status: "PENDING_PAYMENT",
    paymentStatus: "UNPAID",
    currency: "VND",
    grandTotalMinor: 1028000,
    itemCount: 2,
    placedAt: "2026-10-24T10:00:00Z",
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <OrdersPage />
    </QueryClientProvider>,
  )
}

describe("order history page", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    for (const key of Array.from(search.keys())) search.delete(key)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists the orders with their status and total", async () => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(
        jsonResponse({
          data: [
            summary(),
            summary({
              id: "o2",
              orderNumber: "ORD-ABC12345",
              status: "DELIVERED",
              paymentStatus: "PAID",
              grandTotalMinor: 529000,
              itemCount: 1,
            }),
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
          message: "Success",
        }),
      ),
    )
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    const first = await screen.findByRole("link", { name: /ORD-7F3K9QX2/ })
    expect(within(first).getByText("Awaiting payment")).toBeInTheDocument()
    expect(within(first).getByText("Unpaid")).toBeInTheDocument()
    expect(within(first).getByText(/1\.028\.000/)).toBeInTheDocument()
    expect(within(first).getByText(/2 items/)).toBeInTheDocument()
    expect(first).toHaveAttribute("href", "/account/orders/o1")

    const second = screen.getByRole("link", { name: /ORD-ABC12345/ })
    expect(within(second).getByText("Delivered")).toBeInTheDocument()
    expect(within(second).getByText("Paid")).toBeInTheDocument()
    expect(within(second).getByText(/1 item$/)).toBeInTheDocument()

    expect(screen.getByText("2 orders")).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/orders\?page=0&size=20$/)
  })

  it("shows the empty state for a customer with no orders", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse({
            data: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
            message: "Success",
          }),
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText("No orders yet")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Start shopping" })).toHaveAttribute(
      "href",
      "/shop",
    )
  })

  it("reads the requested page from the URL and links to the neighbours", async () => {
    search.set("page", "1")
    fetchMock = vi.fn((_url: string, _init?: RequestInit) => Promise.resolve(jsonResponse({
      data: [summary({ id: "o3", orderNumber: "ORD-PAGE2XXX" })],
      page: 1,
      size: 20,
      totalElements: 25,
      totalPages: 2,
      message: "Success",
    })))
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    await screen.findByText("ORD-PAGE2XXX")
    expect(fetchMock.mock.calls[0][0]).toMatch(/page=1&size=20$/)
    expect(screen.getByRole("link", { name: "Newer orders" })).toHaveAttribute(
      "href",
      "/account/orders",
    )
    expect(screen.queryByRole("link", { name: "Older orders" })).not.toBeInTheDocument()
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument()
  })

  it("recovers a bad page parameter instead of failing", async () => {
    search.set("page", "-3")
    const fetchMockLocal = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(
        jsonResponse({
          data: [summary()],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          message: "Success",
        }),
      ),
    )
    vi.stubGlobal("fetch", fetchMockLocal)
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    // an invalid page falls back to the first page rather than sending -3
    expect(fetchMockLocal.mock.calls[0][0]).toMatch(/page=0&size=20$/)
  })

  it("reports a failed load with a retry", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse(
            {
              timestamp: "2026-10-25T00:00:00Z",
              status: 500,
              code: "INTERNAL_ERROR",
              message: "An unexpected error occurred",
            },
            500,
          ),
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText("We could not load your orders")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument()
  })
})
