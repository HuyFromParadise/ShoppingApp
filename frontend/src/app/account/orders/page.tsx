"use client"

import * as React from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { OrderListItem } from "@/components/account/order-list-item"
import { buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { Skeleton } from "@/components/ui/skeleton"
import { useOrders } from "@/features/orders/hooks"
import { orderHistoryHref } from "@/features/orders/workflow"
import { paginationSchema } from "@/lib/validation"

const PAGE_SIZE = 20

/**
 * Order history (Journey D). The page number lives in the URL so a page can be
 * shared and the back button works; everything else comes from the server.
 */
export default function OrdersPage() {
  return (
    <React.Suspense fallback={<Skeleton lines={5} />}>
      <OrdersView />
    </React.Suspense>
  )
}

function OrdersView() {
  const params = useSearchParams()
  const parsed = paginationSchema.safeParse({
    page: params.get("page") ?? undefined,
    size: PAGE_SIZE,
  })
  const page = parsed.success ? parsed.data.page : 0
  const orders = useOrders(page, PAGE_SIZE)

  if (orders.isPending) {
    return <Skeleton lines={5} />
  }

  if (orders.error) {
    return (
      <ErrorState
        title="We could not load your orders"
        onRetry={() => void orders.refetch()}
      />
    )
  }

  const rows = orders.data?.items ?? []
  const totalPages = orders.data?.totalPages ?? 0

  if (rows.length === 0) {
    return (
      <EmptyState
        title={page > 0 ? "No more orders" : "No orders yet"}
        description={
          page > 0
            ? "This page is empty. Go back to your most recent orders."
            : "When you place an order it will appear here with its delivery status."
        }
        action={
          page > 0 ? (
            <Link href={orderHistoryHref(0)} className={buttonVariants()}>
              Back to your orders
            </Link>
          ) : (
            <Link href="/shop" className={buttonVariants()}>
              Start shopping
            </Link>
          )
        }
      />
    )
  }

  return (
    <section aria-labelledby="orders-heading">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="orders-heading" className="text-lg font-medium tracking-tight text-ink">
          Your orders
        </h2>
        <p className="text-xs text-muted" aria-live="polite">
          {orders.data?.totalElements ?? 0} order
          {orders.data?.totalElements === 1 ? "" : "s"}
        </p>
      </div>

      <ul className="mt-4 divide-y divide-line border-y border-line">
        {rows.map((order) => (
          <OrderListItem key={order.id} order={order} />
        ))}
      </ul>

      {totalPages > 1 ? (
        <nav aria-label="Order history pages" className="mt-6 flex items-center justify-between gap-4">
          {page > 0 ? (
            <Link
              href={orderHistoryHref(page - 1)}
              className={buttonVariants({ variant: "secondary", size: "sm" })}
            >
              Newer orders
            </Link>
          ) : (
            <span />
          )}
          <p className="text-xs text-muted">
            Page {page + 1} of {totalPages}
          </p>
          {page + 1 < totalPages ? (
            <Link
              href={orderHistoryHref(page + 1)}
              className={buttonVariants({ variant: "secondary", size: "sm" })}
            >
              Older orders
            </Link>
          ) : (
            <span />
          )}
        </nav>
      ) : null}
    </section>
  )
}
