import { cn } from "@/lib/utils/cn"
import { formatMoney } from "@/lib/utils/money"

export interface PriceDisplayProps {
  amountMinor: number
  currency?: string
  compareAtMinor?: number
  className?: string
  size?: "sm" | "md" | "lg"
}

const sizeClasses = {
  sm: { current: "text-sm", compare: "text-xs" },
  md: { current: "text-base", compare: "text-sm" },
  lg: { current: "text-xl", compare: "text-base" },
} as const

/** Renders an authoritative server amount. No arithmetic happens here. */
export function PriceDisplay({
  amountMinor,
  currency = "VND",
  compareAtMinor,
  className,
  size = "md",
}: PriceDisplayProps) {
  const showCompareAt =
    typeof compareAtMinor === "number" && compareAtMinor > amountMinor
  const classes = sizeClasses[size]

  return (
    <span className={cn("inline-flex items-baseline gap-2", className)}>
      <span className={cn("font-medium text-ink", classes.current)}>
        {formatMoney(amountMinor, currency)}
      </span>
      {showCompareAt ? (
        <s className={cn("text-muted", classes.compare)}>
          <span className="sr-only">Giá gốc </span>
          {formatMoney(compareAtMinor, currency)}
        </s>
      ) : null}
    </span>
  )
}
