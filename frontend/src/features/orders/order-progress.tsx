import { cn } from "@/lib/utils/cn"
import { lifecycleIndex, lifecycleSteps } from "@/features/orders/workflow"

const STEP_LABELS: Record<string, string> = {
  placed: "Order placed",
  processing: "Processing",
  shipped: "Shipped",
  delivered: "Delivered",
}

/**
 * Fulfillment timeline. Purely visual: it reads the existing status and never
 * predicts one. Cancelled and return states are not steps, so they are stated
 * by the status badge instead.
 */
export function OrderProgress({ status }: { status: string }) {
  const current = lifecycleIndex({ status })
  if (current < 0) return null

  const steps = lifecycleSteps()

  return (
    <ol className="mt-4 flex flex-wrap gap-x-6 gap-y-2" aria-label="Order progress">
      {steps.map((step, index) => {
        const state = index < current ? "done" : index === current ? "current" : "todo"
        return (
          <li
            key={step}
            aria-current={state === "current" ? "step" : undefined}
            className={cn(
              "flex items-center gap-2 text-sm",
              state === "current" && "font-medium text-ink",
              state === "done" && "text-muted",
              state === "todo" && "text-muted/70",
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                "h-2 w-2 rounded-full border",
                state === "todo"
                  ? "border-line bg-transparent"
                  : state === "current"
                    ? "border-ink bg-ink"
                    : "border-success bg-success",
              )}
            />
            {STEP_LABELS[step]}
          </li>
        )
      })}
    </ol>
  )
}
