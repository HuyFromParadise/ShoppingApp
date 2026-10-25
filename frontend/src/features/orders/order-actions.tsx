"use client"

import { Button } from "@/components/ui/button"
import { useToast } from "@/components/ui/toast"
import { useCancelOrder, useRequestReturn } from "@/features/orders/hooks"
import { isCancellable, isReturnable } from "@/features/orders/workflow"
import { ApiError } from "@/lib/api/client"
import type { OrderDetail } from "@/types/orders"

/**
 * What the customer may do with an order (DESIGN_REFERENCE §7). The rules mirror
 * the backend state machine, but the buttons are only a convenience: the server
 * rejects a transition it does not allow, and that rejection is shown verbatim.
 */
export function OrderActions({ order }: { order: OrderDetail }) {
  const cancel = useCancelOrder()
  const requestReturn = useRequestReturn()
  const { toast } = useToast()

  const cancellable = isCancellable(order)
  const returnable = isReturnable(order)

  if (!cancellable && !returnable) return null

  function handleError(failure: unknown, fallback: string) {
    toast({
      title: fallback,
      description:
        failure instanceof ApiError ? failure.message : "Please try again.",
      variant: "error",
    })
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {cancellable ? (
        <Button
          variant="destructive"
          size="sm"
          loading={cancel.isPending}
          disabled={requestReturn.isPending}
          onClick={() => {
            if (!window.confirm("Cancel this order? Reserved items go back on sale.")) {
              return
            }
            cancel.mutate(order.id, {
              onSuccess: () =>
                toast({
                  title: "Order cancelled",
                  description: "Any payment will be refunded.",
                  variant: "success",
                }),
              onError: (failure) =>
                handleError(failure, "We could not cancel this order"),
            })
          }}
        >
          Cancel order
        </Button>
      ) : null}

      {returnable ? (
        <Button
          variant="secondary"
          size="sm"
          loading={requestReturn.isPending}
          disabled={cancel.isPending}
          onClick={() => {
            if (!window.confirm("Request a return for this order?")) return
            requestReturn.mutate(order.id, {
              onSuccess: () =>
                toast({
                  title: "Return requested",
                  description: "We will review it and contact you by email.",
                  variant: "success",
                }),
              onError: (failure) =>
                handleError(failure, "We could not request the return"),
            })
          }}
        >
          Request a return
        </Button>
      ) : null}
    </div>
  )
}
