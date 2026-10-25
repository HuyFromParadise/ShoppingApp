"use client"

import * as React from "react"
import { StockEditor } from "@/components/admin/stock-editor"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { MoneyField } from "@/components/admin/form-controls"
import { useAddVariant, useUpdateVariant } from "@/features/admin/hooks"
import { PriceDisplay } from "@/components/ui/price-display"
import { cn } from "@/lib/utils/cn"
import type { AdminVariant } from "@/types/admin"

/**
 * Variant editor (PROJECT_SPEC §7: "VariantEditor"). Price, size, colour and
 * active state are edited in place; stock has its own absolute-set control
 * because it is the audited field (DESIGN_REFERENCE §5.3).
 */
export function VariantList({
  productId,
  variants,
  currency,
}: {
  productId: string
  variants: readonly AdminVariant[]
  currency: string
}) {
  return (
    <ul className="divide-y divide-line border-y border-line">
      {variants.map((variant) => (
        <VariantRow
          key={variant.id}
          productId={productId}
          variant={variant}
          currency={currency}
        />
      ))}
    </ul>
  )
}

function VariantRow({
  productId,
  variant,
  currency,
}: {
  productId: string
  variant: AdminVariant
  currency: string
}) {
  const update = useUpdateVariant(productId)
  const [editing, setEditing] = React.useState(false)
  const [size, setSize] = React.useState(variant.size ?? "")
  const [color, setColor] = React.useState(variant.color ?? "")
  const [sku, setSku] = React.useState(variant.sku)
  const [price, setPrice] = React.useState<number | null>(variant.priceOverrideMinor)
  const [error, setError] = React.useState<string | null>(null)

  React.useEffect(() => {
    setSize(variant.size ?? "")
    setColor(variant.color ?? "")
    setSku(variant.sku)
    setPrice(variant.priceOverrideMinor)
  }, [variant])

  function save() {
    setError(null)
    update.mutate(
      {
        variantId: variant.id,
        input: {
          size,
          color,
          sku,
          // The server treats a missing field as "leave it"; only send it when set.
          ...(price === null ? {} : { priceOverride: price }),
        },
      },
      {
        onSuccess: () => setEditing(false),
        onError: (failure) =>
          setError(failure instanceof Error ? failure.message : "Could not save the variant."),
      },
    )
  }

  return (
    <li className="py-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink">
            {[variant.color, variant.size].filter(Boolean).join(" / ") || "Default"}
          </p>
          <p className="mt-0.5 text-xs text-muted">{variant.sku}</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <PriceDisplay
              amountMinor={variant.effectivePriceMinor}
              currency={currency}
              size="sm"
            />
            {variant.priceOverrideMinor !== null ? (
              <Badge variant="outline">Override</Badge>
            ) : null}
            {!variant.active ? <Badge variant="neutral">Inactive</Badge> : null}
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          <StockEditor variantId={variant.id} productId={productId} current={variant.stockQuantity} />
          {editing ? null : (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="text-xs text-ink underline underline-offset-4 hover:text-accent"
            >
              Edit details
            </button>
          )}
        </div>
      </div>

      {editing ? (
        <div className="mt-4 space-y-3 border border-line bg-surface p-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted">Colour</span>
              <Input value={color} onChange={(event) => setColor(event.target.value)} maxLength={64} />
            </label>
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted">Size</span>
              <Input value={size} onChange={(event) => setSize(event.target.value)} maxLength={32} />
            </label>
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted">SKU</span>
              <Input value={sku} onChange={(event) => setSku(event.target.value)} maxLength={64} />
            </label>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <MoneyField
              label="Price override"
              valueMinor={price}
              onChangeMinor={setPrice}
              hint="Leave empty to use the product price."
            />
            <div className="flex items-end gap-3 pb-1">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => {
                  setEditing(false)
                  setError(null)
                }}
              >
                Close
              </Button>
              <Button type="button" size="sm" loading={update.isPending} onClick={save}>
                Save variant
              </Button>
            </div>
          </div>
          {error ? (
            <p role="alert" className={cn("text-xs text-accent")}>
              {error}
            </p>
          ) : null}
        </div>
      ) : null}
    </li>
  )
}

export interface NewVariantDraft {
  color: string
  size: string
  sku: string
  stock: string
}

/** Adds a variant. Size and colour must be unique within the product. */
export function AddVariantForm({ productId }: { productId: string }) {
  const add = useAddVariant(productId)
  const [draft, setDraft] = React.useState<NewVariantDraft>({
    color: "",
    size: "",
    sku: "",
    stock: "0",
  })
  const [error, setError] = React.useState<string | null>(null)

  const stock = Number(draft.stock)
  const stockValid = draft.stock.trim() !== "" && Number.isInteger(stock) && stock >= 0

  function submit() {
    setError(null)
    if (!stockValid) {
      setError("Stock must be a whole number, zero or more.")
      return
    }
    add.mutate(
      {
        color: draft.color.trim() || undefined,
        size: draft.size.trim() || undefined,
        sku: draft.sku.trim() || undefined,
        stockQuantity: stock,
        active: true,
      },
      {
        onSuccess: () => setDraft({ color: "", size: "", sku: "", stock: "0" }),
        onError: (failure) =>
          setError(failure instanceof Error ? failure.message : "Could not add the variant."),
      },
    )
  }

  return (
    <form
      className="space-y-3 border border-line bg-surface p-4"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      <p className="text-xs font-medium uppercase tracking-label text-muted">
        Add a variant
      </p>
      <div className="grid gap-3 sm:grid-cols-4">
        <label className="space-y-1">
          <span className="block text-xs text-muted">Colour</span>
          <Input
            value={draft.color}
            onChange={(event) => setDraft({ ...draft, color: event.target.value })}
            maxLength={64}
            placeholder="Sand"
          />
        </label>
        <label className="space-y-1">
          <span className="block text-xs text-muted">Size</span>
          <Input
            value={draft.size}
            onChange={(event) => setDraft({ ...draft, size: event.target.value })}
            maxLength={32}
            placeholder="M"
          />
        </label>
        <label className="space-y-1">
          <span className="block text-xs text-muted">SKU (optional)</span>
          <Input
            value={draft.sku}
            onChange={(event) => setDraft({ ...draft, sku: event.target.value })}
            maxLength={64}
            placeholder="Generated if blank"
          />
        </label>
        <label className="space-y-1">
          <span className="block text-xs text-muted">Opening stock</span>
          <Input
            type="number"
            min={0}
            step={1}
            value={draft.stock}
            onChange={(event) => setDraft({ ...draft, stock: event.target.value })}
          />
        </label>
      </div>
      {error ? (
        <p role="alert" className="text-xs text-accent">
          {error}
        </p>
      ) : null}
      <Button type="submit" size="sm" loading={add.isPending} disabled={!stockValid}>
        Add variant
      </Button>
    </form>
  )
}
