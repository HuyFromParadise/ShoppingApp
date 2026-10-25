import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import AdminOrderPage from "./page"

vi.mock("next/navigation", () => ({
  useParams: () => ({ orderId: "o1" }),
  usePathname: () => "/admin/orders/o1",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function order(overrides: Record<string, unknown> = {}) {
  return {
    id: "o1",
    orderNumber: "ORD-7F3K9QX2",
    status: "PROCESSING",
    paymentStatus: "PAID",
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
    customerNote: null,
    placedAt: "2026-10-25T02:00:00Z",
    items: [
      {
        id: "i1",
        variantId: "v1",
        productName: "Oxford Shirt",
        sku: "OXF-WHITE-M",
        size: "M",
        color: "White",
        imageUrl: null,
        unitPriceMinor: 499000,
        quantity: 2,
        lineTotalMinor: 998000,
      },
    ],
    statusHistory: [
      {
        fromStatus: "CONFIRMED",
        toStatus: "PROCESSING",
        note: "Packed",
        changedAt: "2026-10-25T02:30:00Z",
      },
    ],
    ...overrides,
  }
}

function stubServer(options: { patch?: (body: unknown) => Response } = {}) {
  let current = order()
  const mock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET"
    if (method === "PATCH" && String(url).endsWith("/status")) {
      const body = JSON.parse(String(init?.body)) as { status: string; note?: string }
      if (options.patch) return Promise.resolve(options.patch(body))
      current = order({ status: body.status })
      return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
    }
    return Promise.resolve(jsonResponse({ data: current, message: "Success" }))
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
        <AdminOrderPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function statusPatches(mock: ReturnType<typeof stubServer>) {
  return mock.mock.calls.filter(([, init]) => init?.method === "PATCH")
}

describe("admin order detail", () => {
  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("shows the order, its items, address and history", async () => {
    stubServer()
    renderPage()

    expect(await screen.findByText("ORD-7F3K9QX2")).toBeInTheDocument()
    expect(screen.getAllByText("Processing").length).toBeGreaterThan(0)
    expect(screen.getByText("Oxford Shirt")).toBeInTheDocument()
    expect(screen.getByText("White / M · ×2")).toBeInTheDocument()
    expect(screen.getAllByText(/1\.028\.000/).length).toBeGreaterThan(0)
    expect(screen.getByText("Ben Thanh, District 1, Ho Chi Minh City, 700000")).toBeInTheDocument()
    expect(screen.getByText("Packed")).toBeInTheDocument()
  })

  it("offers only the transitions the state machine allows", async () => {
    stubServer()
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    expect(screen.getByRole("button", { name: "Mark shipped" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Cancel order" })).toBeInTheDocument()
    // not reachable from PROCESSING
    expect(screen.queryByRole("button", { name: "Mark delivered" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Start processing" })).not.toBeInTheDocument()
  })

  it("warns that cancelling releases stock and refunds before confirming", async () => {
    stubServer()
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    fireEvent.click(screen.getByRole("button", { name: "Cancel order" }))

    expect(
      screen.getByText(/Any stock the order holds is released and a settled payment is marked refunded/),
    ).toBeInTheDocument()
  })

  it("sends the transition with a note and then re-reads the order", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    fireEvent.click(screen.getByRole("button", { name: "Mark shipped" }))
    fireEvent.change(screen.getByLabelText(/Note/), {
      target: { value: "Handed to courier" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }))

    await waitFor(() => expect(statusPatches(mock)).toHaveLength(1))
    expect(JSON.parse(String(statusPatches(mock)[0][1]?.body))).toEqual({
      status: "SHIPPED",
      note: "Handed to courier",
    })
    // the new status comes from the re-read, not from local guessing
    await waitFor(() => {
      const shipped = screen.getAllByText("Shipped")
      expect(shipped.length).toBeGreaterThan(0)
    })
    expect(screen.getByRole("button", { name: "Mark delivered" })).toBeInTheDocument()
  })

  it("can back out of a pending change without calling the server", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    fireEvent.click(screen.getByRole("button", { name: "Mark shipped" }))
    fireEvent.click(screen.getByRole("button", { name: "Keep as is" }))

    expect(screen.queryByRole("button", { name: "Confirm" })).not.toBeInTheDocument()
    expect(statusPatches(mock)).toHaveLength(0)
  })

  it("shows an invalid transition from the server instead of hiding it", async () => {
    stubServer({
      patch: () =>
        jsonResponse(
          {
            timestamp: "x",
            status: 409,
            code: "INVALID_STATE_TRANSITION",
            message: "An order cannot go from SHIPPED to CANCELLED",
          },
          409,
        ),
    })
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    fireEvent.click(screen.getByRole("button", { name: "Mark shipped" }))
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }))

    expect(await screen.findByText("That change is not allowed")).toBeInTheDocument()
    expect(
      screen.getAllByText(/An order cannot go from SHIPPED to CANCELLED/).length,
    ).toBeGreaterThan(0)
  })

  it("says a delivered order only changes through a customer return request", async () => {
    stubServer()
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(jsonResponse({ data: order({ status: "DELIVERED" }), message: "Success" })),
      ),
    )
    renderPage()

    expect(
      await screen.findByText(/Delivered orders can only change further when the customer requests a return/),
    ).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Request return" })).not.toBeInTheDocument()
  })

  it("offers the return decisions when a customer asks for one", async () => {
    stubServer()
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse({ data: order({ status: "RETURN_REQUESTED" }), message: "Success" }),
        ),
      ),
    )
    renderPage()

    await screen.findByText("ORD-7F3K9QX2")
    expect(screen.getByRole("button", { name: "Accept return" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Reject return" })).toBeInTheDocument()

    fireEvent.click(screen.getByRole("button", { name: "Accept return" }))
    expect(screen.getByText(/returned items go back into stock/)).toBeInTheDocument()
  })

  it("reports a missing order", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          jsonResponse(
            { timestamp: "x", status: 404, code: "NOT_FOUND", message: "Order not found" },
            404,
          ),
        ),
      ),
    )
    renderPage()

    expect(await screen.findByText("We could not find that order")).toBeInTheDocument()
    expect(within(document.body).getByRole("link", { name: "Back to orders" })).toHaveAttribute(
      "href",
      "/admin/orders",
    )
  })
})
