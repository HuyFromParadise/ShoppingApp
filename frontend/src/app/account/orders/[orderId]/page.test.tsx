import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { OrderDetail } from "@/types/orders"
import OrderDetailPage from "./page"

const search = new URLSearchParams()

vi.mock("next/navigation", () => ({
  useParams: () => ({ orderId: "o1" }),
  useSearchParams: () => search,
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

function detail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: "o1",
    orderNumber: "ORD-7F3K9QX2",
    status: "PENDING_PAYMENT",
    paymentStatus: "UNPAID",
    currency: "VND",
    subtotalMinor: 998000,
    discountTotalMinor: 0,
    shippingTotalMinor: 30000,
    grandTotalMinor: 1028000,
    shippingMethodCode: "STANDARD",
    shippingMethodName: "Standard delivery",
    shippingAddress: {
      recipientName: "An Nguyen",
      phone: "0901234567",
      addressLine1: "12 Le Loi",
      addressLine2: null,
      ward: "Ben Thanh",
      district: "District 1",
      cityOrProvince: "Ho Chi Minh City",
      postalCode: "700000",
    },
    customerNote: "Leave at reception",
    placedAt: "2026-10-24T10:00:00Z",
    items: [
      {
        id: "i1",
        variantId: "v1",
        productName: "Oxford Shirt",
        sku: "OXF-M",
        size: "M",
        color: "White",
        imageUrl: null,
        unitPriceMinor: 499000,
        quantity: 2,
        lineTotalMinor: 998000,
      },
    ],
    statusHistory: [
      { fromStatus: null, toStatus: "PENDING_PAYMENT", note: "Order placed", changedAt: "2026-10-24T10:00:00Z" },
    ],
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
        <OrderDetailPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function orderGets(mock: Mock<[string, RequestInit?], Promise<Response>>) {
  return mock.mock.calls.filter(
    ([url, init]) => /\/orders\/o1$/.test(String(url)) && (init?.method ?? "GET") === "GET",
  )
}

describe("order detail page", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    for (const key of Array.from(search.keys())) search.delete(key)
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("renders the order, its address snapshot, totals and history", async () => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse({ data: detail(), message: "Success" })),
    )
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    expect(await screen.findByText("Order ORD-7F3K9QX2")).toBeInTheDocument()
    expect(screen.getByText("Awaiting payment")).toBeInTheDocument()
    expect(screen.getByText("Unpaid")).toBeInTheDocument()
    expect(screen.getByText("Oxford Shirt")).toBeInTheDocument()
    expect(screen.getByText("White / M")).toBeInTheDocument()
    // the total appears in the summary and in the payment panel; both are server amounts
    expect(screen.getAllByText(/1\.028\.000/).length).toBeGreaterThan(0)
    expect(screen.getByText("Ben Thanh, District 1, Ho Chi Minh City, 700000")).toBeInTheDocument()
    // rendered in curly quotes, which is why the assertion is a regex
    expect(screen.getByText(/Leave at reception/)).toBeInTheDocument()
    expect(screen.getAllByText("Order placed").length).toBeGreaterThan(0)
  })

  it("pays through the labelled mock gateway and refreshes the order", async () => {
    let current = detail()
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const target = String(url)
      const method = init?.method ?? "GET"

      if (/\/orders\/o1$/.test(target)) {
        return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
      }
      if (target.endsWith("/payments/o1/mock/create") && method === "POST") {
        return Promise.resolve(
          jsonResponse({
            data: {
              paymentId: "p1",
              orderId: "o1",
              provider: "MOCK",
              status: "PENDING",
              providerTransactionId: "MOCK-1",
              paymentUrl: null,
              mock: true,
              createdAt: null,
            },
            message: "Success",
          }),
        )
      }
      if (target.includes("/payments/mock/p1/confirm") && method === "POST") {
        current = { ...current, status: "CONFIRMED", paymentStatus: "PAID" }
        return Promise.resolve(
          jsonResponse({
            data: {
              orderId: "o1",
              orderNumber: "ORD-7F3K9QX2",
              orderStatus: "CONFIRMED",
              paymentStatus: "PAID",
              provider: "MOCK",
              paid: true,
            },
            message: "Success",
          }),
        )
      }
      return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
    })
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole("button", { name: "Pay now" }))

    expect(await screen.findByText("Test payment (no money is charged)")).toBeInTheDocument()
    expect(screen.getByText(/no money is charged/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Approve test payment" }))

    expect(await screen.findByText("Payment received")).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText("Confirmed")).toBeInTheDocument())
    expect(screen.getByText("Paid")).toBeInTheDocument()
    // the panel is gone: a paid order is no longer payable
    expect(screen.queryByRole("button", { name: "Pay now" })).not.toBeInTheDocument()
  })

  it("cancels a pending order after confirmation", async () => {
    let current = detail()
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const target = String(url)
      const method = init?.method ?? "GET"
      if (/\/orders\/o1$/.test(target) && method === "GET") {
        return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
      }
      if (target.endsWith("/orders/o1/cancel") && method === "POST") {
        current = {
          ...current,
          status: "CANCELLED",
          statusHistory: [
            ...current.statusHistory,
            {
              fromStatus: "PENDING_PAYMENT",
              toStatus: "CANCELLED",
              note: "Cancelled by customer",
              changedAt: "2026-10-25T00:00:00Z",
            },
          ],
        }
        return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
      }
      return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
    })
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole("button", { name: "Cancel order" }))

    expect(window.confirm).toHaveBeenCalled()
    expect(await screen.findByText("Order cancelled")).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText("Cancelled")).toBeInTheDocument())
    expect(screen.getByText("Cancelled by customer")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Cancel order" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Pay now" })).not.toBeInTheDocument()
  })

  it("shows the server's refusal instead of hiding the failure", async () => {
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET"
      if (method === "POST") {
        return Promise.resolve(
          errorResponse(409, "INVALID_STATE_TRANSITION", "An order cannot go from SHIPPED to CANCELLED"),
        )
      }
      return Promise.resolve(
        jsonResponse({
          data: detail({ status: "PROCESSING", paymentStatus: "PAID" }),
          message: "Success",
        }),
      )
    })
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    await screen.findByText("Order ORD-7F3K9QX2")
    expect(screen.getAllByText("Processing").length).toBeGreaterThan(0)
    // no cancel button for an order the admin already took over
    expect(screen.queryByRole("button", { name: "Cancel order" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Request a return" })).not.toBeInTheDocument()
  })

  it("requests a return for a shipped order", async () => {
    let current = detail({
      status: "SHIPPED",
      paymentStatus: "PAID",
    })
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const target = String(url)
      const method = init?.method ?? "GET"
      if (/\/orders\/o1$/.test(target) && method === "GET") {
        return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
      }
      if (target.endsWith("/orders/o1/return") && method === "POST") {
        current = { ...current, status: "RETURN_REQUESTED" }
        return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
      }
      return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
    })
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole("button", { name: "Request a return" }))

    await waitFor(() =>
      expect(screen.getAllByText("Return requested").length).toBeGreaterThan(0),
    )
  })

  it("treats a foreign or missing order as not found", async () => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(errorResponse(404, "NOT_FOUND", "Order not found")),
    )
    vi.stubGlobal("fetch", fetchMock)
    renderPage()

    expect(await screen.findByText("We could not find that order")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Back to your orders" })).toHaveAttribute(
      "href",
      "/account/orders",
    )
    expect(orderGets(fetchMock)).toHaveLength(1)
  })

  it("confirms a just-placed order and offers the payment", async () => {
    search.set("placed", "1")
    vi.stubGlobal(
      "fetch",
      vi.fn((_url: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse({ data: detail(), message: "Success" })),
      ),
    )
    renderPage()

    const banner = await screen.findByRole("status")
    expect(banner).toHaveTextContent("Thank you — your order is placed.")
    const pay = within(screen.getByRole("article")).getByRole("button", {
      name: "Pay now",
    })
    expect(pay).toBeInTheDocument()
  })
})
