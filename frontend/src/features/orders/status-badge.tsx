import { Badge } from "@/components/ui/badge"
import {
  orderStatusLabel,
  orderStatusTone,
  paymentStatusLabel,
  paymentStatusTone,
} from "@/features/orders/workflow"

/** Fulfillment status in the storefront's voice. */
export function OrderStatusBadge({ status }: { status: string }) {
  return <Badge variant={orderStatusTone(status)}>{orderStatusLabel(status)}</Badge>
}

/**
 * Payment status. It is shown next to the fulfillment status because the two
 * are orthogonal (DESIGN_REFERENCE §7): a "Confirmed" order can still be unpaid.
 */
export function PaymentStatusBadge({ status }: { status: string }) {
  return (
    <Badge variant={paymentStatusTone(status)}>
      {paymentStatusLabel(status)}
    </Badge>
  )
}
