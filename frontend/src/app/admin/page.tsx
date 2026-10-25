"use client"

import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import {
  useAdminCategories,
  useAdminOrders,
  useDashboard,
} from "@/features/admin/hooks"
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/status-badge"
import { formatDate } from "@/lib/utils/date"

/**
 * Admin dashboard (Journey E, step 2: "opens a protected dashboard").
 *
 * <p>Revenue is only settled money: the backend counts PAID orders and excludes
 * refunded ones, and the definition is stated on the page so an operator is not
 * left guessing what the number means.
 */
export default function AdminDashboardPage() {
  const summary = useDashboard()
  const recent = useAdminOrders({ page: 0, size: 8 })
  const categories = useAdminCategories()

  if (summary.isPending) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold tracking-tight text-ink">Dashboard</h1>
        <Skeleton lines={6} />
      </div>
    )
  }

  if (summary.error) {
    return (
      <ErrorState
        title="We could not load the dashboard"
        description="The summary is unavailable right now. Try again."
        onRetry={() => void summary.refetch()}
      />
    )
  }

  const data = summary.data
  if (!data) return null

  const pending =
    (data.ordersByStatus.PENDING_PAYMENT ?? 0) + (data.ordersByStatus.CONFIRMED ?? 0)
  const toShip = data.ordersByStatus.PROCESSING ?? 0
  const returns = data.ordersByStatus.RETURN_REQUESTED ?? 0
  const currency = "VND"

  return (
    <div className="space-y-10">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Dashboard</h1>
          <p className="mt-1 text-xs text-muted">
            {data.generatedAt ? `Updated ${formatDate(data.generatedAt)}` : null}
          </p>
        </div>
        <Link
          href="/admin/orders?status=PENDING_PAYMENT"
          className="text-sm text-ink underline underline-offset-4 hover:text-accent"
        >
          Review orders awaiting payment
        </Link>
      </header>

      <section aria-labelledby="revenue-heading">
        <h2
          id="revenue-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          Revenue
        </h2>
        <div className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Metric label="Last 30 days" value={
            <PriceDisplay
              amountMinor={data.revenueLast30Days.revenueMinor}
              currency={currency}
              size="lg"
            />
          } hint="Paid orders" />
          <Metric label="All time" value={
            <PriceDisplay
              amountMinor={data.revenueAllTime.revenueMinor}
              currency={currency}
              size="lg"
            />
          } hint="Paid orders" />
          <Metric
            label="Average order"
            value={
              <PriceDisplay
                amountMinor={data.revenueLast30Days.averageOrderValueMinor}
                currency={currency}
                size="lg"
              />
            }
            hint="Last 30 days, paid"
          />
          <Metric
            label="Paid orders"
            value={<span className="text-2xl font-semibold tabular-nums text-ink">
              {data.revenueLast30Days.paidOrders}
            </span>}
            hint={`${data.revenueLast30Days.ordersPlaced} placed`}
          />
        </div>
        <p className="mt-3 max-w-3xl text-xs text-muted">
          Revenue counts settled payments only. Orders cancelled after payment
          are refunded and excluded, so this figure never includes money that was
          given back.
        </p>
      </section>

      <section aria-labelledby="work-heading">
        <h2
          id="work-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          Needs attention
        </h2>
        <div className="mt-3 grid gap-4 sm:grid-cols-3">
          <WorkCard
            href="/admin/orders?status=PENDING_PAYMENT"
            count={pending}
            label="Awaiting payment or confirmation"
          />
          <WorkCard href="/admin/orders?status=PROCESSING" count={toShip} label="To ship" />
          <WorkCard
            href="/admin/orders?status=RETURN_REQUESTED"
            count={returns}
            label="Return requests"
          />
        </div>
      </section>

      <div className="grid gap-10 lg:grid-cols-2">
        <section aria-labelledby="catalog-heading">
          <h2
            id="catalog-heading"
            className="text-xs font-medium uppercase tracking-label text-muted"
          >
            Catalogue
          </h2>
          <dl className="mt-3 divide-y divide-line border-y border-line text-sm">
            <Row label="Live products" value={data.activeProducts} href="/admin/products?status=ACTIVE" />
            <Row label="Drafts" value={data.draftProducts} href="/admin/products?status=DRAFT" />
            <Row
              label="Archived"
              value={data.archivedProducts}
              href="/admin/products?status=ARCHIVED"
            />
            <Row
              label="Categories"
              value={categories.data?.length ?? null}
              href="/admin/categories"
            />
          </dl>
        </section>

        <section aria-labelledby="stock-heading">
          <div className="flex items-baseline justify-between gap-3">
            <h2
              id="stock-heading"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Low stock
            </h2>
            <Link
              href="/admin/inventory"
              className="text-xs text-ink underline underline-offset-4 hover:text-accent"
            >
              Manage inventory
            </Link>
          </div>
          {data.lowStock.length === 0 ? (
            <EmptyState
              className="mt-3"
              title="Nothing running low"
              description={`Every active variant is above ${data.lowStockThreshold} units.`}
            />
          ) : (
            <ul className="mt-3 divide-y divide-line border-y border-line">
              {data.lowStock.slice(0, 8).map((item) => (
                <li
                  key={item.variantId}
                  className="flex items-center justify-between gap-4 py-3 text-sm"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium text-ink">{item.productName}</p>
                    <p className="text-xs text-muted">
                      {item.sku}
                      {[item.color, item.size].filter(Boolean).length > 0
                        ? ` · ${[item.color, item.size].filter(Boolean).join(" / ")}`
                        : ""}
                    </p>
                  </div>
                  <Badge variant={item.stockQuantity === 0 ? "accent" : "warning"}>
                    {item.stockQuantity === 0 ? "Out" : `${item.stockQuantity} left`}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section aria-labelledby="recent-heading">
        <div className="flex items-baseline justify-between gap-3">
          <h2
            id="recent-heading"
            className="text-xs font-medium uppercase tracking-label text-muted"
          >
            Latest orders
          </h2>
          <Link
            href="/admin/orders"
            className="text-xs text-ink underline underline-offset-4 hover:text-accent"
          >
            All orders
          </Link>
        </div>
        {recent.isPending ? (
          <div className="mt-3">
            <Skeleton lines={4} />
          </div>
        ) : recent.error ? (
          <p className="mt-3 text-sm text-muted">Orders could not be loaded.</p>
        ) : (recent.data?.items.length ?? 0) === 0 ? (
          <EmptyState className="mt-3" title="No orders yet" />
        ) : (
          <ul className="mt-3 divide-y divide-line border-y border-line">
            {(recent.data?.items ?? []).map((order) => (
              <li key={order.id}>
                <Link
                  href={`/admin/orders/${order.id}`}
                  className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm hover:bg-[#f5f3f0]"
                >
                  <span className="font-medium text-ink">{order.orderNumber}</span>
                  <span className="flex flex-wrap items-center gap-2">
                    <OrderStatusBadge status={order.status} />
                    <PaymentStatusBadge status={order.paymentStatus} />
                  </span>
                  <span className="text-xs text-muted">
                    {order.customerEmail ?? order.shipRecipientName ?? "—"}
                  </span>
                  <PriceDisplay
                    amountMinor={order.grandTotalMinor}
                    currency={order.currency}
                    size="sm"
                  />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function Metric({
  label,
  value,
  hint,
}: {
  label: string
  value: React.ReactNode
  hint?: string
}) {
  return (
    <div className="border border-line bg-surface p-4">
      <p className="text-xs font-medium uppercase tracking-label text-muted">{label}</p>
      <p className="mt-2">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted">{hint}</p> : null}
    </div>
  )
}

function WorkCard({
  href,
  count,
  label,
}: {
  href: string
  count: number
  label: string
}) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between gap-3 border border-line bg-surface p-4 transition-colors duration-150 hover:border-ink"
    >
      <span className="text-sm text-ink">{label}</span>
      <span className="text-2xl font-semibold tabular-nums text-ink">{count}</span>
    </Link>
  )
}

function Row({
  label,
  value,
  href,
}: {
  label: string
  value: number | null
  href: string
}) {
  return (
    <div className="flex items-center justify-between gap-3 py-3">
      <dt>
        <Link href={href} className="text-ink hover:text-accent">
          {label}
        </Link>
      </dt>
      <dd className="tabular-nums text-muted">{value ?? "—"}</dd>
    </div>
  )
}
