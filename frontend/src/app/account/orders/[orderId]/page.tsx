"use client"

import * as React from "react"
import Link from "next/link"
import { useParams, useSearchParams } from "next/navigation"
import { ProductImage } from "@/components/product/product-image"
import { buttonVariants } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { TotalsBreakdown } from "@/features/checkout/totals"
import { OrderActions } from "@/features/orders/order-actions"
import { OrderProgress } from "@/features/orders/order-progress"
import { PaymentPanel } from "@/features/orders/payment-panel"
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/status-badge"
import {
  formatOrderAddress,
  orderItemAttributes,
} from "@/features/orders/workflow"
import { useOrder } from "@/features/orders/hooks"
import { shippingMethodLabel } from "@/types/orders"
import { ApiError } from "@/lib/api/client"
import { formatDateTime } from "@/lib/utils/date"

/**
 * Order detail (Journey D): what was ordered, the address snapshot, status
 * history, and the only two actions the customer may take — cancel or request a
 * return. Ownership is enforced by the backend; a foreign id answers 404.
 */
export default function OrderDetailPage() {
  const params = useParams<{ orderId: string }>()
  const orderId = typeof params?.orderId === "string" ? params.orderId : undefined
  return <OrderDetailView orderId={orderId} />
}

function OrderDetailView({ orderId }: { orderId: string | undefined }) {
  const order = useOrder(orderId)
  const search = useSearchParams()
  const justPlaced = search.get("placed") === "1"

  if (!orderId) {
    return <InvalidOrder />
  }

  if (order.isPending) {
    return <Skeleton lines={8} />
  }

  if (order.error) {
    return order.error instanceof ApiError && order.error.status === 404 ? (
      <InvalidOrder />
    ) : (
      <ErrorState
        title="We could not load this order"
        onRetry={() => void order.refetch()}
      />
    )
  }

  if (!order.data) {
    return <InvalidOrder />
  }

  const data = order.data
  const addressLines = formatOrderAddress(data.shippingAddress)

  return (
    <article className="space-y-10" aria-labelledby="order-heading">
      <header>
        <Link
          href="/account/orders"
          className="text-xs text-muted underline underline-offset-4 hover:text-ink"
        >
          ← All orders
        </Link>
        <div className="mt-4 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2
              id="order-heading"
              className="text-lg font-medium tracking-tight text-ink"
            >
              Order {data.orderNumber}
            </h2>
            <p className="mt-1 text-xs text-muted">
              Placed {data.placedAt ? formatDateTime(data.placedAt) : "—"}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <OrderStatusBadge status={data.status} />
            <PaymentStatusBadge status={data.paymentStatus} />
          </div>
        </div>
        {justPlaced ? (
          <p
            role="status"
            className="mt-4 border border-[#c9dfd3] bg-[#eef6f1] px-4 py-3 text-sm text-ink"
          >
            Thank you — your order is placed. Pay it below to confirm the items.
          </p>
        ) : null}
        <OrderProgress status={data.status} />
      </header>

      <section aria-labelledby="payment-heading" className="border-y border-line py-6">
        <h3
          id="payment-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          Payment
        </h3>
        <div className="mt-3">
          <PaymentPanel order={data} />
        </div>
      </section>

      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <section aria-labelledby="items-heading">
          <h3
            id="items-heading"
            className="text-xs font-medium uppercase tracking-label text-muted"
          >
            Items
          </h3>
          <ul className="mt-4 divide-y divide-line border-y border-line">
            {data.items.map((item) => (
              <li key={item.id} className="flex gap-4 py-5">
                <div className="w-16 shrink-0 sm:w-20">
                  <ProductImage
                    src={item.imageUrl}
                    alt={item.productName}
                    className="aspect-[3/4] w-full"
                  />
                </div>
                <div className="flex min-w-0 flex-1 items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="font-medium text-ink">{item.productName}</p>
                    <p className="mt-1 text-xs uppercase tracking-label text-muted">
                      {orderItemAttributes(item)}
                    </p>
                    <p className="mt-1 text-xs text-muted">
                      <PriceDisplay
                        amountMinor={item.unitPriceMinor}
                        currency={data.currency}
                        size="sm"
                      />{" "}
                      × {item.quantity}
                    </p>
                  </div>
                  <PriceDisplay
                    amountMinor={item.lineTotalMinor}
                    currency={data.currency}
                  />
                </div>
              </li>
            ))}
          </ul>
        </section>

        <aside className="space-y-8">
          <section
            aria-labelledby="summary-heading"
            className="border border-line bg-surface p-6"
          >
            <h3
              id="summary-heading"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Summary
            </h3>
            <div className="mt-4">
              <TotalsBreakdown
                subtotalMinor={data.subtotalMinor}
                discountTotalMinor={data.discountTotalMinor}
                shippingTotalMinor={data.shippingTotalMinor}
                grandTotalMinor={data.grandTotalMinor}
                currency={data.currency}
                shippingMethodName={
                  data.shippingMethodName ?? shippingMethodLabel(data.shippingMethodCode)
                }
              />
            </div>
          </section>

          <section aria-labelledby="delivery-heading">
            <h3
              id="delivery-heading"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Delivery
            </h3>
            <address className="mt-3 text-sm not-italic text-muted">
              <span className="block font-medium text-ink">
                {data.shippingAddress.recipientName ?? "—"}
              </span>
              {addressLines.map((line) => (
                <span key={line} className="block">
                  {line}
                </span>
              ))}
              {data.shippingAddress.phone ? (
                <span className="mt-1 block text-xs">{data.shippingAddress.phone}</span>
              ) : null}
            </address>
            {data.customerNote ? (
              <p className="mt-4 border-l-2 border-line pl-3 text-sm text-muted">
                “{data.customerNote}”
              </p>
            ) : null}
          </section>
        </aside>
      </div>

      <section aria-labelledby="history-heading">
        <h3
          id="history-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          History
        </h3>
        <ol className="mt-4 space-y-3 border-l border-line pl-4">
          {data.statusHistory.map((entry, index) => (
            <li key={`${entry.toStatus}-${entry.changedAt ?? index}`} className="text-sm">
              <p className="text-ink">
                {entry.note ?? entry.toStatus}
              </p>
              <p className="text-xs text-muted">
                {entry.changedAt ? formatDateTime(entry.changedAt) : ""}
              </p>
            </li>
          ))}
        </ol>
      </section>

      <footer className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6">
        <OrderActions order={data} />
        <Link href="/shop" className={buttonVariants({ variant: "ghost", size: "sm" })}>
          Continue shopping
        </Link>
      </footer>
    </article>
  )
}

function InvalidOrder() {  return (
    <div className="space-y-4">
      <h2 className="text-lg font-medium tracking-tight text-ink">
        We could not find that order
      </h2>
      <p className="text-sm text-muted">
        The link may be wrong, or the order belongs to another account.
      </p>
      <Link href="/account/orders" className={buttonVariants({ variant: "secondary" })}>
        Back to your orders
      </Link>
    </div>
  )
}
