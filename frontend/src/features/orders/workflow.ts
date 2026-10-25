import type {
  CreateOrderInput,
  Order,
  OrderDetail,
  OrderSummary,
} from "@/types/orders"

export type { OrderStatusChange } from "@/types/orders"

/**
 * Pure order workflow rules. They mirror the backend `OrderStateMachine`
 * (DESIGN_REFERENCE §7) so the UI only offers changes the server will accept.
 * The server stays authoritative: hiding a button is never the check.
 */

/** Backend requirement: `[A-Za-z0-9_-]{8,80}`. */
const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9_-]{8,80}$/

export type OrderLifecycleStep = "placed" | "processing" | "shipped" | "delivered"

const LIFECYCLE_STEPS: readonly OrderLifecycleStep[] = [
  "placed",
  "processing",
  "shipped",
  "delivered",
]

const CANCELABLE = new Set(["PENDING_PAYMENT", "CONFIRMED"])
const RETURNABLE = new Set(["SHIPPED", "DELIVERED"])
const AWAITING_PAYMENT = new Set(["UNPAID", "PENDING", "FAILED"])

export function isCancellable(order: Pick<Order, "status">): boolean {
  return CANCELABLE.has(order.status)
}

export function isReturnable(order: Pick<Order, "status">): boolean {
  return RETURNABLE.has(order.status)
}

/** An order still waiting for money, so an attempt can be started or retried. */
export function isAwaitingPayment(
  order: Pick<Order, "status" | "paymentStatus">,
): boolean {
  return order.status === "PENDING_PAYMENT" && AWAITING_PAYMENT.has(order.paymentStatus)
}

export function isPaid(order: Pick<Order, "paymentStatus">): boolean {
  return order.paymentStatus === "PAID"
}

/**
 * Where the fulfillment timeline stands. `CANCELLED` and the return states are
 * outside the happy path and collapse to the step they left off at.
 */
export function lifecycleIndex(order: Pick<Order, "status">): number {
  switch (order.status) {
    case "PENDING_PAYMENT":
    case "CONFIRMED":
      return 0
    case "PROCESSING":
      return 1
    case "SHIPPED":
    case "RETURN_REQUESTED":
      return 2
    case "DELIVERED":
    case "RETURNED":
      return 3
    default:
      return -1
  }
}

export function lifecycleSteps(): readonly OrderLifecycleStep[] {
  return LIFECYCLE_STEPS
}

/** Human labels for every backend status value, in the storefront's voice. */
export const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: "Awaiting payment",
  CONFIRMED: "Confirmed",
  PROCESSING: "Processing",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  RETURN_REQUESTED: "Return requested",
  RETURNED: "Returned",
}

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: "Unpaid",
  PENDING: "Payment pending",
  PAID: "Paid",
  FAILED: "Payment failed",
  REFUNDED: "Refunded",
}

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status
}

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status
}

export type BadgeTone = "neutral" | "outline" | "accent" | "success" | "warning"

/** Status colour, using only the editorial palette: ink, muted, accent, success. */
export function orderStatusTone(status: string): BadgeTone {
  switch (status) {
    case "DELIVERED":
    case "RETURNED":
      return "success"
    case "CANCELLED":
      return "accent"
    case "PENDING_PAYMENT":
    case "RETURN_REQUESTED":
      return "warning"
    default:
      return "outline"
  }
}

export function paymentStatusTone(status: string): BadgeTone {
  switch (status) {
    case "PAID":
      return "success"
    case "FAILED":
      return "accent"
    case "REFUNDED":
      return "neutral"
    default:
      return "warning"
  }
}

/**
 * The `Idempotency-Key` for one checkout attempt. `crypto.randomUUID` needs a
 * secure context (https or localhost); when testing over plain http on a LAN IP
 * it is absent, so fall back to `crypto.getRandomValues`, which has no such
 * restriction, before the last-resort timestamp form.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined") {
    if (typeof crypto.randomUUID === "function") {
      return crypto.randomUUID()
    }
    if (typeof crypto.getRandomValues === "function") {
      const bytes = new Uint8Array(16)
      crypto.getRandomValues(bytes)
      return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("")
    }
  }
  return `order-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

export function isValidIdempotencyKey(key: string): boolean {
  return IDEMPOTENCY_KEY_PATTERN.test(key)
}

/**
 * The key for a checkout attempt: reused while the request body is unchanged,
 * replaced when the body changes, because the backend answers a reused key with
 * a different body with `409 IDEMPOTENCY_KEY_REUSED`.
 *
 * <p>A successful placement drops the key (the attempt is spent), so the next
 * order gets a fresh one.
 */
export class CheckoutAttempt {
  private current: { signature: string; key: string } | null = null

  constructor(private readonly generate: () => string = newIdempotencyKey) {}

  keyFor(input: CreateOrderInput): string {
    const signature = orderRequestSignature(input)
    if (this.current?.signature !== signature) {
      this.current = { signature, key: this.generate() }
    }
    return this.current.key
  }

  /** The attempt will never be retried: the next call starts a new one. */
  reset(): void {
    this.current = null
  }

  get key(): string | null {
    return this.current?.key ?? null
  }
}

/**
 * The hash the backend deduplicates on is over the request DTO only. It is
 * repeated here so a failed attempt can be identified without trusting a local
 * copy of the totals.
 */
export function orderRequestSignature(input: CreateOrderInput): string {
  return JSON.stringify({
    addressId: input.addressId,
    shippingMethod: input.shippingMethod,
    promotionCode: undefined,
    customerNote: input.customerNote?.trim() || undefined,
  })
}

function field(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null
}

/** Address lines as the order snapshot stores them, ready for an `<address>`. */
export function formatOrderAddress(address: Order["shippingAddress"]): string[] {
  const lines: string[] = []
  const line1 = field(address.addressLine1)
  const line2 = field(address.addressLine2)
  if (line1) lines.push(line2 ? `${line1}, ${line2}` : line1)

  const locality = [field(address.ward), field(address.district)].filter(
    (part): part is string => part !== null,
  )
  const city = field(address.cityOrProvince)
  const postal = field(address.postalCode)
  const cityLine = [
    locality.join(", "),
    city ?? "",
    postal ? postal : "",
  ]
    .filter((part) => part.length > 0)
    .join(", ")
  if (cityLine) lines.push(cityLine)
  return lines
}

/** Attribute line for an order item (`White / M`, or the SKU when neither exists). */
export function orderItemAttributes(item: Order["items"][number]): string {
  const attributes = [item.color, item.size].filter(
    (value): value is string => typeof value === "string" && value.length > 0,
  )
  return attributes.length > 0 ? attributes.join(" / ") : (item.sku ?? "")
}

/** Next page links for the order history. The history has no filter controls. */
export function orderHistoryHref(page: number): string {
  return page > 0 ? `/account/orders?page=${page}` : "/account/orders"
}

export type { Order, OrderDetail, OrderSummary }
