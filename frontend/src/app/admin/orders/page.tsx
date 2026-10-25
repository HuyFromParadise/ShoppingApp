"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { DataTable, type Column } from "@/components/admin/data-table"
import { SelectField } from "@/components/admin/form-controls"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/error-state"
import { Input } from "@/components/ui/input"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { useAdminOrders } from "@/features/admin/hooks"
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/status-badge"
import { orderStatusLabel } from "@/features/orders/workflow"
import { formatDate } from "@/lib/utils/date"
import type { AdminOrderSummary } from "@/types/admin"

const PAGE_SIZE = 20

const STATUS_OPTIONS = [
  "PENDING_PAYMENT",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "RETURN_REQUESTED",
  "RETURNED",
  "CANCELLED",
].map((value) => ({ value, label: orderStatusLabel(value) }))

/**
 * Order management list (Journey E: "reviews incoming orders"). Filters are URL
 * parameters, so the dashboard can deep-link to a filtered view.
 */
export default function AdminOrdersPage() {
  return (
    <React.Suspense fallback={<Skeleton lines={6} />}>
      <OrdersView />
    </React.Suspense>
  )
}

function OrdersView() {
  const params = useSearchParams()
  const router = useRouter()

  const status = params.get("status") ?? ""
  const q = params.get("q") ?? ""
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
  const [search, setSearch] = React.useState(q)
  const orders = useAdminOrders({ status: status || undefined, q: q || undefined, page, size: PAGE_SIZE })

  React.useEffect(() => setSearch(q), [q])

  function navigate(next: { status?: string; q?: string; page?: number }) {
    const merged = {
      status,
      q,
      page: next.page ?? 0,
      ...next,
    }
    const nextParams = new URLSearchParams()
    if (merged.status) nextParams.set("status", merged.status)
    if (merged.q) nextParams.set("q", merged.q)
    if (merged.page > 0) nextParams.set("page", String(merged.page))
    const query = nextParams.toString()
    router.push(query ? `/admin/orders?${query}` : "/admin/orders")
  }

  const columns: ReadonlyArray<Column<AdminOrderSummary>> = [
    {
      key: "order",
      header: "Order",
      render: (row) => (
        <div>
          <Link
            href={`/admin/orders/${row.id}`}
            className="font-medium text-ink underline-offset-4 hover:underline"
          >
            {row.orderNumber}
          </Link>
          <p className="text-xs text-muted">
            {row.placedAt ? formatDate(row.placedAt) : "—"}
          </p>
        </div>
      ),
    },
    {
      key: "customer",
      header: "Customer",
      hideOnMobile: true,
      render: (row) => (
        <span className="text-muted">{row.customerEmail ?? row.shipRecipientName ?? "—"}</span>
      ),
    },
    {
      key: "items",
      header: "Items",
      align: "right",
      hideOnMobile: true,
      render: (row) => row.itemCount,
    },
    {
      key: "total",
      header: "Total",
      align: "right",
      render: (row) => (
        <PriceDisplay amountMinor={row.grandTotalMinor} currency={row.currency} size="sm" />
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => (
        <div className="flex flex-wrap gap-1.5">
          <OrderStatusBadge status={row.status} />
          <PaymentStatusBadge status={row.paymentStatus} />
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold tracking-tight text-ink">Orders</h1>
        <p className="mt-1 text-xs text-muted">
          Search by order number, or by customer email when the search contains “@”.
        </p>
      </header>

      <form
        role="search"
        className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_14rem_auto]"
        onSubmit={(event) => {
          event.preventDefault()
          navigate({ q: search.trim() })
        }}
      >
        <div>
          <label htmlFor="order-search" className="mb-1.5 block text-sm font-medium text-ink">
            Search
          </label>
          <Input
            id="order-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="ORD-7F3K9QX2 or customer@example.com"
            maxLength={100}
          />
        </div>
        <SelectField
          id="order-status-filter"
          label="Status"
          value={status}
          placeholder="All statuses"
          onChange={(event) => navigate({ status: event.target.value })}
          options={STATUS_OPTIONS}
        />
        <div className="flex items-end">
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </div>
      </form>

      {orders.error ? (
        <ErrorState
          title="We could not load the orders"
          onRetry={() => void orders.refetch()}
        />
      ) : (
        <DataTable
          caption="Orders"
          columns={columns}
          rows={orders.data?.items ?? []}
          rowKey={(row) => row.id}
          loading={orders.isPending}
          emptyMessage={
            status || q ? "No orders match these filters." : "No orders yet."
          }
        />
      )}

      {(orders.data?.totalPages ?? 0) > 1 ? (
        <nav aria-label="Order pages" className="flex items-center justify-between gap-4">
          <Button
            variant="secondary"
            size="sm"
            disabled={page === 0}
            onClick={() => navigate({ page: page - 1 })}
          >
            Previous
          </Button>
          <p className="text-xs text-muted">
            Page {page + 1} of {orders.data?.totalPages}
          </p>
          <Button
            variant="secondary"
            size="sm"
            disabled={page + 1 >= (orders.data?.totalPages ?? 1)}
            onClick={() => navigate({ page: page + 1 })}
          >
            Next
          </Button>
        </nav>
      ) : null}
    </div>
  )
}
