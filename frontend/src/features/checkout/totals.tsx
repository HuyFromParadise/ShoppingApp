import { PriceDisplay } from "@/components/ui/price-display"
import type { CheckoutQuote } from "@/types/orders"

/**
 * The totals block shared by the quote preview, the review step and the order
 * pages. Every number comes from the server; nothing is summed here.
 */
export function TotalsBreakdown({
  subtotalMinor,
  discountTotalMinor,
  shippingTotalMinor,
  grandTotalMinor,
  currency,
  shippingMethodName,
  itemCount,
}: {
  subtotalMinor: number
  discountTotalMinor: number
  shippingTotalMinor: number
  grandTotalMinor: number
  currency: string
  shippingMethodName?: string | null
  itemCount?: number
}) {
  return (
    <dl className="space-y-2 text-sm">
      {typeof itemCount === "number" ? (
        <div className="flex justify-between gap-4">
          <dt className="text-muted">Items</dt>
          <dd className="tabular-nums text-ink">{itemCount}</dd>
        </div>
      ) : null}
      <div className="flex justify-between gap-4">
        <dt className="text-muted">Subtotal</dt>
        <dd>
          <PriceDisplay amountMinor={subtotalMinor} currency={currency} />
        </dd>
      </div>
      {discountTotalMinor > 0 ? (
        <div className="flex justify-between gap-4">
          <dt className="text-muted">Discount</dt>
          <dd className="text-success">
            <PriceDisplay amountMinor={discountTotalMinor} currency={currency} />
          </dd>
        </div>
      ) : null}
      <div className="flex justify-between gap-4">
        <dt className="text-muted">
          Shipping
          {shippingMethodName ? (
            <span className="block text-xs">{shippingMethodName}</span>
          ) : null}
        </dt>
        <dd>
          <PriceDisplay amountMinor={shippingTotalMinor} currency={currency} />
        </dd>
      </div>
      <div className="flex justify-between gap-4 border-t border-line pt-2">
        <dt className="font-medium text-ink">Total</dt>
        <dd>
          <PriceDisplay
            amountMinor={grandTotalMinor}
            currency={currency}
            size="lg"
          />
        </dd>
      </div>
    </dl>
  )
}

/** The quote totals, ready for the checkout summary. */
export function QuoteTotals({ quote }: { quote: CheckoutQuote }) {
  return (
    <TotalsBreakdown
      subtotalMinor={quote.subtotalMinor}
      discountTotalMinor={quote.discountTotalMinor}
      shippingTotalMinor={quote.shippingTotalMinor}
      grandTotalMinor={quote.grandTotalMinor}
      currency={quote.currency}
      shippingMethodName={quote.shippingMethodName}
      itemCount={quote.itemCount}
    />
  )
}
