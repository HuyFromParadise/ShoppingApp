"use client"

import { cn } from "@/lib/utils/cn"

export interface QuantityStepperProps {
  value: number
  /** Upper bound, usually min(per-line cap, stock). */
  max: number
  min?: number
  onChange: (next: number) => void
  disabled?: boolean
  label?: string
}

const stepClass =
  "inline-flex h-9 w-9 items-center justify-center text-base text-ink transition-colors duration-150 hover:bg-[#f0ede8] disabled:cursor-not-allowed disabled:text-muted disabled:hover:bg-transparent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"

/**
 * Bounded quantity control. The bounds mirror the backend rule (1–10 per line,
 * ≤ stock) so the customer cannot submit a request that is guaranteed to fail.
 */
export function QuantityStepper({
  value,
  max,
  min = 1,
  onChange,
  disabled = false,
  label = "Quantity",
}: QuantityStepperProps) {
  const upper = Math.max(min, max)

  return (
    <div
      className="inline-flex items-center divide-x divide-line rounded border border-line bg-surface"
      role="group"
      aria-label={label}
    >
      <button
        type="button"
        aria-label="Decrease quantity"
        disabled={disabled || value <= min}
        onClick={() => onChange(value - 1)}
        className={stepClass}
      >
        <span aria-hidden="true">−</span>
      </button>
      <span
        aria-live="polite"
        className={cn(
          "inline-flex h-9 min-w-10 items-center justify-center px-2 text-sm tabular-nums text-ink",
          disabled && "text-muted",
        )}
      >
        {value}
      </span>
      <button
        type="button"
        aria-label="Increase quantity"
        disabled={disabled || value >= upper}
        onClick={() => onChange(value + 1)}
        className={stepClass}
      >
        <span aria-hidden="true">+</span>
      </button>
    </div>
  )
}
