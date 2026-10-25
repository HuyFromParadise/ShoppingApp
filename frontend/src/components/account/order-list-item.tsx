import Link from "next/link"
import { PriceDisplay } from "@/components/ui/price-display"
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/status-badge"
import { formatDate } from "@/lib/utils/date"
import type { OrderSummary } from "@/types/orders"

/**
 * One row of the order history. The whole row is the link, because the order
 * detail is the only action available from the list.
 */
export function OrderListItem({ order }: { order: OrderSummary }) {
  return (
    <li>
      <Link
        href={`/account/orders/${encodeURIComponent(order.id)}`}
        className="flex flex-wrap items-start justify-between gap-4 py-5 transition-colors duration-150 hover:bg-[#f0ede8] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
      >
        <div className="min-w-0">
          <p className="font-medium text-ink">{order.orderNumber}</p>
          <p className="mt-1 text-xs text-muted">
            {order.placedAt ? formatDate(order.placedAt) : "—"} ·{" "}
            {order.itemCount === 1 ? "1 item" : `${order.itemCount} items`}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <OrderStatusBadge status={order.status} />
            <PaymentStatusBadge status={order.paymentStatus} />
          </div>
        </div>
        <div className="text-right">
          <PriceDisplay
            amountMinor={order.grandTotalMinor}
            currency={order.currency}
          />
          <span className="mt-2 block text-xs text-ink underline underline-offset-4">
            View order
          </span>
        </div>
      </Link>
    </li>
  )
}
