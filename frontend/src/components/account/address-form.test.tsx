import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { Mock } from "vitest"
import { AddressForm } from "./address-form"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

const addressPayload = {
  data: {
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
  message: "Success",
}

function renderForm(onSaved = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <AddressForm onSaved={onSaved} />
    </QueryClientProvider>,
  )
  return onSaved
}

function fillAddress() {
  fireEvent.change(screen.getByLabelText(/recipient name/i), {
    target: { value: "An Nguyen" },
  })
  fireEvent.change(screen.getByLabelText(/phone/i), {
    target: { value: "0901234567" },
  })
  fireEvent.change(screen.getByLabelText(/address line 1/i), {
    target: { value: "12 Le Loi" },
  })
  fireEvent.change(screen.getByLabelText(/city or province/i), {
    target: { value: "Ho Chi Minh City" },
  })
}

describe("AddressForm", () => {
  let fetchMock: Mock<[string, RequestInit?], Promise<Response>>

  beforeEach(() => {
    fetchMock = vi.fn((_url: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(addressPayload, 201)),
    )
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("rejects a missing recipient before calling the API", async () => {
    renderForm()

    fireEvent.change(screen.getByLabelText(/address line 1/i), {
      target: { value: "12 Le Loi" },
    })
    fireEvent.change(screen.getByLabelText(/city or province/i), {
      target: { value: "Ho Chi Minh City" },
    })
    fireEvent.click(screen.getByRole("button", { name: /add address/i }))

    expect(
      await screen.findByText("Recipient name is required"),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("creates an address and reports success", async () => {
    const onSaved = renderForm()

    fillAddress()
    fireEvent.click(screen.getByRole("checkbox", { name: /default address/i }))
    fireEvent.click(screen.getByRole("button", { name: /add address/i }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain("/account/addresses")
    expect(init?.method).toBe("POST")
    const body = JSON.parse(String(init?.body))
    expect(body.recipientName).toBe("An Nguyen")
    expect(body.cityOrProvince).toBe("Ho Chi Minh City")
    expect(body.isDefault).toBe(true)
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it("shows the backend message when the save fails", async () => {
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve(
        jsonResponse(
          {
            timestamp: "2026-10-22T00:00:00Z",
            status: 400,
            code: "VALIDATION_ERROR",
            message: "Request validation failed",
            fieldErrors: { recipientName: "Recipient name is too long" },
            path: "/api/v1/account/addresses",
          },
          400,
        ),
      ),
    )
    renderForm()

    fillAddress()
    fireEvent.click(screen.getByRole("button", { name: /add address/i }))

    expect(
      await screen.findByText("Recipient name is too long"),
    ).toBeInTheDocument()
  })
})
