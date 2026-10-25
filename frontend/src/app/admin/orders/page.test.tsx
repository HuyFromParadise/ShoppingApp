import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import AdminOrdersPage from "./page"

const push = vi.fn()
const search = new URLSearchParams()

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => search,
  usePathname: () => "/admin/orders",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function stubServer(options: { list?: Response } = {}) {
  const mock = vi.fn((_url: string, _init?: RequestInit) =>
    Promise.resolve(
      options.list ??
        jsonResponse({
          data: [
            {
              id: "o1",
              orderNumber: "ORD-7F3K9QX2",
              status: "RETURN_REQUESTED",
              paymentStatus: "PAID",
              currency: "VND",
              grandTotalMinor: 1028000,
              itemCount: 2,
              customerId: "c1",
              customerEmail: "an@example.com",
              shipRecipientName: "An Nguyen",
              placedAt: "2026-10-25T02:00:00Z",
            },
            {
              id: "o2",
              orderNumber: "ORD-ABC12345",
              status: "PROCESSING",
              paymentStatus: "PAID",
              currency: "VND",
              grandTotalMinor: 529000,
              itemCount: 1,
              customerId: "c2",
              customerEmail: null,
              shipRecipientName: "Binh Tran",
              placedAt: "2026-10-24T02:00:00Z",
            },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
          message: "Success",
        }),
    ),
  )
  vi.stubGlobal("fetch", mock)
  return mock
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminOrdersPage />
    </QueryClientProvider>,
  )
}

describe("admin orders list", () => {
  beforeEach(() => {
    push.mockReset()
    for (const key of Array.from(search.keys())) search.delete(key)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists orders with status and links to the detail page", async () => {
    stubServer()
    renderPage()

    const link = await screen.findByRole("link", { name: "ORD-7F3K9QX2" })
    expect(link).toHaveAttribute("href", "/admin/orders/o1")
    const row = link.closest("tr") as HTMLElement
    expect(within(row).getByText("Return requested")).toBeInTheDocument()
    expect(within(row).getByText("Paid")).toBeInTheDocument()
    expect(within(row).getByText("an@example.com")).toBeInTheDocument()
    // no email falls back to the recipient name
    const second = screen.getByRole("link", { name: "ORD-ABC12345" }).closest("tr") as HTMLElement
    expect(within(second).getByText("Binh Tran")).toBeInTheDocument()
  })

  it("filters by status through the URL", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByRole("link", { name: "ORD-7F3K9QX2" })
    fireEvent.change(screen.getByLabelText("Status"), {
      target: { value: "RETURN_REQUESTED" },
    })

    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/admin/orders?status=RETURN_REQUESTED"),
    )
    expect(mock).toHaveBeenCalled()
  })

  it("searches by order number or email on submit", async () => {
    stubServer()
    renderPage()

    await screen.findByRole("link", { name: "ORD-7F3K9QX2" })
    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: " an@example.com " },
    })
    fireEvent.click(screen.getByRole("button", { name: "Search" }))

    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/admin/orders?q=an%40example.com"),
    )
    // the hint explains the "@" rule rather than hiding it
    expect(screen.getByText(/by customer email when the search contains/)).toBeInTheDocument()
  })

  it("reads the status filter from the URL for dashboard deep links", async () => {
    search.set("status", "RETURN_REQUESTED")
    const mock = stubServer()
    renderPage()

    await screen.findByRole("link", { name: "ORD-7F3K9QX2" })
    expect(mock.mock.calls[0][0]).toContain("status=RETURN_REQUESTED")
  })

  it("distinguishes an empty filter result from an empty store", async () => {
    search.set("status", "SHIPPED")
    stubServer({
      list: jsonResponse({
        data: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        message: "Success",
      }),
    })
    renderPage()

    expect(await screen.findByText("No orders match these filters.")).toBeInTheDocument()
  })

  it("offers a retry when the list fails", async () => {
    stubServer({
      list: jsonResponse({ timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" }, 500),
    })
    renderPage()

    expect(await screen.findByText("We could not load the orders")).toBeInTheDocument()
  })
})
