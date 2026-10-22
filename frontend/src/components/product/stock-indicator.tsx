import { cn } from "@/lib/utils/cn"

export interface StockIndicatorProps {
  inStock: boolean
  quantity?: number
  className?: string
}

/** Stock is communicated with both a dot and text, never colour alone. */
export function StockIndicator({
  inStock,
  quantity,
  className,
}: StockIndicatorProps) {
  const lowStock =
    inStock && typeof quantity === "number" && quantity > 0 && quantity <= 3
  const label = !inStock
    ? "Out of stock"
    : lowStock
      ? `Only ${quantity} left`
      : "In stock"

  return (
    <p
      className={cn(
        "inline-flex items-center gap-2 text-sm",
        inStock ? "text-success" : "text-muted",
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          "h-1.5 w-1.5 rounded-full",
          inStock ? "bg-success" : "bg-muted",
        )}
      />
      {label}
    </p>
  )
}
