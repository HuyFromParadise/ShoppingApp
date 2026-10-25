import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { API_BASE_URL } from "@/lib/api/client"
import { tokenStore } from "@/lib/api/token"
import {
  cancelOrder,
  confirmMockPayment,
  createOrder,
  createQuote,
  getOrder,
  listOrders,
  listPayments,
  requestOrderReturn,
  startMockPayment,
} from "./api"

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })
}

type FetchCall = [input: string, init?: RequestInit]

interface FetchMock {
  (input: string, init?: RequestInit): Promise<Response>
  mock: { calls: FetchCall[] }
  mockResolvedValueOnce(value: Response): FetchMock
}

function stubFetch(...responses: Response[]): FetchMock {
  const mock = vi.fn() as unknown as FetchMock
  for (const response of responses) mock.mockResolvedValueOnce(response)
  vi.stubGlobal("fetch", mock)
  return mock
}

function headers(call: FetchCall): Record<string, string> {
  return (call[1]?.headers ?? {}) as Record<string, string>
}

const orderPayload = {
  data: {
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
    shippingAddress: {},
    customerNote: null,
    placedAt: "2026-10-25T00:00:00Z",
    items: [],
  },
  message: "Success",
}

describe("orders api", () => {
  beforeEach(() => {
    tokenStore.set("access-token")
  })

  afterEach(() => {
    tokenStore.clear()
    vi.unstubAllGlobals()
  })

  it("requests a quote with only the two server inputs", async () => {
    const mock = stubFetch(
      jsonResponse({
        data: {
          currency: "VND",
          itemCount: 2,
          subtotalMinor: 998000,
          discountTotalMinor: 0,
          shippingTotalMinor: 30000,
          grandTotalMinor: 1028000,
          shippingMethodCode: "STANDARD",
          shippingMethodName: "Standard delivery",
        },
        message: "Success",
      }),
    )

    const quote = await createQuote({ addressId: "a1", shippingMethod: "STANDARD" })

    expect(quote.grandTotalMinor).toBe(1028000)
    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/checkout/quote`)
    expect(init?.method).toBe("POST")
    expect(JSON.parse(String(init?.body))).toEqual({
      addressId: "a1",
      shippingMethod: "STANDARD",
    })
    expect(headers(mock.mock.calls[0]).Authorization).toBe("Bearer access-token")
  })

  it("places an order with the idempotency key and no amount", async () => {
    const mock = stubFetch(jsonResponse(orderPayload, 201))

    const order = await createOrder(
      { addressId: "a1", shippingMethod: "EXPRESS", customerNote: "Ring the bell" },
      "checkout-key-1",
    )

    expect(order.orderNumber).toBe("ORD-7F3K9QX2")
    const [url, init] = mock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/orders`)
    expect(init?.method).toBe("POST")
    expect(headers(mock.mock.calls[0])["Idempotency-Key"]).toBe("checkout-key-1")
    const body = JSON.parse(String(init?.body)) as Record<string, unknown>
    expect(body).toEqual({
      addressId: "a1",
      shippingMethod: "EXPRESS",
      customerNote: "Ring the bell",
    })
    expect(body).not.toHaveProperty("grandTotalMinor")
  })

  it("reads a page of orders", async () => {
    const mock = stubFetch(
      jsonResponse({
        data: [{ id: "o1", orderNumber: "ORD-1", status: "CONFIRMED" }],
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        message: "Success",
      }),
    )

    const page = await listOrders(1, 20)

    expect(page.items).toHaveLength(1)
    expect(page.totalPages).toBe(2)
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/orders?page=1&size=20`)
  })

  it("reads one order", async () => {
    const mock = stubFetch(jsonResponse(orderPayload))

    await getOrder("o1")

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/orders/o1`)
    expect(mock.mock.calls[0][1]?.method).toBe("GET")
  })

  it("cancels and requests a return with POST", async () => {
    const mock = stubFetch(jsonResponse(orderPayload), jsonResponse(orderPayload))

    await cancelOrder("o1")
    await requestOrderReturn("o1")

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/orders/o1/cancel`)
    expect(mock.mock.calls[0][1]?.method).toBe("POST")
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/orders/o1/return`)
    expect(mock.mock.calls[1][1]?.method).toBe("POST")
  })

  it("starts and confirms the mock payment", async () => {
    const mock = stubFetch(
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
      jsonResponse({
        data: {
          orderId: "o1",
          orderNumber: "ORD-1",
          orderStatus: "CONFIRMED",
          paymentStatus: "PAID",
          provider: "MOCK",
          paid: true,
        },
        message: "Success",
      }),
    )

    await startMockPayment("o1")
    const result = await confirmMockPayment("p1", false)

    expect(result.paid).toBe(true)
    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/payments/o1/mock/create`)
    expect(mock.mock.calls[1][0]).toBe(`${API_BASE_URL}/payments/mock/p1/confirm?approve=false`)
    expect(mock.mock.calls[1][1]?.method).toBe("POST")
  })

  it("lists the payment attempts of an order", async () => {
    const mock = stubFetch(jsonResponse({ data: [], message: "Success" }))

    await listPayments("o1")

    expect(mock.mock.calls[0][0]).toBe(`${API_BASE_URL}/payments/o1`)
  })
})
