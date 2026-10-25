"use client"

import * as React from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { Button, buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { ErrorState } from "@/components/ui/error-state"
import { PriceDisplay } from "@/components/ui/price-display"
import { Skeleton } from "@/components/ui/skeleton"
import { TextareaField } from "@/components/admin/form-controls"
import { useToast } from "@/components/ui/toast"
import { useAdminOrder, useChangeOrderStatus } from "@/features/admin/hooks"
import { TotalsBreakdown } from "@/features/checkout/totals"
import { OrderProgress } from "@/features/orders/order-progress"
import { OrderStatusBadge, PaymentStatusBadge } from "@/features/orders/status-badge"
import { formatOrderAddress, orderItemAttributes } from "@/features/orders/workflow"
import { shippingMethodLabel } from "@/types/orders"
import { ApiError } from "@/lib/api/client"
import { formatDateTime } from "@/lib/utils/date"
import {
  adminOrderActions,
  UPDATE_ORDER_STATUS_NOTE_MAX,
  type AdminOrderAction,
} from "@/types/admin"

/**
 * Admin order detail and status changes (Journey E: "updates fulfillment status
 * through valid transitions"; DESIGN_REFERENCE §7).
 *
 * <p>Only the transitions the state machine allows are offered, and the server
 * checks again. A refusal is shown verbatim rather than swallowed: a transition
 * rejected as INVALID_STATE_TRANSITION is information the operator needs.
 */
export default function AdminOrderPage() {
  const params = useParams<{ orderId: string }>()
  const orderId = typeof params?.orderId === "string" ? params.orderId : undefined
  return <AdminOrderView orderId={orderId} />
}

function AdminOrderView({ orderId }: { orderId: string | undefined }) {
  const order = useAdminOrder(orderId)
  const change = useChangeOrderStatus(orderId ?? "")
  const { toast } = useToast()
  const [pendingAction, setPendingAction] = React.useState<AdminOrderAction | null>(null)
  const [note, setNote] = React.useState("")

  if (!orderId) return <ErrorState title="We could not find that order" />
  if (order.isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-56" />
        <Skeleton lines={7} />
      </div>
    )
  }
  if (order.error) {
    return order.error instanceof ApiError && order.error.status === 404 ? (
      <ErrorState
        title="We could not find that order"
        description="The link may be wrong."
        action={
          <Link href="/admin/orders" className={buttonVariants({ variant: "secondary", size: "sm" })}>
            Back to orders
          </Link>
        }
      />
    ) : (
      <ErrorState title="We could not load the order" onRetry={() => void order.refetch()} />
    )
  }
  if (!order.data) return <ErrorState title="We could not find that order" />

  const data = order.data
  const actions = adminOrderActions(data.status)

  function confirm(action: AdminOrderAction) {
    setPendingAction(action)
    setNote("")
  }

  function submit() {
    if (!pendingAction) return
    change.mutate(
      { status: pendingAction.target, note },
      {
        onSuccess: (updated) => {
          toast({
            title: `Order moved to ${updated.status.toLowerCase().replaceAll("_", " ")}`,
            variant: "success",
          })
          setPendingAction(null)
          setNote("")
        },
        onError: (failure) => {
          if (failure instanceof ApiError) {
            toast({
              title:
                failure.code === "INVALID_STATE_TRANSITION"
                  ? "That change is not allowed"
                  : "We could not update the order",
              description: failure.message,
              variant: "error",
            })
            // The order may have moved on; re-read rather than trust the view.
            void order.refetch()
          } else {
            toast({ title: "We could not update the order", variant: "error" })
          }
        },
      },
    )
  }

  return (
    <div className="space-y-8">
      <header>
        <Link
          href="/admin/orders"
          className="text-xs text-muted underline underline-offset-4 hover:text-ink"
        >
          ← All orders
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-ink">
              {data.orderNumber}
            </h1>
            <p className="mt-1 text-xs text-muted">
              Placed {data.placedAt ? formatDateTime(data.placedAt) : "—"}
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <OrderStatusBadge status={data.status} />
            <PaymentStatusBadge status={data.paymentStatus} />
          </div>
        </div>
        <OrderProgress status={data.status} />
      </header>

      <section aria-labelledby="fulfilment-heading" className="border-y border-line py-5">
        <h2
          id="fulfilment-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          Fulfilment
        </h2>
        {data.status === "DELIVERED" ? (
          <p className="mt-3 text-sm text-muted">
            Delivered orders can only change further when the customer requests a
            return, which they do from their account.
          </p>
        ) : actions.length === 0 ? (
          <p className="mt-3 text-sm text-muted">
            This order is closed; there is nothing left to change.
          </p>
        ) : (
          <div className="mt-3 flex flex-wrap gap-2">
            {actions.map((action) => (
              <Button
                key={action.target}
                size="sm"
                variant={action.destructive ? "destructive" : "secondary"}
                disabled={change.isPending}
                onClick={() => confirm(action)}
              >
                {action.label}
              </Button>
            ))}
          </div>
        )}

        {pendingAction ? (
          <div className="mt-4 space-y-3 border border-line bg-surface p-4">
            <p className="text-sm text-ink">
              Move this order to{" "}
              <strong>{pendingAction.target.toLowerCase().replaceAll("_", " ")}</strong>?
              {pendingAction.destructive
                ? " Any stock the order holds is released and a settled payment is marked refunded."
                : pendingAction.target === "RETURNED"
                  ? " The returned items go back into stock."
                  : null}
            </p>
            <TextareaField
              label="Note (optional)"
              rows={2}
              maxLength={UPDATE_ORDER_STATUS_NOTE_MAX}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              hint="Stored on the order's status history."
            />
            <div className="flex flex-wrap gap-2">
              <Button size="sm" loading={change.isPending} onClick={submit}>
                Confirm
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={change.isPending}
                onClick={() => setPendingAction(null)}
              >
                Keep as is
              </Button>
            </div>
          </div>
        ) : null}
      </section>

      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <section aria-labelledby="items-heading">
          <h2
            id="items-heading"
            className="text-xs font-medium uppercase tracking-label text-muted"
          >
            Items
          </h2>
          <ul className="mt-3 divide-y divide-line border-y border-line">
            {data.items.map((item) => (
              <li key={item.id} className="flex items-start justify-between gap-4 py-3 text-sm">
                <div className="min-w-0">
                  <p className="font-medium text-ink">{item.productName}</p>
                  <p className="text-xs uppercase tracking-label text-muted">
                    {orderItemAttributes(item)} · ×{item.quantity}
                  </p>
                </div>
                <PriceDisplay amountMinor={item.lineTotalMinor} currency={data.currency} size="sm" />
              </li>
            ))}
          </ul>
        </section>

        <aside className="space-y-6">
          <section aria-labelledby="summary-heading" className="border border-line bg-surface p-5">
            <h2
              id="summary-heading"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Summary
            </h2>
            <div className="mt-3">
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

          <section aria-labelledby="ship-heading">
            <h2
              id="ship-heading"
              className="text-xs font-medium uppercase tracking-label text-muted"
            >
              Ship to
            </h2>
            <address className="mt-2 text-sm not-italic text-muted">
              <span className="block font-medium text-ink">
                {data.shippingAddress.recipientName ?? "—"}
              </span>
              {formatOrderAddress(data.shippingAddress).map((line) => (
                <span key={line} className="block">
                  {line}
                </span>
              ))}
              {data.shippingAddress.phone ? (
                <span className="mt-1 block text-xs">{data.shippingAddress.phone}</span>
              ) : null}
            </address>
            {data.customerNote ? (
              <p className="mt-3 border-l-2 border-line pl-3 text-sm text-muted">
                “{data.customerNote}”
              </p>
            ) : null}
          </section>
        </aside>
      </div>

      <section aria-labelledby="history-heading">
        <h2
          id="history-heading"
          className="text-xs font-medium uppercase tracking-label text-muted"
        >
          History
        </h2>
        {data.statusHistory.length === 0 ? (
          <EmptyState className="mt-3" title="No history yet" />
        ) : (
          <ol className="mt-3 space-y-3 border-l border-line pl-4">
            {data.statusHistory.map((entry, index) => (
              <li key={`${entry.toStatus}-${entry.changedAt ?? index}`} className="text-sm">
                <p className="text-ink">{entry.note ?? entry.toStatus}</p>
                <p className="text-xs text-muted">
                  {entry.fromStatus ? `${entry.fromStatus} → ` : ""}
                  {entry.toStatus}
                  {entry.changedAt ? ` · ${formatDateTime(entry.changedAt)}` : ""}
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  )
}
