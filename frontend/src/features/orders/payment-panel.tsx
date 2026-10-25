"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { PriceDisplay } from "@/components/ui/price-display"
import { useToast } from "@/components/ui/toast"
import {
  useConfirmPayment,
  useStartPayment,
} from "@/features/orders/hooks"
import {
  isAwaitingPayment,
  isPaid,
  paymentStatusLabel,
} from "@/features/orders/workflow"
import { ApiError } from "@/lib/api/client"
import type { OrderDetail, PaymentInitiation } from "@/types/orders"

/**
 * Payment step of an order detail.
 *
 * <p>The backend's only enabled provider is the development mock (ADR-007), so
 * the panel says so plainly: it is not a real payment. A real VNPay redirect is
 * shown as an unavailable option with the server's reason, never as a dead end.
 */
export function PaymentPanel({ order }: { order: OrderDetail }) {
  const start = useStartPayment()
  const confirm = useConfirmPayment()
  const { toast } = useToast()
  const [attempt, setAttempt] = React.useState<PaymentInitiation | null>(null)

  React.useEffect(() => {
    // A cancelled or confirmed order no longer has anything to pay.
    if (!isAwaitingPayment(order)) setAttempt(null)
  }, [order])

  if (isPaid(order)) {
    return (
      <p className="text-sm text-muted">
        This order is paid. We are preparing it for delivery.
      </p>
    )
  }

  if (order.status !== "PENDING_PAYMENT") {
    return (
      <p className="text-sm text-muted">
        This order is {paymentStatusLabel(order.paymentStatus).toLowerCase()} and
        currently {order.status.toLowerCase().replaceAll("_", " ")}.
      </p>
    )
  }

  function begin() {
    start.mutate(order.id, {
      onSuccess: (payment) => setAttempt(payment),
      onError: (error) =>
        toast({
          title: "We could not start the payment",
          description:
            error instanceof ApiError ? error.message : "Please try again.",
          variant: "error",
        }),
    })
  }

  function finish(approve: boolean) {
    if (!attempt) return
    confirm.mutate(
      { paymentId: attempt.paymentId, approve },
      {
        onSuccess: (result) => {
          setAttempt(null)
          toast({
            title: result.paid ? "Payment received" : "Payment declined",
            description: result.paid
              ? "Your order is confirmed. We will let you know when it ships."
              : "No money was taken. You can try again or cancel the order.",
            variant: result.paid ? "success" : "error",
          })
        },
        onError: (error) =>
          toast({
            title: "We could not confirm the payment",
            description:
              error instanceof ApiError ? error.message : "Please try again.",
            variant: "error",
          }),
      },
    )
  }

  if (attempt) {
    return (
      <div className="space-y-4">
        <div className="rounded border border-[#e8dcc0] bg-[#f8f1de] p-4 text-sm">
          <p className="font-medium text-ink">Test payment (no money is charged)</p>
          <p className="mt-1 text-muted">
            This storefront is running the development payment gateway. Approving
            here only simulates the provider so the order can move forward.
          </p>
        </div>
        <div className="flex justify-between gap-4 text-sm">
          <span className="text-muted">Amount</span>
          <PriceDisplay
            amountMinor={order.grandTotalMinor}
            currency={order.currency}
          />
        </div>
        <div className="flex flex-wrap gap-3">
          <Button loading={confirm.isPending} onClick={() => finish(true)}>
            Approve test payment
          </Button>
          <Button
            variant="secondary"
            disabled={confirm.isPending}
            onClick={() => finish(false)}
          >
            Decline
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        Pay{" "}
        <PriceDisplay
          amountMinor={order.grandTotalMinor}
          currency={order.currency}
          size="sm"
        />{" "}
        to confirm this order. Orders not paid in time are cancelled and the items
        released.
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Button loading={start.isPending} onClick={begin}>
          {order.paymentStatus === "FAILED" ? "Try payment again" : "Pay now"}
        </Button>
        <span className="text-xs text-muted">
          Development payment gateway — no money is charged.
        </span>
      </div>
    </div>
  )
}
