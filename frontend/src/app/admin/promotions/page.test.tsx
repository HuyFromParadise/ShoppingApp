import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import type { AdminPromotion } from "@/types/p1"
import AdminPromotionsPage from "./page"

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/promotions",
}))

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

function promotion(overrides: Partial<AdminPromotion> = {}): AdminPromotion {
  return {
    id: "pr1",
    code: "SAVE10",
    name: "Autumn ten",
    description: null,
    type: "PERCENT",
    value: 10,
    minOrderMinor: 500000,
    maxDiscountMinor: 100000,
    startsAt: null,
    endsAt: null,
    active: true,
    ...overrides,
  }
}

function stubServer(options: { list?: Response; post?: Response; patch?: Response } = {}) {
  const mock = vi.fn((_url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET"
    if (method === "POST") {
      return Promise.resolve(
        options.post ?? jsonResponse({ data: promotion(), message: "Success" }, 201),
      )
    }
    if (method === "PATCH") {
      return Promise.resolve(
        options.patch ??
          jsonResponse({ data: promotion({ active: false }), message: "Success" }),
      )
    }
    return Promise.resolve(
      options.list ?? jsonResponse({ data: [promotion()], message: "Success" }),
    )
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
        <AdminPromotionsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("admin promotions page", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists codes with their discount and conditions", async () => {
    stubServer()
    renderPage()

    const table = await screen.findByRole("table", { name: "Promotions" })
    await waitFor(() => expect(within(table).getByText("SAVE10")).toBeInTheDocument())
    expect(within(table).getByText("10% off")).toBeInTheDocument()
    expect(within(table).getByText(/Min 500\.000/)).toBeInTheDocument()
    expect(within(table).getByText(/capped 100\.000/)).toBeInTheDocument()
    expect(within(table).getByText("Active")).toBeInTheDocument()
    // there is no delete control, only deactivation
    expect(within(table).queryByText("Delete")).not.toBeInTheDocument()
  })

  it("creates a percentage code with an upper-cased code", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("SAVE10")
    fireEvent.click(screen.getByRole("button", { name: "New promotion" }))
    fireEvent.change(screen.getByLabelText(/Code/), { target: { value: "winter20" } })
    fireEvent.change(screen.getByLabelText(/Percentage/), { target: { value: "20" } })
    fireEvent.click(screen.getByRole("button", { name: "Create promotion" }))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "POST")
      expect(call).toBeDefined()
      expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({
        code: "WINTER20",
        type: "PERCENT",
        value: 20,
        active: true,
      })
    })
    expect(await screen.findByText("Promotion created")).toBeInTheDocument()
  })

  it("blocks a percentage over 100 before calling the server", async () => {
    const mock = stubServer()
    renderPage()

    await screen.findByText("SAVE10")
    fireEvent.click(screen.getByRole("button", { name: "New promotion" }))
    fireEvent.change(screen.getByLabelText(/Code/), { target: { value: "TOOBIG" } })
    fireEvent.change(screen.getByLabelText(/Percentage/), { target: { value: "150" } })
    fireEvent.click(screen.getByRole("button", { name: "Create promotion" }))

    expect(
      await screen.findByText("A percentage must be between 1 and 100."),
    ).toBeInTheDocument()
    expect(mock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("shows a duplicate-code refusal from the server", async () => {
    stubServer({
      post: jsonResponse(
        {
          timestamp: "x",
          status: 400,
          code: "VALIDATION_ERROR",
          message: "Request validation failed",
          fieldErrors: { code: "Already in use" },
        },
        400,
      ),
    })
    renderPage()

    await screen.findByText("SAVE10")
    fireEvent.click(screen.getByRole("button", { name: "New promotion" }))
    fireEvent.change(screen.getByLabelText(/Code/), { target: { value: "SAVE10" } })
    fireEvent.change(screen.getByLabelText(/Percentage/), { target: { value: "10" } })
    fireEvent.click(screen.getByRole("button", { name: "Create promotion" }))

    expect(await screen.findByText("Already in use")).toBeInTheDocument()
  })

  it("deactivates a code rather than deleting it", async () => {
    const mock = stubServer()
    renderPage()

    const table = await screen.findByRole("table", { name: "Promotions" })
    await waitFor(() => expect(within(table).getByText("Deactivate")).toBeInTheDocument())
    fireEvent.click(within(table).getByText("Deactivate"))

    await waitFor(() => {
      const call = mock.mock.calls.find(([, init]) => init?.method === "PATCH")
      expect(call).toBeDefined()
      expect(String(call?.[0])).toContain("/admin/promotions/pr1")
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ active: false })
    })
    expect(await screen.findByText("Promotion deactivated")).toBeInTheDocument()
  })

  it("offers a retry when the list fails", async () => {
    stubServer({
      list: jsonResponse(
        { timestamp: "x", status: 500, code: "INTERNAL_ERROR", message: "boom" },
        500,
      ),
    })
    renderPage()
    expect(await screen.findByText("We could not load the promotions")).toBeInTheDocument()
  })
})
