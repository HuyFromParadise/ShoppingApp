/** Shared checkout and order DTOs mirroring the backend responses. */

/** Shipping option codes the server accepts (`app.checkout.shipping`). */
export const SHIPPING_METHOD_CODES = ["STANDARD", "EXPRESS"] as const

export type ShippingMethodCode = (typeof SHIPPING_METHOD_CODES)[number]

/** One selectable shipping option. Fees are server-side; labels are UI-side. */
export interface ShippingMethodOption {
  code: ShippingMethodCode
  label: string
  description: string
}

/**
 * The shipping options the checkout UI offers. The order and quote endpoints
 * reject anything else, so the list must stay in sync with the backend config.
 */
export const SHIPPING_METHODS: readonly ShippingMethodOption[] = [
  {
    code: "STANDARD",
    label: "Standard delivery",
    description: "3–5 business days",
  },
  {
    code: "EXPRESS",
    label: "Express delivery",
    description: "1–2 business days",
  },
]

export function shippingMethodLabel(code: string | null | undefined): string {
  return SHIPPING_METHODS.find((method) => method.code === code)?.label ?? code ?? "—"
}

export interface QuoteInput {
  addressId: string
  shippingMethod: ShippingMethodCode
}

/** Server-computed totals. The client never adds these up itself. */
export interface CheckoutQuote {
  currency: string
  itemCount: number
  subtotalMinor: number
  discountTotalMinor: number
  shippingTotalMinor: number
  grandTotalMinor: number
  shippingMethodCode: string
  shippingMethodName: string
}

export interface OrderAddress {
  recipientName: string | null
  phone: string | null
  addressLine1: string | null
  addressLine2: string | null
  ward: string | null
  district: string | null
  cityOrProvince: string | null
  postalCode: string | null
}

export interface OrderItem {
  id: string
  variantId: string | null
  productName: string
  sku: string | null
  size: string | null
  color: string | null
  imageUrl: string | null
  unitPriceMinor: number
  quantity: number
  lineTotalMinor: number
}

/** The order returned right after checkout. */
export interface Order {
  id: string
  orderNumber: string
  status: string
  paymentStatus: string
  currency: string
  subtotalMinor: number
  discountTotalMinor: number
  shippingTotalMinor: number
  grandTotalMinor: number
  shippingMethodCode: string | null
  shippingMethodName: string | null
  shippingAddress: OrderAddress
  customerNote: string | null
  placedAt: string | null
  items: OrderItem[]
}

export interface OrderStatusChange {
  fromStatus: string | null
  toStatus: string
  note: string | null
  changedAt: string | null
}

/** The order detail page shape: the order plus its status history. */
export interface OrderDetail extends Order {
  statusHistory: OrderStatusChange[]
}

/** One row of the customer's order history. */
export interface OrderSummary {
  id: string
  orderNumber: string
  status: string
  paymentStatus: string
  currency: string
  grandTotalMinor: number
  itemCount: number
  placedAt: string | null
}

export interface CreateOrderInput extends QuoteInput {
  customerNote?: string
}

/** Backend limit for the delivery note (`orders.customer_note` is 500 chars). */
export const MAX_CUSTOMER_NOTE_LENGTH = 500

/**
 * What the mock payment flow returns. `mock` is always true when the backend is
 * running its development adapter, so the UI can say so out loud.
 */
export interface PaymentInitiation {
  paymentId: string
  orderId: string
  provider: string
  status: string
  providerTransactionId: string | null
  paymentUrl: string | null
  mock: boolean
  createdAt: string | null
}

export interface PaymentStatusView {
  id: string
  provider: string
  status: string
  amountMinor: number | null
  currency: string | null
  providerTransactionId: string | null
  createdAt: string | null
}

export interface PaymentResult {
  orderId: string
  orderNumber: string
  orderStatus: string
  paymentStatus: string
  provider: string
  paid: boolean
  settledAt?: string | null
}
