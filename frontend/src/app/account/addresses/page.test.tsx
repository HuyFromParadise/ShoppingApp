import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { ToastProvider } from "@/components/ui/toast"
import AddressesPage from "./page"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const addresses = [
  {
    id: "a1",
    recipientName: "An Nguyen",
    phone: "0901234567",
    addressLine1: "12 Le Loi",
    addressLine2: null,
    ward: "Ben Thanh",
    district: "District 1",
    cityOrProvince: "Ho Chi Minh City",
    postalCode: "700000",
    isDefault: true,
  },
]

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AddressesPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe("addresses page", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse({ data: addresses, message: "Success" })),
    )
    vi.stubGlobal("fetch", fetchMock)
    vi.spyOn(window, "confirm").mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("lists the addresses of the customer", async () => {
    renderPage()

    expect(await screen.findByText("An Nguyen")).toBeInTheDocument()
    const rendered = document.querySelector("address")
    expect(rendered).toHaveTextContent("12 Le Loi")
    expect(rendered).toHaveTextContent("Ho Chi Minh City")
    expect(screen.getByText("Default")).toBeInTheDocument()
  })

  it("reports a failed delete instead of failing silently", async () => {
    fetchMock
      .mockImplementationOnce(() =>
        Promise.resolve(jsonResponse({ data: addresses, message: "Success" })),
      )
      .mockImplementationOnce(() =>
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

    fireEvent.click(await screen.findByRole("button", { name: "Delete" }))

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "We could not delete this address",
    )
  })
})
