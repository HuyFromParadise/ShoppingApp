import { describe, expect, it, vi } from "vitest"
import {
  CheckoutAttempt,
  formatOrderAddress,
  isAwaitingPayment,
  isCancellable,
  isPaid,
  isReturnable,
  isValidIdempotencyKey,
  lifecycleIndex,
  newIdempotencyKey,
  orderHistoryHref,
  orderItemAttributes,
  orderStatusLabel,
  orderStatusTone,
  paymentStatusLabel,
  paymentStatusTone,
} from "./workflow"

describe("order workflow rules", () => {
  it("only offers cancellation while the server allows it", () => {
    expect(isCancellable({ status: "PENDING_PAYMENT" })).toBe(true)
    expect(isCancellable({ status: "CONFIRMED" })).toBe(true)
    for (const status of ["PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"]) {
      expect(isCancellable({ status })).toBe(false)
    }
  })

  it("only offers a return once the order shipped", () => {
    expect(isReturnable({ status: "SHIPPED" })).toBe(true)
    expect(isReturnable({ status: "DELIVERED" })).toBe(true)
    expect(isReturnable({ status: "CONFIRMED" })).toBe(false)
    expect(isReturnable({ status: "RETURN_REQUESTED" })).toBe(false)
  })

  it("treats an unpaid or failed pending order as payable", () => {
    expect(isAwaitingPayment({ status: "PENDING_PAYMENT", paymentStatus: "UNPAID" })).toBe(true)
    expect(isAwaitingPayment({ status: "PENDING_PAYMENT", paymentStatus: "FAILED" })).toBe(true)
    expect(isAwaitingPayment({ status: "PENDING_PAYMENT", paymentStatus: "PAID" })).toBe(false)
    expect(isAwaitingPayment({ status: "CANCELLED", paymentStatus: "FAILED" })).toBe(false)
    expect(isPaid({ paymentStatus: "PAID" })).toBe(true)
    expect(isPaid({ paymentStatus: "REFUNDED" })).toBe(false)
  })

  it("maps every status onto the delivery timeline", () => {
    expect(lifecycleIndex({ status: "PENDING_PAYMENT" })).toBe(0)
    expect(lifecycleIndex({ status: "CONFIRMED" })).toBe(0)
    expect(lifecycleIndex({ status: "PROCESSING" })).toBe(1)
    expect(lifecycleIndex({ status: "SHIPPED" })).toBe(2)
    expect(lifecycleIndex({ status: "DELIVERED" })).toBe(3)
    expect(lifecycleIndex({ status: "CANCELLED" })).toBe(-1)
  })

  it("labels unknown statuses with the raw value rather than hiding them", () => {
    expect(orderStatusLabel("PROCESSING")).toBe("Processing")
    expect(orderStatusLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
    expect(paymentStatusLabel("FAILED")).toBe("Payment failed")
    expect(orderStatusTone("CANCELLED")).toBe("accent")
    expect(orderStatusTone("DELIVERED")).toBe("success")
    expect(paymentStatusTone("PAID")).toBe("success")
    expect(paymentStatusTone("REFUNDED")).toBe("neutral")
  })

  it("generates an idempotency key the backend accepts", () => {
    for (let i = 0; i < 5; i += 1) {
      const key = newIdempotencyKey()
      expect(key.length).toBeGreaterThanOrEqual(8)
      expect(key.length).toBeLessThanOrEqual(80)
      expect(isValidIdempotencyKey(key)).toBe(true)
    }
    expect(isValidIdempotencyKey("short")).toBe(false)
    expect(isValidIdempotencyKey("has spaces here")).toBe(false)
  })

  it("falls back to a random key without crypto.randomUUID", () => {
    vi.stubGlobal("crypto", {
      getRandomValues: (bytes: Uint8Array) => bytes.fill(7),
    })
    try {
      expect(newIdempotencyKey()).toBe("07070707070707070707070707070707")
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it("reuses the key for the same request and replaces it when the request changes", () => {
    let issued = 0
    const attempt = new CheckoutAttempt(() => `key-${(issued += 1)}`)
    const first = { addressId: "a1", shippingMethod: "STANDARD" as const }

    expect(attempt.keyFor(first)).toBe("key-1")
    // a retry of the same attempt keeps the key, so the server can replay
    expect(attempt.keyFor({ ...first })).toBe("key-1")
    expect(attempt.keyFor({ ...first, shippingMethod: "EXPRESS" })).toBe("key-2")
    expect(attempt.keyFor({ ...first, shippingMethod: "EXPRESS", customerNote: "x" })).toBe(
      "key-3",
    )

    attempt.reset()
    expect(attempt.key).toBeNull()
    expect(attempt.keyFor(first)).toBe("key-4")
  })

  it("formats the address snapshot like the order page shows it", () => {
    expect(
      formatOrderAddress({
        recipientName: "An Nguyen",
        phone: "0901234567",
        addressLine1: "12 Le Loi",
        addressLine2: "Floor 3",
        ward: "Ben Thanh",
        district: "District 1",
        cityOrProvince: "Ho Chi Minh City",
        postalCode: "700000",
      }),
    ).toEqual(["12 Le Loi, Floor 3", "Ben Thanh, District 1, Ho Chi Minh City, 700000"])

    // a sparse snapshot never renders "null" or stray commas
    expect(
      formatOrderAddress({
        recipientName: null,
        phone: null,
        addressLine1: "1 Main St",
        addressLine2: null,
        ward: null,
        district: null,
        cityOrProvince: "Ha Noi",
        postalCode: null,
      }),
    ).toEqual(["1 Main St", "Ha Noi"])
  })

  it("falls back to the SKU when the item has no colour or size", () => {
    expect(
      orderItemAttributes({
        id: "i1",
        variantId: null,
        productName: "Tote",
        sku: "TOTE-01",
        size: null,
        color: null,
        imageUrl: null,
        unitPriceMinor: 1,
        quantity: 1,
        lineTotalMinor: 1,
      }),
    ).toBe("TOTE-01")
  })

  it("keeps page 0 out of the URL", () => {
    expect(orderHistoryHref(0)).toBe("/account/orders")
    expect(orderHistoryHref(2)).toBe("/account/orders?page=2")
  })
})
