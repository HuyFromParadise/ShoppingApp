import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  CheckoutQuote,
  CreateOrderInput,
  Order,
  OrderDetail,
  OrderSummary,
  PaymentInitiation,
  PaymentResult,
  PaymentStatusView,
  QuoteInput,
} from "@/types/orders"

/**
 * Checkout, order and payment endpoints. All of them are authenticated, so the
 * API client attaches the in-memory access token and refreshes it when needed.
 * No amount is ever sent: the server prices the cart itself.
 */

/** Re-prices the cart for an address and shipping option. It writes nothing. */
export function createQuote(input: QuoteInput): Promise<CheckoutQuote> {
  return api.post<CheckoutQuote>(ENDPOINTS.checkout.quote, input)
}

/**
 * Places the order. The key makes the call retry-safe: repeating it with the
 * same body replays the same order instead of charging twice.
 */
export function createOrder(
  input: CreateOrderInput,
  idempotencyKey: string,
): Promise<Order> {
  return api.post<Order>(ENDPOINTS.orders, input, {
    headers: { "Idempotency-Key": idempotencyKey },
  })
}

export function listOrders(
  page: number,
  size: number,
): Promise<{
  items: OrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}> {
  return api.getPage<OrderSummary>(ENDPOINTS.orders, { query: { page, size } })
}

export function getOrder(orderId: string): Promise<OrderDetail> {
  return api.get<OrderDetail>(ENDPOINTS.order(orderId))
}

export function cancelOrder(orderId: string): Promise<OrderDetail> {
  return api.post<OrderDetail>(ENDPOINTS.orderCancel(orderId))
}

export function requestOrderReturn(orderId: string): Promise<OrderDetail> {
  return api.post<OrderDetail>(ENDPOINTS.orderReturn(orderId))
}

/** Starts the labelled development payment for an order. */
export function startMockPayment(orderId: string): Promise<PaymentInitiation> {
  return api.post<PaymentInitiation>(ENDPOINTS.payments.mockCreate(orderId))
}

/**
 * Settles a mock payment from the confirmation step. Only the development
 * adapter exposes this; a real provider settles through its webhook instead.
 */
export function confirmMockPayment(
  paymentId: string,
  approve: boolean,
): Promise<PaymentResult> {
  return api.post<PaymentResult>(ENDPOINTS.payments.mockConfirm(paymentId), undefined, {
    query: { approve },
  })
}

export function listPayments(orderId: string): Promise<PaymentStatusView[]> {
  return api.get<PaymentStatusView[]>(ENDPOINTS.payments.order(orderId))
}
