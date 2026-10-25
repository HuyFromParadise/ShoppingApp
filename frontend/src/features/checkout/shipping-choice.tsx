"use client"

import { ApiError } from "@/lib/api/client"
import { SHIPPING_METHODS, type ShippingMethodCode } from "@/types/orders"

export interface ShippingChoiceProps {
  value: ShippingMethodCode
  onChange: (code: ShippingMethodCode) => void
  disabled?: boolean
}

/**
 * Shipping options. The UI only picks a code; the fee is fetched from the quote
 * endpoint, so a fee change in `application.yml` needs no frontend change.
 */
export function ShippingChoice({ value, onChange, disabled }: ShippingChoiceProps) {
  return (
    <fieldset disabled={disabled} className="space-y-3">
      <legend className="text-xs font-medium uppercase tracking-label text-muted">
        Delivery
      </legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {SHIPPING_METHODS.map((method) => (
          <label
            key={method.code}
            className="flex cursor-pointer items-start gap-3 rounded border border-line bg-surface p-4 text-sm has-[:checked]:border-ink"
          >
            <input
              type="radio"
              name="shippingMethod"
              value={method.code}
              checked={value === method.code}
              onChange={() => onChange(method.code)}
              className="mt-0.5 accent-[var(--color-ink)]"
            />
            <span>
              <span className="block font-medium text-ink">{method.label}</span>
              <span className="mt-0.5 block text-xs text-muted">
                {method.description}
              </span>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}

/** One server-side reason the quote could not be produced. */
export interface QuoteProblem {
  title: string
  message: string
}

/**
 * Turns a failed quote into the storefront's wording. Out-of-stock and
 * unavailable lines are the two things a customer can act on.
 */
export function describeQuoteError(error: unknown): QuoteProblem {
  if (!(error instanceof ApiError)) {
    return {
      title: "We could not calculate your total",
      message: "Please try again in a moment.",
    }
  }
  switch (error.code) {
    case "OUT_OF_STOCK":
      return {
        title: "An item is no longer available in that quantity",
        message: `${error.message} Adjust your cart and request the total again.`,
      }
    case "CONFLICT":
      return {
        title: "An item is no longer available",
        message: `${error.message} Remove it from your cart to continue.`,
      }
    case "VALIDATION_ERROR":
      return { title: "Check your details", message: error.message }
    case "NOT_FOUND":
      return {
        title: "That address is no longer available",
        message: "Choose another delivery address.",
      }
    case "NETWORK_ERROR":
      return {
        title: "We could not reach the server",
        message: "Check your connection and try again.",
      }
    default:
      return {
        title: "We could not calculate your total",
        message: "Please try again in a moment.",
      }
  }
}
