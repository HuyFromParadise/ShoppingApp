"use client"

import * as React from "react"
import { useToast } from "@/components/ui/toast"
import { useSetStock } from "@/features/admin/hooks"
import { cn } from "@/lib/utils/cn"

/**
 * Absolute stock set (DESIGN_REFERENCE §5.3: "absolute set, audited").
 *
 * <p>The inputs are a count, never a delta, and the entered number is only sent
 * on an explicit save. A reason is optional but encouraged: it lands in the
 * audit row next to before/after, which is what makes a stock correction
 * explainable later.
 */
export function StockEditor({
  variantId,
  productId,
  current,
  threshold = 5,
}: {
  variantId: string
  productId?: string
  current: number
  threshold?: number
}) {
  const [value, setValue] = React.useState<string>(String(current))
  const [reason, setReason] = React.useState("")
  const [open, setOpen] = React.useState(false)
  const [localError, setLocalError] = React.useState<string | null>(null)
  const mutation = useSetStock(productId)
  const { toast } = useToast()

  React.useEffect(() => {
    setValue(String(current))
  }, [current])

  const parsed = value.trim() === "" ? null : Number(value)
  const invalid = parsed !== null && (!Number.isInteger(parsed) || parsed < 0)
  const dirty = parsed !== null && parsed !== current

  function save() {
    if (parsed === null || invalid) {
      setLocalError("Enter a whole number of units, zero or more.")
      return
    }
    setLocalError(null)
    mutation.mutate(
      { variantId, stockQuantity: parsed, reason },
      {
        onSuccess: (variant) => {
          toast({
            title: "Stock updated",
            description: `Now ${variant.stockQuantity} in stock. The change is recorded.`,
            variant: "success",
          })
          setOpen(false)
          setReason("")
        },
        onError: (error) => {
          setLocalError(
            error instanceof Error ? error.message : "Could not update stock.",
          )
        },
      },
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span
        className={cn(
          "inline-flex min-w-10 justify-center rounded-sm border px-2 py-0.5 text-xs font-medium tabular-nums",
          current === 0
            ? "border-[#e7c9c9] bg-[#f6e9e9] text-accent"
            : current <= threshold
              ? "border-[#e8dcc0] bg-[#f8f1de] text-[#7a5a12]"
              : "border-[#c9dfd3] bg-[#e8f1ec] text-success",
        )}
      >
        {current}
      </span>
      {open ? (
        <div className="w-full space-y-2">
          <div className="flex items-center gap-2">
            <label htmlFor={`stock-${variantId}`} className="text-xs text-muted">
              New total
            </label>
            <input
              id={`stock-${variantId}`}
              type="number"
              inputMode="numeric"
              min={0}
              step={1}
              value={value}
              onChange={(event) => setValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault()
                  save()
                }
              }}
              className="h-8 w-24 rounded border border-line bg-surface px-2 text-sm tabular-nums text-ink focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            />
            <input
              type="text"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={200}
              placeholder="Reason (optional)"
              aria-label="Reason for the stock change"
              className="h-8 min-w-40 flex-1 rounded border border-line bg-surface px-2 text-sm text-ink placeholder:text-muted focus-visible:border-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={save}
              disabled={mutation.isPending || !dirty || invalid}
              className="rounded border border-ink bg-ink px-3 py-1 text-xs font-medium text-surface transition-colors duration-150 hover:bg-[#2b2b2b] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {mutation.isPending ? "Saving…" : "Save stock"}
            </button>
            <button
              type="button"
              onClick={() => {
                setOpen(false)
                setValue(String(current))
                setReason("")
                setLocalError(null)
              }}
              className="rounded border border-line px-3 py-1 text-xs text-ink transition-colors duration-150 hover:border-ink"
            >
              Cancel
            </button>
            {invalid ? (
              <p role="alert" className="text-xs text-accent">
                Whole number, zero or more.
              </p>
            ) : localError ? (
              <p role="alert" className="text-xs text-accent">
                {localError}
              </p>
            ) : null}
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="text-xs text-ink underline underline-offset-4 hover:text-accent"
        >
          Set stock
        </button>
      )}
    </div>
  )
}
