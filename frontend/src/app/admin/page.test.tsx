import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { AdminDashboardSummary } from "@/types/admin"
import AdminDashboardPage from "./page"

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/admin",
}))

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function summary(overrides: Partial<AdminDashboardSummary> = {}): AdminDashboardSummary {
  return {
    ordersByStatus: {
      PENDING_PAYMENT: 2,
      CONFIRMED: 1,
      PROCESSING: 3,
      RETURN_REQUESTED: 1,
    },
    revenueLast30Days: {
      since: null,
      paidOrders: 4,
      revenueMinor: 4232000,
      averageOrderValueMinor: 1058000,
      ordersPlaced: 7,
    },
    revenueAllTime: {
      since: null,
      paidOrders: 9,
      revenueMinor: 9000000,
      averageOrderValueMinor: 1000000,
      ordersPlaced: 12,
    },
    activeProducts: 12,
    draftProducts: 3,
    archivedProducts: 1,
    lowStock: [
      {
        variantId: "v1",
        sku: "OXF-WHITE-M",
        size: "M",
        color: "White",
        stockQuantity: 0,
        productName: "Oxford Shirt",
        productId: "p1",
      },
      {
        variantId: "v2",
        sku: "TOTE-01",
        size: null,
        color: null,
        stockQuantity: 3,
        productName: "Canvas Tote",
        productId: "p2",
      },
    ],
    lowStockThreshold: 5,
    generatedAt: "2026-10-25T03:00:00Z",
    ...overrides,
  }
}

const recentOrders = {
  data: [
    {
      id: "o1",
      orderNumber: "ORD-7F3K9QX2",
      status: "PENDING_PAYMENT",
      paymentStatus: "UNPAID",
      currency: "VND",
      grandTotalMinor: 1028000,
      itemCount: 2,
      customerId: "c1",
      customerEmail: "an@example.com",
      shipRecipientName: "An Nguyen",
      placedAt: "2026-10-25T02:00:00Z",
    },
  ],
  page: 0,
  size: 8,
  totalElements: 1,
  totalPages: 1,
  message: "Success",
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminDashboardPage />
    </QueryClientProvider>,
  )
}

function stubServer(options: { summary?: Response; orders?: Response; categories?: Response } = {}) {
  const mock = vi.fn((url: string) => {
    const target = String(url)
    if (target.endsWith("/admin/dashboard/summary")) {
      return Promise.resolve(options.summary ?? jsonResponse({ data: summary(), message: "Success" }))
    }
    if (target.includes("/admin/orders")) {
      return Promise.resolve(options.orders ?? jsonResponse(recentOrders))
    }
    if (target.endsWith("/admin/categories")) {
      return Promise.resolve(
        options.categories ?? jsonResponse({ data: [{ id: "c1" }, { id: "c2" }], message: "Success" }),
      )
    }
    return Promise.resolve(jsonResponse({ data: null, message: "Success" }))
  })
  vi.stubGlobal("fetch", mock)
  return mock
}

describe("admin dashboard", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("shows settled revenue separately from all-time revenue", async () => {
    stubServer()
    renderPage()

    const last30 = (await screen.findByText("Last 30 days")).closest("div") as HTMLElement
    expect(within(last30).getByText(/4\.232\.000/)).toBeInTheDocument()

    const allTime = screen.getByText("All time").closest("div") as HTMLElement
    expect(within(allTime).getByText(/9\.000\.000/)).toBeInTheDocument()

    // the definition is stated, not implied
    expect(
      screen.getByText(/Revenue counts settled payments only/),
    ).toBeInTheDocument()
  })

  it("shows the work queues with counts and links to the filtered lists", async () => {
    stubServer()
    renderPage()

    const awaiting = await screen.findByRole("link", {
      name: /Awaiting payment or confirmation/,
    })
    // PENDING_PAYMENT (2) + CONFIRMED (1)
    expect(within(awaiting).getByText("3")).toBeInTheDocument()
    expect(awaiting).toHaveAttribute("href", "/admin/orders?status=PENDING_PAYMENT")

    const toShip = screen.getByRole("link", { name: /To ship/ })
    expect(within(toShip).getByText("3")).toBeInTheDocument()
    expect(toShip).toHaveAttribute("href", "/admin/orders?status=PROCESSING")

    const returns = screen.getByRole("link", { name: /Return requests/ })
    expect(within(returns).getByText("1")).toBeInTheDocument()
    expect(returns).toHaveAttribute("href", "/admin/orders?status=RETURN_REQUESTED")
  })

  it("lists low stock variants, calling out the ones that are out", async () => {
    stubServer()
    renderPage()

    await screen.findByText("Oxford Shirt")
    expect(screen.getByText("Out")).toBeInTheDocument()
    expect(screen.getByText("3 left")).toBeInTheDocument()
    expect(screen.getByText("OXF-WHITE-M · White / M")).toBeInTheDocument()
  })

  it("shows catalogue counts with links to the filtered product list", async () => {
    stubServer()
    renderPage()

    const live = await screen.findByRole("link", { name: "Live products" })
    expect(live).toHaveAttribute("href", "/admin/products?status=ACTIVE")
    expect(screen.getByText("12")).toBeInTheDocument()

    // category count comes from the category list, not the summary
    const categories = screen.getByRole("link", { name: "Categories" })
    expect(categories).toHaveAttribute("href", "/admin/categories")
    expect(await screen.findByText("2")).toBeInTheDocument()
  })

  it("shows the latest orders with their status", async () => {
    stubServer()
    renderPage()

    const link = await screen.findByRole("link", { name: /ORD-7F3K9QX2/ })
    expect(link).toHaveAttribute("href", "/admin/orders/o1")
    expect(within(link).getByText("Awaiting payment")).toBeInTheDocument()
    expect(within(link).getByText("Unpaid")).toBeInTheDocument()
    expect(within(link).getByText("an@example.com")).toBeInTheDocument()
  })

  it("says so when nothing is running low", async () => {
    stubServer({
      summary: jsonResponse({
        data: summary({ lowStock: [] }),
        message: "Success",
      }),
    })
    renderPage()

    expect(await screen.findByText("Nothing running low")).toBeInTheDocument()
    expect(screen.getByText(/above 5 units/)).toBeInTheDocument()
  })

  it("offers a retry when the summary fails", async () => {
    stubServer({
      summary: jsonResponse(
        { timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" },
        500,
      ),
    })
    renderPage()

    expect(await screen.findByText("We could not load the dashboard")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument()
  })

  it("keeps the page usable when the recent orders fail to load", async () => {
    stubServer({
      orders: jsonResponse({ timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" }, 500),
    })
    renderPage()

    // the revenue section still renders
    await screen.findByText("Last 30 days")
    expect(await screen.findByText("Orders could not be loaded.")).toBeInTheDocument()
  })
})
